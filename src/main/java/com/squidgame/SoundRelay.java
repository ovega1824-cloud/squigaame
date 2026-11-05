package com.squidgame;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Servidor: recibe el audio de quien usa los audífonos y lo reparte a los demás jugadores. */
@Mod.EventBusSubscriber(modid = SquidGameMod.MODID)
public class SoundRelay {
    private static final class Transfer {
        UUID sender;
        List<UUID> to;
        int chunks;
        int got;
    }

    private static final Map<UUID, Transfer> TRANSFERS = new HashMap<>();

    private static boolean allowed(ServerPlayer p) {
        return !ServerConfig.onlyOps() || p.hasPermissions(2);
    }

    private static List<ServerPlayer> targets(ServerPlayer sender, int radius) {
        List<ServerPlayer> list = new ArrayList<>();
        for (ServerPlayer p : sender.server.getPlayerList().getPlayers()) {
            if (radius > 0 && (p.level() != sender.level() || p.distanceToSqr(sender) > (double) radius * radius)) {
                continue;
            }
            list.add(p);
        }
        return list;
    }

    private static void send(ServerPlayer p, Object pkt) {
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }

    public static void onBegin(ServerPlayer sender, SoundPackets.BeginPacket m) {
        if (!allowed(sender)) return;
        long max = ServerConfig.maxMb() * 1024L * 1024L;
        if (m.total <= 0 || m.total > max || m.chunks <= 0) return;

        UUID senderId = sender.getUUID();
        TRANSFERS.values().removeIf(t -> t.sender.equals(senderId));

        Transfer t = new Transfer();
        t.sender = senderId;
        t.chunks = m.chunks;
        t.to = new ArrayList<>();
        for (ServerPlayer p : targets(sender, m.radius)) {
            t.to.add(p.getUUID());
            send(p, m);
        }
        TRANSFERS.put(m.id, t);
    }

    public static void onChunk(ServerPlayer sender, SoundPackets.ChunkPacket m) {
        Transfer t = TRANSFERS.get(m.id);
        if (t == null || !t.sender.equals(sender.getUUID())) return;
        for (UUID id : t.to) {
            ServerPlayer p = sender.server.getPlayerList().getPlayer(id);
            if (p != null) send(p, m);
        }
        t.got++;
        if (t.got >= t.chunks) TRANSFERS.remove(m.id);
    }

    public static void onStop(ServerPlayer sender, SoundPackets.StopPacket m) {
        if (!allowed(sender)) return;
        for (ServerPlayer p : targets(sender, m.radius)) send(p, m);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        TRANSFERS.values().removeIf(t -> t.sender.equals(id));
    }
}
