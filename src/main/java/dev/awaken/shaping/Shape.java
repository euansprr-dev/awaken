package dev.awaken.shaping;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The cuts a block can take (SPEC §52.2). Each is one closed outline seen from the side, pushed straight across the block,
 * and turned by the block's facing; the mesh, the collision and the Looks preview all come from the same outline.
 * <p>
 * A cut that runs along an edge (an edge cut) turns corners the way stairs do (§52.2, {@link Corners}). Its outline keeps
 * everything behind it at every height (at height y the block is solid from the back to some depth), so a corner is the
 * cut pushed both ways at once: an outer corner keeps what both keep, an inner corner what either keeps. Each is built as
 * two halves mitred on the diagonal, the outline pushed one way on one side of it and the other way on the other.
 */
public enum Shape implements StringRepresentable {
    FULL("full", false, new float[] {1, 0, 1, 1, 0, 1, 0, 0}),
    HALF_BOTTOM("half_bottom", false, new float[] {1, 0, 1, .5F, 0, .5F, 0, 0}),
    HALF_TOP("half_top", false, new float[] {1, .5F, 1, 1, 0, 1, 0, .5F}),
    SLAB("slab", true, new float[] {.5F, 0, .5F, 1, 0, 1, 0, 0}),
    NOTCH("notch", true, new float[] {1, 0, 1, .5F, .5F, .5F, .5F, 1, 0, 1, 0, 0}),
    BEVEL("bevel", true, new float[] {1, 0, 1, .5F, .5F, 1, 0, 1, 0, 0}),
    ROUND("round", true, round()),
    UNDER("under", true, new float[] {.5F, 0, .5F, .5F, 1, .5F, 1, 1, 0, 1, 0, 0}),
    COVE("cove", true, cove());

    /** Each cut's variants: straight, then the outer corner, then the inner, each four ways round (by 2D data value). */
    public static final int VARIANTS = 12;
    private static final int STRAIGHT = 0, OUTER = 1, INNER = 2;
    private static final float E = 1.0E-4F;

    /**
     * One quad of a shape, turned to its facing: four corners in the game's winding, the outward normal, the side whose
     * texture it wears and the side of the block it lies flat on (null when it's inside the block's outline).
     */
    public record Face(float[] corners, float nx, float ny, float nz, Direction dominant, @Nullable Direction cull) {
        public float x(int corner) {
            return this.corners[corner * 3];
        }

        public float y(int corner) {
            return this.corners[corner * 3 + 1];
        }

        public float z(int corner) {
            return this.corners[corner * 3 + 2];
        }
    }

    private final String id;
    private final boolean edge;
    private final float[] outline;
    private final List<Face>[] faces;
    private final VoxelShape[] voxels;

    @SuppressWarnings("unchecked")
    Shape(String id, boolean edge, float[] outline) {
        this.id = id;
        this.edge = edge;
        this.outline = outline;
        this.faces = new List[VARIANTS];
        this.voxels = new VoxelShape[VARIANTS];
        for (Direction f : Direction.Plane.HORIZONTAL) {
            int at = f.get2DDataValue();
            this.faces[at] = List.copyOf(build(outline, f, STRAIGHT));
            this.voxels[at] = voxel(outline, f);
        }
        for (Direction f : Direction.Plane.HORIZONTAL) {
            int at = f.get2DDataValue();
            if (!edge) {
                this.faces[4 + at] = this.faces[8 + at] = this.faces[at];
                this.voxels[4 + at] = this.voxels[8 + at] = this.voxels[at];
                continue;
            }
            this.faces[4 + at] = List.copyOf(build(outline, f, OUTER));
            this.faces[8 + at] = List.copyOf(build(outline, f, INNER));
            // the corner whose fronts are f and the side to its left: the two straight cuts' collision, both or either
            VoxelShape a = this.voxels[at], b = this.voxels[f.getCounterClockWise().get2DDataValue()];
            this.voxels[4 + at] = Shapes.join(a, b, BooleanOp.AND);
            this.voxels[8 + at] = Shapes.or(a, b);
        }
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    /** Whether this cut runs along an edge, so it has a front and turns corners. */
    public boolean edge() {
        return this.edge;
    }

    /**
     * Which of the {@link #VARIANTS} this cut is, facing this way and turning this corner. A corner's left and right are a
     * stair's: left joins the side counterclockwise of the front, right the side clockwise, so OUTER_RIGHT facing f is
     * the same corner as OUTER_LEFT facing f's clockwise side.
     */
    public int variant(Direction facing, StairsShape corner) {
        if (!this.edge || corner == StairsShape.STRAIGHT) return facing.get2DDataValue();
        boolean outer = corner == StairsShape.OUTER_LEFT || corner == StairsShape.OUTER_RIGHT;
        boolean right = corner == StairsShape.OUTER_RIGHT || corner == StairsShape.INNER_RIGHT;
        return (outer ? 4 : 8) + (right ? facing.getClockWise() : facing).get2DDataValue();
    }

    public List<Face> faces(Direction facing, StairsShape corner) {
        return this.faces[variant(facing, corner)];
    }

    public VoxelShape voxel(Direction facing, StairsShape corner) {
        return this.voxels[variant(facing, corner)];
    }

    /** The outline itself, (depth, height) pairs: for the tests. */
    float[] outline() {
        return this.outline.clone();
    }

    // ---- outlines: (z, y) pairs, z = 1 is the front, anticlockwise so the outward normal is (dy, -dz) ---------------------

    private static float[] round() {
        int arc = 4;
        float[] p = new float[2 * (arc + 4)];
        int n = 0;
        p[n++] = 1;
        p[n++] = 0;
        for (int i = 0; i <= arc; i++) {
            double a = Math.PI / 2.0 * i / arc;
            p[n++] = (float) (0.5 + 0.5 * Math.cos(a));
            p[n++] = (float) (0.5 + 0.5 * Math.sin(a));
        }
        p[n++] = 0;
        p[n++] = 1;
        p[n++] = 0;
        p[n] = 0;
        return p;
    }

    private static float[] cove() {
        int arc = 4;
        float[] p = new float[2 * (arc + 4)];
        int n = 0;
        for (int i = 0; i <= arc; i++) {
            double a = Math.PI - Math.PI / 2.0 * i / arc;
            p[n++] = (float) (1.0 + 0.5 * Math.cos(a));
            p[n++] = (float) (0.5 * Math.sin(a));
        }
        p[n++] = 1;
        p[n++] = 1;
        p[n++] = 0;
        p[n++] = 1;
        p[n++] = 0;
        p[n] = 0;
        return p;
    }

    // ---- mesh ---------------------------------------------------------------------------------------------------------------

    private static final int FRONT = 0, BACK = 1, UP = 2, DOWN = 3, PLUS_X = 4, MINUS_X = 5, NONE = -1;

    /** A quad before it's turned: x across the block, z the depth with the front at 1, the second front of a corner at x = 1. */
    private record Quad(float[] c, float nx, float ny, float nz, int cull) {
        /** The same quad across the diagonal x = z: a corner's other half. A mirror turns it inside out, so it's wound back. */
        Quad mirrored() {
            float[] m = new float[12];
            for (int i = 0; i < 4; i++) {
                int from = (4 - i) % 4 * 3;    // 0, 3, 2, 1
                m[i * 3] = this.c[from + 2];
                m[i * 3 + 1] = this.c[from + 1];
                m[i * 3 + 2] = this.c[from];
            }
            int side = switch (this.cull) {
                case FRONT -> PLUS_X;
                case PLUS_X -> FRONT;
                case BACK -> MINUS_X;
                case MINUS_X -> BACK;
                default -> this.cull;
            };
            return new Quad(m, this.nz, this.ny, this.nx, side);
        }
    }

    /**
     * The quads of one variant. Straight, the outline runs the whole width. A corner is two halves split on the diagonal
     * x = z: an outer corner's first half runs from the side at x = 0 to the diagonal, an inner corner's from the diagonal
     * to the side at x = 1, and the other half is its mirror. The mitre between the halves is inside the block and has
     * no quad; where an edge has no width on a half it's left out, and where it narrows to nothing at one end it's a
     * triangle (two corners the same).
     */
    private static List<Face> build(float[] p, Direction f, int kind) {
        List<Quad> half = new ArrayList<>();
        int n = p.length / 2;
        for (int i = 0; i < n; i++) {
            float pz = p[i * 2], py = p[i * 2 + 1], qz = p[(i + 1) % n * 2], qy = p[(i + 1) % n * 2 + 1];
            float dz = qz - pz, dy = qy - py;
            float len = (float) Math.sqrt(dz * dz + dy * dy);
            float nz = dy / len, ny = -dz / len;
            int cull = NONE;
            if (Math.abs(dz) < E && Math.abs(pz - 1) < E && nz > 0) cull = FRONT;
            else if (Math.abs(dz) < E && Math.abs(pz) < E && nz < 0) cull = BACK;
            else if (Math.abs(dy) < E && Math.abs(py - 1) < E && ny > 0) cull = UP;
            else if (Math.abs(dy) < E && Math.abs(py) < E && ny < 0) cull = DOWN;
            float p0 = kind == INNER ? pz : 0, q0 = kind == INNER ? qz : 0;
            float p1 = kind == OUTER ? pz : 1, q1 = kind == OUTER ? qz : 1;
            if (p1 - p0 < E && q1 - q0 < E) continue;
            half.add(new Quad(new float[] {q0, qy, qz, p0, py, pz, p1, py, pz, q1, qy, qz}, 0, ny, nz, cull));
        }
        for (float[] s : strips(p)) {
            float a = s[0], b = s[1], loA = s[2], loB = s[3], hiA = s[4], hiB = s[5];
            if (kind != OUTER) half.add(new Quad(new float[] {1, hiB, b, 1, loB, b, 1, loA, a, 1, hiA, a}, 1, 0, 0, PLUS_X));
            if (kind != INNER) half.add(new Quad(new float[] {0, hiA, a, 0, loA, a, 0, loB, b, 0, hiB, b}, -1, 0, 0, MINUS_X));
        }
        List<Face> out = new ArrayList<>(kind == STRAIGHT ? half.size() : half.size() * 2);
        for (Quad q : half) out.add(turned(f, q));
        if (kind != STRAIGHT) for (Quad q : half) out.add(turned(f, q.mirrored()));
        return out;
    }

    /** The outline sliced front to back: each strip {a, b, low at a, low at b, high at a, high at b}. */
    private static List<float[]> strips(float[] p) {
        int n = p.length / 2;
        TreeSet<Float> zs = new TreeSet<>();
        for (int i = 0; i < n; i++) zs.add(p[i * 2]);
        List<float[]> out = new ArrayList<>();
        Float prev = null;
        for (Float z : zs) {
            if (prev != null && z - prev > E) {
                float a = prev, b = z, mid = (a + b) / 2F;
                float[] ya = new float[2], yb = new float[2], ym = new float[2];
                int found = 0;
                for (int i = 0; i < n && found < 2; i++) {
                    float z0 = p[i * 2], y0 = p[i * 2 + 1], z1 = p[(i + 1) % n * 2], y1 = p[(i + 1) % n * 2 + 1];
                    if (Math.abs(z1 - z0) < E || mid < Math.min(z0, z1) || mid > Math.max(z0, z1)) continue;
                    ya[found] = y0 + (y1 - y0) * (a - z0) / (z1 - z0);
                    yb[found] = y0 + (y1 - y0) * (b - z0) / (z1 - z0);
                    ym[found] = y0 + (y1 - y0) * (mid - z0) / (z1 - z0);
                    found++;
                }
                int lo = ym[0] <= ym[1] ? 0 : 1, hi = 1 - lo;
                out.add(new float[] {a, b, ya[lo], yb[lo], ya[hi], yb[hi]});
            }
            prev = z;
        }
        return out;
    }

    private static Face turned(Direction f, Quad q) {
        float[] c = q.c();
        float nx = q.nx(), ny = q.ny(), nz = q.nz();
        int cull = q.cull();
        float[] r = new float[12];
        for (int i = 0; i < 4; i++) {
            float x = c[i * 3], z = c[i * 3 + 2];
            r[i * 3] = switch (f) {
                case EAST -> z;
                case NORTH -> 1 - x;
                case WEST -> 1 - z;
                default -> x;
            };
            r[i * 3 + 1] = c[i * 3 + 1];
            r[i * 3 + 2] = switch (f) {
                case EAST -> 1 - x;
                case NORTH -> 1 - z;
                case WEST -> x;
                default -> z;
            };
        }
        float wx = switch (f) {
            case EAST -> nz;
            case NORTH -> -nx;
            case WEST -> -nz;
            default -> nx;
        };
        float wz = switch (f) {
            case EAST -> -nx;
            case NORTH -> -nz;
            case WEST -> nx;
            default -> nz;
        };
        Direction side = switch (cull) {
            case FRONT -> f;
            case BACK -> f.getOpposite();
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
            case PLUS_X -> f.getCounterClockWise();
            case MINUS_X -> f.getClockWise();
            default -> null;
        };
        return new Face(r, wx, ny, wz, dominant(wx, ny, wz), side);
    }

    /** Which side's texture a face wears; a slope exactly between the top and a side wears the side's. */
    private static Direction dominant(float x, float y, float z) {
        float ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ay > Math.max(ax, az) + 1.0E-3F) return y > 0 ? Direction.UP : Direction.DOWN;
        if (ax > az) return x > 0 ? Direction.EAST : Direction.WEST;
        return z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ---- collision: boxes, slopes in steps of an eighth ----------------------------------------------------------------------

    private static VoxelShape voxel(float[] p, Direction f) {
        if (p.length == 8 && p[0] == 1 && p[1] == 0 && p[3] == 1 && p[4] == 0) return Shapes.block();
        VoxelShape out = Shapes.empty();
        for (float[] s : strips(p)) {
            float rise = Math.max(Math.abs(s[3] - s[2]), Math.abs(s[5] - s[4]));
            int steps = Math.max(1, (int) Math.ceil(rise * 8F - 1.0E-3F));
            for (int i = 0; i < steps; i++) {
                float t0 = i / (float) steps, t1 = (i + 1) / (float) steps, tm = (t0 + t1) / 2F;
                float z0 = s[0] + (s[1] - s[0]) * t0, z1 = s[0] + (s[1] - s[0]) * t1;
                float lo = s[2] + (s[3] - s[2]) * tm, hi = s[4] + (s[5] - s[4]) * tm;
                out = Shapes.or(out, box(f, lo, hi, z0, z1));
            }
        }
        return out.optimize();
    }

    private static VoxelShape box(Direction f, double lo, double hi, double a, double b) {
        return switch (f) {
            case EAST -> Shapes.box(a, lo, 0, b, hi, 1);
            case NORTH -> Shapes.box(0, lo, 1 - b, 1, hi, 1 - a);
            case WEST -> Shapes.box(1 - b, lo, 0, 1 - a, hi, 1);
            default -> Shapes.box(0, lo, a, 1, hi, b);
        };
    }
}
