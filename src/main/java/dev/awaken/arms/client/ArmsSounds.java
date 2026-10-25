package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * What the fighter hears on their own prediction and results (SPEC §24): the swish on the press (rising a semitone
 * per rhythm step), the impact on the confirmed hit, the deflect's ring, the dodge's rush. Everyone else hears the
 * server's. These are the slice's stand-ins until the Arms sound kit (tools/art/specs/arms_sounds.py) lands.
 */
final class ArmsSounds {
    private ArmsSounds() {}

    private static final float SEMITONE = 1.0594631F;

    static void swing(Player p, Archetypes.Loaded style, Archetype.Move move, int cadence) {
        float pitch = (float) Math.pow(SEMITONE, cadence);
        boolean big = move == Archetype.Move.HEAVY || move == Archetype.Move.EXECUTION;
        play(p, SoundEvents.PLAYER_ATTACK_SWEEP, big ? 0.7F : 0.45F, (big ? 0.75F : 1.25F) * pitch);
    }

    static void impact(Player p, int entityId, int move, int cadence) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
        Entity at = e != null ? e : p;
        boolean big = move == Archetype.Move.HEAVY.ordinal() || move == Archetype.Move.EXECUTION.ordinal() || move == Archetype.Move.STRIKE_3.ordinal();
        play(at, big ? SoundEvents.PLAYER_ATTACK_CRIT : SoundEvents.PLAYER_ATTACK_STRONG, big ? 1.0F : 0.8F, (float) Math.pow(SEMITONE, cadence));
    }

    static void deflect(Player p) {
        play(p, SoundEvents.ANVIL_LAND, 0.5F, 1.95F);
        play(p, SoundEvents.TRIDENT_HIT, 0.7F, 1.6F);
    }

    static void perfectDodge(Player p) {
        play(p, SoundEvents.ENDER_EYE_DEATH, 0.5F, 1.6F);
    }

    static void dodge(Player p) {
        play(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.35F, 1.9F);
    }

    static void charge(Player p) {
        play(p, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.25F, 1.4F);
    }

    static void glint(Player p) {
        play(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.8F);
    }

    private static void play(Entity at, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(at.getX(), at.getY(), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }
}
