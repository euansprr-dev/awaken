package dev.awaken.signal.client;

import dev.awaken.core.api.Notices;
import dev.awaken.core.api.TipHooks;
import dev.awaken.core.api.Unlocks;
import dev.awaken.core.audio.AudioSounds;
import dev.awaken.core.client.ui.Kit;
import dev.awaken.core.client.ui.Sheet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Unlock title (SPEC §71.2, {@link Unlocks}): the discovery {@link Moment}'s bigger sibling, for the few things that
 * change what the player can do. The spaced label, a line of light opening from the centre, the name three times the
 * size of text, and one line of how to use it with the player's own key caps. Six and a half seconds, alone: while it
 * shows the director is held (every paced message), new toasts and info cards wait, tips are held and a tip already up
 * fades, and {@link Unlocks.Client#showing()} lets other HUD step aside. It never starts in a fight or over a screen or a
 * choice card: it waits, however long, and is never dropped or filed. Several play one after the other.
 */
final class UnlockMoment {
    private UnlockMoment() {}

    /** Ticks: the opening, the hold, the fade; and the rest between two titles. */
    static final int OPEN = 14, HOLD = 98, FADE = 18, REST = 14;
    /** The most titles that wait their turn. */
    private static final int MOST = 5;
    /** The name's size against text, stepping down for a long name on a narrow screen. */
    private static final float[] SCALES = {3F, 2.4F, 2F, 1.5F};
    private static final Pattern TOKEN = Pattern.compile("\\{(hold|key\\.[a-z0-9_.]+)}");

    /** A piece of the how-line: words, a key cap, or the Hold cap. */
    private record Seg(String text, boolean cap, boolean hold) {}

    private static final ArrayDeque<Unlocks.Unlock> QUEUE = new ArrayDeque<>();
    private static Unlocks.Unlock shown;
    private static List<Seg> how = List.of();
    private static int age, rest;

    static void install() {
        Unlocks.installClient(new Unlocks.Client() {
            @Override public void show(Unlocks.Unlock unlock) {
                offer(unlock);
            }

            @Override public boolean showing() {
                return shown != null;
            }

            @Override public boolean pending() {
                return shown != null || !QUEUE.isEmpty();
            }
        });
    }

    static void offer(Unlocks.Unlock u) {
        // the same one twice in a row is one unlock (a scene and its server both saying so)
        if (u == null || shown != null && shown.id().equals(u.id())) return;
        for (Unlocks.Unlock q : QUEUE) if (q.id().equals(u.id())) return;
        // play never gives more than three at once (a rank, its stratum, a rung); a command that hands out everything
        // would otherwise hold the screen and every message for minutes
        if (QUEUE.size() >= MOST) {
            dev.awaken.Awaken.LOG.info("[unlock] {} is not shown: {} titles are already waiting", u.id(), QUEUE.size());
            return;
        }
        QUEUE.addLast(u);
        dev.awaken.Awaken.LOG.info("[unlock] {} waits for its title ({} in line)", u.id(), QUEUE.size());
    }

    static boolean active() {
        // also through the short rest between two titles that follow each other, so nothing slips in between them (a
        // card came up under a rank's stratum, the titles tour); a title that is only waiting for a fight to end holds
        // nothing
        return shown != null || rest > 0 && !QUEUE.isEmpty();
    }

    static void clear() {
        QUEUE.clear();
        if (shown != null) hold(false);
        shown = null;
    }

    private static void hold(boolean on) {
        Notices.client().hold(UnlockMoment.class, on);
        TipHooks.client().hold(UnlockMoment.class, on);
    }

    static void tick(Minecraft mc) {
        if (shown != null) {
            if (++age > OPEN + HOLD + FADE) {
                shown = null;
                rest = REST;
                hold(false);
            }
            return;
        }
        if (rest > 0) rest--;
        if (QUEUE.isEmpty() || rest > 0 || mc.player == null) return;
        // never in a fight, never over a screen, a choice or a discovery already up: it waits, and is never lost
        if (mc.gui.screen() != null || SystemHud.inCombat() || SystemHud.choiceUp() || Moment.active() || mc.gui.hud.isHidden()) return;
        shown = QUEUE.pollFirst();
        how = parse(shown.how());
        age = 0;
        hold(true);
        Herald.took();
        // the System's own swell under the game's sound for its biggest advancement (no synthetic ding)
        mc.getSoundManager().play(SimpleSoundInstance.forUI(AudioSounds.UI_MOMENT.get(), 0.8F, 0.7F));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9F, 0.6F));
        dev.awaken.Awaken.LOG.info("[unlock] {} shows: {}", shown.id(), shown.name());
    }

    private static List<Seg> parse(String text) {
        List<Seg> out = new ArrayList<>(4);
        Matcher m = TOKEN.matcher(text);
        int at = 0;
        while (m.find()) {
            if (m.start() > at) out.add(new Seg(text.substring(at, m.start()), false, false));
            String t = m.group(1);
            if (t.equals("hold")) {
                out.add(new Seg("Hold", true, true));
            } else {
                KeyMapping k = KeyMapping.get(t);
                // a key with nothing bound says so, and the K menu's Controls page is where to set it
                out.add(new Seg(k == null ? t : k.isUnbound() ? "Not set" : k.getTranslatedKeyMessage().getString(), true, false));
            }
            at = m.end();
        }
        if (at < text.length()) out.add(new Seg(text.substring(at), false, false));
        return out;
    }

    private static int width(Font font, List<Seg> segs) {
        int w = 0;
        for (Seg s : segs) w += s.cap ? Math.max(11, font.width(s.text) + 6) + 3 : font.width(s.text);
        return w;
    }

    static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Unlocks.Unlock s = shown;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.player == null || mc.gui.hud.isHidden()) return;
        Font font = mc.font;
        int sw = g.guiWidth(), sh = g.guiHeight();
        float t = age + dt.getGameTimeDeltaPartialTick(false);
        boolean still = dev.awaken.core.api.FirstPerson.reducedMotion.getAsBoolean();
        float open = Math.min(1F, t / OPEN);
        float e = still ? 1F : 1F - (1F - open) * (1F - open) * (1F - open);
        float a = t <= OPEN + HOLD ? (still ? Math.min(1F, t / 4F) : e) : Math.max(0F, 1F - (t - OPEN - HOLD) / FADE);
        if (a <= 0.01F) return;
        int cx = sw / 2;
        // the name's size: as large as fits with a margin either side, never cut off
        float scale = SCALES[SCALES.length - 1];
        for (float sc : SCALES) {
            if (font.width(s.name()) * sc <= sw - 24) {
                scale = sc;
                break;
            }
        }
        int nameW = Math.round(font.width(s.name()) * scale), nameH = Math.round(9 * scale);
        int howW = width(font, how);
        // the upper middle, clear of the crosshair: label, light, name, how
        // (and under the panels of the top right corner: the body's list ends 33 px down, and on a 240-tall screen a long
        // name's band reached up into it, the titles tour)
        int top = Math.max(40, (int) (sh * 0.167F));
        int ly = top + 12, ny = ly + 7, hy = ny + nameH + 6;
        int half = Math.max(nameW, howW) / 2 + 18;
        // a soft dark band so the words read over a bright sky or a busy wall: ten thin steps, so its edge is a fade
        // (three steps read as stacked panels over the open sky, the titles tour)
        for (int i = 0; i < 10; i++) {
            int gx = 4 * (9 - i), gy = (9 - i) * 3 / 2;
            // wide to the sides and below (short of the crosshair); above, only to the foot of the corner's panels
            g.fill(cx - half - gx, top - 3 - Math.min(gy, 4), cx + half + gx, hy + 12 + Math.min(gy, 8), Sheet.argb(0x05080F, 0.058F * a));
        }
        // the label, spaced out, in the unlock's colour
        HudLook.centered(g, font, spaced(s.label().toUpperCase(java.util.Locale.ROOT)), cx, top, Glass.a(s.colour(), 0.95F * a), true);
        // a line of light opening from the centre, twice a discovery's
        int reach = Math.round(Math.min(sw / 2F - 12, Math.max(half, 120)) * e);
        g.fill(cx - reach, ly, cx + reach, ly + 1, Glass.a(s.colour(), 0.8F * a));
        g.fill(cx - reach / 3, ly, cx + reach / 3, ly + 1, Glass.a(0xFFFFFF, 0.75F * a));
        g.fill(cx - reach / 2, ly + 1, cx + reach / 2, ly + 2, Glass.a(s.colour(), 0.22F * a));
        // the name, landing a little too big and settling
        float land = still ? 1F : 1F + 0.14F * (1F - e);
        float rise = still ? 0F : (1F - e) * 5F;
        g.pose().pushMatrix();
        g.pose().translate(cx, ny + rise + nameH / 2F);
        g.pose().scale(scale * land, scale * land);
        HudLook.text(g, font, s.name(), -font.width(s.name()) / 2, -4, Glass.a(Glass.TITLE, a), true);
        g.pose().popMatrix();
        // how to use it: the player's own keys as caps, following the name in
        float la = Mth.clamp((t - OPEN * 0.7F) / OPEN, 0F, 1F) * a;
        if (la > 0.01F && !how.isEmpty()) {
            int x = cx - howW / 2;
            for (Seg seg : how) {
                if (seg.cap) {
                    x += Kit.cap(g, font, seg.text, x, hy, la, seg.hold) + 3;
                } else {
                    HudLook.text(g, font, seg.text, x, hy, Glass.a(Glass.TEXT, 0.96F * la), true);
                    x += font.width(seg.text);
                }
            }
        }
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            if (i > 0) b.append(' ');
            b.append(s.charAt(i));
        }
        return b.toString();
    }
}
