package com.squidgame;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Bloque negro con una F. Los bloques F colocados forman el área de la final. */
public class FinalBlock extends Block {
    public FinalBlock(Properties props) {
        super(props);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            MinecraftServer server = level.getServer();
            if (server != null) {
                SGData.get(server).addFinalBlock(pos, level.dimension().location().toString());
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            MinecraftServer server = level.getServer();
            if (server != null) {
                SGData.get(server).removeFinalBlock(pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
