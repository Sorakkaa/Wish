package com.wish.client.util;

public interface ISizeableState {
    float wish$getScaleX();
    float wish$getScaleY();
    float wish$getScaleZ();
    boolean wish$hasCustomScale();

    void wish$setScale(float x, float y, float z);
    
    boolean wish$isLocalPlayer();
    void wish$setLocalPlayer(boolean isLocal);
    
    boolean wish$isCustomPlayer();
    void wish$setCustomPlayer(boolean isCustomPlayer);

    float wish$getSpinSpeedY();
    void wish$setSpinSpeedY(float spinSpeedY);

    boolean wish$isSmolPeople();
    void wish$setSmolPeople(boolean smolPeople);
}
