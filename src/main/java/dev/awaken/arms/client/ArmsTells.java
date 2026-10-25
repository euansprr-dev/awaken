package dev.awaken.arms.client;

import dev.awaken.core.api.Strikes;
import dev.awaken.powers.client.fx.FxClient;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * Every told blow seen as well as heard (SPEC §25 "Tells"): the eye learns the class before the blow, as the ear does
 * from its voice. LIGHT (deflect, block or dodge): a white glint on the striking hand five ticks before it lands, the
 * cue to raise the guard. HEAVY (deflect or dodge; it breaks a guard): an amber flare and ring as the wind-up starts,
 * then the hand burns amber, brighter as it nears. PERILOUS (dodge only): a big red flare at the hand and a red rune
 * ring on the ground under it, turning and tightening for the whole wind-up (over the head it hid behind the name
 * plate, first tells tour). GRAB (dodge only): the same in violet. Drawn on the effects engine from the synced tell
 * ({@link Strikes#tell}); fixed slots, nothing allocated per tick.
 */
final class ArmsTells {
    private ArmsTells() {}

    private static final int LIGHT = 0xDCEEFF, HEAVY = 0xFFA63A, PERILOUS = 0xFF2E2E, GRAB = 0xC452FF;
    /** Ticks before contact the LIGHT glint shows (the deflect window is 4: a tick to see it, then the press). */
    private static final int GLINT_AT = 5;
    private static final double RANGE = 32;
    private static final int SLOTS = 48;
    private static final int[] ID = new int[SLOTS], KIND = new int[SLOTS], LEFT = new int[SLOTS];

    static {
        java.util.Arrays.fill(ID, Integer.MIN_VALUE);
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        for (int i = 0; i < SLOTS; i++) {
            if (ID[i] != Integer.MIN_VALUE && mc.level.getEntity(ID[i]) == null) ID[i] = Integer.MIN_VALUE;
        }
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player || !(e instanceof LivingEntity) || e.distanceToSqr(mc.player) > RANGE * RANGE) continue;
            Strikes.Tell t = Strikes.tell(e);
            int slot = slot(e.getId(), t != null);
            if (slot < 0) continue;
            if (t == null || t.kind() == Strikes.Kind.NONE) {
                ID[slot] = Integer.MIN_VALUE;
                continue;
            }
            boolean fresh = KIND[slot] != t.kind().ordinal() || t.ticks() > LEFT[slot];
            KIND[slot] = t.kind().ordinal();
            LEFT[slot] = t.ticks();
            mark(e, t, fresh);
        }
    }

    private static void mark(Entity e, Strikes.Tell t, boolean fresh) {
        double hx = handX(e), hy = e.getY() + e.getBbHeight() * 0.62, hz = handZ(e);
        float size = Mth.clamp(e.getBbWidth() * 0.55F, 0.35F, 1.6F);
        switch (t.kind()) {
            case LIGHT -> {
                if (t.ticks() == GLINT_AT || fresh && t.ticks() < GLINT_AT) FxClient.tellMark(FxClient.TELL_GLINT, hx, hy, hz, LIGHT, size * 1.1F, 5);
            }
            case HEAVY -> {
                if (fresh) FxClient.tellMark(FxClient.TELL_FLARE, hx, hy, hz, HEAVY, size * 1.2F, 8);
                // the hand burning amber, brighter as the blow nears
                float near = 1F - Math.min(1F, t.ticks() / 16F);
                FxClient.tellMark(FxClient.TELL_GLOW, hx, hy, hz, HEAVY, size * (0.45F + 0.5F * near), 2);
            }
            case PERILOUS, GRAB -> {
                int rgb = t.kind() == Strikes.Kind.PERILOUS ? PERILOUS : GRAB;
                if (fresh) FxClient.tellMark(FxClient.TELL_FLARE, hx, hy, hz, rgb, size * 2.0F, 10);
                // the ring tightens as the blow nears: from its reach to the body's own width
                float near = 1F - Math.min(1F, t.ticks() / 20F);
                float ring = Mth.clamp(e.getBbWidth() * (3.2F - 1.4F * near) + 0.6F, 1.4F, 6F);
                FxClient.tellMark(FxClient.TELL_SIGN, e.getX(), e.getY() + 0.06, e.getZ(), rgb, ring, 2);
                FxClient.tellMark(FxClient.TELL_GLOW, hx, hy, hz, rgb, size * (0.5F + 0.6F * near), 2);
            }
            default -> { }
        }
    }

    /** The striking hand: ahead of the body at its reach, toward its main arm. */
    private static double handX(Entity e) {
        float yaw = (e instanceof LivingEntity l ? l.yBodyRot : e.getYRot()) * Mth.DEG_TO_RAD;
        int side = e instanceof LivingEntity l && l.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        return e.getX() - Mth.sin(yaw) * e.getBbWidth() * 0.75 - Mth.cos(yaw) * e.getBbWidth() * 0.35 * side;
    }

    private static double handZ(Entity e) {
        float yaw = (e instanceof LivingEntity l ? l.yBodyRot : e.getYRot()) * Mth.DEG_TO_RAD;
        int side = e instanceof LivingEntity l && l.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        return e.getZ() + Mth.cos(yaw) * e.getBbWidth() * 0.75 - Mth.sin(yaw) * e.getBbWidth() * 0.35 * side;
    }

    private static int slot(int id, boolean claim) {
        int free = -1;
        for (int i = 0; i < SLOTS; i++) {
            if (ID[i] == id) return i;
            if (ID[i] == Integer.MIN_VALUE && free < 0) free = i;
        }
        if (!claim || free < 0) return -1;
        ID[free] = id;
        KIND[free] = -1;
        LEFT[free] = 0;
        return free;
    }

    static void reset() {
        java.util.Arrays.fill(ID, Integer.MIN_VALUE);
    }
}
