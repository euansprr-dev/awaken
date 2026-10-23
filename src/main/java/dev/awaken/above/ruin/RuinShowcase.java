package dev.awaken.above.ruin;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.Arrays;

/**
 * {@code /awaken above ruin showcase <kind>} (game masters): builds a ruin of that kind ahead of you, its front toward
 * you, for screenshots and look tests (the autopilot's structures tour). Not a structure start: no landmark, no
 * placement rules; the loot is real.
 */
final class RuinShowcase {
    private RuinShowcase() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("above").then(Commands.literal("ruin").then(Commands.literal("showcase")
                .requires(AwakenCommands.admin())
                .then(Commands.argument("kind", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(RuinKind.values()).map(k -> k.id), b))
                        .executes(RuinShowcase::run))))));
    }

    private static int run(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        RuinKind kind = RuinKind.byId(StringArgumentType.getString(c, "kind"));
        if (p == null || kind == null) {
            c.getSource().sendFailure(Component.literal("Kinds: vault, spawn_vault, monolith, ocean_lab, sky_platform"));
            return 0;
        }
        ServerLevel level = p.level();
        RuinBuilder.Size s = RuinBuilder.size(kind);
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        double dist = s.x() * 0.5 + 18;
        int cx = Mth.floor(p.getX() - Mth.sin(yaw) * dist), cz = Mth.floor(p.getZ() + Mth.cos(yaw) * dist);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz) - 1;
        int floor = level.getHeight(Heightmap.Types.OCEAN_FLOOR, cx, cz) - 1;
        int y = switch (kind) {
            case MONOLITH -> ground - 6;
            case OCEAN -> floor;
            case SKY -> Math.max(ground + 24, p.getBlockY() + 12);
            default -> ground;
        };
        // the door faces -Z in the blueprint; turn it so the front faces the player
        int quarter = Math.floorMod(Math.round(p.getYRot() / 90F), 4); // 0 south, 1 west, 2 north, 3 east (facing)
        int rot = quarter; // the door (blueprint -Z) faces north at 0, east at 1, south at 2, west at 3: back at the player
        BlockPos corner = new BlockPos(cx - s.x() / 2, y, cz - s.z() / 2);
        int w = s.x() - 1;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        net.minecraft.world.level.block.Rotation r = switch (rot) {
            case 1 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_90;
            case 2 -> net.minecraft.world.level.block.Rotation.CLOCKWISE_180;
            case 3 -> net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90;
            default -> net.minecraft.world.level.block.Rotation.NONE;
        };
        RuinBuilder.build(kind, level.getRandom().nextLong(), kind == RuinKind.SPAWN_VAULT ? 1 : 2, new RuinBuilder.Sink() {
            private BlockPos.MutableBlockPos at(int x, int yy, int z) {
                int lx, lz;
                switch (rot) {
                    case 1 -> { lx = w - z; lz = x; }
                    case 2 -> { lx = w - x; lz = w - z; }
                    case 3 -> { lx = z; lz = w - x; }
                    default -> { lx = x; lz = z; }
                }
                return m.set(corner.getX() + lx, corner.getY() + yy, corner.getZ() + lz);
            }

            @Override
            public void set(int x, int yy, int z, BlockState state) {
                level.setBlock(at(x, yy, z), state.rotate(r), Block.UPDATE_CLIENTS);
            }

            @Override
            public void fillDown(int x, int yy, int z, BlockState state, int depth) {
                at(x, yy, z);
                for (int i = 0; i < depth; i++) {
                    BlockState here = level.getBlockState(m);
                    if (!here.isAir() && here.getFluidState().isEmpty() && !here.canBeReplaced()) break;
                    level.setBlock(m, state, Block.UPDATE_CLIENTS);
                    m.move(Direction.DOWN);
                }
            }

            @Override
            public void chest(int x, int yy, int z, Direction facing, ResourceKey<LootTable> table) {
                BlockPos pos = at(x, yy, z).immutable();
                level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, r.rotate(facing)), Block.UPDATE_CLIENTS);
                RandomizableContainer.setBlockEntityLootTable(level, level.getRandom(), pos, table);
            }
        });
        c.getSource().sendSuccess(() -> Component.literal("Showcase " + kind.displayName + " at " + corner.toShortString()), true);
        return 1;
    }
}
