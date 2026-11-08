package com.squidgame;

import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Guarda dónde están el bloque 1 (inicio) y el bloque 2 (meta). */
public class SGData extends SavedData {
    public BlockPos start;
    public String startDim;
    public BlockPos finish;
    public String finishDim;
    public BlockPos bridgeA;
    public BlockPos bridgeB;
    public String bridgeDim;
    /** Bloques F que forman el área de la final (posiciones empaquetadas). */
    public final Set<Long> finalBlocks = new HashSet<>();
    public String finalDim;
    /** Número (001-100) que se ve bajo el nombre de cada jugador. */
    public final Map<UUID, Integer> numbers = new HashMap<>();
    /** Jugadores con el nombre oculto (/nick hide). */
    public final Set<UUID> hiddenNames = new HashSet<>();
    /** Para apagar el mensaje de muerte de vanilla solo una vez por mundo. */
    public boolean deathRuleApplied = false;

    public static SGData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SGData::load, SGData::new, "squidgame_lines");
    }

    public void set(int number, BlockPos pos, String dim) {
        if (number == 1) {
            start = pos.immutable();
            startDim = dim;
        } else {
            finish = pos.immutable();
            finishDim = dim;
        }
        setDirty();
    }

    public void setBridgePoint(int index, BlockPos pos, String dim) {
        if (bridgeDim != null && !bridgeDim.equals(dim)) {
            bridgeA = null;
            bridgeB = null;
        }
        bridgeDim = dim;
        if (index == 1) bridgeA = pos.immutable();
        else bridgeB = pos.immutable();
        setDirty();
    }

    public void addFinalBlock(BlockPos pos, String dim) {
        if (finalDim != null && !finalDim.equals(dim)) finalBlocks.clear();
        finalDim = dim;
        finalBlocks.add(pos.asLong());
        setDirty();
    }

    public void removeFinalBlock(BlockPos pos) {
        if (finalBlocks.remove(pos.asLong())) setDirty();
    }

    public void clearIf(int number, BlockPos pos) {
        if (number == 1 && pos.equals(start)) {
            start = null;
            startDim = null;
            setDirty();
        } else if (number == 2 && pos.equals(finish)) {
            finish = null;
            finishDim = null;
            setDirty();
        }
    }

    public static SGData load(CompoundTag tag) {
        SGData d = new SGData();
        if (tag.contains("start")) {
            d.start = BlockPos.of(tag.getLong("start"));
            d.startDim = tag.getString("startDim");
        }
        if (tag.contains("finish")) {
            d.finish = BlockPos.of(tag.getLong("finish"));
            d.finishDim = tag.getString("finishDim");
        }
        if (tag.contains("bridgeA")) d.bridgeA = BlockPos.of(tag.getLong("bridgeA"));
        if (tag.contains("bridgeB")) d.bridgeB = BlockPos.of(tag.getLong("bridgeB"));
        if (tag.contains("bridgeDim")) d.bridgeDim = tag.getString("bridgeDim");
        if (tag.contains("finalDim")) d.finalDim = tag.getString("finalDim");
        for (long l : tag.getLongArray("finalBlocks")) d.finalBlocks.add(l);
        CompoundTag nums = tag.getCompound("numbers");
        for (String k : nums.getAllKeys()) {
            try {
                d.numbers.put(UUID.fromString(k), nums.getInt(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (Tag t : tag.getList("hiddenNames", Tag.TAG_STRING)) {
            try {
                d.hiddenNames.add(UUID.fromString(t.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        d.deathRuleApplied = tag.getBoolean("deathRuleApplied");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (start != null) {
            tag.putLong("start", start.asLong());
            tag.putString("startDim", startDim);
        }
        if (finish != null) {
            tag.putLong("finish", finish.asLong());
            tag.putString("finishDim", finishDim);
        }
        if (bridgeA != null) tag.putLong("bridgeA", bridgeA.asLong());
        if (bridgeB != null) tag.putLong("bridgeB", bridgeB.asLong());
        if (bridgeDim != null) tag.putString("bridgeDim", bridgeDim);
        if (finalDim != null) tag.putString("finalDim", finalDim);
        long[] arr = new long[finalBlocks.size()];
        int i = 0;
        for (Long l : finalBlocks) arr[i++] = l;
        tag.putLongArray("finalBlocks", arr);
        CompoundTag nums = new CompoundTag();
        for (Map.Entry<UUID, Integer> e : numbers.entrySet()) nums.putInt(e.getKey().toString(), e.getValue());
        tag.put("numbers", nums);
        ListTag hid = new ListTag();
        for (UUID u : hiddenNames) hid.add(StringTag.valueOf(u.toString()));
        tag.put("hiddenNames", hid);
        tag.putBoolean("deathRuleApplied", deathRuleApplied);
        return tag;
    }
}
