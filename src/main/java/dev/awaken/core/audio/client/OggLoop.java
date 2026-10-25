package dev.awaken.core.audio.client;

import dev.awaken.Awaken;
import it.unimi.dsi.fastutil.floats.FloatConsumer;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.resources.Identifier;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * One looping Ogg Vorbis file decoded in Java into a FIFO of stereo frames (mono is doubled), for the mixers that play
 * several files as one stream ({@link StemMixer}, the world's air bed). The encoded bytes stay in memory and every lap
 * re-reads them. Two kinds of loop:
 * <ul>
 *   <li><b>exact</b> ({@code lapFrames > 0}): every lap lasts exactly that many frames: a decoder that ends early is
 *   padded with silence, one that runs long is cut. Layered stems use this so they never drift apart;</li>
 *   <li><b>natural</b> ({@code lapFrames <= 0}): the file loops at its own length. Air beds use this: layers of
 *   different lengths drift against each other on purpose, so their mix never repeats.</li>
 * </ul>
 * Sound thread only, apart from the constructor.
 */
public final class OggLoop implements FloatConsumer {
    public static final int SR = 44100;

    final byte[] data;
    final long lapFrames;
    final Identifier path;
    private JOrbisAudioStream dec;
    private int channels;
    float[] buf;
    int start, end;
    private long lap;
    private boolean eof, odd;
    private float pendingL;

    /** Opens the decoder; throws when the file isn't 44.1 kHz mono or stereo. {@code reserve}: frames to buffer ahead. */
    public OggLoop(byte[] data, long lapFrames, Identifier path, int reserve) throws IOException {
        this.data = data;
        this.lapFrames = lapFrames;
        this.path = path;
        this.buf = new float[2 * (reserve + 32768)];
        reopen();
        AudioFormat f = dec.getFormat();
        if ((int) f.getSampleRate() != SR || f.getChannels() < 1 || f.getChannels() > 2) {
            throw new IOException(path + ": " + f + " (looped files are 44.1 kHz mono or stereo)");
        }
    }

    private void reopen() throws IOException {
        closeDecoder();
        dec = new JOrbisAudioStream(new ByteArrayInputStream(data));
        channels = dec.getFormat().getChannels();
        lap = 0;
        eof = odd = false;
    }

    void closeDecoder() {
        if (dec == null) return;
        try {
            dec.close();
        } catch (IOException e) {
            Awaken.LOG.debug("[audio] closing {}: {}", path, e.toString());
        }
        dec = null;
    }

    /** Makes sure at least n frames are buffered: decodes, ends a lap (padding an exact one with silence), starts the next. */
    void fill(int n) throws IOException {
        while (end - start < n) {
            if (lapFrames > 0 && lap >= lapFrames) {
                reopen();
                continue;
            }
            if (eof) {
                if (lapFrames > 0) {
                    push(0F, 0F); // a decoder that ends early: the lap still lasts exactly lapFrames
                } else {
                    if (lap == 0) throw new IOException(path + ": decodes to nothing");
                    reopen(); // a natural loop starts again at its own length
                }
                continue;
            }
            if (!dec.readChunk(this)) eof = true;
        }
    }

    void consume(int n) {
        start += n;
        if (start == end) start = end = 0;
    }

    @Override
    public void accept(float x) {
        if (lapFrames > 0 && lap >= lapFrames) return; // past an exact lap's length: dropped, so every lap is exact
        if (channels == 1) {
            push(x, x);
        } else if (!odd) {
            pendingL = x;
            odd = true;
        } else {
            odd = false;
            push(pendingL, x);
        }
    }

    private void push(float l, float r) {
        if (2 * (end + 1) > buf.length) {
            int live = end - start;
            if (start > 0) {
                System.arraycopy(buf, 2 * start, buf, 0, 2 * live);
                start = 0;
                end = live;
            }
            if (2 * (end + 1) > buf.length) buf = java.util.Arrays.copyOf(buf, buf.length * 2); // never in practice
        }
        buf[2 * end] = l;
        buf[2 * end + 1] = r;
        end++;
        lap++;
    }
}
