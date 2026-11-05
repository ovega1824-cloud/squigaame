package com.squidgame.client;

import com.squidgame.SquidGameMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = SquidGameMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
    private static final long HOLD_MS = 1200L;
    private static final long TOTAL_MS = 3200L;

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        SoundLibrary.ensureFolder(); // crea .minecraft/sound
        ClientConfig.load();
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("green_flash", (gui, graphics, partialTick, width, height) -> {
            long start = ClientHooks.flashStart;
            if (start < 0) return;
            long t = System.currentTimeMillis() - start;
            if (t >= TOTAL_MS) {
                ClientHooks.flashStart = -1L;
                return;
            }
            float alpha = 0.65F;
            if (t > HOLD_MS) {
                alpha *= 1.0F - (float) (t - HOLD_MS) / (float) (TOTAL_MS - HOLD_MS);
            }
            int a = Math.max(0, Math.min(255, (int) (alpha * 255.0F)));
            graphics.fill(0, 0, width, height, (a << 24) | 0x1EC83C);
        });
    }
}
