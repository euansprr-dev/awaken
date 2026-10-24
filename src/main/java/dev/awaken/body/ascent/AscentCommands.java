package dev.awaken.body.ascent;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.AwakenNet;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /awaken ascent} (game masters, Phase 0): {@code grant <all|none|tether|wings|skyborn>} sets the tiers you
 * hold, {@code sandbox} grows a grove of giant trees and a launch tower in front of you and puts you on the tower,
 * {@code pilot <line>} runs a line of the tour pilot on your client (scripted input: the autopilot can't hold keys).
 */
final class AscentCommands {
    private AscentCommands() {}

    private static final String[] GRANTS = {"all", "none", "tether", "wings", "skyborn"};

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var ascent = Commands.literal("ascent").requires(AwakenCommands.admin()).executes(c -> show(c.getSource()));
            ascent.then(Commands.literal("grant").then(Commands.argument("what", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(GRANTS, b))
                    .executes(c -> grant(c.getSource(), StringArgumentType.getString(c, "what")))));
            ascent.then(Commands.literal("sandbox").executes(c -> sandbox(c.getSource())));
            ascent.then(Commands.literal("pilot").then(Commands.argument("line", StringArgumentType.greedyString())
                    .executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        AwakenNet.send(p, new AscentPayloads.Pilot(StringArgumentType.getString(c, "line")));
                        return 1;
                    })));
            root.then(ascent);
        });
    }

    private static int show(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        int t = AscentServer.tiers(p);
        StringBuilder sb = new StringBuilder();
        for (AscentPayloads.Tier tier : AscentPayloads.Tier.values()) if (tier.in(t)) sb.append(sb.isEmpty() ? "" : ", ").append(tier.name().toLowerCase());
        src.sendSuccess(() -> Component.literal("Ascent tiers: " + (sb.isEmpty() ? "none (Runner)" : sb)).withStyle(ChatFormatting.AQUA), false);
        return t;
    }

    private static int grant(CommandSourceStack src, String what) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        int bits = switch (what) {
            case "all" -> AscentPayloads.Tier.TETHER.bit() | AscentPayloads.Tier.LEAPER.bit() | AscentPayloads.Tier.WINGS.bit()
                    | AscentPayloads.Tier.SKYBORN.bit();
            case "none" -> 0;
            case "tether" -> AscentPayloads.Tier.TETHER.bit();
            case "wings" -> AscentPayloads.Tier.TETHER.bit() | AscentPayloads.Tier.LEAPER.bit() | AscentPayloads.Tier.WINGS.bit();
            case "skyborn" -> AscentPayloads.Tier.TETHER.bit() | AscentPayloads.Tier.LEAPER.bit() | AscentPayloads.Tier.WINGS.bit()
                    | AscentPayloads.Tier.SKYBORN.bit();
            default -> -1;
        };
        if (bits < 0) {
            src.sendFailure(Component.literal("grant all, none, tether, wings or skyborn"));
            return 0;
        }
        AscentServer.setTiers(p, bits);
        return show(src);
    }

    private static int sandbox(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (AscentServer.tiers(p) == 0) grant(src, "all");
        int n = AscentSandbox.build(p);
        src.sendSuccess(() -> Component.literal("Sandbox: " + n + " giant trees ahead. Hold Left Alt (Tether) at a trunk or branch "
                + "to swing, W at the bottom to pump, let go to fly, Space to jump off the rope. Space in the air opens the wings; "
                + "Space again while gliding takes flight (Ctrl to boost).").withStyle(ChatFormatting.AQUA), false);
        return n;
    }
}
