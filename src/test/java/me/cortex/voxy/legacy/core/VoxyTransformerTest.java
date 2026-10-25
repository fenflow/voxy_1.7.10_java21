package me.cortex.voxy.legacy.core;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.jupiter.api.Assertions.*;

class VoxyTransformerTest implements Opcodes {
    @Test void extendsOnlyCameraFarArgumentBeforeAndAfterDeobfuscation() throws Exception {
        for(String name:new String[]{"a","func_78479_a","setupCameraTransform"}) {
            var writer=new ClassWriter(0);
            writer.visit(V1_6,ACC_PUBLIC,"net/minecraft/client/renderer/EntityRenderer",null,"java/lang/Object",null);
            var method=writer.visitMethod(ACC_PUBLIC,name,"(FI)V",null,null);
            method.visitCode();for(int i=0;i<4;i++)method.visitInsn(FCONST_1);
            method.visitMethodInsn(INVOKESTATIC,"org/lwjgl/util/glu/Project","gluPerspective","(FFFF)V",false);
            method.visitInsn(RETURN);method.visitMaxs(4,3);method.visitEnd();writer.visitEnd();
            byte[] transformed=new VoxyTransformer().transform("blt","net.minecraft.client.renderer.EntityRenderer",writer.toByteArray());
            var node=new ClassNode();new ClassReader(transformed).accept(node,0);
            var m=node.methods.get(0);
            long hooks=java.util.Arrays.stream(m.instructions.toArray()).filter(i->i instanceof MethodInsnNode call&&call.name.equals("cameraFarPlane")&&call.desc.equals("(F)F")).count();
            assertEquals(1,hooks,name);
            new Analyzer<>(new BasicVerifier()).analyze(node.name,m);
        }
    }
}
