package dev.awaken;

import com.mojang.logging.LogUtils;
import dev.awaken.above.AboveModule;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.CoreModule;
import dev.awaken.descent.DescentModule;
import dev.awaken.gates.GatesModule;
import dev.awaken.hearth.HearthModule;
import dev.awaken.lucidium.LucidiumModule;
import dev.awaken.mobs.MobsModule;
import dev.awaken.powers.PowersModule;
import dev.awaken.progression.ProgressionModule;
import dev.awaken.signal.SignalModule;
import dev.awaken.story.StoryModule;
import dev.awaken.veil.VeilModule;
import dev.awaken.world.WorldModule;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/** AWAKEN: one home, four directions. Wires every module; each module registers its own content. */
@Mod(Awaken.MODID)
public final class Awaken {
    public static final String MODID = "awaken";
    public static final Logger LOG = LogUtils.getLogger();

    public Awaken(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SYNCED, AwakenConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, AwakenConfig.Client.SPEC);
        CoreModule.init(modBus);
        SignalModule.init(modBus);
        ProgressionModule.init(modBus);
        PowersModule.init(modBus);
        MobsModule.init(modBus);
        GatesModule.init(modBus);
        DescentModule.init(modBus);
        LucidiumModule.init(modBus);
        VeilModule.init(modBus);
        AboveModule.init(modBus);
        HearthModule.init(modBus);
        StoryModule.init(modBus);
        WorldModule.init(modBus);
        dev.awaken.gear.GearModule.init(modBus);
        dev.awaken.body.BodyModule.init(modBus);
        dev.awaken.core.audio.AudioModule.init(modBus);
        dev.awaken.compat.CompatModule.init(modBus);
        dev.awaken.terrain.TerrainModule.init(modBus);
        dev.awaken.city.CityModule.init(modBus);
        dev.awaken.citizens.CitizensModule.init(modBus);
        dev.awaken.opening.OpeningModule.init(modBus);
        dev.awaken.holo.HoloModule.init(modBus);
        dev.awaken.wilds.WildsModule.init(modBus);
        dev.awaken.lens.LensModule.init(modBus);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
