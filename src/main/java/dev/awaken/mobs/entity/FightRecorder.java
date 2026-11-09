package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.Strikes;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.boss.KitMoves;
import dev.awaken.mobs.boss.Kits;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Tier;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The fight recorder (SPEC §48.7): real fights, tick by tick, against six kinds of player who don't play like people but
 * do play differently. One {@code [record]} line each in the log; {@code tools/fights/record_table.py} makes the table,
 * and every stage of the plan is checked against it. It asserts only that the fight ran: what a fight should look like
 * is held by the mind's gates.
 */
public final class FightRecorder {
    private FightRecorder() {}

    static final String[] BOSSES = {"hive_regent", "gearwright_colossus", "ember_tyrant", "sky_leviathan", "glass_serpent", "bell_warden"};

    enum Kind { STAND, RETREAT, CIRCLE, GUARD, KITE, BEHIND }

    /** How long a fight is recorded (72 s), and where its health is dropped so each phase is seen. */
    static final int TICKS = 1440, SECOND_PHASE = 450, THIRD_PHASE = 900;
    /** The floor's half width, and a pack's fight. */
    private static final int ROOM = 22, PACK = 12, PACK_TICKS = 600;

    public static void register() {
        for (String id : BOSSES) {
            for (Kind k : Kind.values()) {
                AwakenTests.registerIsolated("fights_record_" + id + "_" + k.name().toLowerCase(java.util.Locale.ROOT), TICKS + 60, h -> fight(h, id, k));
            }
        }
        AwakenTests.registerIsolated("fights_record_pack", PACK_TICKS + 60, FightRecorder::pack);
    }

    /** A floor wide enough for the fight, all of it ticking: a boss that walks off loaded ground freezes mid-move. */
    private static void floor(GameTestHelper h, int size) {
        ServerLevel level = h.getLevel();
        for (int x = -2; x <= size; x++) for (int z = -2; z <= size; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
        AwakenTests.wideArea(h, h.absolutePos(new BlockPos(-32, 0, -32)), h.absolutePos(new BlockPos(size + 32, 0, size + 32)));
    }

    private static void fight(GameTestHelper h, String id, Kind kind) {
        ServerLevel level = h.getLevel();
        floor(h, ROOM * 2);
        ServerPlayer p = FairTests.reference(h, Rank.E, new BlockPos(ROOM, 1, ROOM + 10));
        if (kind == Kind.GUARD) {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            p.startUsingItem(InteractionHand.MAIN_HAND);
        }
        MobDef def = Bestiary.get(id);
        Vec3 home = h.absoluteVec(new Vec3(ROOM + 0.5, 1, ROOM + 0.5));
        AwakenBoss boss = (AwakenBoss) Mobs.spawn(level, def, home, 10, new MobRole(MobRole.Role.BOSS, 10, def.theme.id, Optional.of(def.bossId()), ""), Set.of());
        boss.getRandom().setSeed(id.hashCode() * 31L + kind.ordinal()); // the same fight each run, so a stage's change is what moves the numbers
        boss.setTarget(p);
        double room = Math.min(ROOM - 3, def.arena - 3), half = boss.getBbWidth() * 0.5;
        int[] tick = {0}, begun = {0}, repeats = {0}, idle = {0}, side = {0}, walking = {0}, opening = {0}, playing = {0}, frozen = {0}, last = {0};
        String[] was = {null}, previous = {null};
        Vec3[] prev = {boss.position()};
        double[] angle = {0};
        TreeMap<String, Integer> tags = new TreeMap<>();
        h.onEachTick(() -> {
            tick[0]++;
            if (tick[0] > 5 && boss.tickCount == last[0]) frozen[0]++;
            last[0] = boss.tickCount;
            boss.setTarget(p);
            p.setHealth(p.getMaxHealth());
            if (tick[0] == SECOND_PHASE) boss.setHealth(boss.getMaxHealth() * 0.6F);
            if (tick[0] == THIRD_PHASE) boss.setHealth(boss.getMaxHealth() * 0.3F);
            Vec3 bp = boss.position(), me = p.position(), away = new Vec3(me.x - bp.x, 0, me.z - bp.z);
            double d = away.length();
            Vec3 dir = d < 1.0E-3 ? new Vec3(1, 0, 0) : away.scale(1 / d), next = me;
            switch (kind) {
                case RETREAT -> next = me.add(dir.scale(0.28));
                case KITE -> next = d < 12 ? me.add(dir.scale(0.28)) : d > 14 ? me.subtract(dir.scale(0.28)) : me;
                case CIRCLE -> {
                    angle[0] += 0.05;
                    next = bp.add(Math.cos(angle[0]) * (half + 4), 0, Math.sin(angle[0]) * (half + 4));
                }
                case BEHIND -> {
                    float yaw = boss.yBodyRot * Mth.DEG_TO_RAD;
                    next = bp.add(Mth.sin(yaw) * (half + 1.5), 0, -Mth.cos(yaw) * (half + 1.5));
                }
                default -> { }
            }
            Vec3 off = new Vec3(next.x - home.x, 0, next.z - home.z);
            if (off.length() > room) next = home.add(off.scale(room / off.length())).add(0, me.y - home.y, 0);
            p.setPos(next.x, me.y, next.z);
            String now = boss.kitPlaying();
            if (now != null && !now.equals(was[0])) {
                begun[0]++;
                if (now.equals(previous[0])) repeats[0]++;
                previous[0] = now;
                Kits.Kit kit = boss.kit();
                KitMoves.Move m = kit.moves().get(kit.moves().index(now));
                for (String t : m.kinds()) tags.merge(t, 1, Integer::sum);
                tags.merge("c_" + m.kind(), 1, Integer::sum);
            }
            was[0] = now;
            Vec3 step = new Vec3(bp.x - prev[0].x, 0, bp.z - prev[0].z);
            prev[0] = bp;
            if (now != null) playing[0]++;
            else if (boss.inOpening()) opening[0]++;
            else if (step.length() < 0.012) idle[0]++;
            else if (Math.abs(step.x * dir.z - step.z * dir.x) > Math.abs(step.x * dir.x + step.z * dir.z)) side[0]++;
            else walking[0]++;
        });
        h.runAfterDelay(TICKS, () -> {
            Awaken.LOG.info("[record] boss={} player={} ticks={} begun={} repeats={} playing={} opening={} idle={} walking={} sideways={} tags={}",
                    id, kind.name().toLowerCase(java.util.Locale.ROOT), tick[0], begun[0], repeats[0], playing[0], opening[0], idle[0], walking[0], side[0], tags);
            h.assertTrue(frozen[0] == 0, id + " stopped ticking for " + frozen[0] + " ticks: it left the loaded ground, and the numbers mean nothing");
            h.assertTrue(begun[0] > 0, id + " began nothing in " + TICKS + " ticks");
            boss.discard();
            h.succeed();
        });
    }

    /** A pack on one player: the most of them winding up at once, and how often. Their tells count every kind of blow. */
    private static void pack(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        floor(h, ROOM * 2);
        ServerPlayer p = FairTests.reference(h, Rank.E, new BlockPos(ROOM, 1, ROOM));
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1_000_000);
        p.setHealth(p.getMaxHealth());
        List<MobDef> normals = Bestiary.all().stream().filter(d -> d.tier == Tier.NORMAL).sorted(Comparator.comparing(d -> d.id)).toList();
        List<Mob> pack = new ArrayList<>();
        for (int i = 0; i < PACK; i++) {
            MobDef def = normals.get((i * 7) % normals.size());
            double a = Math.PI * 2 * i / PACK;
            Mob m = Mobs.spawn(level, def, h.absoluteVec(new Vec3(ROOM + 0.5 + Math.cos(a) * 9, 1, ROOM + 0.5 + Math.sin(a) * 9)), 10,
                    new MobRole(MobRole.Role.NORMAL, 10, def.theme.id, Optional.empty(), ""), Set.of());
            if (m != null) pack.add(m);
        }
        Vec3 stand = h.absoluteVec(new Vec3(ROOM + 0.5, 1, ROOM + 0.5));
        int[] most = {0}, sum = {0}, atThree = {0}, ticks = {0};
        h.onEachTick(() -> {
            p.setHealth(p.getMaxHealth());
            p.setPos(stand);
            ticks[0]++;
            int now = 0;
            for (Mob m : pack) {
                if (!m.isAlive()) continue;
                m.setTarget(p);
                Strikes.Tell tell = Strikes.tell(m);
                if (tell != null && tell.ticks() > 0 && tell.kind() != Strikes.Kind.NONE) now++;
            }
            most[0] = Math.max(most[0], now);
            sum[0] += now;
            if (now >= 3) atThree[0]++;
        });
        h.runAfterDelay(PACK_TICKS, () -> {
            Awaken.LOG.info("[record-pack] size={} ticks={} most={} mean={} ticksAtThreeOrMore={}", pack.size(), ticks[0], most[0],
                    String.format(java.util.Locale.ROOT, "%.2f", sum[0] / (float) Math.max(1, ticks[0])), atThree[0]);
            pack.forEach(Mob::discard);
            h.succeed();
        });
    }
}
