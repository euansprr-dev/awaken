package dev.awaken.anims.station;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Who is working a station, for everyone who can see them (SPEC §63.5). While a player has a station's own menu open
 * the server marks them with where it is, which side they work it from, what lies there for others to see (a table's
 * nine squares and its result; the six largest stacks in an open box) and a count of the slots they have touched, so
 * the other clients can show a body at work, the things on the table and a reach to each slot. It is appearance only:
 * transient (never saved, never copied on death), synced to whoever tracks the player, taken away the moment the menu
 * shuts for any reason. The server reads the menu it already owns; nothing here changes an item.
 */
public final class StationPresence {
    private StationPresence() {}

    /** How a station is worked: bent over a table's top, on one knee before a furnace, bent to a box. */
    public static final byte NONE = 0, BENCH = 1, KNEEL = 2, BOX = 3;
    /** A beat is passed on no more often than this (ticks): six a second for one player. */
    public static final int BEAT_GAP = 3;
    /** What lies in an open box is read this often (ticks), and at most this many stacks of it are shown. */
    public static final int BOX_TICKS = 5, BOX_SHOWN = 6;

    /**
     * A player at a station: {@code kind}; the block; the way in (from them to it, along an axis); {@code beats}, how
     * many slots they have touched there (the low byte of {@code beat} is the last one); what lies there to be seen.
     */
    public record At(byte kind, BlockPos pos, byte fx, byte fz, int beat, List<ItemStack> shown) {
        public static final At NOWHERE = new At(NONE, BlockPos.ZERO, (byte) 0, (byte) 1, 0, List.of());
        public static final StreamCodec<RegistryFriendlyByteBuf, At> STREAM_CODEC = StreamCodec.ofMember((a, b) -> {
            b.writeByte(a.kind);
            b.writeBlockPos(a.pos);
            b.writeByte(a.fx);
            b.writeByte(a.fz);
            b.writeVarInt(a.beat);
            b.writeVarInt(a.shown.size());
            for (ItemStack s : a.shown) ItemStack.OPTIONAL_STREAM_CODEC.encode(b, s);
        }, b -> {
            byte kind = b.readByte();
            BlockPos pos = b.readBlockPos();
            byte fx = b.readByte(), fz = b.readByte();
            int beat = b.readVarInt(), n = b.readVarInt();
            if (n < 0 || n > 16) throw new IllegalArgumentException("Station view count");
            List<ItemStack> shown = new ArrayList<>(n);
            for (int i = 0; i < n; i++) shown.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(b));
            return new At(kind, pos, fx, fz, beat, shown);
        });

        public boolean here() {
            return kind != NONE;
        }

        public int beatSlot() {
            return beat & 0xFF;
        }

        public int beats() {
            return beat >>> 8;
        }
    }

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    /** Appearance only: where a player is working, sent to existing and new observers; never saved or copied on death. */
    public static final Supplier<AttachmentType<At>> AT = ATTACHMENTS.register("station",
            () -> AttachmentType.builder(() -> At.NOWHERE).sync(At.STREAM_CODEC).build());

    /** The block a player last used, and when (server; the menu that opens just after is that block's). */
    private record Used(BlockPos pos, long at, byte kind, byte fx, byte fz) {}

    private static final Map<UUID, Used> USED = new HashMap<>();
    /** Players marked as at a station, with what was last sent of it and when their last beat went. */
    private static final Map<UUID, Sent> PRESENT = new HashMap<>();

    static final class Sent {
        List<ItemStack> shown = List.of();
        long beatAt = -1_000_000L, readAt = -1_000_000L; // long ago (a difference from the smallest long would wrap)
        int beats;
    }

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, e -> {
            if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p)) return;
            BlockState state = p.level().getBlockState(e.getPos());
            byte kind = kindOf(state);
            if (kind == NONE) return;
            BlockPos pos = e.getPos().immutable();
            double dx = pos.getX() + 0.5 - p.getX(), dz = pos.getZ() + 0.5 - p.getZ();
            byte fx, fz;
            if (kind == KNEEL && state.hasProperty(AbstractFurnaceBlock.FACING)) {
                fx = (byte) -state.getValue(AbstractFurnaceBlock.FACING).getStepX();
                fz = (byte) -state.getValue(AbstractFurnaceBlock.FACING).getStepZ();
            } else if (Math.abs(dx) >= Math.abs(dz)) {
                fx = (byte) Math.signum(dx);
                fz = 0;
            } else {
                fx = 0;
                fz = (byte) Math.signum(dz);
            }
            USED.put(p.getUUID(), new Used(pos, p.level().getGameTime(), kind, fx, fz));
        });
        NeoForge.EVENT_BUS.addListener(PlayerContainerEvent.Open.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) opened(p, e.getContainer());
        });
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            if (PRESENT.isEmpty()) return;
            for (Iterator<Map.Entry<UUID, Sent>> it = PRESENT.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Sent> en = it.next();
                ServerPlayer p = e.getServer().getPlayerList().getPlayer(en.getKey());
                if (p == null || !tick(p, en.getValue())) {
                    if (p != null) p.removeData(AT);
                    it.remove();
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            USED.remove(e.getEntity().getUUID());
            PRESENT.remove(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> {
            USED.clear();
            PRESENT.clear();
        });
    }

    /** How a block is worked, or {@link #NONE} when it is not a station. */
    public static byte kindOf(BlockState state) {
        var b = state.getBlock();
        StationView.Kind k = StationView.kindOf(state);
        if (k != null) return k == StationView.Kind.FURNACE ? KNEEL : BENCH;
        if (b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof EnderChestBlock || b instanceof ShulkerBoxBlock) return BOX;
        return NONE;
    }

    /** Whether a menu is the one a station of this kind opens. */
    static boolean menuFits(byte kind, AbstractContainerMenu menu) {
        return switch (kind) {
            case BENCH -> benchSlots(menu) > 0;
            case KNEEL -> menu instanceof AbstractFurnaceMenu;
            case BOX -> menu instanceof ChestMenu || menu instanceof ShulkerBoxMenu;
            default -> false;
        };
    }

    /** A menu has opened for a player: if it is the station's they just used, they are at it. */
    static void opened(ServerPlayer p, AbstractContainerMenu menu) {
        Used u = USED.get(p.getUUID());
        if (u == null || p.level().getGameTime() - u.at > 5 || !menuFits(u.kind, menu)) return;
        Sent sent = new Sent();
        sent.shown = shown(u.kind, menu);
        sent.readAt = p.level().getGameTime();
        PRESENT.put(p.getUUID(), sent);
        p.setData(AT, new At(u.kind, u.pos, u.fx, u.fz, 0, sent.shown));
    }

    /** Each tick for a player at a station: false when they have left it (the menu shut, they died, it is too far). */
    static boolean tick(ServerPlayer p, Sent sent) {
        At at = p.getExistingData(AT).orElse(At.NOWHERE);
        AbstractContainerMenu menu = p.containerMenu;
        if (!at.here() || !p.isAlive() || menu == p.inventoryMenu || !menuFits(at.kind, menu)) return false;
        long now = p.level().getGameTime();
        // a box holds many stacks: read it now and then; a table's ten are read every tick
        if (at.kind == BOX && now - sent.readAt < BOX_TICKS) return true;
        sent.readAt = now;
        List<ItemStack> shown = shown(at.kind, menu);
        int changed = changedSlot(sent.shown, shown);
        if (changed < 0) return true;
        sent.shown = shown;
        int beat = at.beat;
        // the slot they touched is passed on as a beat, at most six a second (the rest are only seen as what lies there)
        if (now - sent.beatAt >= BEAT_GAP) {
            sent.beatAt = now;
            sent.beats++;
            beat = (sent.beats << 8) | (changed & 0xFF);
        }
        p.setData(AT, new At(at.kind, at.pos, at.fx, at.fz, beat, shown));
        return true;
    }

    /** What lies at a station for others to see: a table's result and nine squares; the largest stacks in a box. */
    static List<ItemStack> shown(byte kind, AbstractContainerMenu menu) {
        if (kind == BENCH) {
            int n = benchSlots(menu);
            List<ItemStack> out = new ArrayList<>(n);
            for (int i = 0; i < n && i < menu.slots.size(); i++) out.add(menu.getSlot(i).getItem().copy());
            return out;
        }
        if (kind == BOX) {
            int n = menu instanceof ChestMenu c ? c.getRowCount() * 9 : menu instanceof ShulkerBoxMenu ? 27 : 0;
            List<ItemStack> out = new ArrayList<>(BOX_SHOWN);
            for (int i = 0; i < n && i < menu.slots.size(); i++) {
                ItemStack st = menu.getSlot(i).getItem();
                if (st.isEmpty()) continue;
                // the largest stacks, one of each kind of thing, biggest first
                int at = out.size();
                boolean merged = false;
                for (int k = 0; k < out.size(); k++) {
                    if (ItemStack.isSameItemSameComponents(out.get(k), st)) {
                        out.set(k, out.get(k).copyWithCount(Math.min(99, out.get(k).getCount() + st.getCount())));
                        merged = true;
                        at = k;
                        break;
                    }
                }
                if (!merged) out.add(st.copy());
                while (at > 0 && out.get(at).getCount() > out.get(at - 1).getCount()) {
                    ItemStack up = out.get(at);
                    out.set(at, out.get(at - 1));
                    out.set(at - 1, up);
                    at--;
                }
            }
            return out.size() > BOX_SHOWN ? new ArrayList<>(out.subList(0, BOX_SHOWN)) : out;
        }
        return List.of();
    }

    /** How many of a bench menu's first slots lie on the station (0: not a bench's menu). */
    static int benchSlots(AbstractContainerMenu menu) {
        for (StationView.Kind k : StationView.Kind.values()) if (k.bench() && StationView.menuFits(k, menu)) return k.slots;
        return 0;
    }

    /**
     * The slot whose stack differs, or -1 when the two lists show the same: the first of them, but a table's result
     * (slot 0) only when nothing else changed (laying an ingredient changes the result too, and the hand went to the
     * ingredient).
     */
    static int changedSlot(List<ItemStack> was, List<ItemStack> now) {
        int n = Math.max(was.size(), now.size()), first = -1;
        for (int i = 0; i < n; i++) {
            ItemStack a = i < was.size() ? was.get(i) : ItemStack.EMPTY, b = i < now.size() ? now.get(i) : ItemStack.EMPTY;
            if (ItemStack.matches(a, b)) continue;
            if (i > 0) return i;
            first = 0;
        }
        return first;
    }
}
