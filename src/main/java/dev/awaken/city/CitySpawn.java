package dev.awaken.city;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * A new Carillon world: spawn goes to the Runners' Roost (SPEC §16.0.4, §16.2; terrain never sets spawn), the respawn
 * radius to 0 so everyone appears on the Roost's terrace, then the whole city is pregenerated and its graphs baked,
 * all before the world opens. Fired once, by vanilla's first-time spawn setup; existing worlds never see it.
 */
final class CitySpawn {
    private CitySpawn() {}

    static void onCreateSpawn(LevelEvent.CreateSpawnPosition e) {
        if (!(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        CityLayout l = City.layout();
        if (l == null) return;
        ServerLevelData data = e.getSettings();
        BlockPos start = City.start(l);
        data.setSpawn(LevelData.RespawnData.of(level.dimension(), start, l.plan.startYaw, 0.0F));
        level.getServer().getGameRules().set(GameRules.RESPAWN_RADIUS, 0, level.getServer());
        CityPregen.Result r = CityPregen.run(level, l);
        CityData.bake(level.getServer(), l, r.seconds());
        Awaken.LOG.info("[city] spawn on the Runners' Roost at {} {} {}", start.getX(), start.getY(), start.getZ());
        e.setCanceled(true);
    }
}
