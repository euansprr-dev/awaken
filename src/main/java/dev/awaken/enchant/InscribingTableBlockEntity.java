package dev.awaken.enchant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * An Inscribing Table (schemaVersion 1). Nothing it holds is kept: while someone writes, it shows a copy of their piece
 * on the slate, the page their book is open at, and the channel's light in the enchantment's colour, synced to everyone
 * near; the piece itself never leaves the writer's hand. The client animates the floating book.
 */
public class InscribingTableBlockEntity extends BlockEntity {
    public static final int SCHEMA = 1;
    /** {@link #phase}: nobody writing; the book open; a mark being traced; the piece waking. */
    public static final int IDLE = 0, BOOK = 1, TRACE = 2, WAKE = 3;

    private @Nullable UUID writer;
    private ItemStack piece = ItemStack.EMPTY;
    private int phase, no, colour;

    // the client's book (not saved)
    public float open, oOpen, turn, oTurn, rot, oRot, bob;
    public int time;
    /** The game tick the phase last changed on this client, for the channel's fill and the wake's lift. */
    public long phaseAt = -1000;
    private int lastPhase;

    public InscribingTableBlockEntity(BlockPos pos, BlockState state) {
        super(EnchantContent.TABLE_BE.get(), pos, state);
    }

    public @Nullable UUID writer() {
        return writer;
    }

    public ItemStack piece() {
        return piece;
    }

    public int phase() {
        return phase;
    }

    /** The enchantment the book is open at (its Journal number; 0 none). */
    public int no() {
        return no;
    }

    /** The channel's light (0: none). */
    public int colour() {
        return colour;
    }

    void set(@Nullable UUID writer, ItemStack piece, int phase, int no, int colour) {
        this.writer = writer;
        this.piece = piece.copyWithCount(1);
        this.phase = phase;
        this.no = no;
        this.colour = colour;
        changed();
    }

    void clear() {
        set(null, ItemStack.EMPTY, IDLE, 0, 0);
    }

    void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, InscribingTableBlockEntity be) {
        if (be.writer != null && level instanceof ServerLevel sl && level.getGameTime() % 10 == 0) Inscribe.tableTick(sl, be);
    }

    /** The book opens toward the nearest player (the writer when there is one), turns, and bobs. */
    static void clientTick(Level level, BlockPos pos, BlockState state, InscribingTableBlockEntity be) {
        be.oOpen = be.open;
        be.oTurn = be.turn;
        be.oRot = be.rot;
        be.time++;
        Player near = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, false);
        boolean wanted = near != null || be.phase != IDLE;
        be.open = Mth.clamp(be.open + (wanted ? 0.08F : -0.06F), 0F, 1F);
        if (be.phase != be.lastPhase) {
            be.phaseAt = level.getGameTime();
            be.lastPhase = be.phase;
        }
        // the book turns its pages when the writer looks at another enchantment
        float target = be.no * 0.37F;
        be.turn += Mth.clamp(target - be.turn, -0.12F, 0.12F);
        float want = be.rot;
        if (near != null) want = (float) Mth.atan2(near.getZ() - (pos.getZ() + 0.5), near.getX() - (pos.getX() + 0.5));
        float d = Mth.wrapDegrees((want - be.rot) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        be.rot += d * 0.2F;
        be.bob = Mth.sin(be.time * 0.1F) * 0.02F;
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("schemaVersion", SCHEMA);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        // the live state arrives only in update packets (see getUpdateTag); a saved table is always idle
        writer = in.read("writer", UUIDUtil.CODEC).orElse(null);
        piece = in.read("piece", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        phase = in.getIntOr("phase", IDLE);
        no = in.getIntOr("no", 0);
        colour = in.getIntOr("colour", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        if (writer != null) tag.store("writer", UUIDUtil.CODEC, writer);
        if (!piece.isEmpty()) tag.store("piece", ItemStack.OPTIONAL_CODEC, registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), piece);
        tag.putInt("phase", phase);
        tag.putInt("no", no);
        tag.putInt("colour", colour);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel sl && writer != null) Inscribe.tableGone(sl, pos);
    }

    Optional<UUID> writerOpt() {
        return Optional.ofNullable(writer);
    }
}
