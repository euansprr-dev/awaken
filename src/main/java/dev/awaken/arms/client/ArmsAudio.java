package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.ArmsSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * What the fighter hears of their own fight (SPEC §25), on the frame it happens: the swish on the press, the impact
 * on the confirmed hit, the guard's answer, the dodge, the heavy's draw and glint, a blow landing on them. Everyone
 * else hears the server's copy of the same choice ({@link ArmsSounds}).
 */
final class ArmsAudio {
    private ArmsAudio() {}

    static void swing(Player p, Archetypes.Loaded style, Archetype.Move move, int cadence) {
        Archetype.MoveDef def = style.move(move);
        if (def != null) play(p, ArmsSounds.swish(def), 1.0F, ArmsSounds.swishPitch(cadence));
    }

    static void impact(Player p, int entityId, Archetypes.@Nullable Loaded style, int moveId) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
        if (e == null) return;
        Archetype.Move move = Archetype.Move.byId(moveId);
        Archetype.MoveDef def = style == null || move == null ? null : style.move(move);
        play(e, ArmsSounds.impact(e, move, def, style == null ? null : style.id()), 1.0F, ArmsSounds.impactPitch(e, def));
    }

    static void deflect(Player p) {
        play(p, ArmsSounds.DEFLECT.get(), 1.0F, 1.0F);
    }

    static void block(Player p) {
        play(p, ArmsSounds.BLOCK.get(), 1.0F, 1.0F);
    }

    /** A shot met our guard: its glance off the blade, or the block's lighter knock. */
    static void shot(Player p, boolean deflected) {
        if (deflected) play(p, ArmsSounds.GLANCE.get(), 1.0F, 1.0F);
        else play(p, ArmsSounds.BLOCK.get(), ArmsSounds.SHOT_BLOCK_VOLUME, ArmsSounds.SHOT_BLOCK_PITCH);
    }

    static void guardBreak(Player p) {
        play(p, ArmsSounds.GUARD_BREAK.get(), 1.0F, 1.0F);
    }

    static void perfectDodge(Player p) {
        play(p, ArmsSounds.PERFECT_DODGE.get(), 1.0F, 1.0F);
    }

    static void dodge(Player p) {
        play(p, ArmsSounds.DODGE.get(), 1.0F, 1.0F);
    }

    static void charge(Player p) {
        play(p, ArmsSounds.CHARGE.get(), 1.0F, 1.0F);
    }

    static void glint(Player p) {
        play(p, ArmsSounds.GLINT.get(), 1.0F, 1.0F);
    }

    static void hurt(Player p) {
        play(p, ArmsSounds.HURT.get(), 1.0F, 1.0F);
    }

    private static void play(Entity at, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(at.getX(), at.getY(0.6), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }
}
