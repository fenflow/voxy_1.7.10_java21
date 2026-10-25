#version 450 core
layout(binding=0) uniform sampler2D sourceDepth;
in vec2 UV;
out vec4 colour;
void main(){
    if(texelFetch(sourceDepth,ivec2(gl_FragCoord.xy),0).r<1.0)discard;
    colour=vec4(1.0);
}
