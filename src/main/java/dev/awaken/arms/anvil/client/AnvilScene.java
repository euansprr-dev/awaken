package dev.awaken.arms.anvil.client;

import dev.awaken.arms.LordForged;
import dev.awaken.arms.anvil.AnvilSong;
import dev.awaken.arms.anvil.AnvilWork;
import dev.awaken.arms.anvil.WarAnvilBlock;
import dev.awaken.arms.anvil.WarAnvilBlockEntity;
import dev.awaken.arms.client.WeaponDraw;
import dev.awaken.core.Forces;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Where everything at a War Anvil is this moment (SPEC §46.3), worked out once a frame for the renderer, the hands and
 * the screen alike: the work (in the Forge's mouth, carried to the anvil, drawn out strike by strike, turned over, over
 * the trough and into it, the hilt added tap by tap, standing in the slot), the hammer and tongs (in your hands, laid on
 * the anvil, or on the peg), the marks, and the view, which stays at your eyes and turns to the work.
 * <p>
 * Positions are block-relative (the anvil's own corner at 0). The anvil's model faces north, the smith standing on that
 * side; the blockstate turns it, and so does {@link #model}. A weapon lies by its design (texels: x across from its back
 * to its edge, y along from the pommel to the point, z through), its mesh in item space (arms_weapons.to_item).
 */
final class AnvilScene {
    private AnvilScene() {}

    /** Blocks a design texel of the work: the Longsword comes to about a block (a longer design a little less, AnvilShape). */
    static final float TEXEL = 0.035F;
    /** Blocks a texel of the hammer and tongs. */
    static final float TOOL = 0.025F;
    /**
     * The blade across and through (texels): its middle across, half its width, its face up off the middle, its back. How
     * long it is and where its parts lie are its own, read from its mesh ({@link AnvilShape}).
     */
    static final float BLADE_X = -0.5F, HALF_W = 3F, FACE_Z = 1.5F, BACK_Z = 2.5F;
    /** Ticks the view glides between its marks, and the work between its places. */
    static final float GLIDE = 14F, CARRY = 12F;
    /** The hammer's angle above the strike (degrees): at rest, raised for a strike, raised for a tap. */
    private static final float REST = 18F, RAISED = 70F, RAISED_TAP = 35F, DOWN = 1.5F;

    /** Item space to design texels (arms_weapons.to_item undone: its -45° turned back, its grip at 3, 3, 8 moved home). */
    static final Matrix4f ITEM = new Matrix4f().rotateZ(Mth.PI / 4).scale(16F).translate(-3F / 16, -3F / 16, -0.5F);

    // ---- the anvil's frame ------------------------------------------------------------------------------------------

    /** The blockstate's turn for a facing (north 0, east 90, south 180, west 270), as a turn of the model. */
    static Quaternionf frame(BlockState st, Quaternionf out) {
        Direction f = st.hasProperty(WarAnvilBlock.FACING) ? st.getValue(WarAnvilBlock.FACING) : Direction.NORTH;
        float y = (f.toYRot() + 180F) % 360F;
        return out.rotationY(-y * Mth.DEG_TO_RAD);
    }

    /** A point of the model (blocks, its own 0..1) where the turned block has it, block-relative. */
    static Vector3f model(Quaternionf frame, float x, float y, float z, Vector3f out) {
        return out.set(x - 0.5F, y, z - 0.5F).rotate(frame).add(0.5F, 0F, 0.5F);
    }

    /** A direction of the model, turned with the block. */
    static Vector3f dir(Quaternionf frame, float x, float y, float z, Vector3f out) {
        return out.set(x, y, z).rotate(frame);
    }

    // ---- how a weapon lies --------------------------------------------------------------------------------------------

    /**
     * Where a weapon lies: the point its middle is at, its turn (the design's axes to the world's), how drawn out it is
     * (across, along, through), which design height is its middle, and the weapon's own shape.
     */
    static final class Lay {
        final Vector3f at = new Vector3f();
        final Quaternionf turn = new Quaternionf();
        final Vector3f scale = new Vector3f(1F, 1F, 1F);
        AnvilShape shape = AnvilShape.FALLBACK;
        float mid = AnvilShape.FALLBACK.mid;
        /** Whose song it is (its marks) and the strike after which it's turned over: the work's, not moved by set or mix. */
        AnvilSong.Song song = AnvilSong.song("longsword");
        int flipAt = AnvilSong.song("longsword").flipAfter();
        /** Attuning (§46.8): every tap lands on the guard, driving the stone home. */
        boolean guard;

        Lay set(Lay o) {
            at.set(o.at);
            turn.set(o.turn);
            scale.set(o.scale);
            shape = o.shape;
            mid = o.mid;
            return this;
        }

        /** Between two lays (k 0..1, eased), lifted on an arc of {@code arc} blocks at its middle. */
        Lay mix(Lay a, Lay b, float k, float arc) {
            float e = k * k * (3F - 2F * k);
            at.set(a.at).lerp(b.at, e).add(0F, arc * Mth.sin(e * Mth.PI), 0F);
            a.turn.slerp(b.turn, e, turn);
            scale.set(a.scale).lerp(b.scale, e);
            mid = Mth.lerp(e, a.mid, b.mid);
            shape = b.shape;
            return this;
        }

        /** A design point (texels) where this lay puts it. */
        Vector3f point(float x, float y, float z, Vector3f out) {
            return out.set(x * scale.x, (y - mid) * scale.y, z * scale.z).mul(shape.texel).rotate(turn).add(at);
        }

        /** Its pose for the weapon's mesh (item space). */
        Matrix4f matrix(Matrix4f out) {
            float t = shape.texel;
            return out.translation(at).rotate(turn).scale(scale.x * t, scale.y * t, scale.z * t).translate(0F, -mid, 0F).mul(ITEM);
        }
    }

    private static final Matrix3f M3 = new Matrix3f();
    private static final Matrix4f M4 = new Matrix4f();
    private static final Vector3f TA = new Vector3f(), TU = new Vector3f(), TW = new Vector3f();

    /** The turn that lays the design's y along {@code a} and its z along {@code u} (x across, as y × z). */
    static Quaternionf turn(Vector3f a, Vector3f u, Quaternionf out) {
        TA.set(a).normalize();
        TU.set(u).sub(TA.x * TA.dot(u), TA.y * TA.dot(u), TA.z * TA.dot(u)).normalize();
        TA.cross(TU, TW);
        M3.set(TW, TA, TU);
        return out.setFromNormalized(M3);
    }

    private static final Vector3f A = new Vector3f(), U = new Vector3f(), W = new Vector3f(), P = new Vector3f();

    /**
     * On the anvil's face: drawn out d (0 a squat bar, 1 its own shape), turned over by {@code flip} (0..1), the blade's
     * middle over the face's (or the whole weapon's, {@code whole}).
     */
    static Lay onAnvil(Quaternionf frame, AnvilShape shape, float d, float flip, boolean whole, Lay out) {
        out.scale.set(1.5F - 0.5F * d, 0.5F + 0.5F * d, 1.5F - 0.5F * d);
        out.shape = shape;
        out.mid = whole ? shape.wholeMid : shape.mid;
        float lift = BACK_Z * shape.texel * out.scale.z + 0.07F * Mth.sin(flip * Mth.PI);
        model(frame, 0.5F, 1F + lift, 9.5F / 16F, out.at);
        // the point toward the horn, flat up, then turned over about its length
        dir(frame, -1F, 0F, 0F, A);
        dir(frame, 0F, 1F, 0F, U);
        A.cross(U, W);
        float c = Mth.cos(flip * Mth.PI), s = Mth.sin(flip * Mth.PI);
        U.mul(c).add(W.x * s, W.y * s, W.z * s);
        turn(A, U, out.turn);
        return out;
    }

    /**
     * The whole weapon on the face turned end for end, its hilt to your right: for the work at the hilt (the bind, the
     * hilt's taps), so the hammer's hand and the tongs' each stay on their own side rather than crossing over it.
     */
    static Lay hiltRight(Quaternionf frame, AnvilShape shape, Lay out) {
        onAnvil(frame, shape, 1F, 0F, true, out);
        out.turn.rotateLocalY(Mth.PI);
        return out;
    }

    /** Half in the Forge's mouth, squat, its point in the fire. */
    static Lay inForge(BlockPos anvil, BlockPos forge, Direction mouth, AnvilShape shape, Lay out) {
        out.scale.set(1.5F, 0.5F, 1.5F);
        out.shape = shape;
        out.mid = shape.mid;
        out.at.set(forge.getX() - anvil.getX() + 0.5F + mouth.getStepX() * 0.46F, forge.getY() - anvil.getY() + 0.3F,
                forge.getZ() - anvil.getZ() + 0.5F + mouth.getStepZ() * 0.46F);
        turn(A.set(-mouth.getStepX(), 0F, -mouth.getStepZ()), U.set(0F, 1F, 0F), out.turn);
        return out;
    }

    /** Held level over the trough, its edge down (the trough is narrower than the blade is wide). */
    static Lay overTrough(Quaternionf frame, AnvilShape shape, Lay out) {
        out.scale.set(1F, 1F, 1F);
        out.shape = shape;
        out.mid = shape.mid;
        model(frame, 14F / 16F, 0.7F, 8F / 16F, out.at);
        turn(dir(frame, 0F, 0F, 1F, A), dir(frame, -1F, 0F, 0F, U), out.turn);
        return out;
    }

    /**
     * In the trough, point down a little: the point by the trough's far end under the water, the rest rising back over
     * the near rim however long the blade is.
     */
    static Lay inTrough(Quaternionf frame, AnvilShape shape, Lay out) {
        out.scale.set(1F, 1F, 1F);
        out.shape = shape;
        out.mid = shape.mid;
        dir(frame, 0F, -0.42F, 0.906F, A).normalize();
        float back = (shape.tip - shape.mid) * shape.texel;
        model(frame, 14F / 16F, 0.322F, 0.754F, out.at).sub(A.x * back, A.y * back, A.z * back);
        turn(A, dir(frame, -1F, 0F, 0F, U), out.turn);
        return out;
    }

    /** Standing hilt up in the slot, its face to the smith, the guard on the stone; drawn {@code rise} blocks up. */
    static Lay inSlot(Quaternionf frame, AnvilShape shape, float rise, Lay out) {
        out.scale.set(1F, 1F, 1F);
        out.shape = shape;
        out.mid = shape.root - 0.5F; // the blade's root just in the slot, the guard resting on the stone
        model(frame, 0.5F, 0.51F + rise, 3F / 16F, out.at);
        turn(dir(frame, 0F, -1F, 0F, A), dir(frame, 0F, 0F, -1F, U), out.turn);
        return out;
    }

    /**
     * In the rack behind the anvil (§46.6, from the Deepglass horn): the k-th of three standing point down on the stone
     * between the rack's posts, turned to the smith's left and leaning back onto its bar.
     */
    static Lay inRack(Quaternionf frame, AnvilShape shape, int k, Lay out) {
        out.scale.set(1F, 1F, 1F);
        out.shape = shape;
        out.mid = shape.tip;
        model(frame, (3.8F + 2.7F * k) / 16F, 0.505F, 14.2F / 16F, out.at);
        turn(dir(frame, 0F, -1F, -0.12F, A), dir(frame, -0.82F, 0F, -0.57F, U), out.turn);
        return out;
    }

    // ---- the smith's own moment (the song screen sets these) -----------------------------------------------------------

    /** The scene's anvil (the song screen open on it), or null. */
    static @Nullable BlockPos pos;
    /** Beats struck so far as this client has them (ahead of the server's by a moment). */
    static int struck;
    /** The game time of the last blow (a click), the hammer's angle then, and the beat it was. */
    static float blowAt = -1000F, blowFrom = RAISED;
    static int blowBeat = -1;
    /** When the next beat is due (game time), or NaN when none is coming. */
    static float nextDue = Float.NaN;
    /** When this client plunged the blade (game time, NaN not yet) and lifted it. */
    static float plungeAt = Float.NaN, liftAt = Float.NaN;
    /** How far the draw is held (0..1). */
    static float drawK;
    /** The bind: the cord's turn round the grip (radians, as this client winds it) and the turns done, or -1 not winding. */
    static float windAngle;
    static int windTurns = -1;
    /**
     * The ranks' acts (§46.9), as this client has them: the edge off the wheel's line (0..1) and whether the wheel turns
     * (the grind); the blade tipped on the horn (-1..1, the point down at 1) and the weight along the grip (0 the guard, 1
     * the pommel's end; the balance).
     */
    static float grindEdge, tilt, weight;
    static boolean turning;

    static void reset() {
        struck = 0;
        blowAt = -1000F;
        blowBeat = -1;
        nextDue = Float.NaN;
        plungeAt = liftAt = Float.NaN;
        drawK = 0F;
        windAngle = 0F;
        windTurns = -1;
        grindEdge = tilt = weight = 0F;
        turning = false;
        wheelFor = Long.MIN_VALUE;
        wheel = null;
    }

    // ---- this moment, placed ------------------------------------------------------------------------------------------

    /** Everything at one anvil this frame. */
    static final class Placed {
        final Lay work = new Lay();
        boolean hasWork;
        /** The shader's heat (0..255, -1 none), its front along the blade (0..255) and its flags. */
        int heat = -1, front, flags;
        /** It's drawn from here along it on (the hilt's parts come tap by tap). */
        float cut;
        /** The forge's light on it (in the mouth) rather than the anvil's. */
        boolean inForge;
        final Matrix4f hammer = new Matrix4f(), tongs = new Matrix4f();
        boolean hasTongs;
        /** At E the hammer is the stone hammer (the knap), the iron one on its peg. */
        boolean stone;
        /** Where the fists are (block-relative) and how much each shows: the hammer's (main), the tongs' (off). */
        final Vector3f mainFist = new Vector3f(), offFist = new Vector3f();
        float mainW, offW;
        /** The blade is in the water, hot: steam rises from here. */
        boolean steam;
        final Vector3f steamAt = new Vector3f();
        /** The glow's colour while it's worked (the shader's x bits 8-14: a Force's as its stone is set, a Lord's as it's reforged; 0 the Forge's). */
        int glow;
        /** Attuning: the catalyst set in the guard (block-relative), how big it's drawn, and its Force. */
        boolean hasGem;
        final Vector3f gemAt = new Vector3f();
        float gemScale;
        int gemForce;
        /** S (§46.9): the column of starlight over the anvil, 0..1 come down. */
        float column;
    }

    private static final Quaternionf FRAME = new Quaternionf();
    private static final Lay L1 = new Lay(), L2 = new Lay(), L3 = new Lay();
    private static final Vector3f G = new Vector3f(), X = new Vector3f(), Y = new Vector3f(), Z = new Vector3f(), T = new Vector3f(),
            T2 = new Vector3f(), EYE = new Vector3f(), RIGHT = new Vector3f();

    /**
     * Places everything at an anvil at game time {@code now}. {@code mine}: the scene is open on it (the hammer in your
     * hand, the moment as this client has it); otherwise as anyone near sees it.
     */
    static void place(WarAnvilBlockEntity be, float now, boolean mine, Placed out) {
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        frame(be.getBlockState(), FRAME);
        AnvilWork w = be.work();
        out.hasWork = false;
        out.hasTongs = false;
        out.heat = -1;
        out.cut = 0F;
        out.inForge = false;
        out.mainW = out.offW = 0F;
        out.steam = false;
        out.stone = false;
        out.glow = 0;
        out.hasGem = false;
        out.column = 0F;
        eye(pos, mine);
        if (w == null) {
            // the tools back on the peg; your hands on the hilt while you draw it
            pegHammer(out.hammer);
            if (mine && drawK > 0F && !be.slot().isEmpty()) {
                AnvilShape slot = AnvilShape.of(be.slot());
                inSlot(FRAME, slot, 0.22F * drawK, L1);
                L1.point(0F, slot.hands[0], 0F, out.mainFist);
                L1.point(0F, slot.hands[1], 0F, out.offFist);
                out.mainW = out.offW = Math.min(1F, drawK * 4F);
            }
            pegTongs(out.tongs);
            out.hasTongs = true;
            return;
        }
        out.hasWork = true;
        out.work.song = w.song;
        out.work.flipAt = w.memory() ? Integer.MAX_VALUE : w.song.flipAfter();
        out.work.guard = w.kind == AnvilWork.ATTUNE;
        out.stone = w.hand();
        AnvilShape sh = AnvilShape.of(w.weapon);
        float blade = sh.cut[0];
        boolean whole = w.whole();
        // the blade alone until the hilt; a weapon worked again is whole from the start
        out.cut = whole ? sh.cut[sh.cut.length - 1] : blade;
        // a Lord-forged weapon reforged glows in its Lord's colour (its accent), not the Forge's
        if (w.kind == AnvilWork.REFORGE && LordForged.byWeapon(w.weapon) != null) out.glow = 15 << 8 | 7 << 12;
        float t = now - w.stageStart;
        int n = mine ? Math.max(struck, w.beats().size()) : w.beats().size();
        Direction mouth = forgeMouth(level, w.forge);
        switch (w.stage) {
            case AnvilSong.HEAT -> {
                inForge(pos, w.forge, mouth, sh, out.work);
                out.inForge = true;
                float h = w.heat(t);
                heat(out, h, 1F - (1F - blade) * Mth.clamp(h / 0.65F, 0F, 1F), false);
                tongsAt(out, out.work, sh.root - 0.5F, T.set(mouth.getStepX(), 0.25F, mouth.getStepZ()), mine, true);
                restHammer(out.hammer);
            }
            case AnvilSong.SHAPE -> {
                int of = w.beatsNeeded(), flipAt = out.work.flipAt;
                float d = whole ? 1F : AnvilSong.drawn(n, of), flip = n >= flipAt ? 1F : 0F;
                if (mine && !whole && n == blowBeat + 1 && now - blowAt < DOWN + 3F) {
                    // the strike just landing moves the metal: drawn out a step, and the one at half turns it over
                    float k = Mth.clamp((now - blowAt - DOWN) / 3F, 0F, 1F);
                    d = AnvilSong.drawn(n - 1, of) + (d - AnvilSong.drawn(n - 1, of)) * k;
                }
                if (mine && n == flipAt && blowBeat == flipAt - 1)
                    flip = Mth.clamp((now - blowAt - DOWN - 3F) / 6F, 0F, 1F);
                onAnvil(FRAME, sh, d, flip, whole, out.work);
                if (w.scores.isEmpty() && t < GLIDE) {
                    // carried from the fire on a short arc
                    inForge(pos, w.forge, mouth, sh, L1);
                    L2.set(out.work);
                    out.work.mix(L1, L2, t / GLIDE, 0.35F);
                }
                heat(out, Math.max(0.3F, Math.max(0F, w.heat) * (1F - 0.45F * n / (float) of)), blade - 0.02F, false);
                tongsAt(out, out.work, sh.root - 0.5F, dir(FRAME, 1F, 0.35F, -0.8F, T), mine, false);
                if (mine) swing(out, now, n, STRIKE, of);
                else restHammer(out.hammer);
            }
            case AnvilSong.KNAP -> {
                // by hand: the bone or flint blade cold on the face, steadied by your off hand, struck flake by flake
                onAnvil(FRAME, sh, 1F, 0F, false, out.work);
                pegTongs(out.tongs);
                out.hasTongs = true;
                if (mine) {
                    out.work.point(BLADE_X, sh.root - 0.5F, FACE_Z, out.offFist);
                    out.offW = 1F;
                    swing(out, now, n, FLAKE, w.beatsNeeded());
                } else restHammer(out.hammer);
            }
            case AnvilSong.BIND -> {
                // the whole weapon on the face, its hilt to your right, the cord wound round its grip: each turn brings its hilt on
                hiltRight(FRAME, sh, out.work);
                if (t < CARRY) {
                    // turned round from the knap
                    onAnvil(FRAME, sh, 1F, 0F, false, L1);
                    L2.set(out.work);
                    out.work.mix(L1, L2, t / CARRY, 0.15F);
                }
                int turns = mine && windTurns >= 0 ? windTurns : Math.max(0, w.taps.size() - 1);
                out.cut = sh.cut[Math.min(turns, sh.cut.length - 1)];
                pegTongs(out.tongs);
                out.hasTongs = true;
                restHammer(out.hammer);
                if (mine) {
                    out.work.point(BLADE_X, sh.root + 0.7F * (sh.tip - sh.root), FACE_Z, out.offFist);
                    out.offW = 1F;
                    float r = HALF_W + 1F;
                    out.work.point(BLADE_X + r * Mth.cos(windAngle), sh.hands[0], r * Mth.sin(windAngle), out.mainFist);
                    out.mainW = windTurns >= 0 ? 1F : 0F;
                }
            }
            case AnvilSong.QUENCH -> {
                onAnvil(FRAME, sh, 1F, 1F, whole, L1);
                overTrough(FRAME, sh, L2);
                out.work.mix(L1, L2, Mth.clamp(t / CARRY, 0F, 1F), 0.15F);
                float plunged = mine && !Float.isNaN(plungeAt) ? plungeAt - w.stageStart : w.plunge;
                float front = 1F;
                if (plunged >= 0F) {
                    inTrough(FRAME, sh, L3);
                    L1.set(out.work);
                    out.work.mix(L1, L3, Mth.clamp((t - plunged) / 4F, 0F, 1F), 0F);
                    float end = mine && !Float.isNaN(liftAt) ? liftAt - w.stageStart : t;
                    float held = Math.min(Math.max(0F, end - plunged), AnvilSong.HOLD_MAX);
                    front = 1F - (1F - blade) * AnvilSong.line(held);
                    out.steam = t - plunged < 40F;
                    model(FRAME, 14F / 16F, 6.6F / 16F, 9F / 16F, out.steamAt);
                }
                heat(out, Math.max(0.3F, Math.max(0F, w.heat) * 0.55F), front, true);
                tongsAt(out, out.work, sh.root - 0.5F, dir(FRAME, 0F, 0.3F, -1F, T), mine, true);
                restHammer(out.hammer);
            }
            case AnvilSong.HILT -> {
                // back from the water onto the anvil, the whole weapon's length on the face, its hilt to your right
                if (w.plunge >= 0F) inTrough(FRAME, sh, L1);
                else overTrough(FRAME, sh, L1);
                hiltRight(FRAME, sh, L2);
                if (w.kind == AnvilWork.ATTUNE) {
                    // straight from the fire: the stone set in the guard, sinking in with each tap, the steel taking its colour
                    inForge(pos, w.forge, mouth, sh, L1);
                    out.work.mix(L1, L2, Mth.clamp(t / GLIDE, 0F, 1F), 0.35F);
                    float k = n / (float) AnvilSong.TAPS;
                    heat(out, Math.max(0.25F, Math.max(0F, w.heat) * (1F - 0.5F * k)), blade - 0.02F, false);
                    int force = Math.floorMod(w.target, Forces.Force.values().length);
                    out.glow = (force + 1) << 8 | Math.round(2F + 5F * k) << 12;
                    out.hasGem = true;
                    out.gemForce = force;
                    out.work.point(0F, sh.taps[0], 2.5F - 2F * k, out.gemAt);
                    out.gemScale = 0.3F * (1F - 0.4F * k);
                } else {
                    out.work.mix(L1, L2, Mth.clamp(t / CARRY, 0F, 1F), 0.15F);
                    out.cut = sh.cut[Math.min(n, AnvilSong.TAPS)];
                    // what heat it kept, fading
                    float front = w.held >= 0F ? 1F - (1F - blade) * AnvilSong.line(w.held) : 1F;
                    heat(out, 0.4F * Math.max(0F, 1F - t / 40F), front, true);
                }
                // up the blade, clear of the hilt being seated
                tongsAt(out, out.work, sh.root + 0.69F * (sh.tip - sh.root), dir(FRAME, 0.3F, 0.35F, -1F, T), mine, false);
                if (mine) swing(out, now, n, TAP, AnvilSong.TAPS);
                else restHammer(out.hammer);
            }
            case AnvilSong.TWIST -> {
                // A (§46.9): the bar clamped on the face, hot, turned on its own length as you circle; the off hand on the tongs
                onAnvil(FRAME, sh, 0F, 0F, false, out.work);
                if (t < GLIDE) {
                    inForge(pos, w.forge, mouth, sh, L1);
                    L2.set(out.work);
                    out.work.mix(L1, L2, t / GLIDE, 0.35F);
                }
                int turns = Math.max(0, w.taps.size() - 1);
                float turned = mine && windTurns >= 0 ? windAngle : turns * Mth.TWO_PI;
                out.work.turn.rotateY(turned * 0.25F);
                heat(out, Math.max(0.3F, Math.max(0F, w.heat)), blade - 0.02F, false);
                // the tongs' handles low to your left, the turning hand just past the bar's end: neither arm over the bar
                tongsAt(out, out.work, sh.root - 0.5F, dir(FRAME, 1F, 0.2F, -1.2F, T), mine, false);
                restHammer(out.hammer);
                if (mine) {
                    float r = 1.5F;
                    out.work.point(BLADE_X + r * Mth.cos(windAngle), sh.tip + 7F, r * Mth.sin(windAngle), out.mainFist);
                    out.mainW = windTurns >= 0 ? 1F : 0.6F;
                }
            }
            case AnvilSong.GRIND -> {
                // C (§46.9): at the wheel, the blade laid across its rim edge down, sliding along it; at S warm from the light
                BlockPos at = wheel(level, pos, w.stageStart);
                float slide = !mine || turning ? Mth.sin(t * 0.09F) * 0.12F : 0F;
                if (at != null) atWheel(level, pos, at, sh, (mine ? grindEdge : 0F) * 0.035F, slide, out.work);
                else onAnvil(FRAME, sh, 1F, 0F, false, out.work);
                if (t < GLIDE * 1.5F) {
                    if (w.star >= 0F) inLight(FRAME, sh, 1F, L1);
                    else if (w.plunge >= 0F) inTrough(FRAME, sh, L1);
                    else overTrough(FRAME, sh, L1);
                    L2.set(out.work);
                    out.work.mix(L1, L2, t / (GLIDE * 1.5F), 0.3F);
                }
                pegTongs(out.tongs);
                out.hasTongs = true;
                pegHammer(out.hammer);
                if (mine) {
                    out.work.point(0F, sh.root - 1F, 0F, out.mainFist);
                    out.work.point(0F, sh.root + 0.9F * (sh.tip - sh.root), HALF_W * 0.5F, out.offFist);
                    out.mainW = out.offW = 1F;
                }
            }
            case AnvilSong.BALANCE -> {
                // B (§46.9): the whole weapon across the Deepglass horn, tipping to its heavy end, the weight sliding along it
                hiltRight(FRAME, sh, L1);
                onHorn(FRAME, sh, mine ? tilt : 0F, L2);
                out.work.mix(L1, L2, Mth.clamp(t / CARRY, 0F, 1F), 0.12F);
                out.cut = sh.cut[sh.cut.length - 1];
                out.hasGem = true;
                out.gemForce = Forces.Force.SPIRIT.ordinal(); // its catalyst is the Deepglass shard
                out.gemScale = 0.2F;
                out.work.point(0F, Mth.lerp(mine ? weight : 0.5F, sh.taps[0], sh.taps[2]), 1.2F, out.gemAt);
                pegTongs(out.tongs);
                out.hasTongs = true;
                pegHammer(out.hammer);
                if (mine) {
                    out.gemAt.y += 0.02F;
                    out.mainFist.set(out.gemAt).add(0F, 0.06F, 0F);
                    out.mainW = 1F;
                }
            }
            case AnvilSong.STAR -> {
                // S (§46.9): held point up into the column of starlight over the anvil, the glow turning white as it drinks it
                onAnvil(FRAME, sh, 1F, 1F, false, L1);
                inLight(FRAME, sh, Mth.clamp(t / 30F, 0F, 1F), L2);
                out.work.mix(L1, L2, Mth.clamp(t / GLIDE, 0F, 1F), 0.2F);
                out.column = Mth.clamp(t / 30F, 0F, 1F);
                float k = Mth.clamp(t / AnvilSong.STAR_AT, 0F, 1F);
                heat(out, Math.max(0.3F, Math.max(0F, w.heat) * 0.6F), blade - 0.02F, false);
                out.glow = 6 << 8 | Math.round(1F + 6F * k) << 12;
                pegTongs(out.tongs);
                out.hasTongs = true;
                pegHammer(out.hammer);
                if (mine) {
                    out.work.point(0F, sh.root - 1F, 0F, out.mainFist);
                    out.mainW = 1F;
                }
            }
            default -> restHammer(out.hammer);
        }
    }

    /** The grindstone the work is ground at (§46.9): the nearest within six blocks of the anvil, read once a stage. */
    private static long wheelFor = Long.MIN_VALUE;
    private static @Nullable BlockPos wheel;
    /**
     * The wheel's front or back, whichever you stood on when the act began (the view's side: the blade lies across it), and
     * whether the blade lies turned so its grip is to your right from there, under the hammer hand.
     */
    private static int wheelSideX, wheelSideZ;
    private static boolean wheelTurned;

    static @Nullable BlockPos wheel(@Nullable Level level, BlockPos anvil, long stageStart) {
        if (stageStart == wheelFor || level == null) return wheel;
        wheelFor = stageStart;
        wheel = null;
        double best = Double.MAX_VALUE;
        for (BlockPos b : BlockPos.betweenClosed(anvil.offset(-6, -2, -6), anvil.offset(6, 2, 6))) {
            if (!level.getBlockState(b).is(Blocks.GRINDSTONE)) continue;
            double d = b.distSqr(anvil);
            if (d < best) {
                best = d;
                wheel = b.immutable();
            }
        }
        var me = Minecraft.getInstance().player;
        if (wheel != null) {
            Direction f = level.getBlockState(wheel).getValueOrElse(GrindstoneBlock.FACING, Direction.NORTH);
            double tx = me == null ? 0 : me.getX() - (wheel.getX() + 0.5), tz = me == null ? 0 : me.getZ() - (wheel.getZ() + 0.5);
            int s = f.getStepX() * tx + f.getStepZ() * tz >= 0 ? 1 : -1;
            wheelSideX = f.getStepX() * s;
            wheelSideZ = f.getStepZ() * s;
            // seen from the front the axle (its clockwise) runs to your left, so the point goes along it; from the back, turned
            wheelTurned = s < 0;
        }
        return wheel;
    }

    /** Across the grindstone's rim, edge down, along its axle; {@code up} blocks off the line, slid {@code along} it. */
    static Lay atWheel(@Nullable Level level, BlockPos anvil, BlockPos at, AnvilShape shape, float up, float along, Lay out) {
        out.scale.set(1F, 1F, 1F);
        out.shape = shape;
        out.mid = shape.mid;
        Direction f = level == null ? Direction.NORTH : level.getBlockState(at).getValueOrElse(GrindstoneBlock.FACING, Direction.NORTH);
        Direction axle = f.getClockWise();
        // the wheel's top is the block's (its middle 10/16 up, 6/16 round); the edge (design x out from the middle) on it
        float lift = 1F + (HALF_W + BLADE_X) * shape.texel + 0.005F + up;
        out.at.set(at.getX() - anvil.getX() + 0.5F + axle.getStepX() * along, at.getY() - anvil.getY() + lift, at.getZ() - anvil.getZ() + 0.5F + axle.getStepZ() * along);
        // turned end for end, its flat turned with it so the edge stays down
        float s = wheelTurned ? -1F : 1F;
        turn(A.set(axle.getStepX() * s, 0F, axle.getStepZ() * s), U.set(-f.getStepX() * s, 0F, -f.getStepZ() * s), out.turn);
        return out;
    }

    /** Across the horn's tip, flat up, the point away from you, tipped {@code tilt} (-1..1, 14 degrees at the end) about the horn. */
    static Lay onHorn(Quaternionf frame, AnvilShape shape, float tilt, Lay out) {
        out.scale.set(1F, 1F, 1F);
        out.shape = shape;
        out.mid = shape.root + 1.5F; // it balances just above the guard
        model(frame, -0.02F, 15.4F / 16F + BACK_Z * shape.texel, 9.5F / 16F, out.at);
        dir(frame, 0F, 0F, 1F, A);
        dir(frame, 0F, 1F, 0F, U);
        float a = tilt * 14F * Mth.DEG_TO_RAD, c = Mth.cos(a), s = Mth.sin(a);
        // about the horn (the frame's x): the point dips as it tips forward
        A.mul(c).add(U.x * -s, U.y * -s, U.z * -s);
        dir(frame, 0F, 1F, 0F, U).mul(c);
        dir(frame, 0F, 0F, 1F, W);
        U.add(W.x * s, W.y * s, W.z * s);
        turn(A, U, out.turn);
        return out;
    }

    /** Point up over the anvil's face, its flat to you, raised {@code k} (0..1) into the light. */
    static Lay inLight(Quaternionf frame, AnvilShape shape, float k, Lay out) {
        out.scale.set(1F, 1F, 1F);
        out.shape = shape;
        out.mid = shape.mid;
        model(frame, 0.5F, 1.25F + 0.2F * smooth(k) + (shape.mid - shape.root) * shape.texel, 7.5F / 16F, out.at);
        turn(dir(frame, 0F, 1F, 0F, A), dir(frame, 0F, 0F, -1F, U), out.turn);
        return out;
    }

    /** The heat as the shader takes it: dull red to white over 0..0.9 of the Forge's heat, hot past its front. */
    private static void heat(Placed out, float h, float front, boolean quench) {
        out.heat = Math.round(Mth.clamp(h * 1.1F, 0F, 1F) * 255);
        out.front = Math.round(Mth.clamp(front, 0F, 1F) * 255);
        out.flags = WeaponDraw.HEAT | (quench ? WeaponDraw.QUENCH : 0);
    }

    private static Direction forgeMouth(@Nullable Level level, BlockPos forge) {
        if (level == null) return Direction.NORTH;
        BlockState fs = level.getBlockState(forge);
        return fs.hasProperty(HorizontalDirectionalBlock.FACING) ? fs.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
    }

    /** The eye (block-relative) and its right, for the hands: yours while the scene is yours, else where a smith would stand. */
    private static void eye(BlockPos pos, boolean mine) {
        Minecraft mc = Minecraft.getInstance();
        if (mine) {
            var cam = mc.gameRenderer.mainCamera();
            Vec3 e = cam.position();
            EYE.set((float) (e.x - pos.getX()), (float) (e.y - pos.getY()), (float) (e.z - pos.getZ()));
            RIGHT.set(cam.leftVector()).mul(-1F);
        } else {
            model(FRAME, 0.5F, 1.62F, -0.75F, EYE);
            dir(FRAME, -1F, 0F, 0F, RIGHT);
        }
    }

    // ---- the tongs -----------------------------------------------------------------------------------------------------

    /**
     * The tongs' lips closed on the work at design height {@code y} (its middle across, through its thickness), the
     * handles back along {@code back} (block-relative, roughly toward the smith), held in your off hand when within reach;
     * {@code both}: the hammer is down, and your main hand holds them too, ahead of the off on the reins.
     */
    private static void tongsAt(Placed out, Lay work, float y, Vector3f back, boolean mine, boolean both) {
        work.point(BLADE_X, y, 0F, P);
        Y.set(back).normalize().negate(); // handles to jaws
        // the jaws close across the work's thickness: their opening axis as near its through-axis as the line allows
        Z.set(0F, 0F, 1F).rotate(work.turn);
        X.set(Z).sub(Y.x * Y.dot(Z), Y.y * Y.dot(Z), Y.z * Y.dot(Z));
        if (X.lengthSquared() < 1e-4F) X.set(0F, 1F, 0F);
        X.normalize();
        X.cross(Y, Z);
        G.set(Y).mul(-14.5F * TOOL).add(P);
        tool(out.tongs, G, X, Y, Z, -10F);
        out.hasTongs = true;
        if (mine && G.distance(EYE) < 1.6F) {
            out.offFist.set(G);
            out.offW = 1F;
            if (both) {
                // a free main hand would bring back what you held (the hand pass puts it away only behind an arm)
                out.mainFist.set(Y).mul(3F * TOOL).add(G).add(RIGHT.x * 0.08F, RIGHT.y * 0.08F, RIGHT.z * 0.08F);
                out.mainW = 1F;
            }
        }
    }

    private static void pegTongs(Matrix4f out) {
        model(FRAME, -0.1F, 0.406F - 7F * TOOL, 0.5F - 2F * TOOL, G);
        dir(FRAME, 0F, 0F, 1F, X);
        dir(FRAME, 0F, 1F, 0F, Y);
        X.cross(Y, Z);
        tool(out, G, X, Y, Z, -10F);
    }

    // ---- the hammer ----------------------------------------------------------------------------------------------------

    /** What a blow lands on: a strike's mark, the hilt's part a tap seats, a flake point on the edge (E). */
    static final int STRIKE = 0, TAP = 1, FLAKE = 2;

    /**
     * The hammer in your hand: wound up before each beat, down in a tick and a half on your click, its face on the mark
     * (a strike), the hilt's part (a tap) or the flake point (the stone hammer at E), bouncing back up toward the next.
     */
    private static void swing(Placed out, float now, int n, int mode, int need) {
        boolean tap = mode != STRIKE;
        float angle = angle(now, tap), since = now - blowAt;
        float k = since >= 0F && since < DOWN ? 0F : since >= DOWN && since < DOWN + 5F ? smooth((since - DOWN) / 5F) : 1F;
        // the face meets the beat just struck, then moves on to the next
        int next = Math.min(n, need - 1);
        target(out.work, mode, Math.max(0, blowBeat), T);
        target(out.work, mode, next, T2);
        T.lerp(T2, blowBeat < 0 ? 1F : k);
        // the haft runs back to your hand, level with the metal at the strike; the hilt is near you, so a tap's comes in
        // from the side, your arm clear of the work
        float side = mode == TAP ? 0.7F : 0.3F;
        Y.set(T).sub(EYE).sub(RIGHT.x * side, 0F, RIGHT.z * side);
        Y.y = 0F;
        if (Y.lengthSquared() < 1e-4F) Y.set(1F, 0F, 0F);
        Y.normalize(); // forward (the haft at the strike)
        X.set(0F, -1F, 0F); // the face down at the strike
        X.cross(Y, Z);
        float a = angle * Mth.DEG_TO_RAD, c = Mth.cos(a), s = Mth.sin(a), up = angle / RAISED;
        G.set(T).add(0F, 5F * TOOL, 0F).sub(Y.x * 13.5F * TOOL, 0F, Y.z * 13.5F * TOOL);
        G.add(0F, 0.2F * up, 0F).sub(Y.x * 0.06F * up, 0F, Y.z * 0.06F * up);
        // turned about its grip: the head up and back, its face coming round toward the work
        float xx = X.x * c + Y.x * s, xy = X.y * c + Y.y * s, xz = X.z * c + Y.z * s;
        float yx = -X.x * s + Y.x * c, yy = -X.y * s + Y.y * c, yz = -X.z * s + Y.z * c;
        X.set(xx, xy, xz);
        Y.set(yx, yy, yz);
        tool(out.hammer, G, X, Y, Z, -3F);
        out.mainFist.set(G);
        out.mainW = 1F;
    }

    /** The hammer's angle above the work now: wound up before a beat, down on a blow, bouncing back. */
    private static float angle(float now, boolean tap) {
        float raised = tap ? RAISED_TAP : RAISED;
        float wind = REST;
        if (!Float.isNaN(nextDue)) wind = REST + (raised - REST) * smooth(Mth.clamp((now - (nextDue - 12F)) / 10F, 0F, 1F));
        float since = now - blowAt;
        if (since >= 0F && since < DOWN) {
            float u = since / DOWN;
            return blowFrom * (1F - u * u);
        }
        if (since >= DOWN && since < DOWN + 5F) return Mth.lerp(smooth((since - DOWN) / 5F), 0F, wind);
        return wind;
    }

    /** Your blow on beat {@code beat}: the hammer comes down from wherever it is. */
    static void blow(float now, int beat, boolean tap) {
        blowFrom = Math.max(REST * 0.5F, angle(now, tap));
        blowAt = now;
        blowBeat = beat;
    }

    /**
     * Where blow {@code k} lands: a strike's mark on the face up (the work's song's), the hilt's part a tap seats (guard,
     * grip, pommel), or a flake point down the edge.
     */
    static Vector3f target(Lay work, int mode, int k, Vector3f out) {
        AnvilShape sh = work.shape;
        if (mode == TAP) {
            int i = work.guard ? 0 : Mth.clamp(k, 0, sh.taps.length - 1);
            return work.point(0F, sh.taps[i], i == 1 ? 1.5F : 2.5F, out);
        }
        if (mode == FLAKE) {
            float[] f = AnvilSong.FLAKE_MARKS[Mth.clamp(k, 0, AnvilSong.FLAKE_MARKS.length - 1)];
            return work.point(BLADE_X + HALF_W * f[1], sh.root + (sh.tip - sh.root) * f[0], FACE_Z, out);
        }
        float[] m = work.song.mark(k);
        // turned over at half: the other face is up
        float z = k < work.flipAt ? FACE_Z : -FACE_Z;
        return work.point(BLADE_X + HALF_W * m[1], sh.root + (sh.tip - sh.root) * m[0], z, out);
    }

    /** Laid on the anvil's heel, on its side, its handle off the near edge. */
    private static void restHammer(Matrix4f out) {
        dir(FRAME, 0F, 0F, 1F, Y);
        dir(FRAME, 0F, 1F, 0F, Z);
        Y.cross(Z, X);
        model(FRAME, 14F / 16F, 1F + 1.5F * TOOL, 10.5F / 16F, G).sub(Y.x * 13.5F * TOOL, 0F, Y.z * 13.5F * TOOL);
        tool(out, G, X, Y, Z, -3F);
    }

    /** Hung by its head on the peg, handle down, against the stone. */
    private static void pegHammer(Matrix4f out) {
        model(FRAME, -0.04F, 0.406F - 12F * TOOL, 0.5F + 2F * TOOL, G);
        dir(FRAME, 0F, 0F, -1F, X);
        dir(FRAME, 0F, 1F, 0F, Y);
        X.cross(Y, Z);
        tool(out, G, X, Y, Z, -3F);
    }

    /** A tool's pose: its grip (design height {@code gripY}) at {@code g}, its design axes along x, y, z (mesh: design/16). */
    private static void tool(Matrix4f out, Vector3f g, Vector3f x, Vector3f y, Vector3f z, float gripY) {
        M3.set(x, y, z);
        out.translation(g).mul(M4.set(M3)).scale(16F * TOOL).translate(0F, -gripY / 16F, 0F);
    }

    static float smooth(float k) {
        return k * k * (3F - 2F * k);
    }

    // ---- the view ------------------------------------------------------------------------------------------------------

    /** The view this frame (world): the eye, yaw, pitch and field of view, for the marks' projection. */
    static final double[] NOW = new double[6];
    private static int lookAt = -1;
    private static float glideAt, openAt, fromYaw, fromPitch;
    private static boolean started;

    /**
     * What the view looks at: 0 the Forge's mouth, 1 the anvil's face, 2 the trough, 3 the slot; the acts': 4 the wheel, 5 the
     * horn, 6 the light; 7 the grip (the hilt's taps and the bind, the hilt to your right).
     */
    static final int WHEEL = 4, HORN = 5, LIGHT = 6, GRIP = 7;
    /** The grip's place (the anvil's own blocks, from the grip): over the face's middle, looking down and right on it. */
    private static final float GRIP_X = 0.4F, GRIP_Y = 0.66F, GRIP_Z = -0.49F;

    private static int look(WarAnvilBlockEntity be) {
        AnvilWork w = be.work();
        if (w == null) return 3;
        return switch (w.stage) {
            case AnvilSong.HEAT -> 0;
            case AnvilSong.QUENCH -> 2;
            case AnvilSong.HILT, AnvilSong.BIND -> GRIP;
            case AnvilSong.GRIND -> wheel(be.getLevel(), be.getBlockPos(), w.stageStart) != null ? WHEEL : 1;
            case AnvilSong.BALANCE -> HORN;
            case AnvilSong.STAR -> LIGHT;
            default -> 1;
        };
    }

    private static Vector3f lookPoint(WarAnvilBlockEntity be, int kind, Vector3f out) {
        frame(be.getBlockState(), FRAME);
        AnvilWork w = be.work();
        BlockPos pos = be.getBlockPos();
        return switch (kind) {
            case 0 -> {
                Direction m = forgeMouth(be.getLevel(), w == null ? pos : w.forge);
                BlockPos f = w == null ? pos : w.forge;
                yield out.set(f.getX() - pos.getX() + 0.5F + m.getStepX() * 0.5F, f.getY() - pos.getY() + 0.35F, f.getZ() - pos.getZ() + 0.5F + m.getStepZ() * 0.5F);
            }
            case 2 -> model(FRAME, 14F / 16F, 0.5F, 8F / 16F, out);
            case 3 -> model(FRAME, 0.5F, 0.62F, 3F / 16F, out);
            case WHEEL -> {
                BlockPos at = wheel;
                yield at == null ? model(FRAME, 0.55F, 1F, 9.5F / 16F, out) : out.set(at.getX() - pos.getX() + 0.5F, at.getY() - pos.getY() + 0.95F, at.getZ() - pos.getZ() + 0.5F);
            }
            case HORN -> model(FRAME, 0.1F, 1F, 9.5F / 16F, out);
            case LIGHT -> model(FRAME, 0.5F, 1.75F, 8F / 16F, out); // up the blade, into the light
            case GRIP -> {
                AnvilShape sh = AnvilShape.of(w == null ? null : w.weapon);
                yield hiltRight(FRAME, sh, L3).point(0F, sh.taps[1], 0F, out);
            }
            default -> model(FRAME, 0.55F, 1F, 9.5F / 16F, out);
        };
    }

    static void begin() {
        started = false;
        lookAt = -1;
    }

    static void end() {
        pos = null;
        started = false;
        lookAt = -1;
        reset();
        AnvilCamera.end();
    }

    /** Where the eye glides from (world), and where it goes next frame. */
    private static final double[] FROM = new double[3], NEXT = new double[3];

    /** The view turns to the work, gliding between its marks, the eye gliding with it to each look's place ({@link AnvilCamera}). */
    static void angles(ViewportEvent.ComputeCameraAngles e) {
        WarAnvilBlockEntity be = AnvilClient.sceneAnvil();
        if (be == null) return;
        float now = AnvilClient.now((float) e.getPartialTick());
        Vec3 eye = e.getCamera().position();
        if (!started) {
            started = true;
            fromYaw = e.getYaw();
            fromPitch = e.getPitch();
            glideAt = openAt = now;
            FROM[0] = eye.x;
            FROM[1] = eye.y;
            FROM[2] = eye.z;
        }
        int kind = look(be);
        if (kind != lookAt) {
            if (lookAt >= 0) {
                fromYaw = (float) NOW[3];
                fromPitch = (float) NOW[4];
                glideAt = now;
                FROM[0] = eye.x;
                FROM[1] = eye.y;
                FROM[2] = eye.z;
            }
            lookAt = kind;
            if (kind == WHEEL && wheel != null) {
                // the wheel's own place: over its front or back (your side), the blade across the frame, a hand at each
                // end; if that's walled in, a little back from it toward where you stand
                BlockPos at = wheel;
                AnvilCamera.aimAt(be.getLevel(), at.getX() + 0.5 + wheelSideX * 0.95, at.getY() + 1.7, at.getZ() + 0.5 + wheelSideZ * 0.95);
                if (!AnvilCamera.aimed()) {
                    var me = Minecraft.getInstance().player;
                    double dx = me == null ? 0 : me.getX() - (at.getX() + 0.5), dz = me == null ? -1 : me.getZ() - (at.getZ() + 0.5), n = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
                    AnvilCamera.aimAt(be.getLevel(), at.getX() + 0.5 + dx / n * 0.95, at.getY() + 1.7, at.getZ() + 0.5 + dz / n * 0.95);
                }
            } else if (kind == GRIP) {
                // the grip's own place: the hammer's hand comes in from the right of the frame, the tongs' from below it
                lookPoint(be, kind, T);
                dir(FRAME, GRIP_X, GRIP_Y, GRIP_Z, T2).add(T);
                BlockPos p = be.getBlockPos();
                AnvilCamera.aimAt(be.getLevel(), p.getX() + T2.x, p.getY() + T2.y, p.getZ() + T2.z);
            } else AnvilCamera.aim(be.getLevel(), be.getBlockPos(), frame(be.getBlockState(), FRAME), kind);
        }
        lookPoint(be, kind, T);
        BlockPos pos = be.getBlockPos();
        double ax = pos.getX() + T.x - eye.x, ay = pos.getY() + T.y - eye.y, az = pos.getZ() + T.z - eye.z;
        float yaw = (float) (Mth.atan2(-ax, az) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(ay, Math.sqrt(ax * ax + az * az)) * Mth.RAD_TO_DEG);
        float k = cut() ? 1F : smooth(Mth.clamp((now - glideAt) / GLIDE, 0F, 1F));
        yaw = fromYaw + Mth.wrapDegrees(yaw - fromYaw) * k;
        pitch = Mth.lerp(k, fromPitch, pitch);
        e.setYaw(yaw);
        e.setPitch(pitch);
        e.setRoll(0F);
        NOW[0] = eye.x;
        NOW[1] = eye.y;
        NOW[2] = eye.z;
        NOW[3] = yaw;
        NOW[4] = pitch;
        AnvilCamera.eye(FROM, k, (float) e.getPartialTick(), NEXT);
        AnvilCamera.place(NEXT, yaw, pitch);
    }

    /** Reduced motion (the body's comfort setting): the view cuts to each look instead of gliding there. */
    static boolean cut() {
        return FirstPerson.reducedMotion.getAsBoolean();
    }

    /** Your own field of view over the work; a little closer when the view stays at your eyes. */
    static void fov(ViewportEvent.ComputeFov e) {
        if (AnvilClient.sceneAnvil() == null) return;
        float now = AnvilClient.now((float) e.getPartialTick());
        float k = !started ? 0F : cut() ? 1F : smooth(Mth.clamp((now - openAt) / GLIDE, 0F, 1F));
        e.setFOV(e.getFOV() * Mth.lerp(k, 1F, AnvilCamera.away() ? 1F : 0.85F));
        NOW[5] = e.getFOV();
    }

    /** A world point on the screen (GUI pixels) with this frame's view: false when behind the eye. out[2]: pixels a block there. */
    static boolean project(double x, double y, double z, int width, int height, float[] out) {
        double yaw = Math.toRadians(NOW[3]), pitch = Math.toRadians(NOW[4]);
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double ux = -rz * fy, uy = rz * fx - rx * fz, uz = rx * fy;
        double vx = x - NOW[0], vy = y - NOW[1], vz = z - NOW[2];
        double cz = vx * fx + vy * fy + vz * fz;
        if (cz < 0.05) return false;
        double cx = vx * rx + vz * rz, cy = vx * ux + vy * uy + vz * uz;
        double tan = Math.tan(Math.toRadians(NOW[5]) / 2);
        double aspect = width / (double) height;
        out[0] = (float) ((cx / (cz * tan * aspect)) * 0.5 + 0.5) * width;
        out[1] = (float) (0.5 - (cy / (cz * tan)) * 0.5) * height;
        out[2] = (float) (height / (2 * cz * tan));
        return true;
    }
}
