package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.Kits;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.core.api.MobRole;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** A blow aimed at you lands if you don't answer it (SPEC §47.15): a real fight against someone standing still. */
public final class FightReachTests {
    private FightReachTests() {}

    public static void register() {
        for (String id : new String[]{"hive_regent", "sky_leviathan", "gearwright_colossus", "ember_tyrant", "glass_serpent"}) {
            AwakenTests.registerIsolated("fights_reach_live_" + id, 1400, h -> live(h, id));
        }
        AwakenTests.registerIsolated("fights_guard_holds_ground", 200, FightReachTests::holdsGround);
        AwakenTests.registerIsolated("fights_body_keeps_up", 120, FightReachTests::bodyKeepsUp);
        AwakenTests.registerIsolated("fights_charge_stops_where_sent", 200, FightReachTests::chargeStopsWhereSent);
        AwakenTests.register("fights_reach_table", 20, FightReachTests::table);
        AwakenTests.registerIsolated("fights_reach_parry", 300, FightReachTests::parry);
        AwakenTests.register("fights_shots_own_look", 20, FightReachTests::ownLook);
        AwakenTests.registerIsolated("fights_reach_parry_fires", 120, FightReachTests::parryOnFire);
    }

    /** Every kit's every own swing against someone standing still: the share of its distances it can be aimed to reach. */
    private static void table(GameTestHelper h) {
        BlowAim.Result out = new BlowAim.Result();
        int moves = 0, points = 0, bare = 0, aimed = 0, never = 0;
        StringBuilder worst = new StringBuilder();
        for (Kits.Kit kit : Kits.all().values()) {
            MobDef def = Bestiary.get(kit.id());
            float half = def.width * def.scale * 0.5F, hip = def.height * def.scale * AwakenBoss.KIT_LEAN_HIP;
            for (KitMoves.Move m : kit.moves().moves()) {
                if (m.hits().isEmpty() || m.ability().isPresent()) continue;
                moves++;
                int here = 0, n = 0;
                float sign = behind(m) ? -1F : 1F;
                for (float d = m.minRange(); d <= m.maxRange() + 1.0E-3F; d += 0.5F) {
                    n++;
                    double z = sign * (d + half + 0.3);
                    if (BlowAim.bare(m, hip, 0, z, 0, 1.8, 0.3)) bare++;
                    if (BlowAim.solve(m, hip, 0, z, 0, 1.8, 0.3, def.flies(), 0F, true, Math.max(0, d - 0.1), out)) here++;
                }
                points += n;
                aimed += here;
                if (here == 0) {
                    never++;
                    worst.append(kit.id()).append('/').append(m.id()).append(' ');
                }
            }
        }
        Awaken.LOG.info("[reach] table: {} moves, {} places they begin from: {}% land as drawn, {}% once aimed; {} can't reach anyone: {}", moves, points,
                Math.round(100F * bare / points), Math.round(100F * aimed / points), never, worst);
        h.assertTrue(aimed >= points * 0.75F, "aimed blows reach " + aimed + " of " + points + " places");
        h.succeed();
    }

    /** A move swung behind the body (its sweep lies at its back). */
    private static boolean behind(KitMoves.Move m) {
        float sum = 0;
        int n = 0;
        for (var frame : m.hits()) for (var c : frame) {
            sum += c.az() + c.bz();
            n += 2;
        }
        return n > 0 && sum / n < -0.3F;
    }

    /** The share of blows that must land on someone standing still (a blow begun as a phase turns may not), and how high a flier may rise over them. */
    private static final float LANDS = 0.7F;
    private static final double HIGH = 3.5;

    /** A guard that turns an ability's blow on the tick it fires ends the move there; it used to crash the server. */
    private static void parryOnFire(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = -2; x <= 44; x++) for (int z = -2; z <= 44; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
        ServerPlayer p = FairTests.reference(h, Rank.E, new BlockPos(21, 1, 21));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        p.setYRot(180F);
        p.setYHeadRot(180F);
        p.setYBodyRot(180F);
        p.startUsingItem(InteractionHand.MAIN_HAND);
        MobDef def = Bestiary.get("bell_warden");
        AwakenBoss boss = (AwakenBoss) Mobs.spawn(level, def, h.absoluteVec(new Vec3(21.5, 1, 21.5 - (def.width * def.scale * 0.5 + 1.5))), 10,
                new MobRole(MobRole.Role.BOSS, 10, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
        boss.setTarget(p);
        h.assertTrue(boss.playKitString("clapper", java.util.List.of()), "the clapper plays");
        h.onEachTick(() -> boss.setTarget(p));
        h.succeedWhen(() -> {
            h.assertTrue(boss.kitPlaying() == null, "the move ended");
            h.assertTrue(boss.isAlive(), "and the fight goes on");
            boss.discard();
        });
    }

    /** No shot is made of vanilla's particles any more. */
    private static void ownLook(GameTestHelper h) {
        for (dev.awaken.mobs.def.BoltKind k : dev.awaken.mobs.def.BoltKind.values()) {
            var key = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(k.trail().getType());
            h.assertTrue(key != null && "awaken".equals(key.getNamespace()), k + "'s trail is " + key);
        }
        h.succeed();
    }

    /** The Regent's cut lands if you don't answer it; a guard raised into the same cut takes no damage and throws the Regent back. */
    private static void parry(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = -2; x <= 44; x++) for (int z = -2; z <= 44; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
        ServerPlayer p = FairTests.reference(h, Rank.E, new BlockPos(21, 1, 21));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        p.setYRot(180F);
        p.setYHeadRot(180F);
        p.setYBodyRot(180F);
        MobDef def = Bestiary.get("hive_regent");
        KitMoves.Move cut = Kits.all().get("hive_regent").moves().get(Kits.all().get("hive_regent").moves().index("cut"));
        double back = (cut.minRange() + cut.maxRange()) * 0.5 + def.width * def.scale * 0.5 + 0.3;
        AwakenBoss boss = (AwakenBoss) Mobs.spawn(level, def, h.absoluteVec(new Vec3(21.5, 1, 21.5 - back)), 10,
                new MobRole(MobRole.Role.BOSS, 10, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
        int[] stage = {0};
        float[] hp = {0};
        h.onEachTick(() -> {
            boss.setTarget(p);
            String now = boss.kitPlaying();
            if (stage[0] == 0) {
                FairTests.pool(p);
                h.assertTrue(boss.playKitString("cut", java.util.List.of()), "the cut plays");
                stage[0] = 1;
            } else if (stage[0] == 1 && now == null) {
                h.assertTrue(FairTests.share(p) > 1.0E-3F, "the cut, unanswered, lands");
                p.setHealth(p.getMaxHealth());
                hp[0] = p.getHealth();
                p.startUsingItem(InteractionHand.MAIN_HAND);
                h.assertTrue(p.isBlocking(), "the guard is up");
                h.assertTrue(boss.playKitString("cut", java.util.List.of()), "the cut plays again");
                stage[0] = 2;
            } else if (stage[0] == 2 && now == null) {
                h.assertTrue(p.getHealth() == hp[0], "the deflected cut does no damage (took " + (hp[0] - p.getHealth()) + ")");
                h.assertTrue(dev.awaken.arms.Arms.posture(boss) > 0, "the deflect went into its stance");
                h.assertTrue(boss.reeling(), "a lone blow turned aside: it reels");
                boss.discard();
                stage[0] = 3;
                h.succeed();
            }
        });
    }

    /**
     * A held guard holds its ground (SPEC §47.3): a string of light blows meets it blow after blow, and leaves you where
     * you stood. The player's own tick ages the guard, and its motion is walked by hand (a test player has no client).
     */
    private static void holdsGround(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = -2; x <= 44; x++) for (int z = -2; z <= 44; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
        ServerPlayer p = FairTests.reference(h, Rank.E, new BlockPos(21, 1, 21));
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        p.setYRot(180F);
        p.setYHeadRot(180F);
        p.setYBodyRot(180F);
        double floor = p.getY();
        Vec3 start = p.position();
        MobDef def = Bestiary.get("hive_regent");
        KitMoves.Move cut = Kits.all().get("hive_regent").moves().get(Kits.all().get("hive_regent").moves().index("cut"));
        double back = (cut.minRange() + cut.maxRange()) * 0.5 + def.width * def.scale * 0.5 + 0.3;
        AwakenBoss boss = (AwakenBoss) Mobs.spawn(level, def, h.absoluteVec(new Vec3(21.5, 1, 21.5 - back)), 5,
                new MobRole(MobRole.Role.BOSS, 5, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
        p.startUsingItem(InteractionHand.MAIN_HAND);
        FairTests.pool(p);
        int[] tick = {0}, met = {0}, begun = {0};
        double[] moved = {0};
        String[] was = {null};
        StringBuilder seq = new StringBuilder();
        h.onEachTick(() -> {
            tick[0]++;
            boss.setTarget(p);
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(Vec3.ZERO); // its own tick would move it, with no client's gravity
            p.doTick();
            p.setPos(p.getX(), Math.max(floor, p.getY()), p.getZ());
            p.setDeltaMovement(v);
            walk(p, floor);
            moved[0] = Math.max(moved[0], Math.sqrt(Math.pow(p.getX() - start.x, 2) + Math.pow(p.getZ() - start.z, 2)));
            String now = boss.kitPlaying();
            if (tick[0] == 8) h.assertTrue(boss.playKitString("cut", java.util.List.of("cut_b")), "the string plays");
            if (now == null ? was[0] != null : !now.equals(was[0])) {
                if (was[0] != null && hits(boss, was[0])) {
                    boolean on = FairTests.share(p) > 1.0E-3F;
                    if (on) met[0]++;
                    seq.append(was[0]).append(on ? "+ " : "- ");
                    FairTests.pool(p);
                }
                if (now != null && hits(boss, now)) begun[0]++;
                was[0] = now;
            }
            if (tick[0] > 8 && now == null && was[0] == null && begun[0] > 0) {
                Awaken.LOG.info("[reach] guard holds: {} begun, {} met the guard, moved {}; {}", begun[0], met[0], String.format("%.2f", moved[0]), seq);
                h.assertTrue(begun[0] == 2 && met[0] == 2, "both blows meet the guard (" + begun[0] + " begun, " + met[0] + " met)");
                h.assertTrue(moved[0] > 1.0 && moved[0] < 2.6, "the guard was shoved a step a blow and held, moved " + moved[0]);
                boss.discard();
                h.succeed();
            }
        });
    }

    /**
     * The body and its clip keep one clock on a move that carries it (SPEC §48.8; Euan: the Ember Tyrant's run "isn't
     * smooth"): every kit's every such move through the client's three-step glide, fed the place sent each tick, is drawn
     * where the server has it; then a real charge, sent ahead while it runs and not at its end.
     */
    private static void bodyKeepsUp(GameTestHelper h) {
        int moves = 0;
        for (Kits.Kit kit : Kits.all().values()) {
            for (KitMoves.Move m : kit.moves().moves()) {
                if (m.motion().advance() == 0) continue;
                moves++;
                int active = m.time().active(), left = 0;
                float v = m.motion().advance() / active;
                double drawn = 0, target = 0;
                for (int k = 0; k < active; k++) {
                    double sent = (k + 1 + FightRunner.carried(k, active)) * v;
                    if (left == 0 || sent != target) {
                        target = sent;
                        left = 3;
                    }
                    drawn += (target - drawn) / left--;
                    h.assertTrue(Math.abs(drawn - (k + 1) * v) < 1.0E-3F, kit.id() + "/" + m.id() + " drawn " + drawn + " at tick " + k + ", true " + (k + 1) * v);
                }
            }
        }
        h.assertTrue(moves >= 60, "the moves that carry a body: " + moves);
        ServerLevel level = h.getLevel();
        for (int x = -2; x <= 24; x++) for (int z = -2; z <= 24; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
        MobDef def = Bestiary.get("ember_tyrant");
        AwakenBoss boss = (AwakenBoss) Mobs.spawn(level, def, h.absoluteVec(new Vec3(11.5, 1, 11.5)), 5,
                new MobRole(MobRole.Role.BOSS, 5, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
        h.assertTrue(boss.getType().updateInterval() == 1, "a boss is placed every tick");
        KitMoves.Move charge = boss.kit().moves().get(boss.kit().moves().index("charge"));
        double full = charge.motion().advance() / charge.time().active() * FightRunner.LEAD;
        double[] most = {0};
        boolean[] began = {false};
        h.onEachTick(() -> {
            if (!began[0]) {
                began[0] = boss.playKitMove("charge");
                return;
            }
            double ahead = boss.trackingPosition().distanceTo(boss.position());
            most[0] = Math.max(most[0], ahead);
            if (boss.kitPlaying() == null) {
                h.assertTrue(Math.abs(most[0] - full) < 0.01, "sent ahead by two ticks of its run: " + most[0] + ", want " + full);
                h.assertTrue(ahead == 0, "and not once it stops: " + ahead);
                boss.discard();
                h.succeed();
            }
        });
    }

    /** A charge (an ability, not a clip) brakes on its last tick: what's sent ahead must be where the body really gets to. */
    private static void chargeStopsWhereSent(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = -2; x <= 24; x++) for (int z = -2; z <= 28; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
        MobDef def = Bestiary.get("guardian_gardens");
        AwakenBoss boss = (AwakenBoss) Mobs.spawn(level, def, h.absoluteVec(new Vec3(11.5, 1, 5.5)), 5,
                new MobRole(MobRole.Role.BOSS, 5, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
        Pig stand = h.spawn(EntityTypes.PIG, new Vec3(11.5, 1, 17.5));
        stand.setNoAi(true);
        boss.setTarget(stand);
        List<Vec3> sent = new ArrayList<>(), at = new ArrayList<>();
        boolean[] began = {false};
        h.onEachTick(() -> {
            if (!began[0]) {
                began[0] = boss.playKitMove("charge");
                return;
            }
            sent.add(boss.trackingPosition());
            at.add(boss.position());
            if (boss.kitPlaying() != null) return;
            int led = 0;
            for (int k = 0; k + 2 < sent.size(); k++) {
                if (sent.get(k).distanceTo(at.get(k)) == 0) continue;
                led++;
                double off = sent.get(k).distanceTo(at.get(k + 2));
                h.assertTrue(off < 0.15, "tick " + k + " of the charge sent the body " + off + " from where it got to");
            }
            h.assertTrue(led >= 6, "ticks a charge led the body: " + led);
            boss.discard();
            h.succeed();
        });
    }

    private static boolean hits(AwakenBoss boss, String move) {
        KitMoves.Table table = boss.kit().moves();
        return !table.get(table.index(move)).hits().isEmpty();
    }

    /** One tick of what the client does with a player's motion: air drag, gravity, ground friction, a flat floor. */
    private static void walk(ServerPlayer p, double floor) {
        Vec3 v = p.getDeltaMovement();
        if (v.lengthSqr() < 1.0E-6 && p.getY() <= floor) return;
        double y = p.getY() + v.y;
        boolean ground = y <= floor;
        p.setPos(p.getX() + v.x, ground ? floor : y, p.getZ() + v.z);
        p.setOnGround(ground);
        double drag = ground ? 0.546 : 0.91;
        p.setDeltaMovement(v.x * drag, ground ? 0 : (v.y - 0.08) * 0.98, v.z * drag);
    }

    private static void live(GameTestHelper h, String id) {
        ServerLevel level = h.getLevel();
        for (int x = -2; x <= 44; x++) for (int z = -2; z <= 44; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
        ServerPlayer p = FairTests.reference(h, Rank.E, new BlockPos(21, 1, 21));
        MobDef def = Bestiary.get(id);
        AwakenMob m = Mobs.spawn(level, def, h.absoluteVec(new Vec3(21.5, 1, 11.5)), 10,
                new MobRole(MobRole.Role.BOSS, 10, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
        h.assertTrue(m instanceof AwakenBoss, id + " spawned as a boss");
        AwakenBoss boss = (AwakenBoss) m;
        boss.setTarget(p);
        FairTests.pool(p);
        int[] begun = {0}, landed = {0}, airborne = {0}, tick = {0};
        double[] high = {0}, aboveAtBegin = {0};
        String[] was = {null};
        boolean[] melee = {false};
        StringBuilder sb = new StringBuilder();
        h.onEachTick(() -> {
            tick[0]++;
            boss.setTarget(p);
            p.setHealth(p.getMaxHealth());
            double above = boss.getY() - p.getY();
            high[0] = Math.max(high[0], above);
            if (above > 3) airborne[0]++;
            if (tick[0] == 450) boss.setHealth(boss.getMaxHealth() * 0.6F);
            if (tick[0] == 900) boss.setHealth(boss.getMaxHealth() * 0.3F);
            String now = boss.kitPlaying();
            if (now == null ? was[0] != null : !now.equals(was[0])) {
                if (was[0] != null && melee[0]) {
                    boolean hit = FairTests.share(p) > 1.0E-3F;
                    if (hit) landed[0]++;
                    sb.append(was[0]).append(hit ? "+ " : "- ");
                }
                was[0] = now;
                melee[0] = false;
                if (now != null) {
                    Kits.Kit kit = boss.kit();
                    KitMoves.Move mv = kit.moves().get(kit.moves().index(now));
                    melee[0] = !mv.hits().isEmpty();
                    if (melee[0]) {
                        begun[0]++;
                        aboveAtBegin[0] = Math.max(aboveAtBegin[0], above);
                        FairTests.pool(p);
                    }
                }
            }
        });
        h.runAfterDelay(1350, () -> {
            Awaken.LOG.info("[reach] live {}: melee {} begun, {} landed on someone standing still; highest {} blocks over them ({} above at a blow), {} ticks more than 3 over; {}",
                    id, begun[0], landed[0], String.format("%.1f", high[0]), String.format("%.1f", aboveAtBegin[0]), airborne[0], sb);
            h.assertTrue(begun[0] >= 8 && landed[0] >= begun[0] * LANDS, id + " landed " + landed[0] + " of " + begun[0] + " blows on someone standing still: " + sb);
            h.assertTrue(!def.flies() || aboveAtBegin[0] < HIGH, id + " began a blow " + aboveAtBegin[0] + " blocks over them");
            boss.discard();
            h.succeed();
        });
    }
}
