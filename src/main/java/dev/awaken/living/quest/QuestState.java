package dev.awaken.living.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A player's Wayfarer chains (attachment {@code awaken:quests}, schema 1, kept through death, synced to its owner): the
 * chains taken (at most {@link #MAX_ACTIVE}), which one is tracked, the chains finished and the relics claimed. Only
 * what the player found and took is ever here (SPEC §21.0.7).
 */
public final class QuestState {
    public static final int SCHEMA = 1;
    public static final int MAX_ACTIVE = 5;

    /** One chain under way: its current step, progress on that step, and where its scene stands (if it has one). */
    public record Active(String chain, int step, int progress, Optional<Long> scene, long since) {
        public static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("chain").forGetter(Active::chain),
                Codec.INT.fieldOf("step").forGetter(Active::step),
                Codec.INT.optionalFieldOf("progress", 0).forGetter(Active::progress),
                Codec.LONG.optionalFieldOf("scene").forGetter(Active::scene),
                Codec.LONG.optionalFieldOf("since", 0L).forGetter(Active::since)
        ).apply(i, Active::new));

        public Active withStep(int s) {
            return new Active(chain, s, 0, scene, since);
        }

        public Active withProgress(int p) {
            return new Active(chain, step, p, scene, since);
        }
    }

    public static final Codec<QuestState> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(q -> SCHEMA),
            Active.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(q -> q.active),
            Codec.STRING.optionalFieldOf("tracked", "").forGetter(q -> q.tracked),
            Codec.STRING.listOf().optionalFieldOf("done", List.of()).forGetter(q -> List.copyOf(q.done)),
            Codec.STRING.listOf().optionalFieldOf("relics", List.of()).forGetter(q -> List.copyOf(q.relics))
    ).apply(i, (schema, active, tracked, done, relics) -> new QuestState(active, tracked, done, relics)));

    final List<Active> active;
    String tracked;
    final Set<String> done, relics;

    public QuestState() {
        this(List.of(), "", List.of(), List.of());
    }

    QuestState(List<Active> active, String tracked, List<String> done, List<String> relics) {
        this.active = new ArrayList<>(active);
        this.tracked = tracked;
        this.done = new LinkedHashSet<>(done);
        this.relics = new LinkedHashSet<>(relics);
    }

    public List<Active> active() {
        return List.copyOf(active);
    }

    public String tracked() {
        return tracked;
    }

    public Set<String> done() {
        return Set.copyOf(done);
    }

    public boolean has(String chain) {
        return active.stream().anyMatch(a -> a.chain.equals(chain));
    }
}
