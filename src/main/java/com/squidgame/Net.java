package com.squidgame;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SquidGameMod.MODID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    public static void init() {
        CHANNEL.registerMessage(0, GreenFlashPacket.class,
                GreenFlashPacket::encode, GreenFlashPacket::decode, GreenFlashPacket::handle);
        CHANNEL.registerMessage(1, SoundPackets.BeginPacket.class,
                SoundPackets.BeginPacket::encode, SoundPackets.BeginPacket::decode, SoundPackets.BeginPacket::handle);
        CHANNEL.registerMessage(2, SoundPackets.ChunkPacket.class,
                SoundPackets.ChunkPacket::encode, SoundPackets.ChunkPacket::decode, SoundPackets.ChunkPacket::handle);
        CHANNEL.registerMessage(3, SoundPackets.StopPacket.class,
                SoundPackets.StopPacket::encode, SoundPackets.StopPacket::decode, SoundPackets.StopPacket::handle);
    }

    /** Le dice al cliente que ponga la pantalla verde. */
    public static class GreenFlashPacket {
        public static void encode(GreenFlashPacket msg, FriendlyByteBuf buf) {
        }

        public static GreenFlashPacket decode(FriendlyByteBuf buf) {
            return new GreenFlashPacket();
        }

        public static void handle(GreenFlashPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() ->
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> com.squidgame.client.ClientHooks::flashGreen));
            ctx.get().setPacketHandled(true);
        }
    }
}
