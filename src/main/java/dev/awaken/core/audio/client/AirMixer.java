package dev.awaken.core.audio.client;

import net.minecraft.client.sounds.AudioStream;
import net.minecraft.resources.Identifier;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * The world's air as ONE audio stream (docs/sound/PLAN.md §6.1): up to {@value #SLOTS} bed layers, each a natural
 * {@link OggLoop} (layers of different lengths drift against each other, so the mix never repeats), mixed with gains
 * that ramp sample by sample, then through a one-pole low-pass whose cutoff eases toward its target: the air closes
 * when you step indoors, go underwater, project into the Veil or dream at low lucidity. Vanilla's engine can't filter
 * a source without mixins; a stream we mix ourselves can.
 *
 * <p>The client thread assigns files to slots and sets targets ({@link #assign}, {@link #set}, {@link #cutoff}, then
 * {@link #publish}); the sound thread reads. A slot takes a new file only once its gain has reached zero, so a swap is
 * never heard. Reads are a quarter second, like {@link StemMixer}.
 */
public final class AirMixer implements AudioStream {
    public static final int SLOTS = 8;
    static final int SR = OggLoop.SR, CHUNK = StemMixer.CHUNK;
    private static final AudioFormat FORMAT = new AudioFormat(SR, 16, 2, true, false);
    private static final float OPEN_HZ = 20000F;

    private final ByteBuffer out = BufferUtils.createByteBuffer(CHUNK * 4);
    // sound thread
    private final OggLoop[] loops = new OggLoop[SLOTS];
    private final Identifier[] playing = new Identifier[SLOTS];
    private final float[] gain = new float[SLOTS], target = new float[SLOTS], step = new float[SLOTS];
    private final boolean[] filled = new boolean[SLOTS];
    private float a = 1F, lpL, lpR;
    private int seen = -1;
    // client thread -> sound thread
    private final Identifier[] pubFile = new Identifier[SLOTS];
    private final byte[][] pubData = new byte[SLOTS][];
    private final float[] pubTarget = new float[SLOTS], pubSeconds = new float[SLOTS];
    private volatile float pubCutoff = OPEN_HZ;
    private volatile int version;
    private volatile boolean closed;
    // sound thread -> client (diagnostics and slot reuse)
    private final float[] shown = new float[SLOTS];
    private volatile float shownCutoff = OPEN_HZ;

    public AirMixer() {
        java.util.Arrays.fill(pubSeconds, 6F);
    }

    /** Puts a file in a slot (its encoded bytes, already loaded). Only call it on a slot that is {@link #free}. */
    public void assign(int slot, Identifier file, byte[] data) {
        pubFile[slot] = file;
        pubData[slot] = data;
        pubTarget[slot] = 0F;
    }

    /** A slot's next target gain (0..1) and the seconds a full 0 to 1 travel takes. Then {@link #publish}. */
    public void set(int slot, float targetGain, float seconds) {
        pubTarget[slot] = Math.max(0F, Math.min(1F, targetGain));
        pubSeconds[slot] = Math.max(0.05F, seconds);
    }

    /** The low-pass cutoff to ease toward (Hz; 20000 is open). */
    public void cutoff(float hz) {
        pubCutoff = Math.max(200F, Math.min(OPEN_HZ, hz));
    }

    public void publish() {
        version++;
    }

    public Identifier file(int slot) {
        return pubFile[slot];
    }

    /** A slot whose gain has reached zero and whose target is zero: its file may be replaced. */
    public boolean free(int slot) {
        return pubTarget[slot] <= 0F && shown[slot] <= 0.0005F;
    }

    public float shown(int slot) {
        return shown[slot];
    }

    public float shownCutoff() {
        return shownCutoff;
    }

    /** Every slot silent and asked to stay so. */
    public boolean silent() {
        for (int s = 0; s < SLOTS; s++) if (!free(s)) return false;
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
            return out;
        }
        int n = Math.max(256, Math.min(CHUNK, expectedSize / 4));
        int v = version;
        if (v != seen) {
            seen = v;
            for (int s = 0; s < SLOTS; s++) {
                target[s] = pubTarget[s];
                step[s] = 1F / (pubSeconds[s] * SR);
            }
        }
        for (int s = 0; s < SLOTS; s++) {
            Identifier want = pubFile[s];
            if (!java.util.Objects.equals(want, playing[s]) && gain[s] <= 0F) { // a swap only in silence
                if (loops[s] != null) loops[s].closeDecoder();
                loops[s] = want == null || pubData[s] == null ? null : new OggLoop(pubData[s], 0, want, CHUNK);
                playing[s] = want;
            }
            filled[s] = loops[s] != null && (gain[s] > 0F || target[s] > 0F); // a silent layer waits where it is
            if (filled[s]) loops[s].fill(n);
        }
        // the cutoff eases across the read: one-pole coefficient, linear from the last read's to this one's
        float cut = shownCutoff + (pubCutoff - shownCutoff) * 0.35F;
        float a1 = (float) (1.0 - Math.exp(-2.0 * Math.PI * cut / SR)), da = (a1 - a) / n;
        for (int i = 0; i < n; i++) {
            float l = 0F, r = 0F;
            for (int s = 0; s < SLOTS; s++) {
                if (!filled[s]) continue;
                OggLoop t = loops[s];
                float g = gain[s], tg = target[s];
                if (g != tg) {
                    g = g < tg ? Math.min(tg, g + step[s]) : Math.max(tg, g - step[s]);
                    gain[s] = g;
                }
                if (g > 0F) {
                    int j = 2 * (t.start + i);
                    l += g * t.buf[j];
                    r += g * t.buf[j + 1];
                }
            }
            a += da;
            lpL += a * (l - lpL);
            lpR += a * (r - lpR);
            out.putShort(pcm(lpL));
            out.putShort(pcm(lpR));
        }
        a = a1;
        shownCutoff = cut;
        for (int s = 0; s < SLOTS; s++) {
            OggLoop t = loops[s];
            if (filled[s]) t.consume(n);
            shown[s] = t == null ? 0F : gain[s];
        }
        out.flip();
        return out;
    }

    private static short pcm(float x) {
        int v = (int) (x * 32767.5F - 0.5F);
        return (short) Math.max(-32768, Math.min(32767, v));
    }

    @Override
    public void close() {
        closed = true;
        for (OggLoop t : loops) if (t != null) t.closeDecoder();
    }
}
