package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gear.GearItems;
import dev.awaken.gear.HunterTier;
import dev.awaken.gear.Piece;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Fair damage (SPEC §47.14): nobody who belongs in a fight loses most of their health to one thing in it. Measured on the
 * reference player (the rank's first level, a sixth of its points in VIT, the rank's Hunter set, Normal) through the real
 * damage path.
 */
public final class FairTests {
    private FairTests() {}

    public static void register() {
        AwakenTests.registerIsolated("fights_volatile_once", 200, FairTests::volatileOnce);
    }

    private static int made;

    /** The reference player of a rank (SPEC §47.14) standing at {@code rel}, at full health. */
    static ServerPlayer reference(GameTestHelper h, Rank rank, BlockPos rel) {
        ServerPlayer p = AwakenTests.survivalPlayer(h, "fair-" + rank.name().toLowerCase() + "-" + (++made), rel).player();
        Progression.awaken(p);
        int lv = Math.max(1, rank.levelRequired);
        Progression.topUpToLevel(p, lv, Progression.XpSource.COMMAND);
        while (Progression.rank(p).ordinal() < rank.ordinal()) Progression.passReassessment(p);
        Progression.addStat(p, Stat.VIT, 3 * (lv - 1) / 6);
        HunterTier tier = HunterTier.valueOf(rank == Rank.SOVEREIGN ? "S" : rank.name());
        p.setItemSlot(EquipmentSlot.HEAD, GearItems.stack(tier, Piece.HELMET));
        p.setItemSlot(EquipmentSlot.CHEST, GearItems.stack(tier, Piece.CHESTPLATE));
        p.setItemSlot(EquipmentSlot.LEGS, GearItems.stack(tier, Piece.LEGGINGS));
        p.setItemSlot(EquipmentSlot.FEET, GearItems.stack(tier, Piece.BOOTS));
        Progression.applyAttributes(p);
        p.setHealth(p.getMaxHealth());
        return p;
    }

    private static final float POOL = 2000F; // MAX_ABSORPTION tops out at 2048

    /**
     * Readies {@code p} to measure what lands on it through the real path: full health and a deep absorption pool that
     * takes every blow after armour and stats, so a share over 100% shows instead of a death. No i-frames.
     */
    static void pool(ServerPlayer p) {
        p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(POOL);
        p.setAbsorptionAmount(POOL);
        p.setHealth(p.getMaxHealth());
        p.setInvulnerableTime(0);
        p.clearFire();
        p.removeAllEffects();
        POOLED.put(p.getUUID(), p.getHealth());
    }

    private static final java.util.Map<java.util.UUID, Float> POOLED = new java.util.HashMap<>();

    /** What has landed on {@code p} since {@link #pool}, as a share of its max health. */
    static float share(ServerPlayer p) {
        float was = POOLED.getOrDefault(p.getUUID(), p.getMaxHealth());
        return ((POOL - p.getAbsorptionAmount()) + Math.max(0F, was - p.getHealth())) / p.getMaxHealth();
    }

    static String pct(float share) {
        return Math.round(share * 100) + "%";
    }

    /**
     * A Volatile creature killed beside the E reference player: its reach is drawn on the ground at once, nothing lands
     * for its fuse, then one blast within the budget. One stepped just out of the drawn reach takes nothing. Beside them,
     * the old two blasts replayed on a third (the creature's power 2 at 0.5 s, the gate's ownerless 2.2 at 1 s): the
     * before of SPEC §47.14.
     */
    private static void volatileOnce(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = -1; x <= 16; x++) for (int z = -1; z <= 4; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
        MobDef def = Bestiary.get("toll_ghoul");
        int lv = Progression.contentLevel(Rank.E);
        MobRole role = new MobRole(MobRole.Role.NORMAL, lv, def.theme.id, Optional.empty(), "");
        AwakenMob body = Mobs.spawn(level, def, h.absoluteVec(new Vec3(2.5, 1, 2.5)), lv, role, Set.of(Forces.Tag.VOLATILE));
        AwakenMob old = Mobs.spawn(level, def, h.absoluteVec(new Vec3(12.5, 1, 2.5)), lv, role, Set.of());
        h.assertTrue(body != null && old != null, "two creatures spawned");
        body.setNoAi(true);
        old.setNoAi(true);
        ServerPlayer near = reference(h, Rank.E, new BlockPos(1, 1, 2)), far = reference(h, Rank.E, new BlockPos(1, 1, 2)),
                before = reference(h, Rank.E, new BlockPos(11, 1, 2));
        // the far one stands just past the drawn reach on the other side
        float reach = def.blast * 2.0F;
        far.setPos(body.getX() + reach + 0.6, body.getY(), body.getZ());
        for (ServerPlayer p : List.of(near, far, before)) pool(p);
        body.hurtServer(level, near.damageSources().playerAttack(near), 1.0E6F);
        h.assertTrue(body.isDeadOrDying(), "it died");
        Carriers.Carrier shown = Carriers.active().stream().filter(c -> c.kind == Carriers.Kind.BURST && c.owner == body).findFirst().orElse(null);
        h.assertTrue(shown != null && Math.abs(shown.width - reach) < 1.0E-3F, "its reach is drawn on the ground at once, " + reach + " blocks");
        Vec3 o = old.position();
        int[] tick = {0}, landings = {0};
        float[] last = {0};
        h.onEachTick(() -> {
            tick[0]++;
            float n = share(near);
            if (n > last[0] + 1.0E-4F) {
                h.assertTrue(tick[0] >= dev.awaken.core.api.FairShare.BURST_FUSE - 2, "nothing lands before the fuse (tick " + tick[0] + ": absorption "
                        + near.getAbsorptionAmount() + ", health " + near.getHealth() + " of " + near.getMaxHealth() + ")");
                landings[0]++;
                last[0] = n;
            }
            if (tick[0] == 10) level.explode(old, old.damageSources().explosion(old, old), null, o.x, o.y + old.getBbHeight() * 0.4, o.z, 2.0F, false,
                    net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            if (tick[0] == 20) level.explode(null, level.damageSources().explosion(null, null), null, o.x, o.y + 0.5, o.z, 2.2F, false,
                    net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            if (tick[0] == 60) {
                Awaken.LOG.info("[fair] volatile at E, the reference player ({} health) one block off: before {} (two blasts), after {} (one)",
                        near.getMaxHealth(), pct(share(before)), pct(share(near)));
                h.assertTrue(landings[0] == 1, "one blast, not " + landings[0]);
                h.assertTrue(share(near) <= 0.30F, "the blast took " + pct(share(near)) + " of the E reference player's " + near.getMaxHealth()
                        + " health (budget 30%)");
                h.assertTrue(share(far) == 0, "a step past the drawn reach takes nothing, took " + pct(share(far)));
                old.discard();
                h.succeed();
            }
        });
    }
}
