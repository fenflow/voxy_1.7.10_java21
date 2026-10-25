package me.cortex.voxy.legacy;

import java.lang.reflect.Method;
import org.joml.Matrix4f;

/** Uses Angelica's public Iris API and its tracked matrices, including emulated core profiles. */
public final class AngelicaCompat {
    private static Method api, shaders, projection, modelView, fog;
    static {
        try {
            Class<?> iris=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api=iris.getMethod("getInstance"); shaders=iris.getMethod("isShaderPackInUse");
            Class<?> state=Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager");
            projection=state.getMethod("getProjectionMatrix"); modelView=state.getMethod("getModelViewMatrix");
            fog=state.getMethod("glFogf",int.class,float.class);
        } catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported Angelica API",e);}
    }
    public static boolean shadersActive(){try{return (boolean)shaders.invoke(api.invoke(null));}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    public static void extendTerrainFog(float end){
        try { fog.invoke(null,2915,end*.75f); fog.invoke(null,2916,end); }
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public static Matrix4f projection(){return matrix(projection);}
    public static Matrix4f modelView(){return matrix(modelView);}
    private static Matrix4f matrix(Method method){try{return new Matrix4f((org.joml.Matrix4fc)method.invoke(null));}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
}
