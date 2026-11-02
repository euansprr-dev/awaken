package dev.awaken.enchant;

import dev.awaken.core.api.HearthHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EnchantingTableBlock;
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
    /** Whether the table stands in its owner's Study (the server looks every five seconds): its letters drift thicker, and hum. */
    private boolean study;

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

    private ItemStack plainOf = ItemStack.EMPTY, plain = ItemStack.EMPTY;

    /** The piece as the slate shows it: without the violet shimmer, so the mark being written is what shines on it. */
    public ItemStack shownPiece() {
        if (plainOf != piece) {
            plainOf = piece;
            plain = piece.isEmpty() ? ItemStack.EMPTY : piece.copy();
            if (!plain.isEmpty()) plain.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        }
        return plain;
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
        if (!(level instanceof ServerLevel sl)) return;
        if (be.writer != null && level.getGameTime() % 10 == 0) Inscribe.tableTick(sl, be);
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), 100L) == 0) {
            boolean s = HearthHooks.studyAt(sl, pos);
            if (s != be.study) {
                be.study = s;
                be.changed();
            }
        }
    }

    public boolean study() {
        return study;
    }

    // ---- the Study's drifting letters (SPEC §45.13): vanilla's best effect, in our own script -----------------------

    /** The light letters drift in when nothing's being written. */
    private static final int LETTER_LAPIS = 0x8FB0FF;
    private @Nullable ColorParticleOption letter;
    private int letterColour = -1;
    private long lastLetter = -1000;

    /**
     * Letters drift off bookshelves standing where vanilla's table reads them (two out, with air between) into the book,
     * while someone's near: now and then anywhere, thicker in a Study, thickest while someone writes (and then in the
     * colour of what they write). A quiet paper hum goes with them in a Study.
     */
    private void letters(Level level, BlockPos pos, BlockState state, @Nullable Player near) {
        if (near == null && phase == IDLE) return;
        RandomSource r = level.getRandom();
        int tries = phase != IDLE ? 3 : study ? 2 : 1, odds = phase != IDLE || study ? 3 : 7;
        Direction f = state.hasProperty(InscribingTableBlock.FACING) ? state.getValue(InscribingTableBlock.FACING) : Direction.NORTH;
        double bx = pos.getX() + 0.5 - f.getStepX() * 0.3, by = pos.getY() + 1.0, bz = pos.getZ() + 0.5 - f.getStepZ() * 0.3;
        for (int t = 0; t < tries; t++) {
            if (r.nextInt(odds) != 0) continue;
            BlockPos off = EnchantingTableBlock.BOOKSHELF_OFFSETS.get(r.nextInt(EnchantingTableBlock.BOOKSHELF_OFFSETS.size()));
            if (!EnchantingTableBlock.isValidBookShelf(level, pos, off)) continue;
            double sx = pos.getX() + off.getX() + r.nextDouble(), sy = pos.getY() + off.getY() + 0.05 + r.nextDouble() * 0.9;
            double sz = pos.getZ() + off.getZ() + r.nextDouble();
            level.addParticle(letterOption(), bx, by, bz, sx - bx, sy - by, sz - bz);
            lastLetter = time;
        }
        if (study && time % 90 == 0 && time - lastLetter < 40)
            level.playLocalSound(bx, by, bz, EnchantContent.DRIFT.get(), SoundSource.BLOCKS, 0.18F, 0.9F + r.nextFloat() * 0.2F, false);
    }

    private ColorParticleOption letterOption() {
        int c = phase != IDLE && colour != 0 ? colour & 0xFFFFFF : LETTER_LAPIS;
        if (letter == null || c != letterColour) {
            letter = ColorParticleOption.create(EnchantContent.LETTER.get(), 0xFF000000 | c);
            letterColour = c;
        }
        return letter;
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
        if (be.phase != IDLE && state.hasProperty(InscribingTableBlock.FACING)) {
            // while someone writes, the book opens toward the table's front, where they stand
            var f = state.getValue(InscribingTableBlock.FACING);
            want = (float) Mth.atan2(f.getStepZ(), f.getStepX());
        } else if (near != null) want = (float) Mth.atan2(near.getZ() - (pos.getZ() + 0.5), near.getX() - (pos.getX() + 0.5));
        float d = Mth.wrapDegrees((want - be.rot) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD;
        be.rot += d * 0.2F;
        be.bob = Mth.sin(be.time * 0.1F) * 0.02F;
        be.letters(level, pos, state, near);
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
        study = in.getBooleanOr("study", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        if (writer != null) tag.store("writer", UUIDUtil.CODEC, writer);
        if (!piece.isEmpty()) tag.store("piece", ItemStack.OPTIONAL_CODEC, registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE), piece);
        tag.putInt("phase", phase);
        tag.putInt("no", no);
        tag.putInt("colour", colour);
        tag.putBoolean("study", study);
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
