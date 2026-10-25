package me.cortex.voxy.legacy.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Small lifecycle hooks; leaves Angelica's DH implementation and mod detection intact. */
public final class VoxyTransformer implements IClassTransformer, Opcodes {
    private static final String BRIDGE="me/cortex/voxy/legacy/shader/ShaderBridge";
    public byte[] transform(String name,String transformedName,byte[] bytes) {
        if(bytes==null)return bytes;
        if(transformedName.equals("net.minecraft.client.renderer.EntityRenderer")){
            ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
            for(MethodNode m:node.methods)if(m.name.equals("setupCameraTransform")||m.name.equals("func_78479_a")||(m.name.equals("a")&&m.desc.equals("(FI)V")))
                for(AbstractInsnNode ins:m.instructions.toArray())if(ins instanceof MethodInsnNode call&&call.name.equals("gluPerspective")&&call.desc.equals("(FFFF)V")){
                    m.instructions.insertBefore(ins,call("cameraFarPlane","(F)F"));changed=true;
                }
            if(!changed)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
        }
        if(!transformedName.startsWith("net.coderbot.iris."))return bytes;
        String target=transformedName.substring("net.coderbot.iris.".length());
        if(!java.util.Set.of("pipeline.DeferredWorldRenderingPipeline","pipeline.ShadowRenderer","compat.dh.DHCompat","gl.shader.StandardMacros","uniforms.CameraUniforms").contains(target))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        boolean changed=false;
        for(MethodNode m:node.methods){
            if(target.equals("pipeline.DeferredWorldRenderingPipeline")){
                String hook=switch(m.name){case "beginLevelRendering"->"beginFrame";case "beginTranslucents"->"opaque";case "finalizeLevelRendering"->"translucent";case "destroy"->"destroy";default->null;};
                if(hook!=null){InsnList code=new InsnList();code.add(new VarInsnNode(ALOAD,0));code.add(call(hook,"(Ljava/lang/Object;)V"));m.instructions.insert(code);changed=true;}
            } else if(target.equals("pipeline.ShadowRenderer")&&m.name.equals("copyPreTranslucentDepth")){
                m.instructions.insert(call("shadow","()V"));changed=true;
            } else {
                String hook=null,desc=null;
                if(target.equals("uniforms.CameraUniforms")&&m.name.equals("getRenderDistanceInBlocks")){hook="cameraDistance";desc="(I)I";}
                if(target.equals("gl.shader.StandardMacros")&&m.name.equals("createStandardEnvironmentDefines")){hook="defines";desc="(Ljava/lang/Iterable;)Ljava/lang/Iterable;";}
                if(target.equals("compat.dh.DHCompat")){
                    switch(m.name){
                        case "getProjection"->{hook="projection";desc="(Lorg/joml/Matrix4fc;)Lorg/joml/Matrix4fc;";}
                        case "getFarPlane"->{hook="farPlane";desc="(F)F";}
                        case "getNearPlane"->{hook="nearPlane";desc="(F)F";}
                        case "getRenderDistance"->{hook="distance";desc="(I)I";}
                        case "getDepthTex"->{hook="depth";desc="(I)I";}
                        case "getDepthTexNoTranslucent"->{hook="opaqueDepth";desc="(I)I";}
                    }
                }
                if(hook!=null)for(AbstractInsnNode ins:m.instructions.toArray())if(ins.getOpcode()>=IRETURN&&ins.getOpcode()<=ARETURN){m.instructions.insertBefore(ins,call(hook,desc));changed=true;}
            }
        }
        if(!changed)return bytes;
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static MethodInsnNode call(String name,String desc){return new MethodInsnNode(INVOKESTATIC,BRIDGE,name,desc,false);}
}
