package com.squidgame;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Coloca un panel de cristal de 2x2 (cuatro losas) hacia donde mira el jugador. */
public class BridgeGlassItem extends Item {
    public BridgeGlassItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        BlockPos base = ctx.getClickedPos();
        if (!level.getBlockState(base).canBeReplaced()) {
            base = base.relative(ctx.getClickedFace());
        }
        Direction fwd = ctx.getHorizontalDirection();
        Direction right = fwd.getClockWise();

        List<BlockPos> cells = new ArrayList<>(4);
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                cells.add(base.relative(fwd, a).relative(right, b));
            }
        }
        for (BlockPos p : cells) {
            if (level.isOutsideBuildHeight(p) || !level.getBlockState(p).canBeReplaced()) {
                return InteractionResult.FAIL;
            }
        }

        if (!level.isClientSide) {
            int minX = Integer.MAX_VALUE;
            int minZ = Integer.MAX_VALUE;
            for (BlockPos p : cells) {
                minX = Math.min(minX, p.getX());
                minZ = Math.min(minZ, p.getZ());
            }
            for (BlockPos p : cells) {
                BlockState st = ModRegistry.BRIDGE_GLASS.get().defaultBlockState()
                        .setValue(BridgeGlassBlock.PX, p.getX() != minX)
                        .setValue(BridgeGlassBlock.PZ, p.getZ() != minZ);
                level.setBlock(p, st, 3);
            }
            level.playSound(null, base, SoundType.GLASS.getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
            if (player == null || !player.getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
