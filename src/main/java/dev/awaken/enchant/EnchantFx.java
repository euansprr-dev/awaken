package dev.awaken.enchant;

import dev.awaken.core.Forces;
import dev.awaken.enchant.net.EnchantPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import dev.awaken.core.AwakenNet;

/**
 * Enchantments seen at work (SPEC §45.7): the server says where, in which colour and how strongly, and the client draws
 * it in our own look. A ward flashes its mark where a blow landed; a bane leaves a brief mark on the creature; an extra
 * drop glints gold; a learned page rises in light; an old table turns with a flare. Each sends one small message to
 * those near, never more than one per hit.
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
        if (at.level() instanceof ServerLevel level) near(level, at.getX(), at.getY(), at.getZ(), new EnchantPayloads.Fx(kind, at.getId(), at.getX(),
                at.getY() + at.getBbHeight() * 0.6, at.getZ(), colour, strength));
    }

    // per player through AwakenNet, so test players without our channels are skipped
    private static void near(ServerLevel level, double x, double y, double z, EnchantPayloads.Fx fx) {
        for (ServerPlayer sp : level.players()) if (sp.distanceToSqr(x, y, z) < SEEN * SEEN) AwakenNet.send(sp, fx);
    }

    /** A ward took {@code share} (0-0.3) off a blow of this Force. */
    static void warded(ServerPlayer p, Forces.Force force, float share) {
        send(p, EnchantPayloads.FX_WARD, colour(force), share);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), EnchantContent.NOTE_WARD.get(), SoundSource.PLAYERS, 0.35F + share, 1.15F);
    }

    /** A bane bit this creature. */
    static void baned(ServerPlayer p, LivingEntity target) {
        send(target, EnchantPayloads.FX_BANE, Family.BANE.colour, 1F);
        p.level().playSound(null, target.getX(), target.getY(), target.getZ(), EnchantContent.NOTE_BANE.get(), SoundSource.PLAYERS, 0.45F, 1.3F);
    }

    /** Harvest's extra drop. */
    static void spoils(ServerLevel level, Entity drop) {
        send(drop, EnchantPayloads.FX_SPOILS, Family.SPOILS.colour, 1F);
        level.playSound(null, drop.getX(), drop.getY(), drop.getZ(), EnchantContent.NOTE_SPOILS.get(), SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    /** A page learned. */
    static void learned(ServerPlayer p, Inscriptions.Entry e) {
        send(p, EnchantPayloads.FX_LEARN, e.colour(), 1F);
    }

    /** An old enchanting table turned into an Inscribing Table. */
    static void flare(ServerLevel level, BlockPos pos) {
        near(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                new EnchantPayloads.Fx(EnchantPayloads.FX_FLARE, -1, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 0x3A6BFF, 1F));
        level.playSound(null, pos, EnchantContent.FLARE.get(), SoundSource.BLOCKS, 1F, 1F);
    }
}
