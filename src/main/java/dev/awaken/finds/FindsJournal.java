package dev.awaken.finds;

import dev.awaken.core.api.MapMarks;
import dev.awaken.core.api.Spotting;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * What the finds tell the rest of the game (SPEC §58.7): the binoculars can see a find's site and mark its kind as
 * seen; the finds a player has done are marks on their Atlas. The Journal's slots are drawn by the client from the
 * player's own {@link FindsData} ({@code finds.client.FindsClient}).
 */
final class FindsJournal {
    private FindsJournal() {}

    static void init() {
        Spotting.register(new Spotting.Source() {
            @Override
            public boolean moves() {
                return false;
            }

            @Override
            public void gather(ServerPlayer p, double reach, Spotting.Sink out) {
                if (!(p.level() instanceof ServerLevel level)) return;
                FindsData d = p.getData(FindsContent.FINDS);
                for (FindHeartBlockEntity be : Hearts.all(level)) {
                    Site s = be.site();
                    if (s == null || d.knows(s.kind().id()) || p.distanceToSqr(s.x() + 0.5, s.y() + 1, s.z() + 0.5) > reach * reach) continue;
                    int r = Math.min(6, s.kind().reach());
                    out.place(be.getBlockPos().asLong(), s.x() - r, s.y(), s.z() - r, s.x() + r + 1, s.y() + 6, s.z() + r + 1, be);
                }
            }

            @Override
            public boolean find(ServerPlayer p, Object ref) {
                if (!(ref instanceof FindHeartBlockEntity be) || be.isRemoved()) return false;
                Site s = be.site();
                FindsData d = p.getData(FindsContent.FINDS);
                if (s == null || d.knows(s.kind().id())) return false;
                p.setData(FindsContent.FINDS, d.withSeen(s.kind().id()));
                Progression.count(p, "awaken:finds_seen");
                return true;
            }
        });
        MapMarks.register(FindsJournal::marks);
    }

    /** The finds (not the curios) a player has done, as Atlas marks. The last two hundred. */
    private static List<MapMarks.Mark> marks(Player p) {
        List<String> sites = p.getData(FindsContent.FINDS).sites();
        List<MapMarks.Mark> out = new ArrayList<>();
        for (int i = sites.size() - 1; i >= 0 && out.size() < 200; i--) {
            String key = sites.get(i);
            int at = key.indexOf('@'), comma = key.indexOf(',');
            if (at < 0 || comma < at) continue;
            Kind k = Kinds.byId(key.substring(0, at));
            if (k == null || k.size() != Kind.Size.FIND) continue;
            try {
                out.add(new MapMarks.Mark(new BlockPos(Integer.parseInt(key.substring(at + 1, comma)), p.getBlockY(), Integer.parseInt(key.substring(comma + 1))), 0xE8C36A, "Find · " + k.name(), true));
            } catch (NumberFormatException skip) {
                // a malformed entry is skipped
            }
        }
        return out;
    }
}
