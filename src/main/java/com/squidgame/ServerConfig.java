package com.squidgame;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración del servidor (archivo serverconfig/squidgame-server.toml del mundo). */
public class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ONLY_OPS;
    public static final ForgeConfigSpec.IntValue MAX_SIZE_MB;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("headphones");
        ONLY_OPS = b.comment("Si es true, solo los operadores pueden usar los audífonos para todos.")
                .define("onlyOps", false);
        MAX_SIZE_MB = b.comment("Tamaño máximo (MB) de un archivo de audio que se puede enviar.")
                .defineInRange("maxFileSizeMb", 20, 1, 100);
        b.pop();
        SPEC = b.build();
    }

    public static boolean onlyOps() {
        try {
            return ONLY_OPS.get();
        } catch (Exception e) {
            return false;
        }
    }

    public static int maxMb() {
        try {
            return MAX_SIZE_MB.get();
        } catch (Exception e) {
            return 20;
        }
    }
}
