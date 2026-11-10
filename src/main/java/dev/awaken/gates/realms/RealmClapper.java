package dev.awaken.gates.realms;

import com.mojang.math.Transformation;
import dev.awaken.Awaken;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.realms.land.HourCrypt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Hour Crypt's bell is the size of a house and built of blocks, so it stays still; its tongue moves (SPEC 50.8). The tongue is a few block
 * displays hung once at the middle of its ball and kept with the Realm (they are saved with its chunks and go when its region is erased). On a toll
 * it swings about its pivot, {@link #SWING} ticks a swing, and hangs straight again when the doors lift. The server only ever sets where a swing
 * ends: the display's own interpolation carries it there, so nothing here runs every tick.
 */
public final class RealmClapper {
    private RealmClapper() {}

    static final String TAG = "awaken_realm_clapper";
    /** How far it swings each way, and how long one swing takes. */
    static final float DEGREES = 14;
    static final int SWING = 30;

    /** One piece of the tongue: its block, its size, and where its middle is from the middle of the ball. */
    private record Part(String block, float sx, float sy, float sz, float oy) {}

    private static final List<Part> SHAPE = shape();
    public static final int PARTS = SHAPE.size();

    private static List<Part> shape() {
        List<Part> s = new ArrayList<>();
        // the ball, three boxes through one another so it reads round; no two faces share a plane, or they'd flicker
        s.add(new Part("knell_iron", 8, 5.6F, 5.6F, 0));
        s.add(new Part("knell_iron", 5.8F, 8, 5.8F, 0));
        s.add(new Part("knell_iron", 6, 6, 8, 0));
        // the shaft, from the ball up to the pivot
        for (int y = 8; y <= HourCrypt.CLAPPER_DROP - 4; y += 8) s.add(new Part("knell_iron", 2, 8, 2, y));
        return List.copyOf(s);
    }

    /** One Realm's tongue: its displays by id, and the swing it was last sent on (-1 at rest). */
    private static final class Rig {
        final List<Display.BlockDisplay> parts = new ArrayList<>();
        int swing = -1;
    }

    private static final Map<Integer, Rig> RIGS = new HashMap<>();

    /** Where a swing ends: out one way, back the other, and straight at the last. */
    static float target(int swing) {
        return swing < 0 || swing >= RealmToll.SHUT / SWING - 1 ? 0 : swing % 2 == 0 ? DEGREES : -DEGREES;
    }

    /**
     * Called with the toll, twice a second for each player in the Realm. into is how many ticks the doors have been down, or -1 when they're up.
     * Returns true on the tick a swing ends on a stroke, so the toll can sound it.
     */
    static boolean tick(ServerLevel level, GateRecord g, RealmRef ref, Land land, int into) {
        if (!(land instanceof HourCrypt)) return false;
        BlockPos at = Realms.world(ref.cell(), HourCrypt.CLAPPER_X, HourCrypt.CLAPPER_Y, HourCrypt.CLAPPER_Z);
        Rig rig = RIGS.get(g.id);
        if (rig == null || !whole(rig, level)) {
            if (!level.areEntitiesLoaded(ChunkPos.pack(at))) return false;
            rig = new Rig();
            rig.parts.addAll(hang(level, at));
            RIGS.put(g.id, rig);
            if (rig.parts.size() != PARTS) return false;
        }
        int swing = into < 0 ? -1 : Math.min(RealmToll.SHUT / SWING - 1, into / SWING);
        if (swing == rig.swing) return false;
        boolean stroke = swing > 0;
        rig.swing = swing;
        swing(level, rig.parts, target(swing), swing < 0 ? 10 : Math.max(5, SWING - (into - swing * SWING)));
        if (stroke) {
            // dust shaken off the yoke by every stroke: the gates' own grit, not a vanilla puff
            level.sendParticles(GatesRegistry.P_GRIT.get(), true, false, at.getX() + 0.5, at.getY() + HourCrypt.CLAPPER_YOKE - 1, at.getZ() + 0.5, 40, 9, 0.6, 2.5, 0.01);
        }
        return stroke;
    }

    /** Whether the tongue in hand is still the one in this world: its chunks may have gone and come back, or the server restarted. */
    private static boolean whole(Rig rig, ServerLevel level) {
        if (rig.parts.size() != PARTS) return false;
        for (int i = 0; i < rig.parts.size(); i++) if (rig.parts.get(i).isRemoved() || rig.parts.get(i).level() != level) return false;
        return true;
    }

    /**
     * The tongue at this place, in order: the one already hanging there, or a new one. at is the middle of its ball. Leftovers of a broken one
     * are cleared first, so there is never more than one.
     */
    public static List<Display.BlockDisplay> hang(ServerLevel level, BlockPos at) {
        List<Display.BlockDisplay> found = level.getEntitiesOfClass(Display.BlockDisplay.class, new AABB(at).inflate(2), e -> e.entityTags().contains(TAG));
        if (found.size() == PARTS) {
            Display.BlockDisplay[] inOrder = new Display.BlockDisplay[PARTS];
            for (Display.BlockDisplay d : found) for (int i = 0; i < PARTS; i++) if (d.entityTags().contains(TAG + "_" + i)) inOrder[i] = d;
            if (!java.util.Arrays.asList(inOrder).contains(null)) return List.of(inOrder);
        }
        for (Display.BlockDisplay d : found) d.discard();
        List<Display.BlockDisplay> out = new ArrayList<>();
        for (int i = 0; i < PARTS; i++) {
            Part p = SHAPE.get(i);
            BlockState state = BuiltInRegistries.BLOCK.getValue(Awaken.id(p.block())).defaultBlockState();
            CompoundTag tag = new CompoundTag();
            tag.put("block_state", BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow());
            tag.put("transformation", Transformation.CODEC.encodeStart(NbtOps.INSTANCE, pose(p, 0)).getOrThrow());
            // seen from the head of the nave, and never culled by a box it has outgrown
            tag.putFloat("view_range", 4.0F);
            Entity e = EntityType.loadEntityRecursive(EntityTypes.BLOCK_DISPLAY, tag, level, EntitySpawnReason.TRIGGERED, ent -> {
                ent.snapTo(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 0, 0);
                return ent;
            });
            if (!(e instanceof Display.BlockDisplay d)) continue;
            d.addTag(TAG);
            d.addTag(TAG + "_" + i);
            level.addFreshEntity(d);
            out.add(d);
        }
        return out;
    }

    /** Sends the whole tongue to an angle about its pivot, to arrive in so many ticks. A display's setters are private: its saved form is the way in. */
    public static void swing(ServerLevel level, List<Display.BlockDisplay> parts, float degrees, int ticks) {
        for (int i = 0; i < parts.size() && i < PARTS; i++) {
            Display.BlockDisplay d = parts.get(i);
            TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            d.saveWithoutId(saved);
            CompoundTag tag = saved.buildResult();
            tag.put("transformation", Transformation.CODEC.encodeStart(NbtOps.INSTANCE, pose(SHAPE.get(i), degrees)).getOrThrow());
            tag.putInt("interpolation_duration", ticks);
            tag.putInt("start_interpolation", 0);
            d.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
        }
    }

    /** Where a display's swing is headed, as it would be saved: what a test reads. */
    public static String heading(ServerLevel level, Display.BlockDisplay d) {
        TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        d.saveWithoutId(saved);
        return String.valueOf(saved.buildResult().get("transformation"));
    }

    /** A part turned about the pivot (straight above the ball), in the plane across the nave so the whole crypt sees it swing. */
    private static Transformation pose(Part p, float degrees) {
        Quaternionf turn = new Quaternionf().rotateZ((float) Math.toRadians(degrees));
        // the part's low corner from the pivot, turned, then back to the middle of the ball, where the displays stand
        Vector3f corner = new Vector3f(-p.sx() / 2, p.oy() - p.sy() / 2 - HourCrypt.CLAPPER_DROP, -p.sz() / 2);
        turn.transform(corner);
        corner.add(0, HourCrypt.CLAPPER_DROP, 0);
        return new Transformation(corner, turn, new Vector3f(p.sx(), p.sy(), p.sz()), new Quaternionf());
    }
}
