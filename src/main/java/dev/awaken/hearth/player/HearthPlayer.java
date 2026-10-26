package dev.awaken.hearth.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.hearth.echo.EchoRecord;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player hearth state, saved on the player and kept on death. Mutate on the server, then call
 * {@code HearthViews.refresh(player)} so the owner's client sees it.
 */
public final class HearthPlayer {
    /** 2: {@code respawn_hearth}, which shared Hearth you wake at (SPEC "Together"). 3: {@code home} (W1: Rested, the Ember, rooms told). */
    public static final int SCHEMA = 3;

    /** A rumour Mira told: a Codex entry pointing at a gate or ruin. */
    public record Rumour(String id, String title, String body, long day) {
        public static final Codec<Rumour> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("id").forGetter(Rumour::id),
                Codec.STRING.fieldOf("title").forGetter(Rumour::title),
                Codec.STRING.fieldOf("body").forGetter(Rumour::body),
                Codec.LONG.fieldOf("day").forGetter(Rumour::day)
        ).apply(i, Rumour::new));
    }

    public final List<EchoRecord> echoes = new ArrayList<>();
    /** Whether the field army is currently called out (re-summoned on login/respawn). */
    public boolean fieldOut;
    public final List<Rumour> rumours = new ArrayList<>();
    /** Bosses that already gave their guaranteed first trophy. */
    public final Set<String> trophyBosses = new LinkedHashSet<>();
    public boolean respawnAtHearth;
    /** The owner of the Hearth you wake at (the one whose Anchor you used last); null = your home. */
    public UUID respawnHearth;
    /** Play-day of the last failed Reassessment (one attempt per play-day). */
    public long trialFailDay = -1;
    /** A Hardline Penalty Zone is owed (started the next time the player is home and free). */
    public boolean penaltyPending;
    /** Personal best per training course key, in ticks. */
    public final Map<String, Integer> bests = new HashMap<>();
    /** One-time explanations already shown. */
    public final Set<String> tips = new LinkedHashSet<>();
    /** Play-day Mira last gave a rumour (one per play-day). */
    public long rumourDay = -1;
    /** A second rumour that play-day (a Lookout at home). */
    public long rumourDay2 = -1;
    /** Game time Rested ends (0 = not Rested), and the Comfort it was set with. */
    public long restedUntil;
    public int restedComfort;
    /** Whether the player has ever been given a Hearth Ember (a lost one comes back from the camp). */
    public boolean emberGiven;
    /** Rooms whose name this player has been shown: room key → grade and name hash (shown again when either changes). */
    public final Map<Long, Integer> roomsTold = new java.util.LinkedHashMap<>();

    public HearthPlayer() {}

    public Optional<EchoRecord> echo(UUID id) {
        for (EchoRecord e : echoes) if (e.id().equals(id)) return Optional.of(e);
        return Optional.empty();
    }

    public void replace(EchoRecord r) {
        for (int i = 0; i < echoes.size(); i++) {
            if (echoes.get(i).id().equals(r.id())) {
                echoes.set(i, r);
                return;
            }
        }
    }

    public long fieldCount() {
        return echoes.stream().filter(EchoRecord::field).count();
    }

    private record Saved(int schema, List<EchoRecord> echoes, boolean fieldOut, List<Rumour> rumours, List<String> trophyBosses,
                         boolean respawnAtHearth, long trialFailDay, boolean penaltyPending, Map<String, Integer> bests,
                         List<String> tips, long rumourDay, Optional<UUID> respawnHearth, Home home) {}

    /** Schema 3 (W1): Rested, the Ember and the rooms already told, as one part. */
    private record Home(long restedUntil, int restedComfort, boolean emberGiven, List<Long> told, List<Integer> toldAs, long rumourDay2) {
        static final Home NONE = new Home(0, 0, false, List.of(), List.of(), -1);
        static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("rested_until", 0L).forGetter(Home::restedUntil),
                Codec.INT.optionalFieldOf("rested_comfort", 0).forGetter(Home::restedComfort),
                Codec.BOOL.optionalFieldOf("ember_given", false).forGetter(Home::emberGiven),
                Codec.LONG.listOf().optionalFieldOf("told", List.of()).forGetter(Home::told),
                Codec.INT.listOf().optionalFieldOf("told_as", List.of()).forGetter(Home::toldAs),
                Codec.LONG.optionalFieldOf("rumour_day_2", -1L).forGetter(Home::rumourDay2)
        ).apply(i, Home::new));
    }

    /** At most this many rooms remembered as told (the oldest are forgotten: they'd be told once more). */
    public static final int TOLD_MAX = 96;

    public static final MapCodec<HearthPlayer> MAP_CODEC = RecordCodecBuilder.<Saved>mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(Saved::schema),
            EchoRecord.CODEC.listOf().optionalFieldOf("echoes", List.of()).forGetter(Saved::echoes),
            Codec.BOOL.optionalFieldOf("field_out", false).forGetter(Saved::fieldOut),
            Rumour.CODEC.listOf().optionalFieldOf("rumours", List.of()).forGetter(Saved::rumours),
            Codec.STRING.listOf().optionalFieldOf("trophy_bosses", List.of()).forGetter(Saved::trophyBosses),
            Codec.BOOL.optionalFieldOf("respawn_at_hearth", false).forGetter(Saved::respawnAtHearth),
            Codec.LONG.optionalFieldOf("trial_fail_day", -1L).forGetter(Saved::trialFailDay),
            Codec.BOOL.optionalFieldOf("penalty_pending", false).forGetter(Saved::penaltyPending),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("bests", Map.of()).forGetter(Saved::bests),
            Codec.STRING.listOf().optionalFieldOf("tips", List.of()).forGetter(Saved::tips),
            Codec.LONG.optionalFieldOf("rumour_day", -1L).forGetter(Saved::rumourDay),
            net.minecraft.core.UUIDUtil.CODEC.optionalFieldOf("respawn_hearth").forGetter(Saved::respawnHearth),
            Home.CODEC.optionalFieldOf("home", Home.NONE).forGetter(Saved::home)
    ).apply(i, Saved::new)).xmap(HearthPlayer::fromSaved, HearthPlayer::toSaved);

    private static HearthPlayer fromSaved(Saved s) {
        HearthPlayer h = new HearthPlayer();
        h.echoes.addAll(s.echoes);
        h.fieldOut = s.fieldOut;
        h.rumours.addAll(s.rumours);
        h.trophyBosses.addAll(s.trophyBosses);
        h.respawnAtHearth = s.respawnAtHearth;
        h.trialFailDay = s.trialFailDay;
        h.penaltyPending = s.penaltyPending;
        h.bests.putAll(s.bests);
        h.tips.addAll(s.tips);
        h.rumourDay = s.rumourDay;
        h.respawnHearth = s.respawnHearth.orElse(null);
        h.restedUntil = s.home.restedUntil;
        h.restedComfort = s.home.restedComfort;
        h.emberGiven = s.home.emberGiven;
        h.rumourDay2 = s.home.rumourDay2;
        for (int k = 0; k < s.home.told.size() && k < s.home.toldAs.size(); k++) h.roomsTold.put(s.home.told.get(k), s.home.toldAs.get(k));
        return h;
    }

    private Saved toSaved() {
        Home home = new Home(restedUntil, restedComfort, emberGiven, List.copyOf(roomsTold.keySet()), List.copyOf(roomsTold.values()), rumourDay2);
        return new Saved(SCHEMA, List.copyOf(echoes), fieldOut, List.copyOf(rumours), List.copyOf(trophyBosses),
                respawnAtHearth, trialFailDay, penaltyPending, Map.copyOf(bests), List.copyOf(tips), rumourDay, Optional.ofNullable(respawnHearth), home);
    }
}
