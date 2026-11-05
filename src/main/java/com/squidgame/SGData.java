package com.squidgame;

import net.minecraft.core.BlockPos;
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
        return tag;
    }
}
