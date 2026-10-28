package dev.awaken.holo.client;

import dev.awaken.Awaken;
import dev.awaken.core.api.Aim;
import dev.awaken.core.api.CombatFeedback;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.HudState;
import dev.awaken.holo.ui.ClearRules;
import dev.awaken.holo.ui.ClearRules.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.Map;

/**
 * The clear screen's director (SPEC §40.6): reads the player's state once a tick ({@link ClearRules.State}), asks
 * {@link ClearRules#want} what each piece wants, and eases each one there every frame. Installed as
 * {@link HudState}'s director, so every module's layer and the aim module's dot and mark ask it. Vanilla's hearts,
 * armour, food and XP give way to the {@link Vitals} lines; the hotbar slides down out of view and back up for 2 s when
 * the slot changes; the power bar shows small in a fight; the level chip shows with fresh XP. Drawn through
 * {@link HudMotion#pre}: a piece at 0 is skipped, one between is drawn through the fading graphics. Classic leaves
 * every layer as it was (and the calm HUD's own fade with it).
 */
final class HudDirector implements HudState.Director {
    static final HudDirector INSTANCE = new HudDirector();

    /** The vitals' own layer: it reads {@link #shown} for health and food apart, so it isn't faded as a whole. */
    static final Identifier VITALS = Awaken.id("vitals");
    private static final Identifier POWER_BAR = Awaken.id("power_bar"), LEVEL = Awaken.id("level");
    /** Vanilla's layers the vitals and the wrist carry instead. */
    private static final Map<Identifier, Piece> LAYERS = Map.of(
            VanillaGuiLayers.HOTBAR, Piece.HOTBAR,
            VanillaGuiLayers.PLAYER_HEALTH, Piece.VANILLA_STATUS,
            VanillaGuiLayers.ARMOR_LEVEL, Piece.VANILLA_STATUS,
            VanillaGuiLayers.FOOD_LEVEL, Piece.VANILLA_STATUS,
            VanillaGuiLayers.EXPERIENCE_LEVEL, Piece.VANILLA_STATUS,
            POWER_BAR, Piece.POWERS,
            LEVEL, Piece.LEVEL);
    /** The XP bar's strip also carries a mount's jump bar: kept while riding one. */
    private static final java.util.Set<Identifier> CONTEXT = java.util.Set.of(VanillaGuiLayers.CONTEXTUAL_INFO_BAR,
            VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND);
    /** How far the hotbar slides down when it leaves (GUI px). */
    static final int HOTBAR_DROP = 26;

    private final ClearRules.State state = new ClearRules.State();
    private final float[] shown = new float[ClearRules.PIECES];
    private final ClearRules.Timer fight = new ClearRules.Timer(ClearRules.FIGHT_TAIL);
    private final ClearRules.Timer hotbar = new ClearRules.Timer(ClearRules.HOTBAR_POP);
    private final ClearRules.Timer level = new ClearRules.Timer(ClearRules.LEVEL_SHOW);
    private int slot = -1, ticks;
    private boolean struck, hunted;
    private FadeGraphics graphics;
    private int madeW, madeH;

    private HudDirector() {
        java.util.Arrays.fill(shown, 1F);
    }

    // ---- HudState.Director -------------------------------------------------------------------------------------------

    @Override
    public HudState.Screen screen() {
        return HoloConfig.screen;
    }

    @Override
    public boolean fighting() {
        return fight.on();
    }

    @Override
    public boolean glancing() {
        return Glance.glancing();
    }

    @Override
    public float alpha(Identifier layer) {
        if (HoloConfig.screen == HudState.Screen.CLASSIC) return 1F;
        Piece p = LAYERS.get(layer);
        if (p != null) return shown[p.ordinal()];
        if (CONTEXT.contains(layer)) return state.riding ? 1F : 0F;
        return 1F;
    }

    @Override
    public int vitalsY(int guiHeight, int row) {
        return Vitals.y(guiHeight, shown[Piece.HOTBAR.ordinal()]) + row * Vitals.ROW;
    }

    /** How strongly a piece shows this frame. */
    float shown(Piece p) {
        return shown[p.ordinal()];
    }

    // ---- once a tick --------------------------------------------------------------------------------------------------

    void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            reset();
            return;
        }
        ticks++;
        if (ticks % 5 == 0) hunted = hunted(mc, p);
        fight.tick(p.hurtTime > 0 || struck || hunted || Aim.locked() || CombatFeedback.engaged.getAsBoolean());
        struck = false;
        int now = p.getInventory().getSelectedSlot();
        hotbar.tick(slot >= 0 && now != slot);
        slot = now;
        level.tick(CalmHud.news && ticks > 20); // not the profile's first arrival
        float max = Math.max(1F, p.getMaxHealth());
        state.screen = HoloConfig.screen;
        state.fighting = fight.on();
        state.glancing = Glance.glancing();
        state.hurt = p.getHealth() < max - 0.01F && !p.isCreative() && !p.isSpectator();
        state.low = p.getHealth() / max <= ClearRules.LOW;
        state.hungry = p.getFoodData().getFoodLevel() <= ClearRules.HUNGRY && !p.isCreative() && !p.isSpectator();
        state.hotbarPop = hotbar.on();
        state.powersBusy = CalmHud.powersBusy;
        state.levelNews = level.on();
        state.riding = p.jumpableVehicle() != null;
    }

    /** Something hostile within 16 blocks is after the player (it has a target and is closing), or one is right here. */
    private static boolean hunted(Minecraft mc, LocalPlayer p) {
        for (Mob m : mc.level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16))) {
            if (!m.isAlive()) continue;
            boolean hostile = m instanceof Enemy || CreatureTag.of(m).kind().hostile();
            if (!hostile) continue;
            if (m.isAggressive() || m.distanceToSqr(p) < 25 && p.hasLineOfSight(m)) return true;
        }
        return false;
    }

    /** The attack key on a creature: the player started it. */
    void onKey(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity le && le.isAlive()
                && !(le instanceof net.minecraft.world.entity.player.Player)) struck = true;
        if (Aim.target() >= 0) struck = true;
    }

    // ---- once a frame -------------------------------------------------------------------------------------------------

    void frame(float dtMs) {
        for (Piece p : Piece.values()) shown[p.ordinal()] = ClearRules.ease(shown[p.ordinal()], ClearRules.want(p, state), dtMs);
    }

    /** Whether this layer needs the director's own draw (moved or scaled) even at full strength. */
    boolean moves(Identifier id) {
        if (HoloConfig.screen == HudState.Screen.CLASSIC) return false;
        return id.equals(VanillaGuiLayers.HOTBAR) || id.equals(POWER_BAR);
    }

    /**
     * Draws a layer at {@code alpha} with its clear-screen placement (the hotbar slid down by what it has left, the
     * power bar small toward the corner), then fires its {@code Post} as the layer manager would.
     */
    void draw(RenderGuiLayerEvent.Pre e, float alpha) {
        GuiGraphicsExtractor real = e.getGuiGraphics();
        int w = real.guiWidth(), h = real.guiHeight();
        e.setCanceled(true);
        real.pose().pushMatrix();
        Identifier id = e.getName();
        if (id.equals(VanillaGuiLayers.HOTBAR)) {
            float k = shown[Piece.HOTBAR.ordinal()];
            real.pose().translate(0, Math.round(HOTBAR_DROP * (1F - k * k * (3F - 2F * k))));
        } else if (id.equals(POWER_BAR)) {
            real.pose().translate(w, h);
            real.pose().scale(ClearRules.POWERS_SMALL, ClearRules.POWERS_SMALL);
            real.pose().translate(-w, -h);
        }
        if (alpha >= 0.995F) e.getLayer().render(real, e.getPartialTick());
        else {
            if (graphics == null || w != madeW || h != madeH) { // its scissor base is the screen size it was made at
                graphics = FadeGraphics.create();
                madeW = w;
                madeH = h;
            }
            e.getLayer().render(graphics.begin(real, alpha), e.getPartialTick());
        }
        real.pose().popMatrix();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new RenderGuiLayerEvent.Post(real, e.getPartialTick(), id, e.getLayer()));
    }

    /** Autopilot and tests: as if a fight had just started (or ended long ago). */
    void forceFight(boolean on) {
        if (on) fight.kick();
        else fight.reset();
        state.fighting = fight.on();
    }

    void reset() {
        fight.reset();
        hotbar.reset();
        level.reset();
        slot = -1;
        struck = hunted = false;
        java.util.Arrays.fill(shown, 1F);
    }
}
