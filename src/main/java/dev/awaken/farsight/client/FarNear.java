package dev.awaken.farsight.client;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/**
 * Far blocks handed to City Sight (SPEC §20, frame-budget build C, 2026-10-26): Sodium draws only the near chunks
 * ({@link #near}, 12 by default), City Sight the real chunks past them from its own greedy meshes, and the rings past
 * that, while the world still loads, simulates and sends everything to the player's render distance. Sodium and the
 * rest of the renderer read the client's effective render distance (the setting capped by the server's), so at the
 * start of each frame, before anything reads it, the server's value on the client is set to the near distance; the
 * integrated server keeps loading to the setting. Lens keeps its haze (it gets the real render distance's fog end,
 * FarTerrain). Entities draw to the game's own distance for the near one: the view scale it derives from the effective
 * distance (between the render distance over 8 and 2.5) has no hook short of a mixin, so a one-block mob draws to 96
 * blocks at 12 chunks against 160 at 24 (none of them past 12 chunks either way); the entity distance setting at 175%
 * gives the 160 back (setting it here would turn the player's graphics preset to custom).
 *
 * <p>Single player, the overworld, with Carillon's City Sight on; anywhere else, or off, the effective distance is the
 * setting again (Sodium rebuilds once as it changes).
 */
public final class FarNear {
    private FarNear() {}

    /** Measuring (autopilot {@code farsight near <n>|off|setting}): null follows the setting, 0 off. */
    static Integer forced;
    /** The near distance applied (chunks), or 0 when none is. */
    private static int applied;
    private static boolean logged;

    /** The near distance asked for (chunks), 0 for none. */
    static int near() {
        if (forced != null) return forced;
        return AwakenConfig.Client.SPEC.isLoaded() ? AwakenConfig.Client.FAR_SIGHT_NEAR.get() : 0;
    }

    /** The near distance in force this frame (chunks), 0 for none. */
    public static int applied() {
        return applied;
    }

    /** Where it applies: single player in the overworld, Far Sight drawing there with City Sight (Carillon's world). */
    private static boolean where(Minecraft mc) {
        return mc.level != null && mc.getSingleplayerServer() != null && mc.level.dimension() == Level.OVERWORLD
                && mc.level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD && FarSightClient.enabledHere()
                && !ModList.get().isLoaded("distanthorizons") && FarPipelines.ok() && dev.awaken.city.City.layout() != null;
    }

    /** Frame start (after the tick's packets, before anything renders): the near distance in force. */
    static void frame(RenderFrameEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        int setting = o.renderDistance().get(), want = near();
        if (want > 0 && want < setting && where(mc)) {
            // every frame: the server's chunk radius packet (a join, a respawn) writes the real value back
            o.setServerRenderDistance(want);
            if (applied != want && !logged) {
                logged = true;
                Awaken.LOG.info("[farsight] near chunks: Sodium draws {} of the {} loaded, City Sight the rest", want, setting);
            }
            applied = want;
        } else if (applied != 0) {
            // the integrated server's radius is the setting: the effective distance is the setting again
            o.setServerRenderDistance(setting);
            applied = 0;
            logged = false;
        }
    }

    /**
     * Vanilla's render-distance fog end for Lens's haze: the setting's (the real chunks' reach the haze was made for),
     * not the near distance Sodium draws to, so the world looks as hazy as before.
     */
    static float lensFogEnd(float vanillaEnd) {
        if (applied == 0) return vanillaEnd;
        Minecraft mc = Minecraft.getInstance();
        return vanillaEnd * mc.options.renderDistance().get() / (float) applied;
    }

    static String status() {
        return applied == 0 ? " near=off" : " near=" + applied + "/" + Minecraft.getInstance().options.renderDistance().get();
    }
}
