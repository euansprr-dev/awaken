package dev.awaken.fault;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One Fault (SPEC §43.8; JOURNEY part 5): the System's voice breaking up when the Deep pushes hard. A tell (the world's
 * sound drops out, the HUD flickers, a bell note pitched wrong), the break (the frame tears into sliding bands, red and
 * cyan split, a hard cut to black), the words typed large and broken in the black ({@code words}, then {@code leak}, the
 * line it shouldn't know), the held cut ({@code camFrom} looking at {@code camAt} for {@code cutTicks}), and back: the
 * plain line ({@code after}) with the band's edge flickering until the event mends ({@code holds}).
 * {@code key} names a Great Fault: each plays once per player, then as a plain Fault.
 */
public record Fault(Size size, String key, List<String> words, String leak, String after, @Nullable Vec3 camFrom, @Nullable Vec3 camAt,
                    int cutTicks, boolean holds) {

    /** A flicker (under a second), a Fault (3 to 5 s) or a Great Fault (10 to 20 s, hand-made, about eight in the game). */
    public enum Size {
        FLICKER(0, 14, 0, 0),
        FAULT(10, 22, 34, 40),
        GREAT(22, 60, 110, 90);

        /** Ticks of the tell, the break, the black (at least: long words stretch it) and the longest cut. */
        public final int tell, tear, black, maxCut;

        Size(int tell, int tear, int black, int maxCut) {
            this.tell = tell;
            this.tear = tear;
            this.black = black;
            this.maxCut = maxCut;
        }
    }

    /** Words type at this many characters a tick, with a beat between lines. */
    public static final float TYPE_RATE = 1.1F;
    public static final int LINE_BEAT = 10;

    public Fault {
        words = List.copyOf(words);
        cutTicks = camFrom == null || camAt == null ? 0 : Math.clamp(cutTicks, 0, size.maxCut);
    }

    /** A flicker: no words, no black. */
    public static Fault flicker(String after) {
        return new Fault(Size.FLICKER, "", List.of(), "", after, null, null, 0, false);
    }

    /** The same Fault at another size (a Great Fault already seen plays as a Fault). */
    public Fault as(Size s) {
        return new Fault(s, s == Size.GREAT ? key : "", words, s == Size.GREAT ? leak : "", after, camFrom, camAt, cutTicks, holds);
    }

    /** Ticks the black holds: its least, or long enough to type every line and read the last. */
    public int blackTicks() {
        if (size == Size.FLICKER) return 0;
        int chars = 0;
        for (String w : words) chars += w.length();
        chars += leak.length();
        int lines = words.size() + (leak.isEmpty() ? 0 : 1);
        return Math.max(size.black, (int) (chars / TYPE_RATE) + lines * LINE_BEAT + 30);
    }

    /** Ticks from the tell to the screen coming back. */
    public int length() {
        return size.tell + size.tear + blackTicks() + cutTicks;
    }

    private static void vec(FriendlyByteBuf buf, Vec3 v) {
        buf.writeDouble(v.x);
        buf.writeDouble(v.y);
        buf.writeDouble(v.z);
    }

    public static final StreamCodec<FriendlyByteBuf, Fault> STREAM_CODEC = StreamCodec.of((buf, f) -> {
        buf.writeEnum(f.size);
        buf.writeUtf(f.key, 64);
        buf.writeVarInt(f.words.size());
        for (String w : f.words) buf.writeUtf(w, 256);
        buf.writeUtf(f.leak, 256);
        buf.writeUtf(f.after, 256);
        boolean cut = f.camFrom != null && f.camAt != null;
        buf.writeBoolean(cut);
        if (cut) {
            vec(buf, f.camFrom);
            vec(buf, f.camAt);
        }
        buf.writeVarInt(f.cutTicks);
        buf.writeBoolean(f.holds);
    }, buf -> {
        Size size = buf.readEnum(Size.class);
        String key = buf.readUtf(64);
        int n = Math.min(buf.readVarInt(), 8);
        List<String> words = new ArrayList<>(n);
        for (int i = 0; i < n; i++) words.add(buf.readUtf(256));
        String leak = buf.readUtf(256), after = buf.readUtf(256);
        Vec3 from = null, at = null;
        if (buf.readBoolean()) {
            from = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
            at = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        }
        int cut = buf.readVarInt();
        return new Fault(size, key, words, leak, after, from, at, cut, buf.readBoolean());
    });
}
