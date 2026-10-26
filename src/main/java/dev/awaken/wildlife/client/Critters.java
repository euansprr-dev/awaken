package dev.awaken.wildlife.client;

import dev.awaken.core.audio.client.Soundscape;
import dev.awaken.wildlife.Density;
import dev.awaken.wildlife.WildCritter;
import dev.awaken.wildlife.WildKinds;
import dev.awaken.wildlife.WildlifeModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import static dev.awaken.wildlife.client.Wildlife.clip;
import static dev.awaken.wildlife.client.Wildlife.face;
import static dev.awaken.wildlife.client.Wildlife.now;
import static dev.awaken.wildlife.client.Wildlife.px;
import static dev.awaken.wildlife.client.Wildlife.pz;
import static dev.awaken.wildlife.client.Wildlife.threat;

/**
 * The wild's small animals (SPEC "Wild life"): squirrels up and down the trunks in bursts, foraging at the foot of their
 * tree and racing back up it, round to the far side, scolding; rabbits grazing at the forest's edge, sitting up with
 * their ears high when you come near, then away in a zigzag for cover; a fox trotting the meadow at dusk, sitting,
 * listening, pouncing on something in the grass; frogs at the water's edge, croaking at dusk, plopping in as you come.
 * Deer are the Living Wilds' herds (SPEC §21), not ours.
 */
final class Critters {
    private Critters() {}

    static final int GROUND = 1, TRUNK = 20, RUN = 21, TROT = 22, POUNCE = 23, JUMP = 24, SIT = 25, CLIMB = 26;

    static boolean place(WildCritter c, ClientLevel level, Sites.Site s, int slot, RandomSource r) {
        float draw = Density.draw(s.key, c.kind, slot + 1);
        switch (c.kind) {
            case WildKinds.SQUIRREL -> {
                if (s.nTrunk == 0) return false;
                int i = (int) (draw * s.nTrunk);
                if (!Birds.onTrunk(c, level, s.trunk[i], s.trunkHeight[i], 1.2 + r.nextFloat() * Math.min(5, s.trunkHeight[i] - 2), r.nextInt(4))) return false;
                c.state = TRUNK;
                c.timer = 20 + r.nextInt(60);
                clip(c, "cling");
            }
            case WildKinds.RABBIT, WildKinds.FOX -> {
                if (s.nGround == 0) return false;
                long p = s.ground[(int) (draw * s.nGround)];
                c.setPos(BlockPos.getX(p) + 0.5, BlockPos.getY(p), BlockPos.getZ(p) + 0.5);
                c.setYRot(r.nextFloat() * 360F);
                c.ax = c.getX();
                c.az = c.getZ();
                c.state = GROUND;
                c.timer = 20 + r.nextInt(60);
                clip(c, c.kind == WildKinds.RABBIT ? "graze" : "idle");
            }
            case WildKinds.FROG -> {
                if (s.nShore == 0) return false;
                int i = (int) (draw * s.nShore);
                long p = s.shore[i];
                c.setPos(BlockPos.getX(p) + 0.5, BlockPos.getY(p), BlockPos.getZ(p) + 0.5);
                c.setYRot(s.shoreDir[i] * 90F - 90F + (r.nextFloat() - 0.5F) * 40F);
                c.state = SIT;
                c.timer = 20 + r.nextInt(100);
                clip(c, "idle");
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    static void think(WildCritter c, ClientLevel level) {
        RandomSource r = c.getRandom();
        // no longer wanted here (the hour turned, the rain came): away the way it would go
        if (c.leaving && c.state != RUN && c.state != JUMP && c.state != CLIMB) {
            switch (c.kind) {
                case WildKinds.RABBIT, WildKinds.FOX -> bolt(c, r, c.kind == WildKinds.FOX ? 0.1 : 0.14);
                case WildKinds.FROG -> {
                    c.state = JUMP;
                    c.timer = 10;
                    Wildlife.replay(c, "hop");
                }
                case WildKinds.SQUIRREL -> {
                    if (c.state == GROUND) c.state = RUN;
                    else if (c.getY() < c.hi - 0.5) climb(c, c.hi, 0.08);
                }
                default -> { }
            }
        }
        switch (c.kind) {
            case WildKinds.SQUIRREL -> squirrel(c, level, r);
            case WildKinds.RABBIT -> rabbit(c, level, r);
            case WildKinds.FOX -> fox(c, level, r);
            case WildKinds.FROG -> frog(c, level, r);
            default -> c.discard();
        }
    }

    private static boolean startled(WildCritter c, double near, double run) {
        return threat(c, near, run) || WildSound.hostileNear(c.getX(), c.getY(), c.getZ(), near + 6)
                || c.tickCount % 20 == 0 && Soundscape.hushAt(c.getX(), c.getY(), c.getZ()) < 0.35F;
    }

    /** A step over the ground toward (tx, tz); false when the way is blocked (a wall, a drop) or it's there. */
    private static boolean step(WildCritter c, ClientLevel level, double tx, double tz, double speed, float turn) {
        double dx = tx - c.getX(), dz = tz - c.getZ(), d = Math.sqrt(dx * dx + dz * dz);
        if (d < Math.max(0.15, speed)) return false;
        face(c, dx, dz, turn);
        double k = Math.min(speed, d) / d, nx = c.getX() + dx * k, nz = c.getZ() + dz * k;
        double ny = Wildlife.ground(level, nx, nz);
        if (Double.isNaN(ny) || Math.abs(ny - c.getY()) > 1.1) return false;
        if (!level.getFluidState(BlockPos.containing(nx, ny - 0.5, nz)).isEmpty()) return false;
        c.setPos(nx, c.getY() + (ny - c.getY()) * 0.5, nz);
        c.speed = (float) (d * k);
        return true;
    }

    /** Away from you for good, toward cover, in a zigzag (the director drops it out of sight). */
    private static void bolt(WildCritter c, RandomSource r, double speed) {
        c.leaving = true;
        double ax = c.getX() - px, az = c.getZ() - pz, al = Math.max(0.3, Math.sqrt(ax * ax + az * az));
        double a = Math.atan2(az / al, ax / al) + (r.nextDouble() - 0.5) * 0.9;
        c.tx = c.getX() + Math.cos(a) * 9;
        c.tz = c.getZ() + Math.sin(a) * 9;
        c.vx = speed;
        c.state = RUN;
        c.timer = 18 + r.nextInt(12);
        clip(c, "run");
    }

    private static void run(WildCritter c, ClientLevel level, RandomSource r) {
        if (!step(c, level, c.tx, c.tz, c.vx, 25F) || --c.timer <= 0) {
            // a jink: a new line away from you
            double ax = c.getX() - px, az = c.getZ() - pz, al = Math.max(0.3, Math.sqrt(ax * ax + az * az));
            double a = Math.atan2(az / al, ax / al) + (r.nextDouble() - 0.5) * 1.6;
            c.tx = c.getX() + Math.cos(a) * 8;
            c.tz = c.getZ() + Math.sin(a) * 8;
            c.timer = 12 + r.nextInt(16);
        }
    }

    // ---- squirrels -----------------------------------------------------------------------------------------------------

    private static void squirrel(WildCritter c, ClientLevel level, RandomSource r) {
        if (c.state == TRUNK || c.state == CLIMB) {
            trunk(c, level, r);
            return;
        }
        if (c.state == RUN) {
            // back to its tree's foot, then up
            double fx = c.ax + (c.getX() - c.ax > 0 ? 0.6 : -0.6), fz = c.az;
            if (!step(c, level, fx, fz, 0.16, 30F)) {
                c.setPos(fx, c.getY(), fz);
                c.face = (float) (Mth.atan2(c.az - fz, c.ax - fx) * Mth.RAD_TO_DEG) - 90F;
                c.setYRot(c.face);
                climb(c, c.lo + 2.5 + r.nextDouble() * 2.5, 0.11);
                WildSound.play(c, WildlifeModule.CHATTER.get(), 0.8F, 1F + r.nextFloat() * 0.15F);
            }
            return;
        }
        // on the ground by its tree
        if (startled(c, 6, 11)) {
            c.state = RUN;
            clip(c, "run");
            WildSound.play(c, WildlifeModule.RUSTLE.get(), 0.7F, 1.2F);
            return;
        }
        if (Wildlife.playing(c, "run") && !step(c, level, c.tx, c.tz, 0.13, 30F)) {
            clip(c, "forage");
            c.timer = 30 + r.nextInt(40);
        }
        if (--c.timer > 0) return;
        float roll = r.nextFloat();
        if (roll < 0.35F) {
            clip(c, "forage");
            c.timer = 40 + r.nextInt(60);
        } else if (roll < 0.6F) {
            clip(c, "idle");
            c.timer = 40 + r.nextInt(50);
        } else if (roll < 0.85F) {
            double a = r.nextDouble() * Math.PI * 2, d = 1 + r.nextDouble() * 2.5;
            c.tx = c.ax + Math.cos(a) * d;
            c.tz = c.az + Math.sin(a) * d;
            clip(c, "run");
            c.timer = 40;
            if (r.nextInt(3) == 0) WildSound.play(c, WildlifeModule.RUSTLE.get(), 0.5F, 1.3F);
        } else {
            c.state = RUN; // back up the tree
        }
    }

    private static void climb(WildCritter c, double toY, double speed) {
        c.state = CLIMB;
        c.ty = toY;
        c.vy = speed * Math.signum(toY - c.getY());
        c.setYRot(c.vy < 0 ? c.face + 180F : c.face);
        c.yRotO = c.getYRot();
        clip(c, c.vy < 0 ? "descend" : "climb");
    }

    private static void trunk(WildCritter c, ClientLevel level, RandomSource r) {
        if (c.state == CLIMB) {
            double ny = c.getY() + c.vy;
            boolean bark = level.getBlockState(BlockPos.containing(c.ax, ny + 0.2, c.az)).is(BlockTags.LOGS);
            if (!bark || (c.vy > 0 ? ny >= c.ty : ny <= c.ty)) {
                c.state = TRUNK;
                c.setYRot(c.face);
                c.yRotO = c.face;
                c.timer = 20 + r.nextInt(50);
                clip(c, r.nextInt(3) == 0 ? "chatter" : "cling");
                // at the foot it hops down to forage
                if (c.vy < 0 && ny <= c.lo + 0.3 && bark) toGround(c, level, r);
                return;
            }
            c.setPos(c.getX(), ny, c.getZ());
            c.speed = (float) Math.abs(c.vy);
            return;
        }
        if (threat(c, 7, 12) && c.tickCount % 8 == 0) {
            // up and round to the far side, scolding
            double ox = c.getX() - c.ax, oz = c.getZ() - c.az, toX = px - c.ax, toZ = pz - c.az;
            if (ox * toX + oz * toZ > -0.1) {
                double nx = c.ax - ox, nz = c.az - oz;
                if (level.getBlockState(BlockPos.containing(nx, c.getY(), nz)).isAir()) {
                    c.setPos(nx, c.getY(), nz);
                    c.face = (float) (Mth.atan2(c.az - nz, c.ax - nx) * Mth.RAD_TO_DEG) - 90F;
                    c.setYRot(c.face);
                    c.yRotO = c.face;
                }
            }
            if (c.getY() < c.hi - 1) climb(c, Math.min(c.hi, c.getY() + 2 + r.nextDouble() * 2), 0.14);
            if (r.nextInt(3) == 0) {
                Wildlife.replay(c, "chatter");
                WildSound.play(c, WildlifeModule.CHATTER.get(), 0.9F, 1F + r.nextFloat() * 0.15F);
            }
            return;
        }
        if (--c.timer > 0) return;
        float roll = r.nextFloat();
        if (roll < 0.3F) {
            double to = c.lo + r.nextDouble() * Math.max(0.5, c.hi - c.lo);
            if (Math.abs(to - c.getY()) > 0.8) climb(c, to, 0.09 + r.nextDouble() * 0.05);
            else c.timer = 20;
        } else if (roll < 0.45F) {
            climb(c, c.lo, 0.1);
        } else if (roll < 0.55F) {
            Wildlife.replay(c, "chatter");
            c.timer = 24;
            if (r.nextBoolean()) WildSound.play(c, WildlifeModule.CHATTER.get(), 0.6F, 1F + r.nextFloat() * 0.2F);
        } else {
            clip(c, "cling");
            c.timer = 30 + r.nextInt(70);
        }
    }

    private static void toGround(WildCritter c, ClientLevel level, RandomSource r) {
        double ox = c.getX() - c.ax, oz = c.getZ() - c.az;
        double gx = c.ax + ox * 2.2, gz = c.az + oz * 2.2, gy = Wildlife.ground(level, gx, gz);
        if (Double.isNaN(gy) || Math.abs(gy - c.lo) > 2.5) return;
        c.setPos(gx, gy, gz);
        c.setYRot(c.face + 180F);
        c.yRotO = c.getYRot();
        c.state = GROUND;
        c.timer = 30 + r.nextInt(40);
        clip(c, "forage");
    }

    // ---- rabbits -------------------------------------------------------------------------------------------------------

    private static void rabbit(WildCritter c, ClientLevel level, RandomSource r) {
        if (c.state == RUN) {
            run(c, level, r);
            return;
        }
        if (startled(c, 5, 13)) {
            WildSound.play(c, WildlifeModule.RUSTLE.get(), 0.6F, 1.1F);
            bolt(c, r, 0.24);
            return;
        }
        if (threat(c, 11, 18)) {
            clip(c, "alert");
            Wildlife.lookAtPlayer(c, 18);
            c.timer = Math.max(c.timer, 30);
            return;
        }
        c.lookX = Double.NaN;
        if (Wildlife.playing(c, "hop") && !step(c, level, c.tx, c.tz, 0.06, 15F)) {
            clip(c, "idle");
            c.timer = 10 + r.nextInt(30);
        }
        if (--c.timer > 0) return;
        float roll = r.nextFloat();
        if (roll < 0.4F) {
            clip(c, "graze");
            c.timer = 60 + r.nextInt(100);
        } else if (roll < 0.65F) {
            double a = r.nextDouble() * Math.PI * 2, d = 0.8 + r.nextDouble() * 1.5;
            c.tx = c.getX() + Math.cos(a) * d;
            c.tz = c.getZ() + Math.sin(a) * d;
            if (Math.hypot(c.tx - c.ax, c.tz - c.az) > 8) {
                c.tx = c.ax;
                c.tz = c.az;
            }
            clip(c, "hop");
            c.timer = 40;
        } else if (roll < 0.75F) {
            Wildlife.replay(c, "groom");
            c.timer = 44;
        } else if (roll < 0.85F) {
            clip(c, "alert");
            c.timer = 30 + r.nextInt(30);
        } else {
            clip(c, "idle");
            c.timer = 30 + r.nextInt(60);
        }
    }

    // ---- foxes --------------------------------------------------------------------------------------------------------

    private static void fox(WildCritter c, ClientLevel level, RandomSource r) {
        if (c.state == RUN) {
            run(c, level, r);
            return;
        }
        if (startled(c, 10, 18)) {
            bolt(c, r, 0.28);
            return;
        }
        boolean watched = threat(c, 22, 26);
        if (c.state == TROT) {
            if (watched) {
                // stops and watches you, one forepaw...
                c.state = GROUND;
                clip(c, "idle");
                c.timer = 60;
            } else if (!step(c, level, c.tx, c.tz, 0.085, 6F)) {
                c.state = GROUND;
                c.timer = 0;
            }
            return;
        }
        if (c.state == POUNCE) {
            int t = (int) (now - c.since);
            if (t > 9 && t < 24) step(c, level, c.tx, c.tz, 0.06, 0F);
            if (--c.timer <= 0) {
                c.state = GROUND;
                clip(c, "sniff");
                c.timer = 30;
            }
            return;
        }
        if (watched) Wildlife.lookAtPlayer(c, 26);
        else c.lookX = Double.NaN;
        if (--c.timer > 0) return;
        if (c.next == POUNCE) {
            c.next = 0;
            float yaw = c.getYRot() * Mth.DEG_TO_RAD;
            c.tx = c.getX() - Mth.sin(yaw) * 0.8;
            c.tz = c.getZ() + Mth.cos(yaw) * 0.8;
            c.state = POUNCE;
            c.since = now;
            c.timer = 30;
            Wildlife.replay(c, "pounce");
            WildSound.play(c, WildlifeModule.RUSTLE.get(), 0.6F, 0.9F);
            return;
        }
        boolean night = Wildlife.tod > 14000 && Wildlife.tod < 22000;
        float roll = r.nextFloat();
        if (watched) {
            clip(c, roll < 0.5F ? "sit" : "idle");
            c.timer = 40 + r.nextInt(40);
        } else if (roll < 0.4F) {
            double a = r.nextDouble() * Math.PI * 2, d = 6 + r.nextDouble() * 10;
            c.tx = c.ax + Math.cos(a) * d;
            c.tz = c.az + Math.sin(a) * d;
            c.state = TROT;
            clip(c, "trot");
        } else if (roll < 0.6F && !night) {
            // hears something in the grass: still, head cocked... and the leap
            clip(c, "listen");
            c.timer = 50 + r.nextInt(30);
            c.next = POUNCE;
        } else if (roll < 0.75F) {
            clip(c, "sniff");
            c.timer = 36;
        } else if (roll < 0.9F) {
            clip(c, night ? "lie" : "sit");
            c.timer = 80 + r.nextInt(160);
        } else {
            clip(c, "idle");
            c.timer = 40;
        }
    }

    // ---- frogs --------------------------------------------------------------------------------------------------------

    private static void frog(WildCritter c, ClientLevel level, RandomSource r) {
        if (c.state == JUMP) {
            int t = 10 - c.timer;
            float yaw = c.getYRot() * Mth.DEG_TO_RAD;
            c.setPos(c.getX() - Mth.sin(yaw) * 0.16, c.getY() + (t < 5 ? 0.08 : -0.11), c.getZ() + Mth.cos(yaw) * 0.16);
            if (--c.timer <= 0) {
                Insects.ripple(c.getX(), Math.floor(c.getY()) + 0.92, c.getZ(), 1.2F);
                WildSound.play(c, WildlifeModule.PLOP.get(), 0.8F, 0.9F + r.nextFloat() * 0.3F);
                c.discard();
            }
            return;
        }
        if (startled(c, 3.5, 7)) {
            c.state = JUMP;
            c.timer = 10;
            Wildlife.replay(c, "hop");
            return;
        }
        if (--c.timer > 0) return;
        boolean evening = Wildlife.tod > 11500 && Wildlife.tod < 23000;
        float chance = (evening ? 0.45F : 0.15F) + Wildlife.rain * 0.3F;
        if (r.nextFloat() < chance) {
            Wildlife.replay(c, "croak");
            c.timer = 34 + r.nextInt(60);
            WildSound.play(c, SoundEvents.FROG_AMBIENT, 0.7F, 0.9F + r.nextFloat() * 0.25F);
        } else {
            clip(c, "idle");
            c.timer = 40 + r.nextInt(120);
        }
    }
}
