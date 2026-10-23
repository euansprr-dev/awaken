package dev.awaken.descent.player;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

/**
 * One player's Descent record (attachment {@code awaken:descent}, copied on death). Depth indices as in
 * {@link dev.awaken.descent.Strata}. Mutable; the owner of a change calls {@code DescentSync.push(player)}.
 */
public final class DescentData {
    public static final int SCHEMA = 1;

    public record Grave(String dimension, BlockPos pos, UUID id, int depth) {
        public static final Codec<Grave> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("dim").forGetter(Grave::dimension),
                BlockPos.CODEC.fieldOf("pos").forGetter(Grave::pos),
                UUIDUtil.CODEC.fieldOf("id").forGetter(Grave::id),
                Codec.INT.optionalFieldOf("depth", 0).forGetter(Grave::depth)
        ).apply(i, Grave::new));
    }

    public int schema = SCHEMA;
    public final TreeSet<Integer> visited = new TreeSet<>();
    public final TreeSet<Integer> lit = new TreeSet<>();
    public final TreeSet<Integer> guardians = new TreeSet<>();
    public int checkpoint = -1;
    public boolean seedCleared;
    public final List<Grave> graves = new ArrayList<>();
    public final List<ItemStack> pendingReturn = new ArrayList<>();
    public boolean diedInDescent;
    public int deepest;
    /** Where the player is now (0 = not in the Descent) and how they came in (for "crossed unheard"). */
    public int visitDepth;
    public boolean visitFromEntrance;
    public boolean visitHeard;

    public DescentData() {}

    private static final Codec<TreeSet<Integer>> INT_SET = Codec.INT.listOf().xmap(TreeSet::new, ArrayList::new);

    public static final MapCodec<DescentData> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(d -> d.schema),
            INT_SET.optionalFieldOf("visited", new TreeSet<>()).forGetter(d -> d.visited),
            INT_SET.optionalFieldOf("lit", new TreeSet<>()).forGetter(d -> d.lit),
            INT_SET.optionalFieldOf("guardians", new TreeSet<>()).forGetter(d -> d.guardians),
            Codec.INT.optionalFieldOf("checkpoint", -1).forGetter(d -> d.checkpoint),
            Codec.BOOL.optionalFieldOf("seed_cleared", false).forGetter(d -> d.seedCleared),
            Grave.CODEC.listOf().optionalFieldOf("graves", List.of()).forGetter(d -> d.graves),
            ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("pending_return", List.of()).forGetter(d -> d.pendingReturn),
            Codec.BOOL.optionalFieldOf("died_in_descent", false).forGetter(d -> d.diedInDescent),
            Codec.INT.optionalFieldOf("deepest", 0).forGetter(d -> d.deepest),
            Codec.INT.optionalFieldOf("visit_depth", 0).forGetter(d -> d.visitDepth),
            Codec.BOOL.optionalFieldOf("visit_entrance", false).forGetter(d -> d.visitFromEntrance),
            Codec.BOOL.optionalFieldOf("visit_heard", false).forGetter(d -> d.visitHeard)
    ).apply(i, (schema, visited, lit, guardians, checkpoint, seed, graves, pending, died, deepest, vd, ve, vh) -> {
        DescentData d = new DescentData();
        d.schema = schema;
        d.visited.addAll(visited);
        d.lit.addAll(lit);
        d.guardians.addAll(guardians);
        d.checkpoint = checkpoint;
        d.seedCleared = seed;
        d.graves.addAll(graves);
        for (ItemStack s : pending) if (!s.isEmpty()) d.pendingReturn.add(s);
        d.diedInDescent = died;
        d.deepest = deepest;
        d.visitDepth = vd;
        d.visitFromEntrance = ve;
        d.visitHeard = vh;
        return d;
    }));
}
