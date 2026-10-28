package dev.awaken.delve.cave;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One cave place, everything its builder needs and nothing it reads from the world: its kind, its origin (each kind says
 * what that is), a quarter-turn rotation, the build seed, the natural cave air it joins (the joint, when the site search
 * found one) and a few numbers of its own ({@code p}). Saved with the piece, so a place built half before an update
 * finishes the same way.
 */
public record Spec(CaveKind kind, int x, int y, int z, int rot, long seed, boolean joined, int jx, int jy, int jz, int[] p) {
    /** Bumped when a builder changes what it draws: a piece saved by an older one keeps what it already built. */
    public static final int STYLE = 1;

    public int p(int i) {
        return i < p.length ? p[i] : 0;
    }

    public BoundingBox box() {
        BoundingBox b = switch (kind) {
            case SINKHOLE -> Sinkhole.box(this);
            case MINE -> Mine.box(this);
            case SEEP -> Seep.box(this);
            case CHUTE -> Chute.box(this);
            case GROTTO -> Grotto.box(this);
            case MOUTH -> Mouth.box(this);
        };
        if (joined) b = b.encapsulate(new BoundingBox(jx - 4, jy - 2, jz - 4, jx + 4, jy + 5, jz + 4));
        return b;
    }

    /** Builds the place into the sink (one chunk of it, or all of it). */
    public void build(Sink s) {
        switch (kind) {
            case SINKHOLE -> Sinkhole.build(this, s);
            case MINE -> Mine.build(this, s);
            case SEEP -> Seep.build(this, s);
            case CHUTE -> Chute.build(this, s);
            case GROTTO -> Grotto.build(this, s);
            case MOUTH -> Mouth.build(this, s);
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("kind", kind.id);
        t.putInt("x", x);
        t.putInt("y", y);
        t.putInt("z", z);
        t.putInt("rot", rot);
        t.putLong("seed", seed);
        t.putBoolean("joined", joined);
        t.putInt("jx", jx);
        t.putInt("jy", jy);
        t.putInt("jz", jz);
        t.putIntArray("p", p);
        return t;
    }

    public static Spec load(CompoundTag t) {
        CaveKind k = CaveKind.byId(t.getStringOr("kind", "grotto"));
        return new Spec(k == null ? CaveKind.GROTTO : k, t.getIntOr("x", 0), t.getIntOr("y", 0), t.getIntOr("z", 0), t.getIntOr("rot", 0),
                t.getLongOr("seed", 0L), t.getBooleanOr("joined", false), t.getIntOr("jx", 0), t.getIntOr("jy", 0), t.getIntOr("jz", 0),
                t.getIntArray("p").orElse(new int[0]));
    }

    /** A stable id for this place (its origin), the same in every chunk and on every start. */
    public long id() {
        return net.minecraft.core.BlockPos.asLong(x, y, z);
    }
}
