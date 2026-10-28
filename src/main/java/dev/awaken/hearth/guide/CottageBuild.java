package dev.awaken.hearth.guide;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Oren's cottage while it goes up (saved on the Hearth, schema 1; SPEC §28.11). Everything the plan needs is kept (the
 * site, the turn, the wood and stone, the ground under it as it was before he cleared it), so the same plan comes back
 * after a restart. {@code worked} is how far into the day's work he is: it follows the time that passed, game ticks or the
 * clock, whichever moved more (a night slept through counts), so the build catches up after you've been away.
 */
public final class CottageBuild {
    public static final int SCHEMA = 1;

    /** Who asked for it (the notices go to them, and to the household). */
    public final UUID by;
    /** The local origin at floor level, and the turn. */
    public final BlockPos origin;
    public final Rotation rot;
    public final String palette;
    /** The natural ground's top per local column, relative to the floor ({@link Cottage#WIDTH} wide; VOID = none). */
    final int[] ground;
    /** Which columns were grass or dirt (the path only crosses those), a bit per column. */
    final long[] grass;
    /** Ticks of work done, and the game time and clock last read. */
    long worked, lastGame, lastClock;
    /** The next plan entry to place. */
    int next;
    /** Plan entries passed over because someone stood in the way: placed once they've moved. */
    final List<Integer> held;

    CottageBuild(UUID by, BlockPos origin, Rotation rot, String palette, int[] ground, long[] grass, long worked, long lastGame,
                 long lastClock, int next, List<Integer> held) {
        this.by = by;
        this.origin = origin;
        this.rot = rot;
        this.palette = palette;
        this.ground = ground;
        this.grass = grass;
        this.worked = worked;
        this.lastGame = lastGame;
        this.lastClock = lastClock;
        this.next = next;
        this.held = new ArrayList<>(held);
    }

    /** How far along it is, 0..1. */
    public float progress() {
        return Math.min(1f, worked / (float) Cottage.DURATION);
    }

    boolean grass(int x, int z) {
        int i = (z - CottagePlan.MIN_Z) * Cottage.WIDTH + (x - CottagePlan.MIN_X);
        return i >= 0 && i / 64 < grass.length && (grass[i / 64] >>> (i % 64) & 1) != 0;
    }

    int top(int x, int z) {
        return ground[(z - CottagePlan.MIN_Z) * Cottage.WIDTH + (x - CottagePlan.MIN_X)];
    }

    private record Saved(int schema, UUID by, BlockPos origin, int rot, String palette, List<Integer> ground, List<Long> grass,
                         long worked, long lastGame, long lastClock, int next, List<Integer> held) {}

    private static final Codec<Saved> SAVED = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(Saved::schema),
            UUIDUtil.CODEC.fieldOf("by").forGetter(Saved::by),
            BlockPos.CODEC.fieldOf("origin").forGetter(Saved::origin),
            Codec.INT.fieldOf("rot").forGetter(Saved::rot),
            Codec.STRING.fieldOf("palette").forGetter(Saved::palette),
            Codec.INT.listOf().fieldOf("ground").forGetter(Saved::ground),
            Codec.LONG.listOf().optionalFieldOf("grass", List.of()).forGetter(Saved::grass),
            Codec.LONG.fieldOf("worked").forGetter(Saved::worked),
            Codec.LONG.fieldOf("last_game").forGetter(Saved::lastGame),
            Codec.LONG.fieldOf("last_clock").forGetter(Saved::lastClock),
            Codec.INT.fieldOf("next").forGetter(Saved::next),
            Codec.INT.listOf().optionalFieldOf("held", List.of()).forGetter(Saved::held)
    ).apply(i, Saved::new));

    public static final Codec<CottageBuild> CODEC = SAVED.xmap(CottageBuild::fromSaved, CottageBuild::toSaved);

    private static CottageBuild fromSaved(Saved s) {
        int[] g = new int[Cottage.WIDTH * Cottage.DEPTH];
        java.util.Arrays.fill(g, Cottage.VOID);
        for (int i = 0; i < g.length && i < s.ground.size(); i++) g[i] = s.ground.get(i);
        long[] gr = new long[(g.length + 63) / 64];
        for (int i = 0; i < gr.length && i < s.grass.size(); i++) gr[i] = s.grass.get(i);
        Rotation rot = Rotation.values()[Math.floorMod(s.rot, Rotation.values().length)];
        return new CottageBuild(s.by, s.origin, rot, s.palette, g, gr, s.worked, s.lastGame, s.lastClock, s.next, s.held);
    }

    private Saved toSaved() {
        List<Integer> g = new ArrayList<>(ground.length);
        for (int v : ground) g.add(v);
        List<Long> gr = new ArrayList<>(grass.length);
        for (long v : grass) gr.add(v);
        return new Saved(SCHEMA, by, origin, rot.ordinal(), palette, g, gr, worked, lastGame, lastClock, next, List.copyOf(held));
    }
}
