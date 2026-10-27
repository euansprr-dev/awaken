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
            // tours: the land weighs as if it were this many levels above you (the tint and the sound bed), or the real land
            l.then(Commands.literal("dread").requires(AwakenCommands.admin())
                    .then(Commands.literal("off").executes(c -> {
                        dev.awaken.living.region.RegionTracker.forceGap(c.getSource().getPlayerOrException(), null);
                        return 1;
                    }))
                    .then(Commands.argument("gap", IntegerArgumentType.integer(-20, 120)).executes(c -> {
                        dev.awaken.living.region.RegionTracker.forceGap(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "gap"));
                        return 1;
                    })));
            // forms (SPEC 21.11): one creature in a form, or a row of a species at every rung, standing still ahead of you (tours)
            l.then(Commands.literal("form").requires(AwakenCommands.admin())
                    .then(Commands.argument("species", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Arrays.stream(dev.awaken.living.combat.Forms.Species.values()).map(s -> s.id), b))
                            .then(Commands.argument("rung", IntegerArgumentType.integer(0, 5)).executes(c -> form(c.getSource().getPlayerOrException(), c, 0))
                                    .then(Commands.argument("family", IntegerArgumentType.integer(0, 2)).executes(c -> form(c.getSource().getPlayerOrException(), c, IntegerArgumentType.getInteger(c, "family")))))));
            l.then(Commands.literal("formrow").requires(AwakenCommands.admin())
                    .then(Commands.argument("species", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Arrays.stream(dev.awaken.living.combat.Forms.Species.values()).map(s -> s.id), b))
                            .executes(c -> formRow(c.getSource().getPlayerOrException(), c, 0))
                            .then(Commands.argument("family", IntegerArgumentType.integer(0, 2)).executes(c -> formRow(c.getSource().getPlayerOrException(), c, IntegerArgumentType.getInteger(c, "family"))))));
            l.then(Commands.literal("formclear").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                int n = 0;
                for (var e : p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(40), m -> m.isNoAi() && dev.awaken.living.combat.Forms.Species.of(m.getType()) != null)) {
                    e.discard();
                    n++;
                }
                return n;
            }));
            // wonders (SPEC 21.11): where the nearest ones are, a walk to one (it generates as you arrive), a plan built here for a look
            l.then(Commands.literal("wonder").requires(AwakenCommands.admin())
                    .then(Commands.literal("list").executes(c -> wonderList(c.getSource().getPlayerOrException())))
                    .then(Commands.literal("goto").then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .suggests((c, b2) -> net.minecraft.commands.SharedSuggestionProvider.suggest(java.util.Arrays.stream(dev.awaken.living.wonder.Wonders.Kind.values()).map(k -> k.id), b2))
                            .executes(c -> wonderGoto(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "kind")))))
                    .then(Commands.literal("build").then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes(c -> wonderBuild(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "kind"), 0)))));
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
            // tours of the first thread (SPEC §27.6): outside the High Gate facing the camp; halfway up the road; at the
            // camp facing the nearest standing gate (the first gate, once it has formed)
            l.then(Commands.literal("way").requires(AwakenCommands.admin())
                    .then(Commands.argument("step", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        return way(p, com.mojang.brigadier.arguments.StringArgumentType.getString(c, "step")) ? 1 : 0;
                    })));
            // the first thread's staged moments (SPEC §27.6): the High Gate's shot again, Ilse's scene now, the camp by night
            l.then(Commands.literal("first").requires(AwakenCommands.admin())
                    .then(Commands.literal("gate").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        if (!way(p, "gate")) return 0;
                        // Hale's line as the errand has it (the gate step's done line)
                        String chain = FirstThread.chainId();
                        var d = chain == null ? null : dev.awaken.living.quest.QuestBook.get(chain);
                        String hale = d == null ? "" : d.steps().stream().filter(s -> s.id().equals("gate")).findFirst()
                                .map(s -> s.done().isEmpty() ? "" : s.done().getFirst()).orElse("");
                        return FirstScenes.gateShot(p, hale, true) ? 1 : 0;
                    }))
                    // ten blocks off Ilse with the letters still yours (the Now line counts them), then five off and her scene
                    .then(Commands.literal("letters").executes(c -> ilseStand(c.getSource(), 10) ? 1 : 0))
                    .then(Commands.literal("ilse").executes(c -> ilseStand(c.getSource(), 5) && FirstScenes.devIlse(c.getSource().getPlayerOrException()) ? 1 : 0))
                    .then(Commands.literal("took").executes(c -> {
                        FirstScenes.ready(c.getSource().getPlayerOrException(), LivingNet.FirstSceneReady.TOOK);
                        return 1;
                    }))
                    .then(Commands.literal("camp").executes(c -> {
                        // south-west of the fire, past the bedrolls, looking across them to the fire and Ilse beyond
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        var fire = dev.awaken.living.wayfarer.Camp.fire(p.level().getServer());
                        if (fire == null) return 0;
                        net.minecraft.core.BlockPos at = dev.awaken.living.wayfarer.Scenes.ground(p.level().getServer().overworld(), fire.offset(-6, 0, 6));
                        double dx = fire.getX() - 1 - at.getX(), dz = fire.getZ() + 1 - at.getZ();
                        p.teleportTo(p.level().getServer().overworld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(),
                                (float) Math.toDegrees(Math.atan2(-dx, dz)), 20F, true);
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
            // tours: stand in a fauna land, face the newest happening, or walk up to it
            l.then(Commands.literal("goto").requires(AwakenCommands.admin())
                    .then(Commands.argument("land", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        ServerLevel level = p.level().getServer().overworld();
                        var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,
                                dev.awaken.Awaken.id("fauna/" + com.mojang.brigadier.arguments.StringArgumentType.getString(c, "land")));
                        var found = level.findClosestBiome3d(b -> b.is(tag), p.blockPosition().atY(64), 6400, 32, 64);
                        if (found == null) {
                            c.getSource().sendFailure(Component.literal("No such land within 6400 blocks."));
                            return 0;
                        }
                        var at = found.getFirst();
                        level.getChunk(at.getX() >> 4, at.getZ() >> 4);
                        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ()) + 1;
                        p.teleportTo(level, at.getX() + 0.5, y, at.getZ() + 0.5, java.util.Set.of(), p.getYRot(), 0F, true);
                        c.getSource().sendSuccess(() -> Component.literal("At " + at.getX() + " " + y + " " + at.getZ()), false);
                        return 1;
                    })));
            l.then(Commands.literal("face").requires(AwakenCommands.admin()).executes(c -> faceHappening(c.getSource().getPlayerOrException(), 0)));
            // tours: a Rolling Storm strike this far ahead of you (seen, lighting the cloud, heard late)
            l.then(Commands.literal("strike").requires(AwakenCommands.admin())
                    .then(Commands.argument("distance", IntegerArgumentType.integer(0, 1000)).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        ServerLevel level = p.level().getServer().overworld();
                        var ahead = p.getLookAngle().multiply(1, 0, 1).normalize().scale(IntegerArgumentType.getInteger(c, "distance"));
                        int x = (int) Math.floor(p.getX() + ahead.x), z = (int) Math.floor(p.getZ() + ahead.z);
                        level.getChunk(x >> 4, z >> 4);
                        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                        dev.awaken.living.pulse.StormCell.strike(level, new net.minecraft.core.BlockPos(x, y, z));
                        return 1;
                    })));
            l.then(Commands.literal("near").requires(AwakenCommands.admin())
                    .then(Commands.argument("distance", IntegerArgumentType.integer(4, 200))
                            .executes(c -> faceHappening(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "distance")))));
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
            l.then(Commands.literal("ledger").requires(AwakenCommands.admin()).executes(c -> {
                var lines = dev.awaken.living.population.Ledger.describe(c.getSource().getPlayerOrException());
                for (String line : lines) c.getSource().sendSuccess(() -> Component.literal(line), false);
                if (lines.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("No populations near (yet)."), false);
                return lines.size();
            }));
            l.then(Commands.literal("camps").requires(AwakenCommands.admin()).executes(c -> {
                var lines = dev.awaken.living.camp.Camps.describe(c.getSource().getPlayerOrException());
                for (String line : lines) c.getSource().sendSuccess(() -> Component.literal(line), false);
                if (lines.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("No camps near."), false);
                return lines.size();
            }));
            l.then(Commands.literal("nest").requires(AwakenCommands.admin())
                    .then(Commands.argument("kind", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5)).executes(c -> {
                                var camp = dev.awaken.living.camp.Camps.place(c.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(c, "kind"), IntegerArgumentType.getInteger(c, "tier"));
                                c.getSource().sendSuccess(() -> Component.literal(camp == null ? "No ground for a camp here." : camp.name() + " at " + camp.pos().toShortString()), false);
                                return camp == null ? 0 : 1;
                            }))));
            // crafts (Phase E): a craft's level outright, for tours and tests ("announce": with its level and perk toasts)
            l.then(Commands.literal("craft").requires(AwakenCommands.admin())
                    .then(Commands.argument("craft", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .then(Commands.argument("level", IntegerArgumentType.integer(1, 50)).executes(c -> setCraft(c, false))
                                    .then(Commands.literal("announce").executes(c -> setCraft(c, true))))));
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

    /** Turns the player toward the newest happening; with a distance, first walks them up to that far from it. */
    private static int faceHappening(ServerPlayer p, int distance) {
        var list = dev.awaken.living.pulse.Pulse.get(p.level().getServer()).active();
        if (list.isEmpty()) return 0;
        var h = list.getLast();
        ServerLevel level = p.level().getServer().overworld();
        double px = p.getX(), py, pz = p.getZ();
        if (distance > 0) {
            double dx = px - h.centre.x, dz = pz - h.centre.z, len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
            px = h.centre.x + dx / len * distance;
            pz = h.centre.z + dz / len * distance;
        }
        // a tour's camera: on a small glass stand over the canopy (high for a view from afar, the Hilltop Test)
        level.getChunk((int) px >> 4, (int) pz >> 4);
        int top = 0;
        for (int ox = -2; ox <= 2; ox++) for (int oz = -2; oz <= 2; oz++)
            top = Math.max(top, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) px + ox, (int) pz + oz));
        py = top + (distance > 0 ? 3 : 20);
        for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++)
            level.setBlock(new net.minecraft.core.BlockPos((int) Math.floor(px) + ox, (int) py - 1, (int) Math.floor(pz) + oz), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(), 3);
        double dx = h.centre.x - px, dy = h.centre.y + 6 - (py + 1.6), dz = h.centre.z - pz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.teleportTo(level, px, py, pz, java.util.Set.of(), yaw, pitch, true);
        return 1;
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

    /** {@code form <species> <rung> [family]}: one creature in that form, 4 blocks ahead, facing you. */
    private static int form(ServerPlayer p, com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, int family) {
        var sp = dev.awaken.living.combat.Forms.Species.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "species"));
        if (sp == null) {
            c.getSource().sendFailure(Component.literal("Unknown species."));
            return 0;
        }
        Vec3 at = ahead(p, 4.0, 0.0);
        dev.awaken.living.combat.Forms.still((ServerLevel) p.level(), sp, IntegerArgumentType.getInteger(c, "rung"), family, at.x, at.y, at.z, p.getYRot() + 180.0F);
        return 1;
    }

    /** {@code formrow <species> [family]}: the species at rungs 0 to 5 in a row 7 blocks ahead, each facing you. */
    private static int formRow(ServerPlayer p, com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, int family) {
        var sp = dev.awaken.living.combat.Forms.Species.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "species"));
        if (sp == null) {
            c.getSource().sendFailure(Component.literal("Unknown species."));
            return 0;
        }
        for (int r = 0; r <= 5; r++) {
            Vec3 at = ahead(p, 7.0, (r - 2.5) * 2.0);
            dev.awaken.living.combat.Forms.still((ServerLevel) p.level(), sp, r, family, at.x, at.y, at.z, p.getYRot() + 180.0F);
        }
        return 6;
    }

    /** A point {@code forward} blocks along the way the player faces and {@code side} to their right, at their feet's height. */
    private static Vec3 ahead(ServerPlayer p, double forward, double side) {
        double a = Math.toRadians(p.getYRot());
        Vec3 f = new Vec3(-Math.sin(a), 0, Math.cos(a)), r = new Vec3(-Math.cos(a), 0, -Math.sin(a));
        return p.position().add(f.scale(forward)).add(r.scale(side));
    }

    /** The wonders of the regions round a point (every 500 blocks out to a radius), nearest first; only of {@code only} when given. */
    private static java.util.List<dev.awaken.living.wonder.Wonders.Site> wonderSites(ServerLevel level, int cx, int cz, int radius, dev.awaken.living.wonder.Wonders.Kind only) {
        RegionMap map = RegionService.map(level.getServer());
        var terrain = dev.awaken.living.wonder.WonderFeature.terrain(level, level.getChunkSource().getGenerator());
        java.util.Set<Long> seen = new java.util.HashSet<>();
        java.util.List<dev.awaken.living.wonder.Wonders.Site> out = new java.util.ArrayList<>();
        for (int dx = -radius; dx <= radius; dx += 500)
            for (int dz = -radius; dz <= radius; dz += 500) {
                var r = map.regionAt(cx + dx, cz + dz);
                if (!seen.add(r.id())) continue;
                var s = dev.awaken.living.wonder.Wonders.siteOf(level.getSeed(), map, r, terrain);
                if (s != null && (only == null || s.kind() == only)) out.add(s);
            }
        out.sort(java.util.Comparator.comparingDouble(s -> (double) (s.x() - cx) * (s.x() - cx) + (double) (s.z() - cz) * (s.z() - cz)));
        return out;
    }

    private static int wonderList(ServerPlayer p) {
        for (var s : wonderSites((ServerLevel) p.level(), p.getBlockX(), p.getBlockZ(), 3000, null).stream().limit(8).toList()) {
            double d = Math.sqrt((double) (s.x() - p.getBlockX()) * (s.x() - p.getBlockX()) + (double) (s.z() - p.getBlockZ()) * (s.z() - p.getBlockZ()));
            p.sendSystemMessage(Component.literal(s.kind().display + " in " + s.regionName() + " at " + s.x() + ", " + s.z() + " (" + Math.round(d) + " m)"));
        }
        return 1;
    }

    /** Walks you to 46 blocks south of the nearest wonder of a kind, facing it, on the ground there; the chunks generate as you arrive. */
    private static int wonderGoto(ServerPlayer p, String kindId) {
        var kind = dev.awaken.living.wonder.Wonders.Kind.byId(kindId);
        if (kind == null) {
            p.sendSystemMessage(Component.literal("No wonder called " + kindId + "."));
            return 0;
        }
        ServerLevel level = (ServerLevel) p.level();
        var sites = wonderSites(level, p.getBlockX(), p.getBlockZ(), 9000, kind);
        if (sites.isEmpty()) {
            p.sendSystemMessage(Component.literal("None of that kind within 9,000 blocks."));
            return 0;
        }
        var s = sites.getFirst();
        int x = s.x(), z = s.z() + 46;
        var terrain = dev.awaken.living.wonder.WonderFeature.terrain(level, level.getChunkSource().getGenerator());
        p.teleportTo(level, x + 0.5, terrain.ground(x, z) + 2, z + 0.5, java.util.Set.of(), 180F, 8F, true);
        p.sendSystemMessage(Component.literal(s.kind().display + " at " + s.x() + ", " + s.z() + " in " + s.regionName()));
        return 1;
    }

    /** Builds a wonder's plan 40 blocks ahead on a flat floor of stone (a look at the plan itself, family 0 green, 1 sun, 2 frost). */
    private static int wonderBuild(ServerPlayer p, String kindId, int fam) {
        var kind = dev.awaken.living.wonder.Wonders.Kind.byId(kindId);
        if (kind == null) return 0;
        ServerLevel level = (ServerLevel) p.level();
        Vec3 at = ahead(p, 46, 0);
        int gy = p.getBlockY() - 1;
        var plan = dev.awaken.living.wonder.Wonders.planOf(kind, 7L, fam);
        for (int dx = -40; dx <= 40; dx++) for (int dz = -40; dz <= 40; dz++) level.setBlockAndUpdate(new net.minecraft.core.BlockPos((int) at.x + dx, gy, (int) at.z + dz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState());
        for (var e : plan.blocks().long2ObjectEntrySet()) {
            long k = e.getLongKey();
            level.setBlockAndUpdate(new net.minecraft.core.BlockPos((int) at.x + net.minecraft.core.BlockPos.getX(k), gy + net.minecraft.core.BlockPos.getY(k), (int) at.z + net.minecraft.core.BlockPos.getZ(k)), e.getValue());
        }
        return plan.blocks().size();
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

    private static int setCraft(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, boolean announce)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var craft = dev.awaken.living.craft.Craft.byId(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "craft"));
        if (craft == null) {
            c.getSource().sendFailure(Component.literal("Crafts: hunting, foraging, mining, fishing, smithing, cooking."));
            return 0;
        }
        dev.awaken.living.craft.Crafts.setLevel(c.getSource().getPlayerOrException(), craft, IntegerArgumentType.getInteger(c, "level"), announce);
        return 1;
    }

    /** Dev and tours: the errand at Ilse's step, and the player {@code dist} blocks off her, facing her, the fire beside. */
    private static boolean ilseStand(CommandSourceStack src, int dist) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        String chain = FirstThread.chainId();
        var ilse = dev.awaken.living.wayfarer.Scenes.resident(p.level().getServer(), "ilse");
        if (chain == null || ilse == null || !dev.awaken.living.quest.QuestEngine.devStep(p, chain, "ilse")) {
            src.sendFailure(Component.literal("No Ilse near the camp yet: /awaken living camp first, then wait a few seconds."));
            return false;
        }
        var fire = dev.awaken.living.wayfarer.Camp.fire(p.level().getServer());
        // from the side away from the fire, so she and the fire are both before you
        Vec3 away = fire == null ? ilse.getLookAngle() : ilse.position().subtract(Vec3.atCenterOf(fire));
        away = away.multiply(1, 0, 1).lengthSqr() < 1e-3 ? new Vec3(1, 0, 0) : away.multiply(1, 0, 1).normalize();
        // the first angle with nothing between you and her (the Lumen trunks are wide)
        var level = p.level().getServer().overworld();
        net.minecraft.core.BlockPos at = null;
        for (int turn : new int[]{35, -35, 0, 70, -70, 110, -110, 180}) {
            Vec3 from = ilse.position().add(away.yRot((float) Math.toRadians(turn)).scale(dist));
            net.minecraft.core.BlockPos q = dev.awaken.living.wayfarer.Scenes.ground(level, net.minecraft.core.BlockPos.containing(from));
            if (at == null) at = q;
            var seen = level.clip(new net.minecraft.world.level.ClipContext(Vec3.atCenterOf(q).add(0, 1.1, 0), ilse.getEyePosition(),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
            if (seen.getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
                at = q;
                break;
            }
        }
        double dx = ilse.getX() - (at.getX() + 0.5), dz = ilse.getZ() - (at.getZ() + 0.5);
        p.teleportTo(p.level().getServer().overworld(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, java.util.Set.of(),
                (float) Math.toDegrees(Math.atan2(-dx, dz)), 10F, true);
        return true;
    }

    /** Stands the player for a tour of the first thread; false if the world has no such place. */
    static boolean way(ServerPlayer p, String step) {
        var server = p.level().getServer();
        ServerLevel level = server.overworld();
        net.minecraft.core.BlockPos gate = dev.awaken.core.api.CityHooks.spot(level, "high_gate_yard");
        net.minecraft.core.BlockPos camp = dev.awaken.living.wayfarer.Scenes.marker(server, 0, "camp");
        if (gate == null || camp == null) return false;
        net.minecraft.core.BlockPos at, look;
        float pitch = 4F;
        switch (step) {
            case "gate" -> {
                at = gate.offset((camp.getX() - gate.getX()) / 12, 0, (camp.getZ() - gate.getZ()) / 12);
                look = camp;
            }
            case "road" -> {
                // two thirds of the way: past the city's reserve, where the Commons' caption shows
                at = new net.minecraft.core.BlockPos(gate.getX() + (camp.getX() - gate.getX()) * 2 / 3, 0, gate.getZ() + (camp.getZ() - gate.getZ()) * 2 / 3);
                look = camp;
            }
            case "rise" -> {
                at = camp.offset(0, 0, 6);
                look = camp.offset(0, 0, -400);
                pitch = -18F; // over the canopy, to the pillar
                for (var g : dev.awaken.core.api.GateHooks.near(level, camp, 900)) {
                    if (java.util.Set.of("BUILDING", "READY", "ACTIVE", "OPEN").contains(g.state())) look = g.pos();
                }
            }
            default -> {
                return false;
            }
        }
        level.getChunk(at.getX() >> 4, at.getZ() >> 4);
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        double dx = look.getX() - at.getX(), dz = look.getZ() - at.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        p.teleportTo(level, at.getX() + 0.5, y, at.getZ() + 0.5, java.util.Set.of(), yaw, pitch, true);
        return true;
    }
}
