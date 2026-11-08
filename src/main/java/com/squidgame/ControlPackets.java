package com.squidgame;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Paquetes del palo morado (secuencias de comandos) y de nombres/números. */
public class ControlPackets {

    /** Cliente -> servidor: comandos a ejecutar uno tras otro. Lista vacía = detener la secuencia. */
    public static class Sequence {
        public final List<String> commands;
        public final List<Integer> delays; // ticks de espera ANTES de cada comando

        public Sequence(List<String> commands, List<Integer> delays) {
            this.commands = commands;
            this.delays = delays;
        }

        public static void encode(Sequence m, FriendlyByteBuf b) {
            b.writeVarInt(m.commands.size());
            for (int i = 0; i < m.commands.size(); i++) {
                b.writeUtf(m.commands.get(i), 300);
                b.writeVarInt(m.delays.get(i));
            }
        }

        public static Sequence decode(FriendlyByteBuf b) {
            int n = Math.min(b.readVarInt(), 100);
            List<String> c = new ArrayList<>();
            List<Integer> d = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                c.add(b.readUtf(300));
                d.add(Math.max(0, Math.min(b.readVarInt(), 72000 * 24)));
            }
            return new Sequence(c, d);
        }

        public static void handle(Sequence m, Supplier<NetworkEvent.Context> ctxs) {
            NetworkEvent.Context ctx = ctxs.get();
            ServerPlayer sender = ctx.getSender();
            if (sender != null) ctx.enqueueWork(() -> SequenceRunner.onPacket(sender, m));
            ctx.setPacketHandled(true);
        }
    }

    /** Servidor -> clientes: números bajo el nombre y nombres ocultos. */
    public static class NameData {
        public final Map<UUID, Integer> numbers;
        public final Set<UUID> hidden;

        public NameData(Map<UUID, Integer> numbers, Set<UUID> hidden) {
            this.numbers = numbers;
            this.hidden = hidden;
        }

        public static void encode(NameData m, FriendlyByteBuf b) {
            b.writeVarInt(m.numbers.size());
            for (Map.Entry<UUID, Integer> e : m.numbers.entrySet()) {
                b.writeUUID(e.getKey());
                b.writeVarInt(e.getValue());
            }
            b.writeVarInt(m.hidden.size());
            for (UUID u : m.hidden) b.writeUUID(u);
        }

        public static NameData decode(FriendlyByteBuf b) {
            Map<UUID, Integer> nums = new HashMap<>();
            int n = Math.min(b.readVarInt(), 4096);
            for (int i = 0; i < n; i++) nums.put(b.readUUID(), b.readVarInt());
            Set<UUID> hid = new HashSet<>();
            int h = Math.min(b.readVarInt(), 4096);
            for (int i = 0; i < h; i++) hid.add(b.readUUID());
            return new NameData(nums, hid);
        }

        public static void handle(NameData m, Supplier<NetworkEvent.Context> ctxs) {
            NetworkEvent.Context ctx = ctxs.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.squidgame.client.ClientNames.onData(m)));
            ctx.setPacketHandled(true);
        }
    }
}
