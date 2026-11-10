package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.ArmsSounds;
import dev.awaken.arms.ArmsWalls;
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
        if (def != null) play(p, ArmsSounds.swish(def, dev.awaken.arms.FpSwings.swishSound(dev.awaken.arms.FpSwings.style(style.id()), move)),
                1.0F, ArmsSounds.swishPitch(cadence));
    }

    static void impact(Player p, int entityId, Archetypes.@Nullable Loaded style, int moveId) {
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(entityId);
        if (e == null) return;
        Archetype.Move move = Archetype.Move.byId(moveId);
        Archetype.MoveDef def = style == null || move == null ? null : style.move(move);
        play(e, ArmsSounds.impact(e, move, def, style == null ? null : style.id()), 1.0F, ArmsSounds.impactPitch(e, def));
    }

    /** A killing blow, over its impact: the body's drop by what it is made of (SPEC §25 "Fight Feel"). */
    static void kill(Entity e) {
        play(e, ArmsSounds.KILL.get(), 1.0F, ArmsSounds.killPitch(e));
    }

    /** One more body in the sweep: the cleave, a semitone up for each. */
    static void cleave(Entity e, int bodies) {
        play(e, ArmsSounds.CLEAVE.get(), 1.0F, ArmsSounds.cleavePitch(bodies));
    }

    static void raise(Player p) {
        play(p, ArmsSounds.GUARD_RAISE.get(), 1.0F, 1.0F);
    }

    static void deflect(Player p) {
        play(p, ArmsSounds.DEFLECT.get(), 1.0F, 1.0F);
    }

    static void block(Player p) {
        play(p, ArmsSounds.BLOCK.get(), 1.0F, 1.0F);
        play(p, ArmsSounds.BLOCK_STRUCK.get(), 1.0F, 1.0F);
        play(p, ArmsSounds.BLOCK_STEEL.get(), 1.0F, 1.0F);
    }

    /** A shot met our guard: its glance off the blade, or the block's lighter knock. */
    static void shot(Player p, boolean deflected) {
        if (deflected) play(p, ArmsSounds.GLANCE.get(), 1.0F, 1.0F);
        else {
            play(p, ArmsSounds.BLOCK.get(), ArmsSounds.SHOT_BLOCK_VOLUME, ArmsSounds.SHOT_BLOCK_PITCH);
            play(p, ArmsSounds.BLOCK_STRUCK.get(), ArmsSounds.SHOT_BLOCK_VOLUME, ArmsSounds.SHOT_BLOCK_PITCH);
        }
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

    /** A blow that struck and did no harm: Minecraft's own weak attack, so a blow the hands showed is answered. */
    static void dull(Entity e) {
        play(e, ArmsSounds.DULL.get(), 0.8F, 1.0F);
    }

    /** A weapon drawn (SPEC §25 "Weight in the Hand", stage 4): its family's sound. */
    static void draw(Player p, String style) {
        play(p, ArmsSounds.draw(style), ArmsSounds.drawVolume(style), ArmsSounds.drawPitch(style));
    }

    /** The Katana lowered to the hip after its rest: a soft click. */
    static void sheath(Player p) {
        play(p, ArmsSounds.DRAW_GAUNTLETS.get(), 0.35F, 1.5F);
    }

    /** The Katana's drawn cut, over its swish. */
    static void drawnCut(Player p) {
        for (ArmsSounds.Voice v : ArmsSounds.DRAWN_CUT) playOne(p, v.sound().get(), v.volume(), v.pitch());
    }

    /** A blade glancing off a block (stage 5): the block's own hit, louder and lower, and for stone or metal a ring over it. */
    static void glance(double x, double y, double z, net.minecraft.world.level.block.SoundType type, int kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(x, y, z, type.getHitSound(), SoundSource.BLOCKS, Math.min(1F, type.getVolume() * 0.9F), type.getPitch() * 0.75F, false);
        if (kind == ArmsWalls.GLANCE_HARD) {
            mc.level.playLocalSound(x, y, z, ArmsSounds.BLOCK_STEEL.get(), SoundSource.PLAYERS, 0.55F, 1.25F, false);
            mc.level.playLocalSound(x, y, z, ArmsSounds.GLANCE_STONE.get(), SoundSource.PLAYERS, 0.9F, 0.8F, false);
        } else if (kind == ArmsWalls.GLANCE_WOOD) {
            mc.level.playLocalSound(x, y, z, ArmsSounds.GLANCE_WOOD.get(), SoundSource.PLAYERS, 0.9F, 0.8F, false);
        }
    }

    /** A sound as its mix (ArmsSounds.mix): Minecraft's own recordings layered, or the one event when it has none. */
    private static void play(Entity at, SoundEvent sound, float volume, float pitch) {
        ArmsSounds.Voice[] mix = ArmsSounds.mix(sound);
        if (mix == null) {
            playOne(at, sound, volume, pitch);
            return;
        }
        for (ArmsSounds.Voice v : mix) playOne(at, v.sound().get(), volume * v.volume(), pitch * v.pitch());
    }

    private static void playOne(Entity at, SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.level.playLocalSound(at.getX(), at.getY(0.6), at.getZ(), sound, SoundSource.PLAYERS, volume, pitch, false);
    }
}
