package com.squidgame;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

/** /nick hide, /numero y el mensaje "Jugador #001 Nombre eliminado". */
@Mod.EventBusSubscriber(modid = SquidGameMod.MODID)
public class NameCommands {

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nick")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("hide")
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .executes(ctx -> setHidden(ctx.getSource(), EntityArgument.getPlayer(ctx, "jugador"), true))))
                .then(Commands.literal("show")
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .executes(ctx -> setHidden(ctx.getSource(), EntityArgument.getPlayer(ctx, "jugador"), false)))));

        event.getDispatcher().register(Commands.literal("numero")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("numero", IntegerArgumentType.integer(1, 100))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .executes(ctx -> setNumber(ctx.getSource(), EntityArgument.getPlayer(ctx, "jugador"),
                                        IntegerArgumentType.getInteger(ctx, "numero")))))
                .then(Commands.literal("quitar")
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .executes(ctx -> clearNumber(ctx.getSource(), EntityArgument.getPlayer(ctx, "jugador"))))));
    }

    private static int setHidden(CommandSourceStack src, ServerPlayer p, boolean hide) {
        SGData d = SGData.get(src.getServer());
        if (hide) d.hiddenNames.add(p.getUUID());
        else d.hiddenNames.remove(p.getUUID());
        d.setDirty();
        sync(src.getServer());
        final String n = p.getGameProfile().getName();
        src.sendSuccess(() -> Component.literal(hide ? "Nombre de " + n + " oculto." : "Nombre de " + n + " visible."), true);
        return 1;
    }

    private static int setNumber(CommandSourceStack src, ServerPlayer p, int number) {
        SGData d = SGData.get(src.getServer());
        d.numbers.put(p.getUUID(), number);
        d.setDirty();
        sync(src.getServer());
        final String n = p.getGameProfile().getName();
        src.sendSuccess(() -> Component.literal(n + " ahora es el #" + format(number)), true);
        return 1;
    }

    private static int clearNumber(CommandSourceStack src, ServerPlayer p) {
        SGData d = SGData.get(src.getServer());
        d.numbers.remove(p.getUUID());
        d.setDirty();
        sync(src.getServer());
        final String n = p.getGameProfile().getName();
        src.sendSuccess(() -> Component.literal("Se quitó el número de " + n + "."), true);
        return 1;
    }

    public static String format(int n) {
        return String.format(Locale.ROOT, "%03d", n);
    }

    // ------------------------------------------------------------------ sincronizar

    private static ControlPackets.NameData data(MinecraftServer server) {
        SGData d = SGData.get(server);
        return new ControlPackets.NameData(new HashMap<>(d.numbers), new HashSet<>(d.hiddenNames));
    }

    public static void sync(MinecraftServer server) {
        Net.CHANNEL.send(PacketDistributor.ALL.noArg(), data(server));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), data(p.server));
        }
    }

    // ------------------------------------------------------------------ mensaje de muerte

    /** El mensaje normal de muerte se apaga una sola vez por mundo; el nuestro lo reemplaza. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        SGData d = SGData.get(server);
        if (!d.deathRuleApplied) {
            server.getGameRules().getRule(GameRules.RULE_SHOWDEATHMESSAGES).set(false, server);
            d.deathRuleApplied = true;
            d.setDirty();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer server = p.server;
        Integer n = SGData.get(server).numbers.get(p.getUUID());
        String middle = (n != null ? "#" + format(n) + " " : "") + p.getGameProfile().getName() + " ";
        MutableComponent msg = Component.literal("Jugador ").withStyle(ChatFormatting.RED)
                .append(Component.literal(middle).withStyle(ChatFormatting.WHITE))
                .append(Component.literal("eliminado").withStyle(ChatFormatting.RED));
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }
}
