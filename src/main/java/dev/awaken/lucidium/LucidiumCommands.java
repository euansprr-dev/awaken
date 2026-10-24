package dev.awaken.lucidium;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.PlayDays;
import dev.awaken.lucidium.data.Dreamer;
import dev.awaken.lucidium.data.Snapshot;
import dev.awaken.lucidium.data.Vision;
import dev.awaken.lucidium.dream.Dreams;
import dev.awaken.lucidium.dream.Planner;
import dev.awaken.lucidium.dream.Visions;
import dev.awaken.lucidium.journal.DayJournal;
import dev.awaken.lucidium.plan.DreamPlan;
import dev.awaken.lucidium.plan.DreamPlanner;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/** /awaken dream now|plan|journal|wake (+ admin: capture, lucidity, replay, vision). */
final class LucidiumCommands {
    private LucidiumCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var dream = Commands.literal("dream");
            dream.then(Commands.literal("now").requires(AwakenCommands.admin()).executes(c -> now(c.getSource())));
            dream.then(Commands.literal("plan").requires(AwakenCommands.admin()).executes(c -> plan(c.getSource())));
            dream.then(Commands.literal("journal").executes(c -> journal(c.getSource())));
            dream.then(Commands.literal("wake").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                if (!Dreams.isDreaming(sp)) {
                    c.getSource().sendFailure(Component.literal("You're awake."));
                    return 0;
                }
                Dreams.wake(sp, Dreams.Wake.COMMAND);
                return 1;
            }));
            dream.then(Commands.literal("capture").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                Optional<Snapshot> s = DayJournal.capture(sp, Snapshot.Kind.MANUAL, sp.blockPosition(), "a place you chose to remember");
                c.getSource().sendSuccess(() -> Component.literal(s.map(x -> "Remembered: " + x.detail() + " (" + x.id() + ")")
                        .orElse("Nothing captured (empty, unloaded, or someone else's base).")), false);
                return s.isPresent() ? 1 : 0;
            }));
            dream.then(Commands.literal("lucidity").requires(AwakenCommands.admin())
                    .then(Commands.argument("value", FloatArgumentType.floatArg(0, 100)).executes(c -> {
                        ServerPlayer sp = c.getSource().getPlayerOrException();
                        if (!Dreams.isDreaming(sp)) return 0;
                        Dreams.setLucidity(sp, FloatArgumentType.getFloat(c, "value"));
                        return 1;
                    })));
            dream.then(Commands.literal("replay").requires(AwakenCommands.admin())
                    .then(Commands.argument("index", IntegerArgumentType.integer(0)).executes(c ->
                            Lucidium.replay(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "index")) ? 1 : 0)));
            dream.then(Commands.literal("vision").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                Visions.prepare(sp, true);
                Planner.invalidate(sp);
                c.getSource().sendSuccess(() -> Component.literal("Preparing a vision (it loads in the background)."), false);
                return 1;
            }));
            root.then(dream);
        });
    }

    private static int now(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        if (Dreams.isDreaming(sp)) {
            src.sendFailure(Component.literal("Already dreaming."));
            return 0;
        }
        if (Dreams.dreamLevel(src.getServer()) == null) {
            src.sendFailure(Component.literal("The Lucidium dimension is missing from this world."));
            return 0;
        }
        Optional<Planner.Prepared> prepared = Planner.take(sp);
        if (prepared.isEmpty()) {
            Dreamer d = Dreams.dreamer(sp);
            if (d.snapshots.isEmpty()) DayJournal.capture(sp, Snapshot.Kind.MANUAL, sp.blockPosition(), "where you stood when you fell asleep");
            Optional<DreamPlan> plan = DreamPlanner.plan(Planner.input(sp));
            if (plan.isEmpty()) {
                src.sendFailure(Component.literal("Nothing to dream of yet (the journal is empty)."));
                return 0;
            }
            prepared = Optional.of(Planner.buildNow(src.getServer(), sp.getUUID(), plan.get()));
        }
        boolean ok = Dreams.enter(sp, prepared.get(), Dreams.Entry.awake()) != null;
        if (!ok) {
            Planner.discard(src.getServer(), prepared.get());
            src.sendFailure(Component.literal("Couldn't enter the dream."));
        }
        return ok ? 1 : 0;
    }

    private static int plan(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        Optional<Planner.Prepared> ready = Planner.ready(sp);
        DreamPlan plan = ready.map(Planner.Prepared::plan).orElse(null);
        String status = ready.isPresent() ? "ready" : Planner.building(sp) ? "building" : "not built (preview)";
        if (plan == null) plan = DreamPlanner.plan(Planner.input(sp)).orElse(null);
        if (plan == null) {
            src.sendSuccess(() -> Component.literal("No dream tonight: nothing in the Day Journal yet. You'd sleep deeply."), false);
            return 0;
        }
        DreamPlan p = plan;
        src.sendSuccess(() -> Component.literal("Tonight's dream (" + status + "): " + p.summary()).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (int i = 0; i < p.nodes().size(); i++) {
            DreamPlan.Node n = p.nodes().get(i);
            int idx = i;
            src.sendSuccess(() -> Component.literal(String.format("  %d. [%s] %s  (%d, %d, %d)%s%s", idx, n.kind(), n.label(),
                    n.x(), n.y(), n.z(), n.rot() != 0 ? " turned " + n.rot() * 90 : "", n.flip() ? " upside down" : "")), false);
        }
        StringBuilder links = new StringBuilder("  links:");
        for (DreamPlan.Edge e : p.edges()) links.append(' ').append(e.a()).append(e.kind().equals(DreamPlan.PORTAL) ? "=door=" : e.kind().equals(DreamPlan.STAIR) ? "~stair~" : "--").append(e.b());
        src.sendSuccess(() -> Component.literal(links.toString()), false);
        src.sendSuccess(() -> Component.literal("  signs: " + String.join(", ", p.signs()) + " · mood " + p.mood() + " (colours +" + p.hueShift() + ")"
                + " · " + p.durationTicks() / 1200.0 + " min" + (p.intention().isEmpty() ? "" : " · intention " + p.intention())), false);
        return 1;
    }

    private static int journal(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        Dreamer d = Dreams.dreamer(sp);
        long day = PlayDays.current(src.getServer());
        d.rollDay(day);
        src.sendSuccess(() -> Component.literal("Day Journal: " + d.snapshots.size() + "/" + Dreamer.KEEP + " kept, "
                + d.todayCount + "/" + Dreamer.PER_DAY + " today · Recall " + d.recall + " · dreams " + d.dreams).withStyle(ChatFormatting.LIGHT_PURPLE), false);
        for (int i = Math.max(0, d.snapshots.size() - 20); i < d.snapshots.size(); i++) {
            Snapshot s = d.snapshots.get(i);
            src.sendSuccess(() -> Component.literal("  day " + s.day() + " · " + s.kind().name().toLowerCase() + " · " + s.label()
                    + (s.detail().isEmpty() ? "" : " — " + s.detail())), false);
        }
        for (Vision v : d.visions) {
            if (!v.shown()) continue;
            src.sendSuccess(() -> Component.literal("  vision: " + v.label() + (v.found() ? " (found)" : " · " + v.hint())).withStyle(ChatFormatting.AQUA), false);
        }
        return d.snapshots.size();
    }
}
