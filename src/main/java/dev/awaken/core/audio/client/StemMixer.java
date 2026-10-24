package dev.awaken.core.audio.client;

import dev.awaken.Awaken;
import dev.awaken.core.audio.LayerManifest;
import it.unimi.dsi.fastutil.floats.FloatConsumer;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.JOrbisAudioStream;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Plays a layered music context as ONE audio stream: it decodes every stem in lockstep and mixes them with per-stem
 * gains that ramp sample by sample. One OpenAL source, so the stems can never drift apart (they are equal-length loops,
 * and every lap is forced to exactly the manifest's length), and a crossfade never clicks.
 *
 * <p>The client thread sets targets and ramp times ({@link #set}, then {@link #publish}); the sound thread reads the
 * stream. Reads are short ({@value #CHUNK} frames, a quarter second), so OpenAL's four queued buffers hold about a
 * second: a change of intensity is heard within a second of the client knowing it. Above -1 dBFS a soft knee catches
 * the moments where one stem is still leaving while another arrives. Nothing here allocates per read except what the
 * Vorbis decoder itself does (vanilla's streams do the same).
 */
final class StemMixer implements AudioStream {
    static final int SR = 44100;
    /** Frames per read: 0.25 s. */
    static final int CHUNK = 11025;
    private static final AudioFormat FORMAT = new AudioFormat(SR, 16, 2, true, false);
    private static final float KNEE = 0.891F; // -1 dBFS

    private final Track[] tracks;
    private final ByteBuffer out = BufferUtils.createByteBuffer(CHUNK * 4);
    // sound thread
    private final float[] gain, target, step;
    // client thread -> sound thread
    private final float[] pubTarget, pubSeconds;
    private volatile int version;
    private int seen = -1;
    private volatile boolean closed;
    // diagnostics (read by the client for logs)
    final float[] shown;
    volatile long frames;
    volatile int softened;

    private StemMixer(Track[] tracks) {
        this.tracks = tracks;
        int n = tracks.length;
        gain = new float[n];
        target = new float[n];
        step = new float[n];
        pubTarget = new float[n];
        pubSeconds = new float[n];
        shown = new float[n];
        java.util.Arrays.fill(pubSeconds, 1F);
    }

    /** Loads every stem of a manifest (the encoded files stay in memory; each lap re-reads them). IO thread. */
    static StemMixer open(LayerManifest m, ResourceProvider resources) throws IOException {
        Track[] t = new Track[m.stems().size()];
        for (int i = 0; i < t.length; i++) {
            Identifier file = m.stems().get(i).file();
            Identifier path = Identifier.fromNamespaceAndPath(file.getNamespace(), "sounds/" + file.getPath() + ".ogg");
            byte[] data;
            try (InputStream in = resources.open(path)) {
                data = in.readAllBytes();
            }
            t[i] = new Track(data, m.frames(), path);
        }
        return new StemMixer(t);
    }

    int stems() {
        return tracks.length;
    }

    /** A stem's next target gain (0..1) and the seconds a full 0 to 1 travel takes. Client thread; then {@link #publish}. */
    void set(int stem, float targetGain, float seconds) {
        pubTarget[stem] = Math.max(0F, Math.min(1F, targetGain));
        pubSeconds[stem] = Math.max(0.02F, seconds);
    }

    void publish() {
        version++;
    }

    /** Whether every stem has arrived at a gain of zero (a fade-out finished). */
    boolean silent() {
        for (float g : shown) if (g > 0.0005F) return false;
        for (float t : pubTarget) if (t > 0F) return false;
        return true;
    }

    @Override
    public AudioFormat getFormat() {
        return FORMAT;
    }

    @Override
    public ByteBuffer read(int expectedSize) throws IOException {
        out.clear();
        if (closed) {
            out.flip();
            return out; // empty: the channel ends
        }
        int n = Math.max(256, Math.min(CHUNK, expectedSize / 4));
        int v = version;
        if (v != seen) {
            seen = v;
            for (int k = 0; k < tracks.length; k++) {
                target[k] = pubTarget[k];
                step[k] = 1F / (pubSeconds[k] * SR);
            }
        }
        for (Track t : tracks) t.fill(n);
        int soft = 0;
        for (int i = 0; i < n; i++) {
            float l = 0F, r = 0F;
            for (int k = 0; k < tracks.length; k++) {
                float g = gain[k];
                float tg = target[k];
                if (g != tg) {
                    g = g < tg ? Math.min(tg, g + step[k]) : Math.max(tg, g - step[k]);
                    gain[k] = g;
                }
                if (g > 0F) {
                    Track t = tracks[k];
                    int j = 2 * (t.start + i);
                    l += g * t.buf[j];
                    r += g * t.buf[j + 1];
                }
            }
            if (l > KNEE || l < -KNEE || r > KNEE || r < -KNEE) soft++;
            out.putShort(pcm(knee(l)));
            out.putShort(pcm(knee(r)));
        }
        for (Track t : tracks) t.consume(n);
        System.arraycopy(gain, 0, shown, 0, gain.length);
        frames += n;
        if (soft > 0) softened += soft;
        out.flip();
        return out;
    }

    private static float knee(float x) {
        float a = Math.abs(x);
        if (a <= KNEE) return x;
        float y = KNEE + (1F - KNEE) * (float) Math.tanh((a - KNEE) / (1F - KNEE));
        return x < 0 ? -y : y;
    }

    private static short pcm(float x) {
        int v = (int) (x * 32767.5F - 0.5F);
        return (short) Math.max(-32768, Math.min(32767, v));
    }

    @Override
    public void close() throws IOException {
        closed = true;
        for (Track t : tracks) t.closeDecoder();
    }

    /** One stem: its encoded bytes, a Vorbis decoder, and a FIFO of decoded stereo frames. */
    private static final class Track implements FloatConsumer {
        final byte[] data;
        final long loop;
        final Identifier path;
        JOrbisAudioStream dec;
        int channels;
        float[] buf = new float[2 * (CHUNK + 32768)];
        int start, end;
        long lap;
        boolean eof, odd;
        float pendingL;

        Track(byte[] data, long loop, Identifier path) throws IOException {
            this.data = data;
            this.loop = loop;
            this.path = path;
            reopen();
            AudioFormat f = dec.getFormat();
            if ((int) f.getSampleRate() != SR || f.getChannels() < 1 || f.getChannels() > 2) {
                throw new IOException(path + ": " + f + " (layered stems are 44.1 kHz mono or stereo)");
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

        /** Makes sure at least n frames are buffered: decodes, pads a short lap with silence, starts the next lap. */
        void fill(int n) throws IOException {
            while (end - start < n) {
                if (lap >= loop) {
                    reopen();
                    continue;
                }
                if (eof) {
                    push(0F, 0F); // a decoder that ends early: the lap still lasts exactly `loop` frames
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
            if (lap >= loop) return; // past the loop's length: dropped, so every lap is exact
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
}
