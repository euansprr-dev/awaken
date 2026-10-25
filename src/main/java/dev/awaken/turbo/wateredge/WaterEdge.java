package dev.awaken.turbo.wateredge;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.farsight.client.FarSightClient;
import dev.awaken.turbo.wateredge.mixin.SectionRegionProbe;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * Water edges (Far Sight, SPEC §20): vanilla draws a water block's side face wherever its neighbour is air, and a chunk
 * the client hasn't received yet reads as air, so the loaded area's edge stands in water walls. Over fog they hardly
 * show; over Far Sight's far sea they are pale slabs after every jump and along a fast flight. While Far Sight draws,
 * a water side that looks into an unloaded chunk is left out (vanilla re-meshes the neighbours when that chunk arrives).
 *
 * <p>A Turbo feature: its two client mixins (dev.awaken.turbo.wateredge.mixin) turn on only for the game and NeoForge
 * versions they were read against, only when the probe finds the region accessor applied, and only with the client
 * setting {@code farSightWaterEdges} on; otherwise, and on the first thing that goes wrong, chunks mesh exactly as
 * vanilla does.
 */
public final class WaterEdge {
    private WaterEdge() {}

    /** The versions the hooks were read against and tested on. Any other: off, with one log line. */
    static final String MINECRAFT = "26.3", NEOFORGE = "26.3.0.95";

    // read by section-meshing worker threads
    private static volatile boolean active;
    // -1 unknown, 0 off (logged why), 1 on
    private static int versionOk = -1, probeOk = -1;
    private static volatile boolean failed;
    private static @Nullable Boolean forced;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> refresh());
    }

    private static void refresh() {
        boolean want = forced != null ? forced : setting();
        active = want && !failed && usable() && FarSightClient.drawing();
    }

    private static boolean setting() {
        try {
            return !AwakenConfig.Client.SPEC.isLoaded() || AwakenConfig.Client.FAR_SIGHT_WATER_EDGES.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean usable() {
        if (versionOk < 0) versionOk = versionsMatch() ? 1 : 0;
        if (versionOk == 0) return false;
        if (probeOk < 0) probeOk = probe() ? 1 : 0;
        return probeOk == 1;
    }

    /**
     * Whether the water face at {@code pos} toward {@code side} should be left out: Far Sight draws, the side is
     * horizontal and the block beyond lies in a chunk that hadn't arrived when this section was copied. Worker threads.
     */
    public static boolean hides(BlockAndTintGetter level, BlockPos pos, Direction side) {
        if (!active || side.getAxis() == Direction.Axis.Y) return false;
        try {
            return level instanceof SectionRegionProbe region && region.awaken$unloaded(pos.relative(side));
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            active = false;
            Awaken.LOG.warn("[wateredge] off: the region probe failed", e);
            return false;
        }
    }

    private static boolean versionsMatch() {
        String mc = SharedConstants.getCurrentVersion().name();
        String neo = ModList.get().getModContainerById("neoforge").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        if (MINECRAFT.equals(mc) && NEOFORGE.equals(neo)) return true;
        Awaken.LOG.warn("[wateredge] off: verified on Minecraft {} with NeoForge {}, this is {} with {}", MINECRAFT, NEOFORGE, mc, neo);
        return false;
    }

    private static boolean probe() {
        try {
            // the region mixin adds its interface to RenderSectionRegion; without it no face can be told apart
            if (SectionRegionProbe.class.isAssignableFrom(RenderSectionRegion.class)) return true;
        } catch (LinkageError ignored) {
            // the mixin package failed to load
        }
        Awaken.LOG.warn("[wateredge] off: the section region probe did not apply");
        return false;
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
        return (forced == null ? "setting" : forced ? "on" : "off") + (failed ? ", failed" : usable() ? "" : ", unavailable");
    }
}
