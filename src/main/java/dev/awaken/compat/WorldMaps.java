package dev.awaken.compat;

import dev.awaken.Awaken;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Optional;

/**
 * Xaero's World Map stays shut in Awaken's {@link Instanced} dimensions (SPEC §14). A gate is learned by walking it and
 * by Perception (its layout from PER 20); a map of every chunk in view would read a labyrinth's rooms at the door, and a
 * reused cell would show an earlier gate's. Xaero 1.47.1 has no setting to skip a dimension, so this uses the control it
 * supports for servers: its own No World Map effect ({@code xaeroworldmap:no_world_map}, registered while Xaero's
 * {@code register_status_effects} option is on, its default). Players in an instanced dimension carry it without
 * particles or a HUD icon, renewed every two seconds and taken off the moment they leave. Xaero still records those
 * chunks, in that dimension's own cache (never the overworld's). The effect lives in the server's registry, so a
 * dedicated server needs Xaero's World Map installed too; without it nothing here runs.
 */
public final class WorldMaps {
    private WorldMaps() {}

    public static final Identifier NO_WORLD_MAP = Identifier.fromNamespaceAndPath("xaeroworldmap", "no_world_map");
    /** Ticks between renewals, and the effect's length: long enough to outlast a renewal, short enough to lapse alone. */
    static final int REFRESH = 40, DURATION = 100;

    // looked up once the registries are frozen (the first player tick); empty when Xaero registers no effects
    private static Optional<Holder<MobEffect>> effect;

    static void init() {
        NeoForge.EVENT_BUS.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && (sp.tickCount + sp.getId()) % REFRESH == 0) apply(sp);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) apply(sp);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) apply(sp);
        });
        Awaken.LOG.debug("[compat] Xaero's World Map, if a player adds it: shut in instanced Awaken dimensions");
    }

    /** Xaero's effect, when it is registered. */
    static Optional<Holder<MobEffect>> effect() {
        if (effect == null) {
            effect = BuiltInRegistries.MOB_EFFECT.get(NO_WORLD_MAP).map(h -> h);
            // the usual case since the Atlas replaced Xaero's in the pack (SPEC §16.8): nothing to shut, nothing to say
            if (effect.isEmpty()) Awaken.LOG.debug("[compat] {} isn't registered (no Xaero's World Map): nothing to shut", NO_WORLD_MAP);
        }
        return effect;
    }

    /** Shuts or opens the map for where the player is now. Returns whether it is shut. */
    static boolean apply(ServerPlayer sp) {
        var e = effect();
        if (e.isEmpty()) return false;
        boolean shut = Instanced.is(sp.level().dimension().identifier().toString());
        keep(sp, e.get(), shut);
        return shut;
    }

    /** The effect on while {@code shut} and off otherwise; never touches one Awaken didn't give (an operator's /effect). */
    static void keep(ServerPlayer sp, Holder<MobEffect> e, boolean shut) {
        MobEffectInstance now = sp.getEffect(e);
        boolean ours = now != null && ours(now);
        if (shut && (now == null || ours)) sp.addEffect(new MobEffectInstance(e, DURATION, 0, true, false, false));
        else if (!shut && ours) sp.removeEffect(e);
    }

    /** Awaken's instance: ambient, no particles, no icon, short. A command's or a potion's is none of these. */
    static boolean ours(MobEffectInstance i) {
        return i.isAmbient() && !i.isVisible() && !i.showIcon() && i.getAmplifier() == 0
                && !i.isInfiniteDuration() && i.getDuration() <= DURATION;
    }
}
