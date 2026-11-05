package com.squidgame;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Bloque 1 = inicio, Bloque 2 = meta. Al colocarlos se guardan como la zona de juego. */
public class LineBlock extends Block {
    private final int number;

    public LineBlock(int number, Properties props) {
        super(props);
        this.number = number;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide && !state.is(oldState.getBlock())) {
            MinecraftServer server = level.getServer();
            if (server != null) {
                SGData.get(server).set(number, pos, level.dimension().location().toString());
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !state.is(newState.getBlock())) {
            MinecraftServer server = level.getServer();
            if (server != null) {
                SGData.get(server).clearIf(number, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
