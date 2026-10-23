package dev.awaken.progression;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Everything the Signal knows about one player. Saved on the player, kept on death, synced to its owner only.
 * Mutate through {@link Progression}; call {@code Progression.markDirty(player)} after direct edits.
 */
public final class Profile {
    /** 2: banked prologue XP and level-road unlock ids (both default empty for older saves). */
    public static final int SCHEMA = 2;

    // core
    int schema = SCHEMA;
    boolean awakened;
    int level = 1;
    double xp;
    Rank rank = Rank.E;
    int unspent;
    int[] stats = {Stat.START, Stat.START, Stat.START, Stat.START, Stat.START, Stat.START};
    String path = "";
    boolean reassessmentReady;
    /** XP earned before awakening (the prologue), paid on awakening. */
    double bankedXp;

    // identity
    final List<String> truthsDeclared = new ArrayList<>();
    final List<String> truthsOffered = new ArrayList<>();
    final Set<String> truthsTrue = new LinkedHashSet<>();
    final Set<String> titles = new LinkedHashSet<>();
    String title = "";
    final Set<String> latents = new LinkedHashSet<>();
    final List<String> pathsOffered = new ArrayList<>();
    /** Level-road unlock ids already granted (ids, never table positions, so editing the table never revokes). */
    final Set<String> unlocks = new LinkedHashSet<>();

    // activity
    final Map<String, Long> counters = new HashMap<>();
    final List<DirectiveState> directives = new ArrayList<>();
    RegimenState regimen = RegimenState.EMPTY;
    long lastNewThing;
    long fractureUntil;
    final Map<String, Integer> insight = new HashMap<>();
    DayLog day = DayLog.EMPTY;

    public Profile() {}

    // ---- read API ---------------------------------------------------------------------------------------------------
    public boolean awakened() { return awakened; }
    public int level() { return level; }
    public double xp() { return xp; }
    public Rank rank() { return rank; }
    public int unspent() { return unspent; }
    public int stat(Stat s) { return stats[s.ordinal()]; }
    public Optional<String> path() { return path.isEmpty() ? Optional.empty() : Optional.of(path); }
    public boolean reassessmentReady() { return reassessmentReady; }
    public double bankedXp() { return bankedXp; }
    public Set<String> unlocks() { return unlocks; }
    public List<String> truthsDeclared() { return truthsDeclared; }
    public List<String> truthsOffered() { return truthsOffered; }
    public Set<String> truthsTrue() { return truthsTrue; }
    public Set<String> titles() { return titles; }
    public Optional<String> title() { return title.isEmpty() ? Optional.empty() : Optional.of(title); }
    public Set<String> latents() { return latents; }
    public List<String> pathsOffered() { return pathsOffered; }
    public long counter(String id) { return counters.getOrDefault(id, 0L); }
    public Map<String, Long> counters() { return counters; }
    public List<DirectiveState> directives() { return directives; }
    public RegimenState regimen() { return regimen; }
    public long lastNewThing() { return lastNewThing; }
    public long fractureUntil() { return fractureUntil; }
    public int insight(String bossId) { return insight.getOrDefault(bossId, 0); }
    public DayLog day() { return day; }
    public double xpToNext() { return Curve.xpToNext(level); }

    // ---- sub records ------------------------------------------------------------------------------------------------
    public record DirectiveState(String id, boolean accepted, long offeredDay, long expiresDay, Map<String, Long> baseline) {
        public static final Codec<DirectiveState> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(DirectiveState::id),
                Codec.BOOL.fieldOf("accepted").forGetter(DirectiveState::accepted),
                Codec.LONG.fieldOf("offered").forGetter(DirectiveState::offeredDay),
                Codec.LONG.fieldOf("expires").forGetter(DirectiveState::expiresDay),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("baseline").forGetter(DirectiveState::baseline)
        ).apply(i, DirectiveState::new));
    }

    public record RegimenState(long day, List<String> drills, List<String> done, Map<String, Long> baseline) {
        public static final RegimenState EMPTY = new RegimenState(-1, List.of(), List.of(), Map.of());
        public static final Codec<RegimenState> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(RegimenState::day),
                Codec.STRING.listOf().fieldOf("drills").forGetter(RegimenState::drills),
                Codec.STRING.listOf().fieldOf("done").forGetter(RegimenState::done),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("baseline").forGetter(RegimenState::baseline)
        ).apply(i, RegimenState::new));
    }

    /** Today's tallies for the Day's End card. */
    public record DayLog(long day, int levelStart, int gates, int kills, String best) {
        public static final DayLog EMPTY = new DayLog(-1, 1, 0, 0, "");
        public static final Codec<DayLog> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("day").forGetter(DayLog::day),
                Codec.INT.fieldOf("level_start").forGetter(DayLog::levelStart),
                Codec.INT.fieldOf("gates").forGetter(DayLog::gates),
                Codec.INT.fieldOf("kills").forGetter(DayLog::kills),
                Codec.STRING.fieldOf("best").forGetter(DayLog::best)
        ).apply(i, DayLog::new));
    }

    // ---- codec (split in three groups to stay under the 16-field limit) ---------------------------------------------
    private record Core(int schema, boolean awakened, int level, double xp, Rank rank, int unspent, List<Integer> stats,
                        String path, boolean reassessmentReady, double bankedXp) {
        static final Codec<Core> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("schema").forGetter(Core::schema),
                Codec.BOOL.fieldOf("awakened").forGetter(Core::awakened),
                Codec.INT.fieldOf("level").forGetter(Core::level),
                Codec.DOUBLE.fieldOf("xp").forGetter(Core::xp),
                Rank.CODEC.fieldOf("rank").forGetter(Core::rank),
                Codec.INT.fieldOf("unspent").forGetter(Core::unspent),
                Codec.INT.listOf().fieldOf("stats").forGetter(Core::stats),
                Codec.STRING.fieldOf("path").forGetter(Core::path),
                Codec.BOOL.optionalFieldOf("reassessment_ready", false).forGetter(Core::reassessmentReady),
                Codec.DOUBLE.optionalFieldOf("banked_xp", 0.0).forGetter(Core::bankedXp)
        ).apply(i, Core::new));
    }

    private record Identity(List<String> declared, List<String> offered, List<String> truths, List<String> titles,
                            String title, List<String> latents, List<String> paths, List<String> unlocks) {
        static final Codec<Identity> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().fieldOf("declared").forGetter(Identity::declared),
                Codec.STRING.listOf().fieldOf("offered").forGetter(Identity::offered),
                Codec.STRING.listOf().fieldOf("true").forGetter(Identity::truths),
                Codec.STRING.listOf().fieldOf("titles").forGetter(Identity::titles),
                Codec.STRING.fieldOf("title").forGetter(Identity::title),
                Codec.STRING.listOf().fieldOf("latents").forGetter(Identity::latents),
                Codec.STRING.listOf().fieldOf("paths_offered").forGetter(Identity::paths),
                Codec.STRING.listOf().optionalFieldOf("unlocks", List.of()).forGetter(Identity::unlocks)
        ).apply(i, Identity::new));
    }

    private record Activity(Map<String, Long> counters, List<DirectiveState> directives, RegimenState regimen,
                            long lastNewThing, long fractureUntil, Map<String, Integer> insight, DayLog day) {
        static final Codec<Activity> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(Codec.STRING, Codec.LONG).fieldOf("counters").forGetter(Activity::counters),
                DirectiveState.CODEC.listOf().fieldOf("directives").forGetter(Activity::directives),
                RegimenState.CODEC.fieldOf("regimen").forGetter(Activity::regimen),
                Codec.LONG.fieldOf("last_new_thing").forGetter(Activity::lastNewThing),
                Codec.LONG.fieldOf("fracture_until").forGetter(Activity::fractureUntil),
                Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("insight").forGetter(Activity::insight),
                DayLog.CODEC.fieldOf("day").forGetter(Activity::day)
        ).apply(i, Activity::new));
    }

    public static final MapCodec<Profile> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Core.CODEC.fieldOf("core").forGetter(p -> new Core(p.schema, p.awakened, p.level, p.xp, p.rank, p.unspent,
                    java.util.Arrays.stream(p.stats).boxed().toList(), p.path, p.reassessmentReady, p.bankedXp)),
            Identity.CODEC.fieldOf("identity").forGetter(p -> new Identity(List.copyOf(p.truthsDeclared),
                    List.copyOf(p.truthsOffered), List.copyOf(p.truthsTrue), List.copyOf(p.titles), p.title,
                    List.copyOf(p.latents), List.copyOf(p.pathsOffered), List.copyOf(p.unlocks))),
            Activity.CODEC.fieldOf("activity").forGetter(p -> new Activity(Map.copyOf(p.counters),
                    List.copyOf(p.directives), p.regimen, p.lastNewThing, p.fractureUntil, Map.copyOf(p.insight), p.day))
    ).apply(i, Profile::fromParts));

    public static final Codec<Profile> CODEC = MAP_CODEC.codec();
    public static final StreamCodec<ByteBuf, Profile> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC, net.minecraft.nbt.NbtAccounter::unlimitedHeap);

    private static Profile fromParts(Core c, Identity id, Activity a) {
        Profile p = new Profile();
        p.schema = SCHEMA; // older schemas load with defaults for the newer fields; nothing else changed shape
        p.awakened = c.awakened;
        p.level = Math.max(1, c.level);
        p.xp = c.xp;
        p.rank = c.rank;
        p.unspent = c.unspent;
        for (int s = 0; s < 6 && s < c.stats.size(); s++) p.stats[s] = c.stats.get(s);
        p.path = c.path;
        p.reassessmentReady = c.reassessmentReady;
        p.bankedXp = Math.max(0, c.bankedXp);
        p.truthsDeclared.addAll(id.declared);
        p.truthsOffered.addAll(id.offered);
        p.truthsTrue.addAll(id.truths);
        p.titles.addAll(id.titles);
        p.title = id.title;
        p.latents.addAll(id.latents);
        p.pathsOffered.addAll(id.paths);
        p.unlocks.addAll(id.unlocks);
        p.counters.putAll(a.counters);
        p.directives.addAll(a.directives);
        p.regimen = a.regimen;
        p.lastNewThing = a.lastNewThing;
        p.fractureUntil = a.fractureUntil;
        p.insight.putAll(a.insight);
        p.day = a.day;
        return p;
    }
}
