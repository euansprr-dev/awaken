package dev.awaken.mobs.client.life;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.Awaken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Dev: one creature's life frame by frame in the log (SPEC §47.13), for what a clip at a few frames a second can't show.
 * {@code /awaken lifetrace <type path> <frames>} picks the nearest of that kind and logs, each frame it's drawn, where
 * it's drawn, its body's turn and every way the life layer moves the body (pitch, roll, dip, the feet's sink and tilt,
 * which feet are up, a blow's flinch and its red). Render thread only; off unless asked.
 */
public final class LifeTrace {
    private LifeTrace() {}

    /** The traced creature's entity id, -1 when off. */
    static int id = -1;
    private static int left;

    public static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("awaken").then(Commands.literal("lifetrace")
                .then(Commands.argument("type", StringArgumentType.word())
                        .then(Commands.argument("frames", IntegerArgumentType.integer(1, 4000)).executes(c -> {
                            start(StringArgumentType.getString(c, "type"), IntegerArgumentType.getInteger(c, "frames"));
                            return 1;
                        })))));
    }

    private static void start(String type, int frames) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Entity best = null;
        double near = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals(type)) continue;
            double d = e.distanceToSqr(mc.player);
            if (d < near) {
                near = d;
                best = e;
            }
        }
        id = best != null ? best.getId() : -1;
        left = frames;
        Awaken.LOG.info("[lifetrace] {} {}: {} frames", type, best != null ? "id " + id + " at " + (int) Math.sqrt(near) + " blocks" : "none near", frames);
        Awaken.LOG.info("[lifetrace] cols: frame t dt far x y z bodyRot yRot walk speed pitch roll dip sway legsW gY gPitch gRoll gaitY vault up hurt red");
    }

    static void frame(Life l, LivingEntityRenderState s) {
        Legs g = l.legs;
        int up = 0;
        if (g != null) for (int i = 0; i < g.rig.n; i++) if (g.stepping(i)) up |= 1 << i;
        Awaken.LOG.info(String.format(java.util.Locale.ROOT,
                "[lifetrace] %d %.3f %.4f %d %.4f %.4f %.4f %.2f %.2f %.3f %.3f %.4f %.4f %.3f %.4f %.3f %.3f %.4f %.4f %.3f %.3f %s %.2f %d",
                l.frame, l.t, l.dt, l.far() ? 1 : 0, s.x, s.y, s.z, s.bodyRot, s.yRot, l.walk, l.speed, l.bodyPitch, l.bodyRoll, l.dip, l.swayRoll,
                g != null ? g.weight : 0, g != null ? g.groundY : 0, g != null ? g.groundPitch : 0, g != null ? g.groundRoll : 0,
                g != null ? g.gaitY : 0, g != null ? g.vault : 0, Integer.toBinaryString(up), l.hurt, s.hasRedOverlay ? 1 : 0));
        if (--left <= 0) {
            id = -1;
            Awaken.LOG.info("[lifetrace] done");
        }
    }
}
