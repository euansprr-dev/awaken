package dev.awaken.arms.client;

import dev.awaken.arms.Archetype;
import dev.awaken.arms.Archetypes;
import dev.awaken.arms.Arms;
import dev.awaken.arms.FpSwings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * Third person's wrist (SPEC §25 "Working parts"): vanilla's third-person hold sets a weapon across the fist, square to
 * the forearm, and a vanilla arm has no wrist, so turning the forearm swings the blade round rather than turning its
 * edge. Through each move's clip the weapon turns in the fist (FpSwings' wrist keys, solved by the animation lab for
 * the arm that holds it): about its own length so its edge, bit or face leads the blow, and bent at the wrist so it
 * never passes through the head, the body or the legs. The fighter's hand items are
 * resolved again marked with their arm while a move turns them ({@link #modify}); {@link WeaponRenderer} turns them.
 * Authored right-handed: a left main arm keeps the hold as it is.
 */
final class ArmsWrist {
    private ArmsWrist() {}

    /** A fighter's move in the air: its style's keys, the move, the tick its clip began; or a heavy's draw held. */
    private record Going(FpSwings.Style style, Archetype.Move move, long start, boolean held) {}

    private static final Map<Integer, Going> GOING = new HashMap<>();

    /** The straight clips play (no Bendable Cuboids, so no elbows): their own wrist keys, where a rig solved them. */
    private static final boolean STRAIGHT = !net.neoforged.fml.ModList.get().isLoaded("bendable_cuboids");

    /** The arm whose item is being resolved (0 none, 1 right, 2 left) and whose it is: read by WeaponRenderer. */
    static int marking, markedHolder = -1;

    /** A move's clip began (ArmsBodies played it). */
    static void start(Entity e, Archetypes.Loaded style, Archetype.Move move) {
        FpSwings.Style st = FpSwings.style(style.id());
        if (st == null || FpSwings.wristLength(st, move) <= 0 || e instanceof LivingEntity l && l.getMainArm() == HumanoidArm.LEFT) {
            GOING.remove(e.getId());
            return;
        }
        GOING.put(e.getId(), new Going(st, move, e.level().getGameTime(), false));
    }

    /** A heavy's draw held (its charge clip): the wrist holds the turn the heavy is loosed with, so nothing snaps. */
    static void charge(Entity e) {
        GOING.remove(e.getId());
        if (!(e instanceof LivingEntity l) || l.getMainArm() == HumanoidArm.LEFT) return;
        Archetypes.Loaded style = Arms.styleOf(l.getMainHandItem());
        FpSwings.Style st = style == null ? null : FpSwings.style(style.id());
        if (st != null && FpSwings.wristLength(st, Archetype.Move.HEAVY) > 0) {
            GOING.put(e.getId(), new Going(st, Archetype.Move.HEAVY, 0L, true));
        }
    }

    /** Another clip took over (a dodge, a charge, a settle). */
    static void stop(Entity e) {
        GOING.remove(e.getId());
    }

    static void reset() {
        GOING.clear();
    }

    /** Forgets fighters no longer in the level. */
    static void prune(Level level) {
        if (!GOING.isEmpty()) GOING.keySet().removeIf(id -> level.getEntity(id) == null);
    }

    /** The turn of the weapon in a holder's right or left arm now, into {@code out}; false when no move turns it. */
    static boolean turn(int holder, boolean left, Quaternionf out) {
        Going g = GOING.get(holder);
        Minecraft mc = Minecraft.getInstance();
        if (g == null || mc.level == null) return false;
        float t = g.held() ? 0F : (mc.level.getGameTime() - g.start()) + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        return t <= FpSwings.wristLength(g.style(), g.move()) && FpSwings.wrist(g.style(), g.move(), left, STRAIGHT, t, out);
    }

    /** The player's render-state modifier: while a move turns the wrist, its hand items again, marked with their arm. */
    static void modify(Entity entity, EntityRenderState state) {
        if (!(entity instanceof Player p) || !(state instanceof ArmedEntityRenderState armed)) return;
        Going g = GOING.get(p.getId());
        if (g == null) return;
        if (!g.held() && p.level().getGameTime() - g.start() > FpSwings.wristLength(g.style(), g.move()) + 1) {
            GOING.remove(p.getId());
            return;
        }
        var resolver = Minecraft.getInstance().getItemModelResolver();
        markedHolder = p.getId();
        try {
            marking = 1;
            if (!armed.rightHandItemStack.isEmpty()) {
                resolver.updateForLiving(armed.rightHandItemState, armed.rightHandItemStack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, p);
            }
            marking = 2;
            if (!armed.leftHandItemStack.isEmpty()) {
                resolver.updateForLiving(armed.leftHandItemState, armed.leftHandItemStack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, p);
            }
        } finally {
            marking = 0;
            markedHolder = -1;
        }
    }
}
