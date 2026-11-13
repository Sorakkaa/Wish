package com.wish.client.util;

public interface IWeatherRenderState {
    void wish$clearParticles();
    void wish$setSimMode(int mode);
    int wish$getSimMode();
    int wish$getParticleCount();
    void wish$ensureCapacity(int cap);
    void wish$addParticle(float rx, float ry, float rz, float dx, float dy, float dz,
                          float sizeScale, float spinRad, float alpha, int lightCoords, byte variant);
    float[] wish$rx();
    float[] wish$ry();
    float[] wish$rz();
    float[] wish$dx();
    float[] wish$dy();
    float[] wish$dz();
    float[] wish$scale();
    float[] wish$spin();
    float[] wish$alpha();
    int[] wish$light();
    byte[] wish$variant();
}
