package dev.awaken.body;

import dev.awaken.body.ascent.AscentState;
import dev.awaken.body.net.BodyPayloads;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/** Current climb state and its end paths; the renderer is checked headlessly by tools/tests/body-held-items.py. */
final class BodyStowTests {
    private BodyStowTests() {}

    static void check(GameTestHelper h) {
        var p = BodyTests.player(h, null);
        var other = BodyTests.player(h, null);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        p.setItemInHand(InteractionHand.MAIN_HAND, sword);
        BodyMotion m = BodyServer.motion(p);
        long now = BodyServer.now(p);
        BodyStow.sync(p);
        h.assertTrue(!p.hasData(BodyContent.STOWED), "idle players need no appearance attachment");

        m.hanging = true;
        BodyStow.sync(p);
        h.assertTrue(Boolean.TRUE.equals(p.getExistingDataOrNull(BodyContent.STOWED)), "the current hang is available to existing and new observers");
        h.assertTrue(BodyStow.climbing(m, AscentState.NONE, now + 20 * 60), "a still-held hang never expires with an animation");
        h.assertTrue(!Boolean.TRUE.equals(other.getExistingDataOrNull(BodyContent.STOWED)), "one player's climb never stows another player's hands");
        h.assertTrue(p.getMainHandItem() == sword && sword.getCount() == 1, "stowing never changes held equipment");
        BodyServer.onEnd(p, new BodyPayloads.End(Move.LEDGE_HANG.ordinal()));
        BodyStow.sync(p);
        h.assertTrue(!p.getData(BodyContent.STOWED), "dropping from a hold restores items");

        m.gripping = true;
        h.assertTrue(m.active(), "a pipe grip must keep server validation ticking, even with no other move");
        BodyStow.sync(p);
        h.assertTrue(p.getData(BodyContent.STOWED), "a pipe grip stows");
        BodyServer.onEnd(p, new BodyPayloads.End(Move.GRIP.ordinal()));
        BodyStow.sync(p);
        h.assertTrue(!p.getData(BodyContent.STOWED), "a pipe release restores items");

        m.runUntil = now + 40;
        m.runClimb = false;
        h.assertTrue(!BodyStow.climbing(m, AscentState.NONE, now), "a wall-run keeps its weapon");
        m.runClimb = true;
        BodyStow.sync(p);
        h.assertTrue(p.getData(BodyContent.STOWED), "the vertical wall climb stows");
        BodyServer.onEnd(p, new BodyPayloads.End(Move.WALL_CLIMB.ordinal()));
        BodyStow.sync(p);
        h.assertTrue(!p.getData(BodyContent.STOWED), "leaving the wall climb restores items");

        m.mantleUntil = now + BodyRules.MANTLE_TICKS;
        BodyStow.sync(p);
        h.assertTrue(p.getData(BodyContent.STOWED), "mantling stows");
        BodyServer.onEnd(p, new BodyPayloads.End(Move.MANTLE.ordinal()));
        BodyStow.sync(p);
        h.assertTrue(!p.getData(BodyContent.STOWED), "a completed or blocked mantle restores immediately");

        m.climbOutUntil = now + RunnerRules.CLIMB_OUT_TICKS;
        BodyStow.sync(p);
        h.assertTrue(p.getData(BodyContent.STOWED), "climbing out stays stowed after leaving the hang");
        h.assertTrue(!BodyStow.climbing(m, AscentState.NONE, m.climbOutUntil), "a missing climb-out end is bounded");
        BodyServer.onEnd(p, new BodyPayloads.End(Move.LEDGE_JUMP.ordinal()));
        BodyStow.sync(p);
        h.assertTrue(!p.getData(BodyContent.STOWED), "a finished or blocked climb-out restores immediately");

        for (int kind = VaultRules.VAULT; kind <= VaultRules.OVER; kind++) {
            m.vaultUntil = now + 20;
            m.vaultKind = kind;
            BodyStow.sync(p);
            h.assertTrue(p.getData(BodyContent.STOWED) == (kind == VaultRules.HIGH || kind == VaultRules.ONTO || kind == VaultRules.OVER),
                    "only climb-over and climb-onto crossings stow, kind " + kind);
            BodyServer.onEnd(p, new BodyPayloads.End(Move.VAULT.ordinal()));
            BodyStow.sync(p);
            h.assertTrue(!p.getData(BodyContent.STOWED), "every crossing restores when ended");
        }
        for (AscentState state : AscentState.values()) {
            h.assertTrue(BodyStow.climbing(m, state, now) == (state == AscentState.TETHER || state == AscentState.ZIP || state == AscentState.RAPPEL),
                    "only rope states occupy the hands: " + state);
        }
        m.hanging = true;
        p.setGameMode(GameType.SPECTATOR);
        BodyStow.sync(p);
        h.assertTrue(!p.getData(BodyContent.STOWED), "leaving the body stops stowing even before movement cleanup");
        p.setGameMode(GameType.SURVIVAL);
        BodyStow.sync(p);
        BodyStow.reset(p);
        BodyStow.sync(p);
        h.assertTrue(!p.hasData(BodyContent.MOTION) && !Boolean.TRUE.equals(p.getExistingDataOrNull(BodyContent.STOWED)),
                "a replacement or dimension change clears the source as well as the appearance");
        var speed = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        m = BodyServer.motion(p);
        m.tacUntil = now + 40;
        speed.addOrUpdateTransientModifier(BodyRules.TAC_MODIFIER);
        h.assertTrue(speed.hasModifier(BodyRules.TAC_ID), "active tactical sprint owns its speed modifier");
        BodyStow.reset(p);
        h.assertTrue(!speed.hasModifier(BodyRules.TAC_ID) && !p.hasData(BodyContent.MOTION),
                "dimension/reset ends tactical sprint before forgetting its cleanup state");
        m = BodyServer.motion(p);
        m.slideUntil = now + 30;
        m.stairSliding = true;
        p.setForcedPose(net.minecraft.world.entity.Pose.SWIMMING);
        BodyStow.reset(p);
        h.assertTrue(p.getForcedPose() == null && !m.stairSliding && !p.hasData(BodyContent.MOTION),
                "dimension/reset ends a slide and releases its forced crawl pose");
        h.succeed();
    }
}
