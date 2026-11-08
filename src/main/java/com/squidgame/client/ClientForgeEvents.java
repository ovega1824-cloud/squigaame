package com.squidgame.client;

import com.squidgame.SquidGameMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SquidGameMod.MODID, value = Dist.CLIENT)
public class ClientForgeEvents {
    /** Al salir del mundo se corta cualquier sonido de los audífonos. */
    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAudio.clear();
        ClientFinal.reset();
        ClientNames.reset();
    }
}
