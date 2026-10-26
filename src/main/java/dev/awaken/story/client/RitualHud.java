package dev.awaken.story.client;

import dev.awaken.Awaken;
import dev.awaken.lens.client.LensClient;
import dev.awaken.powers.client.fx.PowerFxConfig;
import dev.awaken.story.net.StoryPayloads;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The ritual on the screen: what a rule asks of you (in plain words, under the crosshair), the letterbox while the scene
 * holds the camera, a red edge when you're hit, the "[ERROR]" that tears through the white at the end, the white and
 * the black of the hand closing, and the fallback fade for the tear when Lens isn't drawing. Flashes follow the
 * power-fx {@code screenFlashes} setting (a fade replaces a flash when it's off).
 */
final class RitualHud {
    private RitualHud() {}

    private static final Identifier LAYER = Awaken.id("ritual_hud");

    private static int wordId = -1;
    private static long wordUntil, wordFrom;
    private static long errorFrom = -1, errorUntil;
    private static long deathFrom = -1;
    private static long tearFrom = -1;
    private static long hurtFrom = -100;
    private static int flashColour;
    private static float flash, flashDecay;
    private static float bars, barsO;
    private static long judgementTip = -1;
    /** What the System says in the black, a sentence a line, and when the black began. */
    private static String[] question = new String[0];
    private static long questionFrom = -1;

    static void register(RegisterGuiLayersEvent e) {
        e.registerAboveAll(LAYER, RitualHud::render);
        NeoForge.EVENT_BUS.addListener(RenderGuiLayerEvent.Pre.class, RitualHud::hideHud);
    }

    static void reset() {
        wordId = -1;
        question = new String[0];
        questionFrom = -1;
        errorFrom = deathFrom = tearFrom = judgementTip = -1;
        flash = 0;
        bars = barsO = 0;
    }

    static boolean flashesOn() {
        try {
            return !PowerFxConfig.SPEC.isLoaded() || PowerFxConfig.SCREEN_FLASHES.get();
        } catch (RuntimeException e) {
            return true;
        }
    }

    static void word(int id, int ticks) {
        wordId = id;
        wordFrom = RitualClient.ticks;
        wordUntil = RitualClient.ticks + ticks;
    }

    static void plate(int ticks) {}

    /** A new beat: the last rule's line goes. */
    static void newBeat() {
        wordId = -1;
        errorFrom = -1;
    }

    static void error(int ticks) {
        errorFrom = RitualClient.ticks;
        errorUntil = RitualClient.ticks + ticks;
    }

    static void death(int ticks) {
        deathFrom = RitualClient.ticks;
    }

    static void tear() {
        tearFrom = RitualClient.ticks;
        deathFrom = -1;
        question = new String[0];
        questionFrom = -1;
    }

    /** The void: the System's words, drawn large in the dark, typed out once the black has settled. */
    static void question(String text) {
        if (questionFrom >= 0 && question.length > 0) return; // a relog's second VOID keeps the words where they are
        question = text.isBlank() ? new String[0] : text.strip().split("(?<=[.!?])\\s+");
        questionFrom = RitualClient.ticks;
    }

    static void hurt() {
        hurtFrom = RitualClient.ticks;
    }

    static void flash(int rgb, float strength, int ticks) {
        if (!flashesOn()) return;
        flashColour = rgb & 0xFFFFFF;
        flash = Math.max(flash, strength);
        flashDecay = flash / Math.max(1, ticks);
    }

    static void tick() {
        flash = Math.max(0, flash - flashDecay);
        barsO = bars;
        bars += ((RitualClient.held() ? 1 : 0) - bars) * 0.15F;
        if (RitualClient.beat == RitualClient.JUDGEMENT && judgementTip < 0) judgementTip = RitualClient.ticks;
        if (RitualClient.beat != RitualClient.JUDGEMENT) judgementTip = -1;
    }

    /**
     * The scene is the whole screen. In the hall nothing else of AWAKEN's shows (no tracker, toasts, level line or
     * cards; vanilla's hearts stay while you must dodge); while the scene holds the camera the game's own HUD steps
     * aside too. In the black only the System's words and the question's card are left; while the tear opens, only the
     * tear and the world (subtitles always stay, for whoever needs them).
     */
    private static void hideHud(RenderGuiLayerEvent.Pre e) {
        Identifier id = e.getName();
        if (id.equals(LAYER)) return;
        String path = id.getPath();
        if (path.equals("subtitle_overlay")) return;
        int beat = RitualClient.beat;
        if (RitualClient.tearing()) {
            e.setCanceled(true);
        } else if (beat == StoryPayloads.RitualScene.VOID) {
            if (!(id.getNamespace().equals(Awaken.MODID) && path.equals("signal")) && !path.equals("chat")) e.setCanceled(true);
        } else if (bars > 0.5F || deathFrom >= 0) {
            if (!path.equals("chat")) e.setCanceled(true);
        } else if (RitualClient.inScene() && id.getNamespace().equals(Awaken.MODID)) {
            e.setCanceled(true);
        }
    }

    private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        long now = RitualClient.ticks;
        int w = g.guiWidth(), h = g.guiHeight();
        Font font = mc.font;
        // the letterbox
        float b = Mth.lerp(pt, barsO, bars);
        if (b > 0.01F) {
            int bh = (int) (h * 0.11F * b);
            g.fill(0, 0, w, bh, 0xFF000000);
            g.fill(0, h - bh, w, h, 0xFF000000);
        }
        // what the rule asks
        if (wordId >= 0 && now < wordUntil && !mc.gui.hud.isHidden()) {
            String line = switch (wordId) {
                case 0 -> "Hold " + mc.options.keyShift.getTranslatedKeyMessage().getString() + " to kneel.";
                case 1 -> "Stand on the plate that lights up.";
                default -> "";
            };
            if (!line.isEmpty()) {
                float a = Mth.clamp((now - wordFrom + pt - 20) / 10F, 0, 1) * Mth.clamp((wordUntil - now - pt) / 10F, 0, 1);
                int alpha = (int) (a * 230);
                if (alpha > 4) g.centeredText(font, Component.literal(line), w / 2, h / 2 + 22, (alpha << 24) | 0xF2DDB0);
            }
        }
        if (judgementTip >= 0 && now - judgementTip < 80 && !mc.gui.hud.isHidden()) {
            float a = Mth.clamp((now - judgementTip + pt) / 10F, 0, 1) * Mth.clamp((80 - (now - judgementTip) - pt) / 12F, 0, 1);
            int alpha = (int) (a * 230);
            if (alpha > 4) g.centeredText(font, Component.literal("Get out of the red!"), w / 2, h / 2 + 22, (alpha << 24) | 0xFFB08A);
        }
        // hit: a red edge
        float hurt = 1 - (now - hurtFrom + pt) / 10F;
        if (hurt > 0 && flashesOn()) {
            int a = (int) (hurt * 110);
            int edge = Math.max(8, w / 12);
            g.fillGradient(0, 0, w, edge, (a << 24) | 0x5A0000, 0x005A0000);
            g.fillGradient(0, h - edge, w, h, 0x005A0000, (a << 24) | 0x5A0000);
        }
        // a flash (the eyes opening, the gaze)
        if (flash > 0.005F) g.fill(0, 0, w, h, ((int) (flash * 255) << 24) | flashColour);
        // the end: the palm's white fills everything, the hand closes, black
        if (deathFrom >= 0) {
            float t = now - deathFrom + pt;
            if (RitualClient.beat == RitualClient.END || RitualClient.beat == StoryPayloads.RitualScene.NONE || t < 40) {
                float white = flashesOn() ? Mth.clamp(t / 5F, 0, 1) * Mth.clamp(1 - (t - 5) / 3F, 0, 1) : 0;
                if (white > 0) g.fill(0, 0, w, h, ((int) (white * 255) << 24) | 0xFFF8EC);
                float black = Mth.clamp((t - 5) / 2F, 0, 1);
                if (black > 0) g.fill(0, 0, w, h, ((int) (black * 255) << 24));
            } else if (RitualClient.beat == StoryPayloads.RitualScene.VOID) {
                // the black lifts to the dark of the question
                float black = 1 - Mth.clamp((t - 40) / 30F, 0, 0.7F);
                g.fill(0, 0, w, h, ((int) (black * 255) << 24));
            }
        }
        // the end's "[ERROR]": the System breaking through the white
        if (errorFrom >= 0 && now < errorUntil + 4) {
            float t = now - errorFrom + pt;
            boolean on = ((int) (t * 1.7F)) % 3 != 1;
            if (on) {
                g.pose().pushMatrix();
                float s = 3.0F + (float) Math.sin(t * 2.3) * 0.15F;
                g.pose().translate(w / 2F + (float) Math.sin(t * 17) * 3, h / 2F - 10 + (float) Math.cos(t * 13) * 2);
                g.pose().scale(s, s);
                String text = "[ERROR]";
                int tw = font.width(text);
                g.text(font, text, -tw / 2 - 1, -4, 0xC0FF3040, false);
                g.text(font, text, -tw / 2 + 1, -4, 0xC040E0FF, false);
                g.text(font, text, -tw / 2, -4, 0xFFFFFFFF, false);
                g.pose().popMatrix();
            }
        }
        // the void: what the System says in the black, a line a sentence, typed slowly once the black has settled
        if (RitualClient.beat == StoryPayloads.RitualScene.VOID && question.length > 0 && questionFrom >= 0) {
            float t = now - questionFrom + pt - 46;
            if (t > 0) {
                float scale = Math.max(1F, Math.min(2F, w / 320F));
                int total = 0;
                for (String q : question) total += q.length();
                int shown = (int) (t * 0.9F);
                int lineH = (int) (font.lineHeight * scale * 1.6F);
                int y0 = (int) (h * 0.36F) - lineH * question.length / 2;
                int used = 0;
                for (int k = 0; k < question.length; k++) {
                    String line = question[k];
                    int n = Math.max(0, Math.min(line.length(), shown - used - k * 8)); // a beat between sentences
                    used += line.length();
                    if (n <= 0) break;
                    String part = line.substring(0, n);
                    g.pose().pushMatrix();
                    g.pose().translate(w / 2F, y0 + k * lineH);
                    g.pose().scale(scale, scale);
                    int x = -font.width(line) / 2;
                    boolean last = k == question.length - 1;
                    g.text(font, part, x, 0, last ? 0xFFB9C4FF : 0xFFE6E2EE, false);
                    g.pose().popMatrix();
                }
                if (total == 0) questionFrom = -1;
            }
        }
        // the tear, when Lens isn't drawing it: the black lifts with a blue-white flash
        if (tearFrom >= 0) {
            float t = now - tearFrom + pt;
            if (t > 50) tearFrom = -1;
            else if (!LensClient.active()) {
                float black = 1 - Mth.clamp((t - 6) / 30F, 0, 1);
                if (black > 0) g.fill(0, 0, w, h, ((int) (black * 255) << 24));
                float fl = flashesOn() ? Mth.clamp(1 - Math.abs(t - 14) / 10F, 0, 1) * 0.5F : 0;
                if (fl > 0) g.fill(0, 0, w, h, ((int) (fl * 255) << 24) | 0x9ED2FF);
            } else if (t < 4) {
                g.fill(0, 0, w, h, 0xFF000000); // until the tear starts drawing
            }
        }
    }
}
