package dev.awaken.living;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.living.api.Regions;
import dev.awaken.living.combat.LevelMoments;
import dev.awaken.living.region.RegionMap;
import dev.awaken.living.region.RegionService;
import dev.awaken.progression.Progression;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code /awaken living region} (anyone): the region you stand in, its levels and where its heart is.
 * Admin: {@code regions [radius]} (the regions round you, nearest first), {@code tag} (what the band reads off the creature
 * you look at), {@code moment} (play the level moment here, for tours).
 */
final class LivingCommands {
    private LivingCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var l = Commands.literal("living");
            l.then(Commands.literal("region").executes(c -> region(c.getSource().getPlayerOrException())));
            l.then(Commands.literal("regions").requires(AwakenCommands.admin())
                    .executes(c -> regions(c.getSource(), c.getSource().getPlayerOrException(), 3000))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(500, 20000))
                            .executes(c -> regions(c.getSource(), c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "radius")))));
            l.then(Commands.literal("tag").requires(AwakenCommands.admin()).executes(c -> tag(c.getSource().getPlayerOrException())));
            l.then(Commands.literal("moment").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                LevelMoments.offer(p, Progression.profile(p).level(), 1);
                return 1;
            }));
            root.then(l);
        });
    }

    private static int region(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || !Regions.on(level)) {
            p.sendSystemMessage(Component.literal("Regions are an overworld thing. Here the world keeps its vanilla danger."));
            return 0;
        }
        Regions.Region r = Regions.regionAt(level, p.getBlockX(), p.getBlockZ());
        int here = Regions.levelAt(level, p.getBlockX(), p.getBlockZ());
        RegionMap map = RegionService.map(level.getServer());
        p.sendSystemMessage(Component.literal(r.name() + " · Lv " + r.low() + "-" + r.high() + " · level " + here + " here"
                + (r.commons() ? "" : " · heart at " + r.heartX() + " " + r.heartZ())
                + " · " + (int) Math.sqrt(Math.pow(p.getX() - map.centreX(), 2) + Math.pow(p.getZ() - map.centreZ(), 2)) + " m from the centre"));
        return 1;
    }

    private static int regions(CommandSourceStack src, ServerPlayer p, int radius) {
        if (!(p.level() instanceof ServerLevel level) || !Regions.on(level)) return 0;
        Map<Long, Regions.Region> found = new LinkedHashMap<>();
        int step = 150;
        for (int dx = -radius; dx <= radius; dx += step) {
            for (int dz = -radius; dz <= radius; dz += step) {
                if (dx * dx + dz * dz > radius * radius) continue;
                Regions.Region r = Regions.regionAt(level, p.getBlockX() + dx, p.getBlockZ() + dz);
                found.putIfAbsent(r.id(), r);
            }
        }
        found.values().stream()
                .sorted(java.util.Comparator.comparingDouble(r -> Math.pow(r.heartX() - p.getX(), 2) + Math.pow(r.heartZ() - p.getZ(), 2)))
                .forEach(r -> src.sendSuccess(() -> Component.literal(String.format("%-22s Lv %3d-%-3d %s", r.name(), r.low(), r.high(),
                        r.commons() ? "(home)" : "heart " + r.heartX() + " " + r.heartZ())), false));
        return found.size();
    }

    private static int tag(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1).scale(24);
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, eye.add(look),
                new AABB(eye, eye.add(look)).inflate(1), e -> e instanceof LivingEntity && e != p, 24 * 24);
        if (hit == null) {
            p.sendSystemMessage(Component.literal("Look at a creature."));
            return 0;
        }
        CreatureTag t = CreatureTag.of(hit.getEntity());
        p.sendSystemMessage(Component.literal(hit.getEntity().getName().getString() + ": " + (t.present()
                ? "Lv " + t.level() + " " + t.kind() + (t.elite() ? " ELITE" : "") + (t.title().isEmpty() ? "" : " · " + t.title())
                : "no tag")));
        return 1;
    }
}
