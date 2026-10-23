package dev.awaken.gear.client;

import dev.awaken.gear.HunterTier;
import dev.awaken.gear.SetBonus;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * Wildhide's full-set twist: while you sneak, living creatures within 12 + PER/2 blocks are outlined through walls
 * (amber for animals and the awakened creatures, crimson for monsters). Entirely client side: the client already knows
 * your armour and your stats. Other players, mannequins and armour stands are never outlined.
 */
public final class HunterSense {
    private HunterSense() {}

    static final int AMBER = 0xFFF2A541;
    static final int CRIMSON = 0xFFD84A4F;

    private static boolean active;
    private static double radiusSq;

    /** Once per client tick: is the sense on, and how far does it reach? */
    static void tick() {
        LocalPlayer p = Minecraft.getInstance().player;
        active = p != null && !p.isSpectator() && p.isShiftKeyDown() && SetBonus.has(p, HunterTier.E, SetBonus.FULL);
        if (active) {
            int r = SetBonus.senseRadius(SetBonus.stat(p, Stat.PER));
            radiusSq = (double) r * r;
        }
    }

    public static boolean active() {
        return active;
    }

    /** Render-state modifier for every entity; never overrides an outline someone else set. */
    static void modify(Entity entity, EntityRenderState state) {
        if (!active || state.outlineColor != 0) return;
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity instanceof Avatar || entity instanceof ArmorStand) return;
        LocalPlayer self = Minecraft.getInstance().player;
        if (self == null || entity == self || entity.distanceToSqr(self) > radiusSq) return;
        state.outlineColor = entity.getType().getCategory() == MobCategory.MONSTER ? CRIMSON : AMBER;
    }
}
