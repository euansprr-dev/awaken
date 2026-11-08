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
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
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
        AwakenTests.registerIsolated("fights_fair_creatures", 2400, FairAudit::creatures);
        AwakenTests.registerIsolated("fights_fair_lords", 1200, FairAudit::lords);
        AwakenTests.registerIsolated("fights_fair_guard", 200, FairTests::guard);
        AwakenTests.registerIsolated("fights_fair_arrival", 400, FairTests::arrival);
    }

    private static int made;

    /** The reference player of a rank (SPEC §47.14) standing at {@code rel}, at full health. */
    public static ServerPlayer reference(GameTestHelper h, Rank rank, BlockPos rel) {
        ServerPlayer p = AwakenTests.survivalPlayer(h, "fair-" + rank.name().toLowerCase() + "-" + (++made), rel).player();
        Progression.awaken(p);
        int lv = Math.max(1, rank.levelRequired);
        Progression.topUpToLevel(p, lv, Progression.XpSource.COMMAND);
        while (Progression.rank(p).ordinal() < rank.ordinal()) Progression.passReassessment(p);
        Progression.addStat(p, Stat.VIT, 3 * (lv - 1) / 6);
        HunterTier tier = HunterTier.valueOf(rank == Rank.SOVEREIGN ? "S" : rank.name());
        // unbreakable: an audit lands thousands of blows on one set, and worn-out armour would read as unfair damage
        for (Piece piece : List.of(Piece.HELMET, Piece.CHESTPLATE, Piece.LEGGINGS, Piece.BOOTS)) {
            ItemStack s = GearItems.stack(tier, piece);
            s.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            p.setItemSlot(switch (piece) {
                case HELMET -> EquipmentSlot.HEAD;
                case CHESTPLATE -> EquipmentSlot.CHEST;
                case LEGGINGS -> EquipmentSlot.LEGS;
                default -> EquipmentSlot.FEET;
            }, s);
        }
        // a test player's connection never ticks it, and only its own tick puts its armour's attributes on
        p.doTick();
        Progression.applyAttributes(p);
        dev.awaken.gear.SetBonus.refresh(p); // its set's own (the S set's Measure), which a tick only puts on every tenth
        p.setHealth(p.getMaxHealth());
        Awaken.LOG.info("[fair] reference {}: level {}, {} health, armour {}, toughness {}", rank.label, lv, p.getMaxHealth(),
                p.getAttributeValue(Attributes.ARMOR), p.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        return p;
    }

    private static final float POOL = 2000F; // MAX_ABSORPTION tops out at 2048

    /**
     * Readies {@code p} to measure what lands on it through the real path: full health and a deep absorption pool that
     * takes every blow after armour and stats, so a share over 100% shows instead of a death. No i-frames.
     */
    public static void pool(ServerPlayer p) {
        p.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(POOL);
        p.setAbsorptionAmount(POOL);
        p.setHealth(p.getMaxHealth());
        p.setInvulnerableTime(0);
        p.damageCooldownTime = 0; // 26.3's i-frames: a blow inside them only lands what it has over the last
        p.clearFire();
        p.removeAllEffects();
        FairGuard.forget(p.getUUID());
        POOLED.put(p.getUUID(), p.getHealth());
    }

    private static final java.util.Map<java.util.UUID, Float> POOLED = new java.util.HashMap<>();

    /** What has landed on {@code p} since {@link #pool}, as a share of its max health. */
    public static float share(ServerPlayer p) {
        float was = POOLED.getOrDefault(p.getUUID(), p.getMaxHealth());
        return ((POOL - p.getAbsorptionAmount()) + Math.max(0F, was - p.getHealth())) / p.getMaxHealth();
    }

    /** What would have landed without the fair guard: the source's own number. */
    public static float raw(ServerPlayer p) {
        return share(p) + FairGuard.trimmed(p.getUUID()) / p.getMaxHealth();
    }

    public static String pct(float share) {
        return Math.round(share * 100) + "%";
    }

    /**
     * The fair guard: a blow that would take 90% of an on-rank player's health takes half; a second from the same
     * creature inside the window takes nothing more, one from another creature lands; once the window has passed the
     * first creature lands again. A player a rank under takes all of it.
     */
    private static void guard(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = -1; x <= 6; x++) for (int z = -1; z <= 4; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
        MobDef def = Bestiary.get("toll_ghoul");
        int lv = Progression.contentLevel(Rank.D);
        MobRole role = new MobRole(MobRole.Role.NORMAL, lv, def.theme.id, Optional.empty(), "");
        AwakenMob a = Mobs.spawn(level, def, h.absoluteVec(new Vec3(4.5, 1, 1.5)), lv, role, Set.of());
        AwakenMob b = Mobs.spawn(level, def, h.absoluteVec(new Vec3(4.5, 1, 3.5)), lv, role, Set.of());
        h.assertTrue(a != null && b != null, "two creatures spawned");
        a.setNoAi(true);
        b.setNoAi(true);
        ServerPlayer on = reference(h, Rank.D, new BlockPos(1, 1, 1)), under = reference(h, Rank.E, new BlockPos(1, 1, 3));
        // a blow that lands 90% of the on-rank player's health after their armour: armour is off this one
        float big = on.getMaxHealth() * 0.9F;
        java.util.function.BiConsumer<ServerPlayer, AwakenMob> hit = (p, by) -> {
            p.damageCooldownTime = 0;
            p.hurtServer(level, level.damageSources().mobAttack(by), big);
        };
        for (ServerPlayer p : List.of(on, under)) {
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) p.setItemSlot(slot, ItemStack.EMPTY);
            p.doTick();
            pool(p);
        }
        float bigShare = big / on.getMaxHealth();
        hit.accept(on, a);
        h.assertTrue(Math.abs(share(on) - FairGuard.BLOW) < 0.02F, "one blow takes half, took " + pct(share(on)) + " of " + pct(bigShare));
        hit.accept(on, a);
        h.assertTrue(Math.abs(share(on) - FairGuard.SOURCE) < 0.02F, "the same creature again inside the window takes nothing more, " + pct(share(on)));
        hit.accept(on, b);
        h.assertTrue(share(on) > FairGuard.SOURCE + 0.3F, "another creature's blow lands, " + pct(share(on)));
        hit.accept(under, a);
        h.assertTrue(share(under) > 0.8F, "a rank under takes all of it, " + pct(share(under)));
        float before = share(on);
        h.runAfterDelay(FairGuard.WINDOW + 1, () -> {
            hit.accept(on, a);
            h.assertTrue(share(on) > before + 0.3F, "past the window it lands again, " + pct(share(on) - before));
            a.discard();
            b.discard();
            h.succeed();
        });
    }

    /**
     * Walking into a boss room never kills: the moment a Lord first sees the E reference player, three blows that would
     * each take 90% of them (two the Lord's, one a creature's in its room) take a quarter together.
     */
    private static void arrival(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        for (int x = -1; x <= 12; x++) for (int z = -1; z <= 12; z++) level.setBlockAndUpdate(h.absolutePos(new BlockPos(x, 0, z)), Blocks.STONE.defaultBlockState());
        int lv = Progression.contentLevel(Rank.E);
        AwakenMob lord = (AwakenMob) dev.awaken.core.api.MobHooks.spawnBoss(level, "awaken:bell_warden", h.absolutePos(new BlockPos(6, 1, 6)), lv, "");
        MobDef def = Bestiary.get("toll_ghoul");
        AwakenMob add = Mobs.spawn(level, def, h.absoluteVec(new Vec3(9.5, 1, 9.5)), lv, new MobRole(MobRole.Role.NORMAL, lv, def.theme.id, Optional.empty(), ""), Set.of());
        h.assertTrue(lord != null && add != null, "a Lord and one of its room");
        add.setNoAi(true);
        ServerPlayer p = reference(h, Rank.E, new BlockPos(3, 1, 3));
        pool(p);
        lord.setTarget(p);
        h.onEachTick(() -> {
            if (!FairGuard.inMoment(p)) {
                lord.setTarget(p);
                return;
            }
            float big = p.getMaxHealth() * 2.0F, was = share(p); // after the reference player's armour, still past 90%
            for (AwakenMob by : List.of(lord, lord, add)) {
                p.damageCooldownTime = 0;
                p.hurtServer(level, level.damageSources().mobAttack(by), big);
            }
            float took = share(p) - was;
            Awaken.LOG.info("[fair] arrival: three blows of {} each took {} together", big, pct(took));
            h.assertTrue(took <= FairGuard.ARRIVAL_SHARE + 0.01F, "the Lord's moment held them to a quarter, took " + pct(took));
            lord.discard();
            add.discard();
            h.succeed();
        });
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
                        near.getMaxHealth(), pct(raw(before)), pct(share(near)));
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
