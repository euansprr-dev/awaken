package dev.awaken.delve.sense;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * {@code /awaken delve senses goto <lush_caves|dripstone_caves|deep_dark|sulfur_caves>} (admin, for tours): the nearest
 * place in that cave biome to stand in (air for feet and head on solid ground, a roof over it, the most headroom near
 * the biome's first sample), facing the longest open way, so a shot sees the cave and its look. The client half of
 * {@code /awaken delve senses} (status, a forced seep) is {@code delve.client.SenseClient}'s.
 */
final class SenseCommands {
    private SenseCommands() {}

    static final List<String> CAVES = List.of("lush_caves", "dripstone_caves", "deep_dark", "sulfur_caves");
    private static final int RING = 20, RISE = 24;

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("delve").then(Commands.literal("senses")
                .then(Commands.literal("goto").requires(AwakenCommands.admin())
                        .then(Commands.argument("biome", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(CAVES, b))
                                .executes(SenseCommands::gotoCave))))));
    }

    private static @Nullable ResourceKey<Biome> cave(String name) {
        return switch (name) {
            case "lush_caves" -> Biomes.LUSH_CAVES;
            case "dripstone_caves" -> Biomes.DRIPSTONE_CAVES;
            case "deep_dark" -> Biomes.DEEP_DARK;
            case "sulfur_caves" -> Biomes.SULFUR_CAVES;
            default -> null;
        };
    }

    private static int gotoCave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "biome");
        ResourceKey<Biome> key = cave(name);
        if (key == null) {
            c.getSource().sendFailure(Component.literal("Caves: " + String.join(", ", CAVES)));
            return 0;
        }
        ServerLevel level = p.level().getServer().overworld();
        // the deep dark lies at the bottom; the others at lush and dripstone depth
        BlockPos origin = p.blockPosition().atY(key == Biomes.DEEP_DARK ? -40 : 16);
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(b -> b.is(key), origin, 4800, 24, 16);
        if (found == null) {
            c.getSource().sendFailure(Component.literal("No " + name + " within 4800 blocks."));
            return 0;
        }
        BlockPos at = found.getFirst();
        BlockPos stand = stand(level, at, key);
        if (stand == null) {
            c.getSource().sendFailure(Component.literal(name + " at " + at.toShortString() + ", but no air to stand in near it."));
            return 0;
        }
        float yaw = openest(level, stand);
        p.teleportTo(level, stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, Set.of(), yaw, 0F, true);
        int sky = level.getBrightness(LightLayer.SKY, stand.above());
        c.getSource().sendSuccess(() -> Component.literal("In " + name + " at " + stand.toShortString() + " (sky light " + sky + ")"), false);
        return 1;
    }

    /**
     * A spot to stand in near {@code at}: ring by ring outward and level by level up and down, the first in the biome
     * with room for feet and head, solid ground and a roof (no sky), taking the first with four blocks of headroom (a
     * cave you can see), else the first at all.
     */
    private static @Nullable BlockPos stand(ServerLevel level, BlockPos at, ResourceKey<Biome> key) {
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        BlockPos fallback = null;
        for (int r = 0; r <= RING; r++) {
            for (int dy = 0; dy <= 2 * RISE; dy++) {
                int y = at.getY() + (dy % 2 == 0 ? dy / 2 : -(dy + 1) / 2);
                if (y <= level.getMinY() + 1 || y >= level.getMaxY() - 4) continue;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        q.set(at.getX() + dx, y, at.getZ() + dz);
                        if (!standable(level, q) || !level.getBiome(q).is(key) || level.canSeeSky(q)) continue;
                        if (headroom(level, q) >= 4) return q.immutable();
                        if (fallback == null) fallback = q.immutable();
                    }
                }
            }
            if (fallback != null && r >= 6) return fallback;
        }
        return fallback;
    }

    private static boolean standable(ServerLevel level, BlockPos.MutableBlockPos q) {
        if (!open(level, q)) return false;
        q.move(Direction.UP);
        boolean head = open(level, q);
        q.move(Direction.DOWN, 2);
        boolean ground = level.getBlockState(q).isFaceSturdy(level, q, Direction.UP) && level.getFluidState(q).isEmpty();
        q.move(Direction.UP);
        return head && ground;
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getCollisionShape(level, pos).isEmpty() && s.getFluidState().isEmpty();
    }

    private static int headroom(ServerLevel level, BlockPos feet) {
        BlockPos.MutableBlockPos q = feet.mutable();
        int n = 0;
        while (n < 8 && open(level, q.move(Direction.UP))) n++;
        return n + 1;
    }

    /** The yaw (Minecraft's: 0 south, 90 west) of the longest open run at eye level, of eight ways. */
    private static float openest(ServerLevel level, BlockPos stand) {
        float best = 0F;
        int bestRun = -1;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 8; i++) {
            float yaw = i * 45F;
            double rad = Math.toRadians(yaw), dx = -Math.sin(rad), dz = Math.cos(rad);
            int run = 0;
            while (run < 32) {
                q.set(stand.getX() + 0.5 + dx * (run + 1), stand.getY() + 1, stand.getZ() + 0.5 + dz * (run + 1));
                if (!open(level, q)) break;
                run++;
            }
            if (run > bestRun) {
                bestRun = run;
                best = yaw;
            }
        }
        return best;
    }
}
