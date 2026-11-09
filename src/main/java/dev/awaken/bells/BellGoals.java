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
 * The Tasks page's Bells tab (SPEC §27.13): inside a gate, the bell its Lord kept for you while it lies there; a bell set
 * down where you won it; a village's bell you're on the trail of and a spirit's errand (stage 5); a step per lead in the Bell Book that you don't hold yet, newest first, with
 * its last line and, once it has a bearing, where to look (the same place Track pointed); and your tower's next step when
 * it's yours to do (make and set the frame, hang the bells in your bag, a Bell Beam when the frame's full, a Great Beam for
 * a Great Bell, the Lampwing's old bell, clear room, the Great Belfry, the Hundred Peal). Finding more bells is the leads'
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
        Step lord = lordBell(p);
        if (lord != null) out.add(lord);
        Step lying = lying(p);
        if (lying != null) out.add(lying);
        out.addAll(VillageBells.steps(p));
        out.addAll(SpiritBells.steps(p));
        List<Map.Entry<Integer, BellBook.Lead>> leads = new ArrayList<>();
        for (Map.Entry<Integer, BellBook.Lead> e : b.leads.entrySet()) if (!b.held.containsKey(e.getKey())) leads.add(e);
        leads.sort(Comparator.comparingLong((Map.Entry<Integer, BellBook.Lead> e) -> -e.getValue().since).thenComparingInt(Map.Entry::getKey));
        for (Map.Entry<Integer, BellBook.Lead> e : leads) {
            if (out.size() >= LEADS) break;
            BellCatalog.Def d = BellCatalog.of(e.getKey());
            BellBook.Lead l = e.getValue();
            String name = Bells.title(d, l);
            String title = name.endsWith("?") ? "Find " + name.substring(0, name.length() - 1) : "Find where that bell's note came from";
            Goals.Builder s = Step.of("bell/" + d.no(), Goal.BELLS, title).lead().why(l.lines.isEmpty() ? "" : l.lines.getLast()).priority(40 - out.size());
            if (l.bearings == 2) s.at(BlockPos.containing(l.tx, 64, l.tz));
            else if (l.bearings == 1) s.at(BlockPos.containing(l.hx, 64, l.hz)); // along its one bearing, within a hum's reach
            out.add(s.build());
        }
        Step tower = tower(p, b);
        if (tower != null) out.add(tower);
        return out;
    }

    /** A bell a gate's Lord kept, lying on its floor for you, while you're in its gate (SPEC §43.16 stage 4), or null. */
    static @org.jspecify.annotations.Nullable Step lordBell(ServerPlayer p) {
        if (!dev.awaken.core.api.GateHooks.inGate(p)) return null;
        for (BellWorld.Kept k : BellWorld.get(p.level().getServer()).kept) {
            if (k.gate.isEmpty() || !k.left.contains(p.getUUID())) continue;
            String name = BellCatalog.of(k.no).name().replaceFirst("^The ", "the ");
            return Step.of("bells/lord", Goal.BELLS, "Lift " + name + " from where the Lord fell").lead()
                    .why("It's lying on the floor of the Lord's room. Lift it before you leave: once the gate closes, it goes back to the gates.")
                    .priority(60).at(k.pos).build();
        }
        return null;
    }

    /** A bell set down where you won it (stage 5), lying in this dimension for you to lift, or null. */
    static @org.jspecify.annotations.Nullable Step lying(ServerPlayer p) {
        String dim = p.level().dimension().identifier().toString();
        for (BellWorld.Kept k : BellWorld.get(p.level().getServer()).kept) {
            if (!k.gate.isEmpty() || !k.dim.equals(dim) || !k.left.contains(p.getUUID())) continue;
            return Step.of("bells/lying", Goal.BELLS, "Lift " + BellCatalog.of(k.no).name().replaceFirst("^The ", "the ")).lead()
                    .why("It's lying where you won it. Lift it and take it home to hang.").priority(55).at(k.pos).build();
        }
        return null;
    }

    /** The tower's next step when it's in your hands, or null. */
    static @org.jspecify.annotations.Nullable Step tower(ServerPlayer p, BellBook b) {
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        if (h.isEmpty() || !b.given) return null;
        BellWorld.Home home = BellWorld.get(p.level().getServer()).peek(h.get().pos);
        int n = home == null ? 0 : home.hung.size();
        // the bells in your bag waiting to be hung (SPEC §43.16): the Great Bells apart, they need a Great Beam
        List<BellCatalog.Def> bag = new ArrayList<>();
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            BellCatalog.Def d = BellItem.def(p.getInventory().getItem(i));
            if (d != null && (home == null || !home.hung.contains(d.no()))) bag.add(d);
        }
        BellCatalog.Def great = bag.stream().filter(Hanging::great).findFirst().orElse(null);
        List<BellCatalog.Def> small = bag.stream().filter(d -> !Hanging.great(d)).toList();
        int free = freeFrameHooks(p, home);
        String title, why;
        BlockPos at = home == null ? null : home.frame;
        if (home == null || home.frame == null) {
            title = "Make a Bell Frame and set it by your fire";
            why = "Planks and logs, at a table. Set it down inside your Hearth, then use your bell on it to hang it. Your song rings from the bells that hang.";
            at = h.get().pos;
        } else if (!small.isEmpty() && free > 0) {
            title = small.size() == 1 ? "Hang " + small.getFirst().name().replaceFirst("^The ", "the ") : "Hang your " + small.size() + " bells";
            why = "Use " + (small.size() == 1 ? "it" : "each one") + " on your Bell Frame by your fire. It has " + free + (free == 1 ? " free hook." : " free hooks.");
        } else if (!small.isEmpty()) {
            title = "Your frame's full: hang the next bell on a Bell Beam";
            why = "A Bell Beam is three logs and two iron nuggets. Lay it across anything at home 8 or more blocks above your fire, and hang four bells from it.";
        } else if (great != null) {
            title = great.name() + " needs a Great Beam";
            why = "Three logs, two chains and an iron ingot. Set it 12 or more blocks above your fire, then hang the Great Bell from it.";
        } else if (!home.yard.isEmpty()) {
            Map.Entry<Integer, BlockPos> y = home.yard.entrySet().iterator().next();
            title = BellCatalog.of(y.getKey()).name() + " is in your yard";
            why = "The Registry's wagon left it there. Lift it, then hang it from a Great Beam 12 or more blocks above your fire.";
            at = y.getValue();
        } else if (home.roost != null && !FoundBells.asleep(p.level(), h.get().pos.asLong()) && !b.held.containsKey(BellCatalog.of("nook").no())) {
            title = "Take the Lampwing's old bell down from its tree";
            why = "It slept in it before it came to live with you. It's yours now.";
            at = home.roost;
        } else if (home.stage < 3 && n >= BellTower.GREAT) {
            title = "Raise the Great Belfry";
            why = "Only a Great Belfry can lead the Hundred Peal. Set the Heart-clapper and a Sovereign's relic into your bell frame.";
        } else if (n >= 100 && home.pealed < 0) {
            title = "Ring the Hundred Peal";
            why = "Your song is whole. Ring your bells and all hundred ring together.";
        } else return null;
        Goals.Builder s = Step.of("bells/tower", Goal.BELLS, title).thread("bells.tower", 10).why(why).priority(45);
        if (at != null) s.at(at);
        return s.build();
    }

    /** Free hooks on the home's Bell Frame (0 if it isn't loaded or set). */
    static int freeFrameHooks(ServerPlayer p, BellWorld.@org.jspecify.annotations.Nullable Home home) {
        if (home == null || home.frame == null || !p.level().isLoaded(home.frame)) return home != null && home.frame != null ? 1 : 0;
        if (!(p.level().getBlockEntity(home.frame) instanceof HooksEntity be)) return 0;
        int n = 0;
        for (int i = 0; i < be.hooks(); i++) if (be.bell(i) < 0) n++;
        return n;
    }
}
