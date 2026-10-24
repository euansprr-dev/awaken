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
            // replays "your body remembers" for the tiers held (Wings if none): for tours and the clip recorder
            ascent.then(Commands.literal("remember").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                int bits = AscentServer.tiers(p);
                AwakenNet.send(p, new AscentPayloads.Remember(bits != 0 ? bits : 0b111));
                return 1;
            }));
            // the Tether Vow at the world's spawn vault: there, wait for its chunks, then stand in its forecourt (tours)
            ascent.then(Commands.literal("vow").then(Commands.literal("goto").executes(c -> vowGoto(c.getSource()))));
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

    private static int vowGoto(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        net.minecraft.server.level.ServerLevel level = src.getServer().overworld();
        var chunk = dev.awaken.above.ruin.RuinLocator.spawnVault(level);
        if (chunk == null) {
            src.sendFailure(Component.literal("No spawn vault in this world"));
            return 0;
        }
        // a dev tool: generate the vault's chunks now (the structure's references come with them)
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.getChunk(chunk.x() + x, chunk.z() + z);
        int cx = chunk.getMiddleBlockX(), cz = chunk.getMiddleBlockZ();
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
        for (int dy = -24; dy <= 12; dy += 2) {
            for (int dx = -24; dx <= 24; dx += 6) {
                for (int dz = -24; dz <= 24; dz += 6) {
                    TetherVow.Site site = TetherVow.siteAt(level, new net.minecraft.core.BlockPos(cx + dx, ground + dy, cz + dz));
                    if (site == null) continue;
                    net.minecraft.core.BlockPos f = site.world(11, 1, 2);
                    float yaw = switch (site.rot()) {
                        case 1 -> 90F;
                        case 2 -> 180F;
                        case 3 -> -90F;
                        default -> 0F;
                    };
                    p.teleportTo(level, f.getX() + 0.5, f.getY(), f.getZ() + 0.5, java.util.Set.of(), yaw, 0, true);
                    dev.awaken.Awaken.LOG.info("[ascent] vow goto: the spawn vault at {} (turn {})", site.box().getCenter().toShortString(), site.rot());
                    return 1;
                }
            }
        }
        dev.awaken.Awaken.LOG.warn("[ascent] vow goto: no vault found round {}, {} (ground {})", cx, cz, ground);
        // what is there: every structure start the chunks round it know, and how the vow reads each vault
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            var ch = level.getChunk(chunk.x() + x, chunk.z() + z);
            for (var e : ch.getAllStarts().entrySet()) {
                for (var piece : e.getValue().getPieces()) {
                    var box = piece.getBoundingBox();
                    var c = box.getCenter();
                    dev.awaken.Awaken.LOG.warn("[ascent] vow goto: {} piece box {} turn {}; site at its centre {}", level.registryAccess()
                                    .lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).getKey(e.getKey()), box,
                            TetherVow.rotationOf(level, box), TetherVow.siteAt(level, c));
                }
            }
        }
        src.sendFailure(Component.literal("No vault found near the spawn vault's chunk"));
        return 0;
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
