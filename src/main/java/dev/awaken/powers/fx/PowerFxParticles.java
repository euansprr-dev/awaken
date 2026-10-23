package dev.awaken.powers.fx;

import dev.awaken.Awaken;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The power FX sprites as real particle types ({@code awaken:power_fx_<name>}), each with its JSON and animated
 * frames in the particle atlas. The client FX system draws its pooled sprites from these sprite sets; the types can
 * also be spawned like any particle.
 */
public final class PowerFxParticles {
    private PowerFxParticles() {}

    static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Awaken.MODID);

    /** One sprite family: its index (used by the client's sprite table), name, frame count and blend. */
    public record Sprite(int index, String name, int frames, boolean additive, DeferredHolder<ParticleType<?>, SimpleParticleType> type) {
        public String id() {
            return "power_fx_" + name;
        }
    }

    private static final List<Sprite> ALL = new ArrayList<>();
    public static final List<Sprite> SPRITES = Collections.unmodifiableList(ALL);

    public static final Sprite GLOW = sprite("glow", 1, true);
    public static final Sprite SPARK = sprite("spark", 2, true);
    public static final Sprite FLARE = sprite("flare", 4, true);
    public static final Sprite BOLT = sprite("bolt", 4, true);
    public static final Sprite WISP = sprite("wisp", 4, true);
    public static final Sprite EMBER = sprite("ember", 4, true);
    public static final Sprite RING = sprite("ring", 1, true);
    public static final Sprite RUNE = sprite("rune", 4, true);
    public static final Sprite HEX = sprite("hex", 1, true);
    public static final Sprite MOTE = sprite("mote", 2, true);
    public static final Sprite SHARD = sprite("shard", 4, true);
    public static final Sprite SLASH = sprite("slash", 3, true);
    public static final Sprite PALM = sprite("palm", 1, true);
    public static final Sprite WIND = sprite("wind", 3, true);
    public static final Sprite GLYPH = sprite("glyph_circle", 1, true);
    public static final Sprite SMOKE = sprite("smoke", 6, false);
    public static final Sprite DUST = sprite("dust", 6, false);
    public static final Sprite CRACK = sprite("decal_crack", 1, false);
    public static final Sprite SCORCH = sprite("decal_scorch", 1, false);
    public static final Sprite POOL = sprite("decal_pool", 1, false);

    private static Sprite sprite(String name, int frames, boolean additive) {
        // false: these never get past the vanilla particle limiter; our pool has its own caps
        var holder = PARTICLES.register("power_fx_" + name, () -> new SimpleParticleType(false));
        Sprite s = new Sprite(ALL.size(), name, frames, additive, holder);
        ALL.add(s);
        return s;
    }

    static void init(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
