package dev.awaken.hearth.request;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.PlayDays;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.progression.Progression;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code /awaken hearth requests}: what the Anchor asked for, the streak, the town's regard, Forge credit. Admin
 * ({@code /awaken admin request ...}): {@code offer} (one now), {@code fill} (carry what your requests need),
 * {@code deliver} (as if you used your Anchor), {@code pools} (what could be asked of you, and why not),
 * {@code reputation <n>}, {@code streak <n>}, {@code reset}.
 */
final class RequestCommands {
    private RequestCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            root.then(Commands.literal("hearth").then(Commands.literal("requests").executes(RequestCommands::status)));
            var req = Commands.literal("request");
            req.then(Commands.literal("offer").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                if (HearthService.hearthOf(sp).isEmpty()) return fail(c, "You need a Hearth Anchor first.");
                boolean ok = Requests.offerNow(sp);
                return ok ? ok(c, "Offered: see the toast, the tracker and the Status screen (K)") : fail(c, "No free slot, or nothing you could be asked for yet.");
            }));
            req.then(Commands.literal("fill").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                int n = 0;
                for (ActiveRequest r : Requests.state(sp).active) {
                    ItemStack icon = RequestRules.icon(r.target());
                    if (icon.isEmpty()) continue;
                    int left = r.remaining();
                    while (left > 0) {
                        int k = Math.min(left, icon.getMaxStackSize());
                        ItemStack s = icon.copyWithCount(k);
                        if (!sp.addItem(s)) sp.drop(s, false, Prediction.SERVER_ONLY);
                        left -= k;
                        n += k;
                    }
                }
                int total = n;
                return ok(c, "Gave " + total + " items for your requests");
            }));
            req.then(Commands.literal("deliver").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                Optional<HearthRecord> h = HearthService.hearthOf(sp);
                if (h.isEmpty()) return fail(c, "You need a Hearth Anchor first.");
                boolean any = Requests.onAnchorUsed(sp, HearthService.levelOf(sp.level().getServer(), h.get()), h.get());
                return any ? ok(c, "Delivered") : fail(c, "Nothing to deliver.");
            }));
            req.then(Commands.literal("pools").executes(RequestCommands::pools));
            req.then(Commands.literal("reputation").then(Commands.argument("n", IntegerArgumentType.integer(0, 10_000)).executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                Requests.state(sp).reputation = IntegerArgumentType.getInteger(c, "n");
                Requests.sync(sp);
                return ok(c, "Reputation " + Requests.state(sp).reputation + " · " + RequestRules.Standing.of(Requests.state(sp).reputation).label);
            })));
            req.then(Commands.literal("streak").then(Commands.argument("n", IntegerArgumentType.integer(0, 1000)).executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                RequestState st = Requests.state(sp);
                st.streak = IntegerArgumentType.getInteger(c, "n");
                st.lastDeliveryDay = PlayDays.current(sp.level().getServer());
                Requests.sync(sp);
                return ok(c, "Streak " + st.streak);
            })));
            req.then(Commands.literal("reset").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                for (RequestState.Offer o : Requests.state(sp).offers) dev.awaken.signal.Signal.withdraw(sp, o.action());
                sp.setData(RequestContent.STATE.get(), new RequestState());
                return ok(c, "Requests reset");
            }));
            root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(req));
        });
    }

    private static int status(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        RequestState st = Requests.state(sp);
        long day = PlayDays.current(sp.level().getServer());
        if (HearthService.hearthOf(sp).isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("The Anchor asks for things once you have one: craft a Hearth Anchor."), false);
            return 0;
        }
        for (ActiveRequest r : st.active) {
            int carried = RequestRules.carried(sp, r.target());
            c.getSource().sendSuccess(() -> Requests.describe(r).append(Component.literal(" · carrying " + carried)), false);
        }
        for (RequestState.Offer o : st.offers) {
            c.getSource().sendSuccess(() -> Component.literal("Offered: " + o.request().count() + " ").append(Requests.name(o.request()))
                    .append(" · answer in the Status screen (K)"), false);
        }
        if (st.active.isEmpty() && st.offers.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("No request right now: a new one comes with the next play-day."), false);
        int streak = RequestRules.liveStreak(st.streak, st.lastDeliveryDay, day);
        var standing = RequestRules.Standing.of(st.reputation);
        c.getSource().sendSuccess(() -> Component.literal("Done " + st.completed + " · streak " + streak + " · town " + standing.label + " (" + st.reputation
                + ") · Forge credit " + st.forgeCredit + "%"), false);
        return st.active.size();
    }

    private static int pools(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        RequestState st = Requests.state(sp);
        var ctx = Requests.context(sp, st);
        int doable = 0;
        for (RequestRules.Entry e : RequestPools.entries()) {
            boolean ok = RequestRules.doable(e.def(), ctx);
            if (ok) doable++;
            List<String> why = new ArrayList<>();
            if (e.def().countAt(ctx.rank()) <= 0) why.add("not at " + ctx.rank().label + "-rank");
            for (String t : e.def().requires()) if (!ctx.met(t)) why.add("needs " + t);
            if (!e.def().anyOf().isEmpty() && e.def().anyOf().stream().noneMatch(ctx::met)) why.add("needs one of " + e.def().anyOf());
            String line = (ok ? "✔ " : "· ") + e.id() + (ok ? " ×" + e.def().countAt(ctx.rank()) : " (" + String.join(", ", why) + ")");
            c.getSource().sendSuccess(() -> Component.literal(line).withColor(ok ? 0xFFCF7A : 0x8A93A6), false);
        }
        int n = doable;
        c.getSource().sendSuccess(() -> Component.literal(n + " of " + RequestPools.all().size() + " could be asked of you now (level "
                + Progression.profile(sp).level() + ", lowest Y " + st.lowestY + ")"), false);
        return doable;
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }
}
