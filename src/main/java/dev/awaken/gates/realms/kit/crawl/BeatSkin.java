package dev.awaken.gates.realms.kit.crawl;

import dev.awaken.gates.realms.kit.crawl.Sculpt.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A kind's blocks for the parts of a beat (SPEC §72.2): its fallen beams, the worn lips you hang from, the face a run is
 * along, its rails and plinths, its climb lines. Every rolled kind gives one ({@link CrawlLand#beatSkin}); what it leaves
 * out falls back to its rock, its floor and its half step. A role a move depends on must keep the plain role's shape
 * ({@link #check}): a lip is a whole block, a cap a slab hung high, so a beat proved in plain stone holds in every kind.
 */
public final class BeatSkin {
    @Nullable BlockState beam, lip, scuff, rail, plinth, plinthTop, step, cap, dust, crumble, deck, glow, tooth, line, rope, door;

    /** A fallen beam or lintel: something with an axis (a log, a pillar, basalt) lies the way the beam does. */
    public BeatSkin beam(BlockState s) {
        beam = s;
        return this;
    }

    /** The worn top of what you hang from: a whole block. */
    public BeatSkin lip(BlockState s) {
        lip = s;
        return this;
    }

    /** A run wall's face at running height, scuffed by feet: a whole block. */
    public BeatSkin scuff(BlockState s) {
        scuff = s;
        return this;
    }

    public BeatSkin rail(BlockState s) {
        rail = s;
        return this;
    }

    public BeatSkin plinth(BlockState s) {
        plinth = s;
        return this;
    }

    /** A plinth's half-step top: a bottom slab. */
    public BeatSkin plinthTop(BlockState s) {
        plinthTop = s;
        return this;
    }

    /** The half step of a beat's stairs and chutes: a bottom slab (the cap is the same slab hung high, unless given). */
    public BeatSkin step(BlockState s) {
        step = s;
        return this;
    }

    public BeatSkin cap(BlockState s) {
        cap = s;
        return this;
    }

    /** The floor before something low: dust, ash, frost, the mark of a draught. A whole block. */
    public BeatSkin dust(BlockState s) {
        dust = s;
        return this;
    }

    public BeatSkin crumble(BlockState s) {
        crumble = s;
        return this;
    }

    public BeatSkin deck(BlockState s) {
        deck = s;
        return this;
    }

    public BeatSkin glow(BlockState s) {
        glow = s;
        return this;
    }

    public BeatSkin tooth(BlockState s) {
        tooth = s;
        return this;
    }

    /** A wall line (a ladder, a drainpipe, ivy, a trellis): a block in #awaken:runner_climbs that faces out from its wall. */
    public BeatSkin line(BlockState s) {
        line = s;
        return this;
    }

    /** A free rope: a block in #awaken:runner_climbs with no facing. */
    public BeatSkin rope(BlockState s) {
        rope = s;
        return this;
    }

    public BeatSkin door(BlockState s) {
        door = s;
        return this;
    }

    /**
     * A climb line of the city's kit by name (SPEC §12: awaken:drainpipe, awaken:trellis, awaken:wall_ivy,
     * awaken:hanging_ladder), so the gates never reach into the city's classes; a ladder where it isn't there.
     */
    public static BlockState named(String id) {
        BlockState s = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(dev.awaken.Awaken.id(id)).defaultBlockState();
        return s.isAir() ? LADDER : s;
    }

    static BlockState or(@Nullable BlockState s, BlockState fallback) {
        return s != null ? s : fallback;
    }

    /** A beam's block lying its way (arg 0 along x, 2 along z, 1 upright), when it has an axis. */
    static BlockState axis(BlockState s, int arg) {
        if (!s.hasProperty(BlockStateProperties.AXIS)) return s;
        return s.setValue(BlockStateProperties.AXIS, arg == 0 ? Direction.Axis.X : arg == 2 ? Direction.Axis.Z : Direction.Axis.Y);
    }

    /** A wall line facing out from the side of its cell the wall is on (arg 0-3, Direction's 2D data). */
    static BlockState facing(BlockState s, int arg) {
        if (!s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return s;
        return s.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(arg).getOpposite());
    }

    /** A slab hung high. */
    static BlockState high(BlockState s) {
        return s.hasProperty(SlabBlock.TYPE) ? s.setValue(SlabBlock.TYPE, SlabType.TOP) : s;
    }

    /** The roles a move stands on, hangs from, slides under or runs along: these must keep the plain shape. */
    static final List<Role> SHAPED = List.of(Role.ROCK, Role.FLOOR, Role.STEP, Role.CAP, Role.BEAM, Role.LIP, Role.SCUFF, Role.RAIL,
            Role.PLINTH, Role.PLINTH_TOP, Role.DUST, Role.CRUMBLE, Role.DECK);

    /**
     * Where a land's blocks for the shaped roles differ in shape from the plain ones (a lip that is a slab, a cap that sits
     * low, a beam that is a fence): each as "role: block". Empty when the land's beats hold as they were proved.
     */
    public static List<String> check(java.util.function.BiFunction<Role, Boolean, BlockState> block) {
        List<String> out = new ArrayList<>();
        for (Role r : SHAPED) for (boolean top : new boolean[]{false, true}) {
            // a floor-like role's lower cells are the rock under it: the same shape
            BlockState want = Sculpt.plain(r, 0), got = block.apply(r, top);
            if (!same(shape(want), shape(got))) out.add(r.name().toLowerCase() + (top ? " (top)" : "") + ": " + got);
        }
        return out;
    }

    private static VoxelShape shape(BlockState s) {
        return s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
    }

    private static boolean same(VoxelShape a, VoxelShape b) {
        return !Shapes.joinIsNotEmpty(a, b, net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME);
    }

    static final BlockState LADDER = Blocks.LADDER.defaultBlockState();
}
