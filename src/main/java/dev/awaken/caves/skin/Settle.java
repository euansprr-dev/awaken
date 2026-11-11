package dev.awaken.caves.skin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Nothing in a cave floats (SPEC §42.4, "Settled"; Euan 11 Nov: "All should be placed intentionally").
 * <p>
 * The skin dresses a chunk while the eight round it are only carved (FEATURES needs its neighbours at TERRAIN): a lichen at a
 * chunk's edge can lean on the next chunk's rock, and a mineshaft or a Delvers' mouth that chunk lays later can cut that rock
 * away, leaving the lichen in the air. Every piece the skin lays that hangs on or stands on something is therefore re-checked
 * once the land round it is final: its blocks carry the {@link #SELF} post-process, so the world-gen region marks each one
 * where it is set, and when the chunk first ticks (every neighbour FULL by then) the game asks each marked block's shape
 * again. A face with nothing behind it comes off, a heap or a stalactite with nothing under or over it goes. The same pass
 * catches anything that cuts our rock after the skin ran: a neighbour's feature reaching in, a later feature in the chunk.
 * <p>
 * Land made before this rule (Euan's worlds) is repaired as it loads: a chunk without the {@link #SETTLED} mark has every
 * settling block in it put on its post-process list once, then the mark; the skin marks every chunk it dresses, so new land
 * is never read twice.
 */
public final class Settle {
    private Settle() {}

    /** The post-process that marks the block's own cell (vanilla's mushrooms use the same). */
    public static final BlockBehaviour.PostProcess SELF = (state, level, pos) -> pos;

    /** A chunk whose settling blocks are on (or have been through) the post-process list. */
    public record Settled(int schemaVersion) {
        public static final int VERSION = 1;
        static final MapCodec<Settled> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("schemaVersion").forGetter(Settled::schemaVersion)).apply(i, Settled::new));
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Awaken.MODID);
    public static final Supplier<AttachmentType<Settled>> SETTLED = ATTACHMENTS.register("cave_settled",
            () -> AttachmentType.builder(() -> new Settled(0)).serialize(Settled.CODEC, s -> s.schemaVersion() > 0).build());

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        NeoForge.EVENT_BUS.addListener(ChunkEvent.Load.class, Settle::loaded);
    }

    /**
     * A block that hangs on or stands on something and goes when that does: the skin's pieces, the ones that carry {@link #SELF}
     * (not its rock; not the web, which holds itself; not the cave weed, which lives in a lake).
     */
    public static boolean settles(BlockState s) {
        Block b = s.getBlock();
        return b != CaveSkin.DUSTY_WEB.get() && !(b instanceof SkinBlocks.CaveWeed) && CaveSkinFeature.skin(b);
    }

    /** The skin has dressed this chunk under the rule: its pieces were marked as they were set. */
    static void dressed(ChunkAccess chunk) {
        chunk.setData(SETTLED.get(), new Settled(Settled.VERSION));
    }

    private static void loaded(ChunkEvent.Load e) {
        if (!(e.getLevel() instanceof ServerLevel) || !(e.getChunk() instanceof LevelChunk chunk)) return;
        Settled s = chunk.getExistingDataOrNull(SETTLED.get());
        if (s != null && s.schemaVersion() >= Settled.VERSION) return;
        int n = mark(chunk);
        chunk.setData(SETTLED.get(), new Settled(Settled.VERSION));
        if (n > 0) Awaken.LOG.debug("[caves] settle {}: {} pieces to re-check", chunk.getPos(), n);
    }

    /**
     * Puts every settling block of the chunk on its post-process list (checked when it next starts ticking, its neighbours
     * loaded); returns how many. A section without any is passed over on its palette.
     */
    public static int mark(LevelChunk chunk) {
        int n = 0;
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section.hasOnlyAir() || !section.maybeHas(Settle::settles)) continue;
            int y0 = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(si));
            var list = ChunkAccess.getOrCreateOffsetList(chunk.getPostProcessing(), si);
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (!settles(section.getBlockState(x, y, z))) continue;
                        list.add(ProtoChunk.packOffsetCoordinates(m.set(x, y0 + y, z)));
                        n++;
                    }
                }
            }
        }
        if (n > 0) chunk.markUnsaved();
        return n;
    }
}
