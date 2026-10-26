package dev.awaken.turbo.tiles;

import com.mojang.blaze3d.resource.RenderTargetDescriptor;
import com.mojang.blaze3d.resource.ResourceDescriptor;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.ModList;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import com.mojang.renderpearl.api.buffers.GpuBuffer;

import java.util.Optional;

/**
 * Tiles (a Turbo feature, SPEC §22 "Lens on Apple", docs/perf/LENS-APPLE.md): Lens's post passes as a tile-based GPU
 * wants them. Vanilla gives every post chain target a full-size depth buffer and clears colour and depth each frame in a
 * pass of their own, then opens every post pass with both loaded and stored; on Apple's GPUs each of those is a trip
 * through memory for the whole target. For Lens's chains only ({@code awaken:lens_*}): its per-frame targets get no depth
 * and no separate clear, its histories no depth, and each of its passes opens cleared (every one draws a full-screen
 * triangle with no blending or discard, so nothing that was there survives anyway) with no depth attached (no post
 * pipeline tests or writes depth). The picture is the same pixel for pixel.
 * <p>Turbo contract: the hooks live in {@code dev.awaken.turbo.tiles.mixin} (own config, not required); locked to the
 * versions they were read against; a probe logs that they ran; a client setting ({@code lensTiles}); off for the session
 * on the first thing that goes wrong; off is exactly vanilla.
 */
public final class Tiles {
    private Tiles() {}

    static final String MINECRAFT = "26.3", NEOFORGE = "26.3.0.95";
    private static final Optional<Vector4fc> CLEAR = Optional.of(new Vector4f(0f, 0f, 0f, 0f));
    private static int versionOk = -1;
    private static boolean failed, loggedChain, loggedPass, loggedBlock;
    private static @Nullable Boolean forced;

    /** Whether Tiles reshapes this chain's or pass's work: a Lens one, the setting on, the versions right. */
    static boolean on(String name) {
        if (failed || !name.startsWith("awaken:lens_")) return false;
        boolean wanted = forced != null ? forced : !AwakenConfig.Client.SPEC.isLoaded() || AwakenConfig.Client.LENS_TILES.get();
        if (!wanted) return false;
        if (versionOk < 0) {
            String mc = SharedConstants.getCurrentVersion().name();
            String neo = ModList.get().getModContainerById("neoforge").map(c -> c.getModInfo().getVersion().toString()).orElse("?");
            versionOk = MINECRAFT.equals(mc) && NEOFORGE.equals(neo) ? 1 : 0;
            if (versionOk == 0) Awaken.LOG.warn("[tiles] off: read against Minecraft {} with NeoForge {}, this is {} with {}", MINECRAFT, NEOFORGE, mc, neo);
        }
        return versionOk == 1;
    }

    /** A chain's per-frame target: no depth, no clear of its own (the first pass to write it clears it, for free). */
    public static ResourceDescriptor<?> internal(Identifier chain, ResourceDescriptor<?> d) {
        try {
            if (!(d instanceof RenderTargetDescriptor r) || !on(chain.toString())) return d;
            if (!loggedChain) {
                loggedChain = true;
                Awaken.LOG.info("[tiles] {}: targets without depth or a clear pass of their own", chain);
            }
            RenderTargetDescriptor.TextureProperties colour = r.color() == null ? null : new RenderTargetDescriptor.TextureProperties(null, r.color().format());
            return new RenderTargetDescriptor(r.width(), r.height(), colour, null);
        } catch (RuntimeException e) {
            fail(e);
            return d;
        }
    }

    /** A chain's history (kept across frames): no depth; its one clear when made stays, so it never starts as garbage. */
    public static RenderTargetDescriptor persistent(Identifier chain, RenderTargetDescriptor d) {
        try {
            return on(chain.toString()) ? new RenderTargetDescriptor(d.width(), d.height(), d.color(), null) : d;
        } catch (RuntimeException e) {
            fail(e);
            return d;
        }
    }

    /** A pass's colour: cleared on open (tile memory starts clear) instead of loaded from memory. */
    public static Optional<Vector4fc> clear(String pass, Optional<Vector4fc> clear) {
        if (!on(pass)) return clear;
        if (!loggedPass) {
            loggedPass = true;
            Awaken.LOG.info("[tiles] {}: passes open cleared, no depth attached", pass);
        }
        return CLEAR;
    }

    /** A pass's depth: none (post pipelines neither test nor write it, so loading and storing it was all cost). */
    public static @Nullable GpuTextureView depth(String pass, @Nullable GpuTextureView depth) {
        return on(pass) ? null : depth;
    }

    private static @Nullable GpuBuffer lensBlock;
    private static @Nullable Boolean forcedBlock;

    /** Lens's live uniform block (lens.client.LensBlock), set each frame it's written. */
    public static void lensBlock(GpuBuffer buffer) {
        lensBlock = buffer;
    }

    /** A pass's uniform buffer: Lens's live block in place of the chain's zeros for "LensBlock" on a Lens pass. */
    public static GpuBuffer uniform(String pass, String name, GpuBuffer buffer) {
        if (lensBlock == null || !"LensBlock".equals(name) || forcedBlock == Boolean.FALSE || !on(pass)) return buffer;
        if (!loggedBlock) {
            loggedBlock = true;
            Awaken.LOG.info("[tiles] {}: Lens's values from one uniform block", pass);
        }
        return lensBlock;
    }

    /** Autopilot {@code lens block on|off|setting}: the A/B switch for the uniform block alone (Tiles' other parts stay). */
    public static String block(String arg) {
        forcedBlock = switch (arg.trim()) {
            case "on" -> Boolean.TRUE;
            case "off" -> Boolean.FALSE;
            default -> null;
        };
        return "lens block " + (forcedBlock != null ? forcedBlock : "with Tiles");
    }

    static void fail(Throwable e) {
        if (failed) return;
        failed = true;
        Awaken.LOG.warn("[tiles] off: {}", e.toString());
    }

    /** Autopilot {@code lens tiles on|off|setting}: the A/B switch. */
    public static String autopilot(String arg) {
        forced = switch (arg.trim()) {
            case "on" -> Boolean.TRUE;
            case "off" -> Boolean.FALSE;
            default -> null;
        };
        return "tiles " + (forced != null ? forced : "from the setting") + (failed ? " (off: failed)" : "");
    }
}
