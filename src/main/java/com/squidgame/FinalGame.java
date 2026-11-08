package com.squidgame;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * La final: dos peleadores con 3 vidas. Cada vez que te sacan del área (la forma hecha con bloques F)
 * pierdes una vida; con 0 vidas mueres. A 1 corazón caes al suelo, y otro jugador puede cargarte.
 */
@Mod.EventBusSubscriber(modid = SquidGameMod.MODID)
public class FinalGame {
    private static final int GRACE_TICKS = 40;
    private static final int MAX_BOX = 400;

    private static boolean configured = false;
    private static boolean active = false;
    private static final UUID[] fighters = new UUID[2];
    private static final String[] names = {"Jugador 1", "Jugador 2"};
    private static final int[] lives = {3, 3};

    private static final Set<UUID> downed = new HashSet<>();
    private static final Map<UUID, Vec3> lastInside = new HashMap<>();
    private static final Map<UUID, Integer> grace = new HashMap<>();
    private static final Map<UUID, GameType> startModes = new HashMap<>();

    private static Set<Long> area = new HashSet<>();
    private static double minY = 0;
    private static String dimId = "";
    private static Vec3 center = Vec3.ZERO;

    // ------------------------------------------------------------------ comandos

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sg")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("final")
                        .then(Commands.literal("set")
                                .then(Commands.argument("jugador1", EntityArgument.player())
                                        .then(Commands.argument("jugador2", EntityArgument.player())
                                                .executes(ctx -> setPlayers(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "jugador1"),
                                                        EntityArgument.getPlayer(ctx, "jugador2"))))))
                        .then(Commands.literal("start").executes(ctx -> start(ctx.getSource())))
                        .then(Commands.literal("stop").executes(ctx -> stop(ctx.getSource())))));
    }

    private static int setPlayers(CommandSourceStack src, ServerPlayer a, ServerPlayer b) {
        if (active) {
            src.sendFailure(Component.literal("La final ya empezó. Usa /sg final stop primero."));
            return 0;
        }
        if (a.getUUID().equals(b.getUUID())) {
            src.sendFailure(Component.literal("Elige dos jugadores distintos."));
            return 0;
        }
        fighters[0] = a.getUUID();
        fighters[1] = b.getUUID();
        names[0] = a.getGameProfile().getName();
        names[1] = b.getGameProfile().getName();
        lives[0] = lives[1] = ServerConfig.finalLives();
        configured = true;
        sync(src.getServer());
        src.sendSuccess(() -> Component.literal("Final: " + names[0] + " (jugador 1) vs " + names[1]
                + " (jugador 2), " + lives[0] + " vidas cada uno."), true);
        return 1;
    }

    // ------------------------------------------------------------------ área

    private static boolean buildArea(SGData d, CommandSourceStack src) {
        if (d.finalBlocks.isEmpty()) {
            src.sendFailure(Component.literal("Haz primero una forma con bloques F (el área de la final)."));
            return false;
        }
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int minYb = Integer.MAX_VALUE;
        Set<Long> cells = new HashSet<>();
        for (long l : d.finalBlocks) {
            BlockPos p = BlockPos.of(l);
            cells.add(BlockPos.asLong(p.getX(), 0, p.getZ()));
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
            minZ = Math.min(minZ, p.getZ());
            maxZ = Math.max(maxZ, p.getZ());
            minYb = Math.min(minYb, p.getY());
        }
        int w = maxX - minX + 3;
        int h = maxZ - minZ + 3;
        if (w > MAX_BOX || h > MAX_BOX) {
            src.sendFailure(Component.literal("El área es demasiado grande (máx. " + MAX_BOX + "x" + MAX_BOX + ")."));
            return false;
        }
        // Todo lo que esté encerrado por la forma (sin poder llegar a la orilla) también es área.
        boolean[] outside = new boolean[w * h];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        outside[0] = true;
        queue.add(new int[]{0, 0});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int[] dd : dirs) {
                int nx = c[0] + dd[0];
                int nz = c[1] + dd[1];
                if (nx < 0 || nz < 0 || nx >= w || nz >= h) continue;
                if (outside[nz * w + nx]) continue;
                if (cells.contains(BlockPos.asLong(minX - 1 + nx, 0, minZ - 1 + nz))) continue;
                outside[nz * w + nx] = true;
                queue.add(new int[]{nx, nz});
            }
        }
        Set<Long> result = new HashSet<>(cells);
        double sx = 0, sz = 0;
        int n = 0;
        for (int z = 0; z < h; z++) {
            for (int x = 0; x < w; x++) {
                if (!outside[z * w + x]) {
                    result.add(BlockPos.asLong(minX - 1 + x, 0, minZ - 1 + z));
                    sx += minX - 1 + x + 0.5D;
                    sz += minZ - 1 + z + 0.5D;
                    n++;
                }
            }
        }
        area = result;
        minY = minYb;
        dimId = d.finalDim;
        center = new Vec3(n == 0 ? 0 : sx / n, minYb + 1.0D, n == 0 ? 0 : sz / n);
        return true;
    }

    private static boolean inside(ServerPlayer p) {
        long key = BlockPos.asLong(Mth.floor(p.getX()), 0, Mth.floor(p.getZ()));
        return p.level().dimension().location().toString().equals(dimId)
                && area.contains(key) && p.getY() >= minY - 3.0D;
    }

    // ------------------------------------------------------------------ start / stop

    private static int start(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        if (active) {
            src.sendFailure(Component.literal("La final ya está en curso."));
            return 0;
        }
        if (GameManager.isRunning() || BridgeGame.isRunning()) {
            src.sendFailure(Component.literal("Hay otro juego en curso."));
            return 0;
        }
        if (!configured) {
            src.sendFailure(Component.literal("Primero usa /sg final set <jugador1> <jugador2>."));
            return 0;
        }
        ServerPlayer[] ps = new ServerPlayer[2];
        for (int i = 0; i < 2; i++) {
            ps[i] = server.getPlayerList().getPlayer(fighters[i]);
            if (ps[i] == null) {
                src.sendFailure(Component.literal(names[i] + " no está conectado."));
                return 0;
            }
        }
        if (!buildArea(SGData.get(server), src)) return 0;

        downed.clear();
        lastInside.clear();
        grace.clear();
        startModes.clear();
        lives[0] = lives[1] = ServerConfig.finalLives();
        for (ServerPlayer p : ps) {
            startModes.put(p.getUUID(), p.gameMode.getGameModeForPlayer());
            lastInside.put(p.getUUID(), p.position());
        }
        active = true;
        sync(server);
        final int cells = area.size();
        src.sendSuccess(() -> Component.literal("¡Final iniciada! Área: " + cells + " bloques."), true);
        return 1;
    }

    private static int stop(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        endGame(server);
        GameManager.reviveEliminated(server);
        src.sendSuccess(() -> Component.literal("Final detenida."), true);
        return 1;
    }

    private static void endGame(MinecraftServer server) {
        active = false;
        for (UUID id : new ArrayList<>(downed)) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        downed.clear();
        sync(server);
    }

    // ------------------------------------------------------------------ sincronización

    private static FinalPackets.State buildState(MinecraftServer server) {
        List<UUID> models = new ArrayList<>();
        if (active) {
            for (UUID u : fighters) if (u != null) models.add(u);
        }
        return new FinalPackets.State(active || configured, names[0], names[1], lives[0], lives[1],
                new ArrayList<>(downed), models);
    }

    public static void sync(MinecraftServer server) {
        Net.CHANNEL.send(PacketDistributor.ALL.noArg(), buildState(server));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), buildState(p.server));
        }
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !active) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        boolean changed = false;
        for (int i = 0; i < 2 && active; i++) {
            ServerPlayer p = server.getPlayerList().getPlayer(fighters[i]);
            if (p == null || !p.isAlive()) continue;
            UUID id = p.getUUID();

            // --- tirado al suelo
            float hp = p.getHealth();
            boolean isDown = downed.contains(id);
            if (!isDown && hp <= ServerConfig.finalDownHp()) {
                downed.add(id);
                changed = true;
            } else if (isDown && hp > ServerConfig.finalRecoverHp()) {
                downed.remove(id);
                p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                changed = true;
            }
            if (downed.contains(id) && p.tickCount % 10 == 0) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 6, false, false, false));
            }

            // --- fuera del área
            int g = grace.getOrDefault(id, 0);
            if (g > 0) {
                grace.put(id, g - 1);
                continue;
            }
            if (inside(p)) {
                lastInside.put(id, p.position());
            } else {
                knockOut(server, p, i);
                changed = true;
            }
        }

        if (changed && active) sync(server);
    }

    private static void knockOut(MinecraftServer server, ServerPlayer p, int idx) {
        UUID id = p.getUUID();
        lives[idx] = Math.max(0, lives[idx] - 1);
        if (lives[idx] <= 0) {
            GameType original = startModes.getOrDefault(id, GameType.SURVIVAL);
            endGame(server); // active = false antes del /kill, así no se repite
            GameManager.eliminateExternal(p, original);
            return;
        }
        ServerLevel level = levelOf(server, dimId);
        Vec3 back = lastInside.getOrDefault(id, center);
        if (level != null) {
            p.teleportTo(level, back.x, back.y, back.z, p.getYRot(), p.getXRot());
        }
        p.fallDistance = 0.0F;
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        grace.put(id, GRACE_TICKS);
    }

    private static ServerLevel levelOf(MinecraftServer server, String id) {
        if (id == null || id.isEmpty()) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(id)));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!active || !(event.getEntity() instanceof ServerPlayer p)) return;
        for (int i = 0; i < 2; i++) {
            if (p.getUUID().equals(fighters[i])) {
                lives[i] = 0;
                endGame(p.server);
                return;
            }
        }
    }
}
