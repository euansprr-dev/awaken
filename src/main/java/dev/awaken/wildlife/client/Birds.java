package dev.awaken.wildlife.client;

import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.wildlife.WildCritter;
import dev.awaken.wildlife.WildKinds;
import dev.awaken.wildlife.WildlifeModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import static dev.awaken.wildlife.client.Wildlife.clip;
import static dev.awaken.wildlife.client.Wildlife.dist2;
import static dev.awaken.wildlife.client.Wildlife.face;
import static dev.awaken.wildlife.client.Wildlife.now;
import static dev.awaken.wildlife.client.Wildlife.px;
import static dev.awaken.wildlife.client.Wildlife.pz;
import static dev.awaken.wildlife.client.Wildlife.threat;

/**
 * The wild's birds (SPEC "Wild life"). Songbirds on the canopy's edge and on fences, never still: glances, tail flicks,
 * a song from the top of a branch (three times as often in the dawn chorus), short hops along the perch, a flit to the
 * next tree in a bounding flight, down to the ground to forage and back up. A woodpecker hitching up a trunk, pecking,
 * drumming, slipping round to the far side as you come near. Crows striding the open ground; an owl on a branch at night
 * turning its head to follow you, hooting, gliding off without a sound; a buzzard circling high over open land on a
 * thermal, a kestrel hanging still in the wind over a meadow; gulls riding the air over the shore; ducks paddling,
 * dabbling tail up; a heron like a statue at the water's edge, stalking, striking.
 * <p>They flush when you run at them or come too close (away from you, a flutter of wings), go quiet and leave where the
 * world hushes (a predator, an open gate), keep under the leaves and fluffed up in rain, and leave in the evening.
 */
final class Birds {
    private Birds() {}

    static final int PERCH = 0, GROUND = 1, FLY = 2, LAND = 3, HOP = 4, CLING = 5, SOAR = 6, HOVER = 7, STOOP = 8, SWIM = 9,
            STAND = 10, STALK = 11;

    static boolean airborne(WildCritter c) {
        return c.state == FLY || c.state == LAND || c.state == SOAR || c.state == HOVER || c.state == STOOP;
    }

    // ---- where each is made ----------------------------------------------------------------------------------------

    static boolean place(WildCritter c, ClientLevel level, Sites.Site s, int slot, RandomSource r) {
        float draw = dev.awaken.wildlife.Density.draw(s.key, c.kind, slot + 1);
        switch (c.kind) {
            case WildKinds.SONGBIRD, WildKinds.OWL -> {
                if (s.nPerch == 0) return false;
                int i = (int) (draw * s.nPerch);
                // the lowest of three of the chunk's perches: a bird on the canopy's roof is a bird nobody sees
                for (int t = 2; t <= 3; t++) {
                    int j = (int) (dev.awaken.wildlife.Density.draw(s.key, c.kind, slot * 7 + t + 40) * s.nPerch);
                    if (s.perchLift[j] < s.perchLift[i] && (c.kind == WildKinds.SONGBIRD || s.perchKind[j] < Sites.FENCE)) i = j;
                }
                // by day many songbirds are down foraging on the forest floor, under the trees and at their edge
                boolean day = Wildlife.tod < 11800 || Wildlife.tod > 22600;
                if (c.kind == WildKinds.SONGBIRD && s.h.canopy > 0.2F && day && r.nextFloat() < 0.45F) {
                    long p = s.perch[i];
                    double gx = BlockPos.getX(p) + 0.5 + (r.nextFloat() - 0.5) * 4, gz = BlockPos.getZ(p) + 0.5 + (r.nextFloat() - 0.5) * 4;
                    double g = Wildlife.ground(level, gx, gz);
                    if (!Double.isNaN(g) && level.getFluidState(BlockPos.containing(gx, g - 1, gz)).isEmpty()
                            && level.getBlockState(BlockPos.containing(gx, g, gz)).getCollisionShape(level, BlockPos.containing(gx, g, gz)).isEmpty()) {
                        c.setPos(gx, g, gz);
                        c.setYRot(r.nextFloat() * 360F);
                        settle(c, GROUND, r);
                        return true;
                    }
                }
                // owls keep to the trees; a songbird takes a fence or a rock as readily
                if (c.kind == WildKinds.OWL && s.perchKind[i] >= Sites.FENCE) return false;
                long p = s.perch[i];
                c.setPos(BlockPos.getX(p) + 0.5 + (r.nextFloat() - 0.5) * 0.5, BlockPos.getY(p) + (s.perchKind[i] == Sites.FENCE ? 0.5 : 0),
                        BlockPos.getZ(p) + 0.5 + (r.nextFloat() - 0.5) * 0.5);
                c.setYRot(r.nextFloat() * 360F);
                settle(c, PERCH, r);
            }
            case WildKinds.WOODPECKER -> {
                if (s.nTrunk == 0) return false;
                int i = (int) (draw * s.nTrunk);
                if (!onTrunk(c, level, s.trunk[i], s.trunkHeight[i], 1.5 + r.nextFloat() * Math.min(5, s.trunkHeight[i] - 2), r.nextInt(4))) return false;
                settle(c, CLING, r);
            }
            case WildKinds.CROW -> {
                if (s.nGround == 0) return false;
                long p = s.ground[(int) (draw * s.nGround)];
                c.setPos(BlockPos.getX(p) + 0.5, BlockPos.getY(p), BlockPos.getZ(p) + 0.5);
                c.setYRot(r.nextFloat() * 360F);
                settle(c, GROUND, r);
            }
            case WildKinds.RAPTOR, WildKinds.GULL -> {
                boolean gull = c.kind == WildKinds.GULL;
                c.ax = s.centreX() + (r.nextFloat() - 0.5) * 12;
                c.az = s.centreZ() + (r.nextFloat() - 0.5) * 12;
                double base = gull ? waterOrGround(level, c.ax, c.az) : s.groundY;
                if (Double.isNaN(base)) return false;
                c.ty = base + (gull ? 7 + r.nextFloat() * 8 : 24 + r.nextFloat() * 16);
                c.tx = (gull ? 9 : 14) + r.nextFloat() * (gull ? 8 : 12); // the circle's radius
                c.face = r.nextFloat() * Mth.TWO_PI;                       // the angle round it
                c.setPos(c.ax + Math.cos(c.face) * c.tx, c.ty, c.az + Math.sin(c.face) * c.tx);
                c.state = SOAR;
                c.timer = 200 + r.nextInt(400);
                clip(c, gull ? "glide" : "soar");
            }
            case WildKinds.DUCK -> {
                if (s.nWater == 0) return false;
                long p = s.water[(int) (draw * s.nWater)];
                if (!still(level, BlockPos.getX(p), BlockPos.getY(p), BlockPos.getZ(p))) return false;
                c.ax = BlockPos.getX(p) + 0.5;
                c.az = BlockPos.getZ(p) + 0.5;
                c.ty = BlockPos.getY(p) + 0.88;
                c.sink = 0.16F;
                c.tx = c.ax;
                c.tz = c.az;
                c.setPos(c.ax, c.ty, c.az);
                c.setYRot(r.nextFloat() * 360F);
                settle(c, SWIM, r);
            }
            case WildKinds.HERON -> {
                if (s.nShore == 0) return false;
                int i = (int) (draw * s.nShore);
                long p = s.shore[i];
                c.setPos(BlockPos.getX(p) + 0.5, BlockPos.getY(p), BlockPos.getZ(p) + 0.5);
                c.setYRot(s.shoreDir[i] * 90F - 90F);
                c.ax = c.getX();
                c.az = c.getZ();
                settle(c, STAND, r);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Settles into a resting state with a fresh timer and its idle clip. */
    private static void settle(WildCritter c, int state, RandomSource r) {
        c.state = state;
        c.since = now;
        c.timer = 20 + r.nextInt(80);
        c.vx = c.vy = c.vz = 0;
        c.roll = c.pitch = 0F;
        clip(c, switch (state) {
            case CLING -> "cling";
            case SWIM -> "swim";
            case STAND -> c.kind == WildKinds.HERON ? "stand" : "idle";
            default -> "idle";
        });
    }

    // ---- the brain ---------------------------------------------------------------------------------------------------

    static void think(WildCritter c, ClientLevel level) {
        RandomSource r = c.getRandom();
        if (c.leaving && c.state != FLY) {
            if (c.state == SOAR) c.timer = Math.min(c.timer, 1);
            else leave(c, r, level);
        }
        switch (c.state) {
            case PERCH, GROUND -> rest(c, level, r);
            case HOP -> hop(c, level);
            case FLY -> fly(c, level, r);
            case LAND -> land(c, level, r);
            case CLING -> cling(c, level, r);
            case SOAR -> soar(c, level, r);
            case HOVER, STOOP -> hover(c, level, r);
            case SWIM -> swim(c, level, r);
            case STAND, STALK -> heron(c, level, r);
            default -> c.discard();
        }
    }

    /** Whether something should send it off: you, a predator, a hush. */
    private static boolean startled(WildCritter c, double near, double run) {
        if (threat(c, near, run)) return true;
        if (WildSound.hostileNear(c.getX(), c.getY(), c.getZ(), near + 4)) return true;
        return c.tickCount % 20 == 0 && Soundscape.hushAt(c.getX(), c.getY(), c.getZ()) < 0.35F;
    }

    private static double near(int kind) {
        return switch (kind) {
            case WildKinds.SONGBIRD -> 3.2;
            case WildKinds.CROW -> 10;
            case WildKinds.OWL -> 6;
            case WildKinds.DUCK -> 4;
            default -> 5;
        };
    }

    /** A songbird, crow or owl resting on a perch or the ground. */
    private static void rest(WildCritter c, ClientLevel level, RandomSource r) {
        boolean song = c.kind == WildKinds.SONGBIRD, owl = c.kind == WildKinds.OWL, ground = c.state == GROUND;
        if (startled(c, near(c.kind), c.kind == WildKinds.CROW ? 16 : owl ? 10 : 9)) {
            flush(c, level, r);
            return;
        }
        if (owl || c.kind == WildKinds.CROW) Wildlife.lookAtPlayer(c, owl ? 22 : 14);
        else if (dist2(c) < 7 * 7 && r.nextInt(30) == 0) Wildlife.lookAtPlayer(c, 7);
        else if (r.nextInt(40) == 0) c.lookX = Double.NaN;
        if (--c.timer > 0) {
            if (ground && Wildlife.playing(c, "walk") && walkTo(c, level, c.tx, c.tz, 0.05)) c.timer = 0;
            return;
        }
        boolean wet = Wildlife.rain > 0.3F;
        float roll = r.nextFloat();
        if (song) {
            if (wet) {
                // sheltering: puffed up under the leaves, now and then a shift along the branch
                clip(c, "fluff");
                c.timer = 120 + r.nextInt(200);
                if (roll < 0.15F) shuffle(c, level, r);
                return;
            }
            if (ground) {
                if (roll < 0.45F) {
                    hopAlong(c, level, r);
                } else if (roll < 0.8F) {
                    Wildlife.replay(c, "peck");
                    c.timer = 22 + r.nextInt(30);
                } else if (roll < 0.93F || now - c.since < 300) {
                    clip(c, "idle");
                    c.timer = 20 + r.nextInt(40);
                    if (r.nextInt(5) == 0) WildSound.play(c, WildlifeModule.CHIP.get(), 0.4F, 0.9F + r.nextFloat() * 0.3F);
                } else {
                    upToPerch(c, level, r, 8);
                }
                return;
            }
            float sing = WildSound.songChance(c);
            if (roll < sing) {
                Wildlife.replay(c, "sing");
                c.timer = 40 + r.nextInt(20);
                WildSound.song(c);
            } else if (roll < sing + 0.18F) {
                Wildlife.replay(c, "preen");
                c.timer = 40;
            } else if (roll < sing + 0.38F) {
                shuffle(c, level, r);
            } else if (roll < sing + 0.58F) {
                if (!downToForage(c, level, r)) flit(c, level, r, 4, 9);
            } else if (roll < sing + 0.64F) {
                flit(c, level, r, 4, 9);
            } else if (roll < sing + 0.68F) {
                flit(c, level, r, 10, 18);
            } else {
                clip(c, "idle");
                c.timer = 25 + r.nextInt(60);
                if (r.nextInt(4) == 0) WildSound.play(c, WildlifeModule.CHIP.get(), 0.5F, 0.9F + r.nextFloat() * 0.3F);
            }
            return;
        }
        if (owl) {
            if (roll < WildSound.hootChance(c)) {
                Wildlife.replay(c, "hoot");
                c.timer = 60 + r.nextInt(60);
                WildSound.play(c, WildlifeModule.HOOT.get(), 0.9F, 0.95F + r.nextFloat() * 0.1F);
            } else if (roll < 0.9F) {
                clip(c, wet ? "fluff" : "idle");
                c.timer = 80 + r.nextInt(160);
            } else {
                flit(c, level, r, 10, 22);
            }
            return;
        }
        // a crow on the ground: stride about, peck, caw; up into a tree now and then
        if (roll < 0.4F) {
            double a = r.nextDouble() * Math.PI * 2, d = 2 + r.nextDouble() * 4;
            c.tx = c.getX() + Math.cos(a) * d;
            c.tz = c.getZ() + Math.sin(a) * d;
            clip(c, "walk");
            c.timer = 100;
        } else if (roll < 0.7F) {
            Wildlife.replay(c, "peck");
            c.timer = 28 + r.nextInt(40);
        } else if (roll < 0.8F) {
            Wildlife.replay(c, "caw");
            c.timer = 34;
            WildSound.play(c, WildlifeModule.CAW.get(), 0.9F, 0.9F + r.nextFloat() * 0.2F);
        } else if (roll < 0.95F || ground) {
            clip(c, "idle");
            c.timer = 30 + r.nextInt(60);
            if (ground && roll > 0.97F) flit(c, level, r, 12, 24);
        } else {
            flit(c, level, r, 12, 24);
        }
    }

    // ---- small movements -------------------------------------------------------------------------------------------

    /** A little hop along the branch or the ground (a third to half a block), on to something solid. */
    private static void shuffle(WildCritter c, ClientLevel level, RandomSource r) {
        double a = r.nextDouble() * Math.PI * 2, d = 0.3 + r.nextDouble() * 0.4;
        double nx = c.getX() + Math.cos(a) * d, nz = c.getZ() + Math.sin(a) * d;
        double ny = Wildlife.top(level, nx, nz);
        if (Double.isNaN(ny) || Math.abs(ny - c.getY()) > 0.6 || !perchable(level, nx, ny - 1, nz)) {
            Wildlife.replay(c, "hop");
            c.timer = 10;
            return;
        }
        startHop(c, nx, ny, nz);
    }

    private static void hopAlong(WildCritter c, ClientLevel level, RandomSource r) {
        float a = (c.getYRot() + 90F + (r.nextFloat() - 0.5F) * 140F) * Mth.DEG_TO_RAD;
        double d = 0.3 + r.nextDouble() * 0.3;
        double nx = c.getX() + Math.cos(a) * d, nz = c.getZ() + Math.sin(a) * d;
        double ny = Wildlife.ground(level, nx, nz);
        if (Double.isNaN(ny) || Math.abs(ny - c.getY()) > 0.6) {
            c.timer = 10;
            return;
        }
        startHop(c, nx, ny, nz);
    }

    private static void startHop(WildCritter c, double nx, double ny, double nz) {
        face(c, nx - c.getX(), nz - c.getZ(), 180F);
        c.tx = nx;
        c.ty = ny;
        c.tz = nz;
        c.next = c.state;
        c.state = HOP;
        c.timer = 6;
        Wildlife.replay(c, "hop");
    }

    private static void hop(WildCritter c, ClientLevel level) {
        int t = 6 - c.timer;
        double f = Math.min(1.0, (t + 1) / 6.0);
        double arc = Math.sin(f * Math.PI) * 0.15;
        double x = Mth.lerp(f, c.xo, c.tx), z = Mth.lerp(f, c.zo, c.tz);
        double base = c.timer == 6 ? c.getY() : Mth.lerp(f, c.getY(), c.ty);
        c.setPos(Mth.lerp(0.4, c.getX(), x), base + arc * 0.2, Mth.lerp(0.4, c.getZ(), z));
        c.speed = 0.06F;
        if (--c.timer <= 0) {
            c.setPos(c.tx, c.ty, c.tz);
            c.state = c.next;
            c.timer = 6 + c.getRandom().nextInt(30);
            clip(c, "idle");
        }
    }

    /** A crow's stride toward a point on the ground; true when there. */
    private static boolean walkTo(WildCritter c, ClientLevel level, double tx, double tz, double speed) {
        double dx = tx - c.getX(), dz = tz - c.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        if (d < 0.15) return true;
        face(c, dx, dz, 12F);
        double k = Math.min(speed, d) / d, nx = c.getX() + dx * k, nz = c.getZ() + dz * k;
        double ny = Wildlife.ground(level, nx, nz);
        if (Double.isNaN(ny) || Math.abs(ny - c.getY()) > 1.1) return true;
        c.setPos(nx, c.getY() + (ny - c.getY()) * 0.5, nz);
        c.speed = (float) (d * k);
        return false;
    }

    // ---- flights -----------------------------------------------------------------------------------------------------

    /** Something a bird can stand on: leaves, a fence or a wall, a log, solid ground; never water. */
    private static boolean perchable(ClientLevel level, double x, double y, double z) {
        BlockState s = level.getBlockState(BlockPos.containing(x, y, z));
        if (!s.getFluidState().isEmpty()) return false;
        return s.is(BlockTags.LEAVES) || s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS) || s.is(BlockTags.LOGS) || s.isSolidRender();
    }

    /** A perch (a leaf top, a fence) within [min, max] of a point; NaN in tx when none was found in a few looks. */
    private static boolean findPerch(WildCritter c, ClientLevel level, RandomSource r, double ox, double oz, double min, double max) {
        for (int k = 0; k < 6; k++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
            double x = Math.floor(ox + Math.cos(a) * d) + 0.5, z = Math.floor(oz + Math.sin(a) * d) + 0.5;
            double y = Wildlife.top(level, x, z);
            if (Double.isNaN(y)) continue;
            BlockState s = level.getBlockState(BlockPos.containing(x, y - 1, z));
            boolean fence = s.is(BlockTags.FENCES) || s.is(BlockTags.WALLS);
            if (!s.is(BlockTags.LEAVES) && !fence) continue;
            if (Math.abs(y - c.getY()) > 9) continue;
            // a songbird keeps low: the outer rings of the trees' lower leaves, fences, not the canopy's roof
            if (c.kind == WildKinds.SONGBIRD && !fence && k < 5 && y - Wildlife.ground(level, x, z) > 7) continue;
            c.tx = x + (r.nextFloat() - 0.5) * 0.5;
            c.ty = y + (fence ? 0.5 : 0);
            c.tz = z + (r.nextFloat() - 0.5) * 0.5;
            return true;
        }
        return false;
    }

    private static void flit(WildCritter c, ClientLevel level, RandomSource r, double min, double max) {
        if (!findPerch(c, level, r, c.getX(), c.getZ(), min, max)) {
            clip(c, "idle");
            c.timer = 40;
            return;
        }
        takeOff(c, PERCH, r, false);
    }

    private static void upToPerch(WildCritter c, ClientLevel level, RandomSource r, double max) {
        if (findPerch(c, level, r, c.getX(), c.getZ(), 1, max)) takeOff(c, PERCH, r, false);
        else {
            clip(c, "idle");
            c.timer = 40;
        }
    }

    /** Down from the branch to forage on open ground below or near the tree. */
    private static boolean downToForage(WildCritter c, ClientLevel level, RandomSource r) {
        if (Wildlife.tod > 11800 && Wildlife.tod < 22600) return false;
        for (int k = 0; k < 4; k++) {
            double a = r.nextDouble() * Math.PI * 2, d = 1.5 + r.nextDouble() * 4;
            double x = c.getX() + Math.cos(a) * d, z = c.getZ() + Math.sin(a) * d;
            double g = Wildlife.ground(level, x, z), t = Wildlife.top(level, x, z);
            if (Double.isNaN(g) || g > c.getY() || c.getY() - g > 14) continue;
            // the open ground beside the trees, or under them where the canopy stands well clear
            if (t > g && t - g < 4) continue;
            if (!level.getFluidState(BlockPos.containing(x, g - 1, z)).isEmpty()) continue;
            c.tx = x;
            c.ty = g;
            c.tz = z;
            takeOff(c, GROUND, r, false);
            return true;
        }
        return false;
    }

    private static void takeOff(WildCritter c, int then, RandomSource r, boolean burst) {
        c.next = then;
        c.state = FLY;
        c.since = now;
        double dx = c.tx - c.getX(), dz = c.tz - c.getZ(), d = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
        double sp = burst ? 0.25 : 0.12;
        c.vx = dx / d * sp;
        c.vz = dz / d * sp;
        c.vy = burst ? 0.22 + r.nextDouble() * 0.1 : 0.12;
        c.timer = 400;
        clip(c, "fly");
        if (c.kind == WildKinds.SONGBIRD && r.nextInt(3) == 0) WildSound.play(c, WildlifeModule.CHIP.get(), 0.45F, 1F + r.nextFloat() * 0.3F);
    }

    /** Off and away from you: a burst of wings to a perch further off, or out of the place for good. */
    private static void flush(WildCritter c, ClientLevel level, RandomSource r) {
        double ax = c.getX() - px, az = c.getZ() - pz, al = Math.max(0.3, Math.sqrt(ax * ax + az * az));
        double ox = c.getX() + ax / al * 14, oz = c.getZ() + az / al * 14;
        boolean big = c.kind != WildKinds.SONGBIRD;
        if (c.kind == WildKinds.OWL) {
            // owls fly without a sound
        } else WildSound.play(c, big ? WildlifeModule.WINGS_BIG.get() : WildlifeModule.FLUSH.get(), big ? 0.8F : 0.9F, 0.9F + r.nextFloat() * 0.2F);
        if (c.kind == WildKinds.CROW && r.nextBoolean()) WildSound.play(c, WildlifeModule.CAW.get(), 1F, 1.05F);
        if (!c.leaving && c.kind != WildKinds.WOODPECKER && r.nextFloat() < 0.6F && findPerch(c, level, r, ox, oz, 0, 6)) {
            takeOff(c, PERCH, r, true);
            return;
        }
        leave(c, r, level);
        c.vx = ax / al * 0.25;
        c.vz = az / al * 0.25;
        c.vy = 0.25;
    }

    /** Away for good: up and out past the edge of the place; the director drops it once it's out of sight. */
    private static void leave(WildCritter c, RandomSource r, ClientLevel level) {
        c.leaving = true;
        double ax = c.getX() - px, az = c.getZ() - pz, al = Math.max(0.3, Math.sqrt(ax * ax + az * az));
        double a = Math.atan2(az / al, ax / al) + (r.nextDouble() - 0.5) * 0.8;
        c.tx = c.getX() + Math.cos(a) * 80;
        c.tz = c.getZ() + Math.sin(a) * 80;
        c.ty = c.getY() + 10 + r.nextDouble() * 14;
        c.next = PERCH;
        c.state = FLY;
        c.since = now;
        c.timer = 600;
        clip(c, "fly");
    }

    private static void fly(WildCritter c, ClientLevel level, RandomSource r) {
        int k = c.kind;
        double dx = c.tx - c.getX(), dy = c.ty - c.getY(), dz = c.tz - c.getZ(), d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double h = Math.sqrt(dx * dx + dz * dz);
        boolean small = k == WildKinds.SONGBIRD || k == WildKinds.WOODPECKER;
        double cruise = switch (k) {
            case WildKinds.SONGBIRD -> 0.32;
            case WildKinds.WOODPECKER -> 0.34;
            case WildKinds.HERON -> 0.22;
            case WildKinds.OWL -> 0.24;
            case WildKinds.DUCK -> 0.42;
            default -> 0.3;
        };
        if (!c.leaving && d < 2.4 && c.tickCount > 2) {
            c.state = LAND;
            clip(c, "flare");
            c.timer = 30;
            return;
        }
        // climb over what's in the way: aim above the target while far, to clear the trees between
        double clearance = c.leaving ? 0 : Math.min(4, h * 0.25);
        double ty = c.ty + clearance;
        double gx = c.getX() + c.vx * 6, gz = c.getZ() + c.vz * 6;
        double floor = Wildlife.top(level, gx, gz);
        if (!Double.isNaN(floor) && floor + 1.5 > ty && h > 3) ty = floor + 1.5;
        double sp = Math.min(cruise, 0.06 + d * 0.08);
        double wx = dx / Math.max(1e-3, d) * sp, wy = (ty - c.getY()) / Math.max(1e-3, d) * sp * 1.4, wz = dz / Math.max(1e-3, d) * sp;
        double agility = small ? 0.22 : k == WildKinds.HERON ? 0.06 : 0.1;
        c.vx += (wx - c.vx) * agility;
        c.vy += (wy - c.vy) * agility;
        c.vz += (wz - c.vz) * agility;
        // the songbird's bounding flight rises on the beats and dips with the wings shut
        if (small) c.vy += Math.sin((c.tickCount - c.since) * 0.55) * 0.025;
        c.setPos(c.getX() + c.vx, c.getY() + c.vy, c.getZ() + c.vz);
        float turn = face(c, c.vx, c.vz, small ? 30F : 10F);
        c.roll = Mth.lerp(0.3F, c.roll, Mth.clamp(-turn * (small ? 1.5F : 3F), -35F, 35F));
        c.pitch = Mth.lerp(0.3F, c.pitch, (float) Mth.clamp(-c.vy * 90, -25, 25));
        c.speed = (float) Math.sqrt(c.vx * c.vx + c.vz * c.vz);
        String flap = small ? (c.vy > 0.08 || c.tickCount - c.since < 10 ? "fly" : "bound")
                : (c.vy > 0.02 || (now + c.seed) % (k == WildKinds.HERON ? 120 : 70) < (k == WildKinds.DUCK ? 70 : 40)) ? "fly" : "glide";
        clip(c, k == WildKinds.RAPTOR && flap.equals("glide") ? "soar" : flap);
        if (--c.timer <= 0) c.discard();
    }

    private static void land(WildCritter c, ClientLevel level, RandomSource r) {
        double dx = c.tx - c.getX(), dy = c.ty - c.getY(), dz = c.tz - c.getZ(), d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double sp = Mth.clamp(d * 0.16, 0.025, 0.2);
        if (d < 0.08 || --c.timer <= 0) {
            c.setPos(c.tx, c.ty, c.tz);
            c.roll = c.pitch = 0F;
            if (c.next == CLING) {
                c.setYRot(c.face);
                settle(c, CLING, r);
            } else if (c.next == SWIM) {
                c.sink = 0.16F;
                c.ax = c.tx;
                c.az = c.tz;
                c.ty = c.getY();
                settle(c, SWIM, r);
                Insects.ripple(c.tx, c.ty - c.sink + 0.1, c.tz, 1.4F);
            } else if (c.next == STAND) {
                settle(c, STAND, r);
            } else settle(c, c.next == GROUND ? GROUND : PERCH, r);
            return;
        }
        c.setPos(c.getX() + dx / d * sp, c.getY() + dy / d * sp, c.getZ() + dz / d * sp);
        face(c, dx, dz, 20F);
        c.roll *= 0.7F;
        c.pitch = Mth.lerp(0.3F, c.pitch, -16F);
        c.speed = (float) sp;
    }

    // ---- woodpeckers --------------------------------------------------------------------------------------------------

    /**
     * Puts it on a trunk (foot = the lowest log, height of clear bark), at {@code up} above the foot on side 0..3, if that
     * side is open air; the body faces the bark.
     */
    static boolean onTrunk(WildCritter c, ClientLevel level, long foot, int height, double up, int side) {
        int fx = BlockPos.getX(foot), fy = BlockPos.getY(foot), fz = BlockPos.getZ(foot);
        for (int k = 0; k < 4; k++) {
            int sd = (side + k) & 3;
            int ox = sd == 0 ? 1 : sd == 2 ? -1 : 0, oz = sd == 1 ? 1 : sd == 3 ? -1 : 0;
            double y = fy + up;
            if (!level.getBlockState(BlockPos.containing(fx + ox, y, fz + oz)).isAir()) continue;
            if (!level.getBlockState(BlockPos.containing(fx, y, fz)).is(BlockTags.LOGS)) return false;
            c.ax = fx + 0.5;
            c.az = fz + 0.5;
            c.setPos(c.ax + ox * 0.6, y, c.az + oz * 0.6);
            c.face = (float) (Mth.atan2(-oz, -ox) * Mth.RAD_TO_DEG) - 90F;
            c.setYRot(c.face);
            c.hi = fy + Math.max(2, Math.min(height, 10)) - 0.5; // as high as it climbs
            c.lo = fy + 1.2;                                   // as low as it goes
            return true;
        }
        return false;
    }

    private static void cling(WildCritter c, ClientLevel level, RandomSource r) {
        if (threat(c, 3, 7) || WildSound.hostileNear(c.getX(), c.getY(), c.getZ(), 8)
                || c.tickCount % 20 == 0 && Soundscape.hushAt(c.getX(), c.getY(), c.getZ()) < 0.35F) {
            flush(c, level, r);
            return;
        }
        // as you come near it slips round the trunk, keeping the wood between you
        if (threat(c, 9, 14) && c.timer > 2 && c.tickCount % 10 == 0) {
            double ox = c.getX() - c.ax, oz = c.getZ() - c.az, toX = px - c.ax, toZ = pz - c.az;
            if (ox * toX + oz * toZ > 0) {
                hitchRound(c, level, 1);
                return;
            }
        }
        if (--c.timer > 0) return;
        float roll = r.nextFloat();
        boolean morning = Wildlife.tod > 22500 || Wildlife.tod < 4000;
        if (roll < (morning ? 0.18F : 0.08F)) {
            Wildlife.replay(c, "drum");
            c.timer = 22;
            WildSound.play(c, WildlifeModule.DRUM.get(), 1F, 0.9F + r.nextFloat() * 0.2F);
        } else if (roll < 0.45F) {
            Wildlife.replay(c, "peck");
            c.timer = 18 + r.nextInt(36);
            if (r.nextBoolean()) WildSound.play(c, WildlifeModule.TAP.get(), 0.7F, 0.9F + r.nextFloat() * 0.2F);
        } else if (roll < 0.7F) {
            hitchUp(c, level, r);
        } else if (roll < 0.78F) {
            hitchRound(c, level, r.nextBoolean() ? 1 : -1);
        } else if (roll < 0.86F) {
            // off to another tree: try its trunk, then fly there (the new trunk's side, centre and limits are set)
            Sites.Site s = Sites.get(c.home);
            if (s != null && s.nTrunk > 0) {
                int i = r.nextInt(s.nTrunk);
                double ox = c.getX(), oy = c.getY(), oz = c.getZ(), oax = c.ax, oaz = c.az, ohi = c.hi, olo = c.lo;
                float oface = c.face;
                if (onTrunk(c, level, s.trunk[i], s.trunkHeight[i], 1.5 + r.nextFloat() * Math.min(4, s.trunkHeight[i] - 2), r.nextInt(4))) {
                    c.tx = c.getX();
                    c.ty = c.getY();
                    c.tz = c.getZ();
                    c.setPos(ox, oy, oz);
                    c.next = CLING;
                    c.state = FLY;
                    c.since = now;
                    c.timer = 300;
                    c.vx = c.vy = c.vz = 0;
                    clip(c, "fly");
                    return;
                }
                c.setPos(ox, oy, oz);
                c.face = oface;
                c.ax = oax;
                c.az = oaz;
                c.hi = ohi;
                c.lo = olo;
            }
            c.timer = 20;
        } else {
            clip(c, "cling");
            c.timer = 30 + r.nextInt(60);
        }
    }

    /** Up the bark in a little hitch, or back down when it reaches the top of its climb. */
    private static void hitchUp(WildCritter c, ClientLevel level, RandomSource r) {
        double ny = c.getY() + 0.22;
        if (ny > c.hi || !level.getBlockState(BlockPos.containing(c.ax, ny + 0.3, c.az)).is(BlockTags.LOGS)) {
            ny = c.lo + r.nextDouble() * 1.5; // drops back down the trunk to start again
        }
        c.setPos(c.getX(), ny, c.getZ());
        c.yo = ny;
        Wildlife.replay(c, "hitch");
        c.timer = 8;
    }

    /** A quarter of the way round the trunk, if that side is open. */
    private static void hitchRound(WildCritter c, ClientLevel level, int dir) {
        double ox = c.getX() - c.ax, oz = c.getZ() - c.az;
        double nx = c.ax - oz * dir, nz = c.az + ox * dir;
        if (!level.getBlockState(BlockPos.containing(nx, c.getY(), nz)).isAir()) return;
        c.setPos(nx, c.getY() + 0.15, nz);
        c.face = (float) (Mth.atan2(c.az - nz, c.ax - nx) * Mth.RAD_TO_DEG) - 90F;
        c.setYRot(c.face);
        c.yRotO = c.face;
        Wildlife.replay(c, "hitch");
        c.timer = 10;
    }

    // ---- buzzards, kestrels and gulls in the air --------------------------------------------------------------------

    private static void soar(WildCritter c, ClientLevel level, RandomSource r) {
        boolean gull = c.kind == WildKinds.GULL, kestrel = !gull && Math.floorMod(c.variant, 2) == 1;
        double radius = c.tx, sp = gull ? 0.2 : 0.16;
        c.face += (float) (sp / radius);
        // the circle drifts with the wind and the thermal
        c.ax += Math.cos(c.seed * 0.001 + now * 0.0007) * 0.02;
        c.az += Math.sin(c.seed * 0.001 + now * 0.0007) * 0.02;
        double x = c.ax + Math.cos(c.face) * radius, z = c.az + Math.sin(c.face) * radius;
        double y = c.ty + Math.sin((now + c.seed) * 0.01) * 1.5;
        double dx = x - c.getX(), dz = z - c.getZ();
        c.setPos(c.getX() + dx * 0.25, c.getY() + (y - c.getY()) * 0.05, c.getZ() + dz * 0.25);
        face(c, dx, dz, 8F);
        c.roll = Mth.lerp(0.1F, c.roll, gull ? -14F : -24F);
        c.pitch = Mth.lerp(0.1F, c.pitch, 0F);
        c.speed = (float) sp;
        boolean beat = gull ? (now + c.seed) % 160 < 24 : (now + c.seed) % 400 < 30;
        clip(c, beat ? "fly" : gull ? "glide" : "soar");
        if ((now + c.seed) % (gull ? 500 : 900) == 0) {
            if (gull) WildSound.gull(c);
            else WildSound.play(c, WildlifeModule.RAPTOR.get(), 1F, 0.95F + r.nextFloat() * 0.1F);
        }
        if (--c.timer > 0) return;
        if (c.leaving) {
            leave(c, r, level);
            return;
        }
        if (kestrel) {
            // down to hang over the meadow
            double g = Wildlife.ground(level, c.getX(), c.getZ());
            if (!Double.isNaN(g)) {
                c.state = HOVER;
                c.tx = c.getX();
                c.tz = c.getZ();
                c.next = (int) (g + 9 + r.nextInt(5));
                c.timer = 100 + r.nextInt(120);
                return;
            }
        }
        c.timer = 200 + r.nextInt(400);
    }

    /** A kestrel hanging in the wind, head still; now and then it drops on something and climbs back. */
    private static void hover(WildCritter c, ClientLevel level, RandomSource r) {
        if (c.state == HOVER) {
            double want = c.next;
            c.setPos(c.tx + Math.sin(now * 0.07 + c.seed) * 0.06, c.getY() + (want - c.getY()) * 0.04, c.tz + Math.cos(now * 0.05) * 0.06);
            c.roll *= 0.8F;
            c.pitch = Mth.lerp(0.1F, c.pitch, -18F);
            clip(c, "hover");
            if (--c.timer > 0) return;
            if (r.nextFloat() < 0.35F) {
                c.state = STOOP;
                c.timer = 30;
                return;
            }
            c.state = SOAR;
            c.timer = 200 + r.nextInt(300);
            c.ty = c.getY() + 12;
            c.ax = c.getX() - Math.cos(c.face) * c.tx;
            c.az = c.getZ() - Math.sin(c.face) * c.tx;
            return;
        }
        // the stoop: a fold and a drop, then back up
        c.timer--;
        double vy = c.timer > 15 ? -0.35 : 0.25;
        c.setPos(c.getX(), c.getY() + vy, c.getZ());
        c.pitch = Mth.lerp(0.2F, c.pitch, c.timer > 15 ? 40F : -20F);
        clip(c, c.timer > 15 ? "stoop" : "fly");
        if (c.timer <= 0) {
            c.state = HOVER;
            c.timer = 60 + r.nextInt(80);
        }
    }

    // ---- ducks --------------------------------------------------------------------------------------------------------

    /** Still water a duck can sit on: a source block with air above. */
    static boolean still(ClientLevel level, int x, int y, int z) {
        BlockPos p = new BlockPos(x, y, z);
        return level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p).isSource() && level.getBlockState(p.above()).isAir();
    }

    private static double waterOrGround(ClientLevel level, double x, double z) {
        double t = Wildlife.top(level, x, z);
        return t;
    }

    private static void swim(WildCritter c, ClientLevel level, RandomSource r) {
        if (startled(c, 3.5, 11)) {
            // off the water with a clatter of wings and a quack
            WildSound.play(c, WildlifeModule.WINGS_BIG.get(), 0.9F, 1.1F);
            WildSound.play(c, WildlifeModule.QUACK.get(), 1F, 1F);
            Insects.ripple(c.getX(), c.getY() - c.sink + 0.1, c.getZ(), 1.6F);
            c.sink = 0F;
            leave(c, r, level);
            c.vy = 0.2;
            return;
        }
        boolean wary = threat(c, 9, 16);
        if (wary && c.tickCount % 10 == 0) {
            // paddle away
            double ax = c.getX() - px, az = c.getZ() - pz, al = Math.max(0.3, Math.sqrt(ax * ax + az * az));
            c.tx = c.getX() + ax / al * 4;
            c.tz = c.getZ() + az / al * 4;
            c.timer = 40;
            clip(c, "swim");
        }
        if (Wildlife.playing(c, "swim") || wary) {
            double dx = c.tx - c.getX(), dz = c.tz - c.getZ(), d = Math.sqrt(dx * dx + dz * dz);
            if (d > 0.2) {
                double sp = wary ? 0.05 : 0.02;
                double nx = c.getX() + dx / d * sp, nz = c.getZ() + dz / d * sp;
                if (still(level, Mth.floor(nx), Mth.floor(c.ty), Mth.floor(nz))) {
                    c.setPos(nx, c.ty, nz);
                    c.speed = (float) sp;
                    face(c, dx, dz, 5F);
                    if (c.tickCount % 24 == 0) Insects.ripple(c.getX(), c.ty - c.sink + 0.08, c.getZ(), 0.9F);
                } else c.tx = c.getX();
            }
        }
        if (--c.timer > 0) return;
        float roll = r.nextFloat();
        if (roll < 0.35F) {
            double a = r.nextDouble() * Math.PI * 2, d = 1 + r.nextDouble() * 4;
            c.tx = c.ax + Math.cos(a) * d;
            c.tz = c.az + Math.sin(a) * d;
            clip(c, "swim");
            c.timer = 80 + r.nextInt(120);
        } else if (roll < 0.55F) {
            Wildlife.replay(c, "dabble");
            c.timer = 60;
            Insects.ripple(c.getX(), c.ty - c.sink + 0.08, c.getZ(), 1F);
        } else if (roll < 0.7F) {
            Wildlife.replay(c, "preen");
            c.timer = 48;
        } else if (roll < 0.8F) {
            Wildlife.replay(c, "quack");
            c.timer = 28;
            WildSound.play(c, WildlifeModule.QUACK.get(), 0.8F, 0.95F + r.nextFloat() * 0.1F);
        } else {
            clip(c, "swim");
            c.tx = c.getX();
            c.tz = c.getZ();
            c.timer = 60 + r.nextInt(100);
        }
    }

    // ---- herons -------------------------------------------------------------------------------------------------------

    private static void heron(WildCritter c, ClientLevel level, RandomSource r) {
        if (startled(c, 12, 20)) {
            WildSound.play(c, WildlifeModule.CROAK_HERON.get(), 1F, 0.95F + r.nextFloat() * 0.1F);
            WildSound.play(c, WildlifeModule.WINGS_BIG.get(), 0.9F, 0.8F);
            leave(c, r, level);
            c.vy = 0.12;
            return;
        }
        boolean alert = threat(c, 20, 26);
        if (alert) {
            Wildlife.lookAtPlayer(c, 26);
            clip(c, "alert");
            c.timer = Math.max(c.timer, 40);
            return;
        }
        c.lookX = Double.NaN;
        if (c.state == STALK) {
            double dx = c.tx - c.getX(), dz = c.tz - c.getZ(), d = Math.sqrt(dx * dx + dz * dz);
            if (d > 0.1) {
                double sp = 0.018;
                double ny = Wildlife.ground(level, c.getX() + dx / d * sp, c.getZ() + dz / d * sp);
                if (!Double.isNaN(ny) && Math.abs(ny - c.getY()) < 0.6) {
                    c.setPos(c.getX() + dx / d * sp, c.getY() + (ny - c.getY()) * 0.3, c.getZ() + dz / d * sp);
                    c.speed = (float) sp;
                    face(c, dx, dz, 3F);
                } else c.timer = 0;
            } else c.timer = 0;
        }
        if (--c.timer > 0) return;
        float roll = r.nextFloat();
        if (roll < 0.25F) {
            double a = (c.getYRot() + 90F) * Mth.DEG_TO_RAD + (r.nextDouble() - 0.5) * 2.0, d = 1 + r.nextDouble() * 2;
            c.tx = c.getX() + Math.cos(a) * d;
            c.tz = c.getZ() + Math.sin(a) * d;
            c.state = STALK;
            clip(c, "stalk");
            c.timer = 120;
        } else if (roll < 0.4F) {
            c.state = STAND;
            Wildlife.replay(c, "strike");
            c.timer = 30;
            double fx = -Mth.sin(c.getYRot() * Mth.DEG_TO_RAD), fz = Mth.cos(c.getYRot() * Mth.DEG_TO_RAD);
            Insects.ripple(c.getX() + fx * 1.3, c.getY() - 0.1, c.getZ() + fz * 1.3, 0.8F);
        } else {
            c.state = STAND;
            clip(c, "stand");
            c.timer = 160 + r.nextInt(400);
        }
    }
}
