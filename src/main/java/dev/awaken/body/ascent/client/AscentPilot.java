package dev.awaken.body.ascent.client;

import dev.awaken.Awaken;
import dev.awaken.body.ascent.AscentRules;
import dev.awaken.body.ascent.AscentState;
import dev.awaken.core.client.AwakenKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * Development only ({@code /awaken ascent pilot <line>}, game masters): presses this client's keys the way a player
 * would, so tours (whose autopilot can't hold keys) film every move through exactly the path real input takes.
 * <pre>
 * hold &lt;keys&gt; &lt;ticks&gt;      keys: forward,back,left,right,jump,sneak,sprint,tether (comma separated)
 * aim &lt;yaw&gt; &lt;pitch&gt; &lt;ticks&gt; turn smoothly to a view
 * run &lt;script&gt; [n]          a manoeuvre that watches the state: swing (n swings down the lane), glide, boom, soar
 * rec &lt;name&gt; &lt;ticks&gt; [n]     film a clip (every n-th frame)
 * route rec|play|stop [name] record a run's keys and view, or play one back from its start ({@link AscentRoutes})
 * aether &lt;0..1&gt;              set this client's Aether (to film the meter and its empty)
 * double &lt;key&gt;               a double tap, as a player's: 2 ticks down, 3 up, 2 down (a barrel roll: double right)
 * stop                       let go of everything
 * </pre>
 */
public final class AscentPilot {
    private AscentPilot() {}

    private static final int FORWARD = 0, BACK = 1, LEFT = 2, RIGHT = 3, JUMP = 4, SNEAK = 5, SPRINT = 6, TETHER = 7;
    private static final int[] HOLD = new int[8];
    private static float aimYaw, aimPitch, aimStepYaw, aimStepPitch;
    private static int aimTicks;
    /** A double tap in progress: the key and how far along (-1: none). */
    private static int doubleKey = -1, doubleAge = -1;
    /** Keys were pressed last tick (released once when the holds run out). */
    private static boolean holding;
    private static String script = "";
    private static int age, count, target, phase, mark, misses;
    private static float laneYaw;

    static void line(String line) {
        String[] a = line.trim().split("\\s+");
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || a.length == 0) return;
        Awaken.LOG.info("[ascent] pilot {}", line);
        switch (a[0]) {
            case "hold" -> {
                int t = Integer.parseInt(a[2]);
                for (String k : a[1].split(",")) {
                    int i = key(k);
                    if (i >= 0) HOLD[i] = t;
                }
            }
            case "aim" -> {
                float yaw = Float.parseFloat(a[1]), pitch = Float.parseFloat(a[2]);
                aimTicks = Math.max(1, Integer.parseInt(a[3]));
                aimStepYaw = Mth.wrapDegrees(yaw - p.getYRot()) / aimTicks;
                aimStepPitch = (pitch - p.getXRot()) / aimTicks;
                aimYaw = yaw;
                aimPitch = pitch;
            }
            case "run" -> {
                script = a[1];
                target = a.length > 2 ? Integer.parseInt(a[2]) : 4;
                age = count = phase = mark = misses = 0;
                laneYaw = p.getYRot();
                core = null;
            }
            case "rec" -> ClipRecorder.start(a[1], Integer.parseInt(a[2]), a.length > 3 ? Integer.parseInt(a[3]) : 2);
            case "route" -> AscentRoutes.line(a);
            case "lensmotion" -> dev.awaken.body.client.feel.FeelDirector.lensMotionOn = !a[1].equals("off");
            case "double" -> {
                doubleKey = key(a[1]);
                doubleAge = 0;
            }
            case "aether" -> AscentMoves.aether = Math.max(0, Math.min(1, Double.parseDouble(a[1])));
            case "stop" -> stop();
            default -> Awaken.LOG.warn("[ascent] pilot: unknown line {}", line);
        }
    }

    private static int key(String k) {
        return switch (k) {
            case "forward" -> FORWARD;
            case "back" -> BACK;
            case "left" -> LEFT;
            case "right" -> RIGHT;
            case "jump" -> JUMP;
            case "sneak" -> SNEAK;
            case "sprint" -> SPRINT;
            case "tether" -> TETHER;
            default -> -1;
        };
    }

    static void stop() {
        script = "";
        java.util.Arrays.fill(HOLD, 0);
        aimTicks = 0;
        apply();
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (!script.isEmpty()) {
            age++;
            switch (script) {
                case "swing" -> swing(p);
                case "glide" -> glide(p, false);
                case "boom" -> glide(p, true);
                case "soar" -> soar(p);
                case "climb" -> climb(p);
                case "leap" -> leap(p);
                case "spire" -> spire(p);
                default -> script = "";
            }
        }
        if (aimTicks > 0) {
            aimTicks--;
            p.setYRot(p.getYRot() + aimStepYaw);
            p.setYHeadRot(p.getYRot());
            p.setXRot(Mth.clamp(p.getXRot() + aimStepPitch, -90, 90));
        }
        if (doubleAge >= 0 && doubleKey >= 0) {
            if (doubleAge == 0 || doubleAge == 5) HOLD[doubleKey] = Math.max(HOLD[doubleKey], 2);
            if (++doubleAge > 6) doubleAge = -1;
        }
        boolean any = false;
        for (int i = 0; i < HOLD.length; i++) if (HOLD[i] > 0) any = true;
        if (!any && script.isEmpty()) {
            // the last hold ran out: let go of its keys once (a later press is then a new press)
            if (holding) apply();
            holding = false;
            return;
        }
        apply();
        holding = true;
        for (int i = 0; i < HOLD.length; i++) if (HOLD[i] > 0) HOLD[i]--;
    }

    private static void apply() {
        Options o = Minecraft.getInstance().options;
        set(o.keyUp, HOLD[FORWARD] > 0);
        set(o.keyDown, HOLD[BACK] > 0);
        set(o.keyLeft, HOLD[LEFT] > 0);
        set(o.keyRight, HOLD[RIGHT] > 0);
        set(o.keyJump, HOLD[JUMP] > 0);
        set(o.keyShift, HOLD[SNEAK] > 0);
        set(o.keySprint, HOLD[SPRINT] > 0);
        set(AwakenKeys.MOVEMENT, HOLD[TETHER] > 0);
    }

    private static void set(KeyMapping k, boolean down) {
        k.setDown(down);
    }

    private static void press(int key, int ticks) {
        HOLD[key] = Math.max(HOLD[key], ticks);
    }

    // ---- manoeuvres -----------------------------------------------------------------------------------------------------

    /**
     * Swinging down the lane: run, jump, fire at the trees ahead (the rope's aim assist picks them), pump through the
     * bottom, let go in the release window (every third one jumps off instead), fall a little, fire again; after
     * {@code n} swings, fly on and land.
     */
    private static void swing(LocalPlayer p) {
        AscentState st = AscentMoves.state();
        Minecraft mc = Minecraft.getInstance();
        // keep looking down the lane, a little up, so the assist finds the next trunk ahead
        p.setYRot(Mth.approachDegrees(p.getYRot(), laneYaw, 3F));
        p.setYHeadRot(p.getYRot());
        switch (st) {
            case NONE -> {
                if (count >= target) {
                    if (p.onGround() && age - mark > 10) script = "";
                    return;
                }
                p.setXRot(Mth.approach(p.getXRot(), -10F, 4F));
                press(FORWARD, 2);
                press(SPRINT, 2);
                if (p.onGround() && phase == 0 && age > 12) {
                    press(JUMP, 2);
                    phase = 1;
                    mark = age;
                }
                if (phase == 1 && !p.onGround() && age - mark > 5) {
                    press(TETHER, 2);
                    phase = 2;
                }
                if (phase == 2 && p.onGround()) {
                    // landed without a swing (a missed rope, a trunk in the face): turn off the line, as a player would
                    if (AscentMoves.missAt > AscentMoves.biteAt) laneYaw += (count % 2 == 0 ? 35F : -35F) * (++misses % 2 == 0 ? -1 : 1);
                    phase = 0;
                }
            }
            case TETHER -> {
                p.setXRot(Mth.approach(p.getXRot(), -8F, 3F));
                press(TETHER, 2);
                press(FORWARD, 2);
                double q = AscentRules.releaseQuality(AscentMoves.K, AscentMoves.ax, AscentMoves.ay, AscentMoves.az);
                if (AscentMoves.stateAge > 12 && q > 0.75) {
                    count++;
                    mark = age;
                    HOLD[TETHER] = 0;
                    if (count % 3 == 0) press(JUMP, 2);
                    phase = 3;
                }
                if (AscentMoves.stateAge > 90) {
                    count++;
                    HOLD[TETHER] = 0;
                    phase = 3;
                }
            }
            case ZIP -> press(TETHER, 2);
            case CARRY -> {
                p.setXRot(Mth.approach(p.getXRot(), -12F, 4F));
                press(FORWARD, 2);
                if (count < target && AscentMoves.K.vy < -0.05 && age - mark > 6) press(TETHER, 2);
            }
            default -> {
            }
        }
        // never carried into the next scene: about 3.5 s a swing at most
        if (age > Math.max(320, 70 * target)) stop();
    }

    /**
     * Off the tower: walk out, open the wings once falling, then a shallow glide; with {@code boom}, a steep dive until
     * the boom, then a pull-up and a glide out.
     */
    private static void glide(LocalPlayer p, boolean boom) {
        AscentState st = AscentMoves.state();
        switch (phase) {
            case 0 -> {
                press(FORWARD, 2);
                if (!p.onGround() && p.getDeltaMovement().y < -0.25) {
                    press(JUMP, 2);
                    phase = 1;
                    mark = age;
                }
            }
            case 1 -> {
                if (st == AscentState.GLIDE) {
                    phase = 2;
                    mark = age;
                } else if (age - mark > 6) {
                    press(JUMP, 2);
                    mark = age;
                }
            }
            case 2 -> {
                float want = boom ? 62F : 14F;
                p.setXRot(Mth.approach(p.getXRot(), want, boom ? 3F : 1F));
                if (boom && AscentMoves.boomAt > 0 && AscentMoves.ticks - AscentMoves.boomAt >= 1) {
                    phase = 3;
                    mark = age;
                }
                if (!boom && age - mark > 60) {
                    phase = 3;
                    mark = age;
                }
                if (st == AscentState.NONE) phase = 9;
            }
            case 3 -> {
                // pull up, then settle into a glide
                p.setXRot(Mth.approach(p.getXRot(), age - mark < 22 ? -30F : 8F, 6F));
                if (st == AscentState.NONE) phase = 9;
            }
            default -> script = "";
        }
        if (age > 1200) script = "";
    }

    /** Off the tower into flight: wings, take flight, cruise, boost, bank through a turn, slow to a hover. */
    /**
     * Up a sky platform's ladder (SPEC §17.9): look at the underside of the next anchor ring above (or, with none
     * left, the side of the pier over the chain) and throw the rope; from each hang under a ring, let go and throw at
     * the next. On the deck, walk round the cabin to its door and in to the core. Ends with the wings or after 2 min.
     */
    private static void climb(LocalPlayer p) {
        AscentState st = AscentMoves.state();
        if (core == null && (age % 10 == 1 || p.onGround())) core = coreNear(p);
        if (core != null && p.getY() > core.getY() - 1.5) {
            // up: hold the rope until the pull-over sets the feet down, then walk
            if (AscentMoves.has(dev.awaken.body.ascent.AscentPayloads.Tier.WINGS)) {
                Awaken.LOG.info("[ascent] pilot: the core gave wings");
                script = "";
            } else if (st == AscentState.NONE && p.onGround()) {
                walkToCore(p, core);
            }
            return;
        }
        if (AscentMoves.pullTicks > 0) return;
        net.minecraft.core.BlockPos target = null;
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
        int px = p.getBlockX(), py = p.getBlockY(), pz = p.getBlockZ();
        double best = Double.MAX_VALUE;
        // the next ring up: not the one just hung from (a few blocks over the head)
        for (int dy = 7; dy <= 40 && target == null; dy++) {
            for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) {
                m.set(px + dx, py + dy, pz + dz);
                if (!p.level().getBlockState(m).is(dev.awaken.body.ascent.AscentContent.ANCHOR_RING.get())) continue;
                double d = dx * dx + dz * dz;
                if (d < best) {
                    best = d;
                    target = m.immutable();
                }
            }
        }
        double tx, ty, tz;
        boolean perch = target != null && !p.level().getBlockState(target.east()).isAir() && !p.level().getBlockState(target.north()).isAir();
        if (target != null && perch) {
            // the top ring stands in a perch: the underside of its plate nearest the body, just in from the outer
            // edge (the zip vaults the body over that lip)
            int cx = target.getX() + Mth.clamp(px - target.getX(), -1, 1), cz = target.getZ() + Mth.clamp(pz - target.getZ(), -1, 1);
            double ox = p.getX() - (cx + 0.5), oz = p.getZ() - (cz + 0.5);
            boolean alongX = Math.abs(ox) >= Math.abs(oz);
            tx = cx + 0.5 + (alongX ? Math.signum(ox) * 0.35 : 0);
            ty = target.getY() - 0.02;
            tz = cz + 0.5 + (alongX ? 0 : Math.signum(oz) * 0.35);
        } else if (target != null) {
            tx = target.getX() + 0.5;
            ty = target.getY() - 0.02; // its underside
            tz = target.getZ() + 0.5;
        } else {
            // the pier: the top of the chain column nearby, its +z side
            // the nearest chain top with a block over it (the top ring's own chain under the pier's arm)
            net.minecraft.core.BlockPos pier = null;
            double near = Double.MAX_VALUE;
            for (int dy = 3; dy <= 40; dy++) for (int dx = -5; dx <= 5; dx++) for (int dz = -5; dz <= 5; dz++) {
                m.set(px + dx, py + dy, pz + dz);
                if (p.level().getBlockState(m).is(net.minecraft.world.level.block.Blocks.IRON_CHAIN)
                        && !p.level().getBlockState(m.above()).is(net.minecraft.world.level.block.Blocks.IRON_CHAIN)
                        && !p.level().getBlockState(m.above()).isAir() && dx * dx + dz * dz < near) {
                    near = dx * dx + dz * dz;
                    pier = m.above().immutable();
                }
            }
            if (pier == null) {
                if (st == AscentState.NONE && p.onGround() && age > 20) script = "";
                return;
            }
            // its face toward the body (+x when right under it)
            double ox = p.getX() - (pier.getX() + 0.5), oz = p.getZ() - (pier.getZ() + 0.5);
            boolean alongX = Math.abs(ox) >= Math.abs(oz) || Math.abs(ox) + Math.abs(oz) < 0.3;
            double sx = alongX ? (ox >= 0 ? 1 : -1) : 0, sz = alongX ? 0 : (oz >= 0 ? 1 : -1);
            tx = pier.getX() + 0.5 + sx * 0.55;
            ty = pier.getY() + 0.5;
            tz = pier.getZ() + 0.5 + sz * 0.55;
        }
        net.minecraft.world.phys.Vec3 eye = p.getEyePosition();
        double dx = tx - eye.x, dy = ty - eye.y, dz = tz - eye.z;
        p.setYRot((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG));
        p.setYHeadRot(p.getYRot());
        p.setXRot((float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
        switch (st) {
            case NONE, CARRY -> {
                if (age - mark > 6) {
                    press(TETHER, 3);
                    mark = age;
                }
            }
            case TETHER -> {
                // hanging under a ring: let go and throw again
                if (AscentMoves.stateAge > 8) {
                    HOLD[TETHER] = 0;
                    mark = age - 4;
                } else {
                    press(TETHER, 2);
                }
            }
            case ZIP -> press(TETHER, 2);
            default -> {
            }
        }
        if (age > 2400) script = "";
    }

    /**
     * Over a chasm (the Leaper's Gauntlet, SPEC §17.10): sprint straight on, jump at the edge, dash a few ticks up
     * (sprint let go and pressed again), jump again as the rise runs out, and stop once down. Ends 60 ticks after
     * landing (or after 15 s).
     */
    private static void leap(LocalPlayer p) {
        if (phase < 3) press(FORWARD, 2);
        // sprint held on the ground; let go in the air and pressed again: a dash
        if (phase == 0 || phase == 2 || (phase == 1 && age - mark > 4)) press(SPRINT, phase == 1 ? 1 : 2);
        boolean ground = p.onGround();
        switch (phase) {
            case 0 -> {
                // the edge: no floor a stride ahead
                double fx = -Mth.sin(p.getYRot() * Mth.DEG_TO_RAD), fz = Mth.cos(p.getYRot() * Mth.DEG_TO_RAD);
                net.minecraft.core.BlockPos ahead = net.minecraft.core.BlockPos.containing(p.getX() + fx * 1.1, p.getY() - 0.5, p.getZ() + fz * 1.1);
                if (ground && age > 4 && p.level().getBlockState(ahead).isAir()) {
                    press(JUMP, 2);
                    phase = 1;
                    mark = age;
                }
            }
            case 1 -> {
                // a dash a few ticks up (sprint pressed again), then the second jump as the rise runs out
                if (!ground && age - mark > 8 && p.getDeltaMovement().y < 0.12) {
                    press(JUMP, 2);
                    phase = 2;
                    mark = age;
                }
            }
            case 2 -> {
                if (ground && age - mark > 4) {
                    phase = 3;
                    mark = age;
                }
            }
            default -> {
                if (age - mark > 60) script = "";
            }
        }
        if (age > 300) script = "";
    }

    /**
     * Up a Tether Spire (SPEC §17.11): the next platform above (its lit heart 6 to 13 up; at the last, the crown's deck),
     * its rim cell nearest the body; aim at that cell's underside just in from its edge toward the body and throw (the
     * zip vaults over the lip); once on it, the next. Ends at the crown or after 2 min.
     */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> SPIRE_BLOCKS =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, Awaken.id("ward_sealed"));

    private static void spire(LocalPlayer p) {
        AscentState st = AscentMoves.state();
        if (AscentMoves.pullTicks > 0) return;
        // on the way up: hold on whatever the search sees from here
        if (st == AscentState.ZIP) {
            press(TETHER, 2);
            return;
        }
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
        int px = p.getBlockX(), py = p.getBlockY(), pz = p.getBlockZ();
        // at the crown: done
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 1; dy++) {
            if (p.level().getBlockState(m.set(px + dx, py + dy, pz + dz)).is(dev.awaken.body.ascent.AscentContent.SPIRE_CROWN.get())
                    && p.onGround()) {
                Awaken.LOG.info("[ascent] pilot: at the spire's crown");
                script = "";
                return;
            }
        }
        // the next platform: its lit heart 6 to 13 up (a glow panel with air over it), else the crown's deck
        net.minecraft.core.BlockPos heart = null;
        for (int dy = 6; dy <= 13 && heart == null; dy++) {
            for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
                m.set(px + dx, py + dy, pz + dz);
                if (p.level().getBlockState(m).is(dev.awaken.above.AboveBlocks.GLOW_PANEL.get()) && p.level().getBlockState(m.above()).isAir()) {
                    heart = m.immutable();
                }
            }
        }
        int reach = 1;
        if (heart == null) {
            for (int dy = 6; dy <= 14 && heart == null; dy++) {
                for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) {
                    m.set(px + dx, py + dy, pz + dz);
                    if (p.level().getBlockState(m).is(dev.awaken.body.ascent.AscentContent.SPIRE_CROWN.get())) heart = m.below().immutable();
                }
            }
            reach = 2;
        }
        net.minecraft.core.BlockPos target = null;
        double best = Double.MAX_VALUE;
        if (heart != null) {
            // its rim cell nearest the body
            for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != reach) continue;
                m.set(heart.getX() + dx, heart.getY(), heart.getZ() + dz);
                if (p.level().getBlockState(m).getCollisionShape(p.level(), m).isEmpty()) continue;
                double d = (m.getX() + 0.5 - p.getX()) * (m.getX() + 0.5 - p.getX()) + (m.getZ() + 0.5 - p.getZ()) * (m.getZ() + 0.5 - p.getZ());
                if (d < best) {
                    best = d;
                    target = m.immutable();
                }
            }
        }
        if (target == null) {
            if (age > 40) script = "";
            return;
        }
        double ox = p.getX() - (target.getX() + 0.5), oz = p.getZ() - (target.getZ() + 0.5);
        boolean alongX = Math.abs(ox) >= Math.abs(oz);
        double tx = target.getX() + 0.5 + (alongX ? Math.signum(ox) * 0.35 : 0);
        double tz = target.getZ() + 0.5 + (alongX ? 0 : Math.signum(oz) * 0.35);
        double ty = target.getY() - 0.02;
        net.minecraft.world.phys.Vec3 eye = p.getEyePosition();
        double dx = tx - eye.x, dy = ty - eye.y, dz = tz - eye.z;
        p.setYRot((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG));
        p.setYHeadRot(p.getYRot());
        p.setXRot((float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
        switch (st) {
            case NONE, CARRY -> {
                if (age - mark > 8) {
                    press(TETHER, 3);
                    mark = age;
                }
            }
            case TETHER -> {
                // a hang (a ring): let go and throw again
                if (AscentMoves.stateAge > 8) {
                    HOLD[TETHER] = 0;
                    mark = age - 4;
                } else {
                    press(TETHER, 2);
                }
            }
            case ZIP -> press(TETHER, 2);
            default -> {
            }
        }
        if (age > 2400) script = "";
    }

    /** The sky platform's core, if one is in sight (on the deck's level or just under it). */
    private static net.minecraft.core.@org.jspecify.annotations.Nullable BlockPos core;

    private static net.minecraft.core.@org.jspecify.annotations.Nullable BlockPos coreNear(LocalPlayer p) {
        net.minecraft.core.BlockPos.MutableBlockPos m = new net.minecraft.core.BlockPos.MutableBlockPos();
        int px = p.getBlockX(), py = p.getBlockY(), pz = p.getBlockZ();
        for (int dy = -10; dy <= 10; dy++) for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++) {
            m.set(px + dx, py + dy, pz + dz);
            if (p.level().getBlockState(m).is(dev.awaken.body.ascent.AscentContent.WING_CORE.get())) return m.immutable();
        }
        return null;
    }

    /**
     * On the deck: in along the pier, round the cabin (walls 4 out from the core, one door in some side) to 5.5 before
     * its door, then in.
     * Each tick picks its waypoint from where the body stands, along the door's side and across it.
     */
    private static void walkToCore(LocalPlayer p, net.minecraft.core.BlockPos c) {
        double ox = c.getX() + 0.5, oz = c.getZ() + 0.5;
        net.minecraft.core.Direction door = null;
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            net.minecraft.core.BlockPos q = c.relative(d, 4);
            if (p.level().getBlockState(q).getCollisionShape(p.level(), q).isEmpty()
                    && p.level().getBlockState(q.above()).getCollisionShape(p.level(), q.above()).isEmpty()) door = d;
        }
        if (door == null) return;
        double dx = door.getStepX(), dz = door.getStepZ(), sx = -dz, sz = dx;
        double rx = p.getX() - ox, rz = p.getZ() - oz;
        double along = rx * dx + rz * dz, across = rx * sx + rz * sz, side = across >= 0 ? 1 : -1;
        double ta, tc;
        if (rx * rx + rz * rz > 10 * 10) {
            // still on the pier (it runs straight out from the core): in along it to the deck
            ta = 0;
            tc = 0;
        } else if ((Math.abs(along) < 4 && Math.abs(across) < 4) || (along >= 3.5 && Math.abs(across) <= 0.35)) {
            // inside, or lined up on the door: straight to the core
            ta = 0;
            tc = 0;
            if (Math.hypot(rx, rz) < 1.6) return; // at the core: it answers within half a second
        } else if (along >= 4.5) {
            ta = 5.5;
            tc = 0;
        } else if (Math.abs(across) >= 5.5) {
            ta = 6.5;
            tc = 6.5 * side;
        } else {
            ta = along;
            tc = 6.5 * side;
        }
        double tx = ox + ta * dx + tc * sx, tz = oz + ta * dz + tc * sz;
        if (age % 20 == 0) Awaken.LOG.info("[ascent] pilot: deck walk at {} {} (door {}), to {} {}", String.format("%.1f", along),
                String.format("%.1f", across), door.getName(), String.format("%.1f", tx - ox), String.format("%.1f", tz - oz));
        p.setYRot((float) (Mth.atan2(-(tx - p.getX()), tz - p.getZ()) * Mth.RAD_TO_DEG));
        p.setYHeadRot(p.getYRot());
        p.setXRot(10);
        press(FORWARD, 2);
        if (p.horizontalCollision) press(JUMP, 2);
    }

    private static void soar(LocalPlayer p) {
        AscentState st = AscentMoves.state();
        switch (phase) {
            case 0 -> {
                press(FORWARD, 2);
                if (!p.onGround() && p.getDeltaMovement().y < -0.25) {
                    press(JUMP, 2);
                    phase = 1;
                    mark = age;
                }
            }
            case 1 -> {
                if (st == AscentState.GLIDE && age - mark > 8) {
                    press(JUMP, 2);
                    phase = 2;
                    mark = age;
                } else if (st != AscentState.GLIDE && age - mark > 6) {
                    // the first air jump is the Leaper's double jump: the next one opens the wings
                    press(JUMP, 2);
                    mark = age;
                }
            }
            case 2 -> {
                press(FORWARD, 2);
                p.setXRot(Mth.approach(p.getXRot(), -6F, 2F));
                if (age - mark > 30) {
                    phase = 3;
                    mark = age;
                }
            }
            case 3 -> {
                press(FORWARD, 2);
                press(SPRINT, 2);
                if (age - mark > 50) {
                    phase = 4;
                    mark = age;
                }
            }
            case 4 -> {
                // a banked turn
                press(FORWARD, 2);
                press(SPRINT, 2);
                p.setYRot(p.getYRot() + 3.2F);
                p.setYHeadRot(p.getYRot());
                if (age - mark > 45) {
                    phase = 5;
                    mark = age;
                }
            }
            case 5 -> {
                // let go of forward: slow to a hover
                if (age - mark > 50) script = "";
            }
            default -> script = "";
        }
    }
}
