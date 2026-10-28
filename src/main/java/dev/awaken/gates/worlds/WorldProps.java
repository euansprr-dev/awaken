package dev.awaken.gates.worlds;

import com.mojang.math.Transformation;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.GateService;
import dev.awaken.gates.GatesRegistry;
import dev.awaken.gates.world.FloorLayout;
import dev.awaken.gates.world.Hash;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Swinging bells (SPEC §38.6): a floor's {@code prop} marks shown as block displays hung at their canon while anyone is
 * near, and swung when they ring. The display turns about its own position (the hinge) by its pitch, which the game
 * eases between updates, so a swing costs one rotation a bell every few ticks. Never saved: a prop loaded from disk
 * leaves at once and is made again when someone comes near.
 */
public final class WorldProps {
    private WorldProps() {}

    public static final String TAG = "awaken_world_prop";
    /** Shown within this of a player on its floor, gone once nobody is within {@link #FAR}. */
    static final double NEAR = 96, FAR = 128;
    /** Ticks between swing updates, and the display's own easing between them. */
    static final int STEP = 3;
    /** Degrees a full strike swings a bell, and how fast the swing dies away (ticks to fall to a third). */
    static final float SWING = 26f, DAMP = 70f;

    static final class Prop {
        final FloorLayout.Mark mark;
        final double r;
        final int h;
        final float yaw;
        Display.BlockDisplay display;
        float amp;
        long since;

        Prop(FloorLayout.Mark mark) {
            this.mark = mark;
            this.r = Integer.parseInt(mark.id().substring(mark.id().indexOf(':') + 1)) / 10.0;
            this.h = mark.y1() - mark.y0() + 1;
            this.yaw = (Hash.of(mark.x0(), mark.z0(), 41) & 1) == 0 ? 0f : 90f;
        }

        /** A bell this prop shows: the same box. */
        boolean shows(FloorLayout.Mark bell) {
            return bell.x0() == mark.x0() && bell.y0() == mark.y0() && bell.z0() == mark.z0() && bell.x1() == mark.x1();
        }
    }

    static final Map<WorldLive.Key, List<Prop>> FLOORS = new HashMap<>();
    private static long now;

    static void clear() {
        for (List<Prop> l : FLOORS.values()) for (Prop p : l) if (p.display != null) p.display.discard();
        FLOORS.clear();
    }

    static List<Prop> of(GateRecord g, FloorLayout f) {
        return FLOORS.computeIfAbsent(WorldLive.Key.of(g, f), k -> {
            List<Prop> l = new ArrayList<>();
            for (FloorLayout.Mark m : f.marks(FloorLayout.Mark.PROP)) l.add(new Prop(m));
            return l;
        });
    }

    /** Every half second for a player on a world floor: the props near them hang where they should. */
    static void near(ServerLevel level, ServerPlayer p, GateRecord g, FloorLayout f) {
        List<Prop> props = of(g, f);
        if (props.isEmpty()) return;
        BlockPos o = GateService.world(g, f, 0, 0, 0);
        for (Prop pr : props) {
            if (pr.display != null && !pr.display.isRemoved()) continue;
            double x = o.getX() + pr.mark.cx(), y = o.getY() + pr.mark.y1() + 1, z = o.getZ() + pr.mark.cz();
            if (p.distanceToSqr(x, y, z) > NEAR * NEAR || !level.hasChunkAt(BlockPos.containing(x, y, z))) continue;
            pr.display = spawn(level, x, y, z, pr, o);
        }
    }

    static Display.BlockDisplay spawn(ServerLevel level, double x, double y, double z, Prop pr, BlockPos o) {
        CompoundTag tag = new CompoundTag();
        BlockState bell = GatesRegistry.BELL_PROP.get().defaultBlockState();
        tag.put("block_state", BlockState.CODEC.encodeStart(NbtOps.INSTANCE, bell).getOrThrow());
        float w = (float) (pr.r * 2);
        Transformation t = new Transformation(new Vector3f(-w / 2, -pr.h, -w / 2), new Quaternionf(), new Vector3f(w, pr.h, w), new Quaternionf());
        tag.put("transformation", Transformation.CODEC.encodeStart(NbtOps.INSTANCE, t).getOrThrow());
        tag.putInt("teleport_duration", STEP);
        tag.putFloat("view_range", 2.5f);
        // lit by the lamp inside it, not by the dark at its canon
        BlockPos lamp = BlockPos.containing(x, y - pr.h / 2.0, z);
        int light = Math.max(6, level.getBrightness(LightLayer.BLOCK, lamp));
        tag.put("brightness", Brightness.CODEC.encodeStart(NbtOps.INSTANCE, new Brightness(light, 0)).getOrThrow());
        Entity e = EntityType.loadEntityRecursive(EntityTypes.BLOCK_DISPLAY, tag, level, EntitySpawnReason.TRIGGERED, ent -> {
            ent.snapTo(x, y, z, pr.yaw, 0f);
            return ent;
        });
        if (!(e instanceof Display.BlockDisplay d)) return null;
        d.addTag(TAG);
        level.addFreshEntity(d);
        return d;
    }

    /** A bell rang: its prop swings, harder the harder it was struck (1 a full strike). */
    static void swing(GateRecord g, FloorLayout f, FloorLayout.Mark bell, float strength) {
        for (Prop pr : of(g, f)) {
            if (!pr.shows(bell)) continue;
            float left = pr.amp * (float) Math.exp(-(now - pr.since) / DAMP);
            pr.amp = Math.min(SWING * 1.4f, Math.max(left, 0) + SWING * strength);
            pr.since = now;
            return;
        }
    }

    /** Every tick: swings eased along; every two seconds, props nobody is near go. */
    static void tick(ServerLevel level, long tick) {
        now = tick;
        if (FLOORS.isEmpty()) return;
        boolean sweep = tick % 40 == 0;
        if (tick % STEP != 0 && !sweep) return;
        for (Iterator<List<Prop>> it = FLOORS.values().iterator(); it.hasNext(); ) {
            List<Prop> props = it.next();
            boolean any = false;
            for (Prop pr : props) {
                Display.BlockDisplay d = pr.display;
                if (d == null || d.isRemoved()) continue;
                if (sweep && level.getNearestPlayer(d.getX(), d.getY(), d.getZ(), FAR, false) == null) {
                    d.discard();
                    pr.display = null;
                    continue;
                }
                any = true;
                if (pr.amp <= 0 || tick % STEP != 0) continue;
                long t = tick - pr.since;
                float a = pr.amp * (float) Math.exp(-t / DAMP);
                // a small bell swings quicker than a big one (a pendulum's period grows with its length)
                double period = 18 + pr.h * 4;
                float angle = a < 0.6f ? 0f : (float) (a * Math.sin(2 * Math.PI * t / period));
                if (a < 0.6f) pr.amp = 0;
                d.setXRot(angle);
            }
            if (sweep && !any && props.stream().allMatch(pr -> pr.display == null)) it.remove();
        }
    }

    /** Whether an entity is a prop (a prop saved with its chunk leaves when it loads). */
    public static boolean prop(Entity e) {
        return e.entityTags().contains(TAG);
    }
}
