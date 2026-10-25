package me.cortex.voxy.legacy.shader;

import java.util.*;
import java.util.regex.*;
import me.cortex.voxy.client.core.gl.shader.ShaderLoader;

/** Adapts the standard Iris inputs, without depending on a pack's varying names. */
final class PackShaderPatcher {
    record Sources(String vertex,String fragment) {}
    static String prepareVertex(String source) {
        return source.replaceAll("\\bgl_MultiTexCoord0\\b","voxy_texcoord")
            .replaceFirst("(#version[^\\n]*\\n)","$1vec4 voxy_texcoord;\n");
    }
    static Sources adapt(String vertex,String fragment,boolean distant,boolean shadow) {
        var colours=new LinkedHashMap<String,String>();
        if(distant&&!shadow){
            var declarations=Pattern.compile("\\bout\\s+(vec[34])\\s+(\\w+)\\s*;").matcher(vertex);
            while(declarations.find()) {
                String name=declarations.group(2);
                if(Pattern.compile("\\b"+name+"\\s*=\\s*_vert_color\\b").matcher(vertex).find())colours.put(name,declarations.group(1));
            }
            if(colours.isEmpty())throw new IllegalArgumentException("Pack has no supported vertex-colour output for distant terrain");
        }
        vertex=vertex(vertex);
        String exports="";
        for(var colour:colours.entrySet()){
            String n=colour.getKey(),type=colour.getValue();
            vertex=vertex.replaceFirst("\\bout\\s+"+type+"\\s+"+n+"\\s*;","out "+type+" voxy_input_"+n+"; "+type+" "+n+";");
            exports+="voxy_input_"+n+"="+n+";";
            fragment=fragment.replaceFirst("\\bin\\s+"+type+"\\s+"+n+"\\s*;","in "+type+" voxy_input_"+n+"; "+type+" "+n+";");
        }
        if(!exports.isEmpty())vertex=wrapMain(vertex,"",exports);
        String sample=sample();
        String prelude;
        if(shadow) {
            fragment="#version 460 core\nin vec3 voxy_relative;\nuniform float voxyNearRadius;\n"+sample
                +"\nvoid main(){if(max(abs(voxy_relative.x),abs(voxy_relative.z))<voxyNearRadius)discard;voxy_sample();}\n";
        }else{
            prelude="if(texelFetch(voxyNearDepth,ivec2(gl_FragCoord.xy),0).r<1.0)discard;";
            prelude+="vec4 voxy_albedo=voxy_sample();";
            for(var colour:colours.entrySet())prelude+=colour.getKey()+"=voxy_input_"+colour.getKey()+"*voxy_albedo"+(colour.getValue().equals("vec3")?".rgb":"")+";";
            if(!distant) {
                // Standard terrain texture samplers receive the baked, biome-tinted voxel albedo.
                fragment=fragment.replaceAll("\\b(?:texture2D|texture|texture2DLod|textureLod|textureGrad|texture2DGradARB)\\s*\\(\\s*(?:gtexture|texture|tex)\\s*,","voxy_texture(");
                String signatures="vec4 voxy_texture(vec2 uv);vec4 voxy_texture(vec2 uv,float bias);vec4 voxy_texture(vec2 uv,vec2 dx,vec2 dy);\n";
                fragment=fragment.replaceFirst("(#version[^\\n]*\\n)","$1"+signatures);
                sample+="\nvec4 voxy_texture(vec2 uv){return voxy_sample();}vec4 voxy_texture(vec2 uv,float bias){return voxy_sample();}vec4 voxy_texture(vec2 uv,vec2 dx,vec2 dy){return voxy_sample();}\n";
            }
            fragment=insertBeforeMain(fragment,"layout(binding=31) uniform sampler2D voxyNearDepth;\n"+sample);
            fragment=wrapMain(fragment,prelude,"");
        }
        return new Sources(version(vertex),version(fragment));
    }
    static String vertex(String source) {
        String decoder=ShaderLoader.parse("voxy:lod/gl46/quads2.vert")
            .replaceAll("(?m)^#version[^\\n]*|^#extension[^\\n]*", "")
            .replaceAll("layout\\(location = \\d+\\) out", "out")
            .replace("void main()", "void voxy_decode()")
            .replace("gl_Position = MVP*vec4(", "voxyPosition = vec4(");
        decoder=prefix(decoder);
        decoder="vec4 voxyPosition;\nout vec3 voxy_relative;\n"+decoder;
        String attributes="";
        for(String name:new String[]{"mc_Entity","mc_midTexCoord","at_tangent","at_midBlock"}){
            var match=Pattern.compile("(?:layout\\s*\\([^)]*\\)\\s*)?\\bin\\s+(vec[234])\\s+"+name+"\\s*;").matcher(source);
            if(match.find()){
                String type=match.group(1);source=match.replaceFirst(type+" "+name+";");
                String value=name.equals("mc_Entity")?"float(int(modelData[extractStateId(q)]._pad[1]))":"0.5";
                if(name.equals("at_tangent"))attributes+=name+"="+type+"(vec4(normalize(cross(abs(_vert_normal.y)<0.9?vec3(0,1,0):vec3(0,0,1),_vert_normal)),1));";
                else attributes+=name+"="+type+"("+value+");";
            }
        }
        decoder += "\nvoid voxy_init() { voxy_decode(); _vert_position=voxyPosition.xyz-cameraSubPos;voxy_relative=_vert_position;"
            + "Quad q=quadData[uint(gl_VertexID)>>2]; uint f=extractFace(q);"
            + "_vert_normal=irisNormals[int(f)]; uint l=extractLightId(q);"
            + "_vert_tex_light_coord=(vec2(float(l>>4u),float(15u-(l&15u)))+0.5)/16.0;"
            + "dhMaterialId=int(modelData[extractStateId(q)]._pad[0]);_vert_color=vec4(1);"
            + (source.contains("voxy_texcoord")?"voxy_texcoord=vec4(fract(voxy_uv),0,1);":"")+attributes+"}\n";
        source=source.replaceAll("_vert_init\\s*\\(\\s*\\)\\s*;", "voxy_init();");
        return version(insertBeforeMain(source,decoder));
    }
    private static String sample(){
        return prefix(ShaderLoader.parse("voxy:lod/gl46/quads.frag")
            .replaceAll("(?m)^#version[^\\n]*", "")
            .replaceAll("layout\\(location = \\d+\\) in", "in")
            .replace("layout(location = 0) out vec4 outColour;", "")
            .replace("void main()", "vec4 voxy_sample()")
            .replace("outColour = (colour * tinting) + addin;", "return colour;"));
    }
    private static String prefix(String source){
        for(String variable:new String[]{"uv","baseUV","tinting","addin","flags","conditionalTinting"})source=source.replaceAll("\\b"+variable+"\\b", "voxy_"+variable);
        return source;
    }
    private static String insertBeforeMain(String source,String code){return source.replaceFirst("void\\s+main\\s*\\(",Matcher.quoteReplacement(code+"\nvoid main("));}
    private static String wrapMain(String source,String before,String after){
        return source.replaceFirst("void\\s+main\\s*\\(","void voxy_pack_main(")+"\nvoid main(){"+before+"voxy_pack_main();"+after+"}\n";
    }
    private static String version(String source) {
        // GLSL 4.60 adds double overloads: mod(int, float) can become ambiguous.
        // Cast known scalar integer inputs rather than relying on implicit promotion.
        var integers=Pattern.compile("\\bint\\s+(\\w+)\\b").matcher(source);
        var names=new HashSet<String>();while(integers.find())names.add(integers.group(1));
        for(String n:names)source=source.replaceAll("\\bmod\\s*\\(\\s*"+n+"\\s*,","mod(float("+n+"),");
        return source.replaceFirst("#version[^\\n]*", "#version 460 core\n#extension GL_ARB_gpu_shader_int64 : enable");
    }
}
