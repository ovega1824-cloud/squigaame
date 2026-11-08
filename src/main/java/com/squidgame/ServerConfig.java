package com.squidgame;

import net.minecraftforge.common.ForgeConfigSpec;

/** Configuración del servidor (archivo serverconfig/squidgame-server.toml del mundo). */
public class ServerConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ONLY_OPS;
    public static final ForgeConfigSpec.IntValue MAX_SIZE_MB;
    public static final ForgeConfigSpec.IntValue FINAL_LIVES;
    public static final ForgeConfigSpec.DoubleValue FINAL_DOWN_HP;
    public static final ForgeConfigSpec.DoubleValue FINAL_RECOVER_HP;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("headphones");
        ONLY_OPS = b.comment("Si es true, solo los operadores pueden usar los audífonos para todos.")
                .define("onlyOps", false);
        MAX_SIZE_MB = b.comment("Tamaño máximo (MB) de un archivo de audio que se puede enviar.")
                .defineInRange("maxFileSizeMb", 20, 1, 100);
        b.pop();
        b.push("final");
        FINAL_LIVES = b.comment("Vidas de cada peleador.").defineInRange("lives", 3, 1, 3);
        FINAL_DOWN_HP = b.comment("Con esta vida (o menos) el jugador cae al suelo. 2.0 = 1 corazón.")
                .defineInRange("downHealth", 2.0D, 0.5D, 20.0D);
        FINAL_RECOVER_HP = b.comment("Con MÁS de esta vida el jugador se vuelve a parar. 6.0 = 3 corazones.")
                .defineInRange("recoverHealth", 6.0D, 1.0D, 20.0D);
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

    public static int finalLives() {
        try {
            return FINAL_LIVES.get();
        } catch (Exception e) {
            return 3;
        }
    }

    public static float finalDownHp() {
        try {
            return FINAL_DOWN_HP.get().floatValue();
        } catch (Exception e) {
            return 2.0F;
        }
    }

    public static float finalRecoverHp() {
        try {
            return FINAL_RECOVER_HP.get().floatValue();
        } catch (Exception e) {
            return 6.0F;
        }
    }
}
