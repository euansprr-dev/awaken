package dev.awaken.holo.client;

import dev.awaken.Awaken;
import dev.awaken.holo.ui.HudFade;
import dev.awaken.powers.Loadout;
import dev.awaken.powers.Powers;
import dev.awaken.progression.Profile;
import dev.awaken.progression.Progression;
import dev.awaken.signal.client.SystemHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import java.util.Set;

/**
 * The calm HUD's tier 1 (docs/proposals/holo-ui.md §8, {@link HudFade}): the power bar and the level readout beside the
 * hotbar fade to 35% while nothing needs them. What needs them is read each tick from public APIs only: combat as the
 * System judges it ({@link SystemHud#inCombat}: hurt in the last 5 s or something hostile near), the loadout
 * ({@link Powers#clientLoadoutOrNull}: energy below full, a cooldown running, a power active, a slot changed) and the
 * profile ({@link Progression#profile}: level, XP or unspent points changed). The layers themselves are untouched: a
 * faded layer is drawn through {@link FadeGraphics}, which multiplies the alpha of everything it draws. The gate run
 * HUD is not tier 1 here: inside a gate the run is the context, and its collapse timer must never dim.
 */
final class CalmHud {
    private CalmHud() {}

    static final Set<Identifier> TIER_1 = Set.of(Awaken.id("power_bar"), Awaken.id("level"));

    private static final HudFade FADE = new HudFade();
    private static FadeGraphics graphics;
    private static int madeW, madeH;
    // what changed since the last tick
    private static final String[] SLOTS = new String[Loadout.SLOTS];
    private static int level = -1, unspent = -1;
    private static double xp = -1;

    /** Once a client tick. */
    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !HoloConfig.hudFade) {
            FADE.reset();
            return;
        }
        boolean busy = SystemHud.inCombat() | powersBusy(mc) | news(player);
        FADE.tick(busy);
    }

    /** Energy below full, a cooldown running, a power active or a slot changed. */
    private static boolean powersBusy(Minecraft mc) {
        Loadout l = Powers.clientLoadoutOrNull();
        if (l == null || mc.level == null) return false;
        long now = mc.level.getGameTime();
        boolean busy = l.energyAt(now) < l.maxEnergy() - 0.5f || !l.active().isEmpty();
        for (int i = 0; i < Loadout.SLOTS; i++) {
            String id = l.slot(i);
            if (!id.isEmpty() && l.cooldownLeft(id, now) > 0) busy = true;
            if (!id.equals(SLOTS[i])) {
                SLOTS[i] = id;
                busy = true;
            }
        }
        return busy;
    }

    /** Level, XP or unspent points moved: worth a look. */
    private static boolean news(LocalPlayer player) {
        Profile p = Progression.profile(player);
        boolean changed = p.level() != level || p.xp() != xp || p.unspent() != unspent;
        level = p.level();
        xp = p.xp();
        unspent = p.unspent();
        return changed;
    }

    /** Once a frame, real milliseconds since the last. */
    static void frame(float dtMs) {
        FADE.advance(dtMs);
    }

    /** The tier-1 layers' alpha this frame. */
    static float alpha() {
        return FADE.alpha();
    }

    /**
     * From {@link HudMotion#pre} (after its translate is pushed): a faded tier-1 layer is drawn here, through the fading
     * graphics, and its {@code Post} is fired as the layer manager would have (which pops the translate).
     */
    static void drawFaded(RenderGuiLayerEvent.Pre e, float alpha) {
        int w = e.getGuiGraphics().guiWidth(), h = e.getGuiGraphics().guiHeight();
        if (graphics == null || w != madeW || h != madeH) { // its scissor base is the screen size it was made at
            graphics = FadeGraphics.create();
            madeW = w;
            madeH = h;
        }
        e.setCanceled(true);
        e.getLayer().render(graphics.begin(e.getGuiGraphics(), alpha), e.getPartialTick());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(
                new RenderGuiLayerEvent.Post(e.getGuiGraphics(), e.getPartialTick(), e.getName(), e.getLayer()));
    }

    /** Autopilot: as if nothing had happened for a while (the fade starts now). */
    static void idleNow() {
        for (int i = 0; i < HudFade.IDLE_TICKS; i++) FADE.tick(false);
    }

    static void reset() {
        FADE.reset();
        level = unspent = -1;
        xp = -1;
        java.util.Arrays.fill(SLOTS, null);
    }
}
