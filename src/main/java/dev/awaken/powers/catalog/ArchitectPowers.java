package dev.awaken.powers.catalog;

import dev.awaken.powers.fx.CastAnim;
import dev.awaken.powers.fx.FxEvent;
import dev.awaken.powers.fx.FxKind;

import dev.awaken.powers.Bolts;
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
import dev.awaken.powers.ladder.BuildZones;
import dev.awaken.powers.ladder.Riders;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
                    List<BlockPos> shape = Constructs.wall(Constructs.feet(c.player), Constructs.facing(c), 2, w, h);
                    Result r = build(c, shape, Set.of());
                    // Ricochet (3), Crystal (4), Mirror Wall (5): it answers shots and blasts for its life (StepHooks)
                    if (r.ok() && c.step(3)) BuildZones.add(c, shape, life(c));
                    return r;
                }).register();

        PowerType.builder("ramp", "Ramp", Family.ARCHITECT, Slot.ACTIVE)
                .cost(12).cooldown(40).unlock(Rank.E).icon("ramp")
                .desc("A walkable ramp rising ahead of you, 3 wide and 4 high: run straight up it.")
                .variant(Variant.of("Wide", "5 wide"))
                .variant(Variant.of("Long", "Twice as long and twice as high"))
                .cast(c -> {
                    Set<BlockPos> slabs = new HashSet<>();
                    int length = c.is(2) ? 16 : 8;
                    List<BlockPos> shape = Constructs.ramp(Constructs.feet(c.player), Constructs.facing(c), length, c.is(1) ? 5 : 3, slabs);
                    Result r = build(c, shape, slabs);
                    if (r.ok() && c.step(3)) rampSteps(c, shape, Constructs.feet(c.player), Constructs.facing(c), length);
                    return r;
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
                    List<BlockPos> shape = Constructs.floor(centre, size);
                    Result r = build(c, shape, Set.of());
                    if (r.ok() && c.step(3)) floorSteps(c, shape, centre.getY());
                    return r;
                }).register();

        PowerType.builder("cone", "Cone", Family.ARCHITECT, Slot.ACTIVE)
                .cost(14).cooldown(40).unlock(Rank.D).icon("cone")
                .desc("A stepped roof over your head: cover from arrows and anything falling on you.")
                .variant(Variant.of("Wide", "7 across"))
                .variant(Variant.of("Low", "Built right on top of you, tighter"))
                .cast(c -> {
                    int size = c.is(1) ? 7 : 5;
                    BlockPos base = Constructs.feet(c.player).above(c.is(2) ? 2 : 3);
                    List<BlockPos> shape = Constructs.cone(base, size);
                    Result r = build(c, shape, Set.of());
                    if (r.ok() && c.step(3)) coneSteps(c, shape, base, size);
                    return r;
                }).register();

        PowerType.builder("rampart", "Rampart", Family.ARCHITECT, Slot.ACTIVE)
                .cost(30).cooldown(300).unlock(Rank.C).icon("rampart")
                .desc("Raise a square fort around you, three high with battlements and a door facing the way you look.")
                .variant(Variant.of("Keep", "Bigger and taller"))
                .variant(Variant.of("Bastion", "Tight and tall"))
                .cast(c -> {
                    int r = c.is(1) ? 4 : c.is(2) ? 2 : 3;
                    int h = c.is(0) ? 3 : 4;
                    BlockPos centre = Constructs.feet(c.player);
                    Result res = build(c, Constructs.rampart(centre, Constructs.facing(c), r, h), Set.of());
                    if (res.ok() && c.step(3)) rampartSteps(c, centre, Constructs.facing(c), r, h);
                    return res;
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
        int placed = Constructs.place(c.player, shape, slabs, false, life(c));
        if (placed == 0) return Result.fail("blocked");
        c.sound(PowerSounds.BUILD, 1f, 0.9f + c.player.getRandom().nextFloat() * 0.3f);
        assemble(c, shape);
        return Result.OK;
    }

    /** How long this cast's constructs stand: Blueprint (2) adds 5 s; Crystal and Hold (4) double it. */
    static int life(Cast c) {
        float life = LIFE + c.num(2, "longer", 0f);
        return Math.round(life * c.num(4, "tough", 1f) * c.num(4, "hold", 1f));
    }

    /** The blocks of a construct assemble with glowing seams, sweeping out from the caster over a blueprint circle. */
    static void assemble(Cast c, List<BlockPos> shape) {
        if (shape.isEmpty()) return;
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (BlockPos p : shape) {
            x0 = Math.min(x0, p.getX());
            y0 = Math.min(y0, p.getY());
            z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX());
            y1 = Math.max(y1, p.getY());
            z1 = Math.max(z1, p.getZ());
        }
        Fx.at(c, FxKind.CONSTRUCT, new Vec3(x0, y0, z0)).dir(new Vec3(x1 - x0 + 0.99, y1 - y0 + 0.99, z1 - z0 + 0.99)).send();
    }

    // ---- the ladder's constructs (SPEC 34): what each one does after it stands ----------------------------------------

    /** Players (you and your friends) whose feet stand on one of these blocks. */
    private static List<Player> standingOn(Cast c, Set<BlockPos> blocks, AABB box) {
        List<Player> out = new ArrayList<>();
        for (Player p : c.level.players()) {
            if (!p.isAlive() || p.isSpectator() || !box.contains(p.position())) continue;
            BlockPos under = BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ());
            if (blocks.contains(under) && Constructs.isConstruct(c.level, under)) out.add(p);
        }
        return out;
    }

    private static AABB around(Collection<BlockPos> shape, double grow) {
        AABB box = null;
        for (BlockPos p : shape) box = box == null ? new AABB(p) : box.minmax(new AABB(p));
        return box == null ? new AABB(BlockPos.ZERO) : box.inflate(grow);
    }

    /** Rush (3), Rails (4), Launch Ramp (5): the ramp works on whoever is on it for its life. */
    static void rampSteps(Cast c, List<BlockPos> shape, BlockPos feet, Direction facing, int length) {
        Set<BlockPos> blocks = new HashSet<>(shape);
        AABB box = around(shape, 1.5);
        Vec3 fwd = facing.getUnitVec3();
        Vec3 side = facing.getClockWise().getUnitVec3();
        Vec3 axis = Vec3.atBottomCenterOf(feet);
        Map<UUID, Long> rushed = new HashMap<>(), launched = new HashMap<>();
        Running r = Casting.start(c, c.type.id + ":ramp", life(c));
        r.tick(rr -> {
            if (rr.age % 2 != 0) return;
            long now = c.now();
            for (Player p : standingOn(c, blocks, box)) {
                double along = p.position().subtract(axis).dot(fwd);
                boolean upward = p.getLookAngle().dot(fwd) > 0.3;
                // Rush: running up it, a burst of speed (and the chevrons flare once as you step on)
                if (upward) {
                    Moves.effect(p, MobEffects.SPEED, (int) c.num(3, "rush", 40), 1);
                    Long last = rushed.put(p.getUUID(), now);
                    if (last == null || now - last > 30) Fx.at(c, FxKind.TRAIL, p.position()).dir(fwd).ticks(10).plain().send();
                }
                // Launch Ramp: the top end throws you on in a long arc
                if (c.step(5) && upward && along >= length - 1.5) {
                    Long last = launched.get(p.getUUID());
                    if (last != null && now - last < 30) continue;
                    launched.put(p.getUUID(), now);
                    float k = c.num(5, "launch", 1.4f);
                    Combat.push(p, fwd.scale(k).add(0, 0.85, 0));
                    PowerState.of(p).buff(PowerState.Buffs.NO_FALL, now + 100, 1);
                    p.resetFallDistance();
                    Fx.at(c, FxKind.RING, p.position()).dir(fwd).scale(1.6).ticks(10).plain().send();
                    c.sound(p.position(), PowerSounds.WHOOSH, 1f, 1.2f);
                }
            }
            // Rails: a foe that sets foot on it is thrown off the side
            if (c.step(4) && rr.age % 4 == 0) {
                for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, box, e -> Combat.isFoe(c.player, e))) {
                    BlockPos under = BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ());
                    if (!blocks.contains(under) || !Constructs.isConstruct(c.level, under)) continue;
                    double off = e.position().subtract(axis).dot(side);
                    double sign = Math.abs(off) < 0.2 ? (e.getRandom().nextBoolean() ? 1 : -1) : Math.signum(off);
                    Combat.push(e, side.scale(sign * c.num(4, "rails", 0.6f) * 1.4).add(0, 0.3, 0));
                    Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).dir(side.scale(sign)).scale(0.7).plain().send();
                }
            }
        });
    }

    /** Spring (3, its landings in StepHooks), Hold (4, in life()), Sky Bridge (5): the floor keeps building ahead of you. */
    static void floorSteps(Cast c, List<BlockPos> shape, int y) {
        int life = life(c);
        BuildZones.Zone zone = BuildZones.add(c, shape, life);
        if (!c.step(5)) return;
        int bridge = (int) c.num(5, "bridge", 120);
        double[] last = {c.player.getX(), c.player.getZ()};
        Running r = Casting.start(c, c.type.id + ":bridge", bridge);
        r.tick(rr -> {
            if (rr.age % 2 != 0) return;
            ServerPlayer p = c.player;
            double dx = p.getX() - last[0], dz = p.getZ() - last[1];
            last[0] = p.getX();
            last[1] = p.getZ();
            // only while you walk the floor (or what it has built so far), not once you have left it
            if (p.getY() < y + 0.5 || p.getY() > y + 4 || dx * dx + dz * dz < 0.01) return;
            Vec3 dir = new Vec3(dx, 0, dz).normalize();
            Vec3 across = new Vec3(-dir.z, 0, dir.x);
            List<BlockPos> row = new ArrayList<>();
            for (double ahead = 2; ahead <= 3; ahead += 1) {
                for (int w = -1; w <= 1; w++) {
                    Vec3 at = p.position().add(dir.scale(ahead)).add(across.scale(w));
                    row.add(BlockPos.containing(at.x, y + 0.5, at.z));
                }
            }
            row.removeIf(pos -> !c.level.getBlockState(pos).isAir());
            if (row.isEmpty()) return;
            if (Constructs.place(c.player, row, Set.of(), false, Math.max(20, life - rr.age)) == 0) return;
            for (BlockPos pos : row) if (Constructs.isConstruct(c.level, pos)) zone.blocks.add(pos.immutable());
            if (rr.age % 6 == 0) {
                assemble(c, row);
                c.sound(p.position(), PowerSounds.BUILD, 0.5f, 1.3f + c.player.getRandom().nextFloat() * 0.3f);
            }
        });
    }

    /** Lantern (3): you heal slowly under it; Shatterproof (4, StepHooks): shots can't reach you; Pagoda (5): faster, and foes are pushed out. */
    static void coneSteps(Cast c, List<BlockPos> shape, BlockPos base, int size) {
        int life = life(c);
        BuildZones.Zone zone = BuildZones.add(c, shape, life);
        Vec3 middle = Vec3.atBottomCenterOf(base);
        float heal = c.num(3, "heal", 0.5f) * (c.step(5) ? 2 : 1);
        Running r = Casting.start(c, c.type.id + ":roof", life);
        r.tick(rr -> {
            if (rr.age % 10 != 0 || !Constructs.isConstruct(c.level, base.offset(0, size / 2, 0))) return;
            if (rr.age % 20 == 0) {
                for (Player p : c.level.players()) {
                    if (p.isAlive() && !p.isSpectator() && zone.shelters(p.position())) p.heal(heal);
                }
                // light drips from the roof
                if (rr.age % 40 == 0) Fx.at(c, FxKind.HEAL, middle.subtract(0, 1.5, 0)).ticks(30).plain().flag(FxEvent.QUIET).send();
            }
            if (c.step(5)) {
                for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, zone.box.inflate(0, 4, 0), e -> Combat.isFoe(c.player, e))) {
                    if (!zone.shelters(e.position())) continue;
                    Vec3 out = e.position().subtract(middle).multiply(1, 0, 1);
                    if (out.lengthSqr() < 1e-3) out = new Vec3(1, 0, 0);
                    Combat.push(e, out.normalize().scale(0.7).add(0, 0.25, 0));
                    Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).dir(out).scale(0.6).plain().send();
                }
            }
        });
    }

    /** Arrow Slits (3): the corners fire bolts; Gatehouse (4): a door that opens for friends; Keep (5): a beacon tower that slows foes. */
    static void rampartSteps(Cast c, BlockPos centre, Direction facing, int radius, int height) {
        int life = life(c);
        int every = Math.max(10, (int) c.num(3, "bolts", 40));
        float boltDmg = c.dmg(c.num(3, "bolt", 4f));
        List<Vec3> slits = new ArrayList<>();
        // over each corner's battlement
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) slits.add(Vec3.atCenterOf(centre.offset(sx * radius, height + 1, sz * radius)));
        BlockPos doorAt = centre.relative(facing, radius);
        List<BlockPos> door = List.of(doorAt, doorAt.above());
        if (c.step(4)) Constructs.place(c.player, door, life);
        BlockPos corner = centre.relative(facing.getOpposite(), radius).relative(facing.getClockWise(), radius);
        Vec3 beacon = Vec3.atCenterOf(corner.above(height + 3));
        if (c.step(5)) {
            List<BlockPos> tower = new ArrayList<>();
            for (int h = height; h < height + 3; h++) tower.add(corner.above(h));
            Constructs.place(c.player, tower, life);
            assemble(c, tower);
        }
        Vec3 inside = Vec3.atBottomCenterOf(centre);
        Running r = Casting.start(c, c.type.id + ":keep", life);
        r.tick(rr -> {
            // a fort whose walls are gone stops working
            if (rr.age % 20 == 0 && !Constructs.isConstruct(c.level, corner)) {
                rr.stop();
                return;
            }
            if (rr.age > 0 && rr.age % every == 0) {
                int fired = 0;
                for (LivingEntity e : Combat.foesAround(c.player, inside, 18)) {
                    if (fired >= 2) break;
                    Vec3 aim = e.getBoundingBox().getCenter();
                    Vec3 from = null;
                    for (Vec3 s : slits) {
                        // the Keep's tower stands over one corner: that slit is inside it
                        if (!c.level.getBlockState(BlockPos.containing(s)).isAir()) continue;
                        if (from == null || s.distanceToSqr(aim) < from.distanceToSqr(aim)) from = s;
                    }
                    if (from == null || !clear(c, from, aim)) continue;
                    var b = Bolts.fire(c.level, c.player, null, from, aim.subtract(from), 2.6f, boltDmg, c.force(), BOLT, 20, 0f);
                    b.cast = c;
                    fired++;
                }
                if (fired > 0) c.sound(inside, PowerSounds.SHOT, 0.8f, 1.3f);
            }
            // Gatehouse: friends near the door open it; it closes behind them
            if (c.step(4) && rr.age % 2 == 0) {
                boolean friend = false;
                for (Player p : c.level.players()) {
                    if (p.isAlive() && !p.isSpectator() && p.position().distanceToSqr(Vec3.atBottomCenterOf(doorAt)) < 2.6 * 2.6) friend = true;
                }
                boolean shut = Constructs.isConstruct(c.level, doorAt);
                if (friend && shut) {
                    for (BlockPos d : door) if (Constructs.isConstruct(c.level, d)) Constructs.lift(c.level, d);
                    c.sound(Vec3.atCenterOf(doorAt), PowerSounds.CONSTRUCT_FADE, 0.7f, 1.4f);
                } else if (!friend && !shut && Constructs.place(c.player, door, Math.max(20, life - rr.age)) > 0) {
                    c.sound(Vec3.atCenterOf(doorAt), PowerSounds.BUILD, 0.5f, 1.5f);
                }
            }
            // Keep: the beacon slows every foe near it
            if (c.step(5) && rr.age % 20 == 0) {
                for (LivingEntity e : Combat.foesAround(c.player, beacon, 10)) Moves.effect(e, MobEffects.SLOWNESS, 30, 1);
                if (rr.age % 40 == 0) Fx.at(c, FxKind.RING, beacon).scale(10).ticks(16).plain().flag(FxEvent.QUIET).send();
            }
        });
    }

    private static boolean clear(Cast c, Vec3 from, Vec3 to) {
        return c.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty())).getType()
                == HitResult.Type.MISS;
    }

    /** Hard light's own gold, for the bolts a construct fires. */
    static final int BOLT = 0xFFFFD28A;

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
                var spikeFx = Fx.at(c, FxKind.PILLAR, Vec3.atBottomCenterOf(base)).scale(3);
                if (n > 1) spikeFx.noAnim();
                spikeFx.send();
                assemble(c, spike);
                Fx.at(c, FxKind.IMPACT, e.getBoundingBox().getCenter()).dir(new Vec3(0, 1, 0)).scale(1.2).noAnim().send();
            }
            c.sound(PowerSounds.BUILD, 1.2f, 0.6f);
            return Result.OK;
        }
        // Tall (4): a taller pillar; Seams (2) and Tall throw you higher
        int height = (int) Math.round((c.is(1) ? 9 : 6) * (1 + c.num(4, "tall", 0f)));
        double lift = (c.is(1) ? 2.0 : 1.6) * (1 + c.num(2, "lift", 0f)) * (c.step(4) ? 1.1 : 1.0);
        Moves.launch(c.player, new Vec3(c.player.getDeltaMovement().x, lift, c.player.getDeltaMovement().z));
        Moves.noFall(c, 160);
        double throwRadius = 2.8;
        Fx.at(c, FxKind.RING, c.pos()).scale(throwRadius).ticks(9).flag(FxEvent.REACH).noAnim().send();
        List<LivingEntity> thrown = new ArrayList<>();
        for (LivingEntity e : Combat.foesAround(c.player, c.pos().add(0, 0.5, 0), throwRadius)) {
            c.hurt(e, c.dmg(), null);
            Combat.knockFrom(e, c.pos(), 0.5, 1.0);
            thrown.add(e);
        }
        if (c.step(3) && !thrown.isEmpty()) spikedRim(c, thrown);
        c.sound(PowerSounds.BUILD, 1.3f, 0.5f);
        c.sound(PowerSounds.WHOOSH, 1f, 0.8f);
        // the pillar erupts: a column of light, ground thrown out, then each block assembles as it rises
        Fx.at(c, FxKind.PILLAR, c.pos()).scale(height).send();
        List<BlockPos> column = new ArrayList<>();
        for (int h = 0; h < height; h++) column.add(feet.above(h));
        // the stair's steps arrive as the pillar grows: one seam sweep over the whole tower catches them
        assemble(c, c.step(5) ? List.of(feet.offset(-1, 0, -1), feet.offset(1, height - 1, 1)) : column);
        Fx.at(c, FxKind.TRAIL, c.pos()).aux(0).ticks(30).noAnim().send();
        Running r = Casting.start(c, 40);
        r.tick(rr -> {
            // the pillar grows under the player as they rise, one block at a time
            while (rr.count < height && c.player.getY() >= feet.getY() + rr.count + 1.0) {
                BlockPos p = feet.above(rr.count++);
                Constructs.place(c.player, List.of(p), Set.of(), false, LIFE);
                // Spiral Tower (5): two half-steps wind up round it for every block it grows
                if (c.step(5)) stair(c, feet, 2 * rr.count - 2, 2 * rr.count);
            }
            if (rr.count >= height) rr.stop();
        });
        return Result.OK;
    }

    /** The ring round a column, in walking order: each cell touches the next by a side. */
    private static final int[][] RING = {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

    /** Spiral Tower's steps {@code from} (inclusive) to {@code to} (exclusive): step i tops out (i + 1) half-blocks up, a lap is four blocks. */
    private static void stair(Cast c, BlockPos feet, int from, int to) {
        List<BlockPos> steps = new ArrayList<>();
        Set<BlockPos> slabs = new HashSet<>();
        for (int i = Math.max(0, from); i < to; i++) {
            int halves = i + 1;
            int[] o = RING[i % RING.length];
            BlockPos col = feet.offset(o[0], 0, o[1]);
            BlockPos at = halves % 2 == 1 ? col.above(halves / 2) : col.above(halves / 2 - 1);
            steps.add(at);
            if (halves % 2 == 1) slabs.add(at);
        }
        if (!steps.isEmpty()) Constructs.place(c.player, steps, slabs, false, LIFE);
    }

    /** Spiked Rim (3): each foe the pillar threw takes a hit when it comes down. */
    private static void spikedRim(Cast c, List<LivingEntity> thrown) {
        float dmg = c.dmg() * c.num(3, "spikes", 0.5f);
        Running r = Casting.start(c, c.type.id + ":rim", 80);
        r.tick(rr -> {
            if (rr.age < 6) return;
            thrown.removeIf(e -> {
                if (!e.isAlive()) return true;
                if (!e.onGround()) return false;
                c.hurt(e, dmg, null);
                Fx.at(c, FxKind.IMPACT, e.position().add(0, 0.3, 0)).dir(new Vec3(0, 1, 0)).scale(0.9).plain().send();
                return true;
            });
            if (thrown.isEmpty()) rr.stop();
        });
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
        // Seek (3): the blocks share themselves out, nearest foe first, instead of all flying at the closest
        List<LivingEntity> order = new ArrayList<>(foes);
        order.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(c.player)));
        for (BlockPos p : mine) {
            if (thrown >= cap) break;
            Vec3 from = Vec3.atCenterOf(p);
            LivingEntity target = null;
            if (c.step(3)) {
                LivingEntity pick = order.get(thrown % order.size());
                if (pick.distanceToSqr(from) < 24 * 24) target = pick;
            }
            double best = 16 * 16;
            if (target == null) for (LivingEntity f : foes) {
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
                // Shatter (4) and Avalanche of Light (5) ride each block's hits
                if (c.kit.onHit.length > 0) f.onHit = (e, amount) -> Riders.onHit(c, e, amount, null);
            }
            // every block tears loose with a flash and streaks at its target (Avalanche of Light: more of them, as comets)
            if (thrown < (c.step(5) ? 16 : 10)) Fx.trailOn(c, f, 30);
            if (thrown < 6) Fx.at(c, FxKind.IMPACT, from).scale(0.6).noAnim().send();
            thrown++;
        }
        if (thrown == 0) return Result.fail("no_target");
        Fx.at(c, FxKind.RING, c.pos()).scale(Math.min(10, radius * 0.5)).ticks(12).anim(CastAnim.THROW).send();
        c.sound(PowerSounds.COLLAPSE, 1.3f, 0.8f);
        Fx.quake(c.level, c.pos(), 16, 2.5f);
        return Result.OK;
    }

    static Result citadel(Cast c) {
        int life = c.ticks(LIFE);
        int radius = (int) Math.round(6 * c.v.radius());
        BlockPos centre = Constructs.feet(c.player);
        List<BlockPos> dome = Constructs.dome(centre, radius);
        int rows = radius + 1;
        c.sound(PowerSounds.BUILD, 1.5f, 0.5f);
        Vec3 middle = Vec3.atBottomCenterOf(centre);
        PowerState st = PowerState.of(c.player);
        // blueprint circle, a ground dome of hard light, and the dome's blocks assembling ring by ring
        Fx.at(c, FxKind.GLYPH, middle).scale(radius + 1).ticks(life).send();
        Fx.at(c, FxKind.DOME, middle).scale(radius).aux(1).ticks(life).flag(FxEvent.REACH).send();
        assemble(c, dome);
        // Spires (3): four towers rise just outside the dome, one on each side
        List<Vec3> spires = new ArrayList<>();
        if (c.step(3)) {
            List<BlockPos> towers = new ArrayList<>();
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos foot = centre.relative(d, radius + 2);
                for (int h = 0; h < 4; h++) towers.add(foot.above(h));
                spires.add(Vec3.atCenterOf(foot.above(4)));
            }
            if (Constructs.place(c.player, towers, life) > 0) assemble(c, towers);
        }
        int every = Math.max(10, (int) c.num(3, "spires", 40));
        float boltDmg = c.dmg(c.num(3, "bolt", 3f));
        // Sanctuary (4): the regeneration runs a step stronger; Crown Citadel (5): you heal on top and foes inside burn
        int regen = (c.is(2) ? 1 : 0) + (c.step(4) ? 1 : 0);
        float burn = c.dmg(2f * c.num(5, "burn", 0f));
        Running r = Casting.start(c, life);
        r.tick(rr -> {
            if (rr.age < rows) {
                int y = centre.getY() + rr.age;
                List<BlockPos> ring = new ArrayList<>();
                for (BlockPos p : dome) if (p.getY() == y) ring.add(p);
                Constructs.place(c.player, ring, Set.of(), false, life - rr.age);
                c.sound(middle, PowerSounds.BUILD, 0.6f, 0.6f + rr.age * 0.1f);
            }
            double r2 = radius * radius;
            if (rr.age % 20 == 0) {
                for (Player p : c.level.players()) {
                    if (p.position().distanceToSqr(middle) > r2) continue;
                    Moves.effect(p, MobEffects.REGENERATION, 50, regen);
                    PowerState.of(p).buff(PowerState.Buffs.BULWARK, c.now() + 25, 0.2);
                    if (burn > 0) p.heal(1f);
                }
                if (burn > 0) {
                    for (LivingEntity e : Combat.foesAround(c.player, middle, radius)) {
                        c.hurt(e, burn, null);
                        e.igniteForSeconds(2);
                    }
                }
                if (st != null && rr.age % 60 == 0) Fx.at(c, FxKind.HEAL, middle).ticks(40).flag(FxEvent.QUIET).noAnim().send();
            }
            if (!spires.isEmpty() && rr.age > rows && rr.age % every == 0) {
                for (Vec3 top : spires) {
                    LivingEntity aim = null;
                    double best = 16 * 16;
                    for (LivingEntity e : Combat.foesAround(c.player, top, 16)) {
                        double d = e.distanceToSqr(top);
                        if (d < best && e.position().distanceToSqr(middle) > r2 && clear(c, top, e.getBoundingBox().getCenter())) {
                            best = d;
                            aim = e;
                        }
                    }
                    if (aim == null) continue;
                    var b = Bolts.fire(c.level, c.player, null, top, aim.getBoundingBox().getCenter().subtract(top), 2.6f, boltDmg, c.force(), BOLT, 20, 0f);
                    b.cast = c;
                    c.sound(top, PowerSounds.SHOT, 0.7f, 1.2f);
                }
            }
        });
        return Result.OK;
    }
}
