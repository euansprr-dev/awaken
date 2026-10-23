package dev.awaken.powers.catalog;

import dev.awaken.powers.Cast;
import dev.awaken.powers.Casting;
import dev.awaken.powers.Combat;
import dev.awaken.powers.Constructs;
import dev.awaken.powers.FlyingBlocks;
import dev.awaken.powers.Fx;
import dev.awaken.powers.Moves;
import dev.awaken.powers.PowerSounds;
import dev.awaken.powers.PowerState;
import dev.awaken.powers.PowerType;
import dev.awaken.powers.PowerType.Family;
import dev.awaken.powers.PowerType.Result;
import dev.awaken.powers.PowerType.Slot;
import dev.awaken.powers.PowerType.Variant;
import dev.awaken.powers.Running;
import dev.awaken.powers.content.ConstructBlock;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Architect: build-fights in a block game. One keypress, one shape of hard light. Scales with INT. */
final class ArchitectPowers {
    private ArchitectPowers() {}

    static final int SAND = 0xD9BA80;
    static final int LIFE = ConstructBlock.LIFETIME;

    static void register() {
        PowerType.builder("wall", "Wall", Family.ARCHITECT, Slot.ACTIVE)
                .cost(12).cooldown(40).unlock(Rank.E).icon("wall")
                .desc("A 5x3 wall of hard light two blocks ahead. Constructs fade after 30 s and never replace anything.")
                .variant(Variant.of("Wide", "7 blocks wide"))
                .variant(Variant.of("Tall", "3 wide, 5 high"))
                .cast(c -> {
                    int w = c.is(1) ? 7 : c.is(2) ? 3 : 5;
                    int h = c.is(2) ? 5 : 3;
                    return build(c, Constructs.wall(Constructs.feet(c.player), Constructs.facing(c), 2, w, h), Set.of());
                }).register();

        PowerType.builder("ramp", "Ramp", Family.ARCHITECT, Slot.ACTIVE)
                .cost(12).cooldown(40).unlock(Rank.E).icon("ramp")
                .desc("A walkable ramp rising ahead of you, 3 wide and 4 high: run straight up it.")
                .variant(Variant.of("Wide", "5 wide"))
                .variant(Variant.of("Long", "Twice as long and twice as high"))
                .cast(c -> {
                    Set<BlockPos> slabs = new HashSet<>();
                    List<BlockPos> shape = Constructs.ramp(Constructs.feet(c.player), Constructs.facing(c), c.is(2) ? 16 : 8, c.is(1) ? 5 : 3, slabs);
                    return build(c, shape, slabs);
                }).register();

        PowerType.builder("floor", "Floor", Family.ARCHITECT, Slot.ACTIVE)
                .cost(12).cooldown(40).unlock(Rank.E).icon("floor")
                .desc("A 5x5 platform: under your feet if you're falling, otherwise over the ground ahead. Bridges any gap.")
                .variant(Variant.of("Wide", "7x7"))
                .variant(Variant.of("Quick", "3x3, ready again almost at once").cd(0.4f).cost(0.6f))
                .cast(c -> {
                    int size = c.is(1) ? 7 : c.is(2) ? 3 : 5;
                    BlockPos feet = Constructs.feet(c.player);
                    BlockPos centre = Combat.airborne(c.player) ? feet.below() : feet.relative(Constructs.facing(c), size / 2 + 2).below();
                    return build(c, Constructs.floor(centre, size), Set.of());
                }).register();

        PowerType.builder("cone", "Cone", Family.ARCHITECT, Slot.ACTIVE)
                .cost(14).cooldown(40).unlock(Rank.D).icon("cone")
                .desc("A stepped roof over your head: cover from arrows and anything falling on you.")
                .variant(Variant.of("Wide", "7 across"))
                .variant(Variant.of("Low", "Built right on top of you, tighter"))
                .cast(c -> {
                    int size = c.is(1) ? 7 : 5;
                    BlockPos base = Constructs.feet(c.player).above(c.is(2) ? 2 : 3);
                    return build(c, Constructs.cone(base, size), Set.of());
                }).register();

        PowerType.builder("rampart", "Rampart", Family.ARCHITECT, Slot.ACTIVE)
                .cost(30).cooldown(300).unlock(Rank.C).icon("rampart")
                .desc("Raise a square fort around you, three high with battlements and a door facing the way you look.")
                .variant(Variant.of("Keep", "Bigger and taller"))
                .variant(Variant.of("Bastion", "Tight and tall"))
                .cast(c -> {
                    int r = c.is(1) ? 4 : c.is(2) ? 2 : 3;
                    int h = c.is(0) ? 3 : 4;
                    return build(c, Constructs.rampart(Constructs.feet(c.player), Constructs.facing(c), r, h), Set.of());
                }).register();

        PowerType.builder("spire", "Spire", Family.ARCHITECT, Slot.ACTIVE)
                .cost(22).cooldown(160).damage(6).unlock(Rank.C).icon("spire")
                .desc("A pillar erupts under your feet and throws you skyward, and everything standing next to you with it.")
                .variant(Variant.of("Tower", "A taller pillar, a bigger launch"))
                .variant(Variant.of("Spikes", "No launch for you: three spikes erupt under enemies around you").dmg(1.4f))
                .cast(ArchitectPowers::spire).register();

        PowerType.builder("collapse", "Collapse", Family.ARCHITECT, Slot.ACTIVE)
                .cost(25).cooldown(200).damage(4).unlock(Rank.B).icon("collapse")
                .desc("Bring your constructs down on your enemies: every block of yours nearby flies at the closest foe.")
                .variant(Variant.of("Avalanche", "Pulls in constructs from twice as far").radius(2f))
                .variant(Variant.of("Crush", "Fewer blocks, each one harder and stunning").dmg(1.6f))
                .cast(ArchitectPowers::collapse).register();

        PowerType.builder("citadel", "Citadel", Family.ARCHITECT, Slot.ULTIMATE)
                .cost(50).cooldown(1200).unlock(Rank.C).hidden().icon("citadel")
                .desc("Architect's signature. A dome of hard light rises around you. Inside it you regenerate and take less damage.")
                .variant(Variant.of("Bastion", "A wider dome").radius(1.35f))
                .variant(Variant.of("Sanctum", "A tight dome with stronger healing").radius(0.8f))
                .cast(ArchitectPowers::citadel).register();
    }

    // ---- behaviours -------------------------------------------------------------------------------------------------

    static Result build(Cast c, List<BlockPos> shape, Set<BlockPos> slabs) {
        int placed = Constructs.place(c.player, shape, slabs, false, LIFE);
        if (placed == 0) return Result.fail("blocked");
        c.sound(PowerSounds.BUILD, 1f, 0.9f + c.player.getRandom().nextFloat() * 0.3f);
        return Result.OK;
    }

    static Result spire(Cast c) {
        BlockPos feet = Constructs.feet(c.player);
        if (c.is(2)) {
            List<LivingEntity> foes = Combat.foesAround(c.player, c.pos(), 8);
            if (foes.isEmpty()) return Result.fail("no_target");
            int n = 0;
            for (LivingEntity e : foes) {
                if (n++ >= 3) break;
                BlockPos base = e.blockPosition();
                c.hurt(e, c.dmg(), null);
                Combat.knockFrom(e, e.position(), 0, 1.1);
                List<BlockPos> spike = new ArrayList<>();
                for (int h = 0; h < 3; h++) spike.add(base.above(h));
                Constructs.place(c.player, spike, Set.of(), false, 200);
                c.level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY(), e.getZ(), 10, 0.3, 0.5, 0.3, 0.3);
            }
            c.sound(PowerSounds.BUILD, 1.2f, 0.6f);
            return Result.OK;
        }
        int height = c.is(1) ? 9 : 6;
        Moves.launch(c.player, new Vec3(c.player.getDeltaMovement().x, c.is(1) ? 2.0 : 1.6, c.player.getDeltaMovement().z));
        Moves.noFall(c, 160);
        for (LivingEntity e : Combat.foesAround(c.player, c.pos().add(0, 0.5, 0), 2.8)) {
            c.hurt(e, c.dmg(), null);
            Combat.knockFrom(e, c.pos(), 0.5, 1.0);
        }
        c.sound(PowerSounds.BUILD, 1.3f, 0.5f);
        c.sound(PowerSounds.WHOOSH, 1f, 0.8f);
        Running r = Casting.start(c, 40);
        r.tick(rr -> {
            // the pillar grows under the player as they rise, one block at a time
            while (rr.count < height && c.player.getY() >= feet.getY() + rr.count + 1.0) {
                BlockPos p = feet.above(rr.count++);
                Constructs.place(c.player, List.of(p), Set.of(), false, LIFE);
                c.level.sendParticles(ParticleTypes.END_ROD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.02);
            }
            if (rr.count >= height) rr.stop();
        });
        return Result.OK;
    }

    static Result collapse(Cast c) {
        double radius = c.radius(20);
        List<BlockPos> mine = Constructs.ownedNear(c.player, c.pos(), radius);
        if (mine.isEmpty()) return Result.fail("no_constructs");
        List<LivingEntity> foes = Combat.foesAround(c.player, c.pos(), radius + 6);
        if (foes.isEmpty()) return Result.fail("no_target");
        int cap = c.is(2) ? 12 : 30;
        int thrown = 0;
        float dmg = c.dmg();
        for (BlockPos p : mine) {
            if (thrown >= cap) break;
            Vec3 from = Vec3.atCenterOf(p);
            LivingEntity target = null;
            double best = 16 * 16;
            for (LivingEntity f : foes) {
                double d = f.distanceToSqr(from);
                if (d < best) {
                    best = d;
                    target = f;
                }
            }
            if (target == null) continue;
            BlockState s = Constructs.lift(c.level, p);
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
            Vec3 v = to.subtract(from).normalize().scale(1.1).add(0, 0.25, 0);
            var f = FlyingBlocks.launch(c.level, c.player, s, from, v, dmg, null, 1.2f, 1.0f);
            if (f != null) {
                f.gravity(0.03);
                if (c.is(2)) f.stun = 30;
            }
            thrown++;
        }
        if (thrown == 0) return Result.fail("no_target");
        c.sound(PowerSounds.COLLAPSE, 1.3f, 0.8f);
        Fx.quake(c.level, c.pos(), 16, 2.5f);
        return Result.OK;
    }

    static Result citadel(Cast c) {
        int radius = (int) Math.round(6 * c.v.radius());
        BlockPos centre = Constructs.feet(c.player);
        List<BlockPos> dome = Constructs.dome(centre, radius);
        int rows = radius + 1;
        c.sound(PowerSounds.BUILD, 1.5f, 0.5f);
        Vec3 middle = Vec3.atBottomCenterOf(centre);
        PowerState st = PowerState.of(c.player);
        Running r = Casting.start(c, LIFE);
        r.tick(rr -> {
            if (rr.age < rows) {
                int y = centre.getY() + rr.age;
                List<BlockPos> ring = new ArrayList<>();
                for (BlockPos p : dome) if (p.getY() == y) ring.add(p);
                Constructs.place(c.player, ring, Set.of(), false, LIFE - rr.age);
                c.sound(middle, PowerSounds.BUILD, 0.6f, 0.6f + rr.age * 0.1f);
            }
            if (rr.age % 20 == 0) {
                double r2 = radius * radius;
                for (Player p : c.level.players()) {
                    if (p.position().distanceToSqr(middle) > r2) continue;
                    Moves.effect(p, MobEffects.REGENERATION, 50, c.is(2) ? 1 : 0);
                    PowerState.of(p).buff(PowerState.Buffs.BULWARK, c.now() + 25, 0.2);
                }
                if (st != null) c.level.sendParticles(Fx.dust(SAND, 1.0f), middle.x, middle.y + 1, middle.z, 6, radius * 0.4, 1, radius * 0.4, 0);
            }
        });
        return Result.OK;
    }
}
