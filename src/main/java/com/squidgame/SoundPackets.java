package com.squidgame;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquetes de los audífonos. El mismo paquete se usa en los dos sentidos:
 * cliente -> servidor (subir el audio) y servidor -> clientes (repartirlo).
 */
public class SoundPackets {
    public static final int CHUNK_SIZE = 28000;

    public static class BeginPacket {
        public final UUID id;
        public final String name;
        public final int total;
        public final int chunks;
        public final float volume;
        public final boolean loop;
        public final float fadeIn;
        public final float startSec;
        public final float speed;
        public final boolean stopPrev;
        public final int radius;

        public BeginPacket(UUID id, String name, int total, int chunks, float volume, boolean loop,
                           float fadeIn, float startSec, float speed, boolean stopPrev, int radius) {
            this.id = id;
            this.name = name.length() > 200 ? name.substring(0, 200) : name;
            this.total = Mth.clamp(total, 0, 100 * 1024 * 1024);
            this.chunks = Mth.clamp(chunks, 0, 5000);
            this.volume = Mth.clamp(volume, 0.0F, 2.0F);
            this.loop = loop;
            this.fadeIn = Mth.clamp(fadeIn, 0.0F, 30.0F);
            this.startSec = Mth.clamp(startSec, 0.0F, 3600.0F);
            this.speed = Mth.clamp(speed, 0.25F, 4.0F);
            this.stopPrev = stopPrev;
            this.radius = Mth.clamp(radius, 0, 1000);
        }

        public static void encode(BeginPacket m, FriendlyByteBuf b) {
            b.writeUUID(m.id);
            b.writeUtf(m.name, 256);
            b.writeInt(m.total);
            b.writeInt(m.chunks);
            b.writeFloat(m.volume);
            b.writeBoolean(m.loop);
            b.writeFloat(m.fadeIn);
            b.writeFloat(m.startSec);
            b.writeFloat(m.speed);
            b.writeBoolean(m.stopPrev);
            b.writeInt(m.radius);
        }

        public static BeginPacket decode(FriendlyByteBuf b) {
            return new BeginPacket(b.readUUID(), b.readUtf(256), b.readInt(), b.readInt(), b.readFloat(),
                    b.readBoolean(), b.readFloat(), b.readFloat(), b.readFloat(), b.readBoolean(), b.readInt());
        }

        public static void handle(BeginPacket m, Supplier<NetworkEvent.Context> ctxs) {
            NetworkEvent.Context ctx = ctxs.get();
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                ctx.enqueueWork(() -> SoundRelay.onBegin(sender, m));
            } else {
                ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.squidgame.client.ClientAudio.onBegin(m)));
            }
            ctx.setPacketHandled(true);
        }
    }

    public static class ChunkPacket {
        public final UUID id;
        public final int index;
        public final byte[] data;

        public ChunkPacket(UUID id, int index, byte[] data) {
            this.id = id;
            this.index = index;
            this.data = data;
        }

        public static void encode(ChunkPacket m, FriendlyByteBuf b) {
            b.writeUUID(m.id);
            b.writeInt(m.index);
            b.writeByteArray(m.data);
        }

        public static ChunkPacket decode(FriendlyByteBuf b) {
            return new ChunkPacket(b.readUUID(), b.readInt(), b.readByteArray(32000));
        }

        public static void handle(ChunkPacket m, Supplier<NetworkEvent.Context> ctxs) {
            NetworkEvent.Context ctx = ctxs.get();
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                ctx.enqueueWork(() -> SoundRelay.onChunk(sender, m));
            } else {
                ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.squidgame.client.ClientAudio.onChunk(m)));
            }
            ctx.setPacketHandled(true);
        }
    }

    public static class StopPacket {
        public final float fadeOut;
        public final int radius;

        public StopPacket(float fadeOut, int radius) {
            this.fadeOut = Mth.clamp(fadeOut, 0.0F, 30.0F);
            this.radius = Mth.clamp(radius, 0, 1000);
        }

        public static void encode(StopPacket m, FriendlyByteBuf b) {
            b.writeFloat(m.fadeOut);
            b.writeInt(m.radius);
        }

        public static StopPacket decode(FriendlyByteBuf b) {
            return new StopPacket(b.readFloat(), b.readInt());
        }

        public static void handle(StopPacket m, Supplier<NetworkEvent.Context> ctxs) {
            NetworkEvent.Context ctx = ctxs.get();
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                ctx.enqueueWork(() -> SoundRelay.onStop(sender, m));
            } else {
                ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.squidgame.client.ClientAudio.onStop(m)));
            }
            ctx.setPacketHandled(true);
        }
    }
}
