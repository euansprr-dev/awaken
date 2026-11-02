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
                    .then(Commands.literal("forget").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        Known.save(p, Known.fresh());
                        c.getSource().sendSuccess(() -> Component.literal("Every enchantment forgotten"), false);
                        return 1;
                    }))));
        });
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
