package com.squidgame.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Menú de los audífonos: lista de canciones, importar y ajustes. */
public class HeadphonesScreen extends Screen {
    private static final int PER_PAGE = 6;

    private boolean settings = false;
    private int page = 0;
    private String status = "Formatos: .mp3 y .wav  |  Arrastra archivos aquí para importarlos";
    private EditBox pathBox;
    private List<Path> files = new ArrayList<>();

    public HeadphonesScreen() {
        super(Component.literal("Audífonos"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        if (settings) initSettings();
        else initSongs();
    }

    @Override
    public void removed() {
        ClientConfig.save();
    }

    // ------------------------------------------------------------------ canciones

    private void initSongs() {
        ClientConfig cfg = ClientConfig.get();
        files = SoundLibrary.list(cfg.sortByDate);
        int pages = Math.max(1, (files.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));

        int w = 240;
        int x = this.width / 2 - w / 2;
        int y = 28;

        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= files.size()) break;
            Path f = files.get(idx);
            String label = f.getFileName().toString();
            if (label.length() > 30) label = label.substring(0, 27) + "...";
            if (cfg.showSizes) label = label + "  (" + SoundLibrary.sizeText(f) + ")";
            addRenderableWidget(Button.builder(Component.literal(label), b -> play(f))
                    .bounds(x, y + i * 22, w, 20).build());
        }

        int ny = y + PER_PAGE * 22 + 4;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            if (page > 0) {
                page--;
                rebuildWidgets();
            }
        }).bounds(x, ny, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            if (page < pages - 1) {
                page++;
                rebuildWidgets();
            }
        }).bounds(x + w - 30, ny, 30, 20).build());

        int ry = ny + 22;
        addRenderableWidget(Button.builder(Component.literal("Abrir carpeta"), b ->
                Util.getPlatform().openFile(SoundLibrary.folder().toFile()))
                .bounds(x, ry, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Detener todo"), b -> ClientAudio.sendStop())
                .bounds(x + 81, ry, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Ajustes"), b -> {
            settings = true;
            rebuildWidgets();
        }).bounds(x + 162, ry, 78, 20).build());

        int iy = ry + 22;
        pathBox = new EditBox(this.font, x, iy, 160, 20, Component.literal("Ruta"));
        pathBox.setMaxLength(600);
        addRenderableWidget(pathBox);
        addRenderableWidget(Button.builder(Component.literal("Importar"), b -> doImport())
                .bounds(x + 164, iy, 76, 20).build());
    }

    private void play(Path f) {
        ClientAudio.sendFile(f);
        if (ClientConfig.get().closeOnPlay) this.onClose();
    }

    private void doImport() {
        String t = pathBox == null ? "" : pathBox.getValue().trim();
        if (t.length() >= 2 && t.startsWith("\"") && t.endsWith("\"")) t = t.substring(1, t.length() - 1);
        if (t.isEmpty()) {
            status = "Pega aquí la ruta de un archivo .mp3 o .wav";
            return;
        }
        try {
            status = SoundLibrary.importFile(Paths.get(t));
        } catch (Exception e) {
            status = "Ruta no válida";
        }
        rebuildWidgets();
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        String last = "";
        for (Path p : paths) last = SoundLibrary.importFile(p);
        if (!last.isEmpty()) status = last;
        if (!settings) rebuildWidgets();
    }

    // ------------------------------------------------------------------ ajustes

    private static String pct(double v) {
        return String.format(Locale.ROOT, "%d%%", Math.round(v * 100));
    }

    private static String secs(double v) {
        return String.format(Locale.ROOT, "%.1f s", v);
    }

    private void initSettings() {
        ClientConfig c = ClientConfig.get();
        int w = 150;
        int x1 = this.width / 2 - 155;
        int x2 = this.width / 2 + 5;
        int y = 28;
        int s = 22;

        addRenderableWidget(new ValueSlider(x1, y, w, 20, "Volumen", 0, 2, c.volume,
                HeadphonesScreen::pct, v -> c.volume = (float) v));
        addRenderableWidget(new ValueSlider(x1, y + s, w, 20, "Vol. personal", 0, 2, c.personalVolume,
                HeadphonesScreen::pct, v -> c.personalVolume = (float) v));
        addRenderableWidget(new ValueSlider(x1, y + 2 * s, w, 20, "Fade in", 0, 10, c.fadeIn,
                HeadphonesScreen::secs, v -> c.fadeIn = (float) v));
        addRenderableWidget(new ValueSlider(x1, y + 3 * s, w, 20, "Fade out", 0, 10, c.fadeOut,
                HeadphonesScreen::secs, v -> c.fadeOut = (float) v));
        addRenderableWidget(new ValueSlider(x1, y + 4 * s, w, 20, "Empezar en", 0, 300, c.startSec,
                v -> Math.round(v) + " s", v -> c.startSec = (float) Math.round(v)));
        addRenderableWidget(new ValueSlider(x1, y + 5 * s, w, 20, "Velocidad", 0.5, 2.0, c.speed,
                v -> String.format(Locale.ROOT, "%.2fx", v), v -> c.speed = (float) v));
        addRenderableWidget(new ValueSlider(x1, y + 6 * s, w, 20, "Radio (0=todos)", 0, 200, c.radius,
                v -> Math.round(v) == 0 ? "todos" : Math.round(v) + " bl.", v -> c.radius = (int) Math.round(v)));

        addRenderableWidget(toggle(x2, y, w, "Repetir", () -> c.loop, v -> c.loop = v));
        addRenderableWidget(toggle(x2, y + s, w, "Cortar el anterior", () -> c.stopPrevious, v -> c.stopPrevious = v));
        addRenderableWidget(toggle(x2, y + 2 * s, w, "Solo yo lo oigo", () -> c.localOnly, v -> c.localOnly = v));
        addRenderableWidget(toggle(x2, y + 3 * s, w, "Silenciar audífonos", () -> c.muteReceived, v -> c.muteReceived = v));
        addRenderableWidget(toggle(x2, y + 4 * s, w, "Cerrar al elegir", () -> c.closeOnPlay, v -> c.closeOnPlay = v));
        addRenderableWidget(toggle(x2, y + 5 * s, w, "Mostrar tamaños", () -> c.showSizes, v -> c.showSizes = v));
        addRenderableWidget(toggle(x2, y + 6 * s, w, "Orden por fecha", () -> c.sortByDate, v -> c.sortByDate = v));

        int by = y + 7 * s + 6;
        addRenderableWidget(Button.builder(Component.literal("Restablecer"), b -> {
            ClientConfig.reset();
            rebuildWidgets();
        }).bounds(this.width / 2 - 155, by, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Volver"), b -> {
            ClientConfig.save();
            settings = false;
            rebuildWidgets();
        }).bounds(this.width / 2 + 5, by, 150, 20).build());
    }

    private static Button toggle(int x, int y, int w, String label, BooleanSupplier get, Consumer<Boolean> set) {
        return Button.builder(toggleText(label, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(toggleText(label, get.getAsBoolean()));
        }).bounds(x, y, w, 20).build();
    }

    private static Component toggleText(String label, boolean on) {
        return Component.literal(label + ": " + (on ? "Sí" : "No"));
    }

    // ------------------------------------------------------------------ dibujo

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, settings ? "Audífonos - Ajustes" : "Audífonos", this.width / 2, 10, 0xFFFFFF);
        if (!settings) {
            int w = 240;
            int x = this.width / 2 - w / 2;
            int ny = 28 + PER_PAGE * 22 + 4;
            int pages = Math.max(1, (files.size() + PER_PAGE - 1) / PER_PAGE);
            g.drawCenteredString(this.font, (page + 1) + " / " + pages, this.width / 2, ny + 6, 0xFFFFFF);
            if (files.isEmpty()) {
                g.drawCenteredString(this.font, "La carpeta 'sound' está vacía", this.width / 2, 70, 0xAAAAAA);
            }
            int sy = ny + 22 + 22 + 24;
            g.drawCenteredString(this.font, status, this.width / 2, sy, 0xCCCCCC);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }
}
