package net.caffeinemc.mods.sodium.client.world;

import net.minecraft.world.level.block.state.BlockState;

public abstract class LevelSlice {
    public abstract BlockState getBlockState(int x, int y, int z);
}
