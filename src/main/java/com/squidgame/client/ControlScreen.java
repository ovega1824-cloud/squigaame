package com.squidgame.client;

import com.squidgame.ControlPackets;
import com.squidgame.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Panel del palo morado: controla los juegos, la final, los jugadores y secuencias de comandos. */
public class ControlScreen extends Screen {
    private static final int W = 300;
    private static final int PER_PAGE = 5;
    private static final int MAX_STEPS = 30;
    private static final String[] TABS = {"Juegos", "Final", "Jugadores", "Comandos"};

    private static int lastTab = 0;
    private int tab = lastTab;
    private int page = 0;
    private String status = "";

    public ControlScreen() {
        super(Component.literal("Panel de control"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        lastTab = tab;
        ControlConfig.save();
    }

    private int left() {
        return this.width / 2 - W / 2;
    }

    // ------------------------------------------------------------------ construcción

    @Override
    protected void init() {
        int x = left();
        int bw = 72;
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            Button b = Button.builder(Component.literal(TABS[i]), btn -> {
                tab = idx;
                status = "";
                rebuildWidgets();
            }).bounds(x + i * (bw + 4), 18, bw, 20).build();
            b.active = tab != i;
            addRenderableWidget(b);
        }
        switch (tab) {
            case 0 -> initGames();
            case 1 -> initFinal();
            case 2 -> initPlayers();
            default -> initSequence();
        }
    }

    private EditBox box(int x, int y, int w, String value, int max, Consumer<String> onChange) {
        EditBox e = new EditBox(this.font, x, y, w, 20, Component.empty());
        e.setMaxLength(max);
        e.setValue(value == null ? "" : value);
        e.setResponder(onChange);
        addRenderableWidget(e);
        return e;
    }

    private void button(String label, int x, int y, int w, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), b -> action.run())
                .bounds(x, y, w, 20).build());
    }

    private static String withTime(String base, String time) {
        String t = time == null ? "" : time.trim();
        return t.isEmpty() ? base : base + " " + t;
    }

    // --- pestaña: juegos
    private void initGames() {
        ControlConfig c = ControlConfig.get();
        int x = left();
        int y = 62;
        box(x, y, 60, c.lightTime, 12, s -> c.lightTime = s);
        button("Iniciar", x + 66, y, 56, () -> run(withTime("sg j1 start", c.lightTime)));
        button("Detener", x + 124, y, 56, () -> run("sg j1 stop"));
        button("Revivir", x + 182, y, 56, () -> run("sg j1 revive"));

        y = 114;
        box(x, y, 60, c.bridgeTime, 12, s -> c.bridgeTime = s);
        button("Iniciar", x + 66, y, 56, () -> run(withTime("sg puntedecristal start", c.bridgeTime)));
        button("Detener", x + 124, y, 56, () -> run("sg puntedecristal stop"));
        button("Restaurar", x + 182, y, 56, () -> run("sg puntedecristal restore"));
        button("Revivir", x + 240, y, 56, () -> run("sg puntedecristal revive"));
    }

    // --- pestaña: final
    private void initFinal() {
        ControlConfig c = ControlConfig.get();
        int x = left();
        box(x, 62, 140, c.p1, 32, s -> c.p1 = s);
        box(x + 150, 62, 140, c.p2, 32, s -> c.p2 = s);
        button("Poner jugadores", x, 94, 100, () -> run("sg final set " + c.p1.trim() + " " + c.p2.trim()));
        button("Iniciar final", x + 104, 94, 96, () -> run("sg final start"));
        button("Detener final", x + 204, 94, 96, () -> run("sg final stop"));
    }

    // --- pestaña: jugadores
    private void initPlayers() {
        ControlConfig c = ControlConfig.get();
        int x = left();
        box(x, 62, 190, c.playerName, 32, s -> c.playerName = s);
        box(x + 200, 62, 60, c.playerNumber, 3, s -> c.playerNumber = s);
        button("Poner número", x, 94, 148, () -> run("numero " + c.playerNumber.trim() + " " + c.playerName.trim()));
        button("Quitar número", x + 152, 94, 148, () -> run("numero quitar " + c.playerName.trim()));
        button("Ocultar nombre", x, 118, 148, () -> run("nick hide " + c.playerName.trim()));
        button("Mostrar nombre", x + 152, 118, 148, () -> run("nick show " + c.playerName.trim()));
    }

    // --- pestaña: secuencia de comandos
    private void initSequence() {
        ControlConfig c = ControlConfig.get();
        List<ControlConfig.Step> steps = c.steps;
        int pages = Math.max(1, (steps.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        int x = left();
        int y0 = 64;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= steps.size()) break;
            ControlConfig.Step st = steps.get(idx);
            int y = y0 + i * 24;
            box(x, y, 196, st.cmd, 290, s -> st.cmd = s);
            box(x + 200, y, 44, st.delay, 6, s -> st.delay = s);
            button("X", x + 248, y, 20, () -> {
                steps.remove(st);
                rebuildWidgets();
            });
        }
        int by = y0 + PER_PAGE * 24 + 4;
        button("+ Agregar", x, by, 70, () -> {
            if (steps.size() < MAX_STEPS) {
                steps.add(new ControlConfig.Step());
                page = (steps.size() - 1) / PER_PAGE;
                rebuildWidgets();
            }
        });
        button("Ejecutar", x + 74, by, 70, this::runSequence);
        button("Detener", x + 148, by, 70, () -> {
            send(new ArrayList<>(), new ArrayList<>());
            status = "Secuencia detenida.";
        });
        button("<", x + 244, by, 24, () -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        });
        button(">", x + 272, by, 24, () -> {
            if (page < pages - 1) {
                page++;
                rebuildWidgets();
            }
        });
    }

    // ------------------------------------------------------------------ acciones

    private void run(String command) {
        if (command.endsWith(" ") || command.contains("  ")) {
            command = command.trim().replaceAll(" +", " ");
        }
        List<String> cmds = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        cmds.add(command);
        delays.add(0);
        send(cmds, delays);
        status = "Enviado: /" + command;
    }

    private void runSequence() {
        List<String> cmds = new ArrayList<>();
        List<Integer> delays = new ArrayList<>();
        for (ControlConfig.Step st : ControlConfig.get().steps) {
            String c = st.cmd == null ? "" : st.cmd.trim();
            if (c.isEmpty()) continue;
            cmds.add(c);
            delays.add(delayTicks(st.delay));
        }
        if (cmds.isEmpty()) {
            status = "Agrega al menos un comando.";
            return;
        }
        ControlConfig.save();
        send(cmds, delays);
        status = "Secuencia en marcha (" + cmds.size() + " comandos).";
    }

    private static int delayTicks(String text) {
        try {
            float s = Float.parseFloat(text.trim().replace(',', '.'));
            return Math.max(0, Math.min(Math.round(s * 20.0F), 72000 * 24));
        } catch (Exception e) {
            return 0;
        }
    }

    private static void send(List<String> cmds, List<Integer> delays) {
        Net.CHANNEL.sendToServer(new ControlPackets.Sequence(cmds, delays));
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        int x = left();
        g.drawCenteredString(this.font, "Panel de control", this.width / 2, 6, 0xFFFFFF);
        switch (tab) {
            case 0 -> {
                g.drawString(this.font, "Luz verde, luz roja (tiempo: 90, 90s, 2m, 1m30s)", x, 50, 0xFFFFFF, false);
                g.drawString(this.font, "Puente de cristal (tiempo: 90, 90s, 2m, 1m30s)", x, 102, 0xFFFFFF, false);
            }
            case 1 -> {
                g.drawString(this.font, "Jugador 1 (barra roja)", x, 50, 0xFFFFFF, false);
                g.drawString(this.font, "Jugador 2 (barra azul)", x + 150, 50, 0xFFFFFF, false);
                g.drawString(this.font, "Primero pon el área con bloques F y escribe los nombres.", x, 124, 0xAAAAAA, false);
            }
            case 2 -> {
                g.drawString(this.font, "Jugador", x, 50, 0xFFFFFF, false);
                g.drawString(this.font, "Número (1-100)", x + 200, 50, 0xFFFFFF, false);
            }
            default -> {
                g.drawString(this.font, "Comando (sin /)", x, 50, 0xFFFFFF, false);
                g.drawString(this.font, "Espera (s)", x + 200, 50, 0xFFFFFF, false);
                int pages = Math.max(1, (ControlConfig.get().steps.size() + PER_PAGE - 1) / PER_PAGE);
                g.drawString(this.font, (page + 1) + "/" + pages, x + 222, 64 + PER_PAGE * 24 + 10, 0xCCCCCC, false);
                if (ControlConfig.get().steps.isEmpty()) {
                    g.drawString(this.font, "Pulsa \"+ Agregar\". Cada comando espera antes de ejecutarse.", x, 70, 0xAAAAAA, false);
                }
            }
        }
        if (!status.isEmpty()) {
            g.drawString(this.font, status, x, this.height - 20, 0x8CE68C, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
