package dev.awaken.progression;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.HoloBand;
import dev.awaken.core.api.NowHooks;
import dev.awaken.core.api.OpeningHooks;
import dev.awaken.core.api.RenownHooks;
import dev.awaken.core.api.TrailsApi;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic probe for the Wild start plan (docs/wild-start/PLAN.md §2): walks a world with no Carillon (the GameTest
 * server has no noise overworld, so no city, as a Classic start) through the start of the ladder and logs, line by
 * line, what a player there has and what they never get. It reports and always succeeds: the plan's ladder test
 * replaces it with assertions. Not for merge.
 */
final class WildProbeTests {
    private WildProbeTests() {}

    static void register() {
        AwakenTests.register("wildprobe_classic_ladder", 400, h -> {
            var server = h.getLevel().getServer();
            List<String> out = new ArrayList<>();
            out.add("city site present: " + dev.awaken.terrain.api.CityBasin.site(server).isPresent());

            // 1. story off: does anything start the awakening?
            ServerPlayer off = h.makeMockServerPlayerInLevel();
            boolean was = AwakenConfig.STORY.get();
            AwakenConfig.STORY.set(false);
            off.tickCount = 120;
            for (int i = 0; i < 5; i++) {
                off.tickCount += 20;
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(off));
            }
            out.add("story=false: prologue " + dev.awaken.story.Story.prologue(off) + ", awakened " + Progression.profile(off).awakened());
            AwakenConfig.STORY.set(was);
            // the same player with the story on: the glitch comes
            for (int i = 0; i < 2; i++) {
                off.tickCount += 20;
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(off));
            }
            out.add("story=true: prologue " + dev.awaken.story.Story.prologue(off) + " (the Classic start's own beginning)");

            // 2. a Classic player, awakened by the prologue
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            out.add("start point (Roost): " + CityHooks.startPoint(p) + ", in opening: " + OpeningHooks.inOpening(p));
            dev.awaken.story.Story.skipPrologue(p);
            out.add("awakened: " + Progression.profile(p).awakened() + ", level " + Progression.profile(p).level());

            h.runAfterDelay(120, () -> {
                List<String> o = out;
                o.add("Hearth Ember carried: " + dev.awaken.hearth.Embers.carries(p));
                o.add("first power pending (cards): " + dev.awaken.powers.Starter.pending(p));
                o.add("Holo band worn: " + HoloBand.worn(p));
                o.add("registered counter: " + Progression.profile(p).counter("awaken:carillon_registered"));
                for (String chain : List.of("awaken:the_last_delivery", "awaken:orens_first_steel")) {
                    var d = dev.awaken.living.quest.QuestBook.get(chain);
                    if (d == null) d = dev.awaken.living.quest.QuestBook.get(chain.substring(7));
                    o.add("chain " + chain + ": " + (d == null ? "missing" : "requires " + d.requires() + " met " + Requirements.met(p, d.requires())));
                }
                var t = dev.awaken.trails.Trails.get("awaken:wayfarers_and_camps");
                if (t == null) t = dev.awaken.trails.Trails.get("wayfarers_and_camps");
                if (t != null) for (var s : t.steps()) {
                    o.add("trail wayfarers_and_camps/" + s.id() + " done-tokens " + s.done() + " met " + dev.awaken.trails.Conditions.allMet(p, s.done()));
                }
                o.add("trail place 'quartermaster': " + TrailsApi.placeOf("quartermaster", p));
                o.add("Hale's issue pending: " + dev.awaken.core.api.ArmsApi.issuePending(p));
                o.add("camp marker: " + dev.awaken.living.wayfarer.Scenes.marker(server, 0, "camp") + ", spawn " + server.overworld().getRespawnData().pos());
                o.add("Hale placed: " + dev.awaken.living.wayfarer.Scenes.residentAt(server, "hale"));
                for (var s : NowHooks.collect(p, 12)) o.add("now: [" + s.priority() + "] " + s.text().getString() + " @ " + s.where().map(BlockPos::toShortString).orElse("no place"));
                o.add("renown tier: " + RenownHooks.tier(p) + " (KNOWN=" + RenownHooks.KNOWN + ")");
                o.add("advice (Registry clerk only): " + Advice.next(p));
                o.add("directives: " + Progression.profile(p).directives().size());
                for (String line : o) Awaken.LOG.info("[wildprobe] {}", line);
                h.succeed();
            });
        });
    }
}
