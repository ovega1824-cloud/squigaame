package com.squidgame.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lo que escribes en el panel del palo morado se guarda en config/squidgame_control.json. */
public class ControlConfig {
    public static class Step {
        public String cmd = "";
        public String delay = "1";
    }

    public String lightTime = "5m";
    public String bridgeTime = "5m";
    public String p1 = "";
    public String p2 = "";
    public String playerName = "";
    public String playerNumber = "1";
    public List<Step> steps = new ArrayList<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ControlConfig instance = new ControlConfig();

    public static ControlConfig get() {
        return instance;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("squidgame_control.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                ControlConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), ControlConfig.class);
                if (c != null) instance = c;
            }
        } catch (Exception e) {
            LogUtils.getLogger().warn("No se pudo leer la config del panel de control", e);
        }
        if (instance.steps == null) instance.steps = new ArrayList<>();
        if (instance.lightTime == null) instance.lightTime = "5m";
        if (instance.bridgeTime == null) instance.bridgeTime = "5m";
    }

    public static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LogUtils.getLogger().warn("No se pudo guardar la config del panel de control", e);
        }
    }
}
