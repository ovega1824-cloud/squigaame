package com.squidgame.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.squidgame.ControlPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Números bajo el nombre y nombres ocultos (lo manda el servidor). */
public class ClientNames {
    public static final Map<UUID, Integer> numbers = new HashMap<>();
    public static final Set<UUID> hidden = new HashSet<>();

    public static void onData(ControlPackets.NameData m) {
        numbers.clear();
        numbers.putAll(m.numbers);
        hidden.clear();
        hidden.addAll(m.hidden);
    }

    public static void reset() {
        numbers.clear();
        hidden.clear();
    }

    /** Dibuja "#001" justo debajo de donde va el nombre. */
    public static void renderNumber(Player p, PoseStack ps, MultiBufferSource buffers, int light) {
        Integer n = numbers.get(p.getUUID());
        if (n == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getEntityRenderDispatcher().distanceToSqr(p) > 4096.0D) return;

        String text = "#" + String.format(Locale.ROOT, "%03d", n);
        ps.pushPose();
        ps.translate(0.0F, p.getBbHeight() + 0.27F, 0.0F);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f m = ps.last().pose();
        Font font = mc.font;
        float x = -font.width(text) / 2.0F;
        int bg = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        font.drawInBatch(text, x, 0.0F, 553648127, false, m, buffers, Font.DisplayMode.SEE_THROUGH, bg, light);
        font.drawInBatch(text, x, 0.0F, -1, false, m, buffers, Font.DisplayMode.NORMAL, 0, light);
        ps.popPose();
    }
}
