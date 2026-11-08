package com.squidgame;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Paquete de la final: vidas, nombres, jugadores tirados y quién lleva el modelo. */
public class FinalPackets {

    public static class State {
        public final boolean active;
        public final String n1, n2;
        public final int l1, l2;
        public final List<UUID> downed, models;

        public State(boolean active, String n1, String n2, int l1, int l2, List<UUID> downed, List<UUID> models) {
            this.active = active;
            this.n1 = n1;
            this.n2 = n2;
            this.l1 = l1;
            this.l2 = l2;
            this.downed = downed;
            this.models = models;
        }

        private static void writeList(FriendlyByteBuf b, List<UUID> l) {
            b.writeVarInt(l.size());
            for (UUID u : l) b.writeUUID(u);
        }

        private static List<UUID> readList(FriendlyByteBuf b) {
            int n = Math.min(b.readVarInt(), 512);
            List<UUID> l = new ArrayList<>();
            for (int i = 0; i < n; i++) l.add(b.readUUID());
            return l;
        }

        public static void encode(State m, FriendlyByteBuf b) {
            b.writeBoolean(m.active);
            b.writeUtf(m.n1, 64);
            b.writeUtf(m.n2, 64);
            b.writeVarInt(m.l1);
            b.writeVarInt(m.l2);
            writeList(b, m.downed);
            writeList(b, m.models);
        }

        public static State decode(FriendlyByteBuf b) {
            return new State(b.readBoolean(), b.readUtf(64), b.readUtf(64), b.readVarInt(), b.readVarInt(),
                    readList(b), readList(b));
        }

        public static void handle(State m, Supplier<NetworkEvent.Context> ctxs) {
            NetworkEvent.Context ctx = ctxs.get();
            ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.squidgame.client.ClientFinal.onState(m)));
            ctx.setPacketHandled(true);
        }
    }
}
