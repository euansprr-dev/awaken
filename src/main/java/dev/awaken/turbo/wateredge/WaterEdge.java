package dev.awaken.turbo.wateredge;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.farsight.client.FarSightClient;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * Water edges (Far Sight, SPEC §20): a water block's side is meshed wherever its neighbour is air, and a chunk the
 * client hasn't received yet reads as air, so the loaded area's edge stands in water walls: thin pale lines over Far
 * Sight's far sea after a jump or in fast flight. While Far Sight draws, a water side that looks into a chunk the client
 * doesn't have is left out; the chunk's arrival marks its neighbours' sections dirty, so the face comes back if needed.
 *
 * <p>A Turbo feature. The chunks in the pack are meshed by Sodium, so the hook is in Sodium's fluid renderer
 * (dev.awaken.turbo.wateredge.mixin, its own client mixin config, not required): it turns on only for the game,
 * NeoForge and Sodium versions it was read against, only once the probe has seen the hook applied, and only with the
 * client setting {@code farSightWaterEdges} on; otherwise, and on the first thing that goes wrong, water meshes as
 * Sodium's own.
 */
public final class WaterEdge {
    private WaterEdge() {}

    /** The versions the hook was read against and tested on. Any other: off, with one log line. */
    static final String MINECRAFT = "26.3", NEOFORGE = "26.3.0.95", SODIUM = "0.9.3-beta.1+mc26.3";

    // read by section-meshing worker threads
    private static volatile boolean active;
    private static volatile @Nullable ClientLevel level;
    /** Set by the hook's mixin when Sodium's fluid renderer is built with it: the probe. */
    private static volatile boolean applied;
    private static int versionOk = -1;
    private static volatile boolean failed;
    private static @Nullable Boolean forced;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> refresh());
    }

    /** The hook's mixin reports in (Sodium built a fluid renderer with it). */
    public static void applied() {
        applied = true;
    }

    private static void refresh() {
        try {
            boolean want = forced != null ? forced : setting();
            if (versionOk < 0) versionOk = versionsMatch() ? 1 : 0;
            level = Minecraft.getInstance().level;
            active = want && !failed && versionOk == 1 && applied && level != null && FarSightClient.drawing();
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    private static boolean setting() {
        return !AwakenConfig.Client.SPEC.isLoaded() || AwakenConfig.Client.FAR_SIGHT_WATER_EDGES.get();
    }

    /** Whether the water side at {@code pos} toward {@code side} looks into a chunk the client doesn't have. Worker threads. */
    public static boolean hides(BlockPos pos, Direction side) {
        if (!active || side.getAxis() == Direction.Axis.Y) return false;
        try {
            ClientLevel l = level;
            int x = pos.getX() + side.getStepX(), z = pos.getZ() + side.getStepZ();
            return l != null && !l.getChunkSource().hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        } catch (RuntimeException e) {
            fail(e);
            return false;
        }
    }

    private static void fail(Throwable e) {
        if (failed) return;
        failed = true;
        active = false;
        Awaken.LOG.warn("[wateredge] off: the hook failed", e);
    }

    private static boolean versionsMatch() {
        String mc = SharedConstants.getCurrentVersion().name();
        String neo = version("neoforge"), sodium = version("sodium");
        if (MINECRAFT.equals(mc) && NEOFORGE.equals(neo) && SODIUM.equals(sodium)) return true;
        if (!"-".equals(sodium)) {
            Awaken.LOG.warn("[wateredge] off: verified on Minecraft {}, NeoForge {}, Sodium {}; this is {}, {}, {}",
                    MINECRAFT, NEOFORGE, SODIUM, mc, neo, sodium);
        }
        return false;
    }

    private static String version(String mod) {
        return ModList.get().getModContainerById(mod).map(c -> c.getModInfo().getVersion().toString()).orElse("-");
    }

    /** The autopilot's {@code farsight wateredge on|off|setting}: overrides the client setting for the following scenes. */
    public static String autopilot(String arg) {
        switch (arg.trim()) {
            case "on" -> forced = true;
            case "off" -> forced = false;
            case "setting" -> forced = null;
            default -> {
                return "unknown: " + arg.trim() + " (on|off|setting)";
            }
        }
        refresh();
        return (forced == null ? "setting" : forced ? "on" : "off") + (failed ? ", failed" : applied && versionOk == 1 ? "" : ", unavailable");
    }
}
