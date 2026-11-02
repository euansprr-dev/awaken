package dev.awaken.mobs.entity;

import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Strikes;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.boss.FightScripts;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.Kits;
import dev.awaken.mobs.def.Ability;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.BoltKind;
import dev.awaken.mobs.def.MobDef;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * See it coming (SPEC §46.2): every move that hurts is told; an aimed carrier locks with time to answer it; a nearby
 * player is sent every carrier; a dash at the lock gets out of a volley, a locked line and a leap's ring, and a wave is
 * jumped; nothing an evade answered leaves its effect or push behind.
 */
public final class CarrierTests {
    private CarrierTests() {}

    public static void register() {
        AwakenTests.register("fights_language", 20, CarrierTests::language);
        AwakenTests.register("fights_carriers_seen", 80, CarrierTests::seen);
        AwakenTests.register("fights_dash_escapes", 120, CarrierTests::escapes);
        AwakenTests.register("fights_effect_needs_hit", 120, CarrierTests::effectNeedsHit);
        AwakenTests.register("fights_beast_told", 240, CarrierTests::beastTold);
    }

    // ---- the language (I1, I3; I6-I8 counted for the pilots) ----------------------------------------------------------

    /** Walks all 41 move tables and every bestiary ability: I1 and I3 hold everywhere; I6-I8 are written down per boss. */
    static void language(GameTestHelper h) {
        List<String> bad = new ArrayList<>();
        List<String> rows = new ArrayList<>();
        int moves = 0, abilities = 0;
        for (MobDef d : Bestiary.all()) {
            for (MobDef.Phase ph : d.phases) {
                for (Ability a : ph.abilities()) {
                    abilities++;
                    if (hurts(a) && a.strike() == Strikes.Kind.NONE) bad.add("I1 " + d.id + " " + a.name() + ": an ability that hurts is untold");
                    int total = a.windup() + (a.heavy() ? 8 : 0);
                    if (total < Carriers.lead(a)) bad.add("I3 " + d.id + " " + a.name() + ": winds up " + total + " ticks, under its lock's " + Carriers.lead(a));
                }
            }
            Kits.Kit kit = Kits.get(d.id);
            if (kit == null) continue;
            int blows = 0, parry = 0, red = 0, summons = 0;
            for (KitMoves.Move m : kit.moves().moves()) {
                moves++;
                Ability a = m.ability().map(n -> find(d, n)).orElse(null);
                Strikes.Kind told = FightRunner.told(m, a);
                boolean hurts = !m.hits().isEmpty() || (a != null && hurts(a));
                if (hurts && told == Strikes.Kind.NONE) bad.add("I1 " + d.id + " " + m.id() + ": a move that hurts is untold");
                if (a != null && Carriers.lead(a) > 0) {
                    int wind = KitMoves.played(m, 1F, 0).windup();
                    if (wind < Carriers.lead(a)) bad.add("I3 " + d.id + " " + m.id() + ": winds up " + wind + " ticks, under its lock's " + Carriers.lead(a));
                }
                if (a != null && a.kind() == Ability.Kind.SUMMON) summons++;
                if (!hurts) continue;
                blows++;
                if (told.deflectable()) parry++;
                if (told == Strikes.Kind.PERILOUS || told == Strikes.Kind.GRAB) red++;
            }
            rows.add(String.format(java.util.Locale.ROOT, "%-22s blows %2d  parryable %3.0f%%  red %3.0f%%  summon moves %d%s", d.id, blows,
                    100.0 * parry / Math.max(1, blows), 100.0 * red / Math.max(1, blows), summons, kit.fight().duel() ? "  duel" : ""));
            if (kit.fight().duel()) duel(d, kit, blows, parry, red, summons, bad);
        }
        FightCostTests.write(h.getLevel().getServer().getServerDirectory().resolve("fights-language.txt"), String.join("\n", rows) + "\n");
        h.assertTrue(moves >= 400 && abilities > 300, "read every table and bestiary (" + moves + " moves, " + abilities + " abilities)");
        h.assertTrue(bad.isEmpty(), bad.size() + " broken: " + String.join("; ", bad.subList(0, Math.min(8, bad.size()))));
        h.succeed();
    }

    /**
     * A duel's script (SPEC §46.3): I6 at most one signature and one summon move, never in its first phase; I7 at least
     * 40% of its blows parryable and 20% red; I8 in every phase at most 30% of its starts (by weight) a lone blow.
     */
    private static void duel(MobDef d, Kits.Kit kit, int blows, int parry, int red, int summons, List<String> bad) {
        KitMoves.Table moves = kit.moves();
        int signatures = 0;
        for (KitMoves.Move m : moves.moves()) if (m.signature()) signatures++;
        if (signatures > 1 || summons > 1) bad.add("I6 " + d.id + ": " + signatures + " signatures, " + summons + " summon moves");
        if (parry * 10 < blows * 4) bad.add("I7 " + d.id + ": " + parry + " of " + blows + " blows parryable");
        if (red * 5 < blows) bad.add("I7 " + d.id + ": " + red + " of " + blows + " blows red");
        List<FightScripts.Phase> phases = kit.fight().phases();
        for (int p = 0; p < phases.size(); p++) {
            int all = 0, lone = 0;
            for (FightScripts.Entry e : phases.get(p).pool()) {
                int i = moves.index(e.move());
                if (i < 0) continue;
                KitMoves.Move m = moves.get(i);
                Ability a = m.ability().map(n -> find(d, n)).orElse(null);
                if (p == 0 && a != null && a.kind() == Ability.Kind.SUMMON) bad.add("I6 " + d.id + ": summons in its first phase");
                if (a != null && a.kind() == Ability.Kind.SUMMON && a.count() > AwakenBoss.DUEL_ADDS) bad.add("I6 " + d.id + ": summons " + a.count() + " at once");
                if (m.hits().isEmpty() && (a == null || !hurts(a))) continue;
                all += e.weight();
                if (e.then().isEmpty()) lone += e.weight();
            }
            if (lone * 10 > all * 3) bad.add("I8 " + d.id + " phase " + (p + 1) + ": " + lone + " of " + all + " starts a lone blow");
        }
    }

    private static boolean hurts(Ability a) {
        return switch (a.kind()) {
            case SUMMON, DECOY, HEAL, GUARD, FRENZY, BLINK -> false;
            default -> true;
        };
    }

    private static Ability find(MobDef d, String name) {
        for (MobDef.Phase p : d.phases) for (Ability a : p.abilities()) if (a.name().equals(name)) return a;
        return null;
    }

    // ---- seen: a nearby player is sent every carrier, and a shot is tracked closely -------------------------------------

    static void seen(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        floor(h);
        var live = AwakenTests.survivalPlayer(h, "carrier_seer", new BlockPos(6, 1, 1));
        AwakenMob owner = maker(h);
        Vec3 o = h.absoluteVec(new Vec3(1.5, 1, 1.5));
        // other tests' players stand far off (tests are 54 apart, sends reach 72): count what reaches anyone, then this one
        int before = Carriers.told;
        Carriers.wave(level, owner, o, 1F, 4F, 1F, BoltKind.PLAIN, null, 0);
        Carriers.volley(level, owner, o.add(0, 1.5, 0), List.of(o.add(3, 0, 0)), 2F, 1F, BoltKind.PLAIN, null);
        Carriers.ground(level, owner, Strikes.Kind.PERILOUS, o, o.add(4, 0, 0), 5F, 1F, BoltKind.PLAIN, null);
        Carriers.line(level, owner, Strikes.Kind.HEAVY, o.add(0, 1.5, 0), o.add(6, 1.5, 0), 10, 0.3F);
        Carriers.ring(level, owner, Strikes.Kind.HEAVY, o.add(3, 0, 0), 2F, 20);
        Carriers.wedge(level, owner, Strikes.Kind.HEAVY, o, new Vec3(1, 0, 0), 5F, 10);
        Carriers.field(level, owner, o.add(3, 0, 0), 2F, 30);
        Carriers.pull(level, owner, o, 6F, 1F, BoltKind.PLAIN, null, 0);
        int near = 0;
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(o) <= Carriers.SEND_RANGE * Carriers.SEND_RANGE) near++;
        // eight made, and the pull's wave with it: nine to each player near
        h.assertTrue(near >= 1 && Carriers.told - before == 9 * near,
                "every player near was sent every carrier (9 each to " + near + "), " + (Carriers.told - before) + " sent");
        var bolt = MobTypes.BOLT.get();
        h.assertTrue(bolt.clientTrackingRange() * 16 >= 48 && bolt.updateInterval() <= 2,
                "a shot is tracked from 48 blocks and placed every 2 ticks (" + bolt.clientTrackingRange() * 16 + ", " + bolt.updateInterval() + ")");
        h.runAfterDelay(60, () -> {
            boolean any = false;
            for (Carriers.Carrier c : Carriers.active()) any |= c.owner == owner;
            h.assertTrue(!any, "every carrier ended on its own clock");
            owner.discard();
            live.leave();
            h.succeed();
        });
    }

    // ---- a dash at the lock gets out; a wave is jumped -----------------------------------------------------------------

    static void escapes(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        floor(h);
        AwakenMob owner = maker(h);
        // the volley: rings where two stand at the throw; one dashes out (a dash is 2.6), the other stays
        ServerPlayer gone = player(h, 2.5, 6.5), stays = player(h, 6.5, 6.5);
        Carriers.volley(level, owner, owner.position().add(0, 1.5, 0), List.of(gone.position(), stays.position()), 2.5F, 4F, BoltKind.PLAIN, null);
        Vec3 out = gone.position().add(0, 0, 2.6);
        // the wave: one is in the air over its crest as it passes, one on the ground
        ServerPlayer jumper = player(h, 11.5, 4.5), stander = player(h, 11.5, 1.5);
        Vec3 waveAt = h.absoluteVec(new Vec3(8.5, 1, 1.5));
        float hp = gone.getHealth();
        h.runAfterDelay(2, () -> gone.setPos(out)); // the dash, at the lock (the ring shows at the throw)
        h.runAfterDelay(Carriers.VOLLEY_FALL + 2, () -> {
            h.assertTrue(gone.getHealth() == hp, "a dash out of the volley's ring at the throw leaves it unhurt");
            h.assertTrue(stays.getHealth() < hp, "one who stays in its ring is struck");
            // the wave rolls out from 1 block to 6: the jumper is lifted while its crest passes
            Carriers.wave(level, owner, waveAt, 1F, 6F, 4F, BoltKind.PLAIN, null, 0);
        });
        h.onEachTick(() -> {
            // a jump's arc, held: over the crest for the whole pass
            if (jumper.isAlive()) jumper.setPos(h.absoluteVec(new Vec3(11.5, 2.1, 4.5)));
        });
        h.runAfterDelay(Carriers.VOLLEY_FALL + 2 + 14, () -> {
            h.assertTrue(jumper.getHealth() == hp, "a wave jumped over leaves you unhurt");
            h.assertTrue(stander.getHealth() < hp, "a wave stood in strikes");
            // a locked line: it fires along where you stood at the lock; one dashed aside, one stayed
            ServerPlayer aside = player(h, 4.5, 10.5), stayed = player(h, 6.5, 12.5);
            Vec3 from = owner.getEyePosition();
            Vec3 lineAim = Abilities.chest(aside);
            Ability beam = new Ability(Ability.Kind.BEAM, "test beam", 30, 100, 18F, 1F, 3F, 1, null, null, 0, 0, null, 0F, true, false);
            owner.setTarget(aside);
            aside.setPos(aside.position().add(2.6, 0, 0)); // the dash, at the lock
            Strikes.deliver(owner, Strikes.Kind.PERILOUS, () -> {
                Abilities.beam(level, owner, beam, lineAim);
                return true;
            });
            h.assertTrue(aside.getHealth() == aside.getMaxHealth(), "a dash off a locked line leaves you unhurt");
            Vec3 stayAim = Abilities.chest(stayed);
            owner.setTarget(stayed);
            Strikes.deliver(owner, Strikes.Kind.PERILOUS, () -> {
                Abilities.beam(level, owner, beam, stayAim);
                return true;
            });
            h.assertTrue(stayed.getHealth() < stayed.getMaxHealth(), "one who stays on the line is struck");
            // the leap: its ring is at most 2.5 across its middle, a dash 2.6
            h.assertTrue(Carriers.VOLLEY_RING + 0.3F < 2.6F && Carriers.VOLLEY_RING < 2.6F, "a leap's and a volley's rings are narrower than a dash");
            for (ServerPlayer p : List.of(gone, stays, jumper, stander, aside, stayed)) level.getServer().getPlayerList().remove(p);
            owner.discard();
            h.succeed();
        });
    }

    // ---- nothing an evade answered leaves its effect or push ----------------------------------------------------------

    static void effectNeedsHit(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        floor(h);
        AwakenMob owner = maker(h);
        // the answered stand an evade (their hurt refused, as a dodge refuses it); the others take the hit
        ServerPlayer rootAnswered = player(h, 6.5, 1.5), rootStruck = player(h, 6.5, 2.5);
        ServerPlayer pullAnswered = player(h, 1.5, 6.5), pullStruck = player(h, 2.5, 6.5);
        ServerPlayer cloudAnswered = player(h, 10.5, 10.5);
        for (ServerPlayer p : List.of(rootAnswered, pullAnswered, cloudAnswered)) {
            p.getAbilities().invulnerable = true;
            p.onUpdateAbilities();
        }
        Vec3 o = h.absoluteVec(new Vec3(1.5, 1, 1.5));
        MobEffectInstance hold = new MobEffectInstance(MobEffects.SLOWNESS, 40, 5);
        Carriers.Carrier root = Carriers.ground(level, owner, Strikes.Kind.PERILOUS, o, o.add(6, 0, 0.5), 8F, 2F, BoltKind.PLAIN, hold);
        h.assertTrue(root != null, "a root line was made");
        Carriers.Carrier wide = Carriers.ground(level, owner, Strikes.Kind.PERILOUS, o.add(0, 0, 1), o.add(6, 0, 1), 8F, 2F, BoltKind.PLAIN, hold);
        h.assertTrue(wide != null, "a second root line was made");
        Carriers.pull(level, owner, h.absoluteVec(new Vec3(1.5, 1, 4.5)), 4F, 2F, BoltKind.PLAIN,
                new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), 1.0);
        Carriers.lob(level, owner, owner.position().add(0, 1.5, 0), cloudAnswered.position(), 2F, BoltKind.SPORE,
                new MobEffectInstance(MobEffects.POISON, 60, 0), 2F, 40, 0.5F);
        Vec3 pullBefore = pullAnswered.getDeltaMovement();
        h.runAfterDelay(Carriers.PULL_TICKS + 12, () -> {
            h.assertTrue(!rootAnswered.hasEffect(MobEffects.SLOWNESS), "an evaded root holds nobody");
            h.assertTrue(rootStruck.hasEffect(MobEffects.SLOWNESS), "a root that lands holds");
            h.assertTrue(!pullAnswered.hasEffect(MobEffects.WEAKNESS) && pullAnswered.getDeltaMovement().equals(pullBefore),
                    "an evaded pull's wave leaves no effect and no push");
            h.assertTrue(pullStruck.hasEffect(MobEffects.WEAKNESS), "a pull's wave that lands carries its effect");
        });
        h.runAfterDelay(Carriers.VOLLEY_FALL + Carriers.FIELD_SAFE + 30, () -> {
            h.assertTrue(!cloudAnswered.hasEffect(MobEffects.POISON), "an evaded lob, and the field it leaves, poison nobody");
            for (ServerPlayer p : List.of(rootAnswered, rootStruck, pullAnswered, pullStruck, cloudAnswered)) level.getServer().getPlayerList().remove(p);
            owner.discard();
            h.succeed();
        });
    }

    // ---- a beast's bite is told, and a pack takes turns ------------------------------------------------------------

    /** Three wolves on vanilla bodies bite with the told bite: every bite is told for its wind-up, never three at once. */
    static void beastTold(GameTestHelper h) {
        floor(h);
        var live = AwakenTests.survivalPlayer(h, "bitten", new BlockPos(6, 1, 6));
        ServerPlayer p = live.player();
        List<Wolf> pack = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Wolf w = h.spawn(EntityTypes.WOLF, new Vec3(4.5 + i * 1.5, 1, 4.5));
            w.goalSelector.removeAllGoals(g -> true);
            w.targetSelector.removeAllGoals(g -> true);
            w.goalSelector.addGoal(0, Strikes.bite(w, 1.2, () -> true));
            w.setTarget(p);
            pack.add(w);
        }
        // per wolf: the tell running now, and the last one's length (a bite must follow a whole one)
        int[] toldFor = new int[3], lastRun = new int[3], most = {0}, bites = {0}, early = {0};
        h.onEachTick(() -> {
            p.setPos(h.absoluteVec(new Vec3(6.5, 1, 6.5)));
            int telling = 0;
            for (int i = 0; i < 3; i++) {
                Wolf w = pack.get(i);
                w.setTarget(p);
                Strikes.Tell t = Strikes.tell(w);
                if (t != null && t.kind() == Strikes.Kind.LIGHT) {
                    telling++;
                    toldFor[i]++;
                } else if (toldFor[i] > 0) {
                    lastRun[i] = toldFor[i];
                    toldFor[i] = 0;
                }
            }
            most[0] = Math.max(most[0], telling);
            if (p.getHealth() < p.getMaxHealth()) {
                int i = pack.indexOf(p.getLastHurtByMob());
                bites[0]++;
                if (i < 0 || lastRun[i] < ToldBite.TELL - 1) early[0]++;
                if (i >= 0) lastRun[i] = 0;
                p.setHealth(p.getMaxHealth());
            }
            p.setInvulnerableTime(0); // this player's own tick never runs here: each bite is judged alone
        });
        h.runAfterDelay(220, () -> {
            h.assertTrue(bites[0] >= 2, "the pack bit (" + bites[0] + " bites)");
            h.assertTrue(early[0] == 0, early[0] + " of " + bites[0] + " bites came without their full tell");
            h.assertTrue(most[0] <= ToldBite.AT_ONCE, "at most " + ToldBite.AT_ONCE + " of a pack wind up at once (" + most[0] + ")");
            pack.forEach(Wolf::discard);
            live.leave();
            h.succeed();
        });
    }

    // ---- helpers -------------------------------------------------------------------------------------------------------

    private static void floor(GameTestHelper h) {
        for (int x = -2; x <= 14; x++) for (int z = -2; z <= 14; z++) {
            h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        }
    }

    /** A creature to make the carriers (it stands still: the test drives them). */
    private static AwakenMob maker(GameTestHelper h) {
        MobDef def = Bestiary.get("toll_ghoul");
        AwakenMob m = Mobs.spawn(h.getLevel(), def, h.absoluteVec(new Vec3(0.5, 1, 0.5)), 5,
                new MobRole(MobRole.Role.NORMAL, 5, def.theme.id, Optional.empty(), ""), Set.of());
        h.assertTrue(m != null, "a creature spawned to make them");
        m.setNoAi(true);
        return m;
    }

    private static int named;

    /** A survival player standing at x, 1, z (mock players are creative, and creatures take no aim at those). */
    private static ServerPlayer player(GameTestHelper h, double x, double z) {
        return AwakenTests.survivalPlayer(h, "carrier_mark_" + ++named, new BlockPos(Mth.floor(x), 1, Mth.floor(z))).player();
    }
}
