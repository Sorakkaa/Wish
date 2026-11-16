package com.wish.client.mixin;

import com.wish.client.util.IWeatherRenderState;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(WeatherRenderState.class)
public class WeatherRenderStateMixin implements IWeatherRenderState {

    @Unique private int wish$simMode = 0;
    @Unique private int wish$count = 0;
    @Unique private float[] wish$rx = new float[0];
    @Unique private float[] wish$ry = new float[0];
    @Unique private float[] wish$rz = new float[0];
    @Unique private float[] wish$dx = new float[0];
    @Unique private float[] wish$dy = new float[0];
    @Unique private float[] wish$dz = new float[0];
    @Unique private float[] wish$scale = new float[0];
    @Unique private float[] wish$spin = new float[0];
    @Unique private float[] wish$alpha = new float[0];
    @Unique private int[] wish$light = new int[0];
    @Unique private byte[] wish$variant = new byte[0];

    @Inject(method = "reset", at = @At("RETURN"))
    private void wish$onReset(CallbackInfo ci) {
        wish$clearParticles();
    }

    @Override
    public void wish$clearParticles() {
        wish$count = 0;
        wish$simMode = 0;
    }

    @Override
    public void wish$setSimMode(int mode) {
        wish$simMode = mode;
    }

    @Override
    public int wish$getSimMode() {
        return wish$simMode;
    }

    @Override
    public int wish$getParticleCount() {
        return wish$count;
    }

    @Override
    public void wish$ensureCapacity(int cap) {
        if (cap <= wish$rx.length) {
            return;
        }
        int newCap = Math.max(cap, wish$rx.length == 0 ? 512 : (wish$rx.length << 1));
        wish$rx = Arrays.copyOf(wish$rx, newCap);
        wish$ry = Arrays.copyOf(wish$ry, newCap);
        wish$rz = Arrays.copyOf(wish$rz, newCap);
        wish$dx = Arrays.copyOf(wish$dx, newCap);
        wish$dy = Arrays.copyOf(wish$dy, newCap);
        wish$dz = Arrays.copyOf(wish$dz, newCap);
        wish$scale = Arrays.copyOf(wish$scale, newCap);
        wish$spin = Arrays.copyOf(wish$spin, newCap);
        wish$alpha = Arrays.copyOf(wish$alpha, newCap);
        wish$light = Arrays.copyOf(wish$light, newCap);
        wish$variant = Arrays.copyOf(wish$variant, newCap);
    }

    @Override
    public void wish$addParticle(float rx, float ry, float rz, float dx, float dy, float dz,
                                 float sizeScale, float spinRad, float alpha, int lightCoords, byte variant) {
        if (wish$count >= wish$rx.length) {
            wish$ensureCapacity(wish$count + 1);
        }
        int idx = wish$count++;
        wish$rx[idx] = rx;
        wish$ry[idx] = ry;
        wish$rz[idx] = rz;
        wish$dx[idx] = dx;
        wish$dy[idx] = dy;
        wish$dz[idx] = dz;
        wish$scale[idx] = sizeScale;
        wish$spin[idx] = spinRad;
        wish$alpha[idx] = alpha;
        wish$light[idx] = lightCoords;
        wish$variant[idx] = variant;
    }

    @Override public float[] wish$rx() { return wish$rx; }
    @Override public float[] wish$ry() { return wish$ry; }
    @Override public float[] wish$rz() { return wish$rz; }
    @Override public float[] wish$dx() { return wish$dx; }
    @Override public float[] wish$dy() { return wish$dy; }
    @Override public float[] wish$dz() { return wish$dz; }
    @Override public float[] wish$scale() { return wish$scale; }
    @Override public float[] wish$spin() { return wish$spin; }
    @Override public float[] wish$alpha() { return wish$alpha; }
    @Override public int[] wish$light() { return wish$light; }
    @Override public byte[] wish$variant() { return wish$variant; }
}
