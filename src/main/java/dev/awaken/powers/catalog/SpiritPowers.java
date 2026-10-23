package dev.awaken.powers.catalog;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.Forces;
import dev.awaken.powers.Cast;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Combat;
import dev.awaken.powers.Constructs;
import dev.awaken.powers.ForceInteractions;
import dev.awaken.powers.Fx;
import dev.awaken.powers.Moves;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.PowerState;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.PowerType.Family;
import dev.awaken.powers.PowerType.Result;
import dev.awaken.powers.PowerType.Slot;
import dev.awaken.powers.PowerType.Variant;
import dev.awaken.powers.Powers;
import dev.awaken.powers.Running;
import dev.awaken.powers.Scheduler;
import dev.awaken.powers.TerrainLedger;
import dev.awaken.powers.net.PowerPayloads;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Spirit: a calm power that hits very hard. Path signatures and latents live here too. Scales with SPI. */
public final class SpiritPowers {
    private SpiritPowers() {}

    static final int JADE = 0x86E0A0;
    static final int SPIRIT = 0x9FB0FF;

    /** Blocks the spirit can walk through (Spirit meets Mirror). Gates add their mirror blocks to this tag. */
    public static final TagKey<Block> MIRROR = TagKey.create(Registries.BLOCK, Awaken.id("mirror"));

    static void register() {
        PowerType.builder("aura", "Aura", Family.SPIRIT, Slot.PASSIVE)
                .forces(Forces.Force.SPIRIT).damage(1.2f).unlock(Rank.E).icon("aura")
                .desc("Your spirit leaks out. Hostiles right next to you take a little spirit damage, and out of combat you slowly heal.")
                .passive(SpiritPowers::aura).register();

        PowerType.builder("spirit_palm", "Spirit Palm", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.SPIRIT).cost(16).cooldown(100).damage(8).unlock(Rank.E).icon("palm")
                .desc("A palm strike that sends your spirit through the target: it's thrown back, and a second later the echo hits again.")
                .variant(Variant.of("Echo Palm", "A lighter strike with a doubled echo").dmg(0.7f))
                .variant(Variant.of("Push", "A wide push that throws everything in front of you").dmg(0.8f))
                .cast(SpiritPowers::spiritPalm).register();

        PowerType.builder("mend", "Mend", Family.SPIRIT, Slot.ACTIVE)
                .cost(25).cooldown(400).unlock(Rank.E).icon("mend")
                .desc("Heal over 2 s (more the more you're missing) and wash off poison, wither and weakness. Players near you heal half as much.")
                .variant(Variant.of("Surge", "All at once, just for you").dmg(0.8f))
                .variant(Variant.of("Circle", "Heals everyone near you fully as much").dmg(0.75f))
                .cast(SpiritPowers::mend).register();

        PowerType.builder("third_eye", "Third Eye", Family.SPIRIT, Slot.ACTIVE)
                .cost(15).cooldown(400).unlock(Rank.D).icon("eye")
                .desc("Open your third eye for 8 s: every hostile within 32 blocks shows through walls, even invisible ones.")
                .variant(Variant.of("Far", "Sees 64 blocks, for less time").dur(0.7f))
                .variant(Variant.of("Long", "Stays open 14 s").dur(1.75f))
                .cast(SpiritPowers::thirdEye).register();

        PowerType.builder("cord_lash", "Cord Lash", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.SPIRIT).cost(18).cooldown(120).damage(6).unlock(Rank.D).icon("lash")
                .desc("Lash a target up to 14 blocks away with your spirit cord and haul it to your feet, dazed.")
                .variant(Variant.of("Anchor", "Haul yourself to the target instead"))
                .variant(Variant.of("Bind", "Don't pull: bind it in place for longer"))
                .cast(SpiritPowers::cordLash).register();

        PowerType.builder("bulwark", "Bulwark", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.SPIRIT).cost(40).cooldown(600).unlock(Rank.C).hidden().icon("bulwark")
                .desc("Warden's signature. A dome of will for 8 s: allies inside take half damage, arrows dissolve against it, enemies are pushed out.")
                .variant(Variant.of("Aegis", "A wider dome").radius(1.4f))
                .variant(Variant.of("Fortress", "Lasts 12 s").dur(1.5f))
                .cast(SpiritPowers::bulwark).register();

        PowerType.builder("hundred_palms", "Hundred Palms", Family.SPIRIT, Slot.ULTIMATE)
                .forces(Forces.Force.SPIRIT).cost(50).cooldown(900).damage(1.6f).unlock(Rank.C).hidden().icon("palms")
                .desc("Monk's signature. Two seconds of palms too fast to see in front of you, ending in one that launches. Harder bare-handed.")
                .variant(Variant.of("Storm", "A wide, lighter flurry").dmg(0.7f))
                .variant(Variant.of("Focus", "Every palm on the one target in front of you").dmg(1.6f))
                .cast(SpiritPowers::hundredPalms).register();

        PowerType.builder("void_maw", "Void Maw", Family.SPIRIT, Slot.ULTIMATE)
                .forces(Forces.Force.GRAVITY, Forces.Force.SPIRIT).cost(55).cooldown(1200).damage(3).unlock(Rank.C).hidden().icon("maw")
                .desc("Hollow's signature. Tear open a maw where you aim. It drags everything in, chews, and swallows the weak whole, feeding you.")
                .variant(Variant.of("Abyss", "Wider and slower").radius(1.4f).dur(1.3f))
                .variant(Variant.of("Hunger", "Swallows anything below a quarter of its health"))
                .cast(SpiritPowers::voidMaw).register();

        PowerType.builder("waking_dream", "Waking Dream", Family.SPIRIT, Slot.ACTIVE)
                .cost(25).cooldown(200).unlock(Rank.C).hidden().icon("dream")
                .desc("Dreamwalker's signature. Shape the world like a lucid dream: look at a wall for a door, at a gap for a bridge, up a ledge for stairs.")
                .cast(SpiritPowers::wakingDream).register();

        PowerType.builder("storm_call", "Storm Call", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.CHARGE).cost(45).cooldown(600).damage(14).unlock(Rank.D).hidden().icon("storm")
                .desc("A latent gift from the storm. Call lightning down on up to five enemies around you. Stronger in the rain.")
                .variant(Variant.of("Tempest", "Up to eight weaker strikes").dmg(0.7f))
                .cast(SpiritPowers::stormCall).register();

        PowerType.builder("ember_step", "Ember Step", Family.SPIRIT, Slot.MOVEMENT)
                .forces(Forces.Force.HEAT).cost(14).cooldown(30).damage(2).unlock(Rank.D).hidden().icon("ember").dash()
                .desc("A latent gift from the lava. Dash forward leaving a trail of fire that burns whatever follows you.")
                .predict((p, variant, level) -> {
                    Vec3 f = Moves.flat(p.getLookAngle());
                    return new Vec3(f.x * 1.7, p.onGround() ? 0.12 : 0.3, f.z * 1.7);
                })
                .cast(SpiritPowers::emberStep).register();

        PowerType.builder("frost_nova", "Frost Nova", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.FROST).cost(28).cooldown(240).damage(7).unlock(Rank.D).hidden().icon("frost")
                .desc("A latent gift from the cold. A ring of frost freezes everything around you: slowed, frosted, and ready to burst into steam.")
                .cast(SpiritPowers::frostNova).register();

        PowerType.builder("ghost_step", "Ghost Step", Family.SPIRIT, Slot.MOVEMENT)
                .forces(Forces.Force.SPIRIT).cost(14).cooldown(50).damage(3).unlock(Rank.D).hidden().icon("ghost")
                .desc("A latent gift from the Silent Halls. Slip 7 blocks forward through enemies and thin walls. Mirrors let you through any distance.")
                .cast(SpiritPowers::ghostStep).register();

        PowerType.builder("twin_strike", "Twin Strike", Family.SPIRIT, Slot.ACTIVE)
                .forces(Forces.Force.SPIRIT).cost(25).cooldown(400).unlock(Rank.C).hidden().icon("twin")
                .desc("A latent gift from the Double Gate. For 8 s your twin soul repeats every blow you land, half a second later.")
                .cast(SpiritPowers::twinStrike).register();
    }

    // ---- behaviours -------------------------------------------------------------------------------------------------

    static void aura(Cast c) {
        if (c.player.tickCount % 40 >= PowerType.PASSIVE_PERIOD) return;
        List<LivingEntity> near = Combat.foesAround(c.player, c.pos().add(0, 1, 0), 3.0);
        for (LivingEntity e : near) {
            dev.awaken.powers.Combat.hurtFrom(c.player, null, e, c.dmg(), Forces.Force.SPIRIT, false);
            c.level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 2, 0.2, 0.3, 0.2, 0.01);
        }
        if (near.isEmpty() && !Powers.inCombat(c.player) && c.player.tickCount % 80 < PowerType.PASSIVE_PERIOD
                && c.player.getHealth() < c.player.getMaxHealth()) {
            c.player.heal(0.5f);
        }
        if (c.player.tickCount % 20 < PowerType.PASSIVE_PERIOD) {
            c.level.sendParticles(Fx.dust(JADE, 0.6f), c.player.getX(), c.player.getY() + 0.1, c.player.getZ(), 2, 0.4, 0.05, 0.4, 0);
        }
    }

    static Result spiritPalm(Cast c) {
        Moves.swing(c.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (c.is(2)) {
            List<LivingEntity> hit = Combat.foesInCone(c.player, c.pos(), c.look(), 5, 40);
            if (hit.isEmpty()) return Result.fail("no_target");
            for (LivingEntity e : hit) {
                c.hurt(e, c.dmg(), Forces.Force.SPIRIT);
                Combat.knockFrom(e, c.pos(), 2.6, 0.4);
            }
            Fx.burstRing(c.level, c.eye().add(c.look()), Fx.dust(SPIRIT, 1.2f), 16, 0.4);
            c.sound(PowerSounds.SPIRIT, 1.2f, 0.8f);
            c.feel(hit.get(0), 40, 2f);
            return Result.OK;
        }
        LivingEntity t = Combat.aimEntity(c, 4.5, 0.6);
        if (t == null) return Result.fail("no_target");
        c.hurt(t, c.dmg(), Forces.Force.SPIRIT);
        Combat.knockFrom(t, c.pos(), 2.0, 0.35);
        Vec3 at = t.getBoundingBox().getCenter();
        c.level.sendParticles(Fx.dust(SPIRIT, 1.5f), at.x, at.y, at.z, 16, 0.3, 0.3, 0.3, 0);
        c.level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        c.sound(at, PowerSounds.SPIRIT, 1.2f, 1f);
        c.feel(t, 60, 2.5f);
        float echo = c.dmg() * (c.is(1) ? 1.0f : 0.5f);
        Scheduler.after(20, () -> {
            if (!t.isAlive() || c.player.isRemoved()) return;
            Combat.hurtFrom(c.player, null, t, echo, Forces.Force.SPIRIT, true);
            Vec3 p = t.getBoundingBox().getCenter();
            c.level.sendParticles(ParticleTypes.SOUL, p.x, p.y, p.z, 10, 0.3, 0.4, 0.3, 0.05);
            c.level.playSound(null, p.x, p.y, p.z, PowerSounds.SPIRIT.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.5f);
        });
        return Result.OK;
    }

    static Result mend(Cast c) {
        int spi = Progression.stat(c.player, Stat.SPI);
        float missing = c.player.getMaxHealth() - c.player.getHealth();
        float total = (float) ((4 + 0.2 * spi + 0.2 * missing) * c.v.dmg() * (1 + 0.12 * (c.powerLevel - 1)));
        cleanse(c.player);
        c.sound(PowerSounds.HEAL, 1f, 1.1f);
        List<Player> allies = new ArrayList<>();
        if (!c.is(1)) {
            double r = c.is(2) ? 10 : 6;
            for (Player p : c.level.getEntitiesOfClass(Player.class, c.player.getBoundingBox().inflate(r), p -> p != c.player && p.isAlive())) allies.add(p);
        }
        float allyShare = c.is(2) ? 1.0f : 0.5f;
        if (c.is(1)) {
            c.player.heal(total);
            c.particles(ParticleTypes.HEART, c.pos().add(0, 1.5, 0), 6, 0.4, 0.05);
            return Result.OK;
        }
        Running r = Casting.start(c, 40);
        r.tick(rr -> {
            if (rr.age % 5 != 0) return;
            float step = total / 8f;
            c.player.heal(step);
            c.level.sendParticles(Fx.dust(JADE, 1.1f), c.player.getX(), c.player.getY() + 1, c.player.getZ(), 4, 0.4, 0.6, 0.4, 0);
            for (Player p : allies) {
                if (!p.isAlive()) continue;
                p.heal(step * allyShare);
                if (rr.age == 0) cleanse(p);
                c.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 3, 0.3, 0.5, 0.3, 0);
            }
        });
        return Result.OK;
    }

    static void cleanse(LivingEntity e) {
        e.removeEffect(MobEffects.POISON);
        e.removeEffect(MobEffects.WITHER);
        e.removeEffect(MobEffects.WEAKNESS);
        e.removeEffect(MobEffects.BLINDNESS);
        e.removeEffect(MobEffects.SLOWNESS);
        e.removeEffect(dev.awaken.powers.content.PowerContent.STUNNED);
    }

    static Result thirdEye(Cast c) {
        int ticks = c.ticks(160);
        double range = c.is(1) ? 64 : 32;
        Moves.postOn(c.player, Moves.FX_THIRD_EYE);
        c.sound(PowerSounds.EYE, 1f, 1f);
        Running r = Casting.start(c, ticks);
        r.tick(rr -> {
            if (rr.age % 10 != 0) return;
            List<Integer> ids = new ArrayList<>();
            for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(c.pos(), range * 2, range, range * 2),
                    x -> x.isAlive() && x instanceof net.minecraft.world.entity.monster.Enemy && !Combat.isAlly(c.player, x))) {
                if (e.distanceToSqr(c.player) <= range * range) ids.add(e.getId());
            }
            if (!ids.isEmpty()) AwakenNet.send(c.player, new PowerPayloads.Marks(PowerPayloads.Marks.THIRD_EYE, ids, 14));
        }).end((rr, why) -> Moves.postOff(c.player, Moves.FX_THIRD_EYE));
        return Result.OK;
    }

    static Result cordLash(Cast c) {
        LivingEntity t = Combat.aimEntity(c, 14, 0.8);
        if (t == null) return Result.fail("no_target");
        c.hurt(t, c.dmg(), Forces.Force.SPIRIT);
        Fx.beam(c.player, c.player, t.getBoundingBox().getCenter(), 0xFFB8C4FF, 6, 0.05f, 14, false);
        c.sound(PowerSounds.LASH, 1.1f, 1f);
        if (c.is(1)) {
            Vec3 d = t.position().subtract(c.pos());
            Moves.launch(c.player, d.normalize().scale(Math.min(2.2, 0.6 + d.length() * 0.14)).add(0, 0.35, 0));
            Moves.noFall(c, 60);
            return Result.OK;
        }
        if (c.is(2)) {
            Combat.stun(t, 60);
            return Result.OK;
        }
        if (!Combat.isHeavy(t)) {
            Vec3 d = c.pos().subtract(t.position());
            Combat.push(t, d.normalize().scale(Math.min(1.8, 0.5 + d.length() * 0.14)).add(0, 0.3, 0));
        }
        Scheduler.after(8, () -> {
            if (t.isAlive()) Combat.stun(t, 20);
        });
        return Result.OK;
    }

    static Result bulwark(Cast c) {
        Vec3 centre = c.pos();
        double radius = c.radius(6);
        int ticks = c.ticks(160);
        c.sound(PowerSounds.WARD, 1.3f, 0.8f);
        Running r = Casting.start(c, ticks).at(centre);
        r.tick(rr -> {
            AABB box = AABB.ofSize(centre, radius * 2, radius * 2, radius * 2);
            for (Projectile p : c.level.getEntitiesOfClass(Projectile.class, box,
                    p -> p.position().distanceToSqr(centre) < radius * radius && !(p.getOwner() instanceof Player))) {
                c.level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY(), p.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
                p.discard();
            }
            if (rr.age % 5 == 0) {
                for (Player p : c.level.players()) {
                    if (p.position().distanceToSqr(centre) <= radius * radius) PowerState.of(p).buff(PowerState.Buffs.BULWARK, c.now() + 8, 0.5);
                }
                for (LivingEntity e : Combat.foesAround(c.player, centre.add(0, 1, 0), radius)) {
                    Vec3 out = Moves.flat(e.position().subtract(centre));
                    Combat.push(e, out.scale(0.6).add(0, 0.15, 0));
                }
            }
            if (rr.age % 10 == 0) Fx.sphere(c.level, centre.add(0, 0.5, 0), radius, Fx.dust(JADE, 1.0f), 60);
        });
        return Result.OK;
    }

    static Result hundredPalms(Cast c) {
        boolean bare = c.player.getMainHandItem().isEmpty();
        float mul = bare ? 1.3f : 1f;
        LivingEntity focus = c.is(2) ? Combat.aimEntity(c, 5, 0.8) : null;
        if (c.is(2) && focus == null) return Result.fail("no_target");
        double range = c.is(1) ? 6 : 4.5;
        double half = c.is(1) ? 60 : 30;
        c.sound(PowerSounds.SPIRIT, 1.2f, 1.4f);
        Running r = Casting.start(c, 40);
        r.tick(rr -> {
            if (rr.age % 2 != 0) return;
            boolean last = rr.age >= 38;
            List<LivingEntity> targets = c.is(2) ? (focus.isAlive() ? List.of(focus) : List.of())
                    : Combat.foesInCone(c.player, c.pos(), c.player.getLookAngle(), range, half);
            Moves.swing(c.player, rr.age % 4 == 0 ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND);
            for (LivingEntity e : targets) {
                float dmg = c.dmg() * mul * (last ? 3f : 1f);
                Combat.hurtFrom(c.player, null, e, dmg, Forces.Force.SPIRIT, true);
                Vec3 at = e.getBoundingBox().getCenter();
                c.level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 3, 0.3, 0.3, 0.3, 0.2);
                if (last) {
                    Combat.knockFrom(e, c.pos(), 1.8, 0.8);
                    c.level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
                } else {
                    Combat.knockFrom(e, c.pos(), 0.05, 0.05);
                }
            }
            Vec3 front = c.eye().add(c.player.getLookAngle().scale(1.4));
            c.level.sendParticles(ParticleTypes.SWEEP_ATTACK, front.x, front.y - 0.3, front.z, 1, 0.3, 0.3, 0.3, 0);
            if (rr.age % 8 == 0) c.sound(PowerSounds.PUNCH, 0.6f, 1.6f + c.player.getRandom().nextFloat() * 0.3f);
            if (last) {
                c.sound(PowerSounds.IMPACT, 1.3f, 0.8f);
                c.feel(targets.isEmpty() ? null : targets.get(0), 80, 4f);
            } else if (rr.age % 10 == 0 && !targets.isEmpty()) {
                c.feel(targets.get(0), 20, 0.8f);
            }
        });
        return Result.OK;
    }

    static Result voidMaw(Cast c) {
        Vec3 centre = FlightPowers.aimSpot(c, 20);
        double radius = c.radius(7);
        float executeAt = c.is(2) ? 0.25f : 0.15f;
        int ticks = c.ticks(80);
        boolean dark = c.player.position().distanceToSqr(centre) < 20 * 20;
        if (dark) Moves.postOn(c.player, Moves.FX_VOID);
        c.sound(centre, PowerSounds.VOID, 1.5f, 0.6f);
        Running r = Casting.start(c, ticks).at(centre);
        r.tick(rr -> {
            for (LivingEntity e : Combat.foesAround(c.player, centre, radius)) {
                Vec3 d = centre.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
                if (d.lengthSqr() > 0.01) Combat.push(e, e.getDeltaMovement().scale(0.5).add(d.normalize().scale(Combat.isHeavy(e) ? 0.03 : 0.13)));
                e.fallDistance = 0;
                if (rr.age % 10 == 0) {
                    c.hurt(e, c.dmg(), Forces.Force.GRAVITY);
                    if (!Combat.isBoss(e) && e.isAlive() && e.getHealth() < e.getMaxHealth() * executeAt && e.distanceToSqr(centre) < 9) {
                        Combat.hurtFrom(c.player, null, e, e.getMaxHealth() * 4, Forces.Force.SPIRIT, true);
                        c.player.heal(c.player.getMaxHealth() * 0.1f);
                        Casting.restore(c.player, 10);
                        c.level.sendParticles(ParticleTypes.SOUL, centre.x, centre.y, centre.z, 20, 0.4, 0.4, 0.4, 0.1);
                        c.sound(centre, PowerSounds.VOID, 1f, 1.4f);
                    }
                }
            }
            c.level.sendParticles(ParticleTypes.SQUID_INK, centre.x, centre.y, centre.z, 4, radius * 0.15, radius * 0.15, radius * 0.15, 0.02);
            c.level.sendParticles(ParticleTypes.REVERSE_PORTAL, centre.x, centre.y, centre.z, 6, radius * 0.4, radius * 0.4, radius * 0.4, 0.05);
            if (rr.age % 4 == 0) Fx.sphere(c.level, centre, 1.2, Fx.dust(0x231A3B, 2.0f), 24);
            if (rr.age % 20 == 0) c.sound(centre, PowerSounds.VOID, 0.8f, 0.5f);
        }).end((rr, why) -> {
            if (dark) Moves.postOff(c.player, Moves.FX_VOID);
        });
        return Result.OK;
    }

    static Result wakingDream(Cast c) {
        BlockHitResult hit = Combat.aimBlock(c, 5);
        Direction facing = Constructs.facing(c);
        // a wall in front at body height: a door through it
        if (hit != null && hit.getDirection().getAxis().isHorizontal() && Math.abs(c.xRot()) < 35) {
            BlockPos face = hit.getBlockPos();
            Direction into = hit.getDirection().getOpposite();
            int baseY = Constructs.feet(c.player).getY();
            List<BlockPos> opened = new ArrayList<>();
            for (int depth = 0; depth < 4; depth++) {
                BlockPos col = face.relative(into, depth);
                BlockPos lo = new BlockPos(col.getX(), baseY, col.getZ());
                boolean any = false;
                for (BlockPos p : new BlockPos[]{lo, lo.above()}) {
                    if (c.level.getBlockState(p).isAir()) continue;
                    if (TerrainLedger.lift(c.level, c.player, p, 240) != null) {
                        opened.add(p);
                        any = true;
                    } else {
                        return opened.isEmpty() ? Result.fail("blocked") : dreamDone(c, opened);
                    }
                }
                if (!any && depth > 0) break;
            }
            if (opened.isEmpty()) return Result.fail("blocked");
            return dreamDone(c, opened);
        }
        Set<BlockPos> slabs = new HashSet<>();
        List<BlockPos> shape;
        BlockPos feet = Constructs.feet(c.player);
        if (c.xRot() < -25) {
            shape = Constructs.ramp(feet, facing, 10, 1, slabs); // stairs up toward the ledge
        } else {
            shape = new ArrayList<>();
            for (int d = 1; d <= 10; d++) shape.add(feet.relative(facing, d).below()); // a bridge
        }
        int placed = Constructs.place(c.player, shape, slabs, true, 400);
        if (placed == 0) return Result.fail("blocked");
        for (BlockPos p : shape) {
            if (c.player.getRandom().nextInt(3) == 0)
                c.level.sendParticles(Fx.dust(0xD088C2, 1.0f), p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0);
        }
        c.sound(PowerSounds.DREAM, 1.2f, 1f);
        return Result.OK;
    }

    private static Result dreamDone(Cast c, List<BlockPos> opened) {
        for (BlockPos p : opened) {
            c.level.sendParticles(ParticleTypes.PORTAL, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 12, 0.4, 0.4, 0.4, 0.3);
            c.level.sendParticles(Fx.dust(0xD088C2, 1.2f), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 4, 0.4, 0.4, 0.4, 0);
        }
        c.sound(PowerSounds.DREAM, 1.2f, 0.8f);
        return Result.OK;
    }

    static Result stormCall(Cast c) {
        int max = c.is(1) ? 8 : 5;
        List<LivingEntity> foes = Combat.foesAround(c.player, c.pos(), 20);
        if (foes.isEmpty()) return Result.fail("no_target");
        foes.sort((a, b) -> Double.compare(a.distanceToSqr(c.player), b.distanceToSqr(c.player)));
        int n = 0;
        for (LivingEntity e : foes) {
            if (n >= max) break;
            int delay = n * 3;
            float mul = c.level.isRainingAt(e.blockPosition()) ? 1.5f : 1f;
            float dmg = c.dmg() * mul;
            Scheduler.after(1 + delay, () -> {
                if (!e.isAlive() || c.player.isRemoved()) return;
                Moves.visualLightning(c.level, e.position());
                Combat.hurtFrom(c.player, null, e, dmg, Forces.Force.CHARGE, true);
                Combat.stun(e, 30);
            });
            n++;
        }
        c.sound(PowerSounds.THUNDER, 1.4f, 0.8f);
        Fx.quake(c.level, c.pos(), 24, 3f);
        return Result.OK;
    }

    static Result emberStep(Cast c) {
        if (!c.predicted) Moves.launch(c.player, c.type.predictor.motion(c.player, c.variant, c.powerLevel));
        Moves.noFall(c, 30);
        c.sound(PowerSounds.EMBER, 1f, 1f);
        List<Vec3> trail = new ArrayList<>();
        float dmg = c.dmg();
        Running r = Casting.start(c, 60);
        r.tick(rr -> {
            if (rr.age < 10 && rr.age % 2 == 0) trail.add(c.pos());
            if (rr.age % 3 == 0) {
                for (Vec3 p : trail) c.level.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.1, p.z, 1, 0.25, 0.05, 0.25, 0.01);
            }
            if (rr.age % 5 == 0) {
                Set<Integer> hit = new HashSet<>();
                for (Vec3 p : trail) {
                    for (LivingEntity e : Combat.foesAround(c.player, p.add(0, 0.5, 0), 1.3)) {
                        if (hit.add(e.getId())) {
                            Combat.hurtFrom(c.player, null, e, dmg, Forces.Force.HEAT, true);
                            e.igniteForSeconds(3);
                        }
                    }
                }
            }
        });
        return Result.OK;
    }

    static Result frostNova(Cast c) {
        Vec3 at = c.pos().add(0, 1, 0);
        double radius = c.radius(6);
        long until = c.now() + 160;
        for (LivingEntity e : Combat.foesAround(c.player, at, radius)) {
            c.hurt(e, c.dmg(), Forces.Force.FROST);
            Moves.effect(e, MobEffects.SLOWNESS, 80, 2);
            e.setTicksFrozen(Math.max(e.getTicksFrozen(), 200));
            Forces.tag(e, Forces.Tag.FROSTED);
            Scheduler.after(160, () -> {
                if (e.isAlive() && c.level.getGameTime() >= until - 1) Forces.untag(e, Forces.Tag.FROSTED);
            });
        }
        Fx.sphere(c.level, at, radius * 0.7, ParticleTypes.SNOWFLAKE, 60);
        Fx.burstRing(c.level, c.pos().add(0, 0.2, 0), ParticleTypes.ITEM_SNOWBALL, 30, 0.5);
        c.sound(PowerSounds.FROST, 1.2f, 1f);
        Fx.quake(c.level, at, 12, 1.5f);
        return Result.OK;
    }

    static Result ghostStep(Cast c) {
        Vec3 from = c.pos();
        Vec3 dir = c.look();
        boolean mirror = false;
        BlockHitResult wall = Combat.aimBlock(c, 7);
        if (wall != null && c.level.getBlockState(wall.getBlockPos()).is(MIRROR)) mirror = true;
        Vec3 to = mirror ? Moves.phaseTarget(c.player, dir, 7, 10) : Moves.phaseTarget(c.player, dir, 7, 2);
        if (to.distanceToSqr(from) < 1) return Result.fail("blocked");
        if (!Constructs.mayChange(c.player, BlockPos.containing(to)) && !c.level.noCollision(c.player, c.player.getBoundingBox())) return Result.fail("blocked");
        for (LivingEntity e : Combat.alongLine(c.player, from.add(0, 1, 0), to.add(0, 1, 0), 0.8)) {
            c.hurt(e, c.dmg(), Forces.Force.SPIRIT);
            Moves.effect(e, MobEffects.SLOWNESS, 40, 1);
        }
        Moves.teleport(c.player, to);
        Fx.line(c.level, from.add(0, 1, 0), to.add(0, 1, 0), ParticleTypes.SOUL, 0.6);
        c.level.sendParticles(Fx.dust(SPIRIT, 1.0f), to.x, to.y + 1, to.z, 12, 0.3, 0.6, 0.3, 0);
        c.sound(from, PowerSounds.SPIRIT, 0.8f, 1.6f);
        if (mirror) {
            c.level.sendParticles(ParticleTypes.END_ROD, to.x, to.y + 1, to.z, 20, 0.4, 0.8, 0.4, 0.05);
            c.sound(to, PowerSounds.SHATTER, 0.6f, 1.6f);
            ForceInteractions.count(c.player, ForceInteractions.Reaction.MIRROR_SHATTER);
        }
        return Result.OK;
    }

    static Result twinStrike(Cast c) {
        int ticks = c.ticks(160);
        PowerState.of(c.player).buff(PowerState.Buffs.TWIN, c.now() + ticks, 0.6);
        c.sound(PowerSounds.SPIRIT, 1f, 0.6f);
        Running r = Casting.start(c, ticks);
        r.tick(rr -> {
            if (rr.age % 4 == 0) {
                Vec3 behind = c.pos().subtract(Moves.flat(c.player.getLookAngle()).scale(0.8));
                c.level.sendParticles(Fx.dust(SPIRIT, 1.0f), behind.x, behind.y + 1, behind.z, 3, 0.2, 0.5, 0.2, 0);
            }
        }).end((rr, why) -> PowerState.of(c.player).clearBuff(PowerState.Buffs.TWIN));
        return Result.OK;
    }

    /** The twin soul repeats a melee blow 10 ticks later (called by the damage hook). */
    public static void twinEcho(ServerPlayer player, LivingEntity victim, float amount, double share) {
        Scheduler.after(10, () -> {
            if (!victim.isAlive() || player.isRemoved()) return;
            Combat.hurtFrom(player, null, victim, (float) (amount * share), Forces.Force.SPIRIT, true);
            Vec3 at = victim.getBoundingBox().getCenter();
            player.level().sendParticles(Fx.dust(SPIRIT, 1.3f), at.x, at.y, at.z, 10, 0.3, 0.4, 0.3, 0);
            player.level().sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        });
    }

    /** Is this damage a plain melee hit by the player (what Twin Strike repeats)? */
    public static boolean isMelee(DamageSource src, ServerPlayer player) {
        return src.getDirectEntity() == player && src.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK);
    }
}
