package com.squidgame;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lógica del juego 1: luz verde, luz roja. */
@Mod.EventBusSubscriber(modid = SquidGameMod.MODID)
public class GameManager {
    /** Ticks de gracia al empezar la luz roja (0.4 s) para no matar por latencia/inercia. */
    private static final int GRACE_TICKS = 8;
    /** Movimiento mínimo (en bloques) para considerarlo movimiento. */
    private static final double MOVE_EPS = 0.05D;
    /** Giro mínimo de cámara (en grados) para considerarlo movimiento. */
    private static final float ROT_EPS = 1.0F;

    private record Ref(double x, double y, double z, float yaw, float pitch) {
    }

    private record DeathSpot(String dim, double x, double y, double z, float yaw, float pitch) {
    }

    private static final Map<UUID, DeathSpot> deathSpots = new HashMap<>();
    private static final Set<UUID> alive = new HashSet<>();
    private static final Set<UUID> winners = new HashSet<>();
    private static final Map<UUID, GameType> startModes = new HashMap<>();
    private static final Map<UUID, GameType> eliminated = new HashMap<>();
    private static final Map<UUID, GameType> pendingRevive = new HashMap<>();
    private static final Map<UUID, Ref> refs = new HashMap<>();

    private static boolean running = false;
    private static boolean red = false;
    private static int timeLeft;
    private static int phaseLeft;
    private static int grace;
    private static String dimId = "";
    private static BlockPos finish = BlockPos.ZERO;
    private static boolean axisX = true;
    private static int sign = 1;

    // ------------------------------------------------------------------ comando

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sg")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("j1")
                        .then(Commands.literal("start")
                                .executes(ctx -> start(ctx.getSource(), 300))
                                .then(Commands.argument("tiempo", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                new String[]{"30s", "60s", "90s", "2m", "5m", "10m"}, builder))
                                        .executes(ctx -> {
                                            int secs = parseDuration(StringArgumentType.getString(ctx, "tiempo"));
                                            if (secs <= 0) {
                                                ctx.getSource().sendFailure(Component.literal(
                                                        "Tiempo no válido. Ejemplos: 90, 90s, 2m, 1m30s, 1h"));
                                                return 0;
                                            }
                                            return start(ctx.getSource(), secs);
                                        })))
                        .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                        .then(Commands.literal("revive").executes(ctx -> revive(ctx.getSource())))));
    }

    private static final Pattern DURATION = Pattern.compile("^(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?$");

    /** Acepta: 90, 90s, 2m, 1m30s, 1h, 1h5m10s. Devuelve segundos o -1. */
    static int parseDuration(String text) {
        String s = text.trim().toLowerCase();
        if (s.isEmpty()) return -1;
        try {
            if (s.matches("\\d+")) {
                return clamp(Long.parseLong(s));
            }
            Matcher m = DURATION.matcher(s);
            if (!m.matches()) return -1;
            long h = m.group(1) == null ? 0 : Long.parseLong(m.group(1));
            long min = m.group(2) == null ? 0 : Long.parseLong(m.group(2));
            long sec = m.group(3) == null ? 0 : Long.parseLong(m.group(3));
            return clamp(h * 3600L + min * 60L + sec);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int clamp(long seconds) {
        if (seconds <= 0) return -1;
        return (int) Math.min(seconds, 86400L); // máx. 24 h
    }

    private static int start(CommandSourceStack src, int seconds) {
        if (running || BridgeGame.isRunning()) {
            src.sendFailure(Component.literal("Ya hay un juego en curso. Usa /sg j1 stop para detenerlo."));
            return 0;
        }
        MinecraftServer server = src.getServer();
        SGData data = SGData.get(server);
        if (data.start == null || data.finish == null) {
            src.sendFailure(Component.literal("Coloca primero el Bloque 1 (inicio) y el Bloque 2 (meta)."));
            return 0;
        }
        if (!data.startDim.equals(data.finishDim)) {
            src.sendFailure(Component.literal("El Bloque 1 y el Bloque 2 deben estar en la misma dimensión."));
            return 0;
        }
        int dx = data.finish.getX() - data.start.getX();
        int dz = data.finish.getZ() - data.start.getZ();
        if (dx == 0 && dz == 0) {
            src.sendFailure(Component.literal("El Bloque 1 y el Bloque 2 no pueden estar en la misma columna."));
            return 0;
        }

        alive.clear();
        winners.clear();
        startModes.clear();
        eliminated.clear();
        deathSpots.clear();
        refs.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            GameType gm = p.gameMode.getGameModeForPlayer();
            if (gm == GameType.SURVIVAL || gm == GameType.ADVENTURE) {
                alive.add(p.getUUID());
                startModes.put(p.getUUID(), gm);
            }
        }
        if (alive.isEmpty()) {
            src.sendFailure(Component.literal("No hay jugadores en supervivencia/aventura para jugar."));
            return 0;
        }

        dimId = data.finishDim;
        finish = data.finish;
        axisX = Math.abs(dx) >= Math.abs(dz);
        sign = axisX ? Integer.signum(dx) : Integer.signum(dz);
        timeLeft = seconds * 20;
        running = true;
        startGreen(server);

        final int players = alive.size();
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("¡Luz verde, luz roja! " + players + " jugadores, tiempo: " + format(seconds))
                        .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int stop(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        if (!running && eliminated.isEmpty()) {
            src.sendFailure(Component.literal("No hay ningún juego en curso."));
            return 0;
        }
        running = false;
        alive.clear();
        winners.clear();
        refs.clear();
        reviveAll(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("Juego detenido.").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int revive(CommandSourceStack src) {
        if (eliminated.isEmpty()) {
            src.sendFailure(Component.literal("No hay jugadores eliminados para revivir."));
            return 0;
        }
        reviveAll(src.getServer());
        src.sendSuccess(() -> Component.literal("Jugadores eliminados revividos."), true);
        return 1;
    }

    private static void reviveAll(MinecraftServer server) {
        for (Map.Entry<UUID, GameType> e : eliminated.entrySet()) {
            deathSpots.remove(e.getKey());
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                p.setGameMode(e.getValue());
            } else {
                pendingRevive.put(e.getKey(), e.getValue());
            }
        }
        eliminated.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            GameType gm = pendingRevive.remove(p.getUUID());
            if (gm != null) p.setGameMode(gm);
        }
    }

    static String format(int seconds) {
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 || sb.length() == 0) sb.append(s).append("s");
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ juego

    private static void startGreen(MinecraftServer server) {
        red = false;
        refs.clear();
        RandomSource r = server.overworld().getRandom();
        phaseLeft = 80 + r.nextInt(81); // 4 a 8 s
        playToAll(server, ModRegistry.GREEN_LIGHT.get());
    }

    private static void startRed(MinecraftServer server) {
        red = true;
        refs.clear();
        grace = GRACE_TICKS;
        RandomSource r = server.overworld().getRandom();
        phaseLeft = 60 + r.nextInt(61); // 3 a 6 s
        playToAll(server, ModRegistry.RED_LIGHT.get());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !running) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        timeLeft--;
        phaseLeft--;
        if (grace > 0) grace--;

        for (UUID id : new ArrayList<>(alive)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;

            GameType gm = p.gameMode.getGameModeForPlayer();
            if (gm != GameType.SURVIVAL && gm != GameType.ADVENTURE) {
                alive.remove(id);
                continue;
            }

            // ¿pasó la línea del bloque 2?
            if (p.level().dimension().location().toString().equals(dimId) && hasPassed(p)) {
                alive.remove(id);
                winners.add(id);
                p.playNotifySound(ModRegistry.SUCCESS.get(), SoundSource.MASTER, 1.0F, 1.0F);
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Net.GreenFlashPacket());
                continue;
            }

            if (red && grace == 0) {
                Ref ref = refs.get(id);
                if (ref == null) {
                    refs.put(id, new Ref(p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()));
                } else if (moved(p, ref) || swung(p)) {
                    eliminate(server, p);
                }
            }
        }

        if (alive.isEmpty()) {
            endGame(server, false);
            return;
        }
        if (timeLeft <= 0) {
            endGame(server, true);
            return;
        }
        if (phaseLeft <= 0) {
            if (red) startGreen(server);
            else startRed(server);
        }
    }

    private static boolean hasPassed(ServerPlayer p) {
        double c = axisX ? p.getX() - (finish.getX() + 0.5D) : p.getZ() - (finish.getZ() + 0.5D);
        return c * sign > 0.5D;
    }

    private static boolean moved(ServerPlayer p, Ref r) {
        double dx = p.getX() - r.x();
        double dz = p.getZ() - r.z();
        double dy = p.getY() - r.y();
        if (dx * dx + dz * dz > MOVE_EPS * MOVE_EPS || Math.abs(dy) > MOVE_EPS) return true;
        float dyaw = Math.abs(Mth.wrapDegrees(p.getYRot() - r.yaw()));
        float dpitch = Math.abs(p.getXRot() - r.pitch());
        return dyaw > ROT_EPS || dpitch > ROT_EPS;
    }

    /** Golpear / usar la mano: el servidor marca swinging con swingTime <= 0 en el primer tick del golpe. */
    private static boolean swung(ServerPlayer p) {
        return p.swinging && p.swingTime <= 0;
    }

    private static void eliminate(MinecraftServer server, ServerPlayer p) {
        UUID id = p.getUUID();
        alive.remove(id);
        eliminated.put(id, startModes.getOrDefault(id, GameType.SURVIVAL));
        killPlayer(p);
        playToAll(server, ModRegistry.GUNSHOT.get());
    }

    /** Lo usan otros minijuegos: marca al jugador como eliminado y le hace /kill. */
    public static void eliminateExternal(ServerPlayer p, GameType original) {
        eliminated.put(p.getUUID(), original);
        killPlayer(p);
    }

    public static void reviveEliminated(MinecraftServer server) {
        reviveAll(server);
    }

    /** Equivale a /kill: guarda dónde murió para reaparecer ahí como espectador. */
    static void killPlayer(ServerPlayer p) {
        recordDeathSpot(p);
        p.kill();
    }

    static void recordDeathSpot(ServerPlayer p) {
        deathSpots.put(p.getUUID(), new DeathSpot(p.level().dimension().location().toString(),
                p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot()));
    }

    static boolean isRunning() {
        return running;
    }

    static void registerEliminated(UUID id, GameType originalMode) {
        eliminated.put(id, originalMode);
    }

    static void reviveEveryone(MinecraftServer server) {
        reviveAll(server);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        DeathSpot d = deathSpots.remove(p.getUUID());
        if (d == null) return;
        p.setGameMode(GameType.SPECTATOR);
        ServerLevel level = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(d.dim())));
        if (level != null) {
            p.teleportTo(level, d.x(), d.y(), d.z(), d.yaw(), d.pitch());
        }
    }

    private static void endGame(MinecraftServer server, boolean timedOut) {
        running = false;
        int passed = winners.size();
        if (timedOut) {
            boolean any = false;
            for (UUID id : new ArrayList<>(alive)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                eliminated.put(id, startModes.getOrDefault(id, GameType.SURVIVAL));
                killPlayer(p);
                any = true;
            }
            if (any) playToAll(server, ModRegistry.GUNSHOT.get());
        }
        alive.clear();
        refs.clear();
        server.getPlayerList().broadcastSystemMessage(
                Component.literal((timedOut ? "¡Se acabó el tiempo! " : "¡Juego terminado! ")
                        + "Jugadores que pasaron: " + passed).withStyle(ChatFormatting.GOLD), false);
    }

    private static void playToAll(MinecraftServer server, SoundEvent sound) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.playNotifySound(sound, SoundSource.MASTER, 1.0F, 1.0F);
        }
    }
}
