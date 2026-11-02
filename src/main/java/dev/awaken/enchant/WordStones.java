package dev.awaken.enchant;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The Precursor Word Stones (SPEC §45.14): the ruins carry one each, carved with an enchantment from their own set and
 * drawn by the structure's seed, so a ruin always holds the same one. Holding your hand to it teaches it.
 */
public final class WordStones {
    private WordStones() {}

    /** By enchantment number: the vaults and the monolith (Insight, Efficiency, Infinity, Feather Falling), the sky
     *  platform and the spire's crown (Feather Falling, Infinity, Insight), the ocean lab (the water's six). */
    static final int[] VAULT = {19, 17, 26, 8}, SKY = {8, 26, 19}, SEA = {21, 22, 27, 28, 29, 30};

    static int[] set(String name) {
        return switch (name) {
            case "sea" -> SEA;
            case "sky" -> SKY;
            default -> VAULT;
        };
    }

    static int word(String set, long seed) {
        int[] w = set(set);
        return w[(int) Math.floorMod(HashCommon.mix(seed), (long) w.length)];
    }

    /** What this stone is carved with (null for any other block). */
    public static Inscriptions.@Nullable Entry entry(BlockState state) {
        if (!state.hasProperty(WordStoneBlock.WORD)) return null;
        int no = state.getValue(WordStoneBlock.WORD);
        return no >= 1 && no <= Inscriptions.ALL.size() ? Inscriptions.ALL.get(no - 1) : null;
    }

    static boolean place(LevelAccessor level, BlockPos pos, Direction facing, String set, long seed) {
        BlockState s = EnchantContent.WORD_STONE.get().defaultBlockState().setValue(WordStoneBlock.FACING, facing)
                .setValue(WordStoneBlock.WORD, word(set, seed));
        return level.setBlock(pos, s, Block.UPDATE_CLIENTS);
    }

    /** Your hand on the stone: you learn its enchantment, or it tells you that you already know it. */
    static void touch(ServerPlayer p, BlockState state) {
        Inscriptions.Entry e = entry(state);
        if (e == null) return;
        if (Known.of(p).knows(e.id())) {
            p.sendSystemMessage(Component.literal("The stone is carved with " + e.name() + ", and you already know it."), true);
            return;
        }
        Pages.learn(p, List.of(e), "Copied from a Word Stone");
    }
}
