package com.wish.client.util;

public interface ISizeableState {
    float wish$getScaleX();
    float wish$getScaleY();
    float wish$getScaleZ();
    boolean wish$hasCustomScale();

    void wish$setScale(float x, float y, float z);
    
    boolean wish$isLocalPlayer();
    void wish$setLocalPlayer(boolean isLocal);
    
    boolean wish$isSorakkaa();
    void wish$setSorakkaa(boolean isSorakkaa);

    boolean wish$isMairuy();
    void wish$setMairuy(boolean isMairuy);

    boolean wish$isFi4sk0();
    void wish$setFi4sk0(boolean isFi4sk0);

    boolean wish$isNotsley();
    void wish$setNotsley(boolean isNotsley);
}
