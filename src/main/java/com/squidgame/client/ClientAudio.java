package com.squidgame.client;

import com.mojang.logging.LogUtils;
import com.squidgame.Net;
import com.squidgame.SoundPackets;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cliente: sube el audio al servidor y arma/reproduce el que llega de otros jugadores. */
public class ClientAudio {
    private static final long MAX_LOCAL_BYTES = 100L * 1024 * 1024;
    private static final Map<UUID, Incoming> INCOMING = new HashMap<>();

    private static final class Incoming {
        SoundPackets.BeginPacket begin;
        byte[][] chunks;
        int got;
    }

    // ------------------------------------------------------------ recibir (hilo principal)

    public static void onBegin(SoundPackets.BeginPacket m) {
        if (ClientConfig.get().muteReceived) return;
        if (m.chunks <= 0) return;
        if (INCOMING.size() > 4) INCOMING.clear();
        Incoming in = new Incoming();
        in.begin = m;
        in.chunks = new byte[m.chunks][];
        INCOMING.put(m.id, in);
    }

    public static void onChunk(SoundPackets.ChunkPacket m) {
        Incoming in = INCOMING.get(m.id);
        if (in == null || m.index < 0 || m.index >= in.chunks.length) return;
        if (in.chunks[m.index] == null) {
            in.chunks[m.index] = m.data;
            in.got++;
        }
        if (in.got == in.chunks.length) {
            INCOMING.remove(m.id);
            int total = 0;
            for (byte[] c : in.chunks) total += c.length;
            byte[] all = new byte[total];
            int pos = 0;
            for (byte[] c : in.chunks) {
                System.arraycopy(c, 0, all, pos, c.length);
                pos += c.length;
            }
            SoundPackets.BeginPacket b = in.begin;
            AudioEngine.Params p = new AudioEngine.Params();
            p.volume = b.volume;
            p.loop = b.loop;
            p.fadeIn = b.fadeIn;
            p.startSec = b.startSec;
            p.speed = b.speed;
            p.stopPrev = b.stopPrev;
            AudioEngine.play(b.name, all, p);
        }
    }

    public static void onStop(SoundPackets.StopPacket m) {
        AudioEngine.stopAll(m.fadeOut);
    }

    public static void clear() {
        INCOMING.clear();
        AudioEngine.stopAll(0.0F);
    }

    // ------------------------------------------------------------ enviar

    public static void sendFile(Path file) {
        final ClientConfig c = ClientConfig.get();
        final AudioEngine.Params p = new AudioEngine.Params();
        p.volume = c.volume;
        p.loop = c.loop;
        p.fadeIn = c.fadeIn;
        p.startSec = c.startSec;
        p.speed = c.speed;
        p.stopPrev = c.stopPrevious;
        final boolean local = c.localOnly;
        final int radius = c.radius;

        Thread t = new Thread(() -> {
            try {
                if (Files.size(file) > MAX_LOCAL_BYTES) return;
                byte[] data = Files.readAllBytes(file);
                String name = file.getFileName().toString();
                if (local) {
                    AudioEngine.play(name, data, p);
                    return;
                }
                UUID id = UUID.randomUUID();
                int size = SoundPackets.CHUNK_SIZE;
                int chunks = (data.length + size - 1) / size;
                Net.CHANNEL.sendToServer(new SoundPackets.BeginPacket(id, name, data.length, chunks,
                        p.volume, p.loop, p.fadeIn, p.startSec, p.speed, p.stopPrev, radius));
                for (int i = 0; i < chunks; i++) {
                    int from = i * size;
                    int to = Math.min(data.length, from + size);
                    Net.CHANNEL.sendToServer(new SoundPackets.ChunkPacket(id, i, Arrays.copyOfRange(data, from, to)));
                    if (i % 10 == 9) Thread.sleep(2);
                }
            } catch (Throwable e) {
                LogUtils.getLogger().warn("No se pudo enviar el sonido", e);
            }
        }, "SquidGame-Upload");
        t.setDaemon(true);
        t.start();
    }

    public static void sendStop() {
        ClientConfig c = ClientConfig.get();
        if (c.localOnly) {
            AudioEngine.stopAll(c.fadeOut);
        } else {
            Net.CHANNEL.sendToServer(new SoundPackets.StopPacket(c.fadeOut, c.radius));
        }
    }
}
