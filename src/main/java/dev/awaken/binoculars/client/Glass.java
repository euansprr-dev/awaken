package dev.awaken.binoculars.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.awaken.Awaken;
import dev.awaken.binoculars.BinocularsNet;
import dev.awaken.binoculars.BinocularsSounds;
import dev.awaken.binoculars.GlassFeel;
import dev.awaken.binoculars.GlassKey;
import dev.awaken.binoculars.GlassSpring;
import dev.awaken.binoculars.GlassState;
import dev.awaken.core.api.CameraHooks;
import dev.awaken.core.api.FirstPerson;
import dev.awaken.core.api.MovementHooks;
import dev.awaken.core.client.AwakenKeys;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Your binoculars (SPEC §39.2, §39.3): the key, the raise, the zoom's spring, what lowers them, the view switch from
 * third person, the slow walk, the sounds and telling the server. Advanced once a frame ({@link #frame}) in real time;
 * the drops that can't wait for a frame come straight from their events ({@link #drop}). Everything else reads it.
 */
public final class Glass {
    private Glass() {}

    public static final KeyMapping KEY = new KeyMapping("key.awaken.binoculars", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD,
            InputConstants.KEY_I, AwakenKeys.CATEGORY);

    static final GlassState STATE = new GlassState();
    private static final GlassKey KEYS = new GlassKey();
    private static final GlassSpring SPRING = new GlassSpring();

    /** The step's index in the feel's steps, and the magnification it asks for. */
    private static int step = -1;
    /** The first pull after the raise uses the slower settle until it arrives. */
    private static boolean pulling, atEyes, keyWas;
    private static long lastNanos;
    /** The focus pull: 1 just started, 0 clear; and how long it takes to clear (ms). */
    private static float focus, focusMs = 1;
    /** We switched the view to first person for them: the vanilla type to go back to, or the shoulder toggle. */
    private static CameraType restoreType;
    private static boolean restoreShoulder;
    private static Level level;
    private static int lastHurtTime;
    private static boolean sentUp;

    private static final net.minecraft.resources.Identifier WALK_ID = Awaken.id("binoculars_walk");

    // ---- what the others read ---------------------------------------------------------------------------------------

    /** The magnification now (1 when down). */
    public static double zoom() {
        return SPRING.mag();
    }

    /** The step they're set to. */
    static float stepMag() {
        GlassFeel f = GlassFeel.get();
        return f.steps()[stepIndex(f)];
    }

    /** At the eyes: zoom, lens edge, HUD rules on. */
    static boolean seeing() {
        return atEyes;
    }

    /** How far up (0..1) for the hands. */
    static float raise() {
        return STATE.progress();
    }

    static boolean active() {
        return STATE.active();
    }

    /** The lens edge's opacity: in from the eyecup to the end of the hand-off. */
    static float edge() {
        if (!atEyes) return 0;
        GlassFeel f = GlassFeel.get();
        float t = STATE.progress();
        return f.eyecupEnd() <= f.eyecup() ? 1 : Math.max(0, Math.min(1, (t - f.eyecup()) / (f.eyecupEnd() - f.eyecup())));
    }

    static float focus() {
        return focus;
    }

    static boolean reducedMotion() {
        return FirstPerson.reducedMotion.getAsBoolean();
    }

    private static int stepIndex(GlassFeel f) {
        if (step < 0 || step >= f.steps().length) {
            float last = GlassConfig.lastStep();
            step = f.stepIndex(last > 0 ? last : f.firstStep());
        }
        return step;
    }

    // ---- once a frame -----------------------------------------------------------------------------------------------

    static void frame() {
        long now = System.nanoTime();
        float ms = lastNanos == 0 ? 0 : Math.min(100F, (now - lastNanos) / 1_000_000F);
        lastNanos = now;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            if (STATE.active()) reset();
            return;
        }
        GlassFeel f = GlassFeel.get();
        long nowMs = now / 1_000_000L;
        key(mc, p, f, nowMs);
        checkDrops(mc, p);
        boolean calm = reducedMotion();
        float slow = calm ? 0.5F : 1F;
        float before = STATE.progress();
        STATE.advance(ms, f.raiseMs() * slow, f.lowerMs() * slow);
        boolean eyes = STATE.atEyes(f.eyecup());
        if (eyes && !atEyes) {
            atEyes = true;
            pulling = true;
            play(BinocularsSounds.EYECUP.get(), 1F, 0.5F);
            startFocus(f.focusRaiseMs());
        } else if (!eyes && atEyes) {
            atEyes = false;
            SPRING.snap(1);
            pulling = false;
            focus = 0;
        }
        if (atEyes) {
            double target = stepMag();
            double zeta = calm ? 1.0 : GlassSpring.zeta(f.overshoot());
            SPRING.step(ms, target, pulling ? f.pullMs() : f.settleMs(), zeta);
            if (pulling && SPRING.settled(target)) pulling = false;
        }
        if (focus > 0) focus = Math.max(0, focus - ms / Math.max(1F, focusMs));
        if (before > 0 && STATE.phase() == GlassState.Phase.DOWN) afterDown(mc);
        walk(p, STATE.active());
        send(mc);
    }

    private static void key(Minecraft mc, LocalPlayer p, GlassFeel f, long nowMs) {
        boolean down = KEY.isDown();
        boolean clicked = false;
        while (KEY.consumeClick()) clicked = true;
        GlassKey.Mode mode = GlassConfig.mode();
        GlassKey.Action a = GlassKey.Action.NONE;
        if (down && !keyWas) a = KEYS.press(nowMs, STATE.up(), canRaise(mc, p), mode);
        else if (!down && keyWas) a = KEYS.release(nowMs, STATE.up(), mode, f.tapMs());
        else if (!down && clicked) {
            // pressed and let go between two frames: a tap
            a = KEYS.press(nowMs, STATE.up(), canRaise(mc, p), mode);
            act(mc, a);
            a = KEYS.release(nowMs, STATE.up(), mode, f.tapMs());
        }
        keyWas = down;
        act(mc, a);
    }

    private static void act(Minecraft mc, GlassKey.Action a) {
        if (a == GlassKey.Action.RAISE) raise(mc);
        else if (a == GlassKey.Action.LOWER) drop(GlassState.Drop.KEY);
    }

    /** Nothing keeps the hands busy (SPEC §39.2, "When they can't come up"). */
    static boolean canRaise(Minecraft mc, LocalPlayer p) {
        return GlassConfig.enabled() && mc.gui.screen() == null && p.isAlive() && !p.isSpectator() && !p.isScoping() && !p.isUsingItem()
                && !casting() && !gripping(p);
    }

    private static boolean casting() {
        for (KeyMapping k : AwakenKeys.POWER_SLOTS) if (k.isDown()) return true;
        return AwakenKeys.ULTIMATE.isDown() || AwakenKeys.MOVEMENT.isDown();
    }

    private static boolean gripping(LocalPlayer p) {
        return MovementHooks.hanging(p) || MovementHooks.wallRunning(p) || FirstPerson.armsInWorld();
    }

    private static void raise(Minecraft mc) {
        if (STATE.up()) return;
        boolean fromDown = !STATE.active();
        STATE.raise();
        if (mc.player != null) mc.player.setSprinting(false);
        if (fromDown) {
            level = mc.level;
            if (!mc.options.getCameraType().isFirstPerson()) {
                // the look is through your own eyes; back to your view when they come down
                if (CameraHooks.toggleView()) restoreShoulder = true;
                else {
                    restoreType = mc.options.getCameraType();
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                }
            }
        }
        play(BinocularsSounds.RAISE.get(), 1F, 0.45F);
    }

    /** Down, for {@code why}: the instant ones in this very frame (zoom, look speed and HUD back at once). */
    static void drop(GlassState.Drop why) {
        if (!STATE.active()) return;
        boolean wasUp = STATE.up();
        KEYS.reset();
        STATE.lower(why);
        atEyes = false;
        pulling = false;
        focus = 0;
        SPRING.snap(1);
        Minecraft mc = Minecraft.getInstance();
        if (wasUp && why != GlassState.Drop.DEATH) play(BinocularsSounds.LOWER.get(), 1F, 0.4F);
        if (why == GlassState.Drop.PERSPECTIVE) {
            restoreType = null;      // you chose a view: keep it
            restoreShoulder = false;
        }
        if (!STATE.active()) afterDown(mc);
        if (mc.player != null) walk(mc.player, STATE.active());
        send(mc);
    }

    /** Fully down: the view you had before. */
    private static void afterDown(Minecraft mc) {
        if (restoreShoulder) {
            restoreShoulder = false;
            if (mc.options.getCameraType().isFirstPerson()) CameraHooks.toggleView();
        }
        if (restoreType != null) {
            if (mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(restoreType);
            restoreType = null;
        }
    }

    private static void checkDrops(Minecraft mc, LocalPlayer p) {
        if (!STATE.active()) {
            lastHurtTime = p.hurtTime;
            level = mc.level;
            return;
        }
        if (!GlassConfig.enabled()) drop(GlassState.Drop.OFF);
        else if (!p.isAlive()) drop(GlassState.Drop.DEATH);
        else if (mc.level != level) drop(GlassState.Drop.DIMENSION);
        else if (mc.gui.screen() != null) drop(GlassState.Drop.SCREEN);
        else if (p.hurtTime > lastHurtTime) drop(GlassState.Drop.HURT);
        else if (p.isScoping()) drop(GlassState.Drop.SPYGLASS);
        else if (p.isUsingItem()) drop(GlassState.Drop.USE);
        else if (casting()) drop(GlassState.Drop.CAST);
        else if (gripping(p)) drop(GlassState.Drop.GRIP);
        else if (p.isSprinting()) drop(GlassState.Drop.SPRINT);
        // (a moment's grace after the raise: Shoulder Surfing may switch the view a frame late)
        else if (!mc.options.getCameraType().isFirstPerson() && STATE.progress() > 0.3F) drop(GlassState.Drop.PERSPECTIVE);
        lastHurtTime = p.hurtTime;
    }

    /** One scroll event while up: one step (true: the hotbar never sees it). */
    static boolean scroll(int notches) {
        if (!atEyes && !STATE.up()) return false;
        if (notches == 0) return true;
        GlassFeel f = GlassFeel.get();
        int from = stepIndex(f), to = f.stepBy(from, notches);
        if (to == from) return true;
        step = to;
        GlassConfig.rememberStep(f.steps()[to]);
        play(BinocularsSounds.STEP.get(), 0.9F + 0.04F * to, 0.35F);
        if (atEyes && f.steps()[to] >= f.focusFromStep()) startFocus(f.focusStepMs());
        return true;
    }

    private static void startFocus(float ms) {
        if (reducedMotion() || ms <= 0) return;
        focus = 1;
        focusMs = ms;
    }

    /** The slow walk while they're up: a client-side speed modifier (the client moves its own player). */
    private static void walk(LocalPlayer p, boolean on) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean has = speed.getModifier(WALK_ID) != null;
        if (on && !has) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(WALK_ID, GlassFeel.get().walk() - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        } else if (!on && has) {
            speed.removeModifier(WALK_ID);
        }
    }

    /** The slow walk's own share of the speed, to keep it out of the FOV (1 when not walking slowly). */
    static float walkShare(LocalPlayer p) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(WALK_ID) != null ? GlassFeel.get().walk() : 1F;
    }

    private static void send(Minecraft mc) {
        boolean up = STATE.up();
        if (up == sentUp || mc.getConnection() == null) return;
        sentUp = up;
        if (mc.getConnection().hasChannel(BinocularsNet.State.TYPE)) ClientPacketDistributor.sendToServer(new BinocularsNet.State(up));
    }

    private static void play(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    /** Logged out or the player went away: everything back, nothing sent. */
    static void reset() {
        STATE.lower(GlassState.Drop.OFF);
        KEYS.reset();
        atEyes = pulling = false;
        focus = 0;
        SPRING.snap(1);
        restoreType = null;
        restoreShoulder = false;
        sentUp = false;
        level = null;
    }
}
