package dev.awaken;

import dev.awaken.above.client.AboveClient;
import dev.awaken.core.client.CoreClient;
import dev.awaken.descent.client.DescentClient;
import dev.awaken.gates.client.GatesClient;
import dev.awaken.hearth.client.HearthClient;
import dev.awaken.lucidium.client.LucidiumClient;
import dev.awaken.mobs.client.MobsClient;
import dev.awaken.powers.client.PowersClient;
import dev.awaken.progression.client.ProgressionClient;
import dev.awaken.signal.client.SignalClient;
import dev.awaken.story.client.StoryClient;
import dev.awaken.veil.client.VeilClient;
import dev.awaken.world.client.WorldClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client entry: never loaded on a dedicated server. */
@Mod(value = Awaken.MODID, dist = Dist.CLIENT)
public final class AwakenClient {
    public AwakenClient(IEventBus modBus, ModContainer container) {
        // NeoForge's config screen; Awaken's client section also opens the Look switch (compat.client.LookSettings)
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new ConfigurationScreen(mod, parent,
                (screen, type, config, title) -> dev.awaken.compat.client.LookSettings.section(screen, type, config, title)));
        CoreClient.init(modBus);
        dev.awaken.core.client.dev.Autopilot.init(); // inert unless -Dawaken.autopilot is set
        SignalClient.init(modBus);
        ProgressionClient.init(modBus);
        PowersClient.init(modBus);
        MobsClient.init(modBus);
        GatesClient.init(modBus);
        DescentClient.init(modBus);
        LucidiumClient.init(modBus);
        VeilClient.init(modBus);
        AboveClient.init(modBus);
        HearthClient.init(modBus);
        StoryClient.init(modBus);
        WorldClient.init(modBus);
        dev.awaken.gear.client.GearClient.init(modBus);
        dev.awaken.anims.client.AnimsClient.init(modBus);
        dev.awaken.body.client.BodyClient.init(modBus);
        dev.awaken.core.audio.client.AudioClient.init(modBus);
        dev.awaken.lens.client.LensClient.init(modBus);
        dev.awaken.compat.client.CompatClient.init(modBus);
        dev.awaken.terrain.client.TerrainClient.init(modBus);
        dev.awaken.holo.client.HoloClient.init(modBus);
        dev.awaken.farsight.client.FarSightClient.init(modBus);
        dev.awaken.turbo.pixels.PixelsClient.init(modBus);
        dev.awaken.turbo.wateredge.WaterEdge.init();
        dev.awaken.wilds.client.WildsClient.init(modBus);
        dev.awaken.citizens.client.CitizensClient.init(modBus);
        dev.awaken.opening.client.OpeningClient.init(modBus);
        dev.awaken.living.client.LivingClient.init(modBus);
        dev.awaken.living.client.StormSky.init(modBus);
        dev.awaken.atlas.client.AtlasClient.init(modBus);
        dev.awaken.arms.client.ArmsClient.init(modBus);
    }
}
