package dev.awaken.gates.client;

import dev.awaken.core.AwakenConfig;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.block.RigBlocks;
import dev.awaken.gates.content.Theme;
import dev.awaken.gates.net.RigPayloads;
import dev.awaken.gates.run.RigWellsLive;
import dev.awaken.gates.world.RigBeam;
import dev.awaken.gates.world.RigRoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * The Gate Rig on the client (SPEC §17 "Gate Rig"): the rig rooms of your floor (from the server's scene), the beams
 * traced through the blocks you see each tick (the same tracer the server uses), the cues' animations and particles,
 * and the wells predicted for you, so you start falling up the moment you step in rather than a round trip later.
 * Someone in a well is drawn upside down (feet to the ceiling); your own flip tips the camera a few degrees and back,
 * within the comfort settings (shake off: no tip; the FOV-effects slider scales the rest).
 */
public final class RigClient {
    private RigClient() {}

    /** A beam to draw: points in pairs (segment start, end), world coordinates; lit when it ends on its seal. */
    record Beam(int theme, double[] points, boolean lit) {}

    static List<RigPayloads.Room> rooms = List.of();
    private static boolean[] surge = new boolean[0];
    static final List<Beam> beams = new ArrayList<>();
    /** Your own flip: when it happened and which way (the camera's tip). */
    private static long flipAt = Long.MIN_VALUE;
    private static boolean flipUp, wasUp;
    static final int TIP_TICKS = 14;
    static final float TIP_DEGREES = 5F, TIP_FOV = 0.07F;

    public static void init(IEventBus modBus) {
        modBus.addListener(RigParticles::register);
        modBus.addListener(RegisterRenderPipelinesEvent.class, RigPipelines::onRegister);
        modBus.addListener(RegisterRenderStateModifiersEvent.class, RigClient::modifiers);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(RigClient::tick);
        bus.addListener((SubmitCustomGeometryEvent e) -> RigFx.submit(e));
        bus.addListener(RigClient::camera);
        bus.addListener(RigClient::fov);
        bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> clear());
    }

    static void clear() {
        rooms = List.of();
        surge = new boolean[0];
        beams.clear();
        RigFx.clear();
    }

    // ---- payloads ---------------------------------------------------------------------------------------------------

    static void scene(RigPayloads.Scene s) {
        rooms = List.copyOf(s.rooms());
        surge = new boolean[rooms.size()];
        for (int i = 0; i < rooms.size(); i++) surge[i] = rooms.get(i).surge();
        beams.clear();
    }

    static void cue(RigPayloads.Cue c) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        List<BlockPos> cells = new ArrayList<>(c.cells().size());
        for (long l : c.cells()) cells.add(BlockPos.of(l));
        int col = RigFx.light(c.theme());
        RandomSource r = level.getRandom();
        switch (c.kind()) {
            case RigPayloads.Cue.WARN -> {
                RigFx.GLOWS.add(new RigFx.Glow(wellsAt(cells) >= 0 ? RigPayloads.Cue.SURGE : RigPayloads.Cue.WARN, cells, now, c.ticks(), c.theme()));
                for (BlockPos p : cells) {
                    RigParticles.spawn(level, GatesRegistry.P_GRIT.get(), dust(c.theme()), 1F, p.getX() + r.nextDouble(), p.getY() - 0.05, p.getZ() + r.nextDouble(), 0, -0.02, 0);
                    if (c.theme() == Theme.EMBER_MINES.ordinal() && r.nextInt(2) == 0)
                        RigParticles.spawn(level, GatesRegistry.P_SPARK.get(), col, 1F, p.getX() + r.nextDouble(), p.getY() + 1.05, p.getZ() + r.nextDouble(), 0, 0.04, 0);
                }
            }
            case RigPayloads.Cue.FALL, RigPayloads.Cue.RISE, RigPayloads.Cue.SPIN, RigPayloads.Cue.DROP -> {
                for (int i = 0; i < cells.size(); i++) {
                    BlockState s = Block.stateById(c.states().get(Math.min(i, c.states().size() - 1)));
                    BlockPos p = cells.get(i);
                    float seed = (float) Math.floorMod(p.asLong() * 2654435761L, 1000L) / 1000F;
                    RigFx.MOVES.add(new RigFx.Move(c.kind(), s, p, now, c.ticks(), c.a(), c.theme(), seed));
                    if (c.kind() == RigPayloads.Cue.FALL || c.kind() == RigPayloads.Cue.DROP)
                        RigParticles.spawn(level, GatesRegistry.P_GRIT.get(), dust(c.theme()), 1.3F, p.getX() + 0.5, p.getY() + (c.kind() == RigPayloads.Cue.DROP ? 0 : 0.9), p.getZ() + 0.5,
                                (r.nextDouble() - 0.5) * 0.1, 0.02, (r.nextDouble() - 0.5) * 0.1);
                    if (c.kind() == RigPayloads.Cue.SPIN)
                        RigParticles.spawn(level, GatesRegistry.P_GLINT.get(), 0xFFFFFF, 1F, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 0, 0.01, 0);
                }
            }
            case RigPayloads.Cue.SHATTER, RigPayloads.Cue.IGNITE -> {
                RigFx.GLOWS.add(new RigFx.Glow(c.kind(), cells, now, 10, c.theme()));
                boolean glass = c.kind() == RigPayloads.Cue.SHATTER;
                for (BlockPos p : cells) {
                    for (int k = 0; k < (glass ? 3 : 2); k++) {
                        RigParticles.spawn(level, glass ? GatesRegistry.P_SHARD.get() : GatesRegistry.P_GLINT.get(), glass ? 0xE6EEF8 : col, 1F,
                                p.getX() + r.nextDouble(), p.getY() + r.nextDouble(), p.getZ() + r.nextDouble(),
                                (r.nextDouble() - 0.5) * 0.25, r.nextDouble() * 0.15, (r.nextDouble() - 0.5) * 0.25);
                    }
                }
            }
            case RigPayloads.Cue.CAVE -> RigFx.GLOWS.add(new RigFx.Glow(RigPayloads.Cue.CAVE, cells, now, c.ticks(), c.theme()));
            case RigPayloads.Cue.SURGE -> {
                int i = wellsAt(cells);
                if (i >= 0) surge[i] = c.a() > 0.5F;
            }
            case RigPayloads.Cue.PIT -> {
                for (BlockPos p : cells) for (int k = 0; k < 12; k++) {
                    RigParticles.spawn(level, GatesRegistry.P_GRIT.get(), dust(c.theme()), 1.4F, p.getX() + r.nextDouble() - 0.5, p.getY() + 0.2, p.getZ() + r.nextDouble() - 0.5,
                            (r.nextDouble() - 0.5) * 0.3, 0.1 + r.nextDouble() * 0.2, (r.nextDouble() - 0.5) * 0.3);
                }
            }
            default -> {}
        }
    }

    /** The wells room a cue's first cell is in, or -1. */
    private static int wellsAt(List<BlockPos> cells) {
        if (cells.isEmpty()) return -1;
        BlockPos p = cells.getFirst();
        for (int i = 0; i < rooms.size(); i++) {
            RigPayloads.Room r = rooms.get(i);
            if (r.kind() == RigRoom.Kind.GRAVITY_WELLS.ordinal() && inside(r.min(), r.max(), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)) return i;
        }
        return -1;
    }

    /** The colour of a theme's grit (the pit's dust). */
    static int dust(int theme) {
        return switch (Theme.values()[Math.floorMod(theme, Theme.values().length)]) {
            case EMBER_MINES -> 0x6B4A3A;
            case GLASS_DESERT -> 0xD9BB80;
            case BELL_CRYPT -> 0x9AA0B0;
            default -> 0xA8A2A0;
        };
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer p = mc.player;
        if (level == null || p == null || level.dimension() != GatesRegistry.GATES) {
            if (!rooms.isEmpty()) clear();
            return;
        }
        long now = level.getGameTime();
        RigFx.expire(now);
        beams.clear();
        RandomSource r = level.getRandom();
        for (int i = 0; i < rooms.size(); i++) {
            RigPayloads.Room room = rooms.get(i);
            Vec3 c = new Vec3((room.min().getX() + room.max().getX()) / 2.0, room.min().getY(), (room.min().getZ() + room.max().getZ()) / 2.0);
            if (p.position().distanceToSqr(c) > 80 * 80) continue;
            if (room.kind() == RigRoom.Kind.MIRROR_HALL.ordinal()) beam(level, room, r, p);
            else if (room.kind() == RigRoom.Kind.GRAVITY_WELLS.ordinal()) motes(level, room, surge[i], r, p);
        }
        // the warnings shed grit while they last
        for (RigFx.Glow g : RigFx.GLOWS) {
            if (g.kind() != RigPayloads.Cue.WARN && g.kind() != RigPayloads.Cue.CAVE) continue;
            for (BlockPos q : g.cells()) {
                if (r.nextInt(4) != 0) continue;
                RigParticles.spawn(level, GatesRegistry.P_GRIT.get(), dust(g.theme()), 0.8F, q.getX() + r.nextDouble(), q.getY() - 0.05, q.getZ() + r.nextDouble(), 0, -0.01, 0);
            }
        }
        predictWells(level, p, now);
    }

    /** Traces the hall's light through the blocks the client has, like the server does, and sheds a glint or two. */
    private static void beam(ClientLevel level, RigPayloads.Room room, RandomSource r, LocalPlayer p) {
        int y = room.lens().getY();
        int x0 = room.min().getX(), z0 = room.min().getZ(), x1 = room.max().getX(), z1 = room.max().getZ();
        BlockPos seal = room.seal();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        RigBeam.Grid grid = new RigBeam.Grid() {
            @Override
            public RigBeam.Hit at(int x, int z) {
                BlockState s = level.getBlockState(m.set(x, y, z));
                if (x == seal.getX() && z == seal.getZ()) return s.is(GatesRegistry.RIG_SEAL.get()) ? RigBeam.Hit.RECEIVER : RigBeam.Hit.SOLID;
                if (x < x0 || x > x1 || z < z0 || z > z1) return RigBeam.Hit.SOLID;
                if (s.is(GatesRegistry.RIG_MIRROR.get())) return RigBeam.Hit.MIRROR;
                return s.getCollisionShape(level, m).isEmpty() ? RigBeam.Hit.EMPTY : RigBeam.Hit.SOLID;
            }

            @Override
            public int turn(int x, int z) {
                BlockState s = level.getBlockState(m.set(x, y, z));
                return s.is(GatesRegistry.RIG_MIRROR.get()) ? s.getValue(RigBlocks.Mirror.TURN) : 0;
            }
        };
        List<Double> pts = new ArrayList<>();
        int dir = room.dir();
        long hit = RigBeam.trace(grid, room.lens().getX(), room.lens().getZ(), dir, (ax, az, bx, bz, d) -> {
            boolean first = pts.isEmpty();
            pts.add(ax + 0.5 + (first ? RigBeam.DX[d] * 0.5 : 0));
            pts.add(y + 0.5);
            pts.add(az + 0.5 + (first ? RigBeam.DZ[d] * 0.5 : 0));
            pts.add(bx + 0.5);
            pts.add(y + 0.5);
            pts.add(bz + 0.5);
        });
        double[] a = new double[pts.size()];
        for (int i = 0; i < a.length; i++) a[i] = pts.get(i);
        beams.add(new Beam(room.theme(), a, hit == RigBeam.pack(seal.getX(), seal.getZ())));
        if (a.length >= 6 && r.nextInt(2) == 0 && p.position().distanceToSqr(a[0], a[1], a[2]) < 48 * 48) {
            int s = r.nextInt(a.length / 6) * 6;
            double u = r.nextDouble();
            RigParticles.spawn(level, GatesRegistry.P_GLINT.get(), RigFx.hot(RigFx.light(room.theme()), 0.4F), 0.8F,
                    Mth.lerp(u, a[s], a[s + 3]), a[s + 1] + (r.nextDouble() - 0.5) * 0.2, Mth.lerp(u, a[s + 2], a[s + 5]),
                    (r.nextDouble() - 0.5) * 0.01, 0.01, (r.nextDouble() - 0.5) * 0.01);
        }
    }

    /** A well's motes stream up through it; a surging room streams everywhere. */
    private static void motes(ClientLevel level, RigPayloads.Room room, boolean surging, RandomSource r, LocalPlayer p) {
        int col = RigFx.light(room.theme());
        for (int i = 0; i + 1 < room.wells().size(); i += 2) {
            BlockPos a = room.wells().get(i), b = room.wells().get(i + 1);
            for (int k = 0; k < 2; k++) {
                double x = a.getX() + r.nextDouble() * (b.getX() - a.getX() + 1), y = a.getY() + r.nextDouble() * (b.getY() - a.getY() + 1);
                double z = a.getZ() + r.nextDouble() * (b.getZ() - a.getZ() + 1);
                RigParticles.spawn(level, GatesRegistry.P_MOTE.get(), col, 1F, x, y, z, 0, 0.05 + r.nextDouble() * 0.04, 0);
            }
        }
        if (surging) {
            BlockPos a = room.min(), b = room.max();
            for (int k = 0; k < 4; k++) {
                RigParticles.spawn(level, GatesRegistry.P_MOTE.get(), col, 1.2F, a.getX() + r.nextDouble() * (b.getX() - a.getX() + 1),
                        a.getY() + 4 + r.nextDouble() * (b.getY() - a.getY() - 3), a.getZ() + r.nextDouble() * (b.getZ() - a.getZ() + 1), 0, 0.09, 0);
            }
        }
    }

    // ---- the wells, predicted ---------------------------------------------------------------------------------------

    /**
     * You fall up the moment you step into a well: the client sets the same gravity the server will (the server's sync
     * agrees a moment later). A flip either way refreshes your eye height and plays its sound here, at once.
     */
    private static void predictWells(ClientLevel level, LocalPlayer p, long now) {
        AttributeInstance g = p.getAttribute(Attributes.GRAVITY);
        if (g == null) return;
        boolean want = false, near = false;
        Vec3 at = p.position().add(0, 0.9, 0);
        for (int i = 0; i < rooms.size(); i++) {
            RigPayloads.Room room = rooms.get(i);
            if (room.kind() != RigRoom.Kind.GRAVITY_WELLS.ordinal() || !inside(room.min(), room.max(), at.x, at.y, at.z)) continue;
            near = true;
            if (surge[i]) want = true;
            for (int k = 0; k + 1 < room.wells().size(); k += 2) {
                if (inside(room.wells().get(k), room.wells().get(k + 1), at.x, at.y, at.z)) want = true;
            }
        }
        if (near && !p.isSpectator() && !p.getAbilities().flying && want != g.hasModifier(RigWellsLive.WELL)) {
            if (want) g.addOrUpdateTransientModifier(new AttributeModifier(RigWellsLive.WELL, RigWellsLive.FLIP, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            else g.removeModifier(RigWellsLive.WELL);
            p.resetFallDistance();
        }
        boolean up = g.hasModifier(RigWellsLive.WELL);
        if (up != wasUp) {
            wasUp = up;
            p.refreshDimensions();
            flipAt = now;
            flipUp = up;
            level.playLocalSound(p.getX(), p.getY() + 1, p.getZ(), up ? GatesRegistry.SND_RIG_FLIP_UP.get() : GatesRegistry.SND_RIG_FLIP_DOWN.get(),
                    SoundSource.PLAYERS, 0.9F, 1F, false);
        }
    }

    private static boolean inside(BlockPos a, BlockPos b, double x, double y, double z) {
        return x >= Math.min(a.getX(), b.getX()) && x < Math.max(a.getX(), b.getX()) + 1 && y >= Math.min(a.getY(), b.getY())
                && y < Math.max(a.getY(), b.getY()) + 1 && z >= Math.min(a.getZ(), b.getZ()) && z < Math.max(a.getZ(), b.getZ()) + 1;
    }

    /** The flip's tip of the camera: a few degrees and back over 0.7 s, none with shake off. */
    private static float tip(float partial) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || flipAt == Long.MIN_VALUE) return 0;
        float t = (level.getGameTime() - flipAt + partial) / TIP_TICKS;
        return t < 0 || t > 1 ? 0 : Mth.sin(t * Mth.PI);
    }

    static void camera(ViewportEvent.ComputeCameraAngles e) {
        float s = tip((float) e.getPartialTick());
        if (s <= 0 || !AwakenConfig.Client.CAMERA_SHAKE.get()) return;
        float scale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
        e.setRoll(e.getRoll() + (flipUp ? 1 : -1) * TIP_DEGREES * s * scale);
    }

    static void fov(ViewportEvent.ComputeFov e) {
        float s = tip((float) e.getPartialTick());
        if (s <= 0) return;
        float scale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
        e.setFOV(e.getFOV() * (1 + TIP_FOV * s * scale));
    }

    // ---- upside down ------------------------------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void modifiers(RegisterRenderStateModifiersEvent e) {
        e.registerEntityModifier((Class) LivingEntityRenderer.class, (entity, state) -> flip((Entity) entity, (LivingEntityRenderState) state));
        e.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
            @Override
            public <T extends Avatar & ClientAvatarEntity> void accept(T avatar, AvatarRenderState state) {
                flip(avatar, state);
            }
        });
    }

    /** Someone falling up in a well is drawn feet to the ceiling, the way the game draws its upside-down names. */
    private static void flip(Entity e, LivingEntityRenderState s) {
        if (s.isUpsideDown || !(e instanceof LivingEntity le) || e.level().dimension() != GatesRegistry.GATES || !RigWellsLive.upsideDown(le)) return;
        s.isUpsideDown = true;
        s.xRot = -s.xRot;
        s.yRot = -s.yRot;
    }
}
