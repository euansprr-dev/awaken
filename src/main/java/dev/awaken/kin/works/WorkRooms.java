package dev.awaken.kin.works;

import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** The works as the Hearth reads them (SPEC §43.12): a home's rooms of a kind, and what stands in them. */
public final class WorkRooms {
    private WorkRooms() {}

    public static Optional<HearthRecord> home(ServerLevel level, BlockPos anchor) {
        return HearthService.data(level.getServer()).at(anchor, HearthService.OVERWORLD);
    }

    /** Every room of this kind at the home whose Anchor this is. */
    public static List<Room> rooms(ServerLevel level, BlockPos anchor, RoomType t) {
        return home(level, anchor).map(h -> h.rooms.stream().filter(r -> r.types().contains(t)).toList()).orElse(List.of());
    }

    public static @Nullable Room room(ServerLevel level, BlockPos anchor, RoomType t) {
        List<Room> l = rooms(level, anchor, t);
        return l.isEmpty() ? null : l.getFirst();
    }

    /** The first block in a room's box (and its walls, one block out) that passes. */
    public static @Nullable BlockPos find(ServerLevel level, Room r, Predicate<BlockState> t) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = r.min().getY() - 1; y <= r.max().getY() + 1; y++)
            for (int x = r.min().getX() - 1; x <= r.max().getX() + 1; x++)
                for (int z = r.min().getZ() - 1; z <= r.max().getZ() + 1; z++) {
                    m.set(x, y, z);
                    if (level.isLoaded(m) && t.test(level.getBlockState(m))) return m.immutable();
                }
        return null;
    }

    /** Every chest and barrel in a room (its walls too), nearest the room's middle first. */
    public static List<BlockPos> containers(ServerLevel level, Room r) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = r.min().getY() - 1; y <= r.max().getY() + 1; y++)
            for (int x = r.min().getX() - 1; x <= r.max().getX() + 1; x++)
                for (int z = r.min().getZ() - 1; z <= r.max().getZ() + 1; z++) {
                    m.set(x, y, z);
                    if (!level.isLoaded(m)) continue;
                    BlockState s = level.getBlockState(m);
                    if ((s.is(Blocks.CHEST) || s.is(Blocks.BARREL) || s.is(Blocks.TRAPPED_CHEST)) && level.getBlockEntity(m) instanceof Container) out.add(m.immutable());
                }
        BlockPos c = r.centre();
        out.sort((a, b) -> Double.compare(a.distSqr(c), b.distSqr(c)));
        return out;
    }

    public static @Nullable Container container(ServerLevel level, BlockPos p) {
        return level.getBlockEntity(p) instanceof Container c ? c : null;
    }

    /** The room's furniture block of this class, if it still stands. */
    public static @Nullable BlockPos heart(ServerLevel level, Room r, Class<? extends Block> type) {
        return find(level, r, s -> type.isInstance(s.getBlock()));
    }
}
