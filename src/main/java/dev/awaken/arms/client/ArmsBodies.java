package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import dev.awaken.arms.Arms;
import dev.awaken.arms.ArmsClips;
import dev.awaken.core.api.PlayerClips;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Fighters' bodies in third person (SPEC §25), for players with the Player Animation Library (optional): each move's
 * clip ({@link ArmsClips}) on the fighter's own prediction and on everyone else's word, the guard held while it is
 * up, the heavy's draw held while it charges, the dodge by its way. Without the library {@link PlayerClips} plays
 * nothing and the fighter keeps vanilla's arm swing.
 */
final class ArmsBodies {
    private ArmsBodies() {}

    /** The looped clip a player is holding (guard or charge), so each starts once and settles once. */
    private static final Map<Integer, String> HELD = new HashMap<>();

    static void move(Entity e, Archetype.Move move) {
        String clip = ArmsClips.of(move);
        if (clip == null) return;
        HELD.remove(e.getId());
        PlayerClips.play(e.getId(), clip, 1);
    }

    static void charge(Entity e) {
        if (PlayerClips.play(e.getId(), ArmsClips.CHARGE, 2)) HELD.put(e.getId(), ArmsClips.CHARGE);
    }

    /** The dodge's clip for a way (dx, dz in the world): back, or a step to one side, mirrored for a left hand. */
    static void dodge(Entity e, double dx, double dz) {
        float way = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        float rel = Mth.wrapDegrees(way - e.getYRot());
        if (e instanceof LivingEntity l && l.getMainArm() == HumanoidArm.LEFT) rel = -rel; // the clip is mirrored for it
        HELD.remove(e.getId());
        PlayerClips.play(e.getId(), ArmsClips.dodge(rel), 1);
    }

    /** A refused or cancelled move: whatever is held settles. */
    static void settle(Entity e) {
        String held = HELD.remove(e.getId());
        if (held != null && !PlayerClips.play(e.getId(), held + "_end", 2)) PlayerClips.stop(e.getId());
    }

    /** Guards up and down for every player near: the guard clip held while one is raised. */
    static void tick() {
        if (!PlayerClips.available()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        for (Player p : mc.level.players()) {
            if (p.distanceToSqr(mc.player) > 48 * 48) continue;
            boolean guarding = p.isUsingItem() && Arms.guards(p.getUseItem());
            String held = HELD.get(p.getId());
            if (guarding && !ArmsClips.GUARD.equals(held)) {
                if (PlayerClips.play(p.getId(), ArmsClips.GUARD, 2)) HELD.put(p.getId(), ArmsClips.GUARD);
            } else if (!guarding && ArmsClips.GUARD.equals(held)) {
                settle(p);
            }
        }
        if (!HELD.isEmpty()) HELD.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    static void reset() {
        HELD.clear();
    }
}
