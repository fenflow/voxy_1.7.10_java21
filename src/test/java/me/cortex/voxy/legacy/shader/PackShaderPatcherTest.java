package me.cortex.voxy.legacy.shader;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PackShaderPatcherTest {
    private static final String VERT="#version 330 core\nout vec4 tint;void _vert_init ( ) {}\nvoid main ( ) { _vert_init ( ) ; tint = _vert_color; tint.rgb *= 1.2; }";
    @Test void acceptsAngelicaFormattedVertexInitializer(){
        String patched=PackShaderPatcher.vertex(VERT);
        assertTrue(patched.substring(patched.lastIndexOf("void main")).contains("voxy_init();"));
    }
    @Test void carriesPackColourMultiplierIntoTexturedFragment(){
        var p=PackShaderPatcher.adapt(VERT,"#version 330 core\nin vec4 tint;void main(){vec4 albedo=tint;}",true,false);
        assertTrue(p.vertex().contains("voxy_input_tint=tint;"));
        assertTrue(p.vertex().contains("tint.rgb *= 1.2"));
        assertTrue(p.fragment().contains("tint=voxy_input_tint*voxy_albedo;"));
        assertTrue(p.fragment().contains("texelFetch(voxyNearDepth"));
        assertFalse(p.fragment().contains("return colour * tinting"));
    }
    @Test void supportsRgbVaryingsWithoutPackSpecificNames(){
        var p=PackShaderPatcher.adapt(VERT.replace("vec4 tint","vec3 arbitrary").replace("tint","arbitrary").replace("= _vert_color;","= _vert_color.rgb;"),"#version 400 core\nin vec3 arbitrary;void main(){vec3 a=arbitrary;}",true,false);
        assertTrue(p.fragment().contains("arbitrary=voxy_input_arbitrary*voxy_albedo.rgb;"));
    }
    @Test void rejectsUnknownColourInterfaceInsteadOfRenderingWhite(){
        assertThrows(IllegalArgumentException.class,()->PackShaderPatcher.adapt("#version 330 core\nvoid main(){}","#version 330 core\nvoid main(){}",true,false));
    }
    @Test void detectsProgramsByContents(){
        assertTrue(PackCapabilities.hasDistantTerrain(List.of("renamed/shaders/world0/dh_terrain.vsh","renamed/shaders/world0/dh_terrain.fsh")));
        assertFalse(PackCapabilities.hasDistantTerrain(List.of("shaders/dh_terrain.vsh","shaders/world0/dh_terrain.fsh")));
    }
    @Test void legacyTextureHelpersAndIntegerModAreAdapted(){
        var p=PackShaderPatcher.adapt(VERT,"#version 330 core\nuniform int counter;float phase=mod(counter,100.0);vec4 helper(){return textureGrad(gtexture,vec2(0),vec2(0),vec2(0));}void main(){vec4 a=textureLod(gtexture,vec2(0),0.0);}",false,false);
        assertTrue(p.fragment().contains("mod(float(counter),100.0)"));
        assertFalse(p.fragment().contains("textureLod(gtexture"));
        assertFalse(p.fragment().contains("textureGrad(gtexture"));
        assertTrue(p.fragment().indexOf("vec4 voxy_texture(vec2 uv);")<p.fragment().indexOf("vec4 helper"));
    }
}
