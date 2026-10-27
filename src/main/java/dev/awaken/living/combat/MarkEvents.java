package dev.awaken.living.combat;

import dev.awaken.living.combat.Forms.Form;
import dev.awaken.living.LivingContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** What forms do outside their own goal (SPEC §21.11): a revenant rises once, a spider's webs are taken away, a form's voice is lower. */
public final class MarkEvents {
    private MarkEvents() {}

    /** A revenant (zombie form from the fourth rung) stands up once at 40% health. */
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        Form f = Forms.of(mob);
        Forms.Species sp = Forms.Species.of(mob.getType());
        if (!f.worn() || sp == null || !sp.layout.equals("zombie") || f.rung() < 4 || (f.marks() & Forms.RISEN) != 0) return;
        e.setCanceled(true);
        mob.setHealth(Math.max(1F, mob.getMaxHealth() * 0.4F));
        mob.clearFire();
        mob.setInvulnerableTime(40);
        mob.setData(LivingContent.FORM.get(), new Form(f.rung(), f.family(), f.marks() | Forms.RISEN));
        level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(Moves.COLOUR[Math.min(5, f.rung())], 2F), mob.getX(), mob.getY() + 1, mob.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 1.4F, 0.5F);
    }

    /** Remember a cobweb to take away. */
    static void expire(ServerLevel level, BlockPos pos, long until) {
        if (Moves.WEBS.size() > 96) Moves.WEBS.poll();
        Moves.WEBS.add(new Moves.Web(level, pos, until));
    }

    public static void onTick(ServerTickEvent.Post e) {
        if (Moves.WEBS.isEmpty() || e.getServer().getTickCount() % 20 != 0) return;
        var it = Moves.WEBS.iterator();
        while (it.hasNext()) {
            Moves.Web w = it.next();
            if (w.level().getGameTime() < w.until()) continue;
            if (w.level().getBlockState(w.pos()).is(Blocks.COBWEB)) w.level().removeBlock(w.pos(), false);
            it.remove();
        }
    }

    /** A form's voice drops with its rung: a tenth lower in D, to two fifths lower in S, a little louder (decision 20). */
    public static float pitch(int rung) {
        return 1F - 0.08F * Math.max(0, Math.min(5, rung));
    }

    public static void onSound(PlayLevelSoundEvent.AtPosition e) {
        if (!(e.getLevel() instanceof ServerLevel level) || (e.getSource() != SoundSource.HOSTILE && e.getSource() != SoundSource.NEUTRAL)) return;
        var at = e.getPosition();
        Mob best = null;
        double bd = 1e9;
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(0.5, 1.0, 0.5), m -> Forms.of(m).worn())) {
            double d = m.position().distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        if (best == null) return;
        int rung = Forms.of(best).rung();
        e.setNewPitch(e.getNewPitch() * pitch(rung));
        e.setNewVolume(e.getNewVolume() * (1F + 0.05F * rung));
    }
}
