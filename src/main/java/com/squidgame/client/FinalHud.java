package com.squidgame.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Barras de vidas arriba de la pantalla: roja = jugador 1, azul = jugador 2. */
public class FinalHud {
    private static final ResourceLocation[] P1 = new ResourceLocation[4];
    private static final ResourceLocation[] P2 = new ResourceLocation[4];

    static {
        for (int i = 0; i < 4; i++) {
            P1[i] = new ResourceLocation("squidgame", "textures/gui/final/p1_" + i + ".png");
            P2[i] = new ResourceLocation("squidgame", "textures/gui/final/p2_" + i + ".png");
        }
    }

    private static final int BAR_W = 112; // la imagen es de 32x8 útiles
    private static final int BAR_H = 28;

    public static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        if (ClientFinal.active) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int y = 12;
            int l1 = Math.max(0, Math.min(3, ClientFinal.l1));
            int l2 = Math.max(0, Math.min(3, ClientFinal.l2));
            g.blit(P1[l1], 6, y, BAR_W, BAR_H, 0.0F, 11.0F, 32, 8, 32, 32);
            g.blit(P2[l2], width - 6 - BAR_W, y, BAR_W, BAR_H, 0.0F, 11.0F, 32, 8, 32, 32);
            RenderSystem.disableBlend();

            g.drawString(font, ClientFinal.n1, 8, 3, 0xFFFFFFFF, true);
            int w2 = font.width(ClientFinal.n2);
            g.drawString(font, ClientFinal.n2, width - 8 - w2, 3, 0xFFFFFFFF, true);
        }
    }
}
