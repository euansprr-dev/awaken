package dev.awaken.arms.anvil.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.Awaken;
import dev.awaken.arms.anvil.AnvilContent;
import dev.awaken.arms.anvil.AnvilPayloads;
import dev.awaken.arms.anvil.AnvilSong;
import dev.awaken.arms.anvil.AnvilWork;
import dev.awaken.arms.anvil.WarAnvil;
import dev.awaken.arms.anvil.WarAnvilBlockEntity;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * The War Anvil's song (SPEC §46.2, §46.3, §46.7): what you do at each stage, in plain words, over the scene. The heat's
 * gauge with its gold band; the mark on the metal with a ring closing on the beat (each style's own rhythm and marks), a
 * thin beat lane and a pip a strike; the quench's line climbing to its gold notch while you hold; the hilt's three taps.
 * At E by hand: the flake points down the edge, struck at your own pace, and the cord wound round the grip in circles.
 * Then the weapon standing in the stone, held to draw it out, and its card. The server judges every act by its own
 * clock; this screen shows them at once. Doesn't pause. Click, or Space, is the act; for the quench, the bind and the
 * draw, hold it.
 */
final class AnvilSongScreen extends Screen implements dev.awaken.signal.client.QuietScreen, dev.awaken.signal.client.ReadingScreen {
    private static final int LANTERN = 0xFFFFCF7A, TITLE = 0xFFF4F6FB, LABEL = 0xFFAFC2D8, DIM = 0xFF6F8299, GOOD = 0xFF9FE6D6, BAD = 0xFFFF8A7A;
    /** Ticks before a beat its ring starts closing on the mark. */
    private static final float APPROACH = 28F;

    final BlockPos pos;
    private float opened = Float.NaN, seenAt = Float.NaN;
    private int mx, my;
    private boolean spaceDown;

    // this run of a stage (begun, or begun again after stepping away)
    private int runStage = -1;
    private long runStart = Long.MIN_VALUE;
    private int next, countStep;
    private final float[] predicted = new float[16];
    private boolean pulled, holding, lifted, cooled;
    private float bellowsAt;
    /** The work's weapon (the card shows it once it's drawn). */
    private String weapon = WarAnvil.WEAPON;
    // by hand: the last flake (ticks from the stage's start), and the cord as it's wound
    private float knapLast = Float.NaN;
    private boolean winding;
    private float windAcc, windLast = Float.NaN;
    private int windTurns;
    private final float[] turnAt = new float[AnvilSong.TURNS + 1];

    // what the screen shows for a moment
    private String callout = "";
    private int calloutColour;
    private float calloutAt = -100;
    private static final int SPARKS = 48;
    private final float[] sx = new float[SPARKS], sy = new float[SPARKS], svx = new float[SPARKS], svy = new float[SPARKS], sborn = new float[SPARKS];
    private final int[] scol = new int[SPARKS];
    private int sparkNext;
    private float ringX, ringY, ringAt = -100, ringR;

    // the finished weapon and the draw
    private AnvilPayloads.@Nullable Done done;
    private float doneAt = Float.NaN, drawFrom = Float.NaN, drawnAt = Float.NaN, emptyAt = Float.NaN;
    private boolean drawSent;
    /** The weapon as it stood in the stone, for the card once it's drawn. */
    private ItemStack shown = ItemStack.EMPTY;

    private final float[] p = new float[3];
    private final Vector3f v = new Vector3f();
    private final AnvilScene.Placed placed = new AnvilScene.Placed();
    private final Quaternionf frame = new Quaternionf();

    AnvilSongScreen(BlockPos pos) {
        super(Component.literal("War Anvil"));
        this.pos = pos;
        Arrays.fill(predicted, Float.NaN);
    }

    // ---- the frame ---------------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int bar = Math.max(10, height / 14);
        g.fill(0, 0, width, bar, 0xFF000000);
        g.fill(0, height - bar, width, height, 0xFF000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        mx = mouseX;
        my = mouseY;
        float now = AnvilClient.now(a);
        if (Float.isNaN(opened)) opened = now;
        WarAnvilBlockEntity be = AnvilClient.anvil(pos);
        var player = Minecraft.getInstance().player;
        if (be == null || player == null) {
            onClose();
            return;
        }
        AnvilScene.place(be, now, true, placed);
        AnvilWork w = be.work();
        if (w == null && (done != null || !be.slot().isEmpty() || !Float.isNaN(drawnAt))) {
            draw(g, be, now);
        } else if (w == null || !w.forger.equals(player.getUUID())) {
            // the work hasn't come yet, or it's gone
            boolean gone = Float.isNaN(seenAt) ? now - opened > 60 : now - seenAt > 30;
            if (gone) onClose();
            else if (Float.isNaN(seenAt)) g.centeredText(font, Component.literal("Setting the work..."), width / 2, height / 2 + 40, DIM);
            return;
        } else {
            seenAt = now;
            if (w.stage != runStage || w.stageStart != runStart) newRun(w, now);
            float t = now - w.stageStart;
            header(g, w);
            // before the stage draws: the bind reads the cursor autoplay moves (the frame set it to the real mouse)
            if (AnvilClient.autoplay) autoplay(w, t, now);
            switch (w.stage) {
                case AnvilSong.HEAT -> heat(g, w, t, now);
                case AnvilSong.SHAPE -> beats(g, w, t, now, false);
                case AnvilSong.QUENCH -> quench(g, w, t, now);
                case AnvilSong.HILT -> beats(g, w, t, now, true);
                case AnvilSong.KNAP -> knap(g, w, t, now);
                case AnvilSong.BIND -> bind(g, w, t, now);
                default -> {}
            }
        }
        sparks(g, now);
        ring(g, now);
        callout(g, now);
    }

    private void newRun(AnvilWork w, float now) {
        runStage = w.stage;
        runStart = w.stageStart;
        Arrays.fill(predicted, Float.NaN);
        next = w.beats().size();
        countStep = (int) Math.floor((now - w.stageStart) / w.rhythm().step()) - 1;
        pulled = holding = lifted = cooled = false;
        bellowsAt = now - 100F;
        weapon = w.weapon;
        knapLast = Float.NaN;
        winding = false;
        windAcc = 0F;
        windLast = Float.NaN;
        windTurns = 0;
        AnvilScene.windTurns = -1;
        AnvilScene.windAngle = 0F;
        AnvilScene.struck = next;
        AnvilScene.blowBeat = -1;
        AnvilScene.blowAt = -1000F;
        AnvilScene.nextDue = Float.NaN;
        AnvilScene.plungeAt = AnvilScene.liftAt = Float.NaN;
    }

    private void header(GuiGraphicsExtractor g, AnvilWork w) {
        int top = Math.max(10, height / 14) + 6;
        String stage = switch (w.stage) {
            case AnvilSong.HEAT -> "Heat";
            case AnvilSong.SHAPE -> "Draw it out";
            case AnvilSong.QUENCH -> "Quench";
            case AnvilSong.KNAP -> "Knap the edge";
            case AnvilSong.BIND -> "Bind the grip";
            default -> "The hilt";
        };
        g.centeredText(font, Component.literal(weaponName(w.weapon) + "  ·  " + stage + (w.memory() ? "  ·  from memory" : "")), width / 2, top, TITLE);
    }

    private static String weaponName(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Awaken.id(id))).getHoverName().getString();
    }

    /** The bottom of what the screen draws, above the letterbox: the hint's lines there, the lane and pips above. */
    private int bottom() {
        return height - Math.max(10, height / 14) - 4;
    }

    private void hint(GuiGraphicsExtractor g, String text) {
        if (text.isEmpty()) return;
        var lines = font.split(Component.literal(text), Math.min(420, width - 40));
        int n = Math.min(2, lines.size()), y = bottom() - 10 * n;
        for (int i = 0; i < n; i++) g.centeredText(font, lines.get(i), width / 2, y + i * 10, DIM);
    }

    private Vec3 at(Vector3f blockRelative) {
        return new Vec3(pos.getX() + blockRelative.x, pos.getY() + blockRelative.y, pos.getZ() + blockRelative.z);
    }

    private boolean project(Vector3f blockRelative) {
        return AnvilScene.project(pos.getX() + blockRelative.x, pos.getY() + blockRelative.y, pos.getZ() + blockRelative.z, width, height, p);
    }

    // ---- heat ----------------------------------------------------------------------------------------------------------

    private void heat(GuiGraphicsExtractor g, AnvilWork w, float t, float now) {
        float h = AnvilSong.heat(t);
        if (now - bellowsAt > 46 && !pulled) {
            bellowsAt = now;
            AnvilClient.play(Vec3.atCenterOf(w.forge), AnvilContent.BELLOWS.get(), 0.45F, 0.95F + (now % 7) * 0.01F);
        }
        int gx = width / 2 + Math.min(140, width / 4), gh = Math.min(150, height / 2), gy = height / 2 - gh / 2, gw = 12;
        g.fill(gx - 2, gy - 2, gx + gw + 2, gy + gh + 2, 0xE0080B12);
        g.outline(gx - 2, gy - 2, gw + 4, gh + 4, 0xFF2A3446);
        float scale = gh / 1.2F;
        int fillTop = gy + gh - (int) (Math.min(h, 1.2F) * scale);
        for (int y = gy + gh - 1; y >= fillTop; y--) g.fill(gx, y, gx + gw, y + 1, heatColour((gy + gh - y) / scale));
        int lo = gy + gh - (int) (AnvilSong.BAND_LO * scale), hi = gy + gh - (int) (AnvilSong.BAND_HI * scale);
        g.fill(gx - 6, hi, gx - 3, lo, LANTERN);
        g.fill(gx + gw + 3, hi, gx + gw + 6, lo, LANTERN);
        g.fill(gx - 6, hi, gx + gw + 6, hi + 1, 0xC0FFCF7A);
        g.fill(gx - 6, lo - 1, gx + gw + 6, lo, 0xC0FFCF7A);
        int needle = gy + gh - (int) (Math.min(h, 1.2F) * scale);
        g.fill(gx - 9, needle, gx + gw + 9, needle + 1, TITLE);
        String say;
        int colour;
        if (pulled) {
            say = "Out of the fire";
            colour = LABEL;
        } else if (h < AnvilSong.BAND_LO) {
            say = "Let it heat...";
            colour = LABEL;
        } else if (h <= AnvilSong.BAND_HI) {
            say = "Pull it now";
            colour = (int) (now * 2) % 6 < 3 ? LANTERN : 0xFFFFE6B0;
        } else if (!AnvilSong.burnt(h)) {
            say = "Too hot, pull it!";
            colour = BAD;
        } else {
            say = "The tip is burning!";
            colour = BAD;
        }
        g.text(font, say, gx + gw + 12, needle - 4, colour, true);
        hint(g, "The glow runs from the tip toward your tongs. Pull the blade out while it glows in the gold: click, or press Space. "
                + "Esc steps away; the work waits.");
    }

    /** The shader's ramp: dull red, orange, yellow, near white. */
    private static int heatColour(float h) {
        float r, gg, b;
        if (h < 0.45F) {
            float k = h / 0.45F;
            r = Mth.lerp(k, 0.30F, 0.85F);
            gg = Mth.lerp(k, 0.03F, 0.16F);
            b = Mth.lerp(k, 0.01F, 0.03F);
        } else if (h < 0.75F) {
            float k = (h - 0.45F) / 0.3F;
            r = Mth.lerp(k, 0.85F, 1F);
            gg = Mth.lerp(k, 0.16F, 0.58F);
            b = Mth.lerp(k, 0.03F, 0.12F);
        } else if (h < 1F) {
            float k = (h - 0.75F) / 0.25F;
            r = 1F;
            gg = Mth.lerp(k, 0.58F, 0.9F);
            b = Mth.lerp(k, 0.12F, 0.5F);
        } else {
            r = 1F;
            gg = 0.97F;
            b = 0.88F;
        }
        return 0xFF000000 | (int) (r * 255) << 16 | (int) (gg * 255) << 8 | (int) (b * 255);
    }

    private void pull(AnvilWork w, float t, float now) {
        float h = AnvilSong.heat(t);
        if (pulled || h > AnvilSong.BURNT) return;
        pulled = true;
        AnvilClient.send(pos, AnvilPayloads.PULL, 0, t, 0F);
        AnvilClient.play(Vec3.atCenterOf(w.forge), AnvilContent.PULL.get(), 1F, 1F);
        if (h < AnvilSong.BAND_LO) say("Too soon: the metal will fight the hammer", BAD, now);
        else if (h <= AnvilSong.BAND_HI) say("In the gold", LANTERN, now);
        else say(AnvilSong.burnt(h) ? "Too hot: the tip came out burnt" : "A little hot", BAD, now);
    }

    // ---- the strikes and the taps -------------------------------------------------------------------------------------

    private void beats(GuiGraphicsExtractor g, AnvilWork w, float t, float now, boolean tap) {
        AnvilSong.Rhythm r = w.rhythm();
        int need = w.beatsNeeded();
        next = Math.max(next, w.beats().size());
        // beats that passed unstruck
        while (next < need && t > r.due(next - w.strikeBase) + AnvilSong.WINDOW) {
            predicted[next++] = 0F;
            say("Missed the beat", BAD, now);
        }
        count(w, r, t, need);
        AnvilScene.struck = next;
        AnvilScene.nextDue = next < need ? w.stageStart + r.due(next - w.strikeBase) : Float.NaN;
        // the marks on the face that's up: struck ones in their colours, the next bright with its ring closing
        int flip = placed.work.flipAt, from = tap ? 0 : next < flip ? 0 : flip, to = tap || next >= flip ? need : flip;
        for (int j = from; j < Math.min(to, need); j++) {
            if (!project(AnvilScene.target(placed.work, tap ? AnvilScene.TAP : AnvilScene.STRIKE, j, v))) continue;
            float px = p[0], py = p[1], rad = Math.max(6F, AnvilSong.MARK * p[2]);
            float score = j < w.beats().size() ? w.beats().get(j) : predicted[j];
            if (!Float.isNaN(score)) disc(g, px, py, rad * 0.5F, scoreColour(score) & 0x00FFFFFF | 0xC0000000);
            else if (j == next) {
                disc(g, px, py, rad, 0x50FFCF7A);
                ring(g, px, py, rad, 2F, LANTERN);
                disc(g, px, py, 2F, 0xFFFFF2C2);
                float dt = r.due(j - w.strikeBase) - t;
                if (dt < APPROACH && dt > -AnvilSong.WINDOW) {
                    float k = Mth.clamp(dt / APPROACH, 0F, 1F);
                    int alpha = (int) (255 * (1F - k) * 0.9F) + 25;
                    ring(g, px, py, rad * (1F + 2.4F * k), 1.5F, alpha << 24 | 0xFFE6B0);
                }
            } else if (j == next + 1) {
                disc(g, px, py, rad * 0.45F, 0x60FFCF7A);
                ring(g, px, py, rad * 0.8F, 1F, 0xB0FFE6B0);
            } else {
                disc(g, px, py, rad * 0.35F, 0x70FFCF7A);
            }
        }
        lane(g, w, r, t, need);
        pips(g, w, need);
        boolean countIn = t < r.length();
        if (tap) hint(g, countIn ? "Listen: the anvil counts the rhythm in. Then three light taps on the beat: the guard, the grip, the pommel."
                : "Seat the hilt: tap on the beat (click, or press Space). Esc steps away; the work waits.");
        else if (w.memory()) hint(g, countIn ? "Listen: the anvil counts the rhythm in. You know this one: one strike on the beat draws it out."
                : "Strike the bright mark on the beat: click it, or press Space where you point.");
        else hint(g, countIn ? "Listen: the anvil counts the rhythm in. Then strike each glowing mark on the beat, and the blade draws out."
                : "Strike the bright mark on the beat: click it, or press Space where you point. Esc steps away; the work waits.");
    }

    /** The count-in bar out loud, then a soft tick on each beat still to strike. */
    private void count(AnvilWork w, AnvilSong.Rhythm r, float t, int need) {
        int step = (int) Math.floor(t / r.step());
        int len = r.bar().length();
        while (countStep < step) {
            countStep++;
            if (countStep < 0 || r.bar().charAt(countStep % len) != 'x') continue;
            Vec3 at = at(placed.work.at);
            if (countStep < len) AnvilClient.play(at, AnvilContent.COUNT.get(), 0.9F, countStep == 0 ? 1.25F : 1F);
            else if (next < need) AnvilClient.play(at, AnvilContent.COUNT.get(), 0.22F, 1F);
        }
    }

    private void strike(AnvilWork w, float t, float now, boolean tap) {
        AnvilSong.Rhythm r = w.rhythm();
        int need = w.beatsNeeded();
        next = Math.max(next, w.beats().size());
        if (next >= need) return;
        float off = t - r.due(next - w.strikeBase);
        if (off < -AnvilSong.WINDOW) return; // nowhere near its beat yet
        AnvilScene.target(placed.work, tap ? AnvilScene.TAP : AnvilScene.STRIKE, next, v);
        Vec3 at = at(v);
        float radii = 0F;
        boolean seen = project(v);
        if (!tap) {
            radii = 9F;
            if (seen) radii = (float) Math.hypot(mx - p[0], my - p[1]) / Math.max(6F, AnvilSong.MARK * p[2]);
        }
        float score = tap ? AnvilSong.timing(off) : AnvilSong.strike(off, radii);
        AnvilClient.send(pos, tap ? AnvilPayloads.TAP : AnvilPayloads.STRIKE, next, t, radii);
        predicted[next] = score;
        AnvilScene.blow(now, next, tap);
        if (score > 0F) AnvilClient.play(at, tap ? AnvilContent.TAP.get() : AnvilContent.STRIKE.get(), tap ? 0.8F : 1F, (tap ? 1.2F : 1F) + (now % 10) * 0.01F);
        if (!tap && AnvilSong.clean(score)) AnvilClient.play(at, AnvilContent.RING.get(), 0.7F, WarAnvil.ringPitch(next + 1, need));
        if (!tap && next + 1 == placed.work.flipAt) AnvilClient.play(at, AnvilContent.FLIP.get(), 0.8F, 1F);
        if (seen) burst(p[0], p[1], score, now, tap ? 0.5F : 1F);
        next++;
        AnvilScene.struck = next;
        float timing = AnvilSong.timing(off);
        if (tap) {
            if (timing >= 0.8F) say(next == 1 ? "The guard is on" : next == 2 ? "The grip is bound" : "The pommel is set", LANTERN, now);
            else say(off < 0 ? "Early" : "Late", BAD, now);
        } else if (AnvilSong.clean(score)) say(timing >= 1F && AnvilSong.aim(radii) >= 1F ? "Perfect" : "Clean", LANTERN, now);
        else if (timing < 0.6F) say(off < 0 ? "Early" : "Late", BAD, now);
        else say("Wide of the mark", BAD, now);
        if (!tap && next == placed.work.flipAt) calloutAt = now + 4; // the tongs turn it over: the callout holds a moment longer
    }

    // ---- by hand: the knap and the bind (E) ----------------------------------------------------------------------------

    /** The flake points down the edge: struck ones in their colours, the next lit; your own pace, aim is all that counts. */
    private void knap(GuiGraphicsExtractor g, AnvilWork w, float t, float now) {
        int need = w.beatsNeeded();
        next = Math.max(next, w.beats().size());
        AnvilScene.struck = next;
        AnvilScene.nextDue = now + 2F; // the stone held up, ready
        boolean ready = Float.isNaN(knapLast) || t - knapLast >= AnvilSong.KNAP_GAP;
        for (int j = 0; j < need; j++) {
            if (!project(AnvilScene.target(placed.work, AnvilScene.FLAKE, j, v))) continue;
            float px = p[0], py = p[1], rad = Math.max(6F, AnvilSong.MARK * p[2]);
            float score = j < w.beats().size() ? w.beats().get(j) : predicted[j];
            if (!Float.isNaN(score)) disc(g, px, py, rad * 0.5F, scoreColour(score) & 0x00FFFFFF | 0xC0000000);
            else if (j == next) {
                float k = 0.5F + 0.5F * (float) Math.sin(now * 0.3);
                disc(g, px, py, rad, ready ? 0x50FFCF7A : 0x283A4A60);
                ring(g, px, py, rad * (1.1F + 0.25F * k), 1.5F, ready ? LANTERN : 0xFF6F8299);
                disc(g, px, py, 2F, 0xFFFFF2C2);
            } else disc(g, px, py, rad * 0.35F, 0x70FFCF7A);
        }
        pips(g, w, need);
        hint(g, w.memory() || AnvilWork.styleOf(w.weapon).equals(AnvilWork.BLADE) && need == 1
                ? "Strike the lit point on the edge with the stone: click it." : AnvilWork.styleOf(w.weapon).equals(AnvilWork.BLADE)
                ? "Strike the lit point on the edge with the stone, one flake at a time: click it. Take your time; only your aim counts."
                : "Strike the lit point on the edge with the stone, one flake at a time: click it. Take your time; only your aim counts. Then you'll bind the grip.");
    }

    private void flake(AnvilWork w, float t, float now) {
        int need = w.beatsNeeded();
        next = Math.max(next, w.beats().size());
        if (next >= need || !Float.isNaN(knapLast) && t - knapLast < AnvilSong.KNAP_GAP) return;
        AnvilScene.target(placed.work, AnvilScene.FLAKE, next, v);
        Vec3 at = at(v);
        float radii = 9F;
        boolean seen = project(v);
        if (seen) radii = (float) Math.hypot(mx - p[0], my - p[1]) / Math.max(6F, AnvilSong.MARK * p[2]);
        float score = AnvilSong.knap(radii);
        knapLast = t;
        AnvilClient.send(pos, AnvilPayloads.KNAP, next, t, radii);
        predicted[next] = score;
        AnvilScene.blow(now, next, true);
        AnvilClient.play(at, AnvilContent.TAP.get(), 0.7F, 1.55F + (now % 10) * 0.01F);
        if (seen) burst(p[0], p[1], score, now, 0.6F);
        next++;
        AnvilScene.struck = next;
        if (score >= 0.8F) say("A clean flake", LANTERN, now);
        else if (score > 0.3F) say("A rough flake", LABEL, now);
        else say("Wide: it chipped the wrong way", BAD, now);
    }

    /** The cord: hold and draw circles round the grip, four turns as evenly as you can. */
    private void bind(GuiGraphicsExtractor g, AnvilWork w, float t, float now) {
        AnvilShape sh = placed.work.shape;
        boolean seen = project(placed.work.point(AnvilScene.BLADE_X, sh.hands[0], 0F, v));
        float cx = seen ? p[0] : width / 2F, cy = seen ? p[1] : height / 2F, r = 28F;
        if (winding) wind(w, t, now, cx, cy);
        ring(g, cx, cy, r, 1.5F, winding ? 0xC0FFCF7A : 0x806F8299);
        if (winding) {
            float turn = (float) (Math.abs(windAcc) % (Math.PI * 2) / (Math.PI * 2));
            arc(g, cx, cy, r + 5F, turn, LANTERN);
            float a = windLast;
            if (!Float.isNaN(a)) disc(g, cx + Mth.cos(a) * r, cy + Mth.sin(a) * r, 3F, 0xFFFFF2C2);
        } else {
            float k = 0.5F + 0.5F * (float) Math.sin(now * 0.2);
            disc(g, cx + r, cy, 3F, (int) (90 + 140 * k) << 24 | 0xFFCF7A);
        }
        int y = bottom() - 28, pw = 14, x0 = width / 2 - AnvilSong.TURNS * (pw + 3) / 2;
        for (int j = 0; j < AnvilSong.TURNS; j++) {
            int x = x0 + j * (pw + 3);
            if (j < windTurns) g.fill(x, y, x + pw, y + 4, LANTERN);
            else g.outline(x, y, pw, 4, 0x803A4A60);
        }
        hint(g, winding ? "Keep circling at the same speed..."
                : "Hold the mouse button (or Space) and draw circles round the grip to wind the cord: four turns, as evenly as you can.");
    }

    private void holdCord(AnvilWork w, float t) {
        if (winding || windTurns > 0) return;
        winding = true;
        windAcc = 0F;
        windLast = Float.NaN;
        windTurns = 0;
        turnAt[0] = t;
        AnvilScene.windTurns = 0;
        AnvilClient.send(pos, AnvilPayloads.WIND, 0, t, 0F);
    }

    private void releaseCord(float t, float now) {
        if (!winding) return;
        winding = false;
        AnvilScene.windTurns = windTurns;
        if (windTurns >= AnvilSong.TURNS) return;
        AnvilClient.send(pos, AnvilPayloads.RELEASE, windTurns, t, 0F);
        say("Let go early: the cord is loose", BAD, now);
    }

    /** The mouse's angle round the grip, added up; each whole turn is told to the server with its time. */
    private void wind(AnvilWork w, float t, float now, float cx, float cy) {
        float dx = mx - cx, dy = my - cy;
        if (dx * dx + dy * dy >= 64F) {
            float a = (float) Math.atan2(dy, dx);
            if (!Float.isNaN(windLast)) windAcc += Mth.wrapDegrees((a - windLast) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            windLast = a;
        }
        AnvilScene.windAngle = windAcc;
        int done = (int) (Math.abs(windAcc) / (Math.PI * 2));
        while (windTurns < Math.min(done, AnvilSong.TURNS)) {
            windTurns++;
            turnAt[windTurns] = t;
            AnvilScene.windTurns = windTurns;
            AnvilClient.send(pos, AnvilPayloads.WIND, windTurns, t, 0F);
            AnvilClient.play(at(placed.work.at), AnvilContent.SCRAPE.get(), 0.4F, 1.4F + windTurns * 0.08F);
            if (windTurns >= 2) {
                float mean = (turnAt[windTurns - 1] - turnAt[0]) / (windTurns - 1), last = turnAt[windTurns] - turnAt[windTurns - 1];
                if (Math.abs(last - mean) <= mean / 8F) say("Even", LANTERN, now);
                else say(last < mean ? "Too quick" : "Too slow", BAD, now);
            }
        }
        if (windTurns >= AnvilSong.TURNS) {
            winding = false;
            say("Bound tight", LANTERN, now);
        }
    }

    /** The beat lane: what's coming slides to the line; the count-in shows as the bar to listen to. */
    private void lane(GuiGraphicsExtractor g, AnvilWork w, AnvilSong.Rhythm r, float t, int need) {
        int lw = Math.min(320, width - 80), lx = width / 2 - lw / 2, ly = bottom() - 42;
        float perTick = 2.4F;
        int hit = lx + 56;
        g.fill(lx, ly - 7, lx + lw, ly + 7, 0xB0080B12);
        g.fill(lx, ly, lx + lw, ly + 1, 0x403A4A60);
        g.fill(hit, ly - 9, hit + 1, ly + 9, 0xE0FFCF7A);
        int len = r.bar().length();
        for (int i = 0; i < len; i++) {
            if (r.bar().charAt(i) != 'x') continue;
            float x = hit + (i * r.step() - t) * perTick;
            if (x < lx || x > lx + lw) continue;
            g.fill((int) x - 1, ly - 3, (int) x + 1, ly + 4, 0x90AFC2D8);
        }
        for (int j = Math.max(w.strikeBase, next - 2); j < need; j++) {
            float x = hit + (r.due(j - w.strikeBase) - t) * perTick;
            if (x > lx + lw) break;
            if (x < lx) continue;
            float score = j < w.beats().size() ? w.beats().get(j) : predicted[j];
            diamond(g, x, ly, j == next ? 5 : 4, Float.isNaN(score) ? (j == next ? LANTERN : 0xFFAFC2D8) : scoreColour(score));
        }
    }

    /** One pip a beat, coloured by how it went. */
    private void pips(GuiGraphicsExtractor g, AnvilWork w, int need) {
        int y = bottom() - 28, pw = 8, x0 = width / 2 - need * (pw + 3) / 2;
        for (int j = 0; j < need; j++) {
            float score = j < w.beats().size() ? w.beats().get(j) : predicted[j];
            int x = x0 + j * (pw + 3);
            if (Float.isNaN(score)) g.outline(x, y, pw, 4, 0x803A4A60);
            else g.fill(x, y, x + pw, y + 4, scoreColour(score));
        }
    }

    private static int scoreColour(float s) {
        return s >= 0.8F ? 0xFFFFCF7A : s >= 0.45F ? 0xFF9FE6D6 : 0xFFFF8A7A;
    }

    // ---- quench --------------------------------------------------------------------------------------------------------

    private void quench(GuiGraphicsExtractor g, AnvilWork w, float t, float now) {
        boolean plunged = !Float.isNaN(AnvilScene.plungeAt);
        float held = !plunged ? 0F : (lifted ? AnvilScene.liftAt : now) - AnvilScene.plungeAt;
        if (holding && held >= AnvilSong.HOLD_MAX) lift(w, t, now); // held to the top: it lifts itself
        if (!plunged && !cooled && t > AnvilSong.PLUNGE_LATE) {
            cooled = true;
            say("It cooled in the air: soft, but whole", LABEL, now);
        }
        // the meter beside the trough: the dark line climbs from the point; let go at the gold notch
        int mh = Math.min(140, height / 2), mw = 10, gx = width / 2 + Math.min(150, width / 4), gy = height / 2 - mh / 2;
        g.fill(gx - 2, gy - 2, gx + mw + 2, gy + mh + 2, 0xE0080B12);
        g.outline(gx - 2, gy - 2, mw + 4, mh + 4, 0xFF2A3446);
        g.fill(gx, gy, gx + mw, gy + mh, 0xFFB8461E); // hot metal
        float line = AnvilSong.line(held);
        int top = gy + mh - Math.round(line * mh);
        if (line > 0F) g.fill(gx, top, gx + mw, gy + mh, 0xFF2A3038); // cooled from the point up
        float band = 2F * AnvilSong.LINE_RATE * mh;
        int notch = gy + mh - Math.round(AnvilSong.NOTCH * mh);
        g.fill(gx - 6, notch - Math.round(band), gx + mw + 6, notch + Math.round(band) + 1, 0x90FFCF7A);
        g.fill(gx - 8, notch, gx + mw + 8, notch + 1, LANTERN);
        if (plunged && !lifted) g.fill(gx - 9, top, gx + mw + 9, top + 1, TITLE);
        if (!plunged && project(v.set(placed.work.at))) {
            float k = 0.5F + 0.5F * (float) Math.sin(now * 0.25);
            ring(g, p[0], p[1], 22F, 1.5F, (int) (60 + 120 * k) << 24 | 0xFFCF7A);
        }
        hint(g, lifted ? "" : plunged ? "Keep holding... let go when the dark line reaches the gold notch."
                : "Hold the mouse button (or Space) to plunge the blade into the water. Let go when the dark line reaches the gold notch.");
    }

    private void plunge(AnvilWork w, float t, float now) {
        if (holding || lifted || !Float.isNaN(AnvilScene.plungeAt) || t > AnvilSong.PLUNGE_LATE) return;
        holding = true;
        AnvilScene.plungeAt = now;
        AnvilClient.send(pos, AnvilPayloads.PLUNGE, 0, t, 0F);
        AnvilClient.play(at(placed.work.at), AnvilContent.QUENCH.get(), 1F, 1F);
    }

    private void lift(AnvilWork w, float t, float now) {
        if (!holding) return;
        holding = false;
        lifted = true;
        AnvilScene.liftAt = now;
        float held = now - AnvilScene.plungeAt;
        AnvilClient.send(pos, AnvilPayloads.LIFT, 0, t, 0F);
        AnvilClient.play(at(placed.work.at), AnvilContent.LIFT.get(), 0.9F, 1F);
        if (AnvilSong.quench(held) >= 1F) say("Quenched true", LANTERN, now);
        else if (AnvilSong.brittle(held)) say("Held too long: it'll keep a brittle hairline", BAD, now);
        else say("Lifted early: the steel stays a little soft", BAD, now);
    }

    // ---- the draw and the card ---------------------------------------------------------------------------------------

    void done(AnvilPayloads.Done d) {
        float now = AnvilClient.now(0);
        done = d;
        doneAt = now;
        AnvilSong.Quality q = AnvilSong.Quality.of(d.quality());
        if (q.ordinal() >= AnvilSong.Quality.MASTERWORK.ordinal()) burst(width / 2F, height * 0.5F, 1F, now, 1.6F);
    }

    private void draw(GuiGraphicsExtractor g, WarAnvilBlockEntity be, float now) {
        boolean standing = !be.slot().isEmpty();
        if (standing) shown = be.slot();
        else if (Float.isNaN(emptyAt)) emptyAt = now;
        // holding to draw it out of the stone: a scrape rising into its note
        if (standing && !Float.isNaN(drawFrom) && !drawSent) {
            AnvilScene.drawK = Mth.clamp((now - drawFrom) / AnvilSong.DRAW_HOLD, 0F, 1F);
            if (AnvilScene.drawK >= 1F) {
                drawSent = true;
                drawnAt = now;
                AnvilClient.send(pos, AnvilPayloads.DRAW, 0, 0F, 0F);
                AnvilClient.play(Vec3.atCenterOf(pos), AnvilContent.DRAWN.get(), 1F, 1F);
                say("It's yours", LANTERN, now);
            }
        }
        if (standing && project(AnvilScene.model(AnvilScene.frame(be.getBlockState(), frame), 0.5F, 0.75F, 3F / 16F, v))) {
            float k = AnvilScene.drawK;
            if (Float.isNaN(drawFrom) && !drawSent) {
                float pulse = 0.5F + 0.5F * (float) Math.sin(now * 0.2);
                ring(g, p[0], p[1], 16F, 1.5F, (int) (70 + 110 * pulse) << 24 | 0xFFCF7A);
            } else if (k > 0F) arc(g, p[0], p[1], 16F, k, LANTERN);
        }
        if (done != null) card(g, now);
        if (Float.isNaN(drawnAt) && standing)
            hint(g, "Hold the mouse button (or Space) to draw it from the stone.");
        else if (!Float.isNaN(drawnAt) || !standing)
            hint(g, "Try its first cut, or you're done. Esc closes this.");
        if (AnvilClient.autoplay && standing && !drawSent) {
            // tours: the card a moment, then the draw
            float since = Float.isNaN(doneAt) ? 0F : now - doneAt;
            if (Float.isNaN(drawFrom) && since > 40F) drawFrom = now;
        }
    }

    private void card(GuiGraphicsExtractor g, float now) {
        AnvilPayloads.Done d = done;
        AnvilSong.Quality q = AnvilSong.Quality.of(d.quality());
        float k = Mth.clamp((now - doneAt) / 14F, 0F, 1F);
        int cw = cardW(), cx = cardX() - (int) ((1F - k) * 12);
        var about = font.split(Component.literal(about(d)), cw - 20);
        boolean drawn = drawn();
        int ch = cardH(drawn), cy = cardY(ch);
        int alpha = (int) (k * 0xE8);
        g.fill(cx, cy, cx + cw, cy + ch, alpha << 24 | 0x0F1724);
        g.outline(cx, cy, cw, ch, (int) (k * 255) << 24 | q.colour & 0xFFFFFF);
        if (k < 0.3F) return;
        ItemStack it = cardItem();
        g.item(it, cx + 10, cy + 10);
        var name = font.split(it.getHoverName(), cw - 42);
        int ny = cy + 10;
        for (var line : name) {
            g.text(font, line, cx + 32, ny, TITLE, false);
            ny += 10;
        }
        int dy = 10 * (name.size() - 1); // a long name takes a second line, the rest moving down
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx + 32, cy + 22 + dy);
        pose.scale(1.5F, 1.5F);
        g.text(font, q.label, 0, 0, q.colour, true);
        pose.popMatrix();
        var player = Minecraft.getInstance().player;
        g.text(font, "Forged by " + (player == null ? "you" : player.getGameProfile().name()), cx + 10, cy + 42 + dy, DIM, false);
        int ly = cy + 56 + dy;
        for (var line : about) {
            g.text(font, line, cx + 10, ly, LABEL, false);
            ly += 10;
        }
        for (String line : dev.awaken.core.api.ForgeHooks.cardLines(it)) {
            g.text(font, line, cx + 10, ly, 0xFFA898FF, false);
            ly += 10;
        }
        if (drawn) {
            button(g, cx + 10, cy + ch - 24, "First cut");
            button(g, cx + cw - 10 - bw("Done"), cy + ch - 24, "Done");
        }
    }

    /** The card stands to the left of the weapon in the stone, so the weapon stays in view. */
    private int cardW() {
        return Math.min(240, width / 2 - 66);
    }

    private int cardX() {
        return width / 2 - 50 - cardW();
    }

    private int cardH(boolean drawn) {
        int w = cardW();
        return 62 + 10 * font.split(cardItem().getHoverName(), w - 42).size() + 10 * font.split(Component.literal(about(done)), w - 20).size()
                + 10 * dev.awaken.core.api.ForgeHooks.cardLines(cardItem()).size() + (drawn ? 26 : 0);
    }

    private ItemStack cardItem() {
        return shown.isEmpty() ? new ItemStack(BuiltInRegistries.ITEM.getValue(Awaken.id(weapon))) : shown;
    }

    private int cardY(int ch) {
        return height / 2 - ch / 2 - 10;
    }

    private boolean drawn() {
        return !Float.isNaN(drawnAt) || !Float.isNaN(emptyAt);
    }

    /** The card's words: how well, the Temper it leaves with (and whose limit held it), its flaw, and how it was made. */
    private static String about(AnvilPayloads.Done d) {
        AnvilSong.Quality q = AnvilSong.Quality.of(d.quality());
        String how = switch (q) {
            case PLAIN -> "It'll serve.";
            case FINE -> "Good, even work.";
            case MASTERWORK -> "A blade to be proud of.";
            case FLAWLESS -> "Every blow landed where it should.";
        };
        String temper = d.temper() <= 0 ? " It leaves the anvil untempered."
                : " It leaves the anvil at Temper " + numeral(d.temper()) + (d.temper() < q.temper
                ? ": that's as far as your Forge can temper it. Put an anvil and an armour stand in the Forge's room and it'll go further." : ".");
        String flaw = switch (d.flaw()) {
            case AnvilSong.BURNT_TIP -> " The tip stayed in the fire too long and came out burnt.";
            case AnvilSong.BRITTLE -> " It was held in the water too long: there's a brittle hairline in it.";
            default -> "";
        };
        String made = ((d.flags() & WarAnvil.FROM_MEMORY) != 0 ? " You made it from memory." : "")
                + ((d.flags() & WarAnvil.TWIN) != 0 ? " A second hammer struck between your beats: it's twin-struck." : "")
                + ((d.flags() & WarAnvil.ARMOURY) != 0 ? " Forged in your Armoury, it will last a quarter longer." : "");
        return how + temper + flaw + made;
    }

    private static String numeral(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }

    private boolean cardClick(double x, double y) {
        if (done == null || !drawn()) return false;
        int cw = cardW(), cx = cardX(), ch = cardH(true), by = cardY(ch) + ch - 24;
        if (y < by || y >= by + 18) return false;
        if (x >= cx + 10 && x < cx + 10 + bw("First cut")) {
            // the scene closes on the style's first blow
            onClose();
            KeyMapping.click(Minecraft.getInstance().options.keyAttack.getKey());
            return true;
        }
        if (x >= cx + cw - 10 - bw("Done") && x < cx + cw - 10) {
            onClose();
            return true;
        }
        return false;
    }

    private int bw(String s) {
        return font.width(s) + 14;
    }

    private void button(GuiGraphicsExtractor g, int x, int y, String s) {
        boolean over = mx >= x && mx < x + bw(s) && my >= y && my < y + 18;
        g.fill(x, y, x + bw(s), y + 18, over ? 0xF02A3A56 : 0xE0101622);
        g.outline(x, y, bw(s), 18, LANTERN);
        g.text(font, s, x + 7, y + 5, TITLE, false);
    }

    // ---- autoplay ------------------------------------------------------------------------------------------------------

    /** Tours and demos: every act on its beat, judged by the server like any. */
    private void autoplay(AnvilWork w, float t, float now) {
        switch (w.stage) {
            case AnvilSong.HEAT -> {
                if (!pulled && AnvilSong.heat(t) >= 0.65F) pull(w, t, now);
            }
            case AnvilSong.KNAP -> {
                next = Math.max(next, w.beats().size());
                if (next >= w.beatsNeeded() || t < 12F || !Float.isNaN(knapLast) && t - knapLast < 10F) return;
                if (project(AnvilScene.target(placed.work, AnvilScene.FLAKE, next, v))) {
                    mx = Math.round(p[0]);
                    my = Math.round(p[1]);
                }
                flake(w, t, now);
            }
            case AnvilSong.BIND -> {
                // circles at an even pace: a turn each ten ticks, the cursor drawn round the grip
                if (!winding && windTurns == 0 && t >= 12F) holdCord(w, t);
                if (winding && project(placed.work.point(AnvilScene.BLADE_X, placed.work.shape.hands[0], 0F, v))) {
                    double a = (t - turnAt[0]) / 10.0 * Math.PI * 2;
                    mx = (int) Math.round(p[0] + Math.cos(a) * 28);
                    my = (int) Math.round(p[1] + Math.sin(a) * 28);
                }
            }
            case AnvilSong.SHAPE, AnvilSong.HILT -> {
                boolean tap = w.stage == AnvilSong.HILT;
                next = Math.max(next, w.beats().size());
                if (next >= w.beatsNeeded() || t < w.rhythm().due(next - w.strikeBase)) return;
                if (!tap && project(AnvilScene.target(placed.work, AnvilScene.STRIKE, next, v))) {
                    mx = Math.round(p[0]);
                    my = Math.round(p[1]);
                }
                strike(w, t, now, tap);
            }
            case AnvilSong.QUENCH -> {
                if (Float.isNaN(AnvilScene.plungeAt) && t >= 20F) plunge(w, t, now);
                else if (holding && now - AnvilScene.plungeAt >= AnvilSong.idealHold()) lift(w, t, now);
            }
            default -> {}
        }
    }

    // ---- input -------------------------------------------------------------------------------------------------------

    /** A press or a release (click or Space): whatever the stage asks for, where the cursor is. */
    private boolean act(boolean press) {
        WarAnvilBlockEntity be = AnvilClient.anvil(pos);
        if (be == null) return false;
        float now = AnvilClient.now(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        AnvilWork w = be.work();
        if (w == null) {
            if (be.slot().isEmpty() || drawSent) return false;
            if (press && Float.isNaN(drawFrom)) {
                drawFrom = now;
                AnvilClient.play(Vec3.atCenterOf(pos), AnvilContent.SCRAPE.get(), 0.9F, 1F);
            } else if (!press && !Float.isNaN(drawFrom)) {
                // let go too soon: it settles back into the stone
                drawFrom = Float.NaN;
                AnvilScene.drawK = 0F;
            }
            return true;
        }
        if (w.stage != runStage) return false;
        float t = now - w.stageStart;
        switch (w.stage) {
            case AnvilSong.HEAT -> {
                if (press) pull(w, t, now);
            }
            case AnvilSong.SHAPE -> {
                if (press) strike(w, t, now, false);
            }
            case AnvilSong.QUENCH -> {
                if (press) plunge(w, t, now);
                else lift(w, t, now);
            }
            case AnvilSong.HILT -> {
                if (press) strike(w, t, now, true);
            }
            case AnvilSong.KNAP -> {
                if (press) flake(w, t, now);
            }
            case AnvilSong.BIND -> {
                if (press) holdCord(w, t);
                else releaseCord(t, now);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.mouseClicked(event, doubleClick);
        mx = (int) event.x();
        my = (int) event.y();
        if (cardClick(event.x(), event.y())) return true;
        return act(true) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) act(false);
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() != InputConstants.KEY_SPACE) return super.keyPressed(event);
        if (spaceDown) return true; // held: the key's repeats aren't more blows
        spaceDown = true;
        return act(true);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() != InputConstants.KEY_SPACE) return super.keyReleased(event);
        spaceDown = false;
        return act(false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- drawing -------------------------------------------------------------------------------------------------------

    private void say(String text, int colour, float now) {
        callout = text;
        calloutColour = colour;
        calloutAt = now;
    }

    private void callout(GuiGraphicsExtractor g, float now) {
        float age = now - calloutAt;
        if (age < 0) age = 0;
        if (age > 22 || callout.isEmpty()) return;
        float k = age < 3 ? age / 3F : 1F - Math.max(0F, age - 12) / 10F;
        int alpha = Math.max(8, (int) (k * 255));
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(width / 2F, height * 0.26F - Math.min(age, 6F));
        pose.scale(1.6F, 1.6F);
        g.centeredText(font, Component.literal(callout), 0, 0, alpha << 24 | calloutColour & 0xFFFFFF);
        pose.popMatrix();
    }

    /** Sparks off a blow: bright and many for a clean one, a few dull ones for a poor one. */
    private void burst(float x, float y, float score, float now, float size) {
        int n = (int) ((AnvilSong.clean(score) ? 14 : score > 0F ? 6 : 2) * size);
        int colour = AnvilSong.clean(score) ? 0xFFFFE6A0 : 0xFFFF9050;
        for (int i = 0; i < n; i++) {
            int s = sparkNext++ % SPARKS;
            double a = Math.PI * 2 * ((i + now * 0.37) % n) / n;
            float speed = (2.2F + (float) ((i * 7919 + (int) now) % 13) / 5F) * size;
            sx[s] = x;
            sy[s] = y;
            svx[s] = (float) Math.cos(a) * speed;
            svy[s] = (float) Math.sin(a) * speed - 2.4F * size;
            sborn[s] = now;
            scol[s] = i % 3 == 0 ? 0xFFFFFFFF : colour;
        }
        ringX = x;
        ringY = y;
        ringR = AnvilSong.clean(score) ? 30 * size : 14 * size;
        ringAt = now;
    }

    private void sparks(GuiGraphicsExtractor g, float now) {
        for (int s = 0; s < SPARKS; s++) {
            float age = now - sborn[s];
            if (age < 0 || age > 12) continue;
            float x = sx[s] + svx[s] * age, y = sy[s] + svy[s] * age + 0.35F * age * age;
            int alpha = (int) (255 * (1F - age / 12F));
            g.fill((int) x, (int) y, (int) x + 2, (int) y + 2, alpha << 24 | scol[s] & 0xFFFFFF);
        }
    }

    private void ring(GuiGraphicsExtractor g, float now) {
        float age = now - ringAt;
        if (age < 0 || age > 10) return;
        float k = age / 10F;
        ring(g, ringX, ringY, 6 + ringR * k, 2F, (int) (200 * (1F - k)) << 24 | 0xFFE6B0);
    }

    private static void disc(GuiGraphicsExtractor g, float cx, float cy, float r, int colour) {
        int ri = (int) Math.ceil(r);
        for (int dy = -ri; dy <= ri; dy++) {
            float half = (float) Math.sqrt(Math.max(0F, r * r - dy * dy));
            if (half <= 0F) continue;
            g.fill(Math.round(cx - half), Math.round(cy) + dy, Math.round(cx + half), Math.round(cy) + dy + 1, colour);
        }
    }

    /** A ring as a few dozen short turned bars round it. */
    private static void ring(GuiGraphicsExtractor g, float cx, float cy, float r, float th, int colour) {
        arcBars(g, cx, cy, r, th, 1F, colour);
    }

    /** Part of a ring, clockwise from the top (k of the way round): the draw's hold. */
    private static void arc(GuiGraphicsExtractor g, float cx, float cy, float r, float k, int colour) {
        arcBars(g, cx, cy, r, 2.5F, k, colour);
    }

    private static void arcBars(GuiGraphicsExtractor g, float cx, float cy, float r, float th, float k, int colour) {
        int n = Mth.clamp((int) (r * 0.9F), 10, 40), t = Math.max(1, Math.round(th));
        float mid = r - th / 2F, len = (float) (Math.PI * 2 * mid / n) + 1F;
        var pose = g.pose();
        for (int i = 0; i < Math.round(n * k); i++) {
            double a = Math.PI * 2 * i / n - Math.PI / 2;
            pose.pushMatrix();
            pose.translate(cx + (float) Math.cos(a) * mid, cy + (float) Math.sin(a) * mid);
            pose.rotate((float) (a + Math.PI / 2));
            g.fill(-Math.round(len / 2), -t / 2, Math.round(len / 2), t - t / 2, colour);
            pose.popMatrix();
        }
    }

    private static void diamond(GuiGraphicsExtractor g, float cx, float cy, int r, int colour) {
        for (int dy = -r; dy <= r; dy++) {
            int half = r - Math.abs(dy);
            g.fill(Math.round(cx) - half, Math.round(cy) + dy, Math.round(cx) + half + 1, Math.round(cy) + dy + 1, colour);
        }
    }
}
