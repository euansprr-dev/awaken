package dev.awaken.anims.station;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What kind of touch a block gets (SPEC §24 "Touches", §65.9), by what the block is, for both sides: the client plays
 * your own hands from it, and the server passes it on so everyone who can see you sees your body reach (a clip by how
 * the hand works and how high the thing is). Vanilla's blocks by class, ours by id. The action itself is the block's
 * own: this only names the gesture.
 */
public final class TouchKinds {
    private TouchKinds() {}

    public enum Kind {
        PUSH(Reach.ONE), LEVER(Reach.PULL), BUTTON(Reach.ONE), LID(Reach.BOTH), HATCH(Reach.ONE), PLUCK(Reach.ONE), PALM(Reach.ONE),
        HAMMER(Reach.STRIKE), LAY(Reach.ONE), CRANK(Reach.CRANK), PAGE(Reach.ONE), SET(Reach.BOTH), TEAR(Reach.ONE),
        /** A tap of the fingers: a note block, a repeater's or comparator's switch, a daylight detector. */
        TAP(Reach.ONE),
        /** A bell: the palm sets it swinging. */
        RING(Reach.ONE),
        /** Something put in or on: a disc in a jukebox, scraps in a composter, a book on a shelf, a thing in a pot. */
        PUT(Reach.ONE),
        /** Something taken with the fingers: a slice of cake, a token, a book off a shelf. */
        TAKE(Reach.ONE);

        public final Reach reach;

        Kind(Reach reach) {
            this.reach = reach;
        }
    }

    /** How the body shows a touch: one hand out, both hands, a pull down, a blow, a crank. */
    public enum Reach { ONE, BOTH, PULL, STRIKE, CRANK }

    /** Awaken's stones a palm is pressed to (by id: each is another module's block). */
    private static final Set<String> PALM = Set.of("waystone", "conduit", "ascent_stone", "hearth_anchor", "rest_shrine", "stillpoint_shrine", "gate_entrance",
            "gate_seal", "gate_rune", "word_stone", "precursor_log_terminal", "precursor_door_core", "halo_return_pad", "anomaly_fallen_star", "well_lift",
            "reassessment_monolith", "waking_pick_stone", "hearthfire");
    /** Ours that both hands are set to. */
    private static final Set<String> SET = Set.of("fitting_stand", "foundry_crucible", "storehouse_drop", "work_plan", "fabricator", "broken_machine",
            "trophy_mount", "pattern_stand", "bedroll");
    private static final Set<String> RING = Set.of("bell_frame", "found_bell", "sleeping_bell", "dream_bell");
    private static final Identifier FORGE = Identifier.fromNamespaceAndPath("awaken", "forge");

    /** The touch a use of this block gets, or null for none. {@code held}: what the main hand holds. */
    public static @Nullable Kind of(BlockState s, ItemStack held) {
        Block b = s.getBlock();
        Identifier id = BuiltInRegistries.BLOCK.getKey(b);
        if (id.getNamespace().equals("awaken")) {
            String path = id.getPath();
            if (PALM.contains(path)) return Kind.PALM;
            if (SET.contains(path)) return Kind.SET;
            if (RING.contains(path)) return Kind.RING;
            if (path.equals("spire_board")) return Kind.TEAR;
            if (path.equals("ledger_desk")) return Kind.PAGE;
            if (path.equals("ghost_lever")) return Kind.LEVER;
            if (path.equals("carved_token") || path.equals("piton")) return Kind.TAKE;
            if (path.endsWith("_cache") || path.equals("left_chest")) return Kind.LID;
            if (path.endsWith("_door")) return Kind.PUSH;
            // the Forge: three blows for a piece that can be tempered in the hand, a palm for anything else it offers
            if (FORGE.equals(id)) return held.isEmpty() ? Kind.PALM : Kind.HAMMER;
        }
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) return Kind.PUSH;
        if (b instanceof LeverBlock) return Kind.LEVER;
        if (b instanceof ButtonBlock) return Kind.BUTTON;
        if (b instanceof BarrelBlock) return Kind.HATCH;
        if (b instanceof ChestBlock || b instanceof EnderChestBlock || b instanceof ShulkerBoxBlock) return Kind.LID;
        if (b instanceof SweetBerryBushBlock || b instanceof CaveVinesBlock) return Kind.PLUCK;
        if (s.is(Blocks.LODESTONE) || b instanceof RespawnAnchorBlock || b instanceof BeaconBlock) return Kind.PALM;
        if (b instanceof GrindstoneBlock) return Kind.CRANK;
        if (b instanceof LecternBlock || b instanceof EnchantingTableBlock || b instanceof SignBlock) return Kind.PAGE;
        if (b instanceof SmithingTableBlock || b instanceof AnvilBlock) return Kind.HAMMER;
        if (b instanceof StonecutterBlock || b instanceof LoomBlock || b instanceof CartographyTableBlock || b instanceof BrewingStandBlock
                || b instanceof BedBlock) return Kind.SET;
        if (b instanceof NoteBlock || b instanceof RepeaterBlock || b instanceof ComparatorBlock || b instanceof DaylightDetectorBlock) return Kind.TAP;
        if (b instanceof BellBlock) return Kind.RING;
        if (b instanceof JukeboxBlock || b instanceof ComposterBlock || b instanceof DecoratedPotBlock || b instanceof EndPortalFrameBlock
                || b instanceof FlowerPotBlock) return held.isEmpty() ? Kind.TAKE : Kind.PUT;
        if (b instanceof ChiseledBookShelfBlock) return held.isEmpty() ? Kind.TAKE : Kind.PUT;
        if (b instanceof CakeBlock) return Kind.TAKE;
        return null;
    }

    // ---- the body, for others (SPEC §65.9) --------------------------------------------------------------------------

    /** How high the thing touched is against the body: under 0.6 over the feet low, under 1.4 middle, else high. */
    public static int height(double feetY, double hitY) {
        double d = hitY - feetY;
        return d < 0.6 ? 0 : d < 1.4 ? 1 : 2;
    }

    /** A player's body reaches: who, how the hand works, how high. To everyone tracking them, and to them. */
    public record Body(int entity, byte reach, byte height) implements CustomPacketPayload {
        public static final Type<Body> TYPE = new Type<>(Awaken.id("touch_body"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Body> STREAM_CODEC = StreamCodec.ofMember((m, b) -> {
            b.writeVarInt(m.entity);
            b.writeByte(m.reach);
            b.writeByte(m.height);
        }, b -> new Body(b.readVarInt(), b.readByte(), b.readByte()));

        @Override
        public Type<Body> type() {
            return TYPE;
        }
    }

    /** A body's reach is passed on no more often than this (ticks): four a second for one player. */
    public static final int GAP = 5;
    private static final Map<UUID, Long> LAST = new HashMap<>();

    public static void init(IEventBus modBus) {
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> e.registrar("1").playToClient(Body.TYPE, Body.STREAM_CODEC));
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, e -> {
            if (e.getHand() != InteractionHand.MAIN_HAND || !(e.getEntity() instanceof ServerPlayer p)) return;
            BlockPos pos = e.getPos();
            Kind k = of(p.level().getBlockState(pos), p.getMainHandItem());
            if (k == null || (p.isShiftKeyDown() && !p.getMainHandItem().isEmpty())) return; // a sneak-use places or uses the item
            long now = p.level().getGameTime();
            Long last = LAST.get(p.getUUID());
            if (last != null && now - last < GAP) return;
            LAST.put(p.getUUID(), now);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new Body(p.getId(), (byte) k.reach.ordinal(), (byte) height(p.getY(), e.getHitVec().getLocation().y)));
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> LAST.remove(e.getEntity().getUUID()));
    }
}
