package dev.awaken.citizens;

import dev.awaken.Awaken;
import dev.awaken.citizens.api.Settlements;
import dev.awaken.city.CityPlan;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.RenownHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the city remembers of players (brief M3 §6, SPEC §16.6 first step). A citizen who sees you about grows familiar
 * with you; a deed counts only if someone with line of sight within 24 blocks saw it, and they remember it. Once a
 * play-day, memories spread through households, workplaces and taverns, and fade a little. Tiers: Stranger, Familiar
 * face, Known, per citizen (what that one knows) and for the city (how many know you). At Known a citizen greets you
 * by name. Also the street's voice: stall calls, the Watch calling the hour, the crier's news. Installs a minimal
 * {@code RenownHooks} (witness, tier) until the renown workstream (W5) takes over.
 */
final class Recognition {
    static final Identifier HIT_CITIZEN = Awaken.id("hit_citizen");
    /** Familiarity from only seeing someone about stops here; deeds and gossip go further. */
    static final int SEEN_CAP = 35, SEEN_STEP = 2, SEEN_EVERY = 2400;
    static final int FAMILIAR = 20, KNOWN = 55;
    /** The city's tier: this many citizens who know you. Provisional (W5 sets them from the pacing sim). */
    static final int CITY_FAMILIAR = 30, CITY_KNOWN = 24;
    static final int GREET_EVERY = 9000, TALK_GAP = 140, HEAR_GAP = 100;

    final Settlement c;
    final Roster r;
    final CitizensData d;
    private final Map<UUID, long[]> lastSeen = new HashMap<>(), lastGreet = new HashMap<>();
    private final Map<UUID, Long> lastTalk = new HashMap<>(), lastHeard = new HashMap<>();
    /** Citizens sharing each tavern (gossip goes round the taverns too). */
    private final int[][] tavernMates;
    long greetings, witnessed;

    Recognition(Settlement c) {
        this.c = c;
        this.r = c.roster;
        this.d = c.data;
        Map<Integer, List<Integer>> by = new HashMap<>();
        for (int i = 0; i < r.citizens; i++) by.computeIfAbsent(r.tavern[i], k -> new ArrayList<>()).add(i);
        tavernMates = new int[r.citizens][];
        for (int i = 0; i < r.citizens; i++) tavernMates[i] = by.get(r.tavern[i]).stream().mapToInt(Integer::intValue).toArray();
        int quarter = Math.max(1, r.citizens / 4);
        cityFamiliar = Math.min(CITY_FAMILIAR, quarter);
        cityKnown = Math.min(CITY_KNOWN, quarter);
    }

    // ---- tiers --------------------------------------------------------------------------------------------------

    static int personal(int know) {
        return know >= KNOWN ? RenownHooks.KNOWN : know >= FAMILIAR ? RenownHooks.FAMILIAR : RenownHooks.STRANGER;
    }

    int tier(Player p) {
        CitizensData.Memory m = d.memory(p.getUUID(), "", false);
        if (m == null) return RenownHooks.STRANGER;
        int fam = 0, known = 0;
        for (int i = 0; i < r.citizens; i++) {
            if (m.know[i] >= FAMILIAR) fam++;
            if (m.know[i] >= KNOWN) known++;
        }
        return known >= cityKnown ? RenownHooks.KNOWN : fam >= cityFamiliar ? RenownHooks.FAMILIAR : RenownHooks.STRANGER;
    }

    /**
     * How many of its people must know a player for the settlement to (SPEC §28.4): Carillon's 30 and 24; a smaller
     * place a quarter of its people (a hamlet of six, two), never more than Carillon asks.
     */
    private final int cityFamiliar, cityKnown;

    /** How one citizen sees a player: what they know themselves, or what the whole city does if that's more. */
    int tierFor(ServerPlayer p, int rec) {
        CitizensData.Memory m = d.memory(p.getUUID(), p.getName().getString(), true);
        return Math.max(personal(m.know[rec]), tier(p));
    }

    // ---- seeing and greeting ------------------------------------------------------------------------------------------

    /** From a body's think when a player is within 9 blocks: seen about; a greeting when close. True when it looked at them. */
    boolean think(CitizenBody b, int rec, ServerPlayer p, double distSq, long now) {
        if (!r.isCitizen(rec) || d.role[rec] != 0 || d.hurtUntil[rec] > now || p.isSpectator()) return false;
        long[] seen = lastSeen.computeIfAbsent(p.getUUID(), k -> new long[r.records]);
        if (now - seen[rec] > SEEN_EVERY && sees(b, p)) {
            seen[rec] = now;
            CitizensData.Memory m = d.memory(p.getUUID(), p.getName().getString(), true);
            if (m.know[rec] < SEEN_CAP) {
                m.know[rec] = Math.min(SEEN_CAP, m.know[rec] + SEEN_STEP);
                d.setDirty();
            }
        }
        if (distSq > 4.6 * 4.6) return false;
        long[] greeted = lastGreet.computeIfAbsent(p.getUUID(), k -> new long[r.records]);
        long talk = lastTalk.getOrDefault(p.getUUID(), 0L);
        if (now - greeted[rec] < GREET_EVERY || now - talk < TALK_GAP) return false;
        // facing each other, more or less: the citizen on the player's screen and in plain sight
        Vec3 look = p.getViewVector(1f);
        double dx = b.getX() - p.getX(), dz = b.getZ() - p.getZ(), len = Math.max(1e-3, Math.hypot(dx, dz));
        if ((dx * look.x + dz * look.z) / len < 0.5 || !sees(b, p)) return false;
        greet(b, rec, p, now, false);
        return true;
    }

    /** Says hello the way this citizen knows the player (a stranger often only looks). */
    void greet(CitizenBody b, int rec, ServerPlayer p, long now, boolean force) {
        lastGreet.computeIfAbsent(p.getUUID(), k -> new long[r.records])[rec] = now;
        int tier = tierFor(p, rec);
        int tod = c.tod();
        long seed = rec * 7919L + p.getUUID().getLeastSignificantBits() + PlayDays.current(c.server) * 31L + greetings;
        b.lookAt(p, 60);
        if (tier == RenownHooks.STRANGER && !force && Math.floorMod(CitizenWork.mixHash(seed), 10) >= 4) return; // just a look
        String job = Roster.JOBS[r.job[rec]];
        String t = Lines.TIERS[Math.min(2, tier)];
        Lines lines = Lines.get();
        String line;
        Settlements.Voice voice = c.site.carillon() ? null : Settlements.voice();
        if (voice != null) {
            // (SPEC §28.6) a village speaks by its own standing: stranger, known, friend, kin
            String st = voice.standing(c, p);
            line = lines.pick(seed, tod, "village.greet." + job + "." + st, "village.greet." + job + "." + st, "village.greet.any." + st);
            if (line.isEmpty()) line = lines.pick(seed, tod, "greet." + job + "." + t, "greet.any." + t);
            if (st.equals("stranger") && !force && Math.floorMod(CitizenWork.mixHash(seed + 7), 10) >= 6) return;
            line = voice.fill(c, p, Lines.fill(line, p.getName().getString(), district(b), "", hour(tod)));
            if (line.isEmpty()) return;
            b.gesture(st.equals("kin") || st.equals("friend") ? Act.WAVE : st.equals("known") ? Act.NOD : Act.NONE, 32);
            CitizensNet.say(p, b, line, 70, 0);
            lastTalk.put(p.getUUID(), now);
            greetings++;
            return;
        }
        line = !r.named[rec].isEmpty() ? lines.pick(seed, tod, "named." + r.named[rec] + "." + t)
                : lines.pick(seed, tod, "greet." + job + "." + t, "greet." + job + "." + t, "greet.any." + t);
        if (line.isEmpty()) return;
        line = Lines.fill(line, p.getName().getString(), district(b), "", hour(tod));
        b.gesture(tier >= RenownHooks.KNOWN ? Act.WAVE : tier == RenownHooks.FAMILIAR ? Act.NOD : Act.NONE, 32);
        CitizensNet.say(p, b, line, 70, 0);
        lastTalk.put(p.getUUID(), now);
        greetings++;
        Awaken.LOG.info("[citizens] greet {} ({}) -> {} [{}]: {}", r.name(rec), job, p.getName().getString(), t, line);
    }

    /** A body says something to everyone near (a stall's call, the hour, news), at most one line per player per few seconds. */
    boolean aloud(CitizenBody b, String line, int ticks, int kind, double radius, long now) {
        if (line.isEmpty()) return false;
        boolean anyone = false;
        for (ServerPlayer p : c.level.players()) {
            if (p.distanceToSqr(b) > radius * radius) continue;
            if (now - lastHeard.getOrDefault(p.getUUID(), 0L) < HEAR_GAP && kind < 2) continue;
            lastHeard.put(p.getUUID(), now);
            CitizensNet.say(p, b, line, ticks, kind);
            anyone = true;
        }
        return anyone;
    }

    private boolean sees(CitizenBody b, ServerPlayer p) {
        Vec3 from = b.getEyePosition(), to = p.getEyePosition();
        var hit = c.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, b));
        return hit.getType() == HitResult.Type.MISS;
    }

    /** The part of its settlement a body stands in, as its people name it ({district} in what they say). */
    String district(CitizenBody b) {
        return c.site.districtName(c.site.district((int) Math.floor(b.getX()), (int) Math.floor(b.getZ())));
    }

    /** Carillon's district names (its plan's ids). */

    static String districtName(int id) {
        return switch (id) {
            case CityPlan.MARKET -> "the Market Ward";
            case CityPlan.BELLWATCH -> "Bellwatch";
            case CityPlan.QUAY_D -> "Lantern Quay";
            case CityPlan.TERRACE -> "the High Terrace";
            case CityPlan.WORKS -> "the Works";
            case CityPlan.GARDEN_D -> "the Garden Ward";
            case CityPlan.WALLRING -> "the Wallring";
            default -> "the city";
        };
    }

    static int hour(int tod) {
        int h = (6 + tod / 1000) % 24;
        return h % 12 == 0 ? 12 : h % 12;
    }

    // ---- deeds -----------------------------------------------------------------------------------------------------

    /** Citizens (record ids) with line of sight to a point within a radius: bodies look from their eyes, the rest from where the ledger has them. */
    List<Integer> witnesses(Vec3 at, double radius) {
        List<Integer> out = new ArrayList<>();
        double[] tmp = new double[3];
        int rays = 0;
        long now = c.level.getGameTime();
        for (int i = 0; i < r.citizens && rays < 48; i++) {
            if (d.state[i] == CitizensData.INSIDE || d.state[i] == CitizensData.ABSENT || d.hurtUntil[i] > now) continue;
            CitizenBody b = c.ledger.body[i];
            Vec3 eye;
            if (b != null) eye = b.getEyePosition();
            else {
                if (!c.ledger.position(i, tmp)) continue;
                eye = new Vec3(tmp[0], tmp[1] + 1.6, tmp[2]);
            }
            if (eye.distanceToSqr(at) > radius * radius) continue;
            if (!c.level.isLoaded(BlockPos.containing(eye))) continue;
            rays++;
            var hit = c.level.clip(new ClipContext(eye, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, b));
            if (hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(at) < 1.5 * 1.5) out.add(i);
        }
        return out;
    }

    /** A deed at a place: every citizen who saw it remembers it (familiarity, regard and the deed itself). Returns how many saw. */
    int witness(ServerPlayer p, Identifier deed, BlockPos pos, int weight) {
        List<Integer> saw = witnesses(Vec3.atCenterOf(pos), 24);
        if (saw.isEmpty()) return 0;
        CitizensData.Memory m = d.memory(p.getUUID(), p.getName().getString(), true);
        int gain = (int) Math.clamp(6L * Math.abs(weight), 4, 40);
        int reg = (int) Math.signum(weight) * Math.min(30, 5 * Math.abs(weight));
        for (int i : saw) {
            m.know[i] = Math.min(100, m.know[i] + gain);
            m.regard[i] = Math.clamp(m.regard[i] + reg, -100, 100);
        }
        int district = c.site.district(pos.getX(), pos.getZ());
        m.deeds.add(new CitizensData.Deed(deed.toString(), PlayDays.current(c.server), weight, district));
        while (m.deeds.size() > 16) m.deeds.removeFirst();
        d.setDirty();
        witnessed++;
        Awaken.LOG.info("[citizens] deed {} by {} (weight {}) seen by {}", deed, p.getName().getString(), weight, saw.size());
        return saw.size();
    }

    /** Credit without line of sight (a gate closed outside the wall, reported by the Watch on it). */
    void credit(ServerPlayer p, List<Integer> by, Identifier deed, int weight) {
        CitizensData.Memory m = d.memory(p.getUUID(), p.getName().getString(), true);
        for (int i : by) m.know[i] = Math.min(100, m.know[i] + (int) Math.clamp(6L * Math.abs(weight), 4, 40));
        m.deeds.add(new CitizensData.Deed(deed.toString(), PlayDays.current(c.server), weight, 0));
        while (m.deeds.size() > 16) m.deeds.removeFirst();
        d.setDirty();
    }

    /** Commands: every citizen knows this player this well. */
    void setAll(ServerPlayer p, int know) {
        CitizensData.Memory m = d.memory(p.getUUID(), p.getName().getString(), true);
        for (int i = 0; i < r.citizens; i++) m.know[i] = know;
        lastGreet.remove(p.getUUID());
        d.setDirty();
    }

    // ---- gossip ----------------------------------------------------------------------------------------------------

    void tick(long now, int tod) {
        long day = PlayDays.current(c.server);
        if (d.gossipDay < 0) d.gossipDay = day;
        if (day > d.gossipDay) {
            d.gossipDay = day;
            gossip();
        }
        if (now % 20 == 3) street(now, tod);
    }

    /**
     * Once a play-day: what each citizen knows of a player spreads to their household (half), their workmates (a
     * third) and their tavern (a quarter); then everything fades a point.
     */
    void gossip() {
        for (CitizensData.Memory m : d.memories) {
            int[] know = m.know.clone(), reg = m.regard.clone();
            for (int i = 0; i < r.citizens; i++) {
                if (m.know[i] < 15) continue;
                spread(know, reg, m, i, householdOf(i), 0.5);
                spread(know, reg, m, i, groupOf(i), 0.34);
                spread(know, reg, m, i, tavernMates[i], 0.25);
            }
            for (int i = 0; i < r.citizens; i++) {
                m.know[i] = Math.max(0, know[i] - 1);
                m.regard[i] = reg[i] > 0 ? reg[i] - 1 : reg[i] < 0 ? reg[i] + 1 : 0;
            }
        }
        d.setDirty();
        Awaken.LOG.info("[citizens] gossip spread for {} players", d.memories.size());
    }

    private void spread(int[] know, int[] reg, CitizensData.Memory m, int from, int[] to, double share) {
        if (to == null) return;
        int k = (int) (m.know[from] * share), g = (int) (m.regard[from] * share);
        for (int j : to) {
            if (j == from || j >= r.citizens) continue;
            if (k > know[j]) know[j] = k;
            if (Math.abs(g) > Math.abs(reg[j])) reg[j] = g;
        }
    }

    private int[] householdOf(int i) {
        int h = r.household[i];
        for (Roster.Household hh : r.households) if (hh.id() == h) return hh.members();
        return null;
    }

    private int[] groupOf(int i) {
        int g = r.group[i];
        for (Roster.Group gg : r.groups) if (gg.id() == g) return gg.members();
        return null;
    }

    // ---- the street's voice --------------------------------------------------------------------------------------------

    /** Once a second: one body near a player may call out (wares, the hour at night, a child at play, the crier's news). */
    private void street(long now, int tod) {
        Lines lines = Lines.get();
        CitizenBody pick = null;
        for (CitizenBody b : c.crowd.bodies) {
            if (b.rec < 0 || b.home != c || b.nearestSq > 14 * 14 || !r.isCitizen(b.rec) || d.role[b.rec] != 0) continue;
            int job = r.job[b.rec];
            Act a = b.act();
            boolean caller = job == Roster.STALLKEEPER && a == Act.SERVE || job == Roster.CRIER && a == Act.PROCLAIM
                    || job == Roster.WATCH && tod % 1000 < 40 && tod > 15000 || job == Roster.CHILD && a == Act.PLAY
                    || job == Roster.PORTER && a == Act.CARRY_BACK || job == Roster.SMITH && a == Act.HAMMER
                    || job == Roster.BAKER && tod < 4000 && a != Act.NONE
                    || job >= Roster.FARMER && !c.site.carillon() && (a == Act.HAMMER || a == Act.LEAN || a == Act.SIT || a == Act.SET_DOWN
                    || job == Roster.HUNTER && a == Act.NONE && tod >= 10000);
            if (!caller) continue;
            long phase = CitizenWork.mixHash(b.rec * 131L + now / 20);
            int every = job == Roster.CRIER ? 9 : job == Roster.WATCH ? 2 : 22;
            if (Math.floorMod(phase, (long) every) != 0) continue;
            pick = b;
            break;
        }
        if (pick == null) return;
        int rec = pick.rec, job = r.job[rec];
        long seed = rec * 31L + now / 20;
        String line = switch (job) {
            case Roster.STALLKEEPER -> {
                String good = "produce";
                int place = d.place[rec];
                for (Roster.Stall st : r.stalls) if (st.keeper() == place) good = st.good();
                yield c.site.carillon() ? lines.pick(seed, tod, "calls.stallkeeper." + good)
                        : lines.pick(seed, tod, "village.calls.stallkeeper." + good, "calls.stallkeeper." + good);
            }
            case Roster.WATCH -> lines.pick(seed, tod, "calls.watch_night");
            case Roster.CRIER -> c.events.news(seed, tod);
            default -> c.site.carillon() ? lines.pick(seed, tod, "calls." + Roster.JOBS[job])
                    : lines.pick(seed, tod, "village.calls." + Roster.JOBS[job], "calls." + Roster.JOBS[job]);
        };
        line = Lines.fill(line, c.events.aboutName(), district(pick), "", hour(tod));
        int kind = job == Roster.CRIER ? 2 : 1;
        if (aloud(pick, line, job == Roster.CRIER ? 110 : 60, kind, job == Roster.CRIER ? 28 : 18, now) && job == Roster.CRIER)
            c.level.playSound(null, pick.blockPosition(), CitizensContent.HANDBELL.get(), SoundSource.AMBIENT, 0.35f, 1f);
    }
}
