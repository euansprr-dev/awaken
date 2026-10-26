package dev.awaken.delve.client;

import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.delve.sense.SenseModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SpeleothemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.Locale;

/**
 * The Hollows' sound (SPEC §28.5; PLAN 8.4; docs/sound/PLAN.md "Caves and depth"): delve's own driver, so it plays
 * whether or not AmbientSounds is in the pack (with it, the Conductor's family soundscapes are off and AmbientSounds
 * plays the diffuse cave beds; this adds what has a place, so the two never double). A probe walks one column at a
 * time round the listener (at most {@value #READS} block reads a tick: up to the roof, down to the floor) and
 * remembers what it finds: dripstone tips and wet roofs (and where their drops land: rock or a pool), rock, water,
 * potent sulfur, and where daylight's sky light reaches in. From those:
 * <ul>
 *   <li><b>drips</b> where the drops really land, sometimes twice from one tip; into a pool they plop; off a sulfur
 *   spike into acid they sizzle;</li>
 *   <li><b>far water</b>: a stream heard toward the real water 8 to 32 blocks off (nearer, vanilla's own water speaks);</li>
 *   <li><b>stone settling</b> on real rock round you, rare and deep;</li>
 *   <li><b>a breath of air</b> where a passage leads out (sky light reaching in while you stand in the dark): a loop at
 *   that spot, breathing; the deeper, the lower its tone;</li>
 *   <li>sulfur caves' own: gas hissing from potent sulfur, acid pools bubbling.</li>
 * </ul>
 * The deeper, the fewer events. Everything follows the Conductor's {@link Soundscape#natureLevel()} (a gate's hush,
 * combat or Lord music, a stage cue, a seep's muffle: fewer and quieter) and counts in its voice budget (at most
 * {@value #ONE_SHOTS} one-shots and two loops of its own). Nothing allocated per tick but the sound instances.
 */
final class CaveSound {
    private CaveSound() {}

    static final int READS = 10, ONE_SHOTS = 3;
    private static final int UP = 14, DOWN_NEAR = 10, DOWN_FAR = 14, FORGET = 600, REACH = 34;
    private static final int PICK = 0, SCAN_UP = 1, SCAN_DOWN = 2;
    private static final int ROOF_NONE = 0, ROOF_TIP = 1, ROOF_WET = 2, ROOF_DAMP = 3, ROOF_ACID = 4;
    /** Drip aux bits: lands in water, off a sulfur spike, from a tip (else a wet roof), heard from the roof (no floor). */
    private static final int INTO_WATER = 1, ACID = 2, TIP = 4, FROM_ROOF = 8;

    private static final RandomSource R = RandomSource.create();
    private static final BlockPos.MutableBlockPos P = new BlockPos.MutableBlockPos();
    private static final Ring DRIPS = new Ring(8), ROCK = new Ring(8), WATER = new Ring(6), VENTS = new Ring(4), ACID_POOLS = new Ring(4);

    // the probe's column
    private static int phase, cx, cz, cy, scan, roof, roofY, probes;
    private static boolean far, wasActive;
    // where daylight reaches in: the brightest sky-lit air seen lately
    private static long skyPos;
    private static int skyLight;
    private static float skyScore;
    private static long skySeen = Long.MIN_VALUE / 2;

    // one-shots playing, a drip waiting to fall again from the same tip, the lulls
    private static final SoundInstance[] PLAYING = new SoundInstance[ONE_SHOTS];
    private static long repeatAt = -1, repeatPos;
    private static int repeatAux;
    private static long lullUntil, nextLull = Long.MIN_VALUE;
    private static int newThisSecond;
    private static long second;
    private static String last = "none yet";
    // the loops: the breath at the way out and the far water, each with the one fading out after a move
    private static final SenseLoop[] BREATH = new SenseLoop[2], STREAM = new SenseLoop[2];

    // ---- the tick ------------------------------------------------------------------------------------------------------

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (!SenseState.active || level == null) {
            if (wasActive) reset();
            wasActive = false;
            return;
        }
        wasActive = true;
        long now = SenseState.tick;
        if (SenseState.under > 0.05F) probe(level, now);
        float nature = Soundscape.natureLevel();
        SoundManager sm = mc.getSoundManager();
        for (int i = 0; i < ONE_SHOTS; i++) if (PLAYING[i] != null && (now & 7) == 0 && !sm.isActive(PLAYING[i])) PLAYING[i] = null;
        if (now / 20 != second) {
            second = now / 20;
            newThisSecond = 0;
        }
        events(mc, now, nature);
        loops(mc, now, nature);
    }

    // ---- the probe -----------------------------------------------------------------------------------------------------

    /** One step of the column walk, at most {@value #READS} reads: pick a column, up to its roof, down to its floor. */
    private static void probe(ClientLevel level, long now) {
        int budget = READS;
        boolean lush = SenseState.biome == SenseState.LUSH, sulfur = SenseState.biome == SenseState.SULFUR;
        while (budget > 0) {
            if (phase == PICK) {
                // two near columns (drips, the floor under them) for every far one (water, rock, the way out)
                far = probes++ % 3 == 2;
                float a = R.nextFloat() * Mth.TWO_PI, r = far ? 12F + 18F * R.nextFloat() : 1.5F + 10.5F * R.nextFloat();
                cx = Mth.floor(SenseState.x + Mth.cos(a) * r);
                cz = Mth.floor(SenseState.z + Mth.sin(a) * r);
                cy = Mth.floor(SenseState.y);
                BlockState s = level.getBlockState(P.set(cx, cy, cz));
                budget--;
                FluidState f = s.getFluidState();
                if (!f.isEmpty()) {
                    if (f.is(FluidTags.WATER)) water(level, now, sulfur);
                    continue;
                }
                if (!open(s)) {
                    if (rock(s)) ROCK.put(P.asLong(), 0, now);
                    continue;
                }
                int light = level.getBrightness(LightLayer.SKY, P);
                budget--;
                if (light >= 9) {
                    // the way out: the brightest sky-lit air, nearer counting for more
                    float score = light - dist(cx + 0.5, cy + 0.5, cz + 0.5) / 6F;
                    if (score > skyScore || now - skySeen > 200 || P.asLong() == skyPos) {
                        skyPos = P.asLong();
                        skyLight = light;
                        skyScore = score;
                        skySeen = now;
                    }
                } else if (P.asLong() == skyPos) {
                    skyLight = light; // the way out was closed
                }
                roof = ROOF_NONE;
                phase = far ? SCAN_DOWN : SCAN_UP;
                scan = far ? cy - 1 : cy + 1;
            } else if (phase == SCAN_UP) {
                BlockState s = level.getBlockState(P.set(cx, scan, cz));
                budget--;
                if (s.getBlock() instanceof SpeleothemBlock && s.getValue(SpeleothemBlock.TIP_DIRECTION) == Direction.DOWN) {
                    roof = s.is(Blocks.SULFUR_SPIKE) ? ROOF_ACID : ROOF_TIP;
                } else if (open(s)) {
                    if (++scan - cy > UP) {
                        phase = SCAN_DOWN;
                        scan = cy - 1;
                    }
                    continue;
                } else {
                    BlockState above = level.getBlockState(P.set(cx, scan + 1, cz));
                    budget--;
                    if (above.getFluidState().is(FluidTags.WATER)) roof = ROOF_WET;
                    else if (lush && (s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.CLAY) || s.is(Blocks.ROOTED_DIRT))) roof = ROOF_DAMP;
                    if (rock(s)) ROCK.put(P.set(cx, scan, cz).asLong(), 0, now);
                }
                roofY = scan;
                phase = SCAN_DOWN;
                scan = cy - 1;
            } else {
                BlockState s = level.getBlockState(P.set(cx, scan, cz));
                budget--;
                FluidState f = s.getFluidState();
                if (f.is(FluidTags.WATER)) {
                    water(level, now, sulfur);
                    land(scan, true, now);
                    phase = PICK;
                } else if (open(s)) {
                    if (cy - --scan > (far ? DOWN_FAR : DOWN_NEAR)) {
                        land(Integer.MIN_VALUE, false, now);
                        phase = PICK;
                    }
                } else {
                    if (s.is(Blocks.POTENT_SULFUR)) VENTS.put(P.asLong(), 0, now);
                    else if (far && rock(s)) ROCK.put(P.asLong(), 0, now);
                    land(scan + 1, false, now);
                    phase = PICK;
                }
            }
        }
    }

    /** Water at P: a pool or a stream (in sulfur caves, acid). */
    private static void water(ClientLevel level, long now, boolean sulfur) {
        // the surface: up while the block above is still water (a few reads at most; pools are shallow)
        int top = P.getY();
        for (int k = 0; k < 3 && level.getFluidState(P.set(cx, top + 1, cz)).is(FluidTags.WATER); k++) top++;
        P.set(cx, top, cz);
        WATER.put(P.asLong(), level.getFluidState(P).isSource() ? 0 : 1, now);
        if (sulfur) ACID_POOLS.put(P.asLong(), 0, now);
    }

    /** The column's roof drips (if it does) onto this floor ({@code y}: the air over it, MIN_VALUE: none found). */
    private static void land(int y, boolean water, long now) {
        if (roof == ROOF_NONE || roof == ROOF_DAMP && R.nextFloat() < 0.6F) return;
        int aux = (water ? INTO_WATER : 0) | (roof == ROOF_ACID ? ACID : 0) | (roof == ROOF_TIP || roof == ROOF_ACID ? TIP : 0);
        if (y == Integer.MIN_VALUE) DRIPS.put(BlockPos.asLong(cx, roofY - 1, cz), aux | FROM_ROOF, now);
        else DRIPS.put(BlockPos.asLong(cx, y, cz), aux, now);
    }

    private static boolean open(BlockState s) {
        return s.isAir() || !s.isSolid() && s.getFluidState().isEmpty();
    }

    private static boolean rock(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.DRIPSTONE_BLOCK) || s.is(Blocks.CALCITE)
                || s.is(Blocks.SMOOTH_BASALT) || s.is(Blocks.SULFUR) || s.is(Blocks.CINNABAR);
    }

    // ---- the events ----------------------------------------------------------------------------------------------------

    private static void events(Minecraft mc, long now, float nature) {
        float under = SenseState.under;
        // now and then the whole cave goes still for half a minute: silence is part of it (never in your first 90
        // seconds under the ground)
        if (nextLull == Long.MIN_VALUE) nextLull = now + 1800;
        if (under < 0.05F || SenseState.underwater) {
            nextLull = Math.max(nextLull, now + 1800);
            return;
        }
        if (now >= nextLull) {
            lullUntil = now + 300 + R.nextInt(400);
            nextLull = lullUntil + 2400 + R.nextInt(2400);
        }
        float lull = now < lullUntil ? 0.15F : 1F;
        float keep = under * nature * lull;
        float deeper = 1F - 0.45F * SenseState.depth;
        int biome = SenseState.biome;
        float place = switch (biome) {
            case SenseState.DRIPSTONE -> 1.3F;
            case SenseState.LUSH -> 1.1F;
            case SenseState.DEEP_DARK -> 0.35F; // near silence: the place holds its breath
            default -> 1F;
        };
        // drips: 6.5 a minute from one tip, up to 14 from four or more
        int drips = DRIPS.count(now);
        if (drips > 0 && chance((4F + 2.5F * Math.min(drips, 4)) * place * deeper * keep)) {
            int i = DRIPS.pick(R, now, 0, 18);
            if (i >= 0) {
                drip(mc, DRIPS.pos[i], DRIPS.aux[i], nature);
                // water gathers and falls again from the same tip a moment later
                if (R.nextFloat() < 0.4F) {
                    repeatAt = now + 24 + R.nextInt(46);
                    repeatPos = DRIPS.pos[i];
                    repeatAux = DRIPS.aux[i];
                }
            }
        }
        if (repeatAt >= 0 && now >= repeatAt) {
            repeatAt = -1;
            if (keep > 0.2F) drip(mc, repeatPos, repeatAux, nature);
        }
        // stone settling: rare, deep, on real rock 6 to 26 away
        if (ROCK.count(now) > 0 && chance(0.45F * (biome == SenseState.DEEP_DARK ? 0.5F : 1F) * (1F - 0.25F * SenseState.depth) * keep)) {
            int i = ROCK.pick(R, now, 6, 26);
            if (i >= 0) {
                float pitch = (0.92F + 0.12F * R.nextFloat()) * (1F - 0.14F * SenseState.depth);
                playOff(mc, SenseModule.SETTLE.get(), ROCK.pos[i], 0.4F + 0.3F * R.nextFloat(), pitch, 16F, nature, 0.7);
            }
        }
        if (biome == SenseState.SULFUR) {
            if (VENTS.count(now) > 0 && chance(2.5F * keep)) {
                int i = VENTS.pick(R, now, 0, 22);
                if (i >= 0) playAt(mc, SenseModule.SULFUR_VENT.get(), VENTS.pos[i], 0.5, 1.15, 0.5,
                        0.4F + 0.3F * R.nextFloat(), 0.9F + 0.2F * R.nextFloat(), 12F, nature);
            }
            if (ACID_POOLS.count(now) > 0 && chance(3.5F * keep)) {
                int i = ACID_POOLS.pick(R, now, 0, 20);
                if (i >= 0) playAt(mc, SenseModule.SULFUR_BUBBLE.get(), ACID_POOLS.pos[i], 0.2 + 0.6 * R.nextFloat(), 0.9,
                        0.2 + 0.6 * R.nextFloat(), 0.45F + 0.35F * R.nextFloat(), 0.85F + 0.3F * R.nextFloat(), 12F, nature);
            }
        }
    }

    private static boolean chance(float perMinute) {
        return R.nextFloat() < perMinute / 1200F;
    }

    private static void drip(Minecraft mc, long pos, int aux, float nature) {
        boolean water = (aux & INTO_WATER) != 0, acid = (aux & ACID) != 0;
        SoundEvent e = acid ? SenseModule.DRIP_ACID.get() : water ? SenseModule.DRIP_POOL.get() : SenseModule.DRIP.get();
        double up = (aux & FROM_ROOF) != 0 ? 0.6 : water ? 0.88 : 0.05;
        float vol = (water ? 0.5F : 0.45F) + 0.35F * R.nextFloat();
        float pitch = water ? 0.92F + 0.16F * R.nextFloat() : 0.9F + 0.22F * R.nextFloat();
        playAt(mc, e, pos, 0.3 + 0.4 * R.nextFloat(), up, 0.3 + 0.4 * R.nextFloat(), vol, pitch, 12F, nature);
    }

    /** A one-shot on a block's face toward the listener (a sound inside the rock would be wholly occluded). */
    private static void playOff(Minecraft mc, SoundEvent e, long pos, float vol, float pitch, float near, float nature, double out) {
        int bx = BlockPos.getX(pos), by = BlockPos.getY(pos), bz = BlockPos.getZ(pos);
        double dx = SenseState.x - (bx + 0.5), dy = SenseState.y - (by + 0.5), dz = SenseState.z - (bz + 0.5);
        double len = Math.max(1e-3, Math.sqrt(dx * dx + dy * dy + dz * dz));
        play(mc, e, bx + 0.5 + dx / len * out, by + 0.5 + dy / len * out, bz + 0.5 + dz / len * out, vol, pitch, near, nature);
    }

    private static void playAt(Minecraft mc, SoundEvent e, long pos, double ox, double oy, double oz, float vol, float pitch, float near, float nature) {
        play(mc, e, BlockPos.getX(pos) + ox, BlockPos.getY(pos) + oy, BlockPos.getZ(pos) + oz, vol, pitch, near, nature);
    }

    /** Plays a one-shot at a point: the Conductor's distance law ({@code near / (near + d)}), within the voice cap. */
    private static void play(Minecraft mc, SoundEvent e, double x, double y, double z, float vol, float pitch, float near, float nature) {
        if (newThisSecond >= 2) return;
        int free = -1;
        for (int i = 0; i < ONE_SHOTS; i++) if (PLAYING[i] == null) free = i;
        if (free < 0) return;
        double dx = x - SenseState.x, dy = y - SenseState.y, dz = z - SenseState.z;
        float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float v = vol * near / (near + d) * (0.5F + 0.5F * nature) * SenseState.under;
        if (v < 0.02F) return;
        SimpleSoundInstance s = new SimpleSoundInstance(e.location(), SoundSource.AMBIENT, v, pitch, SoundInstance.createUnseededRandom(),
                false, 0, SoundInstance.Attenuation.NONE, x, y, z, false);
        mc.getSoundManager().play(s);
        PLAYING[free] = s;
        newThisSecond++;
        last = String.format(Locale.ROOT, "%s at %.0f m", e.location().getPath(), d);
    }

    // ---- the loops -----------------------------------------------------------------------------------------------------

    private static void loops(Minecraft mc, long now, float nature) {
        float under = SenseState.under;
        boolean on = under > 0.05F && !SenseState.underwater;
        float bed = 0.5F + 0.5F * nature;
        // the breath: daylight's sky light reaching in near you while you stand in the dark
        float breath = 0F;
        double bxp = 0, byp = 0, bzp = 0;
        if (on && now - skySeen < 400 && skyLight >= 9 && skyLight - SenseState.sky >= 4) {
            bxp = BlockPos.getX(skyPos) + 0.5;
            byp = BlockPos.getY(skyPos) + 0.5;
            bzp = BlockPos.getZ(skyPos) + 0.5;
            float d = dist(bxp, byp, bzp);
            if (d > 3F && d < 32F) {
                float lift = Mth.clamp((skyLight - SenseState.sky - 3F) / 6F, 0F, 1F);
                breath = 0.55F * under * lift * 10F / (10F + d) * bed * (1F - 0.6F * fade(d, 24F, 32F));
            }
        }
        drive(mc, BREATH, SenseModule.BREATH.get(), breath, bxp, byp, bzp, 1F - 0.22F * SenseState.depth, 0.03F);
        // far water: toward the nearest water 8 to 32 off; nearer, vanilla's own water is heard
        float stream = 0F;
        double wx = 0, wy = 0, wz = 0;
        int w = on ? WATER.nearest(now, 6, 32) : -1;
        if (w >= 0) {
            wx = BlockPos.getX(WATER.pos[w]) + 0.5;
            wy = BlockPos.getY(WATER.pos[w]) + 0.9;
            wz = BlockPos.getZ(WATER.pos[w]) + 0.5;
            float d = dist(wx, wy, wz);
            float flow = WATER.aux[w] == 1 ? 1F : 0.6F; // a still pool laps more quietly than a running stream
            stream = 0.5F * under * flow * fade(d, 5F, 10F) * (1F - fade(d, 26F, 33F)) * 14F / (14F + d) * bed;
        }
        drive(mc, STREAM, SenseModule.FAR_WATER.get(), stream, wx, wy, wz, 1F, 0.02F);
    }

    /** A placed loop: its goal this tick, a new one at a spot that moved more than six blocks while the old fades out. */
    private static void drive(Minecraft mc, SenseLoop[] pair, SoundEvent e, float goal, double x, double y, double z, float pitch, float ease) {
        SoundManager sm = mc.getSoundManager();
        for (int i = 0; i < 2; i++) {
            if (pair[i] != null && (pair[i].isStopped() || pair[i].age > 40 && !sm.isActive(pair[i]))) pair[i] = null;
        }
        SenseLoop cur = pair[0];
        if (cur != null) {
            double dx = cur.getX() - x, dy = cur.getY() - y, dz = cur.getZ() - z;
            if (goal > 0.01F && dx * dx + dy * dy + dz * dz > 36 && pair[1] == null) {
                cur.release(); // moved: this one fades while a new one rises at the new spot
                pair[1] = cur;
                pair[0] = cur = null;
            } else {
                cur.goal = goal;
                cur.pitchGoal = pitch;
            }
        }
        if (cur == null && goal > 0.015F) {
            SenseLoop l = new SenseLoop(e, false, x, y, z, pitch, ease);
            l.goal = goal;
            pair[0] = l;
            sm.play(l);
        }
    }

    private static float dist(double x, double y, double z) {
        double dx = x - SenseState.x, dy = y - SenseState.y, dz = z - SenseState.z;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static float fade(float d, float from, float to) {
        float t = Mth.clamp((d - from) / (to - from), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    // ---- the Conductor, status, reset ----------------------------------------------------------------------------------

    /** Voices held now (one-shots and loops), for the Conductor's budget. */
    static int voices() {
        int n = 0;
        for (SoundInstance s : PLAYING) if (s != null) n++;
        for (SenseLoop l : BREATH) if (l != null && !l.isStopped()) n++;
        for (SenseLoop l : STREAM) if (l != null && !l.isStopped()) n++;
        return n;
    }

    static String status(long now) {
        return String.format(Locale.ROOT, "spots: %d drips, %d rock, %d water, %d vents, %d acid; way out %s; breath %.2f, far water %.2f; last %s%s",
                DRIPS.count(now), ROCK.count(now), WATER.count(now), VENTS.count(now), ACID_POOLS.count(now),
                now - skySeen < 400 && skyLight >= 9 ? String.format(Locale.ROOT, "%.0f m (sky %d)", dist(BlockPos.getX(skyPos) + 0.5,
                        BlockPos.getY(skyPos) + 0.5, BlockPos.getZ(skyPos) + 0.5), skyLight) : "none",
                level(BREATH), level(STREAM), last, now < lullUntil ? " (a lull)" : "");
    }

    private static float level(SenseLoop[] pair) {
        return pair[0] == null ? 0F : pair[0].level;
    }

    static void reset() {
        for (int i = 0; i < 2; i++) {
            if (BREATH[i] != null) BREATH[i].end();
            if (STREAM[i] != null) STREAM[i].end();
            BREATH[i] = STREAM[i] = null;
        }
        java.util.Arrays.fill(PLAYING, null);
        DRIPS.clear();
        ROCK.clear();
        WATER.clear();
        VENTS.clear();
        ACID_POOLS.clear();
        skySeen = Long.MIN_VALUE / 2;
        skyLight = 0;
        phase = PICK;
        repeatAt = -1;
        lullUntil = 0;
        nextLull = Long.MIN_VALUE;
    }

    /** A few remembered spots of one kind: forgotten after 30 s or beyond {@value #REACH} blocks. */
    private static final class Ring {
        final long[] pos;
        final int[] aux;
        final long[] seen;
        int next;

        Ring(int n) {
            pos = new long[n];
            aux = new int[n];
            seen = new long[n];
            clear();
        }

        void put(long p, int a, long now) {
            for (int i = 0; i < pos.length; i++) {
                if (pos[i] == p) {
                    aux[i] = a;
                    seen[i] = now;
                    return;
                }
            }
            // a free or stale slot first, else the oldest in turn
            for (int i = 0; i < pos.length; i++) {
                if (!valid(i, now)) {
                    next = i;
                    break;
                }
            }
            pos[next] = p;
            aux[next] = a;
            seen[next] = now;
            next = (next + 1) % pos.length;
        }

        boolean valid(int i, long now) {
            if (now - seen[i] > FORGET) return false;
            double dx = BlockPos.getX(pos[i]) + 0.5 - SenseState.x, dy = BlockPos.getY(pos[i]) + 0.5 - SenseState.y,
                    dz = BlockPos.getZ(pos[i]) + 0.5 - SenseState.z;
            return dx * dx + dy * dy + dz * dz < REACH * REACH;
        }

        int count(long now) {
            int n = 0;
            for (int i = 0; i < pos.length; i++) if (valid(i, now)) n++;
            return n;
        }

        /** A random remembered spot between {@code min} and {@code max} blocks off, or -1. */
        int pick(RandomSource r, long now, float min, float max) {
            int start = r.nextInt(pos.length);
            for (int k = 0; k < pos.length; k++) {
                int i = (start + k) % pos.length;
                if (!valid(i, now)) continue;
                float d = dist(BlockPos.getX(pos[i]) + 0.5, BlockPos.getY(pos[i]) + 0.5, BlockPos.getZ(pos[i]) + 0.5);
                if (d >= min && d <= max) return i;
            }
            return -1;
        }

        /** The nearest remembered spot between {@code min} and {@code max} blocks off, or -1. */
        int nearest(long now, float min, float max) {
            int best = -1;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < pos.length; i++) {
                if (!valid(i, now)) continue;
                float d = dist(BlockPos.getX(pos[i]) + 0.5, BlockPos.getY(pos[i]) + 0.5, BlockPos.getZ(pos[i]) + 0.5);
                if (d >= min && d <= max && d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            return best;
        }

        void clear() {
            java.util.Arrays.fill(seen, Long.MIN_VALUE / 2);
            java.util.Arrays.fill(pos, Long.MIN_VALUE);
            next = 0;
        }
    }
}
