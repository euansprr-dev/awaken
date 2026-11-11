package dev.awaken.enchant;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin tools for enchanting (SPEC §45.10): {@code /awaken admin enchant learn <all|id> | remember all | forget}, for
 * testing and tours. Ids are the enchantment's path ("heat_ward", "sharpness").
 */
final class EnchantCommands {
    private EnchantCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var id = Commands.argument("id", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(
                    Inscriptions.ALL.stream().map(e -> e.id().getPath()), b));
            root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(Commands.literal("enchant")
                    .then(Commands.literal("learn")
                            .then(Commands.literal("all").executes(c -> learn(c.getSource().getPlayerOrException(), null)))
                            .then(id.executes(c -> learn(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "id")))))
                    .then(Commands.literal("remember").then(Commands.literal("all").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        Known k = Known.of(p);
                        int n = 0;
                        for (Inscriptions.Entry e : Inscriptions.ALL) if (k.knows(e.id()) && k.remember(e.id())) n++;
                        Known.save(p, k);
                        int got = n;
                        c.getSource().sendSuccess(() -> Component.literal(got + " marks put in memory"), false);
                        return n;
                    })))
                    // a page as a door gives one: into the pack, flying in from four blocks ahead (for tours of the first touch)
                    .then(Commands.literal("page").then(Commands.argument("page", StringArgumentType.word()).executes(c ->
                            page(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "page")))))
                    .then(Commands.literal("forget").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        Known.save(p, Known.fresh());
                        c.getSource().sendSuccess(() -> Component.literal("Every enchantment forgotten"), false);
                        return 1;
                    }))));
        });
    }

    private static int page(ServerPlayer p, String path) {
        for (Inscriptions.Entry e : Inscriptions.ALL) {
            if (!e.id().getPath().equals(path)) continue;
            var stack = Pages.page(p.level(), e.id(), "From a command");
            if (stack == null) return 0;
            if (!p.getInventory().add(stack)) p.drop(stack, false, net.minecraft.util.Prediction.SERVER_ONLY);
            EnchantFx.page(p, e, p.getEyePosition().add(p.getLookAngle().scale(4)));
            p.sendSystemMessage(Component.literal("A page of " + e.name() + ". " + Pages.then(e)).withColor(e.colour()), false);
            return 1;
        }
        return 0;
    }

    private static int learn(ServerPlayer p, String only) {
        Known k = Known.of(p);
        long day = p.level().getGameTime() / 24000L;
        int n = 0;
        for (Inscriptions.Entry e : Inscriptions.ALL) {
            if (only != null && !e.id().getPath().equals(only) && !e.id().equals(Identifier.tryParse(only))) continue;
            if (k.learn(e.id(), "Taught by a command", day)) n++;
        }
        Known.save(p, k);
        int got = n;
        p.sendSystemMessage(Component.literal(got + (got == 1 ? " enchantment learned" : " enchantments learned")));
        return n;
    }
}
