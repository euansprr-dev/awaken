package dev.awaken.kin;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.core.AwakenCommands;
import dev.awaken.delve.DelveContent;
import dev.awaken.delve.Ore;
import dev.awaken.hearth.Embers;
import dev.awaken.hearth.Hearth;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.kin.works.DropBlockEntity;
import dev.awaken.kin.works.WorkPlans;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * Admin tools for the kin: {@code /awaken admin kin summon <kind> | home <kind> | meet <kind> | watch <kind> <lines> |
 * points <kind> <n> | trust | lesson | site | yard | page}. {@code home} brings one home to the player's Hearth at once (for
 * tours), {@code yard} founds a ready-made home where the player stands, {@code lesson} sends the first fire's Lampwing now, {@code site} fills the nearest wild site.
 */
final class KinCommands {
    private KinCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var kind = Commands.argument("kind", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(KinKind.livingNow().stream().map(KinKind::id), b));
            root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(Commands.literal("kin")
                    .then(Commands.literal("summon").then(kind.executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        KinKind k = KinKind.of(StringArgumentType.getString(c, "kind"));
                        BlockPos at = KinHelp.ground(p.level(), p.getBlockX() + 4, p.getBlockZ());
                        KinBeast b = KinHome.spawn(p.level(), k, k == KinKind.LAMPWING ? at.above(2) : at, EntitySpawnReason.COMMAND);
                        return b == null ? 0 : 1;
                    })))
                    .then(Commands.literal("home").then(Commands.argument("kind", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(KinKind.livingNow().stream().map(KinKind::id), b)).executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                return home(p, KinKind.of(StringArgumentType.getString(c, "kind"))) ? 1 : 0;
                            })))
                    .then(Commands.literal("meet").then(Commands.argument("kind", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(KinKind.livingNow().stream().map(KinKind::id), b)).executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                KinKind k = KinKind.of(StringArgumentType.getString(c, "kind"));
                                Kinship ks = Kinship.of(p);
                                Kinship.Kind s = ks.kind(k);
                                s.met = s.heard = true;
                                s.metDay = p.level().getOverworldClockTime() / 24000L;
                                s.metX = p.getBlockX();
                                s.metZ = p.getBlockZ();
                                Kinship.save(p, ks);
                                return 1;
                            })))
                    .then(Commands.literal("watch").then(Commands.argument("kind", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(KinKind.livingNow().stream().map(KinKind::id), b))
                            .then(Commands.argument("lines", IntegerArgumentType.integer(0, 3)).executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                Kinship ks = Kinship.of(p);
                                ks.kind(KinKind.of(StringArgumentType.getString(c, "kind"))).watched = IntegerArgumentType.getInteger(c, "lines");
                                Kinship.save(p, ks);
                                return 1;
                            }))))
                    .then(Commands.literal("points").then(Commands.argument("kind", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(KinKind.livingNow().stream().map(KinKind::id), b))
                            .then(Commands.argument("n", IntegerArgumentType.integer(0, 1000)).executes(c -> {
                                ServerPlayer p = c.getSource().getPlayerOrException();
                                Kinship ks = Kinship.of(p);
                                ks.kind(KinKind.of(StringArgumentType.getString(c, "kind"))).points = IntegerArgumentType.getInteger(c, "n");
                                Kinship.save(p, ks);
                                return 1;
                            }))))
                    .then(Commands.literal("trust").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        List<KinBeast> near = p.level().getEntitiesOfClass(KinBeast.class, new AABB(p.blockPosition()).inflate(16), b -> b.home == null);
                        if (near.isEmpty()) return 0;
                        KinBeast b = near.getFirst();
                        b.helps.put(p.getUUID(), 3);
                        b.trusted.add(p.getUUID());
                        KinHome.offer(b, p);
                        return 1;
                    }))
                    .then(Commands.literal("lesson").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        Kinship ks = Kinship.of(p);
                        ks.lesson = 0;
                        Kinship.save(p, ks);
                        p.level().getServer().getCommands().performPrefixedCommand(p.level().getServer().createCommandSourceStack().withLevel(p.level()).withSuppressedOutput(), "time set 14000");
                        KinHome.lessonTick(p);
                        return 1;
                    }))
                    .then(Commands.literal("site").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        ServerLevel level = p.level();
                        int cx = p.getBlockX() >> KinSpawner.CELL_SHIFT, cz = p.getBlockZ() >> KinSpawner.CELL_SHIFT;
                        for (int r = 0; r <= 2; r++)
                            for (int dx = -r; dx <= r; dx++)
                                for (int dz = -r; dz <= r; dz++) {
                                    KinSpawner.Site s = KinSpawner.site(level, cx + dx, cz + dz);
                                    if (s == null || !level.isLoaded(s.at())) continue;
                                    int n = KinSpawner.fill(level, s).size();
                                    c.getSource().sendSuccess(() -> Component.literal(n + " " + s.kind().id() + " at " + s.at().toShortString()), false);
                                    return n;
                                }
                        return 0;
                    }))
                    .then(Commands.literal("yard").executes(c -> yard(c.getSource().getPlayerOrException()) ? 1 : 0))
                    .then(Commands.literal("works").executes(c -> works(c.getSource().getPlayerOrException()) ? 1 : 0))
                    .then(Commands.literal("page").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        KinNet.Page page = KinPage.build(p);
                        for (var row : page.home()) c.getSource().sendSuccess(() -> Component.literal("home: " + String.join(" | ", row)), false);
                        for (var row : page.beasts()) c.getSource().sendSuccess(() -> Component.literal("beast: " + String.join(" | ", row)), false);
                        for (var row : page.kin()) c.getSource().sendSuccess(() -> Component.literal("kin: " + String.join(" | ", row)), false);
                        KinNet.send(p);
                        return 1;
                    }))));
        });
    }

    /** One of a kind brought straight home to the player's Hearth (its nook, settled), as if it had followed them. */
    static boolean home(ServerPlayer p, KinKind k) {
        Optional<Hearth.HearthInfo> h = Hearth.home(p);
        if (h.isEmpty()) return false;
        ServerLevel level = p.level();
        BlockPos nook = KinHome.nookFor(level, h.get(), k);
        if (nook == null) nook = h.get().anchor().offset(3, 1, 3);
        KinBeast b = KinHome.spawn(level, k, nook, EntitySpawnReason.COMMAND);
        if (b == null) return false;
        Kinship.of(p).kind(k).met = true;
        KinHome.settle(level, b, h.get().anchor(), nook, p);
        return true;
    }

    /**
     * Tours only: a home right here, whatever the Lull says. The Hearthfire where the player stands, a field east of it
     * (ripe wheat either side of a water channel, a chest at its end) and an Ore Shed west of it (a stone room with an iron
     * lode heart and a barrel of rubble), the heart counted as the player's own. False when they already have a home.
     */
    static boolean yard(ServerPlayer p) {
        if (Hearth.home(p).isPresent()) return false;
        ServerLevel level = p.level();
        BlockPos a = p.blockPosition(), g = a.below();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos q : BlockPos.betweenClosed(g.offset(-13, 0, -6), g.offset(16, 0, 6))) level.setBlock(q, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        for (BlockPos q : BlockPos.betweenClosed(a.offset(-13, 0, -6), a.offset(16, 5, 6))) level.setBlock(q, air, 2);
        // the field: farmland and ripe wheat either side of a water channel, the chest at its end
        for (int x = 4; x <= 12; x++)
            for (int z = -2; z <= 2; z++) {
                if (z == 0) level.setBlock(g.offset(x, 0, z), Blocks.WATER.defaultBlockState(), 3);
                else {
                    level.setBlock(g.offset(x, 0, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, 7), 2);
                    level.setBlock(a.offset(x, 0, z), ((CropBlock) Blocks.WHEAT).getStateForAge(7), 2);
                }
            }
        level.setBlock(a.offset(13, 0, 0), Blocks.CHEST.defaultBlockState(), 3);
        // the Ore Shed: a 5x5 stone room, a door on its east side, the heart and a barrel inside
        BlockPos s = a.offset(-10, 0, -2);
        for (int x = 0; x < 5; x++)
            for (int z = 0; z < 5; z++) {
                level.setBlock(s.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                level.setBlock(s.offset(x, 3, z), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
                boolean wall = x == 0 || x == 4 || z == 0 || z == 4;
                for (int y = 0; y < 3; y++) if (wall) level.setBlock(s.offset(x, y, z), Blocks.COBBLESTONE.defaultBlockState(), 2);
            }
        BlockPos door = s.offset(4, 0, 2);
        level.setBlock(door, Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST), 3);
        level.setBlock(door.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        level.setBlock(s.offset(2, 2, 2), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 3);
        BlockPos heart = s.offset(2, 0, 1), barrel = s.offset(2, 0, 3);
        BlockState hs = DelveContent.HEARTS.get(Ore.IRON).get().defaultBlockState();
        level.setBlock(heart, hs, 3);
        level.setBlock(barrel, Blocks.BARREL.defaultBlockState(), 3);
        if (level.getBlockEntity(barrel) instanceof Container c) {
            c.setItem(0, new ItemStack(Items.COBBLESTONE, 32));
            c.setItem(1, new ItemStack(Items.GRAVEL, 16));
        }
        HearthRecord r = Embers.found(p, level, a);
        KinHome.placed(level, p, heart, hs);
        HearthService.scanNow(level, r);
        p.teleportTo(a.getX() + 0.5, a.getY(), a.getZ() + 4.5);
        return true;
    }

    /**
     * The works raised north of a home (checks and tours): a Smeltery with ore in its barrel, a Storehouse with a heap on
     * its Drop, a Foundry with Sung Iron's metal and a Rookery, each built from its plan, doors toward the fire.
     */
    static boolean works(ServerPlayer p) {
        Optional<Hearth.HearthInfo> home = Hearth.home(p);
        if (home.isEmpty()) return false;
        ServerLevel level = p.level();
        BlockPos a = home.get().anchor(), g = a.below();
        for (BlockPos q : BlockPos.betweenClosed(g.offset(-21, 0, -17), g.offset(19, 0, -7))) level.setBlock(q, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        for (BlockPos q : BlockPos.betweenClosed(a.offset(-21, 0, -17), a.offset(19, 6, -7))) level.setBlock(q, Blocks.AIR.defaultBlockState(), 2);
        String[] order = {"smeltery", "storehouse", "foundry", "rookery"};
        boolean all = true;
        for (int i = 0; i < order.length; i++) all &= WorkPlans.raise(level, a.offset(-15 + 10 * i, 0, -12), Direction.SOUTH, order[i]);
        // the Smeltery's barrel holds ore, the Foundry's the first bell's metal, and the Drop a morning's haul
        stock(level, a.offset(-15, 0, -12), new ItemStack(Items.RAW_IRON, 32), new ItemStack(Items.RAW_COPPER, 24));
        stock(level, a.offset(5, 0, -12), new ItemStack(Items.IRON_INGOT, 12), new ItemStack(DelveContent.BELL_BRONZE_INGOT.get(), 4));
        if (level.getBlockEntity(a.offset(-5, 0, -12)) instanceof DropBlockEntity drop)
            for (ItemStack s : List.of(new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_LOG, 32), new ItemStack(Items.RAW_IRON, 18),
                    new ItemStack(Items.WHEAT, 40), new ItemStack(Items.BONE, 9), new ItemStack(Items.FLINT, 12), new ItemStack(Items.IRON_SWORD),
                    new ItemStack(Items.CARROT, 20), new ItemStack(Items.COAL, 30), new ItemStack(Items.STICK, 24), new ItemStack(Items.DIAMOND, 3)))
                drop.add(s);
        HearthService.data(level.getServer()).at(a, HearthService.OVERWORLD).ifPresent(r -> HearthService.scanNow(level, r));
        return all;
    }

    /** Fills the first barrel within a room's box round its middle. */
    private static void stock(ServerLevel level, BlockPos middle, ItemStack... in) {
        for (BlockPos q : BlockPos.betweenClosed(middle.offset(-3, 0, -3), middle.offset(3, 0, 3)))
            if (level.getBlockState(q).is(Blocks.BARREL) && level.getBlockEntity(q) instanceof Container c) {
                for (int i = 0; i < in.length; i++) c.setItem(i, in[i].copy());
                return;
            }
    }
}
