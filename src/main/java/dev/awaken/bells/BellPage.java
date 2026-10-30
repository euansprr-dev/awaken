package dev.awaken.bells;

import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The Bell Book's words, built on the server (SPEC §43.7): the hundred (a bell's name shows once you hold it or have a
 * lead on it; every slot shows its rank), the leads in the System's words with Track, and your tower: how many hang,
 * what's next, what your bells do while they hang, and which peals you can ring or are one bell away from.
 */
final class BellPage {
    private BellPage() {}

    static BellNet.Page build(ServerPlayer p) {
        BellBook b = Bells.book(p);
        Optional<HearthRecord> h = HearthService.data(p.level().getServer()).home(p.getUUID());
        BellWorld.Home home = h.map(r -> BellWorld.get(p.level().getServer()).peek(r.pos)).orElse(null);
        Set<Integer> hung = home == null ? Set.of() : home.hung;

        List<List<String>> hundred = new ArrayList<>();
        for (BellCatalog.Def d : BellCatalog.ALL) {
            BellBook.Lead l = b.leads.get(d.no());
            String state = b.held.containsKey(d.no()) ? "held" : hung.contains(d.no()) ? "hung" : l != null ? "lead" : "";
            String name = state.isEmpty() || (state.equals("lead") && Bells.title(d, l).startsWith("A bell")) ? "" : d.name();
            String where = b.held.containsKey(d.no()) ? b.held.get(d.no()).where() : "";
            hundred.add(List.of(d.number(), name, d.rank().name(), state, where));
        }

        List<List<String>> leads = new ArrayList<>();
        // a held bell's lead is done: never listed, even from a save made before ringing it dropped its lead (SPEC §27.12)
        b.leads.entrySet().stream().filter(e -> !b.held.containsKey(e.getKey())).sorted((x, y) -> Long.compare(y.getValue().since, x.getValue().since)).forEach(e -> {
            BellCatalog.Def d = BellCatalog.of(e.getKey());
            BellBook.Lead l = e.getValue();
            List<String> hows = new ArrayList<>();
            for (BellBook.How w : BellBook.How.values()) if (l.has(w)) hows.add(w.name().charAt(0) + w.name().substring(1).toLowerCase(java.util.Locale.ROOT));
            leads.add(List.of(d.number(), Bells.title(d, l), String.join("\n", l.lines), String.join(" · ", hows), l.bearings > 0 ? "1" : "0",
                    b.tracked == d.no() ? "1" : "0"));
        });

        List<List<String>> tower = new ArrayList<>();
        int n = hung.size();
        long lo = 0, hi = 0;
        for (int no : hung) {
            if (no < 64) lo |= 1L << no;
            else hi |= 1L << (no - 64);
        }
        boolean frame = home != null && home.frame != null;
        String next = h.isEmpty() ? "Your bells hang in a tower at home. Light an Ember and make one."
                : !frame ? "Set your bell frame down inside your Hearth and your bells hang there."
                : n < BellTower.BELFRY ? (BellTower.BELFRY - n) + " more bells and a belfry goes up round the frame."
                : home.stage < 1 ? "Your belfry is ready to go up, but it needs a clear space five wide and ten high round the frame. Clear it and it goes up at the next song."
                : n < BellTower.TOWER ? (BellTower.TOWER - n) + " more bells and it becomes a stone tower."
                : home.stage < 2 ? "Your stone tower is ready to go up, but it needs a clear space five wide and fifteen high round the frame. Clear it and it goes up at the next song."
                : home != null && home.stage < 3 ? "Your tower can't hold the rest. It needs the Great Belfry: the Heart-clapper and a Sovereign's relic, set into the frame."
                : n < 100 ? (100 - n) + " bells are still missing from your song."
                : home.pealed < 0 ? "Your song is whole. Ring your bells and all hundred ring together: the Hundred Peal."
                : "Your song is whole. Your bells have rung the Hundred Peal.";
        tower.add(List.of(Integer.toString(n), Integer.toString(home == null ? 0 : home.stage), next, Long.toString(lo), Long.toString(hi), frame ? "1" : "0"));
        for (BellCatalog.Voice v : BellCatalog.Voice.values()) {
            if (v == BellCatalog.Voice.NONE) continue;
            int c = 0;
            for (int no : hung) if (BellCatalog.of(no).voice() == v) c++;
            if (c > 0) tower.add(List.of("voice", v.words, Integer.toString(c)));
        }
        for (BellCatalog.Peal pl : BellCatalog.PEALS) {
            List<String> missing = new ArrayList<>();
            for (String id : pl.bells()) if (!b.has(id)) missing.add(id);
            String state = missing.isEmpty() ? "have" : missing.size() == 1 && b.leads.containsKey(BellCatalog.of(missing.get(0)).no())
                    ? "One bell away: " + BellCatalog.of(missing.get(0)).name() + "." : missing.size() == 1 ? "One bell away." : "";
            if (!state.isEmpty()) tower.add(List.of("peal", pl.name(), pl.words(), state));
        }
        if (b.pealed) tower.add(List.of("peal", HundredPeal.KINSONG_NAME, HundredPeal.KINSONG_WORDS, "have"));
        return new BellNet.Page(hundred, leads, tower);
    }
}
