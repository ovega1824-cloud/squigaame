package com.squidgame.client;

import com.squidgame.FinalPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Estado de la final en el cliente (lo manda el servidor). */
public class ClientFinal {
    public static boolean active = false;
    public static String n1 = "", n2 = "";
    public static int l1 = 3, l2 = 3;
    public static final Set<UUID> downed = new HashSet<>();
    public static final Set<UUID> models = new HashSet<>();

    private static final Map<UUID, Float> progress = new HashMap<>();
    private static final Map<UUID, Float> progressPrev = new HashMap<>();

    public static void onState(FinalPackets.State m) {
        active = m.active;
        n1 = m.n1;
        n2 = m.n2;
        l1 = m.l1;
        l2 = m.l2;
        downed.clear();
        downed.addAll(m.downed);
        models.clear();
        models.addAll(m.models);
    }

    public static void reset() {
        active = false;
        downed.clear();
        models.clear();
        progress.clear();
        progressPrev.clear();
    }

    public static boolean hasModel(UUID id) {
        return models.contains(id);
    }

    public static boolean isDowned(UUID id) {
        return downed.contains(id);
    }

    /** 0 = de pie, 1 = tirado; suavizado para que la caída no sea de golpe. */
    public static float downAmount(UUID id, float partialTick) {
        float a = progressPrev.getOrDefault(id, 0.0F);
        float b = progress.getOrDefault(id, 0.0F);
        return a + (b - a) * partialTick;
    }

    /** Se llama una vez por tick del cliente. */
    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        for (Player p : mc.level.players()) {
            UUID id = p.getUUID();
            float cur = progress.getOrDefault(id, 0.0F);
            progressPrev.put(id, cur);
            float target = downed.contains(id) && models.contains(id) ? 1.0F : 0.0F;
            if (cur < target) cur = Math.min(target, cur + 0.1F);
            else if (cur > target) cur = Math.max(target, cur - 0.1F);
            progress.put(id, cur);
        }
    }
}
