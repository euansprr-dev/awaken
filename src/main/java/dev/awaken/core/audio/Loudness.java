package dev.awaken.core.audio;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;

import java.io.IOException;
import java.io.InputStream;

/**
 * Loudness of the mod's audio, measured in Java the way tools/art/audio_mix.py measures it, so the GameTests check
 * the shipped files themselves (SPEC "Plan v2 · audio": music -19 +-2 dBFS RMS / -1 dBTP, UI -24 +-3 / -3, ambience
 * -28 +-3 / -6): gated RMS (100 ms blocks; blocks under -60 dBFS or 20 dB below the mean left out), integrated
 * loudness (ITU-R BS.1770: K-weighting, 400 ms blocks with 75% overlap, -70 LUFS and -10 LU gates) and true peak (4x
 * oversampled, a windowed-sinc interpolator evaluated around the loud samples).
 *
 * <p>Decoding uses the JOrbis library directly (Minecraft's own stream class is client-only and the tests run on a
 * dedicated server). Test code: it allocates whole files.
 */
public final class Loudness {
    private Loudness() {}

    public static final int SR = 44100;

    /** Decoded audio: one float array per channel, every array the same length. */
    public record Pcm(int rate, float[][] channels) {
        public int frames() {
            return channels[0].length;
        }
    }

    public record Measure(double rms, double lufs, double truePeak) {}

    // ---- decoding ------------------------------------------------------------------------------------------------

    /** Decodes a whole Ogg Vorbis stream (trimmed to the last page's granule position, as the game plays it). */
    public static Pcm decode(InputStream in) throws IOException {
        SyncState sync = new SyncState();
        StreamState stream = new StreamState();
        Page page = new Page();
        Packet packet = new Packet();
        Info info = new Info();
        Comment comment = new Comment();
        DspState dsp = new DspState();
        Block block = new Block(dsp);
        info.init();
        comment.init();
        float[][] out = null;
        int n = 0;
        int headers = 0;
        long total = Long.MAX_VALUE;
        boolean started = false;
        byte[] chunk = new byte[1 << 16];
        boolean eof = false;
        while (true) {
            int r = sync.pageout(page);
            if (r == 0) {
                if (eof) break;
                int off = sync.buffer(chunk.length);
                int got = in.read(sync.data, off, chunk.length);
                if (got <= 0) {
                    eof = true;
                    sync.wrote(0);
                } else {
                    sync.wrote(got);
                }
                continue;
            }
            if (r < 0) throw new IOException("corrupt Ogg page");
            if (!started) {
                stream.init(page.serialno());
                started = true;
            }
            if (page.eos() != 0) total = page.granulepos();
            if (stream.pagein(page) < 0) throw new IOException("bad page");
            while (true) {
                int p = stream.packetout(packet);
                if (p == 0) break;
                if (p < 0) throw new IOException("bad packet");
                if (headers < 3) {
                    if (info.synthesis_headerin(comment, packet) < 0) throw new IOException("not Vorbis");
                    headers++;
                    if (headers == 3) {
                        dsp.synthesis_init(info);
                        block.init(dsp);
                        out = new float[info.channels][1 << 20];
                    }
                    continue;
                }
                if (block.synthesis(packet) != 0) continue;
                dsp.synthesis_blockin(block);
                float[][][] pcm = new float[1][][];
                int[] idx = new int[info.channels];
                int s;
                while ((s = dsp.synthesis_pcmout(pcm, idx)) > 0) {
                    int take = (int) Math.min(s, Math.max(0, total - n));
                    if (n + take > out[0].length) {
                        int cap = Math.max(out[0].length * 2, n + take);
                        for (int c = 0; c < out.length; c++) out[c] = java.util.Arrays.copyOf(out[c], cap);
                    }
                    for (int c = 0; c < info.channels; c++) System.arraycopy(pcm[0][c], idx[c], out[c], n, take);
                    n += take;
                    dsp.synthesis_read(s);
                }
            }
        }
        if (out == null) throw new IOException("no Vorbis headers");
        int frames = (int) Math.min(n, total);
        for (int c = 0; c < out.length; c++) out[c] = java.util.Arrays.copyOf(out[c], frames);
        return new Pcm(info.rate, out);
    }

    // ---- measuring -----------------------------------------------------------------------------------------------

    public static Measure measure(float[][] ch) {
        return new Measure(gatedRms(ch), lufs(ch), truePeak(ch));
    }

    /** Gated RMS (dBFS) over 100 ms blocks, as audiokit.gated_rms: mono counts as both channels. */
    public static double gatedRms(float[][] ch) {
        int n = ch[0].length, b = SR / 10, nb = Math.max(1, n / b);
        double[] p = new double[nb];
        for (int k = 0; k < nb; k++) {
            double s = 0;
            for (float[] c : ch) for (int i = k * b; i < Math.min(n, (k + 1) * b); i++) s += c[i] * (double) c[i];
            p[k] = s / ((double) b * ch.length);
        }
        double sum = 0;
        int cnt = 0;
        for (double v : p) if (db(v) > -60) {
            sum += v;
            cnt++;
        }
        if (cnt == 0) return -99;
        double thr = db(sum / cnt) - 20;
        sum = 0;
        cnt = 0;
        for (double v : p) if (db(v) > -60 && db(v) > thr) {
            sum += v;
            cnt++;
        }
        return db(sum / cnt);
    }

    /** Integrated loudness (LUFS), BS.1770 with the same K-weighting designs as audiokit.k_weight. */
    public static double lufs(float[][] ch) {
        int n = ch[0].length;
        double[][] k = new double[ch.length][];
        for (int c = 0; c < ch.length; c++) k[c] = kWeight(ch[c]);
        int blk = (int) (0.4 * SR), hop = (int) (0.1 * SR);
        if (n < blk) return -99;
        int nb = (n - blk) / hop + 1;
        double[] ms = new double[nb];
        for (int j = 0; j < nb; j++) {
            double s = 0;
            for (double[] y : k) {
                double a = 0;
                for (int i = j * hop; i < j * hop + blk; i++) a += y[i] * y[i];
                s += a / blk;
            }
            // mono files count as both channels, as audiokit's stereo() does
            ms[j] = ch.length == 1 ? 2 * s : s;
        }
        double sum = 0;
        int cnt = 0;
        for (double v : ms) if (lk(v) > -70) {
            sum += v;
            cnt++;
        }
        if (cnt == 0) return -99;
        double rel = lk(sum / cnt) - 10;
        sum = 0;
        cnt = 0;
        for (double v : ms) if (lk(v) > -70 && lk(v) > rel) {
            sum += v;
            cnt++;
        }
        return lk(sum / cnt);
    }

    private static double lk(double ms) {
        return -0.691 + 10 * Math.log10(Math.max(ms, 1e-20));
    }

    private static double db(double p) {
        return 10 * Math.log10(Math.max(p, 1e-20));
    }

    /** The BS.1770 pre-filter (high shelf +4 dB at 1.5 kHz) then the RLB high-pass (38 Hz), as biquads. */
    static double[] kWeight(float[] x) {
        double w0 = 2 * Math.PI * 1500.0 / SR, A = Math.pow(10, 4.0 / 40), q = 1 / Math.sqrt(2);
        double al = Math.sin(w0) / (2 * q), c = Math.cos(w0), sq = 2 * Math.sqrt(A) * al;
        double[] sb = {A * ((A + 1) + (A - 1) * c + sq), -2 * A * ((A - 1) + (A + 1) * c), A * ((A + 1) + (A - 1) * c - sq)};
        double[] sa = {(A + 1) - (A - 1) * c + sq, 2 * ((A - 1) - (A + 1) * c), (A + 1) - (A - 1) * c - sq};
        double w1 = 2 * Math.PI * 38.0 / SR, al1 = Math.sin(w1) / (2 * 0.5), c1 = Math.cos(w1);
        double[] hb = {(1 + c1) / 2, -(1 + c1), (1 + c1) / 2};
        double[] ha = {1 + al1, -2 * c1, 1 - al1};
        double[] y = biquad(x, sb, sa);
        return biquad(y, hb, ha);
    }

    private static double[] biquad(float[] x, double[] b, double[] a) {
        double[] d = new double[x.length];
        for (int i = 0; i < x.length; i++) d[i] = x[i];
        return biquad(d, b, a);
    }

    private static double[] biquad(double[] x, double[] b, double[] a) {
        double b0 = b[0] / a[0], b1 = b[1] / a[0], b2 = b[2] / a[0], a1 = a[1] / a[0], a2 = a[2] / a[0];
        double x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        double[] y = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            double v = b0 * x[i] + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1;
            x1 = x[i];
            y2 = y1;
            y1 = v;
            y[i] = v;
        }
        return y;
    }

    // a 4x interpolator: 16 taps per phase, Kaiser-windowed sinc (beta 8), cutoff at the original Nyquist
    private static final int OVER = 4, TAPS = 16;
    private static final double[][] PHASES = new double[OVER][TAPS];

    static {
        int len = OVER * TAPS;
        double centre = (len - 1) / 2.0;
        double i0b = bessel0(8.0);
        double[] h = new double[len];
        for (int i = 0; i < len; i++) {
            double t = (i - centre) / OVER;
            double sinc = t == 0 ? 1 : Math.sin(Math.PI * t) / (Math.PI * t);
            double r = (i - centre) / centre;
            h[i] = sinc * bessel0(8.0 * Math.sqrt(Math.max(0, 1 - r * r))) / i0b;
        }
        for (int p = 0; p < OVER; p++) {
            double sum = 0;
            for (int k = 0; k < TAPS; k++) {
                PHASES[p][k] = h[k * OVER + p];
                sum += PHASES[p][k];
            }
            for (int k = 0; k < TAPS; k++) PHASES[p][k] /= sum; // unity gain per phase
        }
    }

    private static double bessel0(double x) {
        double s = 1, t = 1;
        for (int k = 1; k < 40; k++) {
            t *= (x / (2 * k)) * (x / (2 * k));
            s += t;
        }
        return s;
    }

    /** True peak (dBTP): the largest of the samples and of the 4x interpolated points around every loud sample. */
    public static double truePeak(float[][] ch) {
        double peak = 0;
        for (float[] c : ch) for (float v : c) peak = Math.max(peak, Math.abs(v));
        double floor = peak * 0.5; // an inter-sample peak sits next to a loud sample
        double best = peak;
        for (float[] c : ch) {
            int n = c.length;
            for (int i = 0; i < n; i++) {
                if (Math.abs(c[i]) < floor && Math.abs(c[(i + 1) % n]) < floor) continue;
                for (int p = 1; p < OVER; p++) {
                    double s = 0;
                    double[] h = PHASES[p];
                    for (int k = 0; k < TAPS; k++) {
                        int j = i + TAPS / 2 - k; // circular: loops and short files alike
                        s += h[k] * c[Math.floorMod(j, n)];
                    }
                    best = Math.max(best, Math.abs(s));
                }
            }
        }
        return 20 * Math.log10(Math.max(best, 1e-12));
    }

    /** The sum of several stems with a gain each (stereo out; mono stems count on both sides). */
    public static float[][] mix(float[][][] stems, float[] gains) {
        int n = stems[0][0].length;
        float[][] out = new float[2][n];
        for (int s = 0; s < stems.length; s++) {
            float g = gains[s];
            if (g == 0F) continue;
            float[][] st = stems[s];
            for (int c = 0; c < 2; c++) {
                float[] src = st[Math.min(c, st.length - 1)];
                float[] dst = out[c];
                for (int i = 0; i < n; i++) dst[i] += g * src[i];
            }
        }
        return out;
    }
}
