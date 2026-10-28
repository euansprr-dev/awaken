package dev.awaken.anims.station;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only nearby furnace views. Vanilla owns all items, cooking, fuel and extraction. Nothing is saved here. */
public final class FurnaceContents {
    private FurnaceContents() {}
    public static final int RANGE = 16, MAX = 64, DISCOVER_TICKS = 40, UPDATE_TICKS = 5;
    private static final Map<UUID, Watch> WATCHES = new HashMap<>();

    public record Snapshot(BlockPos pos, ItemStack input, ItemStack fuel, ItemStack output) {
        static Snapshot read(AbstractFurnaceBlockEntity furnace) {
            return new Snapshot(furnace.getBlockPos(), furnace.getItem(0).copy(), furnace.getItem(1).copy(), furnace.getItem(2).copy());
        }
        boolean matches(AbstractFurnaceBlockEntity furnace) {
            return ItemStack.matches(input, furnace.getItem(0)) && ItemStack.matches(fuel, furnace.getItem(1)) && ItemStack.matches(output, furnace.getItem(2));
        }
        public ItemStack item(int slot) { return slot == 0 ? input : slot == 1 ? fuel : output; }
    }

    public record Update(Identifier dimension, boolean reset, List<Snapshot> changed, List<BlockPos> removed) implements CustomPacketPayload {
        public static final Type<Update> TYPE = new Type<>(Awaken.id("furnace_contents"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> STREAM_CODEC = StreamCodec.ofMember((m, b) -> {
            b.writeIdentifier(m.dimension); b.writeBoolean(m.reset); b.writeVarInt(m.changed.size());
            for (Snapshot s : m.changed) {
                b.writeBlockPos(s.pos); ItemStack.OPTIONAL_STREAM_CODEC.encode(b, s.input);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(b, s.fuel); ItemStack.OPTIONAL_STREAM_CODEC.encode(b, s.output);
            }
            b.writeVarInt(m.removed.size());
            for (BlockPos pos : m.removed) b.writeBlockPos(pos);
        }, b -> {
            Identifier dimension = b.readIdentifier(); boolean reset = b.readBoolean();
            int count = b.readVarInt();
            if (count < 0 || count > MAX) throw new IllegalArgumentException("Furnace view count");
            List<Snapshot> changed = new ArrayList<>(count);
            for (int i = 0; i < count; i++) changed.add(new Snapshot(b.readBlockPos(), ItemStack.OPTIONAL_STREAM_CODEC.decode(b),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(b), ItemStack.OPTIONAL_STREAM_CODEC.decode(b)));
            count = b.readVarInt();
            if (count < 0 || count > MAX) throw new IllegalArgumentException("Furnace removal count");
            List<BlockPos> removed = new ArrayList<>(count);
            for (int i = 0; i < count; i++) removed.add(b.readBlockPos());
            return new Update(dimension, reset, changed, removed);
        });
        @Override public Type<Update> type() { return TYPE; }
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> e.registrar("1").playToClient(Update.TYPE, Update.STREAM_CODEC));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
                if (!AwakenNet.canReceive(p, Update.TYPE)) continue;
                Watch watch = WATCHES.computeIfAbsent(p.getUUID(), id -> new Watch());
                Update update = watch.tick(p, p.level().getGameTime());
                if (update != null) AwakenNet.send(p, update);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, e -> {
            if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p) || !AwakenNet.canReceive(p, Update.TYPE)) return;
            BlockEntity be = p.level().getBlockEntity(e.getPos());
            if (be == null || !supported(be)) return;
            Watch watch = WATCHES.computeIfAbsent(p.getUUID(), id -> new Watch());
            // Seed from the server before the menu opens, including a quick open/close between discovery passes.
            Update update = watch.interacted(p, p.level().getGameTime());
            if (update != null) AwakenNet.send(p, update);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> WATCHES.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> WATCHES.clear());
    }

    static boolean supported(BlockEntity entity) {
        var state = entity.getBlockState();
        return entity instanceof AbstractFurnaceBlockEntity && (state.is(Blocks.FURNACE) || state.is(Blocks.SMOKER) || state.is(Blocks.BLAST_FURNACE));
    }

    /** One player's bounded interest set. Discovery reads only nine already-loaded nearby chunks, every two seconds. */
    static final class Watch {
        private ServerLevel level;
        private long nextDiscover, nextUpdate;
        private static final class Seen {
            Snapshot snapshot;
            int generation;
            Seen(Snapshot snapshot, int generation) { this.snapshot = snapshot; this.generation = generation; }
        }
        private final Map<BlockPos, Seen> seen = new HashMap<>();
        private int generation;
        private List<AbstractFurnaceBlockEntity> targets = List.of();

        Update interacted(ServerPlayer player, long now) {
            nextDiscover = nextUpdate = Long.MIN_VALUE;
            return tick(player, now);
        }

        Update tick(ServerPlayer player, long now) {
            ServerLevel at = (ServerLevel) player.level();
            boolean reset = level != at;
            if (reset) { level = at; seen.clear(); nextDiscover = nextUpdate = now; }
            if (now < nextUpdate) return null;
            nextUpdate = now + UPDATE_TICKS;
            if (now >= nextDiscover) {
                nextDiscover = now + DISCOVER_TICKS;
                List<AbstractFurnaceBlockEntity> found = new ArrayList<>();
                int cx = player.blockPosition().getX() >> 4, cz = player.blockPosition().getZ() >> 4;
                for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) {
                    var chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk == null) continue;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (supported(be) && near(player, be.getBlockPos())) found.add((AbstractFurnaceBlockEntity) be);
                    }
                }
                found.sort(Comparator.comparingDouble(be -> player.distanceToSqr(be.getBlockPos().getX() + .5, be.getBlockPos().getY() + .5, be.getBlockPos().getZ() + .5)));
                targets = found.size() <= MAX ? found : new ArrayList<>(found.subList(0, MAX));
            }
            List<Snapshot> changed = null;
            generation++;
            for (AbstractFurnaceBlockEntity furnace : targets) {
                BlockPos pos = furnace.getBlockPos();
                var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
                if (furnace.isRemoved() || chunk == null || chunk.getBlockEntities().get(pos) != furnace || !near(player, pos)) continue;
                Seen old = seen.get(pos);
                if (old == null || !old.snapshot.matches(furnace)) {
                    Snapshot snapshot = Snapshot.read(furnace);
                    if (old == null) { old = new Seen(snapshot, generation); seen.put(pos, old); }
                    else old.snapshot = snapshot;
                    if (changed == null) changed = new ArrayList<>();
                    changed.add(snapshot);
                }
                old.generation = generation;
            }
            List<BlockPos> removed = null;
            var iterator = seen.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (entry.getValue().generation == generation) continue;
                if (removed == null) removed = new ArrayList<>();
                removed.add(entry.getKey());
                iterator.remove();
            }
            if (!reset && changed == null && removed == null) return null;
            return new Update(level.dimension().identifier(), reset, changed == null ? List.of() : changed, removed == null ? List.of() : removed);
        }

        private static boolean near(ServerPlayer p, BlockPos pos) {
            return p.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= RANGE * RANGE;
        }
    }
}
