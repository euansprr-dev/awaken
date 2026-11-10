package dev.awaken.turbo.chunkpace;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import org.jspecify.annotations.Nullable;

/**
 * Chunk pacing (Turbo; SPEC §22 "Steady Light", the late frames in flight). At 60 frames a second with the look's
 * GPU time, a frame has three to five milliseconds to spare, and the game spends more than that in single frames
 * while chunks stream in and out.
 *
 * <p>When chunks leave the view, Sodium takes every one of them out of its renderer in the next frame: their
 * sections' sort triggers out of an interval tree, their meshes back into the buffer arenas. Flying, a whole row of
 * chunks goes at once, about once a second: these removals were four in ten of the render thread's samples in long
 * frames on the Dell (docs/perf/steady60/data/late-1). Sodium's own unload queue is made to carry over (a chunk that
 * comes back before its turn is simply taken off it), so the hook lets a few chunks through each frame and holds the
 * rest in the queue for the next. A chunk that waits is one already out of view, drawn a few frames longer at the
 * very edge, under the fog. Measured on the same flight, legs alternating (pace-1): 94.9% of frames on time against
 * 93.3%, every paced leg above every unpaced one.
 *
 * <p>(Tried with it and dropped: asking the server for 2.5 ms' worth of chunks a tick where the game asks for 7.
 * Smaller batches put chunk work into more frames, each with its own light and mesh scheduling, and with so little
 * to spare that was no better over loaded land and worse over new land: batch-1.)
 *
 * <p>The Turbo contract: active only on the exact Minecraft, NeoForge and Sodium it was read against, only once the
 * probe has seen the hook applied, with the client setting {@code turboChunkPacing}, and off for the session on the
 * first failure. Off, Sodium's queue drains exactly as its own code drains it.
 */
public final class ChunkPace {
    /** The versions the hook was read against and tested on. Any other: off, with one log line. */
    static final String MINECRAFT = "26.3", NEOFORGE = "26.3.0.95", SODIUM = "0.9.3-beta.1+mc26.3";
    /** Chunks taken out a frame; a long queue (a teleport, a change of render distance) drains in about forty frames. */
    private static final int PER_FRAME = 3, DRAIN_FRAMES = 40;

    private static volatile boolean unloadsApplied, failed;
    private static int gameOk = -1, sodiumOk = -1;
    private static @Nullable Boolean forced;
    private static int longest, heldFrames;

    private ChunkPace() {}

    /** The Sodium mixin's probe: the hook is in its chunk tracker. */
    public static void unloadsApplied() {
        unloadsApplied = true;
    }

    /** How many of {@code queued} unloads this frame may take; all of them when pacing is off. */
    public static int unloadBudget(int queued) {
        if (queued <= PER_FRAME || !unloadsApplied || !on() || !sodium()) return queued;
        longest = Math.max(longest, queued);
        heldFrames++;
        return Math.max(PER_FRAME, queued / DRAIN_FRAMES);
    }

    /** A hook threw: off for the session. */
    public static void fail(Throwable e) {
        if (failed) return;
        failed = true;
        Awaken.LOG.warn("[chunkpace] off: a hook failed", e);
    }

    private static boolean on() {
        if (failed) return false;
        if (gameOk < 0) {
            String mc = SharedConstants.getCurrentVersion().name(), neo = version("neoforge");
            gameOk = MINECRAFT.equals(mc) && NEOFORGE.equals(neo) ? 1 : 0;
            if (gameOk == 0) Awaken.LOG.warn("[chunkpace] off: verified on Minecraft {} and NeoForge {}; this is {}, {}", MINECRAFT, NEOFORGE, mc, neo);
        }
        if (gameOk != 1) return false;
        // (off unless the setting says on: the hook waits for Euan's yes, and a config not loaded yet is no yes)
        return forced != null ? forced : AwakenConfig.Client.SPEC.isLoaded() && AwakenConfig.Client.TURBO_CHUNK_PACING.get();
    }

    private static boolean sodium() {
        if (sodiumOk < 0) {
            String sodium = version("sodium");
            sodiumOk = SODIUM.equals(sodium) ? 1 : 0;
            if (sodiumOk == 0) Awaken.LOG.warn("[chunkpace] unloads not paced: verified on Sodium {}; this is {}", SODIUM, sodium);
        }
        return sodiumOk == 1;
    }

    private static String version(String mod) {
        return ModList.get().getModContainerById(mod).map(c -> c.getModInfo().getVersion().toString()).orElse("-");
    }

    /** The autopilot's {@code chunkpace on|off|setting}: overrides the client setting for the following scenes, and reports. */
    public static String autopilot(String arg) {
        switch (arg.trim()) {
            case "on" -> forced = true;
            case "off" -> forced = false;
            case "setting" -> forced = null;
            default -> {
            }
        }
        String out = "pacing " + (on() ? "on" : "off") + (failed ? " (failed)" : "") + "; unload hook " + (unloadsApplied ? "in" : "not applied")
                + ", frames that held unloads back " + heldFrames + ", longest queue " + longest;
        longest = 0;
        heldFrames = 0;
        return out;
    }
}
