package dev.awaken.progression;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.DirectorLog;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /awaken status, /awaken why, and admin tools under /awaken admin ... */
final class ProgressionCommands {
    private ProgressionCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            root.then(Commands.literal("status").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                Profile p = Progression.profile(sp);
                c.getSource().sendSuccess(() -> Component.literal(String.format(
                        "Level %d (%.0f/%.0f XP) · %s-rank · %d points · STR %d AGI %d VIT %d INT %d PER %d SPI %d",
                        p.level(), p.xp(), p.xpToNext(), p.rank().label, p.unspent(), p.stat(Stat.STR), p.stat(Stat.AGI),
                        p.stat(Stat.VIT), p.stat(Stat.INT), p.stat(Stat.PER), p.stat(Stat.SPI))), false);
                return p.level();
            }));
            root.then(Commands.literal("why").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                var entries = DirectorLog.recent(sp.getUUID(), 10);
                if (entries.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("No decisions recorded yet."), false);
                for (var e : entries) {
                    c.getSource().sendSuccess(() -> Component.literal("[" + e.system() + "] " + e.detail()), false);
                }
                return entries.size();
            }));
            root.then(Commands.literal("road").executes(c -> road(c.getSource().getPlayerOrException(), c.getSource())));
            var admin = Commands.literal("admin").requires(AwakenCommands.admin());
            admin.then(Commands.literal("awaken").executes(c -> {
                Progression.awaken(c.getSource().getPlayerOrException());
                return 1;
            }));
            admin.then(Commands.literal("xp").then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(c -> {
                int n = Progression.grantXp(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "amount"), Progression.XpSource.COMMAND);
                return n;
            })));
            admin.then(Commands.literal("levels").then(Commands.argument("count", IntegerArgumentType.integer(1, 200)).executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                int n = IntegerArgumentType.getInteger(c, "count");
                for (int i = 0; i < n; i++) Progression.grantXp(sp, Curve.xpToNext(Progression.profile(sp).level()) + 1, Progression.XpSource.COMMAND);
                return n;
            })));
            admin.then(Commands.literal("rankup").executes(c -> {
                Progression.passReassessment(c.getSource().getPlayerOrException());
                return 1;
            }));
            admin.then(Commands.literal("counter").then(Commands.argument("id", StringArgumentType.string())
                    .then(Commands.argument("value", LongArgumentType.longArg(0)).executes(c -> {
                        Progression.setCounter(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "id"), LongArgumentType.getLong(c, "value"));
                        return 1;
                    }))));
            admin.then(Commands.literal("truths").executes(c -> {
                Progression.offerTruths(c.getSource().getPlayerOrException(), 3);
                return 1;
            }));
            admin.then(Commands.literal("paths").executes(c -> {
                var offers = Progression.offerPaths(c.getSource().getPlayerOrException());
                for (var o : offers) c.getSource().sendSuccess(() -> Component.literal(o.path() + " " + o.evidence()), false);
                return offers.size();
            }));
            admin.then(Commands.literal("directive").executes(c -> {
                Directives.tryOffer(c.getSource().getPlayerOrException(), "command");
                return 1;
            }));
            admin.then(Commands.literal("regimen").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                Progression.profile(sp).regimen = Profile.RegimenState.EMPTY;
                Regimen.morning(sp);
                return 1;
            }));
            admin.then(Commands.literal("daysend").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                DaysEnd.send(sp, Progression.profile(sp));
                return 1;
            }));
            root.then(admin);
        });
    }

    /** /awaken road: the steps around your level with their goals, and the lessons you are on. */
    private static int road(ServerPlayer sp, net.minecraft.commands.CommandSourceStack src) {
        Profile p = Progression.profile(sp);
        int shown = 0;
        for (LevelRoad.Step s : LevelRoad.steps()) {
            int lv = s.def().level();
            if (lv < p.level() - 4 || lv > p.level() + 3) continue;
            boolean got = p.unlocks.contains(s.idString());
            String goal = s.def().goal().map(g -> p.counter(s.goalKey()) > 0 ? " · done: " + g.now() : got ? " · to do: " + g.now() : "").orElse("");
            String line = (got ? "✔ " : "  ") + "Lv " + lv + " " + s.def().title() + goal;
            src.sendSuccess(() -> Component.literal(line).withStyle(got ? net.minecraft.ChatFormatting.WHITE : net.minecraft.ChatFormatting.GRAY), false);
            shown++;
        }
        for (Lessons.Lesson l : Lessons.lessons()) {
            String step = Lessons.currentStep(sp, l.id());
            if (step.isEmpty()) continue;
            for (Lessons.Step st : l.steps()) {
                if (st.id().equals(step)) src.sendSuccess(() -> Component.literal("Lesson " + l.id() + ": " + st.now())
                        .withStyle(net.minecraft.ChatFormatting.AQUA), false);
            }
        }
        return shown;
    }
}
