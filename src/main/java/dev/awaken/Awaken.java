package dev.awaken;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(Awaken.MODID)
public final class Awaken {
    public static final String MODID = "awaken";
    public static final Logger LOG = LogUtils.getLogger();

    public Awaken(IEventBus modBus, ModContainer container) {
        LOG.info("AWAKEN loading");
    }
}
