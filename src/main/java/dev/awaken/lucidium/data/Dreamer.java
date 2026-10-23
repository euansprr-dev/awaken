package dev.awaken.lucidium.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.lucidium.plan.Nightmares;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.util.ValueIOSerializable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Everything lucidium remembers about one player between dreams: the Day Journal index (the template files live in
 * the world folder), death marks for nightmares, intention, WBTB window, Recall, visions and dream history.
 * Saved on the player (attachment {@code awaken:dreamer}), kept on death. Server side only.
 */
public final class Dreamer implements ValueIOSerializable {
    public static final int SCHEMA = 1;
    public static final int KEEP = 64;
    public static final int PER_DAY = 10;
    public static final int RECALL_CAP = 20;
    public static final int HISTORY = 16;

    public record DeathMark(String family, long day) {
        public static final Codec<DeathMark> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("family").forGetter(DeathMark::family),
                Codec.LONG.fieldOf("day").forGetter(DeathMark::day)).apply(i, DeathMark::new));
    }

    // Day Journal
    public final List<Snapshot> snapshots = new ArrayList<>();
    public int nextSeq;
    public long journalDay = -1;
    public int todayCount;
    public final Map<Snapshot.Kind, Integer> todayKinds = new EnumMap<>(Snapshot.Kind.class);
    public final Set<String> biomesSeen = new LinkedHashSet<>();
    public final Set<String> structuresSeen = new LinkedHashSet<>();
    /** Kills per 32x32 area today (area key -> count). */
    public final Map<Long, Integer> killAreas = new HashMap<>();

    // Nightmares
    public final List<DeathMark> deaths = new ArrayList<>();

    // Lucidity techniques
    public String intention = "";
    public long intentionDay = -1;
    public long wbtbUntil;
    public int recall;
    public long lastWoke = -1;
    public long lastWokeDream = -1;

    // Visions and history
    public final List<Vision> visions = new ArrayList<>();
    public final List<DreamRecord> history = new ArrayList<>();
    public long visionPrepDay = -1;
    public int dreams;
    public boolean lucidEver;
    public final Set<String> signsSeen = new LinkedHashSet<>();

    public Dreamer() {}

    // ---- journal ---------------------------------------------------------------------------------------------------

    /** Starts a new journal day if the play-day changed (clears today's allowance and kill tallies). */
    public void rollDay(long day) {
        if (journalDay == day) return;
        journalDay = day;
        todayCount = 0;
        todayKinds.clear();
        killAreas.clear();
    }

    /** Whether one more snapshot of this kind fits today's allowance. */
    public boolean canCapture(Snapshot.Kind kind, long day) {
        rollDay(day);
        if (!kind.countsTowardDay()) return true;
        return todayCount < PER_DAY && todayKinds.getOrDefault(kind, 0) < kind.dailyMax();
    }

    public String newId(long day) {
        return "d" + day + "_" + (nextSeq++);
    }

    /**
     * Adds a snapshot, respecting the daily cap, and keeps only the {@link #KEEP} most recent. Returns the ids that
     * were rotated out (their files should be deleted), or empty if the snapshot did not fit today.
     */
    public Optional<List<String>> add(Snapshot s) {
        if (!canCapture(s.kind(), s.day())) return Optional.empty();
        if (s.kind().countsTowardDay()) {
            todayCount++;
            todayKinds.merge(s.kind(), 1, Integer::sum);
        }
        snapshots.add(s);
        List<String> removed = new ArrayList<>();
        while (snapshots.size() > KEEP) {
            Snapshot old = snapshots.remove(0);
            if (!referencedByVision(old.id())) removed.add(old.id());
        }
        return Optional.of(removed);
    }

    /** Replaces today's snapshot of a kind (used when a better "most kills" place appears). Returns the replaced id. */
    public Optional<String> replaceToday(Snapshot s) {
        for (int i = snapshots.size() - 1; i >= 0; i--) {
            Snapshot old = snapshots.get(i);
            if (old.kind() == s.kind() && old.day() == s.day()) {
                snapshots.set(i, s);
                return Optional.of(old.id());
            }
        }
        return Optional.empty();
    }

    public Optional<Snapshot> snapshot(String id) {
        for (Snapshot s : snapshots) if (s.id().equals(id)) return Optional.of(s);
        return Optional.empty();
    }

    public List<Snapshot> today(long day) {
        List<Snapshot> out = new ArrayList<>();
        for (Snapshot s : snapshots) if (s.day() == day && s.kind() != Snapshot.Kind.VISION) out.add(s);
        return out;
    }

    private boolean referencedByVision(String snapshotId) {
        for (Vision v : visions) if (v.snapshotId().equals(snapshotId) && !v.found()) return true;
        return false;
    }

    // ---- nightmares ------------------------------------------------------------------------------------------------

    public void markDeath(Nightmares.Family family, long day) {
        deaths.add(new DeathMark(family.id, day));
        while (deaths.size() > 64) deaths.remove(0);
    }

    /** Deaths per family in the last seven play-days. */
    public Map<Nightmares.Family, Integer> weeklyMarks(long today) {
        Map<Nightmares.Family, Integer> out = new EnumMap<>(Nightmares.Family.class);
        for (DeathMark m : deaths) {
            if (today - m.day() < 7) Nightmares.Family.byId(m.family()).ifPresent(f -> out.merge(f, 1, Integer::sum));
        }
        return out;
    }

    public void clearFamily(Nightmares.Family family) {
        deaths.removeIf(m -> m.family().equals(family.id));
    }

    // ---- intention -------------------------------------------------------------------------------------------------

    /** Tonight's intention, or "" (an intention lasts until the next morning). */
    public String intention(long day) {
        return intentionDay == day ? intention : "";
    }

    public void setIntention(String intention, long day) {
        this.intention = intention;
        this.intentionDay = day;
    }

    // ---- history ---------------------------------------------------------------------------------------------------

    public void addHistory(DreamRecord r) {
        history.add(r);
        while (history.size() > HISTORY) {
            // keep recorded dreams as long as possible
            int drop = 0;
            for (int i = 0; i < history.size(); i++) {
                if (!history.get(i).recorded()) {
                    drop = i;
                    break;
                }
            }
            history.remove(drop);
        }
    }

    public Optional<Vision> vision(String id) {
        for (Vision v : visions) if (v.id().equals(id)) return Optional.of(v);
        return Optional.empty();
    }

    public void putVision(Vision v) {
        for (int i = 0; i < visions.size(); i++) {
            if (visions.get(i).id().equals(v.id())) {
                visions.set(i, v);
                return;
            }
        }
        visions.add(v);
        while (visions.size() > 24) visions.remove(0);
    }

    /** A prepared vision not yet shown in a dream. */
    public Optional<Vision> pendingVision() {
        for (Vision v : visions) if (!v.shown() && !v.found()) return Optional.of(v);
        return Optional.empty();
    }

    // ---- save ------------------------------------------------------------------------------------------------------

    private static final Codec<Map<String, Integer>> INT_MAP = Codec.unboundedMap(Codec.STRING, Codec.INT);

    @Override
    public void serialize(ValueOutput out) {
        out.putInt("schema", SCHEMA);
        var snaps = out.list("snapshots", Snapshot.CODEC);
        snapshots.forEach(snaps::add);
        out.putInt("next_seq", nextSeq);
        out.putLong("journal_day", journalDay);
        out.putInt("today_count", todayCount);
        Map<String, Integer> kinds = new HashMap<>();
        todayKinds.forEach((k, v) -> kinds.put(k.name(), v));
        out.store("today_kinds", INT_MAP, kinds);
        out.store("biomes", Codec.STRING.listOf(), List.copyOf(biomesSeen));
        out.store("structures", Codec.STRING.listOf(), List.copyOf(structuresSeen));
        Map<String, Integer> areas = new HashMap<>();
        killAreas.forEach((k, v) -> areas.put(Long.toString(k), v));
        out.store("kill_areas", INT_MAP, areas);
        var marks = out.list("deaths", DeathMark.CODEC);
        deaths.forEach(marks::add);
        out.putString("intention", intention);
        out.putLong("intention_day", intentionDay);
        out.putLong("wbtb_until", wbtbUntil);
        out.putInt("recall", recall);
        out.putLong("last_woke", lastWoke);
        out.putLong("last_woke_dream", lastWokeDream);
        var vis = out.list("visions", Vision.CODEC);
        visions.forEach(vis::add);
        var hist = out.list("history", DreamRecord.CODEC);
        history.forEach(hist::add);
        out.putLong("vision_prep_day", visionPrepDay);
        out.putInt("dreams", dreams);
        out.putBoolean("lucid_ever", lucidEver);
        out.store("signs_seen", Codec.STRING.listOf(), List.copyOf(signsSeen));
    }

    @Override
    public void deserialize(ValueInput in) {
        snapshots.clear();
        in.listOrEmpty("snapshots", Snapshot.CODEC).stream().forEach(snapshots::add);
        nextSeq = in.getIntOr("next_seq", 0);
        journalDay = in.getLongOr("journal_day", -1);
        todayCount = in.getIntOr("today_count", 0);
        todayKinds.clear();
        in.read("today_kinds", INT_MAP).ifPresent(m -> m.forEach((k, v) -> {
            try {
                todayKinds.put(Snapshot.Kind.valueOf(k), v);
            } catch (IllegalArgumentException ignored) {
            }
        }));
        biomesSeen.clear();
        in.read("biomes", Codec.STRING.listOf()).ifPresent(biomesSeen::addAll);
        structuresSeen.clear();
        in.read("structures", Codec.STRING.listOf()).ifPresent(structuresSeen::addAll);
        killAreas.clear();
        in.read("kill_areas", INT_MAP).ifPresent(m -> m.forEach((k, v) -> {
            try {
                killAreas.put(Long.parseLong(k), v);
            } catch (NumberFormatException ignored) {
            }
        }));
        deaths.clear();
        in.listOrEmpty("deaths", DeathMark.CODEC).stream().forEach(deaths::add);
        intention = in.getStringOr("intention", "");
        intentionDay = in.getLongOr("intention_day", -1);
        wbtbUntil = in.getLongOr("wbtb_until", 0);
        recall = in.getIntOr("recall", 0);
        lastWoke = in.getLongOr("last_woke", -1);
        lastWokeDream = in.getLongOr("last_woke_dream", -1);
        visions.clear();
        in.listOrEmpty("visions", Vision.CODEC).stream().forEach(visions::add);
        history.clear();
        in.listOrEmpty("history", DreamRecord.CODEC).stream().forEach(history::add);
        visionPrepDay = in.getLongOr("vision_prep_day", -1);
        dreams = in.getIntOr("dreams", 0);
        lucidEver = in.getBooleanOr("lucid_ever", false);
        signsSeen.clear();
        in.read("signs_seen", Codec.STRING.listOf()).ifPresent(signsSeen::addAll);
    }
}
