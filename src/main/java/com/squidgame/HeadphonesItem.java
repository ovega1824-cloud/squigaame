package com.squidgame;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Audífonos: clic derecho abre el menú para elegir un sonido que oyen todos. */
public class HeadphonesItem extends Item {
    public HeadphonesItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.squidgame.client.ClientHooks::openMenu);
        }
        // "fail" no mueve el brazo (no cuenta como golpe en luz roja) y no abre nada más
        return InteractionResultHolder.fail(stack);
    }
}
