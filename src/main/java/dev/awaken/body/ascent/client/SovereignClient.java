package dev.awaken.body.ascent.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.body.ascent.AscentContent;
import dev.awaken.body.ascent.AscentPayloads;
import dev.awaken.body.ascent.Sovereign;
import dev.awaken.body.client.feel.FeelDirector;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * The Sovereign form on the client (SPEC §17.19). The Ascend key (default U) asks the server for the form; once it
 * says yes the body lifts into flight that never tires, Lens turns gold, the camera pulls back wide (ActionRig's
 * SOVEREIGN pose), the wings stay open and a ring of light breathes round you. Ascend again high up: the body falls as
 * Judgement (the Leaper's dive, harder; its landing is the server's Judgement Fall). Others see the wings and the aura.
 */
public final class SovereignClient {
    private SovereignClient() {}

    public static final KeyMapping ASCEND = new KeyMapping("key.awaken.ascend", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD,
            InputConstants.KEY_U, dev.awaken.core.client.AwakenKeys.CATEGORY);
    /** A Judgement Fall needs this much air under you, and falls this fast. */
    static final double JUDGEMENT_HEIGHT = 8, JUDGEMENT_SPEED = 2.6;
    /** The form's lift: this fast up for its first ticks (about ten blocks). */
    static final double RISE = 0.42;
    static final int RISE_TICKS = 24;

    private static int until = -1;

    /** The form holds for this client's player. */
    public static boolean active() {
        return until >= 0 && AscentMoves.ticks < until;
    }

    /** Client tick: the key, the form's light, and its end. */
    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null) return;
        while (ASCEND.consumeClick()) press(p);
        if (until >= 0 && AscentMoves.ticks >= until) end();
        if (!active()) return;
        AscentMoves.aether = 1;
        // the form's first beat lifts the body clear of the ground (a hover alone bleeds its rise away)
        int since = AscentMoves.ticks - (until - Sovereign.FORM);
        if (since < RISE_TICKS && AscentMoves.state() == dev.awaken.body.ascent.AscentState.SOAR) {
            AscentMoves.K.vy = Math.max(AscentMoves.K.vy, RISE);
        }
        int age = until - AscentMoves.ticks;
        if (age % 20 == 0) AscentFx.ring(p.getX(), p.getY() + 1.0, p.getZ(), new Vec3(0, 1, 0), 0.55F);
        if (age % 40 == 0) WingLayer.flourish(p.getId(), 44);
    }

    /** The key: a Judgement Fall in the form high up, else ask for the form. */
    static void press(LocalPlayer p) {
        if (!AscentMoves.has(AscentPayloads.Tier.SOVEREIGN)) return;
        if (active()) {
            if (AscentMoves.judgementDive(p, JUDGEMENT_HEIGHT, JUDGEMENT_SPEED)) AscentSounds.local(p, AscentContent.BOOM.get(), 0.5F, 0.6F);
            return;
        }
        ClientPacketDistributor.sendToServer(new AscentPayloads.Event(AscentPayloads.EV_ASCEND, 0));
    }

    /** The server: the form begins (for {@code ticks}) for this client's player. */
    static void begin(LocalPlayer p, int ticks) {
        until = AscentMoves.ticks + ticks;
        dev.awaken.lens.client.LensClient.override("awaken:sovereign", 30);
        FeelDirector.remember();
        AscentSounds.local(p, AscentContent.REMEMBER.get(), 1F, 0.8F);
        AscentSounds.local(p, AscentContent.WINGS_OPEN.get(), 1F, 0.7F);
        WingLayer.flourish(p.getId(), 60);
        AscentFx.ring(p.getX(), p.getY() + 0.2, p.getZ(), new Vec3(0, 1, 0), 0.9F);
        AscentMoves.sovereignLift(p);
        AscentMoves.trace("the Sovereign form, " + ticks + " ticks");
    }

    static void end() {
        if (until < 0) return;
        until = -1;
        dev.awaken.lens.client.LensClient.clearOverride(40);
        AscentMoves.trace("the Sovereign form ends");
    }

    /** Someone else's form: their wings open and the aura round them. */
    static void seen(Entity e, boolean begun) {
        if (!begun) return;
        WingLayer.flourish(e.getId(), Sovereign.FORM);
        AscentFx.ring(e.getX(), e.getY() + 0.2, e.getZ(), new Vec3(0, 1, 0), 0.9F);
        AscentSounds.local(e, AscentContent.REMEMBER.get(), 0.8F, 0.8F);
    }

    /** A Judgement Fall's landing (anyone's): the ground answers. */
    static void judgement(Entity e, float force, boolean mine) {
        AscentSounds.local(e, AscentContent.BOOM.get(), 1F, 0.55F);
        for (int i = 0; i < 3; i++) AscentFx.ring(e.getX(), e.getY() + 0.15 + i * 0.35, e.getZ(), new Vec3(0, 1, 0), 0.7F + i * 0.35F);
        if (mine) FeelDirector.slam(Math.min(3F, 1.5F + force));
    }

    static void reset() {
        if (until >= 0) dev.awaken.lens.client.LensClient.clearOverride(1);
        until = -1;
    }
}
