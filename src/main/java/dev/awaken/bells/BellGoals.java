package dev.awaken.bells;

import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Step;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Tasks page's Bells tab (SPEC §27.13): a step per lead in the Bell Book that you don't hold yet, newest first, with
 * its last line and, once it has a bearing, where to look (the same place Track pointed); and your tower's next step when
 * it's yours to do (set the frame down, clear room, the Great Belfry, the Hundred Peal). Finding more bells is the leads'
 * job, so a tower that only wants more bells adds nothing. Reads the Book; never changes it.
 */
final class BellGoals {
    private BellGoals() {}

    /** Leads on the tab at once. */
    static final int LEADS = 4;

    static void register() {
        Goals.register(BellGoals::steps);
    }

    static List<Step> steps(ServerPlayer p) {
        BellBook b = Bells.book(p);
        List<Step> out = new ArrayList<>();
        List<Map.Entry<Integer, BellBook.Lead>> leads = new ArrayList<>();
        for (Map.Entry<Integer, BellBook.Lead> e : b.leads.entrySet()) if (!b.held.containsKey(e.getKey())) leads.add(e);
        leads.sort(Comparator.comparingLong((Map.Entry<Integer, BellBook.Lead> e) -> -e.getValue().since).thenComparingInt(Map.Entry::getKey));
        for (Map.Entry<Integer, BellBook.Lead> e : leads) {
            if (out.size() >= LEADS) break;
            BellCatalog.Def d = BellCatalog.of(e.getKey());
            BellBook.Lead l = e.getValue();
            String name = Bells.title(d, l);
            String title = name.endsWith("?") ? "Find " + name.substring(0, name.length() - 1) : "Find where that bell's note came from";
            Goals.Builder s = Step.of("bell/" + d.no(), Goal.BELLS, title).why(l.lines.isEmpty() ? "" : l.lines.getLast()).priority(40 - out.size());
            if (l.bearings == 2) s.at(BlockPos.containing(l.tx, 64, l.tz));
            else if (l.bearings == 1) s.at(BlockPos.containing(l.hx, 64, l.hz)); // along its one bearing, within a hum's reach
            out.add(s.build());
        }
        Step tower = tower(p, b);
        if (tower != null) out.add(tower);
        return out;
    }

    /** The tower's next step when it's in your hands, or null. */
    static @org.jspecify.annotations.Nullable Step tower(ServerPlayer p, BellBook b) {
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        if (h.isEmpty() || !b.given) return null;
        BellWorld.Home home = BellWorld.get(p.level().getServer()).peek(h.get().pos);
        int n = home == null ? 0 : home.hung.size();
        String title, why;
        if (home == null || home.frame == null) {
            title = "Set your bell frame down at home";
            why = "Put it anywhere inside your Hearth. Every bell you hold hangs on it, and the tower grows round it as you find more.";
        } else if (n >= BellTower.BELFRY && home.stage < 1) {
            title = "Clear room for your belfry";
            why = "It needs a clear space five wide and ten high round your bell frame. Clear it and it goes up at the next song.";
        } else if (n >= BellTower.TOWER && home.stage < 2) {
            title = "Clear room for your stone tower";
            why = "It needs a clear space five wide and fifteen high round your bell frame. Clear it and it goes up at the next song.";
        } else if (home.stage >= 2 && home.stage < 3 && n >= BellTower.TOWER) {
            title = "Raise the Great Belfry";
            why = "Your tower can't hold the rest. Set the Heart-clapper and a Sovereign's relic into your bell frame.";
        } else if (n >= 100 && home.pealed < 0) {
            title = "Ring the Hundred Peal";
            why = "Your song is whole. Ring your bells and all hundred ring together.";
        } else return null;
        Goals.Builder s = Step.of("bells/tower", Goal.BELLS, title).why(why).priority(45);
        if (home != null && home.frame != null) s.at(home.frame);
        return s.build();
    }
}
