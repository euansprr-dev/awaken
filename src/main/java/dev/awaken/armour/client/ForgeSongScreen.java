package dev.awaken.armour.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.armour.ArmourContent;
import dev.awaken.armour.ArmourSet;
import dev.awaken.armour.ForgeSong;
import dev.awaken.armour.ForgeWork;
import dev.awaken.armour.net.ArmourPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;

/**
 * The Forge Song (SPEC §44.4), played over the camera's shots. At the Forge: pull the bar when it glows in the gold;
 * strike each mark on the work on the set's beat (the anvil counts you in with one bar, then taps it softly), aim and
 * timing both counting; plunge it when the bell rings. By hand: draw the knife round the hide, then set the rivets on
 * the beat. A clean strike rings the set's next note, so a well-forged piece plays its tune. A helper at the anvil strikes
 * the off-beats. Your client plays your own sounds the moment you act; the server judges by its clock and keeps the work.
 * Esc steps away: the work waits on the stand. Doesn't pause the game.
 */
final class ForgeSongScreen extends Screen implements dev.awaken.signal.client.QuietScreen, dev.awaken.signal.client.ReadingScreen {
    private static final int LANTERN = ForgeScreen.LANTERN, TITLE = ForgeScreen.TITLE, LABEL = ForgeScreen.LABEL, DIM = ForgeScreen.DIM,
            GOOD = ForgeScreen.GOOD, BAD = ForgeScreen.BAD;
    /** Ticks before a strike's beat its ring starts closing on the mark. */
    private static final float APPROACH = 28F;

    /** The stand whose song this client has open (stepping away tells the server). */
    static @Nullable BlockPos last;

    final BlockPos pos;
    private final boolean helper;
    private float opened = Float.NaN, seenAt = Float.NaN;
    private int mx, my;

    // this run of a stage (a stage begun, or begun again after stepping away)
    private int runStage = -1;
    private long runStart = Long.MIN_VALUE;
    private int next, countStep, twinStep = Integer.MIN_VALUE;
    private final float[] predicted = new float[32];
    private float[][] marks = new float[0][];
    private boolean pulled, quenched, cue;
    private float bellowsAt;

    // the cut
    private static final int CUT_POINTS = 28;
    private static final float CUT_TOL = 12F;
    private final float[][] cutPath = new float[CUT_POINTS][3];
    private boolean cutting, cutSent;
    private int cutNext;
    private float cutDev, cutSamples, cutSoundAt;

    // what the screen shows for a moment
    private String callout = "";
    private int calloutColour;
    private float calloutAt = -100;
    private static final int SPARKS = 48;
    private final float[] sx = new float[SPARKS], sy = new float[SPARKS], svx = new float[SPARKS], svy = new float[SPARKS], sborn = new float[SPARKS];
    private final int[] scol = new int[SPARKS];
    private int sparkNext;
    private float ringX, ringY, ringAt = -100, ringR;

    private ArmourPayloads.@Nullable Done done;
    private float doneAt;
    private final float[] p = new float[3];

    ForgeSongScreen(BlockPos pos, boolean helper) {
        super(Component.literal("Forge Song"));
        this.pos = pos;
        this.helper = helper;
        if (!helper) last = pos;
        Arrays.fill(predicted, Float.NaN);
    }

    // ---- the frame ---------------------------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ForgeScreen.letterbox(g, width, height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        mx = mouseX;
        my = mouseY;
        float now = ForgeClient.now(a);
        if (Float.isNaN(opened)) opened = now;
        if (done != null) {
            drawDone(g, now);
            sparks(g, now);
            return;
        }
        var be = ForgeClient.stand(pos);
        ForgeWork w = be == null ? null : be.work();
        var player = Minecraft.getInstance().player;
        boolean mine = w != null && player != null && w.forger.equals(player.getUUID());
        if (w == null || (!helper && !mine) || (helper && w.stage != ForgeSong.SHAPE)) {
            // the work hasn't come (refused: the list comes back) or has gone (its result is on its way); a helper's part
            // ends with the shaping
            boolean gone = Float.isNaN(seenAt) ? now - opened > 60 : now - seenAt > 30;
            if (gone || helper && w != null) onClose();
            else if (Float.isNaN(seenAt)) g.centeredText(font, Component.literal("Setting the work..."), width / 2, height / 2 + 40, DIM);
            return;
        }
        seenAt = now;
        if (w.stage != runStage || w.stageStart != runStart) newRun(w, now);
        float t = now - w.stageStart;
        ForgeFrame f = ForgeFrame.of(pos, w.work);
        header(g, w);
        switch (w.stage) {
            case ForgeSong.HEAT -> heat(g, w, t, now);
            case ForgeSong.SHAPE, ForgeSong.RIVET -> {
                if (helper) twin(g, w, f, t);
                else shape(g, w, f, t, now);
            }
            case ForgeSong.QUENCH -> quench(g, w, f, t, now);
            case ForgeSong.CUT -> cut(g, w, f);
            default -> {}
        }
        if (ForgeClient.autoplay && !helper) autoplay(w, f, t, now);
        sparks(g, now);
        ring(g, now);
        callout(g, now);
    }

    private void newRun(ForgeWork w, float now) {
        boolean first = runStage < 0;
        runStage = w.stage;
        runStart = w.stageStart;
        Arrays.fill(predicted, Float.NaN);
        next = w.scores.size();
        int need = w.strikesNeeded();
        marks = ForgeSong.marks(w.piece, need);
        ForgeSong.Rhythm r = ForgeSong.song(w.set).rhythm();
        countStep = (int) Math.floor((now - w.stageStart) / r.step()) - 1;
        pulled = quenched = cue = cutting = cutSent = false;
        cutNext = 0;
        cutDev = cutSamples = 0;
        bellowsAt = now;
        float blend = switch (w.stage) {
            case ForgeSong.SHAPE -> 7;
            case ForgeSong.QUENCH -> 12;
            default -> first ? 20 : 14;
        };
        ForgeCamera.cut(ForgeShots.stage(w, pos, w.stage), now, blend);
    }

    private void header(GuiGraphicsExtractor g, ForgeWork w) {
        int top = Math.max(10, height / 14) + 6;
        ItemStack it = ForgeScreen.item(w.set, w.piece);
        String stage = switch (w.stage) {
            case ForgeSong.HEAT -> "Heat";
            case ForgeSong.SHAPE -> w.memory ? "From memory" : "Shape";
            case ForgeSong.QUENCH -> "Quench";
            case ForgeSong.CUT -> "Cut";
            default -> "Rivets";
        };
        g.centeredText(font, Component.literal(w.set.setName + "  ·  " + it.getHoverName().getString() + "  ·  " + stage), width / 2, top,
                0xFF000000 | w.set.rank.colour);
    }

    /** The bottom of what the screen draws, above the letterbox; the hint's two lines sit there, the pips and lane above. */
    private int bottom() {
        return height - Math.max(10, height / 14) - 4;
    }

    private void hint(GuiGraphicsExtractor g, String text) {
        if (text.isEmpty()) return;
        var lines = font.split(Component.literal(text), Math.min(420, width - 40));
        int n = Math.min(2, lines.size()), y = bottom() - 10 * n;
        for (int i = 0; i < n; i++) g.centeredText(font, lines.get(i), width / 2, y + i * 10, DIM);
    }

    // ---- heat ----------------------------------------------------------------------------------------------------------

    private void heat(GuiGraphicsExtractor g, ForgeWork w, float t, float now) {
        float h = ForgeSong.heat(t);
        if (now - bellowsAt > 46 && !pulled) {
            bellowsAt = now;
            w.forge.ifPresent(fp -> ForgeClient.play(Vec3.atCenterOf(fp), ArmourContent.BELLOWS.get(), 0.45F, 0.95F + (now % 7) * 0.01F));
        }
        int gx = width / 2 + Math.min(140, width / 4), gh = Math.min(150, height / 2), gy = height / 2 - gh / 2, gw = 12;
        g.fill(gx - 2, gy - 2, gx + gw + 2, gy + gh + 2, 0xE0080B12);
        g.outline(gx - 2, gy - 2, gw + 4, gh + 4, 0xFF2A3446);
        float scale = gh / 1.2F;
        int fillTop = gy + gh - (int) (Math.min(h, 1.2F) * scale);
        for (int y = gy + gh - 1; y >= fillTop; y--) g.fill(gx, y, gx + gw, y + 1, heatColour((gy + gh - y) / scale));
        int lo = gy + gh - (int) (ForgeSong.BAND_LO * scale), hi = gy + gh - (int) (ForgeSong.BAND_HI * scale);
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
        } else if (h < ForgeSong.BAND_LO) {
            say = "Let it heat...";
            colour = LABEL;
        } else if (h <= ForgeSong.BAND_HI) {
            say = "Pull it now";
            colour = (int) (now * 2) % 6 < 3 ? LANTERN : 0xFFFFE6B0;
        } else if (h <= ForgeSong.BURNT) {
            say = "Too hot, pull it!";
            colour = BAD;
        } else {
            say = "It's burning";
            colour = BAD;
        }
        g.text(font, say, gx + gw + 12, needle - 4, colour, true);
        hint(g, "Pull the bar from the fire while it glows in the gold: click, or press Space. Esc steps away; the work waits.");
    }

    /** The shader's ramp: dull red, orange, yellow, white. */
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

    private void pull(ForgeWork w, float t, float now) {
        float h = ForgeSong.heat(t);
        if (pulled || h > ForgeSong.BURNT) return;
        pulled = true;
        ForgeClient.send(pos, ArmourPayloads.PULL, 0, t, 0F);
        ForgeClient.play(w.forge.map(Vec3::atCenterOf).orElse(Vec3.atCenterOf(w.work)), ArmourContent.PULL.get(), 1F, 1F);
        if (h < ForgeSong.BAND_LO) say("Too soon: the metal will fight the hammer", BAD, now);
        else if (h <= ForgeSong.BAND_HI) say("In the gold", LANTERN, now);
        else say(ForgeSong.flawed(h) ? "Too hot: it'll keep a scale flaw" : "A little hot", BAD, now);
    }

    // ---- strikes -----------------------------------------------------------------------------------------------------

    private void shape(GuiGraphicsExtractor g, ForgeWork w, ForgeFrame f, float t, float now) {
        ForgeSong.Rhythm r = ForgeSong.song(w.set).rhythm();
        int need = w.strikesNeeded();
        next = Math.max(next, w.scores.size());
        // beats that passed unstruck
        while (next < need && t > r.due(next - w.strikeBase) + ForgeSong.WINDOW) {
            predicted[next++] = 0F;
            say("Missed the beat", BAD, now);
        }
        count(w, r, t, need);
        float markPx = 10;
        for (int j = 0; j < need; j++) {
            float[] m = marks[j % marks.length];
            Vec3 at = f.mark(m[0], m[1]);
            if (!ForgeCamera.project(at.x, at.y, at.z, width, height, p)) continue;
            float px = p[0], py = p[1], rad = Math.max(6F, ForgeSong.MARK * (float) (ForgeFrame.HALF_ACROSS + ForgeFrame.HALF_ALONG) * 0.5F * p[2]);
            if (j == next) markPx = rad;
            float score = j < w.scores.size() ? w.scores.get(j) : predicted[j];
            if (!Float.isNaN(score)) disc(g, px, py, rad * 0.55F, scoreColour(score) & 0x00FFFFFF | 0xC0000000);
            else if (j == next) {
                disc(g, px, py, rad, 0x50FFCF7A);
                ring(g, px, py, rad, 2F, LANTERN);
                disc(g, px, py, 2F, 0xFFFFF2C2);
                float dt = r.due(j - w.strikeBase) - t;
                if (dt < APPROACH && dt > -ForgeSong.WINDOW) {
                    float k = Mth.clamp(dt / APPROACH, 0F, 1F);
                    int alpha = (int) (255 * (1F - k) * 0.9F) + 25;
                    ring(g, px, py, rad * (1F + 2.4F * k), 1.5F, alpha << 24 | 0xFFE6B0);
                }
            } else if (j == next + 1) {
                disc(g, px, py, rad * 0.45F, 0x60FFCF7A);
                ring(g, px, py, rad * 0.8F, 1F, 0xB0FFE6B0);
            } else {
                disc(g, px, py, rad * 0.35F, 0x70FFCF7A);
                disc(g, px, py, 1.5F, 0xC0FFF2C2);
            }
        }
        lane(g, w, r, t, need, false);
        pips(g, w, need);
        boolean countIn = t < r.bar().length() * r.step();
        hint(g, countIn ? "Listen: the anvil counts the rhythm in. Then strike each glowing mark on the beat."
                : "Strike the bright mark on the beat: click it, or press Space where you point. Esc steps away; the work waits.");
    }

    /** The count-in bar, then a soft tap on each strike's beat. */
    private void count(ForgeWork w, ForgeSong.Rhythm r, float t, int need) {
        int step = (int) Math.floor(t / r.step());
        int len = r.bar().length();
        while (countStep < step) {
            countStep++;
            if (countStep < 0 || r.bar().charAt(countStep % len) != 'x') continue;
            Vec3 at = Vec3.atCenterOf(w.work).add(0, 0.6, 0);
            if (countStep < len) ForgeClient.play(at, ArmourContent.COUNT.get(), 0.9F, countStep == 0 ? 1.25F : 1F);
            else if (next < need) ForgeClient.play(at, ArmourContent.COUNT.get(), 0.22F, 1F);
        }
    }

    private void strike(ForgeWork w, ForgeFrame f, float t, float now) {
        ForgeSong.Rhythm r = ForgeSong.song(w.set).rhythm();
        int need = w.strikesNeeded();
        next = Math.max(next, w.scores.size());
        if (next >= need) return;
        float off = t - r.due(next - w.strikeBase);
        if (off < -ForgeSong.WINDOW) return; // nowhere near its beat yet
        float[] m = marks[next % marks.length];
        Vec3 at = f.mark(m[0], m[1]);
        float radii = 9F;
        if (ForgeCamera.project(at.x, at.y, at.z, width, height, p)) {
            float rad = Math.max(6F, ForgeSong.MARK * (float) (ForgeFrame.HALF_ACROSS + ForgeFrame.HALF_ALONG) * 0.5F * p[2]);
            radii = (float) Math.hypot(mx - p[0], my - p[1]) / rad;
        }
        float score = ForgeSong.strike(off, radii);
        ForgeClient.send(pos, ArmourPayloads.STRIKE, next, t, radii);
        predicted[next] = score;
        if (score > 0F) {
            SoundEvent ring = w.stage == ForgeSong.RIVET ? ArmourContent.STRIKE_COPPER.get() : ArmourContent.strikeSound(w.set);
            ForgeClient.play(at, ring, 1F, 0.95F + (now % 10) * 0.01F);
        }
        if (ForgeSong.clean(score)) {
            int[] melody = ForgeSong.song(w.set).melody();
            ForgeClient.play(at, ArmourContent.NOTE.get(), 0.7F, ForgeSong.notePitch(melody[next % melody.length]));
        }
        burst(p[0], p[1], score, now, 1F);
        next++;
        if (ForgeSong.clean(score)) say(ForgeSong.timing(off) >= 1F && ForgeSong.aim(radii) >= 1F ? "Perfect" : "Clean", LANTERN, now);
        else if (ForgeSong.timing(off) < 0.6F) say(off < 0 ? "Early" : "Late", BAD, now);
        else say("Wide of the mark", BAD, now);
    }

    /** The beat lane: what's coming slides to the line; the count-in shows as the bar to listen to. */
    private void lane(GuiGraphicsExtractor g, ForgeWork w, ForgeSong.Rhythm r, float t, int need, boolean offBeats) {
        int lw = Math.min(320, width - 80), lx = width / 2 - lw / 2, ly = bottom() - 42;
        float perTick = 2.4F;
        int hit = lx + 56;
        g.fill(lx, ly - 7, lx + lw, ly + 7, 0xB0080B12);
        g.fill(lx, ly, lx + lw, ly + 1, 0x403A4A60);
        g.fill(hit, ly - 9, hit + 1, ly + 9, 0xE0FFCF7A);
        int len = r.bar().length();
        // the count-in bar
        for (int i = 0; i < len; i++) {
            if (r.bar().charAt(i) != 'x') continue;
            float x = hit + (i * r.step() - t) * perTick;
            if (x < lx || x > lx + lw) continue;
            g.fill((int) x - 1, ly - 3, (int) x + 1, ly + 4, 0x90AFC2D8);
        }
        if (offBeats) {
            // a helper's beats: the rests between the smith's strikes
            int from = Math.max(len, (int) Math.floor((t - 20) / r.step())), to = (int) Math.ceil((t + lw / perTick) / r.step());
            for (int i = from; i <= to; i++) {
                if (r.bar().charAt(i % len) != '.') continue;
                float x = hit + (i * r.step() - t) * perTick;
                if (x < lx || x > lx + lw) continue;
                diamond(g, x, ly, 3, i == twinStep ? GOOD : LANTERN);
            }
            for (int i = from; i <= to; i++) {
                if (r.bar().charAt(i % len) != 'x') continue;
                float x = hit + (i * r.step() - t) * perTick;
                if (x >= lx && x <= lx + lw) g.fill((int) x, ly - 2, (int) x + 1, ly + 3, 0x80AFC2D8);
            }
            return;
        }
        for (int j = Math.max(w.strikeBase, next - 2); j < need; j++) {
            float x = hit + (r.due(j - w.strikeBase) - t) * perTick;
            if (x > lx + lw) break;
            if (x < lx) continue;
            float score = j < w.scores.size() ? w.scores.get(j) : predicted[j];
            diamond(g, x, ly, j == next ? 5 : 4, Float.isNaN(score) ? (j == next ? LANTERN : 0xFFAFC2D8) : scoreColour(score));
        }
    }

    /** One pip a strike, coloured by how it went. */
    private void pips(GuiGraphicsExtractor g, ForgeWork w, int need) {
        int y = bottom() - 28, pw = 8, x0 = width / 2 - need * (pw + 3) / 2;
        for (int j = 0; j < need; j++) {
            float score = j < w.scores.size() ? w.scores.get(j) : predicted[j];
            int x = x0 + j * (pw + 3);
            if (Float.isNaN(score)) g.outline(x, y, pw, 4, 0x803A4A60);
            else g.fill(x, y, x + pw, y + 4, scoreColour(score));
        }
    }

    private static int scoreColour(float s) {
        return s >= 0.8F ? 0xFFFFCF7A : s >= 0.45F ? 0xFF9FE6D6 : 0xFFFF8A7A;
    }

    // ---- a helper ------------------------------------------------------------------------------------------------------

    private void twin(GuiGraphicsExtractor g, ForgeWork w, ForgeFrame f, float t) {
        ForgeSong.Rhythm r = ForgeSong.song(w.set).rhythm();
        lane(g, w, r, t, w.strikesNeeded(), true);
        Vec3 at = f.top();
        if (ForgeCamera.project(at.x, at.y + ForgeFrame.FACE, at.z, width, height, p)) ring(g, p[0], p[1], 14F, 1F, 0x60AFC2D8);
        hint(g, "You're helping " + ForgeClient.name(w.forger) + ". Strike the off-beats, between their blows: click, or press Space. Esc leaves.");
    }

    private void twinStrike(ForgeWork w, ForgeFrame f, float t, float now) {
        ForgeSong.Rhythm r = ForgeSong.song(w.set).rhythm();
        int step = Math.round(t / r.step());
        if (step == twinStep) return;
        if (!r.offBeat(t) || r.offBy(t) > 1.5F) {
            say("Wait for the gap between blows", BAD, now);
            return;
        }
        twinStep = step;
        ForgeClient.send(pos, ArmourPayloads.TWIN, 0, t, 0F);
        Vec3 at = f.top().add(0, ForgeFrame.FACE, 0);
        ForgeClient.play(at, ArmourContent.strikeSound(w.set), 0.6F, 1.25F);
        if (ForgeCamera.project(at.x, at.y, at.z, width, height, p)) burst(p[0], p[1], 0.9F, now, 0.6F);
        say("Off-beat struck", GOOD, now);
    }

    // ---- quench --------------------------------------------------------------------------------------------------------

    private void quench(GuiGraphicsExtractor g, ForgeWork w, ForgeFrame f, float t, float now) {
        int cueAt = ForgeSong.quenchCue(w.stageStart);
        Vec3 at = f.top().add(0, ForgeFrame.FACE, 0);
        boolean seen = ForgeCamera.project(at.x, at.y, at.z, width, height, p);
        if (!cue && t >= cueAt) {
            cue = true;
            int[] melody = ForgeSong.song(w.set).melody();
            ForgeClient.play(at, ArmourContent.NOTE.get(), 1F, ForgeSong.notePitch(melody[0]));
            if (seen) {
                ringX = p[0];
                ringY = p[1];
                ringR = 90;
                ringAt = now;
            }
            if (!quenched) say("Now!", LANTERN, now);
        }
        if (seen && !quenched) {
            float k = t < cueAt ? 0.5F + 0.5F * (float) Math.sin(now * 0.25) : 1F;
            ring(g, p[0], p[1], 22F, 1.5F, (int) (60 + 120 * k) << 24 | 0xFFCF7A);
        }
        hint(g, quenched ? "" : t < cueAt ? "Hold it over the water, and plunge it when the bell rings: click, or press Space. Esc steps away."
                : "Plunge it! (click or Space)");
    }

    private void plunge(ForgeWork w, ForgeFrame f, float t, float now) {
        if (quenched) return;
        quenched = true;
        ForgeClient.send(pos, ArmourPayloads.QUENCH, 0, t, 0F);
        Vec3 at = f.top().add(0, ForgeFrame.FACE, 0);
        ForgeClient.play(at, ArmourContent.QUENCH.get(), 1F, 1F);
        float off = t - ForgeSong.quenchCue(w.stageStart);
        if (off < 0F) say("Too soon: it hissed", BAD, now);
        else if (off <= 4F) say("A perfect quench", LANTERN, now);
        else say("A little late", LABEL, now);
    }

    // ---- the cut (by hand) -----------------------------------------------------------------------------------------------

    private void cut(GuiGraphicsExtractor g, ForgeWork w, ForgeFrame f) {
        float rx, rz;
        switch (w.piece) {
            case HELMET -> {
                rx = 0.62F;
                rz = 0.62F;
            }
            case CHESTPLATE -> {
                rx = 0.72F;
                rz = 0.82F;
            }
            case LEGGINGS -> {
                rx = 0.55F;
                rz = 0.88F;
            }
            default -> {
                rx = 0.62F;
                rz = 0.6F;
            }
        }
        for (int i = 0; i < CUT_POINTS; i++) {
            double a = Math.PI * 2 * i / (CUT_POINTS - 1);
            Vec3 at = f.mark((float) (Math.sin(a) * rx), (float) (-Math.cos(a) * rz));
            cutPath[i][2] = ForgeCamera.project(at.x, at.y, at.z, width, height, p) ? 1 : 0;
            cutPath[i][0] = p[0];
            cutPath[i][1] = p[1];
        }
        for (int i = 0; i < CUT_POINTS; i++) {
            if (cutPath[i][2] == 0) continue;
            if (i < cutNext && i > 0) line(g, cutPath[i - 1][0], cutPath[i - 1][1], cutPath[i][0], cutPath[i][1], LANTERN);
            else if (i % 2 == 0) disc(g, cutPath[i][0], cutPath[i][1], 1.5F, 0xB0AFC2D8);
        }
        if (cutNext < CUT_POINTS && !cutSent) {
            float[] n = cutPath[cutNext];
            ring(g, n[0], n[1], cutNext == 0 ? 7F : 4F, 1.5F, LANTERN);
        }
        if (cutting) disc(g, mx, my, 2.5F, 0xFFFFF2C2);
        hint(g, cutSent ? "" : cutNext == 0 ? "Press on the bright point and draw the knife round the line, then let go.   Esc: step away"
                : "Keep to the line...");
    }

    private void cutMove(ForgeWork w, float now) {
        if (!cutting || cutSent || cutNext <= 0 || cutNext >= CUT_POINTS) return;
        float[] a = cutPath[cutNext - 1], b = cutPath[cutNext];
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float len2 = Math.max(1F, dx * dx + dy * dy);
        float u = Mth.clamp(((mx - a[0]) * dx + (my - a[1]) * dy) / len2, 0F, 1F);
        float d = (float) Math.hypot(mx - (a[0] + u * dx), my - (a[1] + u * dy));
        cutDev += Math.min(1F, d / (CUT_TOL * 1.5F));
        cutSamples++;
        if (Math.hypot(mx - b[0], my - b[1]) < CUT_TOL || u >= 1F && d < CUT_TOL * 1.5F) {
            cutNext++;
            if (now - cutSoundAt > 5) {
                cutSoundAt = now;
                ForgeClient.play(Vec3.atCenterOf(w.work).add(0, 0.6, 0), ArmourContent.CUT.get(), 0.5F, 0.9F + (cutNext % 5) * 0.05F);
            }
            if (cutNext >= CUT_POINTS) cutDone(w, now);
        }
    }

    private void cutDone(ForgeWork w, float now) {
        if (cutSent || cutNext <= 1) return;
        cutSent = true;
        cutting = false;
        float keep = cutSamples == 0 ? 1F : 1F - cutDev / cutSamples;
        float score = Mth.clamp(keep * 1.15F, 0F, 1F) * Math.min(1F, cutNext / (float) CUT_POINTS);
        ForgeClient.send(pos, ArmourPayloads.CUT, 0, 0F, score);
        say(score >= 0.85F ? "A clean cut" : score >= 0.6F ? "A fair cut" : "A ragged cut", score >= 0.6F ? LANTERN : BAD, now);
    }

    /** The song played for you, on the beat and on the mark (tours and demos). */
    private void autoplay(ForgeWork w, ForgeFrame f, float t, float now) {
        switch (w.stage) {
            case ForgeSong.HEAT -> {
                if (!pulled && ForgeSong.heat(t) >= 0.65F) pull(w, t, now);
            }
            case ForgeSong.SHAPE, ForgeSong.RIVET -> {
                next = Math.max(next, w.scores.size());
                if (next >= w.strikesNeeded() || t < ForgeSong.song(w.set).rhythm().due(next - w.strikeBase)) return;
                float[] m = marks[next % marks.length];
                Vec3 at = f.mark(m[0], m[1]);
                if (ForgeCamera.project(at.x, at.y, at.z, width, height, p)) {
                    mx = Math.round(p[0]);
                    my = Math.round(p[1]);
                }
                strike(w, f, t, now);
            }
            case ForgeSong.QUENCH -> {
                if (!quenched && t >= ForgeSong.quenchCue(w.stageStart) + 1) plunge(w, f, t, now);
            }
            case ForgeSong.CUT -> {
                if (cutSent || t < 20) return;
                int at = Math.min(cutNext, CUT_POINTS - 1);
                mx = Math.round(cutPath[at][0]);
                my = Math.round(cutPath[at][1]);
                if (cutNext == 0) act(true);
                else cutMove(w, now);
            }
            default -> {}
        }
    }

    // ---- the finished piece ------------------------------------------------------------------------------------------

    void done(ArmourPayloads.Done d) {
        float now = ForgeClient.now(0);
        done = d;
        doneAt = now;
        last = null;
        ForgeCamera.cut(ForgeShots.fit(pos), now, 22);
        ForgeSong.Quality q = ForgeSong.Quality.of(d.quality());
        if (q.ordinal() >= ForgeSong.Quality.MASTERWORK.ordinal()) burst(width / 2F, height * 0.62F, 1F, now, 1.6F);
    }

    private void drawDone(GuiGraphicsExtractor g, float now) {
        ArmourPayloads.Done d = done;
        ArmourSet set = ArmourSet.values()[Math.min(d.piece() / 4, ArmourSet.values().length - 1)];
        ArmorType piece = ForgeSong.PIECES[d.piece() % 4];
        ForgeSong.Quality q = ForgeSong.Quality.of(d.quality());
        float k = Mth.clamp((now - doneAt) / 14F, 0F, 1F);
        int cw = Math.min(300, width - 40), cx = width / 2 - cw / 2;
        var about = font.split(Component.literal(doneAbout(q)), cw - 20);
        var woke = d.woke() ? font.split(Component.literal(set.setName + " stands whole on the stand. Wear all four and it wakes on you too."), cw - 20)
                : java.util.List.<net.minecraft.util.FormattedCharSequence>of();
        int ch = doneHeight(d, cw);
        int cy = height - Math.max(10, height / 14) - ch - 10 + (int) ((1F - k) * 20);
        int alpha = (int) (k * 0xE8);
        g.fill(cx, cy, cx + cw, cy + ch, alpha << 24 | 0x0F1724);
        g.outline(cx, cy, cw, ch, (int) (k * 255) << 24 | q.colour & 0xFFFFFF);
        if (k < 0.3F) return;
        ItemStack it = ForgeScreen.item(set, piece);
        g.item(it, cx + 10, cy + 10);
        g.text(font, it.getHoverName(), cx + 32, cy + 10, TITLE, false);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx + 32, cy + 22);
        pose.scale(1.5F, 1.5F);
        g.text(font, q.label, 0, 0, q.colour, true);
        pose.popMatrix();
        int ly = cy + 42;
        for (var line : about) {
            g.text(font, line, cx + 10, ly, LABEL, false);
            ly += 10;
        }
        for (var line : woke) {
            g.text(font, line, cx + 10, ly + 2, 0xFF000000 | set.rank.colour, false);
            ly += 10;
        }
        button(g, cx + 10, cy + ch - 24, "Forge another");
        button(g, cx + cw - 10 - bw("Done"), cy + ch - 24, "Done");
    }

    private static String doneAbout(ForgeSong.Quality q) {
        return switch (q) {
            case PLAIN -> "Plain work. It'll serve.";
            case FINE -> "Fine work. It leaves the forge tempered once.";
            case MASTERWORK -> "Masterwork. Tempered twice, and you can forge it from memory now.";
            case FLAWLESS -> "Flawless. Tempered three times, and known by heart.";
        };
    }

    /** The finished card's height: its lines wrapped to its width, the buttons under them. */
    private int doneHeight(ArmourPayloads.Done d, int cw) {
        ArmourSet set = ArmourSet.values()[Math.min(d.piece() / 4, ArmourSet.values().length - 1)];
        int lines = font.split(Component.literal(doneAbout(ForgeSong.Quality.of(d.quality()))), cw - 20).size();
        if (d.woke()) lines += font.split(Component.literal(set.setName + " stands whole on the stand. Wear all four and it wakes on you too."), cw - 20).size();
        return 74 + 10 * lines;
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

    private boolean doneClick(double x, double y) {
        int cw = Math.min(300, width - 40), ch = doneHeight(done, cw), cx = width / 2 - cw / 2;
        int cy = height - Math.max(10, height / 14) - ch - 10;
        int by = cy + ch - 24;
        if (y < by || y >= by + 18) return false;
        if (x >= cx + 10 && x < cx + 10 + bw("Forge another")) {
            ForgeClient.send(pos, ArmourPayloads.OPEN, 0, 0F, 0F);
            return true;
        }
        if (x >= cx + cw - 10 - bw("Done") && x < cx + cw - 10) {
            onClose();
            return true;
        }
        return false;
    }

    // ---- input -------------------------------------------------------------------------------------------------------

    /** A click or Space: whatever the stage asks for, where the cursor is. */
    private boolean act(boolean press) {
        var be = ForgeClient.stand(pos);
        ForgeWork w = be == null ? null : be.work();
        if (w == null || done != null || w.stage != runStage) return false;
        float now = ForgeClient.now(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
        float t = now - w.stageStart;
        ForgeFrame f = ForgeFrame.of(pos, w.work);
        switch (w.stage) {
            case ForgeSong.HEAT -> pull(w, t, now);
            case ForgeSong.SHAPE, ForgeSong.RIVET -> {
                if (helper) twinStrike(w, f, t, now);
                else strike(w, f, t, now);
            }
            case ForgeSong.QUENCH -> plunge(w, f, t, now);
            case ForgeSong.CUT -> {
                if (!press || cutSent) return false;
                if (cutNext == 0 && Math.hypot(mx - cutPath[0][0], my - cutPath[0][1]) < CUT_TOL * 1.5F) {
                    cutting = true;
                    cutNext = 1;
                    ForgeClient.play(Vec3.atCenterOf(w.work).add(0, 0.6, 0), ArmourContent.CUT.get(), 0.7F, 1F);
                } else if (cutNext > 0) cutting = true;
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
        if (done != null) return doneClick(event.x(), event.y()) || super.mouseClicked(event, doubleClick);
        return act(true) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        mx = (int) event.x();
        my = (int) event.y();
        var be = ForgeClient.stand(pos);
        ForgeWork w = be == null ? null : be.work();
        if (w != null && w.stage == ForgeSong.CUT) cutMove(w, ForgeClient.now(0));
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        var be = ForgeClient.stand(pos);
        ForgeWork w = be == null ? null : be.work();
        if (w != null && w.stage == ForgeSong.CUT && cutting) cutDone(w, ForgeClient.now(0));
        cutting = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_SPACE && done == null) return act(true);
        return super.keyPressed(event);
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

    /** Sparks off a strike: bright and many for a clean blow, a few dull ones for a poor one. */
    private void burst(float x, float y, float score, float now, float size) {
        int n = (int) ((ForgeSong.clean(score) ? 14 : score > 0F ? 6 : 2) * size);
        int colour = ForgeSong.clean(score) ? 0xFFFFE6A0 : 0xFFFF9050;
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
        ringR = ForgeSong.clean(score) ? 30 * size : 14 * size;
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

    /** A ring as a few dozen short turned bars round it (not a fill a pixel row). */
    private static void ring(GuiGraphicsExtractor g, float cx, float cy, float r, float th, int colour) {
        int n = Mth.clamp((int) (r * 0.9F), 10, 40), t = Math.max(1, Math.round(th));
        float mid = r - th / 2F, len = (float) (Math.PI * 2 * mid / n) + 1F;
        var pose = g.pose();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
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

    /** A line two pixels thick as one turned bar. */
    private static void line(GuiGraphicsExtractor g, float x0, float y0, float x1, float y1, int colour) {
        float len = (float) Math.hypot(x1 - x0, y1 - y0);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.rotate((float) Math.atan2(y1 - y0, x1 - x0));
        g.fill(0, -1, Math.max(2, Math.round(len)), 1, colour);
        pose.popMatrix();
    }
}
