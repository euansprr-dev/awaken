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
            // tours: a blow at the creature you look at (no attack key in the autopilot), a drop in a pillar
            l.then(Commands.literal("strike").requires(AwakenCommands.admin())
                    .executes(c -> strike(c.getSource().getPlayerOrException(), 4, false))
                    .then(Commands.literal("crit").executes(c -> strike(c.getSource().getPlayerOrException(), 9, true)))
                    .then(Commands.literal("kill").executes(c -> strike(c.getSource().getPlayerOrException(), 100000, false))));
            l.then(Commands.literal("pillar").requires(AwakenCommands.admin()).executes(c -> pillar(c.getSource().getPlayerOrException())));
            l.then(Commands.literal("moment").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                LevelMoments.offer(p, Progression.profile(p).level(), 1);
                return 1;
            }));
            // Wayfarers (Phase C): a person here, a talk with the nearest (no use key in the autopilot), a scene now
            l.then(Commands.literal("wayfarer").requires(AwakenCommands.admin())
                    .then(Commands.argument("who", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        String who = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "who");
                        if (dev.awaken.living.wayfarer.Wayfarers.get(who) == null) {
                            c.getSource().sendFailure(Component.literal("Nobody called " + who + "."));
                            return 0;
                        }
                        Vec3 at = p.position().add(p.getLookAngle().multiply(1, 0, 1).normalize().scale(3));
                        dev.awaken.living.wayfarer.Scenes.summon(p.level(), who, net.minecraft.core.BlockPos.containing(at));
                        return 1;
                    })));
            l.then(Commands.literal("camp").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                var at = dev.awaken.living.wayfarer.Scenes.campStand(p.level().getServer());
                p.teleportTo(p.level().getServer().overworld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(), 180F, 10F, true);
                return 1;
            }));
            // tours: stand <distance> blocks south of your newest open scene, facing it (to see its flare)
            l.then(Commands.literal("view").requires(AwakenCommands.admin())
                    .then(Commands.argument("distance", IntegerArgumentType.integer(4, 400)).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        var scene = dev.awaken.living.wayfarer.Scenes.newest(p);
                        if (scene == null) return 0;
                        int d = IntegerArgumentType.getInteger(c, "distance");
                        ServerLevel level = p.level().getServer().overworld();
                        int x = scene.getX(), z = scene.getZ() + d;
                        level.getChunk(x >> 4, z >> 4);
                        // above the canopy round the spot, on a small glass stand (a tour's camera, never inside leaves)
                        int y = 0;
                        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++)
                            y = Math.max(y, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz));
                        y += d > 20 ? 6 : 1;
                        if (d > 20) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                            level.setBlock(new net.minecraft.core.BlockPos(x + dx, y - 1, z + dz), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(), 3);
                        float pitch = (float) -Math.toDegrees(Math.atan2(scene.getY() + 20 - (y + 1.6), d));
                        p.teleportTo(level, x + 0.5, y, z + 0.5, java.util.Set.of(), 180F, pitch, true);
                        return 1;
                    })));
            l.then(Commands.literal("talk").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                var near = p.level().getEntitiesOfClass(dev.awaken.living.wayfarer.Wayfarer.class, p.getBoundingBox().inflate(24));
                near.sort(java.util.Comparator.comparingDouble(w -> w.distanceToSqr(p)));
                if (near.isEmpty()) return 0;
                dev.awaken.living.wayfarer.Wayfarers.talkTo(p, near.getFirst());
                return 1;
            }));
            l.then(Commands.literal("scene").requires(AwakenCommands.admin())
                    .then(Commands.argument("chain", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        var at = dev.awaken.living.wayfarer.Scenes.force(p, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "chain"));
                        c.getSource().sendSuccess(() -> Component.literal(at == null ? "No such chain." : "Scene at " + at.toShortString()), false);
                        return at == null ? 0 : 1;
                    })));
            // the World Pulse (Phase D): start a kind here now, list what's on, end it all
            l.then(Commands.literal("happen").requires(AwakenCommands.admin())
                    .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        var h = dev.awaken.living.pulse.Pulse.force(p, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "kind"));
                        c.getSource().sendSuccess(() -> Component.literal(h == null ? "No place for it here (or no such kind)." : "Happening " + h.id + " at " + net.minecraft.core.BlockPos.containing(h.centre).toShortString()), false);
                        return h == null ? 0 : 1;
                    })));
            l.then(Commands.literal("happenings").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                var list = dev.awaken.living.pulse.Pulse.get(p.level().getServer()).active();
                for (var h : list) {
                    int d = (int) Math.sqrt(h.centre.distanceToSqr(p.position()));
                    c.getSource().sendSuccess(() -> Component.literal(h.id + " " + h.kind + " · " + d + " m · " + Math.round(h.progress(p.level().getGameTime()) * 100) + "%"), false);
                }
                c.getSource().sendSuccess(() -> Component.literal(list.size() + " on · moon " + dev.awaken.living.pulse.Pulse.moon(p.level().getServer().overworld())), false);
                return 1;
            }));
            l.then(Commands.literal("calm").requires(AwakenCommands.admin()).executes(c -> {
                int n = dev.awaken.living.pulse.Pulse.endAll(c.getSource().getServer());
                c.getSource().sendSuccess(() -> Component.literal(n + " happenings ended."), false);
                return n;
            }));
            l.then(Commands.literal("quests").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                var s = dev.awaken.living.quest.QuestEngine.state(p);
                for (var a : s.active()) {
                    String line = dev.awaken.living.quest.QuestEngine.line(a);
                    c.getSource().sendSuccess(() -> Component.literal(a.chain() + " · step " + a.step() + " · " + line), false);
                }
                c.getSource().sendSuccess(() -> Component.literal(s.active().size() + " active, " + s.done().size() + " done"), false);
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

    private static int strike(ServerPlayer p, float damage, boolean crit) {
        LivingEntity target = looked(p);
        if (target == null) {
            p.sendSystemMessage(Component.literal("Look at a creature."));
            return 0;
        }
        if (crit) dev.awaken.living.combat.Hits.markCrit(p);
        target.hurtServer((ServerLevel) p.level(), p.damageSources().playerAttack(p), damage);
        return 1;
    }

    private static int pillar(ServerPlayer p) {
        Vec3 at = p.getEyePosition().add(p.getViewVector(1).scale(3));
        var core = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(dev.awaken.Awaken.id("power_core"));
        var stack = new net.minecraft.world.item.ItemStack(core == net.minecraft.world.item.Items.AIR ? net.minecraft.world.item.Items.NETHER_STAR : core);
        var item = new net.minecraft.world.entity.item.ItemEntity(p.level(), at.x, at.y, at.z, stack);
        item.setData(LivingContent.LOOT_PILLAR.get(), 3);
        item.setUnlimitedLifetime();
        p.level().addFreshEntity(item);
        return 1;
    }

    private static @org.jspecify.annotations.Nullable LivingEntity looked(ServerPlayer p) {
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1).scale(24);
        var hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p, eye, eye.add(look),
                new AABB(eye, eye.add(look)).inflate(1), e -> e instanceof LivingEntity && e != p, 24 * 24);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
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
