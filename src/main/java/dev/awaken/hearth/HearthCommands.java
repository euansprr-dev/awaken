package dev.awaken.hearth;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import dev.awaken.hearth.echo.BindMath;
import dev.awaken.hearth.echo.Duty;
import dev.awaken.hearth.echo.EchoRecord;
import dev.awaken.hearth.echo.EchoRole;
import dev.awaken.hearth.echo.Echoes;
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

/** /awaken hearth info|echoes and the admin tools /awaken admin trial &lt;rank&gt|penalty, /awaken admin town arrive &lt;npc&gt;. */
final class HearthCommands {
    private HearthCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            root.then(Commands.literal("hearth")
                    .then(Commands.literal("info").executes(HearthCommands::info))
                    .then(Commands.literal("echoes").executes(HearthCommands::echoes)));
            var admin = Commands.literal("admin").requires(AwakenCommands.admin());
            admin.then(Commands.literal("trial").then(Commands.argument("rank", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("D", "C", "B", "A", "S", "penalty"), b))
                    .executes(HearthCommands::trial)));
            admin.then(Commands.literal("town").then(Commands.literal("arrive").then(Commands.argument("npc", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(Townsfolk.all().keySet(), b))
                    .executes(HearthCommands::arrive))));
            admin.then(Commands.literal("hearth").then(Commands.literal("scan").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                Optional<HearthRecord> h = HearthService.hearthOf(sp);
                if (h.isEmpty()) {
                    c.getSource().sendFailure(Component.literal("You have no Hearth."));
                    return 0;
                }
                List<Room> rooms = HearthService.scanNow(HearthService.levelOf(sp.level().getServer(), h.get()), h.get());
                c.getSource().sendSuccess(() -> Component.literal("Scanned: " + rooms.size() + " enclosed rooms"), false);
                return rooms.size();
            })));
            root.then(admin);
        });
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Optional<HearthRecord> ho = HearthService.hearthOf(sp);
        if (ho.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("You have no Hearth. Craft a Hearth Anchor from a Lord's Heart and place it where home should be."), false);
            return 0;
        }
        HearthRecord h = ho.get();
        List<String> lines = new ArrayList<>();
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
        Map<EchoRole, Integer> pads = new java.util.EnumMap<>(EchoRole.class);
        for (EchoRole r : h.pads.values()) pads.merge(r, 1, Integer::sum);
        lines.add("Role pads: " + (pads.isEmpty() ? "none" : pads.toString().toLowerCase(Locale.ROOT)));
        lines.add("Trophies in a Hall: " + h.hallTrophies.size() + " different bosses");
        lines.add("Echoes have hauled " + h.itemsHauled + " items, harvested " + h.cropsHarvested + " crops, brought up " + h.oresBrought + " ore");
        if (Breaks.active(h)) lines.add("A break is happening near home");
        for (String l : lines) c.getSource().sendSuccess(() -> Component.literal(l), false);
        return 1;
    }

    private static int echoes(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        HearthPlayer hp = HearthService.player(sp);
        int spi = Progression.stat(sp, Stat.SPI);
        int pads = HearthService.hearthOf(sp).map(h -> h.pads.size()).orElse(0);
        c.getSource().sendSuccess(() -> Component.literal("Echoes " + hp.echoes.size() + " / " + BindMath.totalCap(spi, pads)
                + " · field cap " + BindMath.fieldCap(spi) + " · role pads " + pads + " · army " + (hp.fieldOut ? "called" : "dismissed")), false);
        Map<UUID, Duty.Assignment> duties = HearthService.hearthOf(sp).map(h -> Duty.assignments(hp, h)).orElse(Map.of());
        long now = sp.level().getGameTime();
        for (EchoRecord e : hp.echoes) {
            Duty.Assignment a = duties.get(e.id());
            String post = e.field() ? "marches with you" : a != null ? a.role().label + " at " + a.pad().toShortString() : "resting at home";
            String state = e.downUntil() > now ? " · reforming " + (e.downUntil() - now) / 20 + "s" : Echoes.isOut(sp.level().getServer(), e.id()) ? " · out" : "";
            c.getSource().sendSuccess(() -> Component.literal("• " + e.name() + " (" + e.origin().toLowerCase(Locale.ROOT) + ") Lv " + e.level()
                    + " · " + post + state), false);
        }
        return hp.echoes.size();
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
        Optional<HearthRecord> h = HearthService.hearthOf(sp);
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
