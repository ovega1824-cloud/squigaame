package com.squidgame.client;

import com.mojang.logging.LogUtils;
import net.minecraft.util.Mth;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Reproduce el audio recibido (sin mensajes ni nada en pantalla). */
public class AudioEngine {
    public static final class Params {
        public float volume = 1.0F;
        public boolean loop;
        public float fadeIn;
        public float startSec;
        public float speed = 1.0F;
        public boolean stopPrev = true;
    }

    private static final List<Playback> ACTIVE = new CopyOnWriteArrayList<>();

    public static void play(String name, byte[] data, Params p) {
        ClientConfig cfg = ClientConfig.get();
        if (cfg.muteReceived) return;
        if (p.stopPrev) stopAll(0.15F);
        Playback pb = new Playback(name, data, p, cfg.personalVolume);
        ACTIVE.add(pb);
        Thread t = new Thread(pb, "SquidGame-Audio");
        t.setDaemon(true);
        t.start();
    }

    public static void stopAll(float fadeSeconds) {
        for (Playback pb : ACTIVE) pb.requestStop(fadeSeconds);
    }

    private static final class Playback implements Runnable, Decoders.PcmSink {
        private final String name;
        private final byte[] data;
        private final Params p;
        private final float personal;

        private volatile boolean stopped = false;
        private volatile boolean fading = false;
        private volatile float fadeOutSec = 0.0F;
        private volatile SourceDataLine line;

        private long played = 0L;
        private long fadeStart = -1L;
        private long fadeFrames = 1L;
        private int skipLeft = -1;
        private boolean firstPass = true;
        private boolean anyAudio = false;
        private byte[] bytes = new byte[0];

        Playback(String name, byte[] data, Params p, float personal) {
            this.name = name;
            this.data = data;
            this.p = p;
            this.personal = personal;
        }

        void requestStop(float fade) {
            if (fade <= 0.01F) {
                stopped = true;
                SourceDataLine l = line;
                if (l != null) {
                    try {
                        l.stop();
                        l.flush();
                    } catch (Throwable ignored) {
                    }
                }
            } else {
                fadeOutSec = fade;
                fading = true;
            }
        }

        @Override
        public void run() {
            try {
                do {
                    anyAudio = false;
                    skipLeft = -1;
                    Decoders.decode(data, this);
                    firstPass = false;
                } while (p.loop && !stopped && anyAudio);
                SourceDataLine l = line;
                if (l != null && !stopped) l.drain();
            } catch (Throwable t) {
                LogUtils.getLogger().warn("No se pudo reproducir '{}'", name, t);
            } finally {
                SourceDataLine l = line;
                if (l != null) {
                    try {
                        l.stop();
                        l.close();
                    } catch (Throwable ignored) {
                    }
                }
                ACTIVE.remove(this);
            }
        }

        @Override
        public boolean accept(short[] pcm, int len, int rate, int ch) {
            if (stopped) return false;
            if (ch < 1 || ch > 2 || len <= 0) return !stopped;
            anyAudio = true;

            try {
                if (line == null) {
                    float outRate = Mth.clamp(rate * p.speed, 8000.0F, 96000.0F);
                    AudioFormat fmt = new AudioFormat(outRate, 16, ch, true, false);
                    SourceDataLine l = AudioSystem.getSourceDataLine(fmt);
                    int buffer = ((int) (outRate * 0.25F)) * ch * 2;
                    l.open(fmt, buffer);
                    l.start();
                    line = l;
                }
            } catch (Throwable t) {
                LogUtils.getLogger().warn("No se pudo abrir la salida de audio", t);
                return false;
            }

            if (skipLeft < 0) {
                skipLeft = firstPass ? ((int) (p.startSec * rate)) * ch : 0;
            }
            int start = 0;
            if (skipLeft > 0) {
                int s = Math.min(skipLeft, len);
                skipLeft -= s;
                start = s;
                if (start >= len) return true;
            }

            if (fading && fadeStart < 0) {
                fadeStart = played;
                fadeFrames = Math.max(1L, (long) (fadeOutSec * rate));
            }

            int n = len - start;
            if (bytes.length < n * 2) bytes = new byte[n * 2];
            float base = p.volume * personal;
            float fadeInFrames = p.fadeIn * rate;
            int outN = n;
            for (int i = 0; i < n; i++) {
                long frame = played + i / ch;
                float g = base;
                if (fadeInFrames > 0 && frame < fadeInFrames) g *= frame / fadeInFrames;
                if (fadeStart >= 0) {
                    g *= 1.0F - (float) (frame - fadeStart) / (float) fadeFrames;
                    if (g <= 0.0F) {
                        outN = i - (i % ch);
                        stopped = true;
                        break;
                    }
                }
                int v = Math.round(pcm[start + i] * g);
                if (v > 32767) v = 32767;
                if (v < -32768) v = -32768;
                bytes[2 * i] = (byte) v;
                bytes[2 * i + 1] = (byte) (v >> 8);
            }
            played += n / ch;

            if (outN > 0) {
                SourceDataLine l = line;
                if (l != null) l.write(bytes, 0, outN * 2);
            }
            return !stopped;
        }
    }
}
