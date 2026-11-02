package dev.awaken.enchant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.awaken.core.api.FirstPerson;
import dev.awaken.enchant.EnchantContent;
import dev.awaken.enchant.Inscriptions;
import dev.awaken.enchant.InscribingTableBlock;
import dev.awaken.enchant.InscribingTableBlockEntity;
import dev.awaken.enchant.Marks;
import dev.awaken.enchant.net.EnchantPayloads;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

/**
 * The writing scene at an Inscribing Table (SPEC §45.10), on the writer's client. The view leans in from where you stand
 * (your eye never moves; the view turns and narrows): over the table while the book is open, close on the piece while you
 * trace, a little back while it wakes. Your off hand lays the piece flat on the slate and pins it there; your main hand
 * holds the table's burin and follows your cursor over the slate. The mark burns faint onto the piece when you choose;
 * each stroke you draw from its glowing end fills with lapis light, finished strokes burning in the family's colour.
 * The screen ({@link InscribeScreen}) takes the mouse; this class keeps the state, the view, the hands and the light.
 */
public final class InscribeScene {
    private InscribeScene() {}

    static final int NONE = -1, BOOK = 0, TRACE = 1, MEMORY = 2, WAKE = 3;
    /** Half the mark's width on the slate, in blocks. */
    static final double MARK = 0.2;
    /** Where a stroke must be picked up (its glowing end), how far the pen may run ahead, and what counts as on the line (mark units). */
    static final float START_R = 0.3F, FOLLOW_R = 0.34F, LINE_R = 0.15F;
    /** The server counts a stroke no sooner than 6 ticks after the last; the client waits 7. */
    static final int STROKE_GAP = 7;
    /** From memory: the whole mark writes itself in two seconds. */
    static final int MEMORY_TICKS = 40;
    /** The lay-down: the off hand brings the piece down onto the slate. */
    static final int LAY_TICKS = 14;
    static final int LAPIS = 0x5D86FF;

    // the session
    static @Nullable BlockPos pos;
    static int session, slot;
    static boolean study, live;
    static int phase = NONE;
    static int no, level;
    static boolean waiting;
    static String line = "";
    static int lineAt = -1000;
    static EnchantPayloads.@Nullable Done done;
    /** The enchantment the book's open at (a faint preview of its mark on the piece), 0 none. */
    static int hoverNo;

    // time
    static int ticks;
    private static int openedAt = -1000, closedAt = -1000;
    static int phaseAt = -1000;
    private static @Nullable CameraType before;

    // the trace
    static int stroke, progress, onLine, samples;
    static boolean drawing;
    static final float[] SHARE = new float[8];
    private static int queued, sent, lastSentAt = -1000, finishAt = -1;

    // the pen: where the cursor meets the slate, in the mark's own square
    static float penU, penV;
    static boolean penOn;

    // geometry (world): the way the table faces (toward the writer), the writer's right, the mark's centre, the slate's top
    static double fx, fz = 1, rx = 1, rz;
    static Vec3 centre = Vec3.ZERO;
    static double slateY;

    // the view: last tick's and this tick's yaw, pitch and field of view, eased toward the phase's frame
    private static final float[] VIEW = new float[3], VIEW_O = new float[3];
    private static boolean viewSet;

    /** Where the writer stands at the desk (null: stay where you are), and how long the step there takes. */
    private static @Nullable Vec3 desk;
    private static final int STEP_TICKS = 12;

    // ---- the session's messages ---------------------------------------------------------------------------------------

    static void open(EnchantPayloads.Open o) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        boolean again = live && o.pos().equals(pos);
        pos = o.pos();
        session = o.session();
        slot = o.slot();
        study = o.study();
        live = true;
        place();
        phase(BOOK);
        no = level = 0;
        waiting = false;
        done = null;
        line = "";
        if (!again) {
            openedAt = ticks;
            viewSet = false;
            desk = deskSpot(mc.player);
            if (before == null) {
                before = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.FIRST_PERSON);
            }
        }
        closedAt = -1000;
        if (!(mc.gui.screen() instanceof InscribeScreen)) mc.gui.setScreen(new InscribeScreen());
    }

    static void state(EnchantPayloads.State s) {
        if (!live || s.session() != session) return;
        waiting = false;
        switch (s.phase()) {
            case EnchantPayloads.TRACE -> {
                no = s.no();
                level = s.level();
                beginTrace();
                phase(TRACE);
                play(EnchantContent.PAGE_TURN.get(), 0.8F, 1.1F);
            }
            case EnchantPayloads.FROM_MEMORY -> {
                no = s.no();
                level = s.level();
                beginTrace();
                phase(MEMORY);
            }
            default -> {
                phase(BOOK);
                if (!s.line().isEmpty()) say(s.line());
            }
        }
    }

    static void done(EnchantPayloads.Done d) {
        if (!live || d.session() != session) return;
        done = d;
        no = d.no();
        phase(WAKE);
        // the piece's chord is the server's sound at the table; the client adds nothing over it
    }

    static void closed(EnchantPayloads.Closed c) {
        if (c.session() != session) return;
        end();
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof InscribeScreen) mc.gui.setScreen(null);
    }

    /** Ends the scene here (the screen closing tells the server, see {@link InscribeScreen#removed}). */
    static void end() {
        if (!live) return;
        live = false;
        closedAt = ticks;
        phase = NONE;
        drawing = false;
        Minecraft mc = Minecraft.getInstance();
        if (before != null) {
            mc.options.setCameraType(before);
            before = null;
        }
    }

    /**
     * The spot at the table's front where both hands reach the slate: 0.36 out from its face, on your own floor, if you're
     * within three blocks of it and there's room to stand (else you stay where you are and the view leans further in).
     */
    private static @Nullable Vec3 deskSpot(LocalPlayer p) {
        if (pos == null || Math.abs(p.getY() - pos.getY()) > 0.6) return null;
        Vec3 to = new Vec3(pos.getX() + 0.5 + fx * 0.86, p.getY(), pos.getZ() + 0.5 + fz * 0.86);
        if (to.distanceToSqr(p.position()) > 9) return null;
        AABB box = p.getBoundingBox().move(to.x - p.getX(), 0, to.z - p.getZ());
        return p.level().noCollision(p, box) ? to : null;
    }

    /** The step to the desk as the scene opens: a few short moves, collisions and all (the server sees an ordinary walk). */
    private static void step(LocalPlayer p) {
        if (desk == null || ticks - openedAt > STEP_TICKS + 4) return;
        double dx = desk.x - p.getX(), dz = desk.z - p.getZ(), l = Math.sqrt(dx * dx + dz * dz);
        if (l < 0.02) return;
        double go = Math.min(l, Math.max(0.05, l * 0.3));
        p.move(MoverType.SELF, new Vec3(dx / l * go, 0, dz / l * go));
    }

    static void reset() {
        end();
        pilot = false;
        desk = null;
        pos = null;
        openedAt = closedAt = -1000;
    }

    private static void phase(int p) {
        phase = p;
        phaseAt = ticks;
    }

    static void say(String s) {
        line = s;
        lineAt = ticks;
    }

    private static void beginTrace() {
        stroke = progress = onLine = samples = 0;
        drawing = false;
        queued = sent = 0;
        lastSentAt = ticks;
        finishAt = -1;
        java.util.Arrays.fill(SHARE, 0F);
    }

    // ---- what the screen asks ---------------------------------------------------------------------------------------

    static void act(int act, int which, int lvl, float a) {
        if (!live) return;
        ClientPacketDistributor.sendToServer(new EnchantPayloads.Act(session, act, which, lvl, a));
    }

    static void choose(Inscriptions.Entry e, int to, boolean memory) {
        if (waiting || phase != BOOK) return;
        waiting = true;
        act(memory ? EnchantPayloads.MEMORY : EnchantPayloads.CHOOSE, e.no(), to, 0F);
    }

    /** Back to the book after the wake (the session stays open: write another, or step back). */
    static void again() {
        if (phase != WAKE) return;
        done = null;
        no = 0;
        phase(BOOK);
    }

    static int strokes() {
        return no > 0 ? Marks.strokes(no) : 0;
    }

    /** The cursor moved (GUI pixels): where it meets the slate. */
    static void pen(double mx, double my, int guiW, int guiH) {
        penOn = false;
        Minecraft mc = Minecraft.getInstance();
        var cam = mc.gameRenderer.mainCamera();
        if (pos == null || guiW <= 0 || guiH <= 0) return;
        Vec3 o = cam.position();
        var fw = cam.forwardVector();
        var up = cam.upVector();
        var left = cam.leftVector();
        double tan = Math.tan(Math.toRadians(cam.getFov()) / 2), aspect = guiW / (double) guiH;
        double nx = (mx / guiW) * 2 - 1, ny = 1 - (my / guiH) * 2;
        double dx = fw.x() - left.x() * nx * tan * aspect + up.x() * ny * tan;
        double dy = fw.y() - left.y() * nx * tan * aspect + up.y() * ny * tan;
        double dz = fw.z() - left.z() * nx * tan * aspect + up.z() * ny * tan;
        double planeY = slateY + 0.035;
        if (dy > -1e-4) return;
        double t = (planeY - o.y) / dy;
        if (t <= 0 || t > 8) return;
        double px = o.x + dx * t - centre.x, pz = o.z + dz * t - centre.z;
        penU = (float) ((px * rx + pz * rz) / MARK);
        penV = (float) (-(px * fx + pz * fz) / MARK);
        penOn = Math.abs(penU) < 2.2F && Math.abs(penV) < 2.2F;
    }

    /** The mouse went down: pick up the stroke at its glowing end (or where it was left). */
    static void press() {
        if (phase != TRACE || !penOn || stroke >= strokes()) return;
        float[] s = Marks.of(no)[stroke];
        int i = Math.min(progress, s.length / 2 - 1);
        if (Mth.length(penU - s[i * 2], penV - s[i * 2 + 1]) <= START_R) {
            drawing = true;
            if (progress == 0) play(EnchantContent.BURIN_METAL.get(), 0.35F, 1.2F);
        }
    }

    /** The pen moved while down: run along the stroke, counting how much of it stays on the line. */
    static void drag() {
        if (phase != TRACE || !drawing || stroke >= strokes()) return;
        float[] s = Marks.of(no)[stroke];
        int n = s.length / 2;
        if (!penOn) return;
        samples++;
        float best = Float.MAX_VALUE;
        for (int i = Math.max(0, progress - 2); i < Math.min(n, progress + 3); i++)
            best = Math.min(best, Mth.length(penU - s[i * 2], penV - s[i * 2 + 1]));
        if (best <= LINE_R) onLine++;
        else if (ticks % 4 == 0) play(EnchantContent.SCRATCH.get(), 0.18F, 0.9F + 0.2F * (float) Math.random());
        for (int j = Math.min(n - 1, progress + 5); j > progress; j--) {
            if (Mth.length(penU - s[j * 2], penV - s[j * 2 + 1]) <= FOLLOW_R) {
                progress = j;
                break;
            }
        }
        if (progress >= n - 2) strokeDone();
    }

    static void release() {
        drawing = false;
    }

    private static void strokeDone() {
        float share = samples == 0 ? 1F : onLine / (float) samples;
        SHARE[stroke] = share;
        queued++;
        // each stroke rings its family's note, a step higher each time; one that wandered scratches dully
        Inscriptions.Entry e = entry();
        if (e != null) {
            if (share >= 0.6F) play(EnchantContent.note(e.family()), 0.55F, 0.84F + 0.12F * stroke);
            else play(EnchantContent.SCRATCH.get(), 0.45F, 0.7F);
        }
        stroke++;
        progress = onLine = samples = 0;
        drawing = false;
    }

    static Inscriptions.@Nullable Entry entry() {
        return no >= 1 && no <= Inscriptions.ALL.size() ? Inscriptions.ALL.get(no - 1) : null;
    }

    /** The whole trace's share on the line so far (for the screen). */
    static float clean() {
        int n = strokes();
        if (n == 0) return 0;
        float sum = 0;
        for (int i = 0; i < Math.min(stroke, SHARE.length); i++) sum += SHARE[i];
        return sum / n;
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    // ---- the autopilot's hand (tours: /awaken enchant pilot trace) ---------------------------------------------------

    /** Whether the pilot traces for you (tours can't drag a mouse): each stroke from its end, on the line, a breath between. */
    static boolean pilot;
    private static int pilotAt;

    private static void pilotTick() {
        if (!pilot || phase != TRACE || stroke >= strokes()) return;
        float[] s = Marks.of(no)[stroke];
        int n = s.length / 2;
        penOn = true;
        if (!drawing) {
            if (ticks - pilotAt < 8) return;
            penU = s[0];
            penV = s[1];
            press();
            pilotAt = ticks;
            return;
        }
        int j = Math.min(n - 1, progress + 2);
        penU = s[j * 2];
        penV = s[j * 2 + 1];
        drag();
        if (!drawing) pilotAt = ticks;
    }

    static void tick() {
        ticks++;
        pilotTick();
        Minecraft mc = Minecraft.getInstance();
        if (live && (mc.player == null || !mc.player.isAlive() || mc.level == null || pos == null
                || !(mc.level.getBlockEntity(pos) instanceof InscribingTableBlockEntity))) {
            end();
            if (mc.gui.screen() instanceof InscribeScreen) mc.gui.setScreen(null);
        }
        if (live && phase == TRACE) {
            // strokes go to the server no faster than it counts them; the last lifted, it's written
            if (queued > sent && ticks - lastSentAt >= STROKE_GAP) {
                act(EnchantPayloads.STROKE, 0, sent, SHARE[sent]);
                sent++;
                lastSentAt = ticks;
            }
            if (stroke >= strokes() && sent == queued && finishAt < 0) finishAt = ticks + 6;
        }
        if (live && phase == MEMORY && finishAt < 0 && ticks - phaseAt >= MEMORY_TICKS) finishAt = ticks;
        if (live && finishAt >= 0 && ticks >= finishAt && (phase == TRACE || phase == MEMORY)) {
            act(EnchantPayloads.FINISH, 0, 0, 0F);
            finishAt = Integer.MAX_VALUE;
        }
        if (live && mc.player != null) step(mc.player);
        VIEW_O[0] = VIEW[0];
        VIEW_O[1] = VIEW[1];
        VIEW_O[2] = VIEW[2];
        if (pos != null && mc.player != null) frame(mc.player);
    }

    /** Where the table's parts are, from the way it faces (the writer stands at its front). */
    private static void place() {
        Minecraft mc = Minecraft.getInstance();
        if (pos == null || mc.level == null) return;
        var st = mc.level.getBlockState(pos);
        Direction d = st.hasProperty(InscribingTableBlock.FACING) ? st.getValue(InscribingTableBlock.FACING) : Direction.NORTH;
        fx = d.getStepX();
        fz = d.getStepZ();
        rx = fz;
        rz = -fx;
        slateY = pos.getY() + 13 / 16.0;
        centre = new Vec3(pos.getX() + 0.5 + fx * 0.08, slateY, pos.getZ() + 0.5 + fz * 0.08);
    }

    /** A point of the mark's square on the slate (u along the piece, v away from the writer), at a height over it. */
    static double wx(double u, double v) {
        double ox = (rx * u - fx * v) * MARK, oz = (rz * u - fz * v) * MARK;
        return centre.x + ox * spinC + oz * spinS;
    }

    static double wz(double u, double v) {
        double ox = (rx * u - fx * v) * MARK, oz = (rz * u - fz * v) * MARK;
        return centre.z - ox * spinS + oz * spinC;
    }

    /** The mark turning with the piece as it wakes (about the piece's centre, as the table turns the piece). */
    private static double spinC = 1, spinS;

    private static void spin(double a) {
        spinC = Math.cos(a);
        spinS = Math.sin(a);
    }

    /**
     * The view each phase asks for, eased toward a tick at a time. Each frames what matters ({@code size} blocks across
     * filling {@code frac} of it) in the middle of the band the screen leaves clear above its pages, line or card.
     */
    private static void frame(LocalPlayer p) {
        Vec3 eye = p.getEyePosition(1F);
        Minecraft mc = Minecraft.getInstance();
        double base = mc.options.fov().get();
        int guiH = Math.max(1, mc.getWindow().getGuiScaledHeight());
        double tx = centre.x, ty = slateY + 0.03, tz = centre.z, size, frac, clear;
        switch (phase) {
            case TRACE, MEMORY -> {
                size = 0.5;
                frac = 0.55;
                clear = 1 - 44.0 / guiH;
            }
            case WAKE -> {
                ty += 0.08;
                size = 0.6;
                frac = 0.6;
                clear = InscribeScreen.cardTop(guiH);
            }
            default -> {
                // the piece on the slate (its mark faint as you rest on a row) with the floating book behind, over the pages
                ty = slateY + 0.05;
                size = 0.62;
                frac = 0.9;
                clear = InscribeScreen.pagesTop(guiH) - 0.02;
            }
        }
        clear = Mth.clamp(clear, 0.3, 1.0);
        double dx = tx - eye.x, dy = ty - eye.y, dz = tz - eye.z, flat = Math.sqrt(dx * dx + dz * dz), dist = Math.max(0.6, Math.sqrt(flat * flat + dy * dy));
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float fov = (float) Mth.clamp(Math.toDegrees(2 * Math.atan(size / (dist * 2 * frac * clear))), 14, Math.min(base, 78));
        // looking a little below it puts it in the middle of the clear band
        double half = Math.tan(Math.toRadians(fov / 2));
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, flat)) + Math.toDegrees(Math.atan((1 - clear) * half)));
        if (!viewSet) {
            VIEW[0] = VIEW_O[0] = yaw;
            VIEW[1] = VIEW_O[1] = pitch;
            VIEW[2] = VIEW_O[2] = fov;
            viewSet = true;
            return;
        }
        float k = 0.22F;
        VIEW[0] += Mth.wrapDegrees(yaw - VIEW[0]) * k;
        VIEW[1] += (pitch - VIEW[1]) * k;
        VIEW[2] += (fov - VIEW[2]) * k;
    }

    /** How far in the scene is (0 out, 1 in): in over 12 ticks, out over 10. */
    static float weight(float pt) {
        float t = ticks + pt;
        float in = ease(Mth.clamp((t - openedAt) / 12F, 0F, 1F));
        if (live) return in;
        return Math.min(in, 1F - ease(Mth.clamp((t - closedAt) / 10F, 0F, 1F)));
    }

    private static float ease(float x) {
        return x * x * (3 - 2 * x);
    }

    static float now(float pt) {
        return ticks + pt;
    }

    // ---- the view ---------------------------------------------------------------------------------------------------

    static void angles(ViewportEvent.ComputeCameraAngles e) {
        float pt = (float) e.getPartialTick();
        float w = weight(pt);
        if (w <= 0 || pos == null || !viewSet) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getCameraEntity() != mc.player) return;
        float yaw = VIEW_O[0] + Mth.wrapDegrees(VIEW[0] - VIEW_O[0]) * pt, pitch = Mth.lerp(pt, VIEW_O[1], VIEW[1]);
        e.setYaw(e.getYaw() + Mth.wrapDegrees(yaw - e.getYaw()) * w);
        // the slightest breath while you write
        e.setPitch(Mth.lerp(w, e.getPitch(), pitch) + 0.2F * w * Mth.sin(now(pt) * 0.11F));
    }

    static void fov(ComputeFovModifierEvent e) {
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float w = weight(pt);
        if (w <= 0 || !viewSet) return;
        float fov = Mth.lerp(pt, VIEW_O[2], VIEW[2]);
        e.setNewFovModifier(Mth.lerp(w, e.getNewFovModifier(), fov / mc.options.fov().get()));
    }

    static void player(RenderPlayerEvent.Pre<?> e) {
        Minecraft mc = Minecraft.getInstance();
        if (live && mc.player != null && e.getRenderState().id == mc.player.getId() && mc.options.getCameraType().isFirstPerson()) e.setCanceled(true);
    }

    // ---- the hands --------------------------------------------------------------------------------------------------

    private static ItemStack burin = ItemStack.EMPTY;

    /** Both hands at the table: the off hand lays the piece down and pins it, the main hand holds the burin to the pen. */
    static boolean pose(float pt, FirstPerson.Rig rig) {
        Minecraft mc = Minecraft.getInstance();
        float w = weight(pt);
        if (w <= 0.01F || pos == null || mc.player == null) return false;
        if (burin.isEmpty()) burin = new ItemStack(EnchantContent.BURIN.get());
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        double inX = centre.x - eye.x, inZ = centre.z - eye.z, inL = Math.max(1e-4, Math.sqrt(inX * inX + inZ * inZ));
        inX /= inL;
        inZ /= inL;
        double bx = -inZ, bz = inX; // the body's right
        rig.fx = inX;
        rig.fz = inZ;
        rig.handDistance = 0.8;
        float t = now(pt);
        int main = mc.player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? 1 : 0, off = 1 - main;
        double mainSide = main == 0 ? 1 : -1;
        float wake = phase == WAKE ? ease(Mth.clamp((t - phaseAt) / 10F, 0F, 1F)) : 0F;
        for (int h = 0; h < 2; h++) {
            double side = h == 0 ? 1 : -1;
            double x, y, z;
            if (h == off) {
                // laying it down: from low by your side, over the slate, down onto it; then pinning its near end
                double ex = wx(-mainSide * 0.95, -0.35), ez = wz(-mainSide * 0.95, -0.35);
                x = ex;
                z = ez;
                y = slateY + 0.04;
                float lay = Mth.clamp((t - openedAt) / LAY_TICKS, 0F, 1F);
                if (lay < 1F) {
                    float k = ease(lay);
                    double sx0 = eye.x + bx * side * 0.35 + inX * 0.25, sy0 = eye.y - 0.65, sz0 = eye.z + bz * side * 0.35 + inZ * 0.25;
                    x = Mth.lerp(k, sx0, centre.x);
                    z = Mth.lerp(k, sz0, centre.z);
                    y = Mth.lerp(k, sy0, slateY + 0.06) + 0.12 * Mth.sin(k * Mth.PI);
                    rig.held[h] = laying();
                } else rig.held[h] = ItemStack.EMPTY;
            } else {
                rig.held[h] = burin;
                double[] tip = tip(t);
                x = tip[0];
                y = tip[1] + 0.1;
                z = tip[2];
                // the hand rides a little behind the tip, toward you
                x -= inX * 0.05;
                z -= inZ * 0.05;
            }
            y += wake * 0.08;
            x -= inX * wake * 0.08;
            z -= inZ * wake * 0.08;
            y -= (1 - w) * 0.6; // in and out of the scene: up from below the table's edge
            double sx = eye.x + bx * side * 0.33 - inX * 0.1, sy = eye.y - 0.4, sz = eye.z + bz * side * 0.33 - inZ * 0.1;
            rig.reach(h == 0 ? FirstPerson.Limb.RIGHT_ARM : FirstPerson.Limb.LEFT_ARM, sx, sy, sz, x, y, z, FirstPerson.Rig.ARM_REACH, 0, 1, 0, w);
        }
        return true;
    }

    /** What the off hand brings down onto the slate while it lays it: your piece (the table shows it once it's down). */
    private static ItemStack laying() {
        Minecraft mc = Minecraft.getInstance();
        if (pos != null && mc.level != null && mc.level.getBlockEntity(pos) instanceof InscribingTableBlockEntity be && !be.piece().isEmpty()) return be.piece();
        return mc.player == null ? ItemStack.EMPTY : mc.player.getMainHandItem();
    }

    /** Whether the piece is still in your hand on its way down (the table holds off drawing it for you). */
    static boolean laying(float pt) {
        return live && now(pt) - openedAt < LAY_TICKS;
    }

    private static final double[] TIP = new double[3];

    /** The burin's tip: at the pen while you trace, running the mark from memory, else resting at the slate's near corner. */
    private static double[] tip(float t) {
        double u = 1.1, v = -0.9, lift = 0.05;
        if (phase == TRACE && penOn && Math.abs(penU) < 1.6F && Math.abs(penV) < 1.6F) {
            u = penU;
            v = penV;
            lift = drawing ? 0.0 : 0.03;
        } else if (phase == MEMORY && no > 0) {
            float[] uv = memoryPoint(t);
            u = uv[0];
            v = uv[1];
            lift = 0;
        }
        TIP[0] = wx(u, v);
        TIP[1] = slateY + 0.035 + lift;
        TIP[2] = wz(u, v);
        return TIP;
    }

    private static final float[] MP = new float[2];

    /** Where memory's single stroke has reached: the mark's strokes one after another over two seconds. */
    static float[] memoryPoint(float t) {
        float[][] m = Marks.of(no);
        float k = Mth.clamp((t - phaseAt) / MEMORY_TICKS, 0F, 0.9999F) * m.length;
        int s = (int) k;
        float[] st = m[s];
        int n = st.length / 2;
        float f = (k - s) * (n - 1);
        int i = Math.min(n - 2, (int) f);
        float a = f - i;
        MP[0] = Mth.lerp(a, st[i * 2], st[i * 2 + 2]);
        MP[1] = Mth.lerp(a, st[i * 2 + 1], st[i * 2 + 3]);
        return MP;
    }

    // ---- the light on the piece ---------------------------------------------------------------------------------------

    private static double cx, cy, cz;

    static void submit(SubmitCustomGeometryEvent e) {
        Minecraft mc = Minecraft.getInstance();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (pos == null || weight(pt) <= 0.01F || mc.level == null) return;
        int shown = phase == BOOK ? hoverNo : no;
        if (shown <= 0 || (phase == BOOK && waiting)) return;
        Vec3 cam = e.getLevelRenderState().cameraRenderState.pos;
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        float t = now(pt);
        Inscriptions.Entry en = Inscriptions.ALL.get(shown - 1);
        int colour = en.colour();
        float[][] m = Marks.of(shown);
        final int ph = phase;
        e.getSubmitNodeCollector().submitCustomGeometry(e.getPoseStack(), RenderTypes.lightning(), (pose, vc) -> {
            double y = slateY + 0.036;
            switch (ph) {
                case BOOK -> {
                    // the book open at it: its mark, barely there, where it would go
                    float a = 0.12F + 0.05F * Mth.sin(t * 0.15F);
                    for (float[] s : m) ribbon(pose, vc, s, 0, s.length / 2, y, 0.016, argb(colour, a));
                }
                case TRACE -> {
                    float burn = Mth.clamp((t - phaseAt) / 15F, 0F, 1F);
                    for (int i = 0; i < m.length; i++) {
                        float[] s = m[i];
                        int n = s.length / 2;
                        if (i < stroke) {
                            // a finished cut: lapis light in it, the family's colour round it
                            ribbon(pose, vc, s, 0, n, y, 0.05, argb(colour, 0.32F));
                            ribbon(pose, vc, s, 0, n, y + 0.001, 0.014, argb(hot(LAPIS, 0.55F), 0.95F));
                        } else {
                            ribbon(pose, vc, s, 0, n, y, 0.02, argb(colour, 0.2F * burn));
                            if (i == stroke && progress > 0) {
                                ribbon(pose, vc, s, 0, progress + 1, y, 0.04, argb(LAPIS, 0.4F));
                                ribbon(pose, vc, s, 0, progress + 1, y + 0.001, 0.013, argb(hot(LAPIS, 0.6F), 0.95F));
                            }
                        }
                    }
                    if (stroke < m.length) {
                        // where to pick the stroke up: its end glows and breathes
                        float[] s = m[stroke];
                        int i = Math.min(progress, s.length / 2 - 1);
                        float pulse = drawing ? 0.6F : 0.75F + 0.25F * Mth.sin(t * 0.35F);
                        disc(pose, vc, wx(s[i * 2], s[i * 2 + 1]), y + 0.002, wz(s[i * 2], s[i * 2 + 1]), 0.035 * pulse + 0.012, argb(hot(colour, 0.5F), 0.85F));
                        disc(pose, vc, wx(s[i * 2], s[i * 2 + 1]), y + 0.001, wz(s[i * 2], s[i * 2 + 1]), 0.09 * pulse, argb(colour, 0.25F));
                    }
                    if (drawing && penOn) {
                        disc(pose, vc, wx(penU, penV), y + 0.003, wz(penU, penV), 0.022, argb(0xFFFFFF, 0.8F));
                        disc(pose, vc, wx(penU, penV), y + 0.002, wz(penU, penV), 0.06, argb(LAPIS, 0.35F));
                    }
                }
                case MEMORY -> {
                    float k = Mth.clamp((t - phaseAt) / MEMORY_TICKS, 0F, 1F) * m.length;
                    for (int i = 0; i < m.length; i++) {
                        float[] s = m[i];
                        int n = s.length / 2;
                        ribbon(pose, vc, s, 0, n, y, 0.02, argb(colour, 0.16F));
                        int upto = k >= i + 1 ? n : k > i ? Math.max(1, (int) ((k - i) * n)) : 0;
                        if (upto > 0) {
                            ribbon(pose, vc, s, 0, upto, y, 0.05, argb(colour, 0.32F));
                            ribbon(pose, vc, s, 0, upto, y + 0.001, 0.014, argb(hot(LAPIS, 0.55F), 0.95F));
                        }
                    }
                    float[] p = memoryPoint(t);
                    disc(pose, vc, wx(p[0], p[1]), y + 0.003, wz(p[0], p[1]), 0.05, argb(hot(colour, 0.6F), 0.6F));
                }
                case WAKE -> {
                    // it wakes: the mark flares white-hot and settles into its colour, and a band of light runs the piece
                    // it lifts a finger's width and turns once with the piece
                    float a = Mth.clamp((t - phaseAt) / 50F, 0F, 1F);
                    float glow = 1F - 0.75F * a;
                    double lift = InscribingTableRenderer.wakeLift(InscribingTableBlockEntity.WAKE, t - phaseAt);
                    spin(InscribingTableRenderer.wakeSpin(t - phaseAt));
                    for (float[] s : m) {
                        ribbon(pose, vc, s, 0, s.length / 2, y + lift, 0.07 * glow + 0.02, argb(colour, 0.35F * glow));
                        ribbon(pose, vc, s, 0, s.length / 2, y + lift + 0.001, 0.015, argb(hot(colour, 0.7F * glow), glow));
                    }
                    float sweep = Mth.clamp((t - phaseAt - 4) / 24F, 0F, 1F);
                    if (sweep > 0 && sweep < 1) band(pose, vc, -1.4 + 2.8 * sweep, y + lift + 0.002, argb(hot(colour, 0.5F), 0.6F * Mth.sin(sweep * Mth.PI)));
                    spin(0);
                }
                default -> {}
            }
        });
    }

    /** A stroke's samples [from, to) as a flat ribbon of light lying on the slate, bright down its middle, soft at its edges. */
    private static void ribbon(PoseStack.Pose pose, VertexConsumer vc, float[] s, int from, int to, double y, double width, int argb) {
        int n = s.length / 2;
        to = Math.min(to, n);
        int rim = argb & 0x00FFFFFF;
        double h = width / 2;
        for (int i = Math.max(0, from); i + 1 < to; i++) {
            double ax = wx(s[i * 2], s[i * 2 + 1]), az = wz(s[i * 2], s[i * 2 + 1]);
            double bx = wx(s[i * 2 + 2], s[i * 2 + 3]), bz = wz(s[i * 2 + 2], s[i * 2 + 3]);
            double dx = bx - ax, dz = bz - az, l = Math.sqrt(dx * dx + dz * dz);
            if (l < 1e-6) continue;
            double nx = -dz / l * h, nz = dx / l * h;
            // carry the ribbon a hair past each end so the joints close
            double ex = dx / l * h * 0.5, ez = dz / l * h * 0.5;
            ax -= ex;
            az -= ez;
            bx += ex;
            bz += ez;
            quad(pose, vc, ax, y, az, bx, y, bz, bx + nx, y, bz + nz, ax + nx, y, az + nz, argb, rim);
            quad(pose, vc, ax, y, az, bx, y, bz, bx - nx, y, bz - nz, ax - nx, y, az - nz, argb, rim);
        }
    }

    /** A round glow lying on the slate. */
    private static void disc(PoseStack.Pose pose, VertexConsumer vc, double x, double y, double z, double r, int argb) {
        int rim = argb & 0x00FFFFFF;
        for (int i = 0; i < 10; i++) {
            float a0 = i * Mth.TWO_PI / 10, a1 = (i + 1) * Mth.TWO_PI / 10;
            quad(pose, vc, x, y, z, x, y, z, x + Mth.cos(a1) * r, y, z + Mth.sin(a1) * r, x + Mth.cos(a0) * r, y, z + Mth.sin(a0) * r, argb, rim);
        }
    }

    /** A band of light across the piece at {@code u}, soft on both sides. */
    private static void band(PoseStack.Pose pose, VertexConsumer vc, double u, double y, int argb) {
        int rim = argb & 0x00FFFFFF;
        double w = 0.18;
        quad(pose, vc, wx(u, -1.1), y, wz(u, -1.1), wx(u, 1.1), y, wz(u, 1.1), wx(u + w, 1.1), y, wz(u + w, 1.1), wx(u + w, -1.1), y, wz(u + w, -1.1), argb, rim);
        quad(pose, vc, wx(u, -1.1), y, wz(u, -1.1), wx(u, 1.1), y, wz(u, 1.1), wx(u - w, 1.1), y, wz(u - w, 1.1), wx(u - w, -1.1), y, wz(u - w, -1.1), argb, rim);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer vc, double x0, double y0, double z0, double x1, double y1, double z1,
                             double x2, double y2, double z2, double x3, double y3, double z3, int inner, int outer) {
        vc.addVertex(pose, (float) (x0 - cx), (float) (y0 - cy), (float) (z0 - cz)).setColor(inner);
        vc.addVertex(pose, (float) (x1 - cx), (float) (y1 - cy), (float) (z1 - cz)).setColor(inner);
        vc.addVertex(pose, (float) (x2 - cx), (float) (y2 - cy), (float) (z2 - cz)).setColor(outer);
        vc.addVertex(pose, (float) (x3 - cx), (float) (y3 - cy), (float) (z3 - cz)).setColor(outer);
    }

    static int argb(int rgb, float a) {
        return (Math.round(Mth.clamp(a, 0F, 1F) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    /** A colour pushed toward white. */
    static int hot(int rgb, float k) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r += (int) ((255 - r) * k);
        g += (int) ((255 - g) * k);
        b += (int) ((255 - b) * k);
        return (r << 16) | (g << 8) | b;
    }

    static void play(SoundEvent sound, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || pos == null) return;
        mc.level.playLocalSound(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, sound, SoundSource.BLOCKS, volume, pitch, false);
    }
}
