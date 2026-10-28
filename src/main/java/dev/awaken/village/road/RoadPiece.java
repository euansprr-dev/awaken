package dev.awaken.village.road;

import dev.awaken.city.SettlementPaint;
import dev.awaken.village.world.VillageWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * One leg of a road (SPEC §28.9): it keeps its own stretch of the line (two points more each side, so its ends meet its
 * neighbours'), its waymarkers and where its lanterns stand, so after a restart it paints without the road being laid
 * again. Each chunk it touches paints its share through the settlement painter; the land under it was eased to its
 * heights by its markers' thin beards (none over water: a bridge never dams a river).
 */
public class RoadPiece extends StructurePiece implements net.neoforged.neoforge.common.world.PieceBeardifierModifier {
    static long paintNanos, chunks, blocks;

    private final String key;
    private final int legIndex, w0, a, b, cls;
    private final float[] xs, zs;
    private final int[] ys;
    private final byte[] fl, fam;
    private final List<Road.Marker> markers;
    private transient RoadPaint paint;

    public RoadPiece(Road.Leg leg) {
        this(leg.road().key(), leg.index(), window(leg), leg.road().cls(), leg);
    }

    private RoadPiece(String key, int legIndex, int[] win, int cls, Road.Leg leg) {
        super(VillageWorldgen.ROAD_PIECE.get(), 0, new BoundingBox(0, 0, 0, 0, 0, 0));
        Road r = leg.road();
        this.key = key;
        this.legIndex = legIndex;
        this.cls = cls;
        this.w0 = win[0];
        int n = win[1] - win[0] + 1;
        xs = new float[n];
        zs = new float[n];
        ys = new int[n];
        fl = new byte[n];
        fam = new byte[n];
        int last = r.points() - 1;
        for (int k = 0; k < n; k++) {
            int i = w0 + k;
            xs[k] = r.x()[i];
            zs[k] = r.z()[i];
            ys[k] = r.y()[i];
            fam[k] = r.fam()[i];
            byte f = r.flags()[i];
            if (r.lit(i)) f |= RoadPaint.LIT;
            if (r.to().town() && last - i <= 40 || r.from().town() && i <= 40) f |= RoadPaint.TOWN;
            fl[k] = f;
        }
        this.a = leg.i0() - w0;
        this.b = leg.i1() - w0;
        List<Road.Marker> ms = new ArrayList<>();
        for (Road.Marker m : r.markers()) if (m.point() >= leg.i0() && (m.point() < leg.i1() || m.point() == leg.i1() && leg.i1() == last)) ms.add(m);
        this.markers = ms;
        setOrientation(null);
        this.boundingBox = box(paint());
    }

    /** The window of the road a leg keeps: its points and four more either side (a viaduct's span reaches its piers). */
    private static int[] window(Road.Leg leg) {
        return new int[]{Math.max(0, leg.i0() - 4), Math.min(leg.road().points() - 1, leg.i1() + 4)};
    }

    public RoadPiece(CompoundTag tag) {
        super(VillageWorldgen.ROAD_PIECE.get(), tag);
        key = tag.getStringOr("key", "?");
        legIndex = tag.getIntOr("leg", 0);
        cls = tag.getIntOr("cls", 0);
        w0 = tag.getIntOr("w0", 0);
        a = tag.getIntOr("a", 0);
        b = tag.getIntOr("b", 1);
        int[] bx = tag.getIntArray("x").orElse(new int[2]), bz = tag.getIntArray("z").orElse(new int[2]);
        xs = new float[bx.length];
        zs = new float[bz.length];
        for (int i = 0; i < bx.length; i++) {
            xs[i] = Float.intBitsToFloat(bx[i]);
            zs[i] = Float.intBitsToFloat(bz[i]);
        }
        ys = tag.getIntArray("y").orElse(new int[xs.length]);
        fl = tag.getByteArray("f").orElse(new byte[xs.length]);
        fam = tag.getByteArray("fam").orElse(new byte[xs.length]);
        List<Road.Marker> ms = new ArrayList<>();
        ListTag list = tag.getListOrEmpty("marks");
        for (int i = 0; i < list.size(); i++) {
            CompoundTag m = list.getCompoundOrEmpty(i);
            List<Road.Arm> arms = new ArrayList<>();
            ListTag al = m.getListOrEmpty("arms");
            for (int k = 0; k < al.size(); k++) {
                CompoundTag at = al.getCompoundOrEmpty(k);
                arms.add(new Road.Arm(at.getIntOr("s", 0), at.getStringOr("n", ""), at.getIntOr("d", 0)));
            }
            ms.add(new Road.Marker(m.getIntOr("p", 0), m.getIntOr("x", 0), m.getIntOr("y", 0), m.getIntOr("z", 0), arms, m.getBooleanOr("cairn", false)));
        }
        markers = ms;
        setOrientation(null);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putString("key", key);
        tag.putInt("leg", legIndex);
        tag.putInt("cls", cls);
        tag.putInt("w0", w0);
        tag.putInt("a", a);
        tag.putInt("b", b);
        int[] bx = new int[xs.length], bz = new int[zs.length];
        for (int i = 0; i < xs.length; i++) {
            bx[i] = Float.floatToIntBits(xs[i]);
            bz[i] = Float.floatToIntBits(zs[i]);
        }
        tag.putIntArray("x", bx);
        tag.putIntArray("z", bz);
        tag.putIntArray("y", ys);
        tag.putByteArray("f", fl);
        tag.putByteArray("fam", fam);
        ListTag list = new ListTag();
        for (Road.Marker m : markers) {
            CompoundTag mt = new CompoundTag();
            mt.putInt("p", m.point());
            mt.putInt("x", m.x());
            mt.putInt("y", m.y());
            mt.putInt("z", m.z());
            mt.putBoolean("cairn", m.cairn());
            ListTag al = new ListTag();
            for (Road.Arm arm : m.arms()) {
                CompoundTag at = new CompoundTag();
                at.putInt("s", arm.side());
                at.putString("n", arm.name());
                at.putInt("d", arm.distance());
                al.add(at);
            }
            mt.put("arms", al);
            list.add(mt);
        }
        tag.put("marks", list);
    }

    RoadPaint paint() {
        RoadPaint p = paint;
        if (p == null) paint = p = new RoadPaint(w0, xs, zs, ys, fl, fam, a, b, cls, markers);
        return p;
    }

    /** The leg's raster (its trees' band) and its markers, from a little under its lowest point to well over its highest. */
    private static BoundingBox box(RoadPaint p) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int y : p.ys) {
            lo = Math.min(lo, y);
            hi = Math.max(hi, y);
        }
        return new BoundingBox(p.ox + p.rx0, lo - 20, p.oz + p.rz0, p.ox + p.rx0 + p.rw - 1, hi + 24, p.oz + p.rz0 + p.rd - 1);
    }

    @Override
    public BoundingBox getBeardifierBox() {
        return boundingBox;
    }

    @Override
    public net.minecraft.world.level.levelgen.structure.TerrainAdjustment getTerrainAdjustment() {
        return net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE;
    }

    @Override
    public int getGroundLevelDelta() {
        return 0;
    }

    public String key() {
        return key;
    }

    int legIndex() {
        return legIndex;
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator, RandomSource random,
                            BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos) {
        long t0 = System.nanoTime();
        RoadPaint p = paint();
        var chunk = level.getChunk(chunkPos.x(), chunkPos.z());
        SettlementPaint.Land land = RoadBanks.enabled(level.enabledFeatures()) ? RoadBanks.land(this, chunk, structureManager) : null;
        int n = SettlementPaint.paintChunk(p, chunkPos.x(), chunkPos.z(), level.getSeed(), chunk, land);
        n += SettlementPaint.sweep(level, p, chunkPos.x(), chunkPos.z());
        for (Road.Marker m : markers) RoadMarks.words(chunk, level.registryAccess(), p, m);
        paintNanos += System.nanoTime() - t0;
        chunks++;
        blocks += n;
    }

    static String stats() {
        return String.format(java.util.Locale.ROOT, "%d road chunks painted, %.2f ms and %d blocks a chunk on average", chunks,
                chunks == 0 ? 0 : paintNanos / 1e6 / chunks, chunks == 0 ? 0 : blocks / chunks);
    }
}
