package com.squidgame;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Juego 2: puente de cristal. En cada fila de paneles hay uno resistente y los demás son falsos.
 * Los dos tipos son el mismo bloque: solo el servidor sabe cuál es cuál.
 */
@Mod.EventBusSubscriber(modid = SquidGameMod.MODID)
public class BridgeGame {
    /** Ticks que el jugador queda congelado sobre un cristal falso antes de que se rompa (0.5 s). */
    private static final int FREEZE_TICKS = 10;
    /** Tiempo máximo (ticks) para que un jugador que cae sea eliminado. */
    private static final int FALL_TIMEOUT = 80;
    private static final int MAX_VOLUME = 400_000;

    private static final class Panel {
        final BlockPos origin; // esquina mínima (px=false, pz=false)
        final boolean safe;
        boolean broken;

        Panel(BlockPos origin, boolean safe) {
            this.origin = origin;
            this.safe = safe;
        }

        BlockPos[] cells() {
            return new BlockPos[]{origin, origin.east(), origin.south(), origin.east().south()};
        }
    }

    private static final class Doom {
        final Panel panel;
        int ticks = FREEZE_TICKS;
        final double x, y, z;

        Doom(Panel panel, ServerPlayer p) {
            this.panel = panel;
            this.x = p.getX();
            this.y = p.getY();
            this.z = p.getZ();
        }
    }

    private static final class Fall {
        int ticks = FALL_TIMEOUT;
        final double limitY;

        Fall(double limitY) {
            this.limitY = limitY;
        }
    }

    private static final Map<Long, Panel> byCell = new HashMap<>();
    private static final List<Panel> panels = new ArrayList<>();
    private static final Set<UUID> alive = new HashSet<>();
    private static final Set<UUID> winners = new HashSet<>();
    private static final Map<UUID, GameType> startModes = new HashMap<>();
    private static final Map<UUID, Doom> doomed = new HashMap<>();
    private static final Map<UUID, Fall> falling = new HashMap<>();

    private static boolean running = false;
    private static int timeLeft;
    private static String dimId = "";
    private static BlockPos finish = BlockPos.ZERO;
    private static boolean axisX = true;
    private static int sign = 1;

    public static boolean isRunning() {
        return running;
    }

    // ------------------------------------------------------------------ comandos

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        for (String name : new String[]{"puntedecristal", "puentedecristal"}) {
            event.getDispatcher().register(Commands.literal("sg")
                    .requires(src -> src.hasPermission(2))
                    .then(Commands.literal(name)
                            .then(Commands.literal("start")
                                    .executes(ctx -> start(ctx.getSource(), 300))
                                    .then(Commands.argument("tiempo", StringArgumentType.word())
                                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                    new String[]{"30s", "60s", "90s", "2m", "5m", "10m"}, builder))
                                            .executes(ctx -> {
                                                int secs = GameManager.parseDuration(
                                                        StringArgumentType.getString(ctx, "tiempo"));
                                                if (secs <= 0) {
                                                    ctx.getSource().sendFailure(Component.literal(
                                                            "Tiempo no válido. Ejemplos: 90, 90s, 2m, 1m30s, 1h"));
                                                    return 0;
                                                }
                                                return start(ctx.getSource(), secs);
                                            })))
                            .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))
                            .then(Commands.literal("restore").executes(ctx -> restore(ctx.getSource())))
                            .then(Commands.literal("revive").executes(ctx -> revive(ctx.getSource())))));
        }
    }

    private static ServerLevel levelOf(MinecraftServer server, String id) {
        if (id == null || id.isEmpty()) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(id)));
    }

    private static int start(CommandSourceStack src, int seconds) {
        MinecraftServer server = src.getServer();
        if (running || GameManager.isRunning()) {
            src.sendFailure(Component.literal("Ya hay un juego en curso."));
            return 0;
        }
        SGData d = SGData.get(server);
        if (d.bridgeA == null || d.bridgeB == null) {
            src.sendFailure(Component.literal(
                    "Marca los dos puntos del puente con el palo verde (clic derecho en un bloque y luego en otro)."));
            return 0;
        }
        if (d.start == null || d.finish == null) {
            src.sendFailure(Component.literal("Coloca primero el Bloque 1 (inicio) y el Bloque 2 (meta)."));
            return 0;
        }
        if (!d.bridgeDim.equals(d.startDim) || !d.bridgeDim.equals(d.finishDim)) {
            src.sendFailure(Component.literal("El puente y los bloques 1 y 2 deben estar en la misma dimensión."));
            return 0;
        }
        int dx = d.finish.getX() - d.start.getX();
        int dz = d.finish.getZ() - d.start.getZ();
        if (dx == 0 && dz == 0) {
            src.sendFailure(Component.literal("El Bloque 1 y el Bloque 2 no pueden estar en la misma columna."));
            return 0;
        }
        ServerLevel level = levelOf(server, d.bridgeDim);
        if (level == null) {
            src.sendFailure(Component.literal("No se encontró la dimensión del puente."));
            return 0;
        }

        // Si quedó roto de una partida anterior, se arregla antes de empezar.
        restorePanels(server);

        axisX = Math.abs(dx) >= Math.abs(dz);
        sign = axisX ? Integer.signum(dx) : Integer.signum(dz);
        finish = d.finish;

        int minX = Math.min(d.bridgeA.getX(), d.bridgeB.getX()) - 1;
        int maxX = Math.max(d.bridgeA.getX(), d.bridgeB.getX()) + 1;
        int minZ = Math.min(d.bridgeA.getZ(), d.bridgeB.getZ()) - 1;
        int maxZ = Math.max(d.bridgeA.getZ(), d.bridgeB.getZ()) + 1;
        int minY = Math.min(d.bridgeA.getY(), d.bridgeB.getY()) - 2;
        int maxY = Math.max(d.bridgeA.getY(), d.bridgeB.getY()) + 2;
        long volume = (long) (maxX - minX + 1) * (maxZ - minZ + 1) * (maxY - minY + 1);
        if (volume > MAX_VOLUME) {
            src.sendFailure(Component.literal("La zona entre los dos puntos es demasiado grande."));
            return 0;
        }

        List<BlockPos> origins = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos o = new BlockPos(x, y, z);
                    if (isPanelAt(level, o)) origins.add(o);
                }
            }
        }
        if (origins.isEmpty()) {
            src.sendFailure(Component.literal("No encontré paneles de cristal del puente entre los dos puntos."));
            return 0;
        }

        // Agrupar en filas a lo largo del puente; en cada fila uno es resistente y los demás falsos.
        int minCoord = Integer.MAX_VALUE;
        for (BlockPos o : origins) minCoord = Math.min(minCoord, axisX ? o.getX() : o.getZ());
        Map<Integer, List<BlockPos>> rows = new TreeMap<>();
        for (BlockPos o : origins) {
            int row = ((axisX ? o.getX() : o.getZ()) - minCoord) / 2;
            rows.computeIfAbsent(row, k -> new ArrayList<>()).add(o);
        }
        panels.clear();
        byCell.clear();
        RandomSource rnd = level.getRandom();
        for (List<BlockPos> row : rows.values()) {
            int safeIdx = rnd.nextInt(row.size());
            for (int i = 0; i < row.size(); i++) {
                Panel pn = new Panel(row.get(i), i == safeIdx);
                panels.add(pn);
                for (BlockPos c : pn.cells()) byCell.put(c.asLong(), pn);
            }
        }

        alive.clear();
        winners.clear();
        startModes.clear();
        doomed.clear();
        falling.clear();
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

        dimId = d.bridgeDim;
        timeLeft = seconds * 20;
        running = true;

        final int players = alive.size();
        final int total = panels.size();
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("¡Puente de cristal! " + players + " jugadores, tiempo: " + GameManager.format(seconds))
                        .withStyle(ChatFormatting.GOLD), false);
        src.sendSuccess(() -> Component.literal("Puente listo: " + total + " paneles."), false);
        return 1;
    }

    private static int stop(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        running = false;
        for (UUID id : new ArrayList<>(doomed.keySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        alive.clear();
        winners.clear();
        doomed.clear();
        falling.clear();
        GameManager.reviveEliminated(server);
        restorePanels(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("Puente de cristal detenido.").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int restore(CommandSourceStack src) {
        if (running) {
            src.sendFailure(Component.literal("El juego está en curso. Usa /sg puntedecristal stop."));
            return 0;
        }
        if (panels.isEmpty()) {
            src.sendFailure(Component.literal("No hay un puente anterior que restaurar."));
            return 0;
        }
        restorePanels(src.getServer());
        src.sendSuccess(() -> Component.literal("Puente restaurado."), true);
        return 1;
    }

    private static int revive(CommandSourceStack src) {
        GameManager.reviveEliminated(src.getServer());
        src.sendSuccess(() -> Component.literal("Jugadores eliminados revividos."), true);
        return 1;
    }

    // ------------------------------------------------------------------ paneles

    private static boolean cellOk(ServerLevel level, BlockPos p, boolean px, boolean pz) {
        BlockState s = level.getBlockState(p);
        return s.is(ModRegistry.BRIDGE_GLASS.get())
                && s.getValue(BridgeGlassBlock.PX) == px
                && s.getValue(BridgeGlassBlock.PZ) == pz;
    }

    private static boolean isPanelAt(ServerLevel level, BlockPos o) {
        BlockState s = level.getBlockState(o);
        if (!s.is(ModRegistry.BRIDGE_GLASS.get())) return false;
        return cellOk(level, o, false, false)
                && cellOk(level, o.east(), true, false)
                && cellOk(level, o.south(), false, true)
                && cellOk(level, o.east().south(), true, true);
    }

    private static void restorePanels(MinecraftServer server) {
        ServerLevel level = levelOf(server, dimId);
        if (level == null || panels.isEmpty()) return;
        for (Panel pn : panels) {
            pn.broken = false;
            BlockPos[] cells = pn.cells();
            for (int i = 0; i < 4; i++) {
                BlockPos c = cells[i];
                BlockState cur = level.getBlockState(c);
                if (cur.isAir() || cur.is(ModRegistry.BRIDGE_GLASS.get())) {
                    BlockState st = ModRegistry.BRIDGE_GLASS.get().defaultBlockState()
                            .setValue(BridgeGlassBlock.PX, i == 1 || i == 3)
                            .setValue(BridgeGlassBlock.PZ, i == 2 || i == 3);
                    level.setBlock(c, st, 3);
                }
            }
        }
    }

    private static void breakPanel(ServerLevel level, Panel pn) {
        if (pn.broken) return;
        pn.broken = true;
        for (BlockPos c : pn.cells()) {
            level.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
        }
        double cx = pn.origin.getX() + 1.0D;
        double cy = pn.origin.getY() + 0.5D;
        double cz = pn.origin.getZ() + 1.0D;
        level.sendParticles(ParticleTypes.CLOUD, cx, cy, cz, 30, 0.9D, 0.2D, 0.9D, 0.05D);
        level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 12, 0.9D, 0.2D, 0.9D, 0.05D);
        level.playSound(null, cx, cy, cz, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static boolean onPanel(ServerPlayer p, Panel pn) {
        double ox = pn.origin.getX();
        double oy = pn.origin.getY();
        double oz = pn.origin.getZ();
        return p.getX() >= ox && p.getX() < ox + 2.0D && p.getZ() >= oz && p.getZ() < oz + 2.0D
                && p.getY() >= oy + 0.3D && p.getY() <= oy + 2.0D;
    }

    private static boolean overBridge(ServerPlayer p) {
        for (int k = 0; k <= 3; k++) {
            Panel pn = byCell.get(BlockPos.containing(p.getX(), p.getY() - 0.01D - k, p.getZ()).asLong());
            if (pn != null && !pn.broken) return true;
        }
        return false;
    }

    private static boolean hasPassed(ServerPlayer p) {
        double c = axisX ? p.getX() - (finish.getX() + 0.5D) : p.getZ() - (finish.getZ() + 0.5D);
        return c * sign > 0.5D;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!running && doomed.isEmpty() && falling.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = levelOf(server, dimId);
        if (level == null) return;

        // 1) jugadores que ya cayeron: se eliminan al bajar un poco
        for (UUID id : new ArrayList<>(falling.keySet())) {
            Fall f = falling.get(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (f == null) continue;
            if (p == null || !p.isAlive()) {
                falling.remove(id);
                continue;
            }
            f.ticks--;
            if (p.getY() < f.limitY || f.ticks <= 0) {
                falling.remove(id);
                eliminate(p);
            }
        }

        // 2) jugadores congelados sobre un cristal falso
        for (UUID id : new ArrayList<>(doomed.keySet())) {
            Doom d = doomed.get(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (d == null) continue;
            if (p == null || !p.isAlive()) {
                doomed.remove(id);
                continue;
            }
            d.ticks--;
            if (d.ticks > 0) {
                double dx = p.getX() - d.x;
                double dz = p.getZ() - d.z;
                if (p.getY() > d.y + 0.02D || dx * dx + dz * dz > 0.0025D) {
                    p.connection.teleport(d.x, d.y, d.z, p.getYRot(), p.getXRot());
                }
            } else {
                doomed.remove(id);
                breakPanel(level, d.panel);
                p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                falling.put(id, new Fall(d.y - 2.0D));
                for (UUID oid : new ArrayList<>(alive)) {
                    if (oid.equals(id) || falling.containsKey(oid)) continue;
                    ServerPlayer o = server.getPlayerList().getPlayer(oid);
                    if (o != null && onPanel(o, d.panel)) {
                        doomed.remove(oid);
                        o.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                        falling.put(oid, new Fall(o.getY() - 2.0D));
                    }
                }
            }
        }

        if (!running) return;
        timeLeft--;

        // 3) jugadores en juego
        for (UUID id : new ArrayList<>(alive)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            GameType gm = p.gameMode.getGameModeForPlayer();
            if (gm != GameType.SURVIVAL && gm != GameType.ADVENTURE) {
                alive.remove(id);
                continue;
            }
            if (doomed.containsKey(id) || falling.containsKey(id)) continue;

            if (p.level().dimension().location().toString().equals(dimId) && hasPassed(p)) {
                alive.remove(id);
                winners.add(id);
                p.playNotifySound(ModRegistry.SUCCESS.get(), SoundSource.MASTER, 1.0F, 1.0F);
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Net.GreenFlashPacket());
                continue;
            }
            checkStep(p);
        }

        if (alive.isEmpty()) {
            endGame(server, false);
        } else if (timeLeft <= 0) {
            timeout(server, level);
        }
    }

    /** ¿Se paró sobre un cristal falso? Entonces se congela y el cristal se rompe enseguida. */
    private static void checkStep(ServerPlayer p) {
        BlockPos cell = BlockPos.containing(p.getX(), p.getY() - 0.01D, p.getZ());
        Panel pn = byCell.get(cell.asLong());
        if (pn == null || pn.broken || pn.safe) return;
        if (Math.abs(p.getY() - (cell.getY() + 0.5D)) > 0.15D) return;
        doomed.put(p.getUUID(), new Doom(pn, p));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FREEZE_TICKS + 20, 255, false, false, false));
    }

    private static void eliminate(ServerPlayer p) {
        UUID id = p.getUUID();
        alive.remove(id);
        GameManager.eliminateExternal(p, startModes.getOrDefault(id, GameType.SURVIVAL));
    }

    /** Se acabó el tiempo: todos los cristales se rompen y los que no llegaron a la meta mueren. */
    private static void timeout(MinecraftServer server, ServerLevel level) {
        Set<UUID> over = new HashSet<>();
        for (UUID id : alive) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null && overBridge(p)) over.add(id);
        }
        for (Panel pn : panels) breakPanel(level, pn);
        for (UUID id : new ArrayList<>(alive)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) continue;
            if (falling.containsKey(id)) continue;
            doomed.remove(id);
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            if (over.contains(id)) {
                falling.put(id, new Fall(p.getY() - 2.0D));
            } else {
                eliminate(p);
            }
        }
        endGame(server, true);
    }

    private static void endGame(MinecraftServer server, boolean timedOut) {
        running = false;
        server.getPlayerList().broadcastSystemMessage(
                Component.literal((timedOut ? "¡Se acabó el tiempo! " : "¡Juego terminado! ")
                        + "Jugadores que llegaron: " + winners.size()).withStyle(ChatFormatting.GOLD), false);
        alive.clear();
    }
}
