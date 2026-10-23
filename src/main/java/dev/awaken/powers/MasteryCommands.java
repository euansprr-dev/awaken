package dev.awaken.powers;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.core.AwakenCommands;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /awaken powers mastery (your bars) · /awaken admin power mastery &lt;id&gt; &lt;points&gt; (add mastery, levels up). */
final class MasteryCommands {
    private MasteryCommands() {}

    static void register() {
        AwakenCommands.add((root, buildCtx) -> {
            root.then(Commands.literal("powers").then(Commands.literal("mastery").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                Loadout l = Powers.loadout(sp);
                for (Loadout.Owned o : l.owned().values()) {
                    PowerType t = PowerRegistry.get(o.id());
                    if (t == null) continue;
                    String bar = o.level() >= Loadout.MAX_LEVEL ? "mastered"
                            : String.format("%d%% to level %d (%.0f / %.0f)", (int) (100 * l.masteryProgress(o.id())), o.level() + 1,
                            l.mastery(o.id()), Mastery.need(o.level()));
                    c.getSource().sendSuccess(() -> Component.literal(t.name + " · Lv " + o.level() + " · ").withColor(t.family.colour)
                            .append(Component.literal(bar).withStyle(ChatFormatting.GRAY)), false);
                }
                return l.owned().size();
            })));
            root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(Commands.literal("power")
                    .then(Commands.literal("mastery").then(Commands.argument("id", StringArgumentType.word())
                            .suggests((ctx, b) -> SharedSuggestionProvider.suggest(PowerRegistry.all().stream().map(t -> t.path), b))
                            .then(Commands.argument("points", IntegerArgumentType.integer(1, 1_000_000)).executes(c -> {
                                ServerPlayer sp = c.getSource().getPlayerOrException();
                                PowerType t = PowerRegistry.get(StringArgumentType.getString(c, "id"));
                                if (t == null || !Powers.owns(sp, t.id.toString())) {
                                    c.getSource().sendFailure(Component.literal("You don't own that power"));
                                    return 0;
                                }
                                int levels = Mastery.add(sp, t, IntegerArgumentType.getInteger(c, "points"));
                                c.getSource().sendSuccess(() -> Component.literal(t.name + ": +" + levels + " level(s) from mastery"), true);
                                return levels;
                            }))))));
        });
    }
}
