package dev.awaken.enchant;

import dev.awaken.core.Forces;
import dev.awaken.enchant.net.EnchantPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import dev.awaken.core.AwakenNet;
import org.jspecify.annotations.Nullable;

/**
 * Enchantments seen at work (SPEC §45.7): the server says where, in which colour and how strongly, and the client draws
 * it in our own look. A ward flashes its mark where a blow landed; a bane leaves a brief mark on the creature; an extra
 * drop glints gold as it falls (Harvest, Looting, Fortune); a block lifts out whole (Silk Touch); an orb runs into the
 * piece it mends along a thread; a softened fall puffs light (Feather Falling); a learned page rises in light; an old
 * table turns with a flare. Each sends one small message to those near, never more than one per hit, block or orb.
 */
final class EnchantFx {
    private EnchantFx() {}

    static int colour(Forces.Force f) {
        return switch (f) {
            case HEAT -> 0xFF8A4C;
            case CHARGE -> 0xFFD84A;
            case GRAVITY -> 0xB993FF;
            case SPIRIT -> 0x4FE0C2;
            case FROST -> 0x8FD8FF;
        };
    }

    /** Who sees it: players within this many blocks. */
    static final double SEEN = 48;

    private static void send(Entity at, int kind, int colour, float strength) {
        send(at, kind, colour, strength, 0, Float.NaN);
    }

    private static void send(Entity at, int kind, int colour, float strength, int no, float dir) {
        if (at.level() instanceof ServerLevel level) near(level, at.getX(), at.getY(), at.getZ(), new EnchantPayloads.Fx(kind, at.getId(), at.getX(),
                at.getY() + at.getBbHeight() * 0.6, at.getZ(), colour, strength, no, dir));
    }

    /** The angle on the ground from one place toward another (NaN when there's no telling). */
    private static float toward(Vec3 from, @Nullable Vec3 to) {
        if (to == null) return Float.NaN;
        double dx = to.x - from.x, dz = to.z - from.z;
        return dx * dx + dz * dz < 1e-4 ? Float.NaN : (float) Math.atan2(dz, dx);
    }

    /** The number of the ward against a Force (its mark flashes when it takes some of a blow). */
    static int wardNo(Forces.Force f) {
        for (Inscriptions.Entry e : Inscriptions.ALL) if (e.force() == f) return e.no();
        return 0;
    }

    static boolean has(ItemStack s, ResourceKey<Enchantment> key) {
        if (s.isEmpty()) return false;
        for (Holder<Enchantment> h : s.getEnchantments().keySet()) if (h.is(key)) return true;
        return false;
    }

    // per player through AwakenNet, so test players without our channels are skipped
    private static void near(ServerLevel level, double x, double y, double z, EnchantPayloads.Fx fx) {
        for (ServerPlayer sp : level.players()) if (sp.distanceToSqr(x, y, z) < SEEN * SEEN) AwakenNet.send(sp, fx);
    }

    /** A ward took {@code share} (0-0.3) off a blow of this Force that came from {@code from} (null: from nowhere in particular). */
    static void warded(ServerPlayer p, Forces.Force force, float share, @Nullable Vec3 from) {
        send(p, EnchantPayloads.FX_WARD, colour(force), share, wardNo(force), toward(p.position(), from));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), EnchantContent.NOTE_WARD.get(), SoundSource.PLAYERS, 0.55F + share, 1F);
    }

    /** A bane (numbered {@code no}) bit this creature: its mark burns on the side the blow came from. */
    static void baned(ServerPlayer p, LivingEntity target, int no) {
        send(target, EnchantPayloads.FX_BANE, Family.BANE.colour, 1F, no, toward(target.position(), p.position()));
        p.level().playSound(null, target.getX(), target.getY(), target.getZ(), EnchantContent.NOTE_BANE.get(), SoundSource.PLAYERS, 0.8F, 1F);
    }

    /** Harvest's extra drop. */
    static void spoils(ServerLevel level, Entity drop) {
        send(drop, EnchantPayloads.FX_SPOILS, Family.SPOILS.colour, 1F);
        level.playSound(null, drop.getX(), drop.getY(), drop.getZ(), EnchantContent.NOTE_SPOILS.get(), SoundSource.PLAYERS, 0.8F, 1F);
    }

    /** Looting's and Fortune's extra: the first one or two drops a stack richer than one glint as they fall. */
    private static void glint(ServerLevel level, Iterable<ItemEntity> drops) {
        int n = 0;
        for (ItemEntity d : drops) {
            if (d.getItem().getCount() < 2) continue;
            send(d, EnchantPayloads.FX_SPOILS, Family.SPOILS.colour, 0.8F);
            if (n++ == 0) level.playSound(null, d.getX(), d.getY(), d.getZ(), EnchantContent.NOTE_SPOILS.get(), SoundSource.PLAYERS, 0.6F, 1.05F);
            if (n == 2) return;
        }
    }

    static void onBlockDrops(BlockDropsEvent e) {
        ItemStack tool = e.getTool();
        if (tool.isEmpty() || !(e.getBreaker() instanceof ServerPlayer) || e.getDrops().isEmpty()) return;
        if (has(tool, Enchantments.SILK_TOUCH)) {
            // only when the block itself came away (not what Silk Touch leaves alone, like a flower's own drop)
            var whole = e.getState().getBlock().asItem();
            for (ItemEntity d : e.getDrops()) {
                if (!d.getItem().is(whole)) continue;
                BlockPos pos = e.getPos();
                near(e.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new EnchantPayloads.Fx(EnchantPayloads.FX_SILK, -1,
                        pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, Family.SPOILS.colour, 1F, 16, Float.NaN));
                return;
            }
        } else if (has(tool, Enchantments.FORTUNE)) glint(e.getLevel(), e.getDrops());
    }

    static void onLootingDrops(LivingDropsEvent e) {
        if (e.getEntity().level() instanceof ServerLevel level && e.getSource().getEntity() instanceof ServerPlayer p
                && has(p.getMainHandItem(), Enchantments.LOOTING)) glint(level, e.getDrops());
    }

    /** An orb about to be taken: if it will mend something, a thread runs from it into the piece. */
    static void onPickupXp(PlayerXpEvent.PickupXp e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        // Insight in the hand: the experience comes in with a gold edge (one glint in eight ticks, however many orbs)
        if ((p.tickCount & 7) == 0 && has(p.getMainHandItem(), INSIGHT)) {
            var in = e.getOrb();
            near((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), new EnchantPayloads.Fx(EnchantPayloads.FX_SPOILS, in.getId(),
                    in.getX(), in.getY() + 0.15, in.getZ(), Family.SPOILS.colour, 0.6F, 0, Float.NaN));
        }
        var mended = EnchantmentHelper.getRandomItemWith(EnchantmentEffectComponents.REPAIR_WITH_XP, p, ItemStack::isDamaged);
        if (mended.isEmpty()) return;
        EquipmentSlot slot = mended.get().inSlot();
        var orb = e.getOrb();
        near((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), new EnchantPayloads.Fx(EnchantPayloads.FX_MEND, p.getId(),
                orb.getX(), orb.getY() + 0.15, orb.getZ(), Family.KEEPING.colour, 1F, slot == null ? 0 : slot.ordinal(), Float.NaN));
    }

    /** A creature a bane killed goes out in the bane's red: a ring of it over the ground where it fell, motes lifting off. */
    static void banedOut(ServerPlayer p, LivingEntity dead, int no) {
        near((ServerLevel) p.level(), dead.getX(), dead.getY(), dead.getZ(), new EnchantPayloads.Fx(EnchantPayloads.FX_PUFF, dead.getId(),
                dead.getX(), dead.getY(), dead.getZ(), Family.BANE.colour, 0.9F, no, Float.NaN));
    }

    /** Gather: a gold thread from where a thing fell into the hand that took it. */
    static void gathered(ServerPlayer p, double x, double y, double z) {
        near((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), new EnchantPayloads.Fx(EnchantPayloads.FX_MEND, p.getId(),
                x, y, z, Family.SPOILS.colour, 1F, EquipmentSlot.MAINHAND.ordinal(), Float.NaN));
    }

    /** Regrowth: a green thread out of the open sky into the piece it mends. */
    static void regrew(ServerPlayer p, EquipmentSlot slot) {
        near((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), new EnchantPayloads.Fx(EnchantPayloads.FX_MEND, p.getId(),
                p.getX(), p.getEyeY() + 1.5, p.getZ(), Words.REGROWTH_COLOUR, 0.8F, slot.ordinal(), Float.NaN));
    }

    /** Remembrance: its glyph lifts where you stand as a name is kept. */
    static void remembered(ServerPlayer p) {
        near((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), new EnchantPayloads.Fx(EnchantPayloads.FX_PUFF, p.getId(),
                p.getX(), p.getY(), p.getZ(), Family.KEEPING.colour, 0.6F, 35, Float.NaN));
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), EnchantContent.NOTE_KEEP.get(), SoundSource.PLAYERS, 0.6F, 0.9F);
    }

    /** Clear Blood burning poison off: a green pulse where you stand, once a second while it works. */
    static void cleared(ServerPlayer p) {
        near((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), new EnchantPayloads.Fx(EnchantPayloads.FX_PUFF, p.getId(),
                p.getX(), p.getY(), p.getZ(), 0x9FE07A, 0.35F, 9, Float.NaN));
    }

    private static final ResourceKey<Enchantment> INSIGHT = ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, dev.awaken.Awaken.id("insight"));

    /** A fall of four blocks or more on Feather Falling boots: a puff of light where you land. */
    static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getDistance() < 4 || !has(p.getItemBySlot(EquipmentSlot.FEET), Enchantments.FEATHER_FALLING)) return;
        near((ServerLevel) p.level(), p.getX(), p.getY(), p.getZ(), new EnchantPayloads.Fx(EnchantPayloads.FX_PUFF, p.getId(),
                p.getX(), p.getY(), p.getZ(), Family.WARD.colour, (float) Math.min(1, e.getDistance() / 12), 8, Float.NaN));
    }

    /** A page learned: its glyph lifts in front of the reader, cut by cut, and runs into them. */
    static void learned(ServerPlayer p, Inscriptions.Entry e) {
        send(p, EnchantPayloads.FX_LEARN, e.colour(), 1F, e.no(), Float.NaN);
    }

    /** A page given: it flies from where it was (a fallen Lord, a lode's heart) to the one it goes to, its sign showing. */
    static void page(ServerPlayer p, Inscriptions.Entry e, @Nullable Vec3 from) {
        if (from == null || !(p.level() instanceof ServerLevel level)) return;
        near(level, p.getX(), p.getY(), p.getZ(), new EnchantPayloads.Fx(EnchantPayloads.FX_PAGE, p.getId(), from.x, from.y, from.z, e.colour(), 1F, e.no(), Float.NaN));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), EnchantContent.PAGE_TURN.get(), SoundSource.PLAYERS, 0.9F, 1F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.3F, 0.8F);
    }

    /** An old enchanting table turned into an Inscribing Table. */
    static void flare(ServerLevel level, BlockPos pos) {
        near(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new EnchantPayloads.Fx(EnchantPayloads.FX_FLARE, -1, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 0x3A6BFF, 1F));
        level.playSound(null, pos, EnchantContent.FLARE.get(), SoundSource.BLOCKS, 1F, 1F);
    }
}
