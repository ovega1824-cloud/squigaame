package com.squidgame.client;

import com.squidgame.SquidGameMod;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Oculta nombres (/nick hide) y dibuja el número (/numero) bajo el nombre. */
@Mod.EventBusSubscriber(modid = SquidGameMod.MODID, value = Dist.CLIENT)
public class ClientNameEvents {

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (ClientNames.hidden.contains(event.getEntity().getUUID())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SuppressWarnings("rawtypes")
    @SubscribeEvent
    public static void onRenderPost(RenderLivingEvent.Post event) {
        if (event.getEntity() instanceof Player p && !ClientFinal.hasModel(p.getUUID())) {
            ClientNames.renderNumber(p, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        }
    }
}
