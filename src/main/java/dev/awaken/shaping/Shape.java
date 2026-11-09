package dev.awaken.shaping;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * The cuts a block can take (SPEC §52.2). Each is one closed outline seen from the side, pushed straight across the block,
 * and turned by the block's facing; the mesh, the collision and the Looks preview all come from the same outline.
 */
public enum Shape implements StringRepresentable {
    FULL("full", new float[] {1, 0, 1, 1, 0, 1, 0, 0}),
    HALF_BOTTOM("half_bottom", new float[] {1, 0, 1, .5F, 0, .5F, 0, 0}),
    HALF_TOP("half_top", new float[] {1, .5F, 1, 1, 0, 1, 0, .5F}),
    SLAB("slab", new float[] {.5F, 0, .5F, 1, 0, 1, 0, 0}),
    NOTCH("notch", new float[] {1, 0, 1, .5F, .5F, .5F, .5F, 1, 0, 1, 0, 0}),
    BEVEL("bevel", new float[] {1, 0, 1, .5F, .5F, 1, 0, 1, 0, 0}),
    ROUND("round", round()),
    UNDER("under", new float[] {.5F, 0, .5F, .5F, 1, .5F, 1, 1, 0, 1, 0, 0}),
    COVE("cove", cove());

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
    private final float[] outline;
    private final List<Face>[] faces;
    private final VoxelShape[] voxels;

    @SuppressWarnings("unchecked")
    Shape(String id, float[] outline) {
        this.id = id;
        this.outline = outline;
        this.faces = new List[4];
        this.voxels = new VoxelShape[4];
        for (Direction f : Direction.Plane.HORIZONTAL) {
            this.faces[f.get2DDataValue()] = List.copyOf(build(outline, f));
            this.voxels[f.get2DDataValue()] = voxel(outline, f);
        }
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    public List<Face> faces(Direction facing) {
        return this.faces[facing.get2DDataValue()];
    }

    public VoxelShape voxel(Direction facing) {
        return this.voxels[facing.get2DDataValue()];
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

    private static List<Face> build(float[] p, Direction f) {
        List<Face> out = new ArrayList<>();
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
            out.add(turned(f, new float[] {0, qy, qz, 0, py, pz, 1, py, pz, 1, qy, qz}, 0, ny, nz, cull));
        }
        for (float[] s : strips(p)) {
            float a = s[0], b = s[1], loA = s[2], loB = s[3], hiA = s[4], hiB = s[5];
            out.add(turned(f, new float[] {1, hiB, b, 1, loB, b, 1, loA, a, 1, hiA, a}, 1, 0, 0, PLUS_X));
            out.add(turned(f, new float[] {0, hiA, a, 0, loA, a, 0, loB, b, 0, hiB, b}, -1, 0, 0, MINUS_X));
        }
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

    private static Face turned(Direction f, float[] c, float nx, float ny, float nz, int cull) {
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
