package dev.awaken.progression;

import dev.awaken.core.AwakenConfig;
import dev.awaken.core.PlayDays;
import dev.awaken.progression.content.Content;
import dev.awaken.progression.content.Defs;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The Daily Regimen: three quick drills each in-game morning. Optional; skipping costs nothing unless Hardline.
 * Never tied to real-world days (SPEC section 4.9).
 */
public final class Regimen {
    private Regimen() {}

    /** Levels for finishing all three drills (tools/sim/pacing_sim.py REGIMEN_LEVELS). */
    static final double REWARD_LEVELS = 0.1;

    /** Installed by the module that builds the Penalty Zone (Hardline only). */
    public static Consumer<ServerPlayer> penaltyHandler = p -> {};

    static void morning(ServerPlayer player) {
        Profile p = Progression.profile(player);
        if (!p.awakened()) return;
        long today = PlayDays.current(player.level().getServer());
        if (p.regimen.day() == today) return;
        boolean unfinished = p.regimen.day() >= 0 && p.regimen.done().size() < p.regimen.drills().size();
        if (unfinished && AwakenConfig.DIFFICULTY.get() == AwakenConfig.Difficulty.HARDLINE) penaltyHandler.accept(player);

        List<Map.Entry<Identifier, Defs.DrillDef>> pool = new ArrayList<>();
        for (var e : Content.DRILLS.map.entrySet()) {
            var d = e.getValue();
            if (p.rank().ordinal() < d.minRank().ordinal()) continue;
            if (!d.paths().isEmpty() && !d.paths().contains(p.path)) continue;
            if (!Requirements.met(player, d.requires())) continue; // never a drill you can't do yet
            pool.add(e);
        }
        pool.sort(Map.Entry.comparingByKey()); // map order isn't stable: keep the roll reproducible
        List<String> drills = new ArrayList<>();
        Set<String> counters = new HashSet<>();
        var rnd = player.getRandom();
        while (drills.size() < 3 && !pool.isEmpty()) {
            var e = pool.remove(rnd.nextInt(pool.size()));
            drills.add(e.getKey().toString());
            e.getValue().condition().counters(counters);
        }
        p.regimen = new Profile.RegimenState(today, drills, List.of(), Progression.snapshot(p, counters));
        if (drills.isEmpty()) return;
        var w = SignalWindow.builder(SignalWindow.Kind.DIRECTIVE, Component.literal("Daily Regimen"));
        for (String id : drills) w.row(Component.literal("•"), Component.literal(Content.DRILLS.get(id).text()));
        w.footer(Component.literal("Optional · +1 point and a tenth of a level"));
        Signal.send(player, w.ttl(20 * 10).build());
        Progression.markDirty(player);
    }

    static void evaluate(ServerPlayer player, Profile p) {
        var r = p.regimen;
        if (r.drills().isEmpty() || r.done().size() >= r.drills().size()) return;
        List<String> done = new ArrayList<>(r.done());
        boolean changed = false;
        for (String id : r.drills()) {
            if (done.contains(id)) continue;
            var def = Content.DRILLS.get(id);
            if (def != null && def.condition().test(p, r.baseline())) {
                done.add(id);
                changed = true;
                Signal.toast(player, Component.literal("Regimen: " + def.text() + " ✓"));
            }
        }
        if (!changed) return;
        p.regimen = new Profile.RegimenState(r.day(), r.drills(), done, r.baseline());
        if (done.size() == r.drills().size()) {
            p.unspent += 1;
            // a tenth of a level (v1: a quarter): a morning comes every 20 minutes, so this is a steady source (tools/sim)
            Progression.grantLevels(player, REWARD_LEVELS, Progression.XpSource.REGIMEN);
            p.counters.merge("awaken:regimens_done", 1L, Long::sum);
            Signal.send(player, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Regimen complete"))
                    .body(Component.literal("+1 point and a tenth of a level"))
                    .presentation(SignalWindow.Presentation.TOAST).build());
        }
        Progression.markDirty(player);
    }
}
