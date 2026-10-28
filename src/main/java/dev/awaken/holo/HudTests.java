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
            // glancing brings the hotbar, the powers and the level
            s.glancing = true;
            h.assertTrue(ClearRules.want(Piece.HOTBAR, s) == 1F && ClearRules.want(Piece.POWERS, s) == 1F
                    && ClearRules.want(Piece.LEVEL, s) == 1F, "the glance shows the rest");
            // comes fast, leaves slowly, never past its ends
            float a = ClearRules.ease(0F, 1F, ClearRules.IN_MS);
            h.assertTrue(a == 1F, "in within " + ClearRules.IN_MS + " ms");
            a = ClearRules.ease(1F, 0F, ClearRules.IN_MS);
            h.assertTrue(a > 0.7F, "out slowly, was " + a);
            h.assertTrue(ClearRules.ease(1F, 0F, 60_000F) == 0F && ClearRules.ease(0F, 1F, 60_000F) == 1F, "clamped");
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
