package dev.awaken.veil.client;

import dev.awaken.core.client.AwakenKeys;
import dev.awaken.veil.VeilContent;
import dev.awaken.veil.entity.VeilSpirit;
import dev.awaken.veil.net.VeilPayloads;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Veil keys. Awake: sneak + hold Focus (G) to meditate, N to just sit. Out of the body: G speaks to a spirit you look at,
 * B wears a creature (again to let go), use (right click) touches something blue, Y goes back, attack acts while
 * wearing a creature. Vanilla spectator clicks (spectate an entity, the teleport menu) are swallowed here.
 */
final class VeilInput {
    private VeilInput() {}

    private static boolean holdingSent;
    private static boolean justSit;
    private static boolean prevAccept;
    private static boolean prevDismiss;

    static void reset() {
        holdingSent = false;
        justSit = false;
    }

    private static void send(net.minecraft.network.protocol.common.custom.CustomPacketPayload p) {
        if (Minecraft.getInstance().getConnection() != null) ClientPacketDistributor.sendToServer(p);
    }

    static void preTick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        boolean accept = AwakenKeys.ACCEPT.isDown(), dismiss = AwakenKeys.DISMISS.isDown();
        boolean acceptPressed = accept && !prevAccept, dismissPressed = dismiss && !prevDismiss;
        prevAccept = accept;
        prevDismiss = dismiss;
        if (VeilClientState.spirit()) {
            spiritKeys(mc, p, acceptPressed);
            return;
        }
        boolean screenOpen = mc.gui.screen() != null;
        boolean want = !screenOpen && AwakenKeys.FOCUS.isDown() && p.isShiftKeyDown();
        if (want && dismissPressed && VeilClientState.meditation.ticks() > 0 && !justSit) {
            justSit = true;
            send(new VeilPayloads.MeditateInput(true, true));
        }
        if (want != holdingSent) {
            holdingSent = want;
            if (want) {
                send(new VeilPayloads.MeditateInput(true, justSit));
            } else {
                // letting go of Focus completes the meditation; standing up (or a screen) only ends it
                boolean letGoOfFocus = !AwakenKeys.FOCUS.isDown() && !screenOpen;
                send(new VeilPayloads.MeditateInput(false, justSit || !letGoOfFocus));
                justSit = false;
            }
        }
    }

    private static void spiritKeys(Minecraft mc, LocalPlayer p, boolean acceptPressed) {
        if (holdingSent) {
            holdingSent = false;
            justSit = false;
        }
        boolean attack = false, use = false;
        while (mc.options.keyAttack.consumeClick()) attack = true;
        while (mc.options.keyUse.consumeClick()) use = true;
        while (mc.options.keyPickItem.consumeClick()) { /* no spectator menu */ }
        for (KeyMapping k : mc.options.keyHotbarSlots) while (k.consumeClick()) { /* no spectator teleports */ }
        if (mc.gui.screen() != null) return;
        boolean possessing = VeilClientState.possessing();
        while (AwakenKeys.FOCUS.consumeClick()) {
            Entity e = pick(mc, 7, en -> en instanceof VeilSpirit);
            if (e != null) send(new VeilPayloads.Action(VeilPayloads.Action.TALK, e.getId(), BlockPos.ZERO));
        }
        while (AwakenKeys.BIND.consumeClick()) {
            if (possessing) send(new VeilPayloads.Action(VeilPayloads.Action.RELEASE, -1, BlockPos.ZERO));
            else {
                Entity e = pick(mc, 8, en -> en instanceof Mob && !(en instanceof VeilSpirit) && en.isAlive());
                if (e != null) send(new VeilPayloads.Action(VeilPayloads.Action.POSSESS, e.getId(), BlockPos.ZERO));
            }
        }
        if (acceptPressed) send(new VeilPayloads.Action(VeilPayloads.Action.RETURN, -1, BlockPos.ZERO));
        if (possessing) {
            if (attack) {
                Entity t = pick(mc, 4, en -> en instanceof LivingEntity && en.getId() != VeilClientState.state.possessedId() && en != p);
                send(new VeilPayloads.Action(VeilPayloads.Action.ATTACK, t == null ? -1 : t.getId(), BlockPos.ZERO));
            }
            float fwd = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
            float str = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
            send(new VeilPayloads.PossessInput(fwd, str, mc.options.keyJump.isDown(), mc.options.keySprint.isDown(), p.getYRot(), p.getXRot()));
        } else if (use) {
            BlockPos pos = pickBlock(mc, 5.5);
            if (pos != null) send(new VeilPayloads.Action(VeilPayloads.Action.USE, -1, pos));
        }
    }

    /** The eye the spirit sees from: the worn creature's, or the player's. */
    private static Vec3 eye(Minecraft mc) {
        Entity cam = mc.getCameraEntity();
        return cam != null ? cam.getEyePosition() : mc.player.getEyePosition();
    }

    /** Entities under the crosshair, through walls (a spirit looks through things). */
    static @Nullable Entity pick(Minecraft mc, double range, Predicate<Entity> filter) {
        LocalPlayer p = mc.player;
        Vec3 from = eye(mc);
        Vec3 look = Vec3.directionFromRotation(p.getXRot(), p.getYRot());
        Vec3 to = from.add(look.scale(range));
        Entity cam = mc.getCameraEntity();
        AABB box = new AABB(from, to).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, from, to, box,
                e -> e != p && e != cam && !e.isSpectator() && filter.test(e), range * range);
        return hit == null ? null : hit.getEntity();
    }

    /** The first blue (Veil-touchable) block along the look, else the first solid one (so the server can say no). */
    static @Nullable BlockPos pickBlock(Minecraft mc, double range) {
        LocalPlayer p = mc.player;
        Vec3 from = eye(mc);
        Vec3 look = Vec3.directionFromRotation(p.getXRot(), p.getYRot());
        BlockPos firstSolid = null;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (double d = 0.2; d <= range; d += 0.1) {
            Vec3 at = from.add(look.scale(d));
            m.set(at.x, at.y, at.z);
            BlockState s = p.level().getBlockState(m);
            if (s.isAir()) continue;
            if (s.is(VeilContent.VEIL_INTERACTABLE)) return m.immutable();
            if (firstSolid == null && !s.getCollisionShape(p.level(), m).isEmpty()) firstSolid = m.immutable();
        }
        return firstSolid;
    }
}
