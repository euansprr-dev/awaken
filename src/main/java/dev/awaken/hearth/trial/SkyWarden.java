package dev.awaken.hearth.trial;

import dev.awaken.core.api.TrialHooks;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The fourth Proving's Warden (SPEC §47.8): the Sky Warden, for A. The bell won't stop, and you wake on a sea of cloud
 * under a cathedral hanging in the night. The font in its floor opens and the light lifts you up the shaft into the
 * nave; then the Sky Warden comes down out of the vault in a column of light. At two thirds of its health the aisles
 * break away and fall into the cloud (the nave and its arcades stay); at a third the roof lifts away into the sky, and the
 * sky remembers you: you're given Skyborn there, to fight it on the wing. Go over the edge and you wake inside the west
 * door, the porch outside it the way out. Its death gives you the sky for good and the cathedral mends.
 */
final class SkyWarden extends Warden {
    static final SkyWarden WARDEN = new SkyWarden();
    static final String MOB = "awaken:proving_sky_warden";
    /** On the cloud this long, looking up, before the font opens and the light lifts you (and at most this long rising). */
    static final int LIFT_AFTER = 50, LIFT_MAX = 220;
    /** Levitation's strength in the shaft: 0.3 blocks a tick, the 27 up in about four and a half seconds. */
    static final int LIFT_LEVEL = 5;
    /** Once everyone's up the font closes after this; the light comes down this long after; it comes at its end. */
    static final int CLOSE_AFTER = 15, LIGHT_AFTER = 40, DESCENT = 40;
    /** Its phases: the aisles fall at two thirds, the roof lifts at a third (its own phase lines). */
    static final float FALL_AT = 0.66F, LIFT_AT = 0.33F;
    /** The aisles creak this long, then fall a row a tick from the apse to the west front. */
    static final int CREAK = 60, FALL_ROWS = 1;
    /** The roof lifts two rows a tick, from the apse's far end to the west front. */
    static final int LIFT_ROWS = 2, ROOF_NORTH = Cathedral.APSE_Z - (int) Math.ceil(Cathedral.APSE_R) - 2;
    /** The hall is put back this many rows a tick after a reset, and this many in the mending after its death. */
    static final int RESET_ROWS = 16, MEND_ROWS = 3;
    /** Under this you've gone over the edge: lower than anywhere you can stand but the cloud, so a flier has room. */
    static final int VOID = -18;
    /** How far from the font it goes after you: the nave, the apse and the porch. */
    static final int HOME = 44;
    /** A falling piece lasts this long (it's in the cloud by then); a rising one this long (it's gone into the dark). */
    static final int FALL_LIFE = 45, RISE_LIFE = 90;

    private SkyWarden() {
        super(MOB, Rank.A, Cathedral.HALL);
    }

    @Override
    String asked() {
        return "The bell has taken you three times now. Go when you're ready.";
    }

    @Override
    int ring(ServerPlayer m, boolean practice) {
        m.level().playSound(null, m.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.6F, 0.5F);
        if (practice) return 40;
        dev.awaken.fault.Fault f = new dev.awaken.fault.Fault(dev.awaken.fault.Fault.Size.FAULT, "",
                List.of("MEASURING · MEASURING · MEASURING", "[ERROR] YOU ARE ABOVE THE CLOUDS"), "",
                "The bell stopped.", null, null, 0, true);
        dev.awaken.fault.Faults.play(m, f);
        return f.length();
    }

    // ---- the lift ----------------------------------------------------------------------------------------------------

    /** What a run keeps of the cathedral: the lift up the shaft, its coming, the pieces in the air, the mending. */
    static final class Sky {
        long liftAt = -1, upAt = -1, closedAt = -1, lightAt = -1;
        /** Everyone's been lifted into the nave (after a fall or a reset you wake at the door instead). */
        boolean lifted;
        /** Whether the sky has been given this run (at a third, once). */
        boolean given;
        final Set<UUID> up = new HashSet<>();
        final List<Piece> pieces = new ArrayList<>();
        Hall.@Nullable Repair mend;
        int mendRows = RESET_ROWS;
    }

    /** A piece of the cathedral in the air: falling into the cloud, or rising into the dark. */
    static final class Piece {
        final Display.BlockDisplay d;
        final long born;
        final boolean rising;
        Vec3 at, vel;

        Piece(Display.BlockDisplay d, Vec3 at, Vec3 vel, long born, boolean rising) {
            this.d = d;
            this.at = at;
            this.vel = vel;
            this.born = born;
            this.rising = rising;
        }
    }

    @Override
    void before(Provings.Run run, ServerPlayer leader) {
        run.room = new Sky();
    }

    static Sky sky(Provings.Run run) {
        if (run.room instanceof Sky s) return s;
        Sky s = new Sky();
        run.room = s;
        return s;
    }

    /** Inside the west door, facing the altar (where you wake after a fall). */
    @Override
    BlockPos wakeAt(BlockPos centre) {
        return centre.offset(0, 1, Cathedral.DOOR_Z);
    }

    /**
     * The first time, on the cloud under the font, looking up at the cathedral (a party spread a little round the
     * shaft). After a fall, inside the west door.
     */
    @Override
    void wake(ServerPlayer m, Provings.Run run, boolean first) {
        if (first) {
            int i = Math.max(0, List.copyOf(run.members).indexOf(m.getUUID()));
            Vec3 at = new Vec3(run.centre.getX() + 0.5 + (i % 3 - 1) * 1.2, run.centre.getY() + Cathedral.WAKE_DY,
                    run.centre.getZ() + 0.5 + (i / 3 % 3 - 1) * 1.2);
            m.teleport(new TeleportTransition(run.level, at, Vec3.ZERO, 180F, -38F, TeleportTransition.DO_NOTHING));
            m.resetFallDistance();
            run.level.playSound(null, BlockPos.containing(at), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 1.2F, 0.5F);
            return;
        }
        Vec3 at = Vec3.atBottomCenterOf(wakeAt(run.centre));
        m.teleport(new TeleportTransition(run.level, at, Vec3.ZERO, 180F, 0F, TeleportTransition.DO_NOTHING));
        m.resetFallDistance();
        run.level.playSound(null, BlockPos.containing(at), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 0.8F, 0.7F);
    }

    /** The font's blocks (its floor and the brick cap under it), {x, dy, z}. */
    private static final int[][] FONT = font();

    private static int[][] font() {
        List<int[]> out = new ArrayList<>();
        for (int x = -Cathedral.WELL_R; x <= Cathedral.WELL_R; x++) {
            for (int z = -Cathedral.WELL_R; z <= Cathedral.WELL_R; z++) {
                if (Cathedral.rWell(x, z) > Cathedral.WELL_R) continue;
                out.add(new int[]{x, 0, z});
                out.add(new int[]{x, -1, z});
            }
        }
        return out.toArray(new int[0][]);
    }

    /** The font opens (light pours down the shaft) or closes as built. */
    private static void font(Provings.Run run, boolean open) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int[] p : FONT) {
            at.set(run.centre.getX() + p[0], run.centre.getY() + p[1], run.centre.getZ() + p[2]);
            BlockState want = open ? null : Cathedral.block(p[0], p[1], p[2]);
            run.level.setBlock(at, want == null ? Blocks.AIR.defaultBlockState() : want, Cathedral.QUIET);
        }
    }

    /**
     * Before it comes. The first time: on the cloud a while, then the font opens and the light lifts you up the shaft;
     * each one up is set down beside the font, and once everyone is the font closes. Then (and after a reset, once the
     * hall is whole and someone walks up the nave) a column of light comes down where it will land.
     */
    @Override
    void waiting(Provings.Run run, List<ServerPlayer> here, long now) {
        Sky s = sky(run);
        if (!s.lifted) {
            lift(run, s, here, now);
            return;
        }
        if (s.lightAt < 0) {
            if (s.mend != null) return; // the hall still being put back
            boolean first = s.closedAt >= 0;
            boolean walked = false;
            for (ServerPlayer m : here) {
                if (m.level() == run.level && m.getZ() - run.centre.getZ() < Cathedral.DOOR_Z - 5) walked = true;
            }
            if (first ? now >= s.closedAt + LIGHT_AFTER : walked || now - run.waitingSince >= Provings.COME_AFTER) {
                s.lightAt = now;
                s.closedAt = -1;
                BlockPos land = landing(run);
                run.level.playSound(null, land.above(12), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 4F, 0.6F);
                run.level.playSound(null, land, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 3F, 0.5F);
            }
            return;
        }
        // the light coming down out of the vault, gathering where it lands
        long k = now - s.lightAt;
        double x = run.centre.getX() + 0.5, z = run.centre.getZ() + Cathedral.LAND_Z + 0.5, y0 = run.centre.getY() + 1;
        double top = run.centre.getY() + Cathedral.vault(0) - 1, y = top - (top - y0) * Math.min(1, k / (double) DESCENT);
        run.level.sendParticles(ParticleTypes.END_ROD, x, (y + top) / 2, z, 6, 0.25, (top - y) / 4, 0.25, 0.01);
        run.level.sendParticles(ParticleTypes.END_ROD, x, y0 + 0.1, z, 3, 1.2, 0.05, 1.2, 0.02);
        if (k % 10 == 0) run.level.playSound(null, BlockPos.containing(x, y, z), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.HOSTILE, 2F, 0.6F + k / 80F);
        if (k > DESCENT - 12) run.level.sendParticles(ParticleTypes.CLOUD, x, y0 + 0.4, z, 4, 1.5, 0.1, 1.5, 0.02);
    }

    private static void lift(Provings.Run run, Sky s, List<ServerPlayer> here, long now) {
        double ax = run.centre.getX() + 0.5, az = run.centre.getZ() + 0.5;
        if (s.liftAt < 0) {
            if (now < run.waitingSince + LIFT_AFTER) {
                if (now >= run.nextAmbient) {
                    // wind over the cloud, and the cathedral overhead creaking in it
                    RandomSource rnd = run.level.getRandom();
                    run.level.playSound(null, run.centre.offset(0, -4, 0), SoundEvents.CALCITE_HIT, SoundSource.BLOCKS, 2F, 0.4F + rnd.nextFloat() * 0.2F);
                    run.level.sendParticles(ParticleTypes.WHITE_ASH, ax, run.centre.getY() - 8, az, 20, 4, 3, 4, 0.01);
                    run.nextAmbient = now + 25 + rnd.nextInt(20);
                }
                return;
            }
            s.liftAt = now;
            font(run, true);
            run.level.playSound(null, run.centre, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 4F, 0.5F);
            run.level.playSound(null, run.centre.offset(0, Cathedral.WAKE_DY, 0), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 3F, 0.6F);
            for (ServerPlayer m : here) {
                if (m.level() != run.level) continue;
                m.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LIFT_MAX, LIFT_LEVEL, false, false));
                m.resetFallDistance();
            }
        }
        // the column of light up the shaft
        double lo = run.centre.getY() + Cathedral.WAKE_DY, hi = run.centre.getY() + 2;
        run.level.sendParticles(ParticleTypes.END_ROD, ax, (lo + hi) / 2, az, 8, 0.9, (hi - lo) / 4, 0.9, 0.02);
        if ((now - s.liftAt) % 20 == 0) run.level.playSound(null, run.centre.offset(0, -10, 0), SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 3F, 1.2F);
        boolean all = true;
        for (ServerPlayer m : here) {
            if (m.level() != run.level || s.up.contains(m.getUUID())) continue;
            double dx = m.getX() - ax, dz = m.getZ() - az, r = Math.sqrt(dx * dx + dz * dz);
            if (m.getY() >= run.centre.getY() + 1.2) {
                // up through the font: set down beside it
                s.up.add(m.getUUID());
                m.removeEffect(MobEffects.LEVITATION);
                m.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
                double ox = r > 0.2 ? dx / r : 0, oz = r > 0.2 ? dz / r : 1;
                m.setDeltaMovement(ox * 0.5, 0.15, oz * 0.5);
                m.needsSync = true;
                m.resetFallDistance();
                run.level.playSound(null, m.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 1.2F);
                continue;
            }
            all = false;
            if (r > 2.0 && m.getY() < run.centre.getY() - 1) {
                // drifting off the shaft: the light draws you back under it
                m.setDeltaMovement(-dx / r * 0.15, m.getDeltaMovement().y, -dz / r * 0.15);
                m.needsSync = true;
            }
        }
        if (s.upAt < 0 && (all || now >= s.liftAt + LIFT_MAX)) {
            s.upAt = now;
            for (ServerPlayer m : here) {
                if (m.level() != run.level || s.up.contains(m.getUUID())) continue;
                // still in the shaft when the light fades: brought up beside the font
                s.up.add(m.getUUID());
                m.removeEffect(MobEffects.LEVITATION);
                m.teleport(new TeleportTransition(run.level, new Vec3(ax, run.centre.getY() + 1, az + Cathedral.WELL_R + 2), Vec3.ZERO, 180F, 0F,
                        TeleportTransition.DO_NOTHING));
                m.resetFallDistance();
            }
        }
        if (s.upAt >= 0 && now >= s.upAt + CLOSE_AFTER) {
            for (ServerPlayer m : here) {
                // nobody is shut into the floor: anyone still over the font stands on it
                if (m.level() == run.level && Cathedral.rWell((int) Math.floor(m.getX() - run.centre.getX()), (int) Math.floor(m.getZ() - run.centre.getZ())) <= Cathedral.WELL_R + 0.5
                        && m.getY() < run.centre.getY() + 1) {
                    m.teleportTo(m.getX(), run.centre.getY() + 1, m.getZ());
                }
            }
            font(run, false);
            s.lifted = true;
            s.closedAt = now;
            run.level.playSound(null, run.centre.above(), SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 2.5F, 0.6F);
            run.level.playSound(null, run.centre.above(), SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2F, 0.8F);
        }
    }

    private static BlockPos landing(Provings.Run run) {
        return run.centre.offset(0, 1, Cathedral.LAND_Z);
    }

    /** It comes down at the end of its light. */
    @Override
    boolean comes(Provings.Run run, List<ServerPlayer> here, long now) {
        Sky s = sky(run);
        return s.lightAt >= 0 && now >= s.lightAt + DESCENT;
    }

    /** In the nave's north half, facing the door. */
    @Override
    Vec3 arrive(Provings.Run run) {
        return Vec3.atBottomCenterOf(landing(run));
    }

    @Override
    float arriveYaw(Provings.Run run) {
        return 0F;
    }

    @Override
    int home() {
        return HOME;
    }

    /** It lands: the light breaks round it, the glass rings, its wings beat once. */
    @Override
    void came(Provings.Run run, LivingEntity warden, List<ServerPlayer> here) {
        sky(run).lightAt = -1;
        double x = warden.getX(), y = warden.getY(), z = warden.getZ();
        run.level.sendParticles(ParticleTypes.END_ROD, x, y + 2, z, 90, 1.5, 2.5, 1.5, 0.15);
        run.level.sendParticles(ParticleTypes.CLOUD, x, y + 0.3, z, 40, 3.5, 0.2, 3.5, 0.08);
        run.level.sendParticles(ParticleTypes.WHITE_ASH, x, y + 4, z, 60, 4, 3, 4, 0.02);
        BlockPos at = warden.blockPosition();
        run.level.playSound(null, at, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 4F, 1.1F);
        run.level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 4F, 0.7F);
        run.level.playSound(null, at, SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 3F, 0.5F);
    }

    // ---- the hall turning ---------------------------------------------------------------------------------------------

    /** Its second phase (at two thirds): the aisles fall. Its third (a third): the roof lifts, and the sky is given. */
    @Override
    void fight(Provings.Run run, LivingEntity warden, long now) {
        float f = warden.getHealth() / warden.getMaxHealth();
        if (run.turns == 0 && f <= FALL_AT) {
            run.turns = 1;
            run.turnAt = now;
            run.turnRow = 0;
            run.level.playSound(null, run.centre.offset(11, 8, 0), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.BLOCKS, 3F, 0.5F);
            run.level.playSound(null, run.centre.offset(-11, 8, 0), SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 4F, 0.5F);
        }
        if (run.turns == 1 && f <= LIFT_AT) {
            // a blow big enough to skip the aisles' fall finishes it first
            if (run.turnAt >= 0) while (run.turnRow < fallRows().length) fallRow(run, run.turnRow++, now);
            run.turns = 2;
            run.turnAt = now;
            run.turnRow = 0;
            run.level.playSound(null, run.centre.above(Cathedral.CLERESTORY), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 4F, 0.4F);
            run.level.playSound(null, run.centre.above(Cathedral.CLERESTORY), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 4F, 0.4F);
            giveTheSky(run);
        }
    }

    /** The roof opening, the sky remembers you: Skyborn, given there (a real run only), so the rest is fought on the wing. */
    private static void giveTheSky(Provings.Run run) {
        Sky s = sky(run);
        if (s.given || run.practice) return;
        s.given = true;
        for (UUID u : run.members) {
            ServerPlayer m = run.level.getServer().getPlayerList().getPlayer(u);
            if (m != null && m.level() == run.level) TrialHooks.atEdge(m, TrialHooks.SKY_WARDEN);
        }
    }

    @Override
    void step(Provings.Run run, long now) {
        Sky s = sky(run);
        pieces(s, now);
        if (s.mend != null && s.mend.step(s.mendRows)) s.mend = null;
        if (s.lifted) overTheEdge(run);
        if (run.turnAt < 0) return;
        long k = now - run.turnAt;
        if (run.turns == 1) {
            if (k < CREAK) {
                if (k % 4 == 0) creak(run);
                return;
            }
            for (int n = 0; n < FALL_ROWS && run.turnRow < fallRows().length; n++) fallRow(run, run.turnRow++, now);
            if (run.turnRow >= fallRows().length) run.turnAt = -1;
        } else if (run.turns == 2) {
            for (int n = 0; n < LIFT_ROWS && run.turnRow < liftRows().length; n++) liftRow(run, run.turnRow++, now);
            if (run.turnRow >= liftRows().length) {
                run.turnAt = -1;
                run.level.playSound(null, run.centre.above(20), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 3F, 0.5F);
            }
        }
    }

    /** Whoever goes over the edge has fallen; if it goes over, it comes back up into the nave. */
    private static void overTheEdge(Provings.Run run) {
        if (run.stage == Provings.Stage.OPENING) return;
        for (UUID u : List.copyOf(run.members)) {
            ServerPlayer m = run.level.getServer().getPlayerList().getPlayer(u);
            if (m == null || m.level() != run.level || m.isSpectator() || m.getY() >= run.centre.getY() + VOID) continue;
            Trials.Session s = Trials.SESSIONS.get(u);
            if (s != null) Provings.fell(m, s, run.level.damageSources().fellOutOfWorld());
        }
        LivingEntity w = Provings.warden(run);
        if (w != null && w.getY() < run.centre.getY() - 2) {
            double lx = w.getX() - run.centre.getX() - 0.5, lz = w.getZ() - run.centre.getZ() - 0.5;
            double x = Math.max(-5, Math.min(5, lx)), z = Math.max(Cathedral.APSE_Z + 2, Math.min(Cathedral.DOOR_Z - 4, lz));
            w.teleportTo(run.centre.getX() + 0.5 + x, run.centre.getY() + 1, run.centre.getZ() + 0.5 + z);
            w.setDeltaMovement(Vec3.ZERO);
            w.resetFallDistance();
            run.level.sendParticles(ParticleTypes.END_ROD, w.getX(), w.getY() + 1.5, w.getZ(), 40, 1, 1.5, 1, 0.1);
            run.level.playSound(null, w.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 3F, 1.2F);
        }
    }

    /** Before the aisles go: dust sifting from their roofs, the stone groaning. */
    private static void creak(Provings.Run run) {
        RandomSource rnd = run.level.getRandom();
        for (int k = 0; k < 3; k++) {
            int side = rnd.nextBoolean() ? 1 : -1;
            double x = run.centre.getX() + 0.5 + side * (Cathedral.FALL_X + rnd.nextInt(4)), z = run.centre.getZ() + Cathedral.APSE_Z + 2 + rnd.nextInt(76);
            run.level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.CALCITE.defaultBlockState()), x, run.centre.getY() + 13.5, z,
                    8, 1.2, 0.1, 1.2, 0);
        }
        BlockPos at = run.centre.offset(rnd.nextBoolean() ? 12 : -12, 6, Cathedral.APSE_Z + rnd.nextInt(78));
        run.level.playSound(null, at, SoundEvents.CALCITE_HIT, SoundSource.BLOCKS, 3F, 0.4F + rnd.nextFloat() * 0.2F);
        if (rnd.nextInt(3) == 0) run.level.playSound(null, at, SoundEvents.DEEPSLATE_HIT, SoundSource.BLOCKS, 3F, 0.4F);
    }

    /** A row of the aisles (both sides) breaks away: its blocks go, a couple of them falling as pieces into the cloud. */
    private static void fallRow(Provings.Run run, int row, long now) {
        int[][] spots = fallRows()[row];
        int z = Cathedral.APSE_Z + row;
        Sky s = sky(run);
        RandomSource rnd = run.level.getRandom();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        boolean glass = false;
        int thrown = 0;
        for (int[] p : spots) {
            at.set(run.centre.getX() + p[0], run.centre.getY() + p[1], run.centre.getZ() + z);
            BlockState st = run.level.getBlockState(at);
            if (st.isAir()) continue;
            glass |= st.getBlock() instanceof StainedGlassBlock;
            if (thrown < 2 && p[1] >= 0 && !st.is(Blocks.LIGHT) && rnd.nextInt(30) == 0) {
                Vec3 from = Vec3.atCenterOf(at);
                Display.BlockDisplay d = UnmadeWarden.stone(run.level, st, from, 1.0F, rnd.nextFloat() * 0.8F);
                if (d != null) {
                    double out = Math.signum(p[0]) * (0.04 + rnd.nextDouble() * 0.06);
                    s.pieces.add(new Piece(d, from, new Vec3(out, -0.02 - rnd.nextDouble() * 0.05, (rnd.nextDouble() - 0.5) * 0.04), now, false));
                    thrown++;
                }
            }
            run.level.setBlock(at, Blocks.AIR.defaultBlockState(), Cathedral.QUIET);
        }
        for (int side = -1; side <= 1; side += 2) {
            double x = run.centre.getX() + 0.5 + side * 13, cz = run.centre.getZ() + z + 0.5;
            run.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.CALCITE.defaultBlockState()), x, run.centre.getY() + 4, cz,
                    10, 2.5, 4, 0.3, 0.05);
            if (Math.floorMod(z, 3) == 0) run.level.sendParticles(ParticleTypes.CLOUD, x, run.centre.getY() - 2, cz, 4, 2.5, 1, 0.3, 0.02);
        }
        BlockPos p = run.centre.offset(0, 6, z);
        if (Math.floorMod(z, 3) == 0) run.level.playSound(null, p, SoundEvents.CALCITE_BREAK, SoundSource.BLOCKS, 3.5F, 0.5F + rnd.nextFloat() * 0.2F);
        if (Math.floorMod(z, 8) == 0) run.level.playSound(null, p, SoundEvents.DEEPSLATE_BRICKS_BREAK, SoundSource.BLOCKS, 4F, 0.45F);
        if (glass && rnd.nextBoolean()) run.level.playSound(null, p, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 3F, 0.6F + rnd.nextFloat() * 0.3F);
    }

    /** A row of the roof lifts away: its blocks go (the unseen lights stay), one of them rising as a piece into the dark. */
    private static void liftRow(Provings.Run run, int row, long now) {
        int[][] spots = liftRows()[row];
        int z = ROOF_NORTH + row;
        Sky s = sky(run);
        RandomSource rnd = run.level.getRandom();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        boolean thrown = false;
        for (int[] p : spots) {
            at.set(run.centre.getX() + p[0], run.centre.getY() + p[1], run.centre.getZ() + z);
            BlockState st = run.level.getBlockState(at);
            if (st.isAir() || Cathedral.keep(st)) continue;
            if (!thrown && rnd.nextInt(Math.max(1, spots.length / 3)) == 0) {
                Vec3 from = Vec3.atCenterOf(at);
                Display.BlockDisplay d = UnmadeWarden.stone(run.level, st, from, 1.0F, rnd.nextFloat() * 0.5F);
                if (d != null) {
                    s.pieces.add(new Piece(d, from, new Vec3((rnd.nextDouble() - 0.5) * 0.03, 0.04 + rnd.nextDouble() * 0.04, (rnd.nextDouble() - 0.5) * 0.03),
                            now, true));
                    thrown = true;
                }
            }
            run.level.setBlock(at, Blocks.AIR.defaultBlockState(), Cathedral.QUIET);
        }
        if (Math.floorMod(z, 2) == 0) {
            run.level.sendParticles(ParticleTypes.END_ROD, run.centre.getX() + 0.5, run.centre.getY() + 26, run.centre.getZ() + z + 0.5, 6, 6, 3, 0.3, 0.03);
            run.level.sendParticles(ParticleTypes.WHITE_ASH, run.centre.getX() + 0.5, run.centre.getY() + 22, run.centre.getZ() + z + 0.5, 10, 7, 4, 0.4, 0.02);
        }
        if (Math.floorMod(z, 6) == 0) {
            BlockPos p = run.centre.offset(0, 24, z);
            run.level.playSound(null, p, SoundEvents.CALCITE_BREAK, SoundSource.BLOCKS, 3F, 0.6F + rnd.nextFloat() * 0.3F);
            run.level.playSound(null, p, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 3F, 0.5F + rnd.nextFloat() * 0.4F);
        }
    }

    /** The pieces in the air: falling ones drop into the cloud, rising ones climb faster and faster into the dark. */
    private static void pieces(Sky s, long now) {
        int n = 0;
        for (Iterator<Piece> it = s.pieces.iterator(); it.hasNext(); n++) {
            Piece p = it.next();
            long t = now - p.born;
            if (p.d.isRemoved() || t > (p.rising ? RISE_LIFE : FALL_LIFE)) {
                p.d.discard();
                it.remove();
                continue;
            }
            p.vel = p.rising ? p.vel.add(0, 0.006, 0) : p.vel.add(0, -0.06, 0).scale(0.98);
            p.at = p.at.add(p.vel);
            // every other tick each (half of them a tick): the display's own interpolation fills the gap
            if (((n + now) & 1) == 0) p.d.setPos(p.at.x, p.at.y, p.at.z);
        }
    }

    private static void clearPieces(Sky s) {
        for (Piece p : s.pieces) p.d.discard();
        s.pieces.clear();
    }

    private static int[][][] FALL_SPOTS, LIFT_SPOTS;

    /** Each aisle row's blocks, {x, dy}, from the apse's chord south to past the west front (built once). */
    static int[][][] fallRows() {
        if (FALL_SPOTS == null) {
            int rows = Cathedral.WEST + 1 - Cathedral.APSE_Z + 1;
            int[][][] out = new int[rows][][];
            for (int k = 0; k < rows; k++) {
                int z = Cathedral.APSE_Z + k;
                List<int[]> row = new ArrayList<>();
                for (int x = -Cathedral.BUTTRESS - 1; x <= Cathedral.BUTTRESS + 1; x++) {
                    if (!Cathedral.falls(x, z)) continue;
                    for (int dy = Cathedral.BAND_LO; dy <= Cathedral.BAND_HI; dy++) if (Cathedral.block(x, dy, z) != null) row.add(new int[]{x, dy});
                }
                out[k] = row.toArray(new int[0][]);
            }
            FALL_SPOTS = out;
        }
        return FALL_SPOTS;
    }

    /** Each roof row's blocks, {x, dy}, from the apse's far end south to the west front (built once). */
    static int[][][] liftRows() {
        if (LIFT_SPOTS == null) {
            int rows = Cathedral.WEST + 1 - ROOF_NORTH + 1;
            int[][][] out = new int[rows][][];
            for (int k = 0; k < rows; k++) {
                int z = ROOF_NORTH + k;
                List<int[]> row = new ArrayList<>();
                for (int x = -Cathedral.LIFT_X; x <= Cathedral.LIFT_X; x++) {
                    for (int dy = Cathedral.LIFT_DY; dy <= Cathedral.ROOF; dy++) {
                        BlockState st = Cathedral.block(x, dy, z);
                        if (st != null && Cathedral.lifts(x, dy, z) && !Cathedral.keep(st)) row.add(new int[]{x, dy});
                    }
                }
                out[k] = row.toArray(new int[0][]);
            }
            LIFT_SPOTS = out;
        }
        return LIFT_SPOTS;
    }

    /** Everyone fell: the pieces go and the cathedral is put back whole (quickly), and it can come again. */
    @Override
    void reset(Provings.Run run) {
        Sky s = sky(run);
        clearPieces(s);
        s.mend = new Hall.Repair(Cathedral.HALL, run.level, run.centre, false);
        s.mendRows = RESET_ROWS;
        s.lightAt = -1;
        s.closedAt = -1;
        run.turns = 0;
        run.turnAt = -1;
    }

    @Override
    void died(Provings.Run run, long now) {
        run.turnAt = -1; // what was still turning stops where it is
        Entity w = run.boss != null ? run.level.getEntity(run.boss) : null;
        BlockPos at = w != null ? w.blockPosition() : run.centre.above();
        run.level.playSound(null, at, SoundEvents.ENDER_DRAGON_FLAP, SoundSource.HOSTILE, 4F, 0.6F);
        run.level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 4F, 0.4F);
    }

    /** After its death: the light settles, the sky is yours, and the cathedral mends from the apse to the door. */
    @Override
    void outro(Provings.Run run, List<ServerPlayer> here, long t) {
        Sky s = sky(run);
        if (t == 20) {
            for (ServerPlayer m : here) {
                if (m.level() == run.level) run.level.playSound(null, m.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5F, 0.6F);
            }
        }
        if (t == 50 && !run.practice) {
            for (ServerPlayer m : here) if (m.level() == run.level) TrialHooks.done(m, TrialHooks.SKY_WARDEN, 0, 0);
        }
        if (t == 70) {
            clearPieces(s);
            s.mend = new Hall.Repair(Cathedral.HALL, run.level, run.centre, false);
            s.mendRows = MEND_ROWS;
        }
        if (s.mend != null && t % 8 == 0) {
            run.level.playSound(null, run.centre.above(6), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2.5F, 0.5F + (t % 40) / 60F);
        }
    }

    /** Down the porch's steps past its broken end: gone out. */
    @Override
    boolean out(Provings.Run run, ServerPlayer m) {
        double lx = m.getX() - run.centre.getX() - 0.5, lz = m.getZ() - run.centre.getZ();
        return lz >= Cathedral.OUT_Z && Math.abs(lx) <= 4.5 && m.getY() >= run.centre.getY() + 0.5;
    }

    @Override
    String firstFall() {
        return "You wake inside the west door. The porch outside it is the way out.";
    }

    @Override
    String name() {
        return "The Sky Warden";
    }
}
