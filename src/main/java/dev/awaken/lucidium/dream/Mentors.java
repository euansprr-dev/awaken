package dev.awaken.lucidium.dream;

import dev.awaken.lucidium.world.DreamBlueprint;
import dev.awaken.progression.Progression;
import dev.awaken.progression.content.Content;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.Entity;

/**
 * Your Path's mentor: a friendly duel in the dream. The mentor can't kill you (its blows stop short) and yields
 * when worn down. An authored opponent spawned fresh each time, with stats from your rank only.
 */
final class Mentors {
    private Mentors() {}

    static void start(ActiveDream ad, DreamBlueprint.Node node, ServerPlayer player) {
        ad.mentorState = 1;
        String path = Progression.profile(player).path().orElse("");
        var def = Content.PATHS.get(path);
        String name = def != null ? def.name() : "your Path";
        Vindicator mentor = EntityTypes.VINDICATOR.create(ad.level, EntitySpawnReason.TRIGGERED);
        if (mentor == null) {
            ad.mentorState = 2;
            return;
        }
        BlockPos at = ad.standingSpot(node.feet().offset(0, 1, 4));
        if (at == null) at = node.feet();
        mentor.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180, 0);
        mentor.finalizeSpawn(ad.level, ad.level.getCurrentDifficultyAt(at), EntitySpawnReason.TRIGGERED, null);
        int rank = Progression.rank(player).ordinal();
        AttributeInstance hp = mentor.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(24 + 6 * rank);
        AttributeInstance dmg = mentor.getAttribute(Attributes.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(3 + rank);
        mentor.setHealth(mentor.getMaxHealth());
        mentor.setCustomName(Component.literal("Mentor of " + name));
        mentor.setCustomNameVisible(true);
        mentor.setPersistenceRequired();
        mentor.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 600, 0, false, false));
        mentor.setTarget(player);
        ad.level.addFreshEntity(mentor);
        ad.mentor = mentor.getUUID();
        Signal.send(player, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Your mentor is waiting"))
                .body(Component.literal("A friendly duel. Neither of you can be hurt for real here."))
                .footer(Component.literal(name)).ttl(20 * 6).build());
    }

    /** Ends the duel. {@code playerWon}: the mentor yields and you learn from it. */
    static void end(ActiveDream ad, ServerPlayer player, boolean playerWon) {
        if (ad.mentorState != 1) return;
        ad.mentorState = 2;
        Entity mentor = ad.mentor == null ? null : ad.level.getEntity(ad.mentor);
        if (mentor != null) {
            ad.level.sendParticles(ParticleTypes.END_ROD, mentor.getX(), mentor.getY() + 1, mentor.getZ(), 30, 0.4, 0.8, 0.4, 0.03);
            mentor.discard();
        }
        if (playerWon) {
            Progression.grantLevels(player, 0.20, Progression.XpSource.DREAM);
            Progression.count(player, "awaken:mentor_duels_won");
            Dreams.dropResidue(ad.level, player.position(), 3);
            Signal.send(player, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Your mentor yields"))
                    .body(Component.literal("\"Good. Again, another night.\"")).ttl(20 * 6).build());
        } else {
            player.setHealth(Math.max(player.getHealth(), player.getMaxHealth() * 0.6f));
            Signal.send(player, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Your mentor lowers their weapon"))
                    .body(Component.literal("\"Not yet. Rest, and come back.\"")).ttl(20 * 6).build());
        }
    }
}
