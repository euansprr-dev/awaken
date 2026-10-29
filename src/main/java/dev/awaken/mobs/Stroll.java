package dev.awaken.mobs;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * {@code /awaken admin stroll <targets> <blocks per tick> <ticks> [degrees per tick]}: moves still (NoAI) creatures
 * forward along their facing on the server, every tick, turning as they go; each call queues a leg after the ones
 * before. Tours film the motion layer's gait with it (SPEC §23.1): a tp a command is one step per round trip, which a
 * busy runner turns into a crawl.
 */
final class Stroll {
    private Stroll() {}

    private static final Map<Entity, ArrayDeque<float[]>> QUEUE = new IdentityHashMap<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener(Stroll::tick);
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("admin").requires(AwakenCommands.admin())
                .then(Commands.literal("stroll").then(Commands.argument("targets", EntityArgument.entities())
                        .then(Commands.argument("speed", FloatArgumentType.floatArg(0.0F, 2.0F))
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 2000))
                                        .executes(c -> queue(c, 0.0F))
                                        .then(Commands.argument("turn", FloatArgumentType.floatArg(-90.0F, 90.0F))
                                                .executes(c -> queue(c, FloatArgumentType.getFloat(c, "turn"))))))))));
    }

    private static int queue(CommandContext<CommandSourceStack> c, float turn) throws CommandSyntaxException {
        float speed = FloatArgumentType.getFloat(c, "speed");
        int ticks = IntegerArgumentType.getInteger(c, "ticks");
        int n = 0;
        for (Entity e : EntityArgument.getEntities(c, "targets")) {
            QUEUE.computeIfAbsent(e, k -> new ArrayDeque<>()).add(new float[]{speed, ticks, turn});
            n++;
        }
        return n;
    }

    private static void tick(ServerTickEvent.Post event) {
        if (QUEUE.isEmpty()) return;
        for (Iterator<Map.Entry<Entity, ArrayDeque<float[]>>> it = QUEUE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, ArrayDeque<float[]>> en = it.next();
            Entity e = en.getKey();
            ArrayDeque<float[]> legs = en.getValue();
            if (e.isRemoved() || legs.isEmpty()) {
                it.remove();
                continue;
            }
            float[] leg = legs.peek();
            float yaw = e.getYRot() + leg[2];
            float a = yaw * Mth.DEG_TO_RAD;
            e.snapTo(e.getX() - Mth.sin(a) * leg[0], e.getY(), e.getZ() + Mth.cos(a) * leg[0], yaw, e.getXRot());
            if (e instanceof LivingEntity l) {
                l.setYBodyRot(yaw);
                l.setYHeadRot(yaw);
            }
            if (--leg[1] <= 0) legs.poll();
        }
    }
}
