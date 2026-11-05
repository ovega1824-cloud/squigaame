package com.squidgame.client;

import net.minecraft.client.Minecraft;

public class ClientHooks {
    public static long flashStart = -1L;

    public static void flashGreen() {
        flashStart = System.currentTimeMillis();
    }

    public static void openMenu() {
        Minecraft.getInstance().setScreen(new HeadphonesScreen());
    }
}
