package com.squidgame;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ejecuta una lista de comandos uno tras otro con una espera entre cada uno.
 * Los comandos se ejecutan con los permisos de quien los mandó (debe ser operador).
 */
@Mod.EventBusSubscriber(modid = SquidGameMod.MODID)
public class SequenceRunner {
    private static final class Run {
        final List<String> commands;
        final List<Integer> delays;
        int index = 0;
        int wait;

        Run(List<String> commands, List<Integer> delays) {
            this.commands = commands;
            this.delays = delays;
            this.wait = delays.get(0);
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();

    public static void onPacket(ServerPlayer sender, ControlPackets.Sequence m) {
        if (!sender.hasPermissions(2)) {
            sender.sendSystemMessage(Component.literal("Necesitas ser operador para usar el panel de control."));
            return;
        }
        if (m.commands.isEmpty()) {
            RUNS.remove(sender.getUUID());
            return;
        }
        List<String> cmds = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        for (int i = 0; i < Math.min(m.commands.size(), m.delays.size()); i++) {
            String c = m.commands.get(i).trim();
            if (c.startsWith("/")) c = c.substring(1);
            if (c.isEmpty()) continue;
            cmds.add(c);
            delays.add(m.delays.get(i));
        }
        if (cmds.isEmpty()) return;
        RUNS.put(sender.getUUID(), new Run(cmds, delays));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RUNS.isEmpty()) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (UUID id : new ArrayList<>(RUNS.keySet())) {
            Run r = RUNS.get(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (r == null) continue;
            if (p == null) {
                RUNS.remove(id);
                continue;
            }
            r.wait--;
            while (r.wait <= 0) {
                String cmd = r.commands.get(r.index);
                try {
                    server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), cmd);
                } catch (Exception e) {
                    p.sendSystemMessage(Component.literal("Error en el comando: " + cmd));
                }
                r.index++;
                if (r.index >= r.commands.size()) {
                    RUNS.remove(id);
                    break;
                }
                r.wait = r.delays.get(r.index);
            }
        }
    }
}
