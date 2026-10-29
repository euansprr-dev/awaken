package dev.awaken.hearth;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import dev.awaken.hearth.player.HearthPlayer;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.town.Town;
import dev.awaken.hearth.town.Townsfolk;
import dev.awaken.hearth.trial.Trials;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.progression.Stat;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * /awaken hearth info|guide and the admin tools /awaken admin trial &lt;rank&gt;|penalty, /awaken admin town arrive
 * &lt;npc&gt;, /awaken admin hearth scan, /awaken admin hearth cottage [reset|offer|hurry|view outside|inside|side].
 */
final class HearthCommands {
    private HearthCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            root.then(Commands.literal("hearth")
                    .then(Commands.literal("info").executes(HearthCommands::info))
                    .then(Commands.literal("guide").executes(HearthCommands::guide))
                    // sharing a Hearth (SPEC "Together"): every player may use these
                    .then(Commands.literal("members").executes(HearthCommands::members))
                    .then(Commands.literal("invite").then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                            .executes(HearthCommands::invite)))
                    .then(Commands.literal("join").then(Commands.argument("owner", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(invitersOf(c.getSource()), b))
                            .executes(HearthCommands::join)))
                    .then(Commands.literal("leave").executes(c -> leave(c, null))
                            .then(Commands.argument("owner", StringArgumentType.word())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(sharedOwners(c.getSource()), b))
                                    .executes(c -> leave(c, StringArgumentType.getString(c, "owner")))))
                    .then(Commands.literal("remove").then(Commands.argument("member", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(memberNames(c.getSource()), b))
                            .executes(HearthCommands::remove))));
            var admin = Commands.literal("admin").requires(AwakenCommands.admin());
            admin.then(Commands.literal("trial").then(Commands.argument("rank", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("D", "C", "B", "A", "S", "penalty"), b))
                    .executes(HearthCommands::trial)));
            admin.then(Commands.literal("town").then(Commands.literal("arrive").then(Commands.argument("npc", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Townsfolk.all().keySet(), b))
                    .executes(HearthCommands::arrive))));
            // the carried flame (tours and tests): give an Ember, set it down here, raise the fire, read the room you're in
            admin.then(Commands.literal("ember")
                    .then(Commands.literal("give").executes(c -> {
                        ServerPlayer sp = c.getSource().getPlayerOrException();
                        boolean given = Embers.give(sp);
                        c.getSource().sendSuccess(() -> Component.literal(given ? "Gave a Hearth Ember" : "No Ember: you have one, or a Hearth"), false);
                        return given ? 1 : 0;
                    }))
                    .then(Commands.literal("camp").executes(c -> {
                        // Ilse hands the coal over as a talk would (the tour's "Ilse gives the Ember")
                        ServerPlayer sp = c.getSource().getPlayerOrException();
                        var near = sp.level().getEntitiesOfClass(dev.awaken.living.wayfarer.Wayfarer.class, sp.getBoundingBox().inflate(32),
                                w -> w.who().equals("ilse"));
                        near.sort(java.util.Comparator.comparingDouble(w -> w.distanceToSqr(sp)));
                        var inv = sp.getInventory();
                        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(HearthContent.EMBER_ITEM.get())) inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                        String who = near.isEmpty() ? "ilse" : near.getFirst().who();
                        if (!near.isEmpty()) near.getFirst().getLookControl().setLookAt(sp);
                        boolean given = Embers.campGives(sp, who, true);
                        if (given) {
                            // into the hand, the way it's carried
                            for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(HearthContent.EMBER_ITEM.get())) inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                            sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(HearthContent.EMBER_ITEM.get()));
                            sp.inventoryMenu.sendAllDataToRemote(); // give() sent its own slot packet: resync after moving it
                        }
                        return given ? 1 : 0;
                    }))
                    .then(Commands.literal("set").executes(c -> {
                        // three blocks ahead, on the ground there (as if used on it)
                        ServerPlayer sp = c.getSource().getPlayerOrException();
                        ServerLevel level = sp.level();
                        var ahead = sp.blockPosition().relative(sp.getDirection(), 3);
                        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ahead.getX(), ahead.getZ());
                        var ground = new net.minecraft.core.BlockPos(ahead.getX(), y - 1, ahead.getZ());
                        Optional<String> why = Embers.refusal(sp, level, ground, ground.above());
                        if (why.isPresent()) {
                            c.getSource().sendFailure(Component.literal(why.get()));
                            return 0;
                        }
                        var inv = sp.getInventory();
                        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(HearthContent.EMBER_ITEM.get())) inv.getItem(i).shrink(1);
                        HearthRecord h = Embers.found(sp, level, ground.above());
                        c.getSource().sendSuccess(() -> Component.literal("The coal took: " + h.name), false);
                        return 1;
                    }))
                    .then(Commands.literal("raise").executes(c -> {
                        ServerPlayer sp = c.getSource().getPlayerOrException();
                        Optional<HearthRecord> h = HearthService.hearthOf(sp).filter(HearthRecord::hearthfire);
                        if (h.isEmpty()) {
                            c.getSource().sendFailure(Component.literal("You have no Hearthfire."));
                            return 0;
                        }
                        Embers.raise(sp, HearthService.levelOf(sp.level().getServer(), h.get()), h.get());
                        return 1;
                    })));
            admin.then(Commands.literal("hearth").then(Commands.literal("read").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                HearthService.homeOf(sp).ifPresent(h -> HearthService.scanNow(HearthService.levelOf(sp.level().getServer(), h), h));
                Optional<Room> r = HearthService.roomHere(sp);
                if (r.isEmpty()) {
                    c.getSource().sendFailure(Component.literal("You're not in a room the Hearth has read."));
                    return 0;
                }
                var g = r.get().reading();
                c.getSource().sendSuccess(() -> Component.literal(g.name() + " · " + g.grade().label + " · closed " + g.closed() + " light " + g.light()
                        + " warmth " + g.warmth() + " room " + g.space() + " care " + g.care() + " detail " + g.detail()
                        + (g.lift().isEmpty() ? "" : " · lift: " + g.lift()) + " · " + r.get().types()), false);
                return g.grade().ordinal() + 1;
            })).then(Commands.literal("rested").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                Optional<HearthRecord> h = HearthService.homeOf(sp);
                if (h.isEmpty()) return 0;
                Rested.rest(sp, sp.level(), h.get());
                return 1;
            })).then(Commands.literal("scan").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                Optional<HearthRecord> h = HearthService.homeOf(sp);
                if (h.isEmpty()) {
                    c.getSource().sendFailure(Component.literal("You have no Hearth."));
                    return 0;
                }
                List<Room> rooms = HearthService.scanNow(HearthService.levelOf(sp.level().getServer(), h.get()), h.get());
                c.getSource().sendSuccess(() -> Component.literal("Scanned: " + rooms.size() + " enclosed rooms"), false);
                for (var m : h.get().misses) {
                    c.getSource().sendSuccess(() -> Component.literal("Not a room: " + m.furniture().the + " at " + m.seed().toShortString()
                            + " · " + m.leak().name().toLowerCase(Locale.ROOT) + " at " + m.at().toShortString()), false);
                }
                return rooms.size();
            })).then(Commands.literal("cottage").executes(HearthCommands::cottage)
                    .then(Commands.literal("reset").executes(HearthCommands::cottageReset))
                    .then(Commands.literal("hurry").executes(c -> {
                        // as if the rest of the day had passed: the build catches up over the next few ticks
                        ServerPlayer sp = c.getSource().getPlayerOrException();
                        boolean ok = HearthService.hearthOf(sp).map(h -> dev.awaken.hearth.guide.Cottage.hurry(h,
                                dev.awaken.hearth.guide.Cottage.DURATION)).orElse(false);
                        if (ok) c.getSource().sendSuccess(() -> Component.literal("Oren finishes the cottage"), false);
                        else c.getSource().sendFailure(Component.literal("Oren isn't building a cottage here."));
                        return ok ? 1 : 0;
                    }))
                    .then(Commands.literal("offer").executes(c -> {
                        ServerPlayer sp = c.getSource().getPlayerOrException();
                        boolean sent = dev.awaken.hearth.guide.Cottage.offer(sp);
                        c.getSource().sendSuccess(() -> Component.literal(sent ? "Offered (Y raises it)" : "Already offered once (use reset)"), false);
                        return sent ? 1 : 0;
                    }))
                    .then(Commands.literal("view").then(Commands.argument("where", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("outside", "inside", "side"), b))
                            .executes(HearthCommands::cottageView)))));
            // (W6) the people who live with you, the staged village, the tiers (brigadier merges the two "hearth" nodes)
            var people = Commands.literal("hearth");
            for (var t : dev.awaken.hearth.settle.SettleCommands.trees()) people.then(t);
            admin.then(people);
            root.then(admin);
        });
    }

    // ---- sharing ----------------------------------------------------------------------------------------------------

    private static int members(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        List<HearthRecord> mine = HearthData.get(sp.level().getServer()).hearthsOf(sp.getUUID());
        if (mine.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("You share no Hearth yet. Place an Anchor, or use a friend's Anchor to ask them."), false);
            return 0;
        }
        for (HearthRecord h : mine) for (String line : Household.describe(sp.level().getServer(), h)) c.getSource().sendSuccess(() -> Component.literal(line), false);
        return mine.size();
    }

    private static int invite(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        ServerPlayer who = net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player");
        Optional<String> refused = Household.invite(sp, who);
        if (refused.isPresent()) {
            c.getSource().sendFailure(Component.literal(refused.get()));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Invited " + who.getGameProfile().name() + ". They answer with Y, or /awaken hearth join " + sp.getGameProfile().name()), false);
        return 1;
    }

    private static int join(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "owner");
        Optional<HearthRecord> h = byOwnerName(sp, name);
        if (h.isEmpty() || !Household.invited(sp.level().getServer(), sp.getUUID(), h.get().owner)) {
            c.getSource().sendFailure(Component.literal("No open invitation from " + name + "."));
            return 0;
        }
        return Household.accept(sp, h.get().owner) ? 1 : 0;
    }

    private static int leave(CommandContext<CommandSourceStack> c, String ownerName) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        List<HearthRecord> shared = HearthData.get(sp.level().getServer()).memberOf(sp.getUUID());
        if (ownerName != null) shared = shared.stream().filter(h -> h.ownerName.equalsIgnoreCase(ownerName)).toList();
        if (shared.isEmpty()) {
            c.getSource().sendFailure(Component.literal(ownerName == null ? "You don't share anyone's Hearth." : "You don't share " + ownerName + "'s Hearth."));
            return 0;
        }
        if (shared.size() > 1) {
            c.getSource().sendFailure(Component.literal("You share several Hearths: /awaken hearth leave <owner>"));
            return 0;
        }
        HearthRecord h = shared.get(0);
        Household.leave(sp.level().getServer(), h, sp.getUUID(), false);
        c.getSource().sendSuccess(() -> Component.literal("You no longer share " + h.ownerName + "'s Hearth."), false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "member");
        Optional<HearthRecord> ho = HearthService.hearthOf(sp);
        if (ho.isEmpty()) {
            c.getSource().sendFailure(Component.literal("Only a Hearth's owner can take it back."));
            return 0;
        }
        HearthRecord h = ho.get();
        UUID who = null;
        for (var e : h.members.entrySet()) if (name.equalsIgnoreCase(e.getValue()) || name.equalsIgnoreCase(e.getKey().toString())) who = e.getKey();
        if (who == null || !Household.leave(sp.level().getServer(), h, who, true)) {
            c.getSource().sendFailure(Component.literal(name + " doesn't share your Hearth."));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.literal(name + " no longer shares your Hearth."), false);
        return 1;
    }

    private static Optional<HearthRecord> byOwnerName(ServerPlayer sp, String name) {
        for (HearthRecord h : HearthData.get(sp.level().getServer()).all()) if (h.ownerName.equalsIgnoreCase(name)) return Optional.of(h);
        return Optional.empty();
    }

    private static List<String> invitersOf(CommandSourceStack src) {
        List<String> out = new ArrayList<>();
        if (!(src.getEntity() instanceof ServerPlayer sp)) return out;
        for (HearthRecord h : HearthData.get(sp.level().getServer()).all()) {
            if (Household.invited(sp.level().getServer(), sp.getUUID(), h.owner)) out.add(h.ownerName);
        }
        return out;
    }

    private static List<String> sharedOwners(CommandSourceStack src) {
        List<String> out = new ArrayList<>();
        if (src.getEntity() instanceof ServerPlayer sp) for (HearthRecord h : HearthData.get(sp.level().getServer()).memberOf(sp.getUUID())) out.add(h.ownerName);
        return out;
    }

    private static List<String> memberNames(CommandSourceStack src) {
        List<String> out = new ArrayList<>();
        if (src.getEntity() instanceof ServerPlayer sp) HearthService.hearthOf(sp).ifPresent(h -> out.addAll(h.members.values()));
        return out;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Optional<HearthRecord> ho = HearthService.homeOf(sp);
        if (ho.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("You have no Hearth. Craft a Hearth Anchor from a Lord's Heart and place it where home should be."), false);
            return 0;
        }
        HearthRecord h = ho.get();
        List<String> lines = new ArrayList<>();
        if (!h.owner.equals(sp.getUUID())) lines.add("You share " + h.ownerName + "'s Hearth");
        lines.add("Hearth · Tier " + Upgrades.roman(h.tier) + " at " + h.pos.toShortString() + " · radius " + h.radius() + " · Forge " + Upgrades.roman(h.forgeTier));
        if (h.rooms.isEmpty()) lines.add("Rooms: none recognised yet");
        for (Room r : h.rooms) {
            List<String> types = new ArrayList<>();
            r.types().forEach(t -> types.add(t.label));
            lines.add("Room " + r.min().toShortString() + "…" + r.max().toShortString() + " (" + r.volume() + " cells): "
                    + (types.isEmpty() ? "just a room" : String.join(", ", types)));
        }
        List<String> town = new ArrayList<>();
        for (String id : h.arrived) town.add(Townsfolk.displayName(id));
        lines.add("Town: " + (town.isEmpty() ? "nobody yet" : String.join(", ", town)));
        lines.add("Trophies in a Hall: " + h.hallTrophies.size() + " different bosses");
        lines.add("Hauled " + h.itemsHauled + " items, harvested " + h.cropsHarvested + " crops, brought up " + h.oresBrought + " ore");
        if (Breaks.active(h)) lines.add("A break is happening near home");
        for (String l : lines) c.getSource().sendSuccess(() -> Component.literal(l), false);
        return 1;
    }

    /** Opens "Your Hearth" as if the Anchor was used (from anywhere). */
    private static int guide(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Optional<HearthRecord> h = HearthService.homeOf(sp);
        if (h.isEmpty()) {
            c.getSource().sendFailure(Component.literal("You have no Hearth. Craft a Hearth Anchor from a Lord's Heart and place it where home should be."));
            return 0;
        }
        dev.awaken.hearth.guide.Guide.open(sp, HearthService.levelOf(sp.level().getServer(), h.get()), h.get());
        return 1;
    }

    private static int cottage(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        boolean ok = dev.awaken.hearth.guide.Cottage.start(sp);
        if (ok) c.getSource().sendSuccess(() -> Component.literal("Oren is looking for a spot"), false);
        else c.getSource().sendFailure(Component.literal("No Hearth, already built once (use reset), or already building."));
        return ok ? 1 : 0;
    }

    private static int cottageReset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        HearthService.player(sp).tips.removeIf(t -> t.startsWith("cottage_"));
        HearthService.hearthOf(sp).ifPresent(h -> {
            h.cottage = null;
            h.cottageBuild = null;
        });
        c.getSource().sendSuccess(() -> Component.literal("Oren's cottage can be offered and raised again"), false);
        return 1;
    }

    /** Stands you where the cottage shows: outside at the front corner, inside by the door, or at the chimney side. */
    private static int cottageView(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        var site = dev.awaken.hearth.guide.Cottage.lastSite(sp.getUUID());
        if (site == null) {
            c.getSource().sendFailure(Component.literal("No cottage raised this session."));
            return 0;
        }
        String where = StringArgumentType.getString(c, "where");
        int[] from = switch (where) {
            case "inside" -> new int[]{6, 1, 1};
            case "side" -> new int[]{17, 3, 10};
            default -> new int[]{-5, 2, -9};
        };
        int[] look = where.equals("inside") ? new int[]{2, 1, 7} : new int[]{4, 3, 4};
        var origin = site.origin().atY(site.floorY());
        var f = dev.awaken.hearth.guide.CottagePlan.at(origin, site.rot(), from[0], from[1], from[2]);
        var l = dev.awaken.hearth.guide.CottagePlan.at(origin, site.rot(), look[0], look[1], look[2]);
        double fx = f.getX() + 0.5, fy = f.getY(), fz = f.getZ() + 0.5;
        if (!where.equals("inside")) {
            fy = Math.max(fy, sp.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, f.getX(), f.getZ()));
        }
        double dx = l.getX() + 0.5 - fx, dy = l.getY() + 0.5 - (fy + 1.62), dz = l.getZ() + 0.5 - fz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        sp.teleportTo((ServerLevel) sp.level(), fx, fy, fz, java.util.Set.of(), yaw, pitch, true);
        return 1;
    }

    private static int trial(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String arg = StringArgumentType.getString(c, "rank").toUpperCase(Locale.ROOT);
        if (arg.equals("PENALTY")) {
            boolean ok = Trials.start(sp, Trials.Kind.PENALTY, Progression.rank(sp), true);
            return ok ? 1 : 0;
        }
        Rank target;
        try {
            target = Rank.valueOf(arg);
        } catch (IllegalArgumentException ex) {
            c.getSource().sendFailure(Component.literal("Unknown rank " + arg + ". Use D, C, B, A, S or penalty."));
            return 0;
        }
        if (target == Rank.E || target == Rank.SOVEREIGN) {
            c.getSource().sendFailure(Component.literal("There is no trial for " + target.label + "-rank here."));
            return 0;
        }
        boolean real = target == Progression.rank(sp).next() && Progression.profile(sp).reassessmentReady();
        boolean ok = Trials.start(sp, Trials.Kind.forTarget(target), target, !real);
        if (ok) c.getSource().sendSuccess(() -> Component.literal((real ? "Reassessment" : "Practice trial") + " for " + target.label + "-rank started"), true);
        return ok ? 1 : 0;
    }

    private static int arrive(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        String npc = StringArgumentType.getString(c, "npc").toLowerCase(Locale.ROOT);
        String id = Townsfolk.get(npc).isPresent() ? npc : Townsfolk.all().keySet().stream().filter(k -> Townsfolk.skin(k).equals(npc)).findFirst().orElse(null);
        Optional<HearthRecord> h = HearthService.homeOf(sp);
        if (id == null || h.isEmpty()) {
            c.getSource().sendFailure(Component.literal(h.isEmpty() ? "You have no Hearth." : "Nobody called " + npc + "."));
            return 0;
        }
        ServerLevel level = HearthService.levelOf(sp.level().getServer(), h.get());
        Town.forceArrive(sp, level, h.get(), id);
        c.getSource().sendSuccess(() -> Component.literal(Townsfolk.displayName(id) + " arrived"), true);
        return 1;
    }
}
