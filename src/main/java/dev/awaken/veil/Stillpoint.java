package dev.awaken.veil;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.DreamHooks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import dev.awaken.veil.block.StillpointDoorBlock;
import dev.awaken.veil.world.TempleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Stillpoint: a calm void above a sea of clouds at dawn, a temple at the origin. Reached by meditating 20 seconds or
 * from a Stillpoint shrine; left by meditating again. Time is slow here for you: no hunger, energy comes back faster,
 * nothing hunts you. The Out door sends your spirit back into the Waking world while your body sits safe at the seat; the
 * In door only opens while you are dreaming.
 */
public final class Stillpoint {
    private Stillpoint() {}

    public static final Vec3 ARRIVAL = new Vec3(0.5, TempleBuilder.FLOOR + 1.5, 0.5);
    /** Facing east, into the sunrise. */
    public static final float ARRIVAL_YAW = -90F;
    static final String VISITS = "awaken:stillpoint_visits";
    private static final int VOID_Y = 40;

    private static final Map<UUID, int[]> FOOD = new HashMap<>();
    private static final Map<UUID, Long> DOOR_COOLDOWN = new HashMap<>();

    static ServerLevel level(MinecraftServer server) {
        return server.getLevel(Veil.STILLPOINT);
    }

    /** Go to the temple, remembering where you were. */
    public static boolean enter(ServerPlayer sp) {
        if (!Veil.enabled() || Veil.isSpirit(sp) || Veil.inStillpoint(sp)) return false;
        ServerLevel still = level(sp.level().getServer());
        if (still == null) {
            Signal.toast(sp, Component.literal("The Stillpoint is out of reach."));
            return false;
        }
        Meditation.stop(sp);
        VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
        mem.stillpointReturn = Optional.of(new VeilMemory.ReturnPoint(GlobalPos.of(sp.level().dimension(), sp.blockPosition()), sp.getYRot(), sp.getXRot()));
        still.getChunk(0, 0);
        sp.stopRiding();
        Projection.teleport(sp, still, ARRIVAL, ARRIVAL_YAW, 0F);
        sp.resetFallDistance();
        FOOD.remove(sp.getUUID());
        still.playSound(null, ARRIVAL.x, ARRIVAL.y, ARRIVAL.z, VeilContent.BELL.get(), SoundSource.AMBIENT, 0.7F, 0.8F);
        boolean first = Progression.profile(sp).counter(VISITS) == 0;
        Progression.count(sp, VISITS, 1);
        if (first) {
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The Stillpoint"))
                    .body(Component.literal("Time is slow here. Nothing is hunting you. Stay as long as you like."))
                    .row("Leave", "Meditate again")
                    .row("East door", "Out, into the Veil")
                    .row("West door", "In, while you sleep")
                    .ttl(20 * 12).priority(5).build());
        }
        DirectorLog.log(sp, "veil", "entered the Stillpoint");
        return true;
    }

    public static void fromShrine(ServerPlayer sp, BlockPos shrine) {
        if (Veil.isSpirit(sp)) return;
        if (!Progression.profile(sp).awakened()) {
            Signal.toast(sp, Component.literal("It's warm. Nothing else happens. Not yet."));
            return;
        }
        if (Veil.inStillpoint(sp)) {
            leave(sp);
            return;
        }
        enter(sp);
    }

    /** Back to where you came from (or your spawn). */
    public static void leave(ServerPlayer sp) {
        if (Veil.isSpirit(sp)) return;
        MinecraftServer server = sp.level().getServer();
        VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
        ServerLevel dest = null;
        Vec3 pos = null;
        float yaw = sp.getYRot(), pitch = sp.getXRot();
        if (mem.stillpointReturn.isPresent()) {
            VeilMemory.ReturnPoint r = mem.stillpointReturn.get();
            dest = server.getLevel(r.pos().dimension());
            pos = Vec3.atBottomCenterOf(r.pos().pos());
            yaw = r.yaw();
            pitch = r.pitch();
        }
        if (dest == null || dest.dimension() == Veil.STILLPOINT) {
            var cfg = sp.getRespawnConfig();
            dest = cfg == null ? null : server.getLevel(cfg.respawnData().dimension());
            if (dest != null) pos = Vec3.atBottomCenterOf(cfg.respawnData().pos()).add(0, 0.6, 0);
        }
        if (dest == null || pos == null) {
            dest = server.overworld();
            BlockPos spawn = dest.getRespawnData().pos();
            dest.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
            pos = Vec3.atBottomCenterOf(dest.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn));
        }
        Meditation.stop(sp);
        mem.stillpointReturn = Optional.empty();
        FOOD.remove(sp.getUUID());
        Projection.teleport(sp, dest, pos, yaw, pitch);
        sp.resetFallDistance();
        dest.playSound(null, pos.x, pos.y, pos.z, VeilContent.RETURN.get(), SoundSource.PLAYERS, 0.8F, 1.2F);
        DirectorLog.log(sp, "veil", "left the Stillpoint");
    }

    /** One of the temple doors was touched or walked into. */
    public static void useDoor(ServerPlayer sp, StillpointDoorBlock.Kind kind, boolean clicked) {
        if (Veil.isSpirit(sp)) return;
        long now = sp.level().getGameTime();
        Long last = DOOR_COOLDOWN.get(sp.getUUID());
        if (last != null && now - last < (clicked ? 10 : 60)) return;
        DOOR_COOLDOWN.put(sp.getUUID(), now);
        if (kind == StillpointDoorBlock.Kind.OUT) {
            if (!Progression.profile(sp).awakened()) {
                Signal.toast(sp, Component.literal("The light holds still. Not yet."));
                return;
            }
            VeilMemory mem = sp.getData(VeilContent.MEMORY.get());
            if (Veil.inStillpoint(sp)) {
                // sit at the seat first: that is where the body will wait
                Projection.teleport(sp, sp.level(), ARRIVAL, ARRIVAL_YAW, 0F);
            }
            Projection.start(sp, false, mem.stillpointReturn.orElse(null));
        } else {
            if (DreamHooks.isDreaming(sp)) {
                if (!DreamHooks.replay(sp, 0)) Signal.toast(sp, Component.literal("The door opens onto nothing yet."));
            } else {
                Signal.toast(sp, Component.literal("The door is dark. It only opens while you sleep."));
            }
        }
    }

    /** Stillpoint rules, every tick for players here. */
    static void tick(ServerPlayer sp) {
        if (!Veil.inStillpoint(sp)) {
            if (!FOOD.isEmpty()) FOOD.remove(sp.getUUID());
            return;
        }
        FoodData food = sp.getFoodData();
        int[] kept = FOOD.computeIfAbsent(sp.getUUID(), k -> new int[]{food.getFoodLevel(), Float.floatToIntBits(food.getSaturationLevel())});
        if (food.getFoodLevel() < kept[0]) food.setFoodLevel(kept[0]);
        float sat = Float.intBitsToFloat(kept[1]);
        if (food.getSaturationLevel() < sat) food.setSaturation(sat);
        if (food.getFoodLevel() > kept[0]) kept[0] = food.getFoodLevel();
        if (sp.tickCount % 40 == 0) {
            boolean dreaming = DreamHooks.isDreaming(sp);
            dev.awaken.core.AwakenNet.send(sp, new dev.awaken.veil.net.VeilPayloads.State(false, 0, 1, 0, 0, Vec3.ZERO, false, Vec3.ZERO, -1, -1,
                    false, dreaming, false, 0));
        }
        if (sp.getY() < VOID_Y && !Veil.isSpirit(sp)) {
            Projection.teleport(sp, sp.level(), ARRIVAL, ARRIVAL_YAW, 0F);
            sp.resetFallDistance();
            sp.setDeltaMovement(Vec3.ZERO);
            Signal.toast(sp, Component.literal("The void hands you back."));
        }
    }

    static void forget(UUID id) {
        FOOD.remove(id);
        DOOR_COOLDOWN.remove(id);
    }
}
