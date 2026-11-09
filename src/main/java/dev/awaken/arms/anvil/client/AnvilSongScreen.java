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
 * The ranks' acts (§46.9): A's twist (circles at the bar's end), C's grind (the edge kept on a drifting line while the
 * wheel turns), B's balance (the Deepglass weight slid along the grip till it lies level), S's starlight (a ring of light
 * closing on the point). Then the weapon standing in the stone, held to draw it out, and its card. The server judges every act by its own
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
    private final float[] predicted = new float[24];
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
    // the ranks' acts: the last grind sample (ticks from the stage's start), the act told to the server, and the balance's level since and frame
    private float grindLast = Float.NaN, levelSince = Float.NaN, tiltAt = Float.NaN, humAt = Float.NaN;
    private boolean actSent;

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
    // the card's width, fitted once for its words and the window (cardW)
    private @Nullable Object fitFor, fitShown;
    private int fitAt, fitW;

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
                case AnvilSong.TWIST -> twist(g, w, t, now);
                case AnvilSong.GRIND -> grind(g, w, t, now);
                case AnvilSong.BALANCE -> balance(g, w, t, now);
                case AnvilSong.STAR -> star(g, w, t, now);
                case AnvilSong.EYE, AnvilSong.TIPS -> beats(g, w, t, now, false);
                case AnvilSong.HANG, AnvilSong.TEND -> beats(g, w, t, now, true);
                case AnvilSong.COLOURS -> colours(g, w, t, now);
                case AnvilSong.SEAM -> quench(g, w, t, now);
                case AnvilSong.SPINE -> grind(g, w, t, now);
                default -> {}
            }
            backOut(g, w);
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
        grindLast = levelSince = tiltAt = humAt = Float.NaN;
        actSent = false;
        AnvilScene.grindEdge = AnvilScene.tilt = 0F;
        AnvilScene.weight = 0F;
        AnvilScene.turning = false;
    }

    private void header(GuiGraphicsExtractor g, AnvilWork w) {
        int top = Math.max(10, height / 14) + 6;
        String stage = switch (w.stage) {
            case AnvilSong.HEAT -> w.whole() ? "Heat it whole" : "Heat";
            case AnvilSong.SHAPE -> w.kind == AnvilWork.TEMPER ? "Temper it" : w.kind == AnvilWork.REFORGE ? "Fold in the new metal" : "Draw it out";
            case AnvilSong.QUENCH -> "Quench";
            case AnvilSong.KNAP -> "Knap the edge";
            case AnvilSong.BIND -> "Bind the grip";
            case AnvilSong.TWIST -> "Twist the bar";
            case AnvilSong.GRIND -> "Grind the edge";
            case AnvilSong.BALANCE -> "Balance it";
            case AnvilSong.STAR -> "Quench it in starlight";
            case AnvilSong.EYE -> "Cut the eye";
            case AnvilSong.TIPS -> "Steel the tips";
            case AnvilSong.SPINE -> "Thread the spine";
            case AnvilSong.SEAM -> "Pour the seam";
            case AnvilSong.COLOURS -> "Run the colours";
            case AnvilSong.HANG -> "Hang the head";
            case AnvilSong.TEND -> w.kind == AnvilWork.DRESS ? "Dress the edge" : w.kind == AnvilWork.MIGRATE ? "Make it yours" : "Work it in";
            default -> w.kind == AnvilWork.ATTUNE ? "Set the stone" : "The hilt";
        };
        if (w.pick && w.stage == AnvilSong.SHAPE) stage = "Draw the points";
        if (w.pick && w.stage == AnvilSong.KNAP) stage = "Knap the head";
        if (w.pick && w.stage == AnvilSong.BIND) stage = "Bind it to the haft";
        // a weapon worked again goes by its own name, and says what it's being worked to
        String name = w.whole() && !w.piece.isEmpty() ? w.piece.getHoverName().getString() : weaponName(w.weapon);
        String to = switch (w.kind) {
            case AnvilWork.MEMORY -> "  ·  from memory";
            case AnvilWork.TEMPER -> "  ·  to Temper " + numeral(w.target);
            case AnvilWork.REFORGE -> "  ·  to " + dev.awaken.arms.ArmsArmoury.TIERS[Mth.clamp(w.target, 0, 5)] + "-rank";
            case AnvilWork.ATTUNE -> "  ·  " + dev.awaken.arms.ArmsForge.forceName(dev.awaken.core.Forces.Force.values()[Math.floorMod(w.target, 5)]);
            case AnvilWork.RAISE -> "  ·  raised";
            case AnvilWork.REDRAW -> "  ·  re-drawn";
            default -> "";
        };
        g.centeredText(font, Component.literal(name + "  ·  " + stage + to), width / 2, top, TITLE);
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
        float h = w.heat(t);
        // the bellows breathe; worked by an Emberkit (§46.6) they breathe steadily and harder, the fire flaring at each
        if (now - bellowsAt > (w.bellows ? 30 : 46) && !pulled) {
            bellowsAt = now;
            AnvilClient.play(Vec3.atCenterOf(w.forge), AnvilContent.BELLOWS.get(), w.bellows ? 0.7F : 0.45F, 0.95F + (now % 7) * 0.01F);
            if (w.bellows) AnvilClient.puff(w.forge);
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
        hint(g, (w.bellows ? "Your Emberkit is working the bellows: the heat climbs evenly and the gold lasts a little longer. " : "")
                + "The glow runs from the tip toward your tongs. Pull the blade out while it glows in the gold: click, or press Space. "
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
        float h = w.heat(t);
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
        if (w.pick) {
            String strike = "Strike the bright mark on the beat: click it, or press Space where you point. Esc steps away; the work waits.";
            String tapped = "Tap on the beat (click, or press Space). Esc steps away; the work waits.";
            hint(g, switch (w.stage) {
                case AnvilSong.EYE -> countIn ? "Listen: the anvil counts the rhythm in. Then drive the drift through the bar on the beat, from one face and then the other." : strike;
                case AnvilSong.TIPS -> countIn ? "Listen: the anvil counts the rhythm in. Then one square blow on the beat for each point, to steel it." : strike;
                case AnvilSong.HANG -> countIn ? "Listen: the anvil counts the rhythm in. Then three light taps on the beat seat the head on the haft." : tapped;
                case AnvilSong.TEND -> countIn ? "Listen: the anvil counts the rhythm in. Then a few light taps on the beat." : tapped;
                default -> w.memory() ? (countIn ? "Listen: the anvil counts the rhythm in. You know this one: one strike on the beat draws the points." : strike)
                        : countIn ? "Listen: the anvil counts the rhythm in. Then strike each glowing mark on the beat, and the two points draw out." : strike;
            });
            return;
        }
        if (tap && w.kind == AnvilWork.ATTUNE) hint(g, countIn ? "Listen: the anvil counts the rhythm in. Then three taps on the beat drive the stone into the guard."
                : "Drive the stone home: tap on the beat (click, or press Space). Esc steps away; the work waits.");
        else if (tap) hint(g, countIn ? "Listen: the anvil counts the rhythm in. Then three light taps on the beat: the guard, the grip, the pommel."
                : "Seat the hilt: tap on the beat (click, or press Space). Esc steps away; the work waits.");
        else if (w.kind == AnvilWork.TEMPER) hint(g, countIn ? "Listen: the anvil counts the rhythm in. Then one strike on the beat for each level of Temper."
                : "Strike the bright mark on the beat: click it, or press Space where you point. Esc steps away; the work waits.");
        else if (w.kind == AnvilWork.REFORGE) hint(g, countIn ? "Listen: the anvil counts the rhythm in. Then strike each glowing mark on the beat to fold the new metal in."
                : "Strike the bright mark on the beat: click it, or press Space where you point. Esc steps away; the work waits.");
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

    /** Ticks between the Deepglass horn's hums while it's balanced (a hum lasts about three seconds). */
    private static final float HUM_EVERY = 50F;

    /** How many of the first {@code upTo} strikes rang clean, the server's where it has judged them, else as predicted. */
    private int cleanStrikes(AnvilWork w, int upTo) {
        int n = 0;
        for (int j = 0; j < upTo; j++) {
            float score = j < w.beats().size() ? w.beats().get(j) : j < predicted.length ? predicted[j] : 0F;
            if (AnvilSong.clean(score)) n++;
        }
        return n;
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
        var blowSound = !w.pick ? (tap ? AnvilContent.TAP.get() : AnvilContent.strike(w.tier)) : switch (w.stage) {
            case AnvilSong.EYE -> AnvilContent.EYE.get();
            case AnvilSong.TIPS -> AnvilContent.TIP.get();
            case AnvilSong.HANG -> AnvilContent.HANG.get();
            case AnvilSong.TEND -> AnvilContent.TEND.get();
            default -> tap ? AnvilContent.TAP.get() : AnvilContent.strike(w.tier);
        };
        if (score > 0F) AnvilClient.play(at, blowSound, tap ? 0.8F : 1F, (tap ? 1F : 0.95F) + (now % 10) * 0.01F);
        if (!tap && AnvilSong.clean(score)) AnvilClient.play(at, AnvilContent.RING.get(), 0.7F, AnvilSong.ringPitch(w.song, cleanStrikes(w, next + 1), need));
        if (!tap && next + 1 == placed.work.flipAt) AnvilClient.play(at, AnvilContent.FLIP.get(), 0.8F, 1F);
        if (seen) burst(p[0], p[1], score, now, tap ? 0.5F : 1F);
        next++;
        AnvilScene.struck = next;
        float timing = AnvilSong.timing(off);
        if (tap) {
            // attuning, the three taps drive the stone into the guard; at the hilt they fit the guard, grip and pommel
            if (timing >= 0.8F) say(w.stage == AnvilSong.HANG ? (next == 1 ? "The head goes on" : next == 2 ? "It's seating" : "It's hung true")
                    : w.stage == AnvilSong.TEND ? (next == need ? "That's done" : "Good") : w.kind == AnvilWork.ATTUNE ? (next == 1 ? "The stone bites" : next == 2 ? "It sinks in" : "The stone is set")
                    : next == 1 ? "The guard is on" : next == 2 ? "The grip is bound" : "The pommel is set", LANTERN, now);
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
        AnvilClient.play(at, AnvilContent.KNAP.get(), 0.8F, 0.95F + (now % 10) * 0.01F);
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
        circles(g, w, t, now, seen ? p[0] : width / 2F, seen ? p[1] : height / 2F, 28F, AnvilSong.TURNS);
        hint(g, winding ? "Keep circling at the same speed..."
                : "Hold the mouse button (or Space) and draw circles round the grip to wind the cord: four turns, as evenly as you can.");
    }

    /** Circles round a point, held: the bind's cord (four turns), the twist's bar (three); a pip a turn below. */
    private void circles(GuiGraphicsExtractor g, AnvilWork w, float t, float now, float cx, float cy, float r, int need) {
        if (winding) wind(w, t, now, cx, cy, need);
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
        int y = bottom() - 28, pw = 14, x0 = width / 2 - need * (pw + 3) / 2;
        for (int j = 0; j < need; j++) {
            int x = x0 + j * (pw + 3);
            if (j < windTurns) g.fill(x, y, x + pw, y + 4, LANTERN);
            else g.outline(x, y, pw, 4, 0x803A4A60);
        }
    }

    /** The hold begins (the cord, or the twist): told to the server as turn 0. */
    private void holdCord(AnvilWork w, float t) {
        if (winding || windTurns > 0) return;
        winding = true;
        windAcc = 0F;
        windLast = Float.NaN;
        windTurns = 0;
        turnAt[0] = t;
        AnvilScene.windTurns = 0;
        AnvilClient.send(pos, w.stage == AnvilSong.TWIST ? AnvilPayloads.TWIST : AnvilPayloads.WIND, 0, t, 0F);
    }

    private void releaseCord(AnvilWork w, float t, float now) {
        if (!winding) return;
        winding = false;
        AnvilScene.windTurns = windTurns;
        boolean twist = w.stage == AnvilSong.TWIST;
        if (windTurns >= (twist ? AnvilSong.TWISTS : AnvilSong.TURNS)) return;
        AnvilClient.send(pos, AnvilPayloads.RELEASE, windTurns, t, 0F);
        say(twist ? "Let go early: it's only part twisted" : "Let go early: the cord is loose", BAD, now);
    }

    /** The mouse's angle round the point, added up; each whole turn is told to the server with its time. */
    private void wind(AnvilWork w, float t, float now, float cx, float cy, int need) {
        boolean twist = w.stage == AnvilSong.TWIST;
        float dx = mx - cx, dy = my - cy;
        if (dx * dx + dy * dy >= 64F) {
            float a = (float) Math.atan2(dy, dx);
            if (!Float.isNaN(windLast)) windAcc += Mth.wrapDegrees((a - windLast) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
            windLast = a;
        }
        AnvilScene.windAngle = windAcc;
        int done = (int) (Math.abs(windAcc) / (Math.PI * 2));
        while (windTurns < Math.min(done, need)) {
            windTurns++;
            turnAt[windTurns] = t;
            AnvilScene.windTurns = windTurns;
            AnvilClient.send(pos, twist ? AnvilPayloads.TWIST : AnvilPayloads.WIND, windTurns, t, 0F);
            AnvilClient.play(at(placed.work.at), twist ? AnvilContent.TWIST.get() : AnvilContent.CORD.get(), twist ? 0.7F : 0.6F, (twist ? 0.9F : 1F) + windTurns * 0.06F);
            if (windTurns >= 2) {
                float mean = (turnAt[windTurns - 1] - turnAt[0]) / (windTurns - 1), last = turnAt[windTurns] - turnAt[windTurns - 1];
                if (Math.abs(last - mean) <= mean / 8F) say("Even", LANTERN, now);
                else say(last < mean ? "Too quick" : "Too slow", BAD, now);
            }
        }
        if (windTurns >= need) {
            winding = false;
            if (!twist) say("Bound tight", LANTERN, now);
            else if (turnAt[need] - turnAt[0] < AnvilSong.TWIST_MIN) say("Too fast: the bar hardly took the twist", BAD, now);
            else say("Twisted true", LANTERN, now);
        }
    }

    // ---- the ranks' acts (§46.9) ---------------------------------------------------------------------------------------

    /** A: the bar clamped hot on the face; hold and circle round its end, three slow, even turns. */
    private void twist(GuiGraphicsExtractor g, AnvilWork w, float t, float now) {
        AnvilShape sh = placed.work.shape;
        boolean seen = project(placed.work.point(AnvilScene.BLADE_X, sh.tip - 1.5F, 0F, v));
        circles(g, w, t, now, seen ? p[0] : width / 2F, seen ? p[1] : height / 2F, 34F, AnvilSong.TWISTS);
        hint(g, winding ? "Keep circling, slow and even..."
                : "Hold the mouse button (or Space) and circle round the end of the bar to twist it: three slow, even turns.");
    }

    /** The grind's channel: where it is on the screen (x, its middle's y, half its height). */
    private int grindX() {
        return width / 2 + Math.min(150, width / 4);
    }

    private int grindHalf() {
        return Math.min(60, height / 6);
    }

    /** C: hold to turn the wheel and keep the edge on the gold line as it drifts; twenty touches make the two passes. */
    private void grind(GuiGraphicsExtractor g, AnvilWork w, float t, float now) {
        int need = w.beatsNeeded(), gx = grindX(), cy = height / 2, half = grindHalf(), gw = 10;
        next = Math.max(next, w.beats().size());
        float line = cy + AnvilSong.grindLine(t) * half, off = (my - line) / half;
        AnvilScene.turning = holding && next < need;
        AnvilScene.grindEdge = AnvilScene.turning ? Math.min(1F, Math.abs(off)) : 0.5F;
        if (AnvilScene.turning && (Float.isNaN(grindLast) || t - grindLast >= AnvilSong.GRIND_GAP)) touch(w, t, now, off);
        g.fill(gx - 2, cy - half - 2, gx + gw + 2, cy + half + 2, 0xE0080B12);
        g.outline(gx - 2, cy - half - 2, gw + 4, 2 * half + 4, 0xFF2A3446);
        int ly = Math.round(line), ey = Mth.clamp(my, cy - half - 2, cy + half + 2);
        g.fill(gx - 6, ly - 1, gx + gw + 6, ly + 1, LANTERN);
        g.fill(gx - 9, ey, gx + gw + 9, ey + 1, Math.abs(off) <= 0.2F ? TITLE : BAD);
        g.text(font, "edge", gx + gw + 12, ey - 4, Math.abs(off) <= 0.2F ? LABEL : BAD, true);
        pips(g, w, need);
        if (w.stage == AnvilSong.SPINE) hint(g, AnvilScene.turning ? "Keep the thread on the gold line as it drifts: move the mouse up and down."
                : next == 0 ? "Hold the mouse button (or Space) to draw the thread of Deepglass along the spine. Keep it on the gold line by moving the mouse up and down."
                : "Hold the mouse button (or Space) to draw it on.");
        else hint(g, AnvilScene.turning ? "Keep the edge on the gold line as it drifts: move the mouse up and down."
                : next == 0 ? "Hold the mouse button (or Space) to turn the wheel. Keep the edge on the gold line as it drifts by moving the mouse up and down: two passes."
                : "Hold the mouse button (or Space) to turn the wheel again.");
    }

    /** One touch of the edge on the wheel: how far off the line, told to the server; sparks off it. */
    private void touch(AnvilWork w, float t, float now, float off) {
        grindLast = t;
        float score = AnvilSong.grind(Mth.clamp(off, -2F, 2F));
        AnvilClient.send(pos, AnvilPayloads.GRIND, next, t, off);
        predicted[next] = score;
        next++;
        AnvilShape sh = placed.work.shape;
        placed.work.point(AnvilScene.BLADE_X + AnvilScene.HALF_W, sh.mid, 0F, v);
        if (next % 2 == 0) AnvilClient.play(at(v), w.stage == AnvilSong.SPINE ? AnvilContent.THREAD.get() : AnvilContent.WHEEL.get(), 0.45F, 0.95F + (now % 5) * 0.02F);
        if (project(v)) burst(p[0], p[1], score, now, 0.35F);
        if (w.stage == AnvilSong.SPINE) {
            if (next == w.beatsNeeded()) say(score >= 0.8F ? "It takes the thread" : "It holds, unevenly", score >= 0.8F ? LANTERN : LABEL, now);
            return;
        }
        if (next == AnvilSong.PASS) say("One pass done", LABEL, now);
        else if (next == AnvilSong.GRIND_SAMPLES) {
            float sum = 0F;
            for (int j = 0; j < next; j++) sum += Float.isNaN(predicted[j]) ? 0F : predicted[j];
            say(sum / next >= 0.8F ? "A keen edge" : "Ground, but the edge is uneven", sum / next >= 0.8F ? LANTERN : LABEL, now);
        }
    }

    /** B: slide the Deepglass weight along the grip (the mouse) till the blade lies level on the horn, and hold it there. */
    private void balance(GuiGraphicsExtractor g, AnvilWork w, float t, float now) {
        AnvilShape sh = placed.work.shape;
        float point = AnvilSong.balancePoint(w.stageStart);
        if (!actSent) {
            if (AnvilClient.autoplay) AnvilScene.weight = point * AnvilScene.smooth(Mth.clamp((t - 12F) / 40F, 0F, 1F));
            else {
                // the mouse along the grip as it's seen, the guard to the pommel's end
                boolean a = project(placed.work.point(0F, sh.taps[0], 0F, v));
                float ax = p[0], ay = p[1];
                if (a && project(placed.work.point(0F, sh.taps[2], 0F, v))) {
                    float dx = p[0] - ax, dy = p[1] - ay, len = Math.max(1F, dx * dx + dy * dy);
                    AnvilScene.weight = Mth.clamp(((mx - ax) * dx + (my - ay) * dy) / len, 0F, 1F);
                } else AnvilScene.weight = Mth.clamp(mx / (float) Math.max(1, width), 0F, 1F);
            }
        }
        // it tips toward its heavy end, easing there
        float target = Mth.clamp((point - AnvilScene.weight) * 5F, -1F, 1F), dt = Float.isNaN(tiltAt) ? 0F : Math.max(0F, now - tiltAt);
        tiltAt = now;
        AnvilScene.tilt += (target - AnvilScene.tilt) * Math.min(1F, dt * 0.12F);
        boolean level = Math.abs(AnvilScene.weight - point) <= AnvilSong.LEVEL;
        if (!level) levelSince = Float.NaN;
        else if (Float.isNaN(levelSince)) levelSince = now;
        // the horn hums under it, truer the nearer it lies level
        if (!actSent && (Float.isNaN(humAt) || now - humAt >= HUM_EVERY)) {
            humAt = now;
            float flat = Math.min(1F, Math.abs(AnvilScene.weight - point) * 4F);
            AnvilClient.play(at(placed.work.at), AnvilContent.HUM.get(), 0.55F, AnvilSong.truePitch(w.song) * (1F - 0.06F * flat));
        }
        if (!actSent && (level && now - levelSince >= 20F || t >= AnvilSong.BALANCE_ALONE)) {
            actSent = true;
            float off = AnvilScene.weight - point, score = AnvilSong.balance(t, off);
            AnvilClient.send(pos, AnvilPayloads.BALANCE, 0, t, off);
            AnvilClient.play(at(placed.work.at), AnvilContent.RING.get(), 0.8F, AnvilSong.truePitch(w.song));
            say(score >= 0.8F ? "It lies level: well balanced" : "Balanced, at last", score >= 0.8F ? LANTERN : LABEL, now);
        }
        // a level's bubble over the horn
        boolean seen = project(placed.work.at);
        int bx = seen ? Math.round(p[0]) : width / 2, by = (seen ? Math.round(p[1]) : height / 2) - 46, half = 30;
        g.fill(bx - half - 2, by - 4, bx + half + 2, by + 4, 0xE0080B12);
        g.outline(bx - half - 2, by - 4, 2 * half + 4, 8, 0xFF2A3446);
        g.fill(bx - 1, by - 6, bx + 1, by + 6, 0xC0FFCF7A);
        disc(g, bx + AnvilScene.tilt * (half - 4), by, 3F, level ? LANTERN : 0xFFAFC2D8);
        hint(g, actSent ? "" : "Slide the Deepglass weight along the grip with the mouse until the blade lies level on the horn, and hold it there.");
    }

    /** S: the column of starlight comes down; a ring of it closes on the point. Click as it meets it. */
    private void star(GuiGraphicsExtractor g, AnvilWork w, float t, float now) {
        if (!actSent && t >= AnvilSong.STAR_ALONE) starlight(w, t, now);
        boolean seen = project(placed.work.point(AnvilScene.BLADE_X, placed.work.shape.tip, 0F, v));
        float cx = seen ? p[0] : width / 2F, cy = seen ? p[1] : height * 0.35F;
        if (!actSent) {
            float d = AnvilSong.STAR_AT - t, r = 9F + (d > 0F ? 64F * d / AnvilSong.STAR_AT : 2F * -d);
            int alpha = d > 0F ? 220 : Math.max(40, 220 + (int) (6F * d));
            ring(g, cx, cy, 9F, 1F, 0x90F4F6FB);
            ring(g, cx, cy, r, 2F, alpha << 24 | (Math.abs(d) <= 3F ? 0xFFFFFF : 0xDDE8FF));
        }
        hint(g, actSent ? "" : "The starlight closes on the point. Click, or press Space, the moment its ring meets the point.");
    }

    private void starlight(AnvilWork w, float t, float now) {
        if (actSent) return;
        actSent = true;
        float off = t - AnvilSong.STAR_AT, score = AnvilSong.star(off);
        AnvilClient.send(pos, AnvilPayloads.STAR, 0, t, 0F);
        AnvilClient.play(at(placed.work.at), AnvilContent.STAR.get(), 1F, 1F);
        if (project(placed.work.point(AnvilScene.BLADE_X, placed.work.shape.tip, 0F, v))) burst(p[0], p[1], score, now, 1.2F);
        say(score >= 1F ? "Right in the light" : off < 0F ? "Early: the light hadn't reached it" : "Late: the light had passed", score >= 1F ? LANTERN : BAD, now);
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
            say(w.stage == AnvilSong.SEAM ? "It cooled before you poured: plain, but whole" : "It cooled in the air: soft, but whole", LABEL, now);
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
        if (w.stage == AnvilSong.SEAM) hint(g, lifted ? "" : plunged ? "Keep pouring... tip the ladle back when the dark line reaches the gold notch."
                : "Hold the mouse button (or Space) to tip the ladle and pour the seam. Let go when the dark line reaches the gold notch.");
        else hint(g, lifted ? "" : plunged ? "Keep holding... let go when the dark line reaches the gold notch."
                : "Hold the mouse button (or Space) to plunge the blade into the water. Let go when the dark line reaches the gold notch.");
    }

    // ---- a pick's own acts (SPEC §49.4) --------------------------------------------------------------------------------

    private static final float[] OXIDE_AT = {0F, 0.16F, 0.34F, 0.52F, 0.74F, 1F};
    private static final int[] OXIDE = {0xF7ECBE, 0xE2BA68, 0xA86834, 0x803E76, 0x384CB0, 0x8894A8};

    private static int oxide(float c) {
        for (int i = 0; i < 5; i++) {
            if (c > OXIDE_AT[i + 1]) continue;
            return net.minecraft.util.ARGB.opaque(net.minecraft.util.ARGB.srgbLerp((c - OXIDE_AT[i]) / (OXIDE_AT[i + 1] - OXIDE_AT[i]), OXIDE[i], OXIDE[i + 1]));
        }
        return 0xFF000000 | OXIDE[5];
    }

    /** Run the colours: hold to warm the steel, watch them grow from the eye, let go when the point shows the one you want. */
    private void colours(GuiGraphicsExtractor g, AnvilWork w, float t, float now) {
        boolean started = !Float.isNaN(AnvilScene.plungeAt);
        float held = !started ? 0F : (lifted ? AnvilScene.liftAt : now) - AnvilScene.plungeAt;
        if (holding && held >= AnvilSong.COLOUR_FULL) endHold(w, t, now);
        int mh = Math.min(140, height / 2), mw = 10, gx = width / 2 + Math.min(150, width / 4), gy = height / 2 - mh / 2;
        g.fill(gx - 2, gy - 2, gx + mw + 2, gy + mh + 2, 0xE0080B12);
        g.outline(gx - 2, gy - 2, mw + 4, mh + 4, 0xFF2A3446);
        for (int y = 0; y < mh; y++) g.fill(gx, gy + mh - 1 - y, gx + mw, gy + mh - y, oxide(y / (float) (mh - 1)));
        for (float c : new float[]{0.05F, 0.5F, 0.75F}) {
            int y = gy + mh - Math.round(c * mh);
            g.fill(gx - 5, y, gx + mw + 5, y + 1, LABEL);
            g.text(font, dev.awaken.delve.forged.PickStats.colourName(c), gx + mw + 9, y - 4, LABEL, true);
        }
        float c = AnvilSong.colour(held);
        if (started) {
            int y = gy + mh - Math.round(c * mh);
            g.fill(gx - 9, y, gx + mw + 9, y + 1, TITLE);
        } else if (project(v.set(placed.work.at))) {
            float k = 0.5F + 0.5F * (float) Math.sin(now * 0.25);
            ring(g, p[0], p[1], 22F, 1.5F, (int) (60 + 120 * k) << 24 | 0xFFCF7A);
        }
        hint(g, lifted ? "" : started ? "Let go when the point shows the colour you want. Straw digs fastest and wears soonest; blue digs slower and lasts longest."
                : "Hold the mouse button (or Space) to warm the steel. The colours run out from the eye. Let go when the point shows the one you want.");
    }

    /** What a held press is making, so letting go, or leaving, stops it. */
    private net.minecraft.client.resources.sounds.@Nullable SoundInstance running;

    private void stopRunning() {
        if (running != null) minecraft.getSoundManager().stop(running);
        running = null;
    }

    @Override
    public void removed() {
        stopRunning();
        super.removed();
    }

    private void startHold(AnvilWork w, float t, float now) {
        if (holding || lifted || !Float.isNaN(AnvilScene.plungeAt) || t > AnvilSong.COLOUR_LATE) return;
        holding = true;
        AnvilScene.plungeAt = now;
        AnvilClient.send(pos, AnvilPayloads.PLUNGE, 0, t, 0F);
        running = AnvilClient.held(at(placed.work.at), AnvilContent.COLOURS.get(), 0.9F, 1F);
    }

    private void endHold(AnvilWork w, float t, float now) {
        if (!holding) return;
        holding = false;
        lifted = true;
        AnvilScene.liftAt = now;
        AnvilClient.send(pos, AnvilPayloads.LIFT, 0, t, 0F);
        stopRunning();
        AnvilClient.play(at(placed.work.at), AnvilContent.QUENCH.get(), 0.75F, 1.15F);
        float c = AnvilSong.colour(now - AnvilScene.plungeAt);
        say(dev.awaken.delve.forged.PickStats.colourName(c) + " it is", LANTERN, now);
    }

    /** The free way out of a pick's work, while nothing has been struck. */
    private boolean canBack(AnvilWork w) {
        return w.pick && !AnvilClient.autoplay && (w.stage == AnvilSong.HEAT || w.stage == AnvilSong.TEND && w.taps.isEmpty() || w.stage == AnvilSong.KNAP && w.scores.isEmpty());
    }

    private static final String BACK = "Back out";

    private int backX() {
        return width / 2 - bw(BACK) / 2;
    }

    private int backY() {
        return bottom() - 44;
    }

    private void backOut(GuiGraphicsExtractor g, AnvilWork w) {
        if (canBack(w)) button(g, backX(), backY(), BACK);
    }

    private boolean backClick(double x, double y) {
        WarAnvilBlockEntity be = AnvilClient.anvil(pos);
        AnvilWork w = be == null ? null : be.work();
        if (w == null || !canBack(w) || x < backX() || x >= backX() + bw(BACK) || y < backY() || y >= backY() + 18) return false;
        AnvilClient.send(pos, AnvilPayloads.CANCEL, 0, 0F, 0F);
        return true;
    }

    private void plunge(AnvilWork w, float t, float now) {
        if (holding || lifted || !Float.isNaN(AnvilScene.plungeAt) || t > AnvilSong.PLUNGE_LATE) return;
        holding = true;
        AnvilScene.plungeAt = now;
        AnvilClient.send(pos, AnvilPayloads.PLUNGE, 0, t, 0F);
        if (w.stage == AnvilSong.SEAM) running = AnvilClient.held(at(placed.work.at), AnvilContent.POUR.get(), 1F, 1F);
        else AnvilClient.play(at(placed.work.at), AnvilContent.QUENCH.get(), 1F, 1F);
    }

    private void lift(AnvilWork w, float t, float now) {
        if (!holding) return;
        holding = false;
        lifted = true;
        AnvilScene.liftAt = now;
        float held = now - AnvilScene.plungeAt;
        AnvilClient.send(pos, AnvilPayloads.LIFT, 0, t, 0F);
        stopRunning();
        if (w.stage != AnvilSong.SEAM) AnvilClient.play(at(placed.work.at), AnvilContent.LIFT.get(), 0.9F, 1F);
        if (w.stage == AnvilSong.SEAM) {
            if (AnvilSong.quench(held) >= 1F) say("It runs true, end to end", LANTERN, now);
            else say(AnvilSong.brittle(held) ? "A little ran over" : "It stops short of the points", BAD, now);
        } else if (AnvilSong.quench(held) >= 1F) say("Quenched true", LANTERN, now);
        else if (AnvilSong.brittle(held)) say("Held too long: it'll keep a brittle hairline", BAD, now);
        else say("Lifted early: the steel stays a little soft", BAD, now);
    }

    // ---- the draw and the card ---------------------------------------------------------------------------------------

    void done(AnvilPayloads.Done d) {
        if ((d.flags() & dev.awaken.arms.anvil.PickAnvil.TENDED) != 0) {
            // worked in, dressed or remade: it's already back in your hand and the toast has said so
            onClose();
            return;
        }
        float now = AnvilClient.now(0);
        done = d;
        doneAt = now;
        AnvilSong.Quality q = AnvilSong.Quality.of(d.quality());
        if (q.ordinal() >= AnvilSong.Quality.MASTERWORK.ordinal()) burst(width / 2F, height * 0.5F, 1F, now, 1.6F);
        boolean tempered = (d.flags() & WarAnvil.TEMPERED) != 0, attuned = (d.flags() & WarAnvil.ATTUNED) != 0;
        dev.awaken.hall.client.HallMoment.show(cardItem().getHoverName().getString(), tempered ? "Temper " + numeral(d.temper()) : attuned ? "Attuned" : q.label,
                tempered || attuned ? LANTERN : q.colour);
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
                AnvilClient.play(Vec3.atCenterOf(pos), AnvilContent.DRAWN.get(), 1F, WarAnvil.truePitch(be.slot()));
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
            footHint(g, "Hold the mouse button (or Space) to draw it from the stone.");
        else if (!Float.isNaN(drawnAt) || !standing)
            footHint(g, done != null && (done.flags() & dev.awaken.arms.anvil.PickAnvil.PICK) != 0 ? "It's yours. Esc closes this." : "Try its first cut, or you're done. Esc closes this.");
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
        var about = font.split(Component.literal(about(d, cardItem())), cw - 20);
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
        // tempered or attuned, the card says that rather than how well it was once forged
        boolean tempered = (d.flags() & WarAnvil.TEMPERED) != 0, attuned = (d.flags() & WarAnvil.ATTUNED) != 0;
        String big = tempered ? "Temper " + numeral(d.temper()) : attuned ? "Attuned" : q.label;
        g.text(font, big, 0, 0, tempered || attuned ? LANTERN : q.colour, true);
        pose.popMatrix();
        var player = Minecraft.getInstance().player;
        String who = player == null ? "you" : player.getGameProfile().name();
        if (!tempered && !attuned)
            g.text(font, ((d.flags() & WarAnvil.REFORGED) != 0 ? "Reforged by " : "Forged by ") + who, cx + 10, cy + 42 + dy, DIM, false);
        int ly = cy + 56 + dy;
        for (var line : about) {
            g.text(font, line, cx + 10, ly, LABEL, false);
            ly += 10;
        }
        // enchanting's lines (ForgeHooks), wrapped to the card like its own
        for (String line : dev.awaken.core.api.ForgeHooks.cardLines(it)) {
            for (var part : font.split(Component.literal(line), cw - 20)) {
                g.text(font, part, cx + 10, ly, 0xFFA898FF, false);
                ly += 10;
            }
        }
        if (drawn) {
            if ((d.flags() & dev.awaken.arms.anvil.PickAnvil.PICK) == 0) button(g, cx + 10, cy + ch - 24, "First cut");
            button(g, cx + cw - 10 - bw("Done"), cy + ch - 24, "Done");
        }
    }

    /**
     * The card stands to the left of the weapon in the stone, so the weapon stays in view. A long card (reforged and
     * flawed, on a 960-px window at GUI scale 4) widens toward the weapon, up to 240, rather than run off the screen.
     */
    private int cardW() {
        if (fitFor == done && fitShown == shown && fitAt == width * 4096 + height) return fitW;
        int w = Math.min(240, width / 2 - 66), most = Math.min(240, width - 32);
        while (w < most && done != null && cardH(w, true) > bottom() - 6) w = Math.min(most, w + 16);
        fitFor = done;
        fitShown = shown;
        fitAt = width * 4096 + height;
        return fitW = w;
    }

    private int cardX() {
        return Math.max(16, width / 2 - 50 - cardW());
    }

    private int cardH(boolean drawn) {
        return cardH(cardW(), drawn);
    }

    private int cardH(int w, boolean drawn) {
        return 62 + 10 * font.split(cardItem().getHoverName(), w - 42).size() + 10 * font.split(Component.literal(about(done, cardItem())), w - 20).size()
                + 10 * enchantLines(w) + (drawn ? 26 : 0);
    }

    private int enchantLines(int w) {
        int n = 0;
        for (String line : dev.awaken.core.api.ForgeHooks.cardLines(cardItem())) n += font.split(Component.literal(line), w - 20).size();
        return n;
    }

    private ItemStack cardItem() {
        return shown.isEmpty() ? new ItemStack(BuiltInRegistries.ITEM.getValue(Awaken.id(weapon))) : shown;
    }

    /** A little above the middle, but always on the screen. */
    private int cardY(int ch) {
        return Math.max(6, Math.min(height / 2 - ch / 2 - 10, bottom() - ch));
    }

    /** The hint at the foot, or in the room right of the card when the card reaches down over it. */
    private void footHint(GuiGraphicsExtractor g, String text) {
        int ch = done == null ? 0 : cardH(drawn());
        if (done == null || cardY(ch) + ch <= bottom() - 24) {
            hint(g, text);
            return;
        }
        int right = cardX() + cardW();
        var lines = font.split(Component.literal(text), Math.max(60, width - right - 24));
        int n = Math.min(3, lines.size()), y = bottom() - 10 * n;
        for (int i = 0; i < n; i++) g.centeredText(font, lines.get(i), (right + width) / 2, y + i * 10, DIM);
    }

    private boolean drawn() {
        return !Float.isNaN(drawnAt) || !Float.isNaN(emptyAt);
    }

    /** The card's words: how well, the Temper it leaves with (and whose limit held it), its flaw, and how it was made. */
    private static String about(AnvilPayloads.Done d, ItemStack it) {
        if ((d.flags() & dev.awaken.arms.anvil.PickAnvil.PICK) != 0) return aboutPick(d, it);
        if ((d.flags() & WarAnvil.TEMPERED) != 0)
            return "It leaves the anvil at Temper " + numeral(d.temper()) + ": each level adds a point to every blow.";
        if ((d.flags() & WarAnvil.ATTUNED) != 0) {
            String f = dev.awaken.arms.Marks.of(it).attunement().map(dev.awaken.arms.ArmsForge::forceName).orElse("its Force");
            return "The stone is set. Its Marks carry " + f + " now, and it works together with your " + f + " powers.";
        }
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
                + ((d.flags() & WarAnvil.RIPPLED) != 0 ? " The twist shows in its steel." : "")
                + ((d.flags() & WarAnvil.STARLIT) != 0 ? " It drank the starlight, and its runes woke." : "")
                + ((d.flags() & WarAnvil.TWIN) != 0 ? " A second hammer struck between your beats: it's twin-struck." : "")
                + ((d.flags() & WarAnvil.ARMOURY) != 0 ? " Forged in your Armoury, it will last a quarter longer." : "")
                + ((d.flags() & WarAnvil.REFORGED) != 0 ? " Reforged, it kept its Marks, its name and everything written on it." : "");
        return how + temper + flaw + made;
    }

    private static String aboutPick(AnvilPayloads.Done d, ItemStack it) {
        var data = dev.awaken.delve.forged.ForgedPicks.data(it);
        String how = switch (AnvilSong.Quality.of(d.quality())) {
            case PLAIN -> "It'll dig.";
            case FINE -> "Good, even work.";
            case MASTERWORK -> "A pick you'll be glad of.";
            case FLAWLESS -> "Every blow landed where it should.";
        };
        String colour = it.is(dev.awaken.delve.forged.ForgedPicks.get(dev.awaken.delve.forged.PickForm.DELVER, 0)) ? "" : switch (dev.awaken.delve.forged.PickStats.colourName(data.colour())) {
            case "Straw" -> " You stopped the colours at straw: it's quick in the rock, and it'll wear sooner.";
            case "Bronze" -> " You stopped the colours at bronze: quick, and it'll hold.";
            case "Peacock" -> " You stopped the colours at peacock, the even temper.";
            default -> " You let the colours run to blue: slower in the rock, and it'll last.";
        };
        String temper = d.temper() <= 0 ? "" : " It holds Temper " + numeral(d.temper()) + ", so it digs a little faster and lasts a little longer.";
        String made = ((d.flags() & WarAnvil.FROM_MEMORY) != 0 ? " You made it from memory." : "")
                + ((d.flags() & WarAnvil.TWIN) != 0 ? " A second hammer struck between your beats: it's twin-struck." : "")
                + ((d.flags() & WarAnvil.ARMOURY) != 0 ? " Forged in your Armoury, it will last a quarter longer." : "")
                + (d.flaw() == AnvilSong.BURNT_TIP ? " The points stayed in the fire too long and came out burnt." : "");
        return how + colour + temper + made;
    }

    private static String numeral(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> String.valueOf(n);
        };
    }

    private boolean cardClick(double x, double y) {
        if (done == null || !drawn()) return false;
        int cw = cardW(), cx = cardX(), ch = cardH(true), by = cardY(ch) + ch - 24;
        if (y < by || y >= by + 18) return false;
        if ((done.flags() & dev.awaken.arms.anvil.PickAnvil.PICK) == 0 && x >= cx + 10 && x < cx + 10 + bw("First cut")) {
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
                if (!pulled && w.heat(t) >= 0.65F) pull(w, t, now);
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
            case AnvilSong.COLOURS -> {
                if (Float.isNaN(AnvilScene.plungeAt) && t >= 20F) startHold(w, t, now);
                else if (holding && now - AnvilScene.plungeAt >= 36F) endHold(w, t, now);
            }
            case AnvilSong.SEAM -> {
                if (Float.isNaN(AnvilScene.plungeAt) && t >= 20F) plunge(w, t, now);
                else if (holding && now - AnvilScene.plungeAt >= AnvilSong.idealHold()) lift(w, t, now);
            }
            case AnvilSong.SPINE -> {
                if (!holding && t >= 12F) holding = true;
                my = Math.round(height / 2F + (AnvilSong.grindLine(t) + 0.04F * Mth.sin(t * 0.7F)) * grindHalf());
            }
            case AnvilSong.SHAPE, AnvilSong.HILT, AnvilSong.EYE, AnvilSong.TIPS, AnvilSong.HANG, AnvilSong.TEND -> {
                boolean tap = w.stage == AnvilSong.HILT || w.stage == AnvilSong.HANG || w.stage == AnvilSong.TEND;
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
            case AnvilSong.TWIST -> {
                // three turns, twelve ticks each: slow enough for the twist to take
                if (!winding && windTurns == 0 && t >= 12F) holdCord(w, t);
                if (winding && project(placed.work.point(AnvilScene.BLADE_X, placed.work.shape.tip - 1.5F, 0F, v))) {
                    double a = (t - turnAt[0]) / 12.0 * Math.PI * 2;
                    mx = (int) Math.round(p[0] + Math.cos(a) * 34);
                    my = (int) Math.round(p[1] + Math.sin(a) * 34);
                }
            }
            case AnvilSong.GRIND -> {
                // the wheel held turning, the edge kept close on the line
                if (!holding && t >= 12F) holding = true;
                my = Math.round(height / 2F + (AnvilSong.grindLine(t) + 0.04F * Mth.sin(t * 0.7F)) * grindHalf());
            }
            case AnvilSong.STAR -> {
                if (t >= AnvilSong.STAR_AT) starlight(w, t, now);
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
            case AnvilSong.BIND, AnvilSong.TWIST -> {
                if (press) holdCord(w, t);
                else releaseCord(w, t, now);
            }
            case AnvilSong.GRIND, AnvilSong.SPINE -> holding = press; // the wheel turns while it's held, and the thread runs
            case AnvilSong.EYE, AnvilSong.TIPS -> {
                if (press) strike(w, t, now, false);
            }
            case AnvilSong.HANG, AnvilSong.TEND -> {
                if (press) strike(w, t, now, true);
            }
            case AnvilSong.COLOURS -> {
                if (press) startHold(w, t, now);
                else endHold(w, t, now);
            }
            case AnvilSong.SEAM -> {
                if (press) plunge(w, t, now);
                else lift(w, t, now);
            }
            case AnvilSong.STAR -> {
                if (press) starlight(w, t, now);
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
        if (backClick(event.x(), event.y())) return true;
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
