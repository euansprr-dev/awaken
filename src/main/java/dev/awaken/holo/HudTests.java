package dev.awaken.holo;

import dev.awaken.core.api.HudState;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.holo.ui.ClearRules;
import dev.awaken.holo.ui.ClearRules.Piece;

/**
 * GameTests for the clear screen's rules (SPEC §40.6, {@link ClearRules}): what shows exploring, in a fight, on a slot
 * change and while glancing; how pieces come and go; and Classic, where every layer shows as it always has.
 */
final class HudTests {
    private HudTests() {}

    static void register() {
        AwakenTests.register("hud_states", 20, h -> {
            ClearRules.State s = new ClearRules.State();
            // exploring: only the world
            for (Piece p : Piece.values()) h.assertTrue(ClearRules.want(p, s) == 0F, "exploring shows no " + p);
            // a fight: health (with Aether) and the powers, nothing else
            s.fighting = true;
            h.assertTrue(ClearRules.want(Piece.VITALS, s) == 1F && ClearRules.want(Piece.POWERS, s) == 1F, "a fight shows health and powers");
            h.assertTrue(ClearRules.want(Piece.HOTBAR, s) == 0F && ClearRules.want(Piece.LEVEL, s) == 0F
                    && ClearRules.want(Piece.VANILLA_STATUS, s) == 0F, "a fight shows nothing else");
            s.fighting = false;
            // hurt or low: health; hungry: food; fresh XP: the level chip; busy powers: the powers
            s.hurt = true;
            h.assertTrue(ClearRules.want(Piece.VITALS, s) == 1F, "hurt shows health");
            s.hurt = false;
            s.hungry = true;
            h.assertTrue(ClearRules.want(Piece.FOOD, s) == 1F && ClearRules.want(Piece.VITALS, s) == 0F, "hungry shows food alone");
            s.hungry = false;
            s.levelNews = true;
            h.assertTrue(ClearRules.want(Piece.LEVEL, s) == 1F, "fresh XP shows the level chip");
            s.levelNews = false;
            // a slot change pops the hotbar for 2 s
            ClearRules.Timer pop = new ClearRules.Timer(ClearRules.HOTBAR_POP);
            pop.tick(true);
            for (int i = 0; i < ClearRules.HOTBAR_POP - 1; i++) pop.tick(false);
            h.assertTrue(pop.on(), "still up just before 2 s");
            pop.tick(false);
            h.assertFalse(pop.on(), "down at 2 s");
            // a fight lasts 4 s after its last cause
            ClearRules.Timer fight = new ClearRules.Timer(ClearRules.FIGHT_TAIL);
            fight.tick(true);
            for (int i = 0; i < ClearRules.FIGHT_TAIL - 1; i++) fight.tick(false);
            h.assertTrue(fight.on() && ClearRules.FIGHT_TAIL == 80, "a fight holds 4 s");
            fight.tick(false);
            h.assertFalse(fight.on(), "and ends after");
            // glancing brings the powers and the level; the hotbar is on the panel, so the one at the foot steps aside
            s.glancing = true;
            s.hotbarInGlance = true;
            h.assertTrue(ClearRules.want(Piece.HOTBAR, s) == 0F && ClearRules.want(Piece.POWERS, s) == 1F
                    && ClearRules.want(Piece.LEVEL, s) == 1F, "the glance shows the rest, its own hotbar instead of the bar");
            s.hotbarPop = true;
            h.assertTrue(ClearRules.want(Piece.HOTBAR, s) == 0F, "a slot change on the panel doesn't bring the bar back");
            s.hotbarPop = false;
            s.hotbarInGlance = false;
            h.assertTrue(ClearRules.want(Piece.HOTBAR, s) == 1F, "a panel without the holo look keeps the bar, as before");
            // comes fast, leaves slowly, never past its ends
            float a = ClearRules.ease(0F, 1F, ClearRules.IN_MS);
            h.assertTrue(a == 1F, "in within " + ClearRules.IN_MS + " ms");
            a = ClearRules.ease(1F, 0F, ClearRules.IN_MS);
            h.assertTrue(a > 0.7F, "out slowly, was " + a);
            h.assertTrue(ClearRules.ease(1F, 0F, 60_000F) == 0F && ClearRules.ease(0F, 1F, 60_000F) == 1F, "clamped");
            h.succeed();
        });

        // the Tab glance (SPEC §40.7): the rows in order, only those with something to say; the arm and panel are up while
        // the key is held and only then (no tap keeps it up)
        AwakenTests.register("hud_glance_rows", 20, h -> {
            var out = new dev.awaken.holo.ui.GlanceRules.Row[dev.awaken.holo.ui.GlanceRules.MOST];
            // Euan 9 Nov, a third hand: everything the hand pass draws goes by this, not only vanilla's hands
            h.assertTrue(dev.awaken.holo.ui.GlanceRules.handsAway(false, 0.5F, 0F) == 0.5F && dev.awaken.holo.ui.GlanceRules.handsAway(false, 1.1F, 0F) == 1F,
                    "the hands sink as the arm rises and are gone at its top");
            h.assertTrue(dev.awaken.holo.ui.GlanceRules.handsAway(true, 1F, 0.3F) == 0.3F, "as it lowers they follow the re-equip, as vanilla's do");
            // (SPEC §57.9) the vitals two to a row; a task's name and step take a row a line, so nothing is cut; no "next" row
            var R = dev.awaken.holo.ui.GlanceRules.Row.class;
            int n = dev.awaken.holo.ui.GlanceRules.rows(4, 1, 1, 1, true, out);
            h.assertTrue(n == 7 && out[0] == dev.awaken.holo.ui.GlanceRules.Row.LEVEL && out[1] == dev.awaken.holo.ui.GlanceRules.Row.VITALS
                    && out[2] == dev.awaken.holo.ui.GlanceRules.Row.VITALS && out[3] == dev.awaken.holo.ui.GlanceRules.Row.TASK
                    && out[4] == dev.awaken.holo.ui.GlanceRules.Row.STEP && out[5] == dev.awaken.holo.ui.GlanceRules.Row.HEADING
                    && out[6] == dev.awaken.holo.ui.GlanceRules.Row.HOTBAR, "everything, in order, in seven rows (it was ten): " + R.getSimpleName());
            n = dev.awaken.holo.ui.GlanceRules.rows(3, 0, 2, 0, false, out);
            h.assertTrue(n == 3 && out[0] == dev.awaken.holo.ui.GlanceRules.Row.LEVEL && out[2] == dev.awaken.holo.ui.GlanceRules.Row.VITALS,
                    "no Aether yet, no task, nowhere to go, no holo look: the vitals alone, and no step without a task");
            n = dev.awaken.holo.ui.GlanceRules.rows(4, 2, 3, 2, true, out);
            h.assertTrue(n == 11, "a long name and a long step take the rows they need: " + n);
            n = dev.awaken.holo.ui.GlanceRules.rows(4, 40, 40, 40, true, out);
            h.assertTrue(n <= dev.awaken.holo.ui.GlanceRules.MOST && out[n - 1] == dev.awaken.holo.ui.GlanceRules.Row.HOTBAR, "and never past the panel's most");
            var key = new dev.awaken.holo.ui.GlanceRules.Key();
            key.tick(true);
            h.assertTrue(key.arm() && key.panel(), "held: arm and panel");
            key.tick(false);
            h.assertTrue(!key.arm() && !key.panel(), "a tap leaves nothing up");
            for (int i = 0; i < 40; i++) key.tick(true);
            h.assertTrue(key.panel(), "up for as long as it is held");
            key.tick(false);
            h.assertFalse(key.panel(), "and goes with the key");
            h.succeed();
        });

        AwakenTests.register("hud_classic_unchanged", 20, h -> {
            ClearRules.State s = new ClearRules.State();
            s.screen = HudState.Screen.CLASSIC;
            // every combination of what the director knows: Classic shows vanilla's and Awaken's layers in full, never the vitals lines
            for (int bits = 0; bits < 1 << 8; bits++) {
                s.fighting = (bits & 1) != 0;
                s.glancing = (bits & 2) != 0;
                s.hurt = (bits & 4) != 0;
                s.low = (bits & 8) != 0;
                s.hungry = (bits & 16) != 0;
                s.hotbarPop = (bits & 32) != 0;
                s.powersBusy = (bits & 64) != 0;
                s.levelNews = (bits & 128) != 0;
                for (Piece p : Piece.values()) {
                    float want = p == Piece.VITALS || p == Piece.FOOD ? 0F : 1F;
                    h.assertTrue(ClearRules.want(p, s) == want, "Classic " + p + " with " + bits);
                }
            }
            // and where no director is installed (a server) every layer is as it always was
            h.assertTrue(HudState.classic() && HudState.alpha(net.minecraft.resources.Identifier.withDefaultNamespace("hotbar")) == 1F
                    && !HudState.fighting(), "no director: Classic");
            h.succeed();
        });
    }
}
