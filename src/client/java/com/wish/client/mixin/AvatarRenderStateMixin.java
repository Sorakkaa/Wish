package com.wish.client.mixin;

import com.wish.client.util.ISizeableState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class AvatarRenderStateMixin implements ISizeableState {
    @Unique
    private float wish$scaleX = 1.0f;
    @Unique
    private float wish$scaleY = 1.0f;
    @Unique
    private float wish$scaleZ = 1.0f;
    @Unique
    private boolean wish$hasCustomScale = false;
    @Unique
    private boolean wish$isLocalPlayer = false;
    @Unique
    private boolean wish$isSorakkaa = false;
    @Unique
    private boolean wish$isMairuy = false;

    @Override
    public float wish$getScaleX() {
        return wish$scaleX;
    }

    @Override
    public float wish$getScaleY() {
        return wish$scaleY;
    }

    @Override
    public float wish$getScaleZ() {
        return wish$scaleZ;
    }

    @Override
    public boolean wish$hasCustomScale() {
        return wish$hasCustomScale;
    }

    @Override
    public void wish$setScale(float x, float y, float z) {
        this.wish$scaleX = x;
        this.wish$scaleY = y;
        this.wish$scaleZ = z;
        this.wish$hasCustomScale = (x != 1.0f || y != 1.0f || z != 1.0f);
    }
    
    @Override
    public boolean wish$isLocalPlayer() {
        return wish$isLocalPlayer;
    }
    
    @Override
    public void wish$setLocalPlayer(boolean isLocal) {
        this.wish$isLocalPlayer = isLocal;
    }
    
    @Override
    public boolean wish$isSorakkaa() {
        return wish$isSorakkaa;
    }
    
    @Override
    public void wish$setSorakkaa(boolean isSorakkaa) {
        this.wish$isSorakkaa = isSorakkaa;
    }

    @Override
    public boolean wish$isMairuy() {
        return wish$isMairuy;
    }

    @Override
    public void wish$setMairuy(boolean isMairuy) {
        this.wish$isMairuy = isMairuy;
    }
}
