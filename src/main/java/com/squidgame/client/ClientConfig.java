package com.squidgame.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ajustes de los audífonos (se guardan en config/squidgame_headphones.json). */
public class ClientConfig {
    // Lo que se envía a los demás jugadores
    public float volume = 1.0F;          // 0 - 2
    public boolean loop = false;
    public float fadeIn = 0.0F;          // segundos
    public float fadeOut = 1.0F;         // segundos al detener
    public float startSec = 0.0F;        // empezar en el segundo X
    public float speed = 1.0F;           // 0.5 - 2
    public boolean stopPrevious = true;  // detener el sonido anterior al poner uno nuevo
    public boolean localOnly = false;    // solo lo oyes tú
    public int radius = 0;               // 0 = todos los jugadores
    // Ajustes personales (lo que tú oyes)
    public float personalVolume = 1.0F;  // 0 - 2
    public boolean muteReceived = false; // silenciar los audífonos de todos
    // Menú
    public boolean closeOnPlay = true;
    public boolean showSizes = true;
    public boolean sortByDate = false;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ClientConfig instance = new ClientConfig();

    public static ClientConfig get() {
        return instance;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("squidgame_headphones.json");
    }

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                ClientConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), ClientConfig.class);
                if (c != null) instance = c;
            }
        } catch (Exception e) {
            LogUtils.getLogger().warn("No se pudo leer la config de los audífonos", e);
        }
        instance.sanitize();
    }

    public static void save() {
        try {
            instance.sanitize();
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LogUtils.getLogger().warn("No se pudo guardar la config de los audífonos", e);
        }
    }

    public static void reset() {
        instance = new ClientConfig();
        save();
    }

    private void sanitize() {
        volume = Mth.clamp(volume, 0.0F, 2.0F);
        fadeIn = Mth.clamp(fadeIn, 0.0F, 10.0F);
        fadeOut = Mth.clamp(fadeOut, 0.0F, 10.0F);
        startSec = Mth.clamp(startSec, 0.0F, 300.0F);
        speed = Mth.clamp(speed, 0.5F, 2.0F);
        radius = Mth.clamp(radius, 0, 200);
        personalVolume = Mth.clamp(personalVolume, 0.0F, 2.0F);
    }
}
