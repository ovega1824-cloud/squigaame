package com.squidgame;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Palo verde: clic derecho en un bloque = punto 1, otro clic = punto 2, y así. */
public class BridgeWandItem extends Item {
    private static final Map<UUID, Boolean> NEXT_IS_SECOND = new HashMap<>();

    public BridgeWandItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!level.isClientSide && ctx.getPlayer() instanceof ServerPlayer sp) {
            BlockPos pos = ctx.getClickedPos();
            boolean second = NEXT_IS_SECOND.getOrDefault(sp.getUUID(), false);
            SGData.get(sp.server).setBridgePoint(second ? 2 : 1, pos, level.dimension().location().toString());
            NEXT_IS_SECOND.put(sp.getUUID(), !second);
            sp.displayClientMessage(Component.literal("Punto " + (second ? 2 : 1) + " del puente: "
                    + pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
