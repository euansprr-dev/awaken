package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Strikes;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.boss.FightScripts;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.Kits;
import dev.awaken.mobs.def.Ability;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Tier;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The fair-damage audit (SPEC §47.14): every blow our creatures and Lords can deal, measured on the reference player of
 * each rank through the real damage path (armour, VIT, the blow's class), and held to its budget. A creature is fought
 * at every rank (a gate's theme comes at any rank, scaled), never only its home.
 */
final class FairAudit {
    private FairAudit() {}

    /** Every string an entry can become: as written, and with its forks taken (SPEC §48.4). */
    static List<List<String>> forked(FightScripts.Entry e) {
        List<List<String>> out = new ArrayList<>(e.then());
        for (int f = 0; f < e.forks().size(); f++) {
            FightScripts.Fork fork = e.forks().get(f);
            for (List<String> then : List.copyOf(out)) {
                if (f >= then.size()) continue;
                for (java.util.Optional<String> pick : List.of(fork.guard(), fork.dodged(), fork.stayed())) {
                    if (pick.isEmpty()) continue;
                    List<String> changed = new ArrayList<>(then);
                    changed.set(f, pick.get());
                    if (!out.contains(changed)) out.add(changed);
                }
            }
        }
        return out;
    }

    /** Every string a fight can play: its pools' and its answers'. */
    static List<List<String>> strings(FightScripts.Fight fight) {
        List<List<String>> out = new ArrayList<>();
        for (FightScripts.Phase ph : fight.phases()) {
            for (FightScripts.Entry e : ph.pool()) {
                for (List<String> then : forked(e)) out.add(string(e.move(), then));
            }
        }
        fight.mind().ifPresent(mind -> {
            for (FightScripts.Answer a : mind.answers()) {
                for (String move : a.moves()) {
                    for (List<String> then : a.then()) out.add(string(move, then));
                }
            }
        });
        return out;
    }

    private static List<String> string(String first, List<String> then) {
        List<String> all = new ArrayList<>();
        all.add(first);
        all.addAll(then);
        return all;
    }

    static final Rank[] RANKS = {Rank.E, Rank.D, Rank.C, Rank.B, Rank.A, Rank.S, Rank.SOVEREIGN};

    /** Where a creature is fought: the rank of the player who belongs there, and the level it is met at. */
    record Place(Rank rank, int level) {}

    /** The Provings' Wardens and the rank each one's Reassessment is for (hearth.trial.Warden), met at that rank's first level. */
    static final Map<String, Rank> PROVINGS = Map.of("proving_ringer", Rank.D, "proving_examiner", Rank.C, "proving_unmade", Rank.B,
            "proving_sky_warden", Rank.A, "proving_keepers_shadow", Rank.S, "proving_last_loop", Rank.SOVEREIGN);

    /**
     * Every place a creature is met. A gate's (or the wild's, or a dream's) creature from its theme's home rank up to S,
     * at the gate's level (a dream fights at 0.8 of it, so this is the harder end). A stratum's at the stratum's level
     * (Strata.contentLevel: the odd strata open a rank early, so the one-rank-under reading is an early diver's). A
     * Sovereign in the Hollow Court at S, and the Reflection (sovereign_mirrors) again at the Sovereign Reassessment. A
     * Proving's Warden at its Reassessment's first level. The last at the Seed's level.
     */
    static List<Place> places(MobDef def) {
        List<Place> out = new ArrayList<>();
        Rank proving = PROVINGS.get(def.id);
        String stratum = "awaken:stratum_";
        if (proving != null) out.add(new Place(proving, proving.levelRequired));
        else if (def.theme.id.startsWith(stratum)) {
            int depth = Integer.parseInt(def.theme.id.substring(stratum.length()));
            int lv = dev.awaken.descent.Strata.contentLevel(depth);
            out.add(new Place(Rank.of(Math.min(6, (depth + 1) / 2)), lv));
        } else if (def.tier == Tier.SOVEREIGN) {
            out.add(new Place(Rank.S, Rank.S.contentLevel));
            if (def.id.equals("sovereign_mirrors")) out.add(new Place(Rank.SOVEREIGN, Rank.SOVEREIGN.levelRequired));
        } else if (def.tier == Tier.FINAL) out.add(new Place(Rank.S, dev.awaken.descent.Strata.contentLevel(dev.awaken.descent.Strata.COUNT)));
        else for (Rank r : RANKS) if (r.ordinal() >= def.theme.home.ordinal() && r != Rank.SOVEREIGN) out.add(new Place(r, Progression.contentLevel(r)));
        return out;
    }

    /** One blow measured: whose, which, at what rank, its share of the reference player's health, and its budget. */
    record Reading(String who, String blow, Rank rank, float share, float budget, float under) {
        boolean over() {
            return share > budget + 1.0E-4F;
        }

        @Override
        public String toString() {
            return who + " " + blow + " at " + rank.label + ": " + FairTests.pct(share) + " (budget " + FairTests.pct(budget) + (under >= 0
                    ? "; one rank under " + FairTests.pct(under) : "") + ")";
        }
    }

    /** The kinds of ability that strike, and the share of the ability's damage one strike deals (Abilities, AbilityRunner). */
    static float strikeShare(Ability.Kind k) {
        return switch (k) {
            case CLOUD -> 0.6F;
            case PULL -> 0.4F;
            case ROOT -> 0.5F;
            case LIFT, DARKNESS -> 0.3F;
            case HEAL, DECOY, GUARD, FRENZY, SUMMON -> 0F;
            default -> 1F;
        };
    }

    /**
     * What one blow costs {@code ref}: {@code dmg} landed as {@code cls} with its bolt and effect, plus what its effect and
     * burn take after it (poison stops at half a heart, so it counts in full: the budget is for a full health bar).
     */
    static float blow(ServerLevel level, AwakenMob by, ServerPlayer ref, Strikes.Kind cls, float dmg, dev.awaken.mobs.def.BoltKind bolt,
                      @org.jspecify.annotations.Nullable MobEffectInstance effect) {
        FairTests.pool(ref);
        Strikes.deliver(by, cls, () -> Abilities.hurt(level, by, ref, dmg, bolt, effect));
        return FairTests.raw(ref) + after(ref);
    }

    /** A melee blow as the creature deals it (its weapon, its touch traits), with what it leaves behind. */
    static float melee(ServerLevel level, AwakenMob by, ServerPlayer ref) {
        FairTests.pool(ref);
        Strikes.deliver(by, Strikes.Kind.LIGHT, () -> by.doHurtTarget(level, ref));
        return FairTests.raw(ref) + after(ref);
    }

    /** Poison, wither and burn left on {@code ref}, as a share of its health (1 a pulse). */
    static float after(ServerPlayer ref) {
        float dot = 0;
        MobEffectInstance poison = ref.getEffect(MobEffects.POISON), wither = ref.getEffect(MobEffects.WITHER);
        if (poison != null) dot += poison.getDuration() / (float) Math.max(1, 25 >> poison.getAmplifier());
        if (wither != null) dot += wither.getDuration() / (float) Math.max(1, 40 >> wither.getAmplifier());
        dot += ref.getRemainingFireTicks() / 20F;
        ref.removeAllEffects();
        ref.clearFire();
        return dot / ref.getMaxHealth();
    }

    /** One blow, measured on a player: its share of their health. */
    interface Blow {
        float on(ServerPlayer p);
    }

    static List<Reading> creature(ServerLevel level, MobDef def, Map<Rank, ServerPlayer> refs, Vec3 at, float budget, float perilous) {
        List<Reading> out = new ArrayList<>();
        for (Place place : places(def)) {
            Rank rank = place.rank();
            ServerPlayer ref = refs.get(rank), under = rank.ordinal() > 0 ? refs.get(RANKS[rank.ordinal() - 1]) : null;
            int lv = place.level();
            MobRole role = new MobRole(def.tier.role, lv, def.theme.id, def.tier.isBoss() ? Optional.of(def.bossId()) : Optional.empty(), "");
            AwakenMob mob = Mobs.spawn(level, def, at, lv, role, Set.of());
            if (mob == null) continue;
            try {
                mob.setNoAi(true);
                float b = budget;
                java.util.function.BiConsumer<String, Blow> read = (name, blow) -> out.add(new Reading(def.id, name, rank, blow.on(ref), b, under == null ? -1 : blow.on(under)));
                // a scripted Lord never swings as a lesser creature does: its fight script's moves are its blows
                if (!mob.scriptedFight()) read.accept("melee", p -> melee(level, mob, p));
                for (MobDef.Phase ph : def.phases) {
                    for (Ability a : ph.abilities()) {
                        float s = strikeShare(a.kind());
                        if (s <= 0) continue;
                        float dmg = Abilities.damage(mob, a) * s;
                        float ba = a.strike() == Strikes.Kind.PERILOUS ? Math.max(b, perilous) : b;
                        out.add(new Reading(def.id, a.name() + " (" + a.kind() + ")", rank, blow(level, mob, ref, a.strike(), dmg, Abilities.bolt(mob, a), Abilities.effect(mob, a)),
                                ba, under == null ? -1 : blow(level, mob, under, a.strike(), dmg, Abilities.bolt(mob, a), Abilities.effect(mob, a))));
                    }
                }
                Kits.Kit kit = Kits.get(def.id);
                if (kit != null) {
                    // each move lands once on whoever it touches (FightRunner.strike), as the class it is
                    Map<String, java.util.function.Consumer<ServerPlayer>> lands = new java.util.HashMap<>();
                    Map<String, float[]> moved = new java.util.HashMap<>();
                    for (KitMoves.Move m : kit.moves().moves()) {
                        if (m.hits().isEmpty() || m.damage() <= 0) continue;
                        float dmg = (float) mob.getAttributeValue(Attributes.ATTACK_DAMAGE) * m.damage();
                        Strikes.Kind cls = m.kind() == Strikes.Kind.NONE ? Strikes.Kind.HEAVY : m.kind();
                        java.util.function.Consumer<ServerPlayer> land = p -> {
                            p.damageCooldownTime = 0; // a string's next move comes after the last one's i-frames
                            Strikes.deliver(mob, cls, () -> p.hurtServer(level, level.damageSources().mobAttack(mob), dmg));
                        };
                        lands.put(m.id(), land);
                        Blow move = p -> {
                            FairTests.pool(p);
                            land.accept(p);
                            return FairTests.raw(p) + after(p);
                        };
                        float bm = cls == Strikes.Kind.PERILOUS || m.signature() ? Math.max(b, perilous) : b;
                        float[] got = {move.on(ref), under == null ? -1 : move.on(under)};
                        moved.put(m.id(), got);
                        out.add(new Reading(def.id, "move " + m.id() + " (" + cls + ")", rank, got[0], bm, got[1]));
                    }
                    FightScripts.Fight fight = kit.fight();
                    if (fight != null) {
                        // a string is one source: all of it lands on one player, the fair guard holding it to half
                        for (List<String> all : strings(fight)) {
                            if (all.stream().noneMatch(lands::containsKey)) continue;
                            float[] raw = {0};
                            Blow string = p -> {
                                FairTests.pool(p);
                                for (String id : all) if (lands.containsKey(id)) lands.get(id).accept(p);
                                if (p == ref) raw[0] = FairTests.raw(p);
                                return FairTests.share(p) + after(p);
                            };
                            float on = string.on(ref);
                            STRINGS.add(new Reading(def.id, "string " + String.join(" > ", all), rank, raw[0], FairBudget.STRING, on));
                            out.add(new Reading(def.id, "string " + String.join(" > ", all), rank, on, FairBudget.STRING,
                                    under == null ? -1 : string.on(under)));
                        }
                        // the move it opens with as you walk in: it may knock you about, never take much
                        fight.intro().move().ifPresent(id -> {
                            float[] m = moved.get(id);
                            if (m != null) out.add(new Reading(def.id, "intro " + id, rank, m[0], FairBudget.INTRO, m[1]));
                        });
                    }
                }
            } finally {
                mob.discard();
            }
        }
        return out;
    }

    /** Every string's own number beside what the guard let land of it (in {@code under}), for the proof (SPEC §47.14). */
    static final List<Reading> STRINGS = new ArrayList<>();

    static void log(String what, List<Reading> all) {
        List<Reading> over = all.stream().filter(Reading::over).sorted(Comparator.comparing(Reading::share).reversed()).toList();
        Map<Rank, Reading> worst = new EnumMap<>(Rank.class);
        for (Reading r : all) worst.merge(r.rank(), r, (a, b) -> b.share() > a.share() ? b : a);
        for (Reading r : worst.values()) Awaken.LOG.info("[fair] {} worst at {}: {}", what, r.rank().label, r);
        Awaken.LOG.info("[fair] {}: {} blows measured, {} over budget", what, all.size(), over.size());
        for (Reading r : over) Awaken.LOG.info("[fair] over: {}", r);
        // what each over-budget creature's damage would need to be scaled by to fit everywhere (its worst blow)
        Map<String, Float> fit = new java.util.TreeMap<>();
        for (Reading r : over) fit.merge(r.who() + " " + r.blow().replaceAll(" at .*", ""), r.budget() / r.share(), Math::min);
        fit.forEach((k, v) -> Awaken.LOG.info("[fair] fit: {} x{}", k, String.format("%.2f", v)));
        for (Reading r : STRINGS) if (r.share() > FairBudget.STRING) Awaken.LOG.info("[fair] held: {} {} at {}: {} of its own, {} landed",
                r.who(), r.blow(), r.rank().label, FairTests.pct(r.share()), FairTests.pct(r.under()));
        STRINGS.clear();
    }

    static String overList(List<Reading> all) {
        List<Reading> over = all.stream().filter(Reading::over).sorted(Comparator.comparing(Reading::share).reversed()).toList();
        StringBuilder sb = new StringBuilder(over.size() + " over budget");
        for (int i = 0; i < Math.min(12, over.size()); i++) sb.append("; ").append(over.get(i));
        return sb.toString();
    }

    static Map<Rank, ServerPlayer> refs(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = -1; x <= 8; x++) for (int z = -1; z <= 8; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
        Map<Rank, ServerPlayer> refs = new EnumMap<>(Rank.class);
        for (int i = 0; i < RANKS.length; i++) refs.put(RANKS[i], FairTests.reference(h, RANKS[i], new BlockPos(1 + i, 1, 1)));
        return refs;
    }

    /** Every creature that isn't a Lord (gates' normals, elites and wardens, the wild's, the deep's), one a tick. */
    static void creatures(GameTestHelper h) {
        Map<Rank, ServerPlayer> refs = refs(h);
        List<MobDef> defs = Bestiary.all().stream().filter(d -> !d.tier.isBoss()).sorted(Comparator.comparing(d -> d.id)).toList();
        Vec3 at = h.absoluteVec(new Vec3(4.5, 1, 5.5));
        List<Reading> all = new ArrayList<>();
        int[] i = {0};
        h.onEachTick(() -> {
            if (i[0] < defs.size()) {
                MobDef d = defs.get(i[0]++);
                boolean normal = d.tier == Tier.NORMAL, elite = d.tier == Tier.ELITE;
                all.addAll(creature(h.getLevel(), d, refs, at, normal ? FairBudget.NORMAL : elite ? FairBudget.ELITE : FairBudget.WARDEN,
                        normal ? FairBudget.NORMAL_PERILOUS : elite ? FairBudget.ELITE_PERILOUS : FairBudget.WARDEN_PERILOUS));
                return;
            }
            log("creatures", all);
            h.assertTrue(all.stream().noneMatch(Reading::over), overList(all));
            h.succeed();
        });
    }

    /** Every Lord (Gate Lords, guardians, Sovereigns, the Provings' Wardens, the last): its moves and its abilities. */
    static void lords(GameTestHelper h) {
        Map<Rank, ServerPlayer> refs = refs(h);
        List<MobDef> defs = Bestiary.all().stream().filter(d -> d.tier.isBoss() && d.tier != Tier.NIGHTMARE).sorted(Comparator.comparing(d -> d.id)).toList();
        Vec3 at = h.absoluteVec(new Vec3(4.5, 1, 5.5));
        List<Reading> all = new ArrayList<>();
        int[] i = {0};
        h.onEachTick(() -> {
            if (i[0] < defs.size()) {
                MobDef d = defs.get(i[0]++);
                all.addAll(creature(h.getLevel(), d, refs, at, FairBudget.LORD, FairBudget.LORD_PERILOUS));
                return;
            }
            log("lords", all);
            h.assertTrue(all.stream().noneMatch(Reading::over), overList(all));
            h.succeed();
        });
    }

    /**
     * The budgets (SPEC §47.14): one blow's share of the reference player's health, at full health. A PERILOUS blow (or a
     * Lord's signature) is drawn before it lands and a dodge answers it, so it may take a little more; nothing takes half.
     */
    static final class FairBudget {
        static final float NORMAL = 0.25F, NORMAL_PERILOUS = 0.30F, ELITE = 0.35F, ELITE_PERILOUS = 0.40F, WARDEN = 0.40F, WARDEN_PERILOUS = 0.45F,
                LORD = 0.40F, LORD_PERILOUS = 0.50F;
        /** A Lord's string (its moves one after another, SPEC §47.3) together; the move it opens the fight with. */
        static final float STRING = 0.50F, INTRO = 0.25F;
    }
}
