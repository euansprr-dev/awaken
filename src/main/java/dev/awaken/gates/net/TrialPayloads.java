package dev.awaken.gates.net;

import dev.awaken.Awaken;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** What a trial's course tells the client beyond the run's clock (SPEC §71.8). */
public final class TrialPayloads {
    private TrialPayloads() {}

    /**
     * The tally at the end of the Leaper's Gauntlet's Long Run: the XP paid for it, the Leaper mastery level before and
     * after and how full the new level is (0..1000), the run's time and medal (1 bronze, 2 silver, 3 gold), the runner's
     * best time before this run (0: none), and how often each move of the leap was made, with its plain name.
     */
    public record Tally(boolean first, int xp, int masteryBefore, int masteryAfter, int fill, int ticks, int medal, int best, List<String> names,
                        List<Integer> counts) implements CustomPacketPayload {
        public static final Type<Tally> TYPE = new Type<>(Awaken.id("gate_trial_tally"));
        public static final StreamCodec<ByteBuf, Tally> STREAM_CODEC = StreamCodec.of((buf, t) -> {
            ByteBufCodecs.VAR_INT.encode(buf, t.first ? 1 : 0);
            ByteBufCodecs.VAR_INT.encode(buf, t.xp);
            ByteBufCodecs.VAR_INT.encode(buf, t.masteryBefore);
            ByteBufCodecs.VAR_INT.encode(buf, t.masteryAfter);
            ByteBufCodecs.VAR_INT.encode(buf, t.fill);
            ByteBufCodecs.VAR_INT.encode(buf, t.ticks);
            ByteBufCodecs.VAR_INT.encode(buf, t.medal);
            ByteBufCodecs.VAR_INT.encode(buf, t.best);
            int n = Math.min(8, Math.min(t.names.size(), t.counts.size()));
            ByteBufCodecs.VAR_INT.encode(buf, n);
            for (int i = 0; i < n; i++) {
                ByteBufCodecs.STRING_UTF8.encode(buf, t.names.get(i));
                ByteBufCodecs.VAR_INT.encode(buf, t.counts.get(i));
            }
        }, buf -> {
            boolean first = ByteBufCodecs.VAR_INT.decode(buf) != 0;
            int xp = ByteBufCodecs.VAR_INT.decode(buf), before = ByteBufCodecs.VAR_INT.decode(buf), after = ByteBufCodecs.VAR_INT.decode(buf),
                    fill = ByteBufCodecs.VAR_INT.decode(buf), ticks = ByteBufCodecs.VAR_INT.decode(buf), medal = ByteBufCodecs.VAR_INT.decode(buf),
                    best = ByteBufCodecs.VAR_INT.decode(buf), n = Math.min(8, ByteBufCodecs.VAR_INT.decode(buf));
            List<String> names = new ArrayList<>(n);
            List<Integer> counts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                names.add(ByteBufCodecs.STRING_UTF8.decode(buf));
                counts.add(ByteBufCodecs.VAR_INT.decode(buf));
            }
            return new Tally(first, xp, before, after, fill, ticks, medal, best, List.copyOf(names), List.copyOf(counts));
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
