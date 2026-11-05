package com.squidgame.client;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;

/** Convierte MP3 y WAV a muestras PCM de 16 bits. */
public class Decoders {
    public interface PcmSink {
        /** Devuelve false para parar de decodificar. */
        boolean accept(short[] pcm, int len, int rate, int channels);
    }

    public static void decode(byte[] data, PcmSink sink) throws Exception {
        if (data.length > 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F') {
            decodeWav(data, sink);
        } else if (data.length > 12 && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p') {
            throw new UnsupportedOperationException("mp4/aac");
        } else {
            decodeMp3(data, sink);
        }
    }

    private static void decodeMp3(byte[] data, PcmSink sink) throws Exception {
        Bitstream bs = new Bitstream(new ByteArrayInputStream(data));
        Decoder dec = new Decoder();
        try {
            Header h;
            while ((h = bs.readFrame()) != null) {
                SampleBuffer out = (SampleBuffer) dec.decodeFrame(h, bs);
                boolean go = sink.accept(out.getBuffer(), out.getBufferLength(),
                        out.getSampleFrequency(), out.getChannelCount());
                bs.closeFrame();
                if (!go) break;
            }
        } finally {
            try {
                bs.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void decodeWav(byte[] data, PcmSink sink) throws Exception {
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new ByteArrayInputStream(data)))) {
            AudioFormat f = raw.getFormat();
            int ch = f.getChannels();
            int rate = (int) f.getSampleRate();
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, f.getSampleRate(), 16,
                    ch, ch * 2, f.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, raw)) {
                byte[] buf = new byte[4096 * ch * 2];
                short[] out = new short[4096 * ch];
                int n;
                while ((n = pcm.read(buf, 0, buf.length)) > 0) {
                    int samples = n / 2;
                    for (int i = 0; i < samples; i++) {
                        out[i] = (short) ((buf[2 * i] & 0xFF) | (buf[2 * i + 1] << 8));
                    }
                    if (!sink.accept(out, samples, rate, ch)) return;
                }
            }
        }
    }
}
