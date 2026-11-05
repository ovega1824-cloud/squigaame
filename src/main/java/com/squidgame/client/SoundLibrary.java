package com.squidgame.client;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** La carpeta .minecraft/sound con los audios que se pueden poner con los audífonos. */
public class SoundLibrary {
    public static final String FOLDER = "sound";

    public static Path folder() {
        return FMLPaths.GAMEDIR.get().resolve(FOLDER);
    }

    public static void ensureFolder() {
        try {
            Files.createDirectories(folder());
        } catch (IOException ignored) {
        }
    }

    public static boolean isSupported(String fileName) {
        String n = fileName.toLowerCase(Locale.ROOT);
        return n.endsWith(".mp3") || n.endsWith(".wav");
    }

    public static List<Path> list(boolean byDate) {
        ensureFolder();
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(folder())) {
            s.filter(Files::isRegularFile)
                    .filter(p -> isSupported(p.getFileName().toString()))
                    .forEach(out::add);
        } catch (IOException ignored) {
        }
        if (byDate) {
            out.sort(Comparator.comparingLong((Path p) -> {
                try {
                    return Files.getLastModifiedTime(p).toMillis();
                } catch (IOException e) {
                    return 0L;
                }
            }).reversed());
        } else {
            out.sort(Comparator.comparing((Path p) -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    public static String sizeText(Path p) {
        try {
            long b = Files.size(p);
            if (b >= 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", b / 1048576.0);
            return Math.max(1, b / 1024) + " KB";
        } catch (IOException e) {
            return "?";
        }
    }

    /** Copia un archivo a la carpeta sound. Devuelve un mensaje para mostrar en el menú. */
    public static String importFile(Path src) {
        try {
            if (!Files.isRegularFile(src)) return "No se encontró el archivo";
            String name = src.getFileName().toString();
            if (!isSupported(name)) {
                return "Formato no compatible (por ahora .mp3 y .wav)";
            }
            ensureFolder();
            Path dst = folder().resolve(name);
            if (dst.equals(src)) return "Ese archivo ya está en la carpeta";
            int i = 1;
            String base = name.substring(0, name.lastIndexOf('.'));
            String ext = name.substring(name.lastIndexOf('.'));
            while (Files.exists(dst)) {
                dst = folder().resolve(base + " (" + i + ")" + ext);
                i++;
            }
            Files.copy(src, dst);
            return "Importado: " + dst.getFileName();
        } catch (Exception e) {
            return "Error al importar";
        }
    }
}
