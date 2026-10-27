package dev.awaken.living.wonder;

import dev.awaken.core.api.MapMarks;
import dev.awaken.living.LivingContent;
import dev.awaken.living.api.Regions;
import dev.awaken.living.region.RegionMap;
import dev.awaken.living.region.RegionService;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Finds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * What a wonder does once it stands (SPEC §21.11): it is warded (no hostile spawns near it), finding it is a discovery (0.15 of a level, more in a harder
 * land), it is remembered on the player (kept through death) and drawn on the Atlas.
 */
public final class WonderWatch {
    private WonderWatch() {}

    /** How close counts as having found it (horizontal blocks; and a vertical allowance for a tall one). */
    public static final int FIND = 30;

    private static Wonders.Terrain terrain(ServerLevel level) {
        return WonderFeature.terrain(level, level.getChunkSource().getGenerator());
    }

    /** Wonders within {@code radius} of a point, or none when the map is not made yet. */
    public static List<Wonders.Site> near(ServerLevel level, int x, int z, int radius) {
        RegionMap map = RegionService.mapIfReady(level.getServer());
        if (map == null) return List.of();
        var all = Wonders.near(level.getSeed(), map, terrain(level), x, z, x, z, radius);
        List<Wonders.Site> out = new ArrayList<>(all.size());
        for (Wonders.Site s : all) {
            double dx = s.x() - x, dz = s.z() - z;
            if (dx * dx + dz * dz <= (double) radius * radius) out.add(s);
        }
        return out;
    }

    /** Nothing hostile spawns on a wonder's ground. */
    public static void onSpawnCheck(MobSpawnEvent.SpawnPlacementCheck e) {
        if (e.getEntityType().getCategory() != MobCategory.MONSTER) return;
        EntitySpawnReason why = e.getSpawnType();
        if (why != EntitySpawnReason.NATURAL && why != EntitySpawnReason.CHUNK_GENERATION && why != EntitySpawnReason.PATROL && why != EntitySpawnReason.REINFORCEMENT) return;
        ServerLevel level = e.getLevel().getLevel();
        if (!Regions.on(level)) return;
        BlockPos p = e.getPos();
        if (!near(level, p.getX(), p.getZ(), Wonders.WARD).isEmpty()) e.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }

    public static List<String> found(Player p) {
        return p.getExistingData(LivingContent.WONDERS).orElse(List.of());
    }

    /** Once a second, per player: standing at a wonder for the first time finds it. */
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || (p.tickCount + p.getId()) % 20 != 0 || !Regions.on(p.level())) return;
        ServerLevel level = (ServerLevel) p.level();
        for (Wonders.Site s : near(level, p.getBlockX(), p.getBlockZ(), FIND)) {
            if (Math.abs(p.getY() - s.y()) > 60) continue;
            List<String> have = found(p);
            if (have.contains(s.key())) continue;
            List<String> next = new ArrayList<>(have);
            next.add(s.key());
            p.setData(LivingContent.WONDERS.get(), List.copyOf(next));
            int rank = RegionMap.rankOf(Regions.levelAt(level, s.x(), s.z()));
            boolean fresh = Progression.discover(p, "wonder/" + s.kind().id, 0.15 + 0.07 * rank);
            Progression.count(p, "awaken:wonders_found");
            if (fresh) Progression.markNewThing(p, s.kind().display);
            Finds.tell(p, Finds.Find.of("wonder", "New wonder · " + s.kind().display, s.kind().display, s.kind().line + " " + s.regionName() + ".", s.kind().colour, true));
        }
    }

    /** The Atlas marks for the wonders this player has found. */
    public static List<MapMarks.Mark> marks(Player p) {
        List<String> have = found(p);
        if (have.isEmpty()) return List.of();
        List<MapMarks.Mark> out = new ArrayList<>(have.size());
        for (String key : have) {
            int at = key.indexOf('@'), comma = key.indexOf(',');
            if (at < 0 || comma < at) continue;
            Wonders.Kind k = Wonders.Kind.byId(key.substring(0, at));
            if (k == null) continue;
            try {
                out.add(new MapMarks.Mark(new BlockPos(Integer.parseInt(key.substring(at + 1, comma)), p.getBlockY(), Integer.parseInt(key.substring(comma + 1))), k.colour, "Wonder · " + k.display, true));
            } catch (NumberFormatException skip) {
                // a malformed entry is skipped
            }
        }
        return out;
    }
}
