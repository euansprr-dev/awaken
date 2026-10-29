package dev.awaken.world.stilled;

import dev.awaken.core.api.ArmsApi;
import dev.awaken.core.api.CoreApi;
import dev.awaken.village.api.Villages;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The chests people left behind (SPEC §43.3). One stands by an empty settlement's well once somebody carrying its key
 * comes within {@link #NEAR} of it; it stays (unbreakable, like the Stilled). Opened with that key, once per player, it
 * gives the first early weapon pattern they don't know, else the first of the six old relics they haven't had from one,
 * else three essence, and always a few supplies; the key stays in the lock.
 */
final class LeftChests {
    private LeftChests() {}

    static final int CHECK = 20, NEAR = 64;
    /** The six old relics, in the order the chests give them. */
    static final String[] RELICS = {"shepherds_lantern", "lumen_tonic", "wrens_hook", "caravan_writ", "herdcaller_horn", "pells_spurs"};
    static final ResourceKey<LootTable> SUPPLIES = ResourceKey.create(Registries.LOOT_TABLE, dev.awaken.Awaken.id("chests/left_behind"));

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK != 7) return;
        ServerLevel level = server.overworld();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            var inv = p.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                var d = inv.getItem(i).get(StilledContent.KEY.get());
                if (d != null && !Stilled.get(server).chests.containsKey(d.settlement())) stand(level, p, d);
            }
        }
    }

    /** The chest by the well of a key's settlement, once its keyholder is near and the well's ground is loaded. */
    static void stand(ServerLevel level, ServerPlayer p, StilledContent.KeyData d) {
        BlockPos well = wellOf(level, d);
        if (well == null || Math.hypot(well.getX() - p.getX(), well.getZ() - p.getZ()) > NEAR || !level.isLoaded(well)) return;
        BlockPos at = spotBy(level, well, d.settlement().hashCode());
        if (at == null) return;
        Direction facing = Direction.fromYRot(StilledPlacer.yaw(well.getX() - at.getX(), well.getZ() - at.getZ()));
        level.setBlock(at, StilledContent.LEFT_CHEST.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing), Block.UPDATE_ALL);
        Stilled data = Stilled.get(level.getServer());
        data.chests.put(d.settlement(), at.asLong());
        data.setDirty();
        dev.awaken.Awaken.LOG.info("[stilled] a left chest stands at {} by the well of {}", at.toShortString(), d.settlement());
    }

    /** A key's settlement's well (its anchor's ground when it has none), or null while it isn't planned. */
    static @Nullable BlockPos wellOf(ServerLevel level, StilledContent.KeyData d) {
        for (Villages.Empty e : Villages.emptyNear(level, new BlockPos(d.x(), 0, d.z()), 16)) {
            if (!e.key().equals(d.settlement()) || e.name() == null) continue;
            if (e.well() != null) return e.well();
            if (!level.isLoaded(e.anchor())) return null;
            return new BlockPos(e.anchor().getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, e.anchor().getX(), e.anchor().getZ()),
                    e.anchor().getZ());
        }
        return null;
    }

    /** Free ground three to six blocks from the well, about level with it (the seed turns where round it looks first). */
    static @Nullable BlockPos spotBy(ServerLevel level, BlockPos well, long seed) {
        double start = StilledSpots.unit(StilledSpots.mix(seed)) * Math.PI * 2;
        for (int r = 3; r <= 6; r++)
            for (int k = 0; k < 16; k++) {
                double a = start + k * Math.PI / 8;
                int x = well.getX() + (int) Math.round(Math.cos(a) * r), z = well.getZ() + (int) Math.round(Math.sin(a) * r);
                if (!level.isLoaded(new BlockPos(x, 0, z))) continue;
                BlockPos feet = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (Math.abs(feet.getY() - well.getY()) > 4) continue;
                BlockState under = level.getBlockState(feet.below()), here = level.getBlockState(feet);
                if (!under.isFaceSturdy(level, feet.below(), Direction.UP) || !under.getFluidState().isEmpty()) continue;
                if (!here.getFluidState().isEmpty() || !(here.isAir() || here.canBeReplaced())) continue;
                return feet;
            }
        return null;
    }

    /** Someone tries a left chest: locked without its key, empty once they've opened it, else what was left and the supplies. */
    static void open(ServerPlayer p, ServerLevel level, BlockPos pos) {
        Stilled data = Stilled.get(level.getServer());
        String settlement = null;
        for (var e : data.chests.entrySet()) if (e.getValue() == pos.asLong()) settlement = e.getKey();
        if (settlement == null) {
            Stilled.say(p, "[It's locked, and the lock has rusted solid.]");
            return;
        }
        if (Stilled.has(p, "opened:" + settlement)) {
            Stilled.say(p, "[It's empty now. You took what they left, and the key's still in the lock.]");
            return;
        }
        ItemStack key = keyFor(p, settlement);
        if (key == null) {
            level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 0.9F);
            Stilled.say(p, "[Locked. Whoever had the key went back for something.]");
            return;
        }
        var d = key.get(StilledContent.KEY.get());
        String what = give(p, level, pos);
        key.shrink(1);
        Stilled.mark(p, "opened:" + settlement);
        if (d != null) Stilled.mark(p, "done:" + d.figure());
        dev.awaken.progression.Progression.count(p, "awaken:left_chests_opened");
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.7F, 0.8F);
        level.playSound(null, pos, SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5F, 0.6F);
        Stilled.say(p, "[The key turns. Inside there's " + what + ", and a few things for the road, packed by somebody who meant to come back."
                + " You leave the key in the lock.]");
    }

    /** The key a player carries for this settlement's chest, or null. */
    static @Nullable ItemStack keyFor(ServerPlayer p, String settlement) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            var d = st.get(StilledContent.KEY.get());
            if (d != null && d.settlement().equals(settlement)) return st;
        }
        return null;
    }

    /** What the chest held for this player, handed over; said back as words ("a weapon pattern", "a Shepherd's Lantern"). */
    static String give(ServerPlayer p, ServerLevel level, BlockPos pos) {
        List<ItemStack> out = new ArrayList<>();
        String what;
        if (ArmsApi.leftChestPattern(p)) what = "a weapon pattern, wrapped in oilcloth";
        else {
            Item relic = null;
            for (String id : RELICS) {
                if (Stilled.has(p, "relic:" + id)) continue;
                relic = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(dev.awaken.Awaken.MODID, id));
                if (relic != null && relic != net.minecraft.world.item.Items.AIR) {
                    Stilled.mark(p, "relic:" + id);
                    break;
                }
                relic = null;
            }
            if (relic != null) {
                ItemStack st = new ItemStack(relic);
                what = "an old " + st.getHoverName().getString();
                out.add(st);
            } else {
                out.add(new ItemStack(CoreApi.ESSENCE_SHARD.get(), 3));
                what = "three essence shards";
            }
        }
        LootTable table = level.getServer().reloadableRegistries().getLootTable(SUPPLIES);
        table.getRandomItems(new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.THIS_ENTITY, p).create(LootContextParamSets.CHEST), out::add);
        for (ItemStack st : out) p.getInventory().placeItemBackInInventory(st, net.minecraft.util.Prediction.SERVER_ONLY);
        return what;
    }
}
