package dev.awaken.living;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.living.combat.KillXp;
import dev.awaken.living.combat.PlateRules;
import dev.awaken.living.region.RegionMap;
import dev.awaken.living.region.RegionService;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Living Wilds' GameTests (SPEC §21), one per mechanism. Phase B: the region map, kill XP by level and role, the
 * plate rules.
 */
final class LivingTests {
    private LivingTests() {}

    static void register() {
        dev.awaken.living.quest.QuestTests.register();
        dev.awaken.living.bounty.BountyTests.register();
        dev.awaken.living.wayfarer.SceneTests.register();
        dev.awaken.living.pulse.PulseTests.register();
        dev.awaken.living.population.LedgerTests.register();
        dev.awaken.living.camp.CampTests.register();
        dev.awaken.living.craft.CraftTests.register();
        // SPEC §27.6: registration starts Bram's errand (his letters for Ilse), tracked and on the Now line over bookkeeping; the Registry's
        // Directive is the first floor, already taken on, with its progress on the line; no gate forms before level 3
        // the Now line's order (SPEC §27.6): a tracked chain's step, then a taken Directive, then points, then the road
        AwakenTests.register("progression_now_priorities", 100, h -> {
            net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
            try {
                dev.awaken.progression.Progression.awaken(p);
                dev.awaken.progression.Progression.count(p, "awaken:carillon_registered");
                dev.awaken.core.api.OpeningHooks.fireRegistered(p);
                dev.awaken.progression.Directives.tryOffer(p, "opening");
                dev.awaken.progression.Progression.topUpToLevel(p, 3, dev.awaken.progression.Progression.XpSource.COMMAND);
                List<String> ids = dev.awaken.core.api.NowHooks.collect(p, 20).stream().map(dev.awaken.core.api.NowHooks.Suggestion::id).toList();
                int chain = -1, directive = -1, points = -1, road = -1;
                for (int i = 0; i < ids.size(); i++) {
                    String id = ids.get(i);
                    if (id.startsWith("living:chain/") && chain < 0) chain = i;
                    if (id.startsWith("progression:directive/") && directive < 0) directive = i;
                    if (id.equals("progression:points")) points = i;
                    if (id.startsWith("progression:road/") && road < 0) road = i;
                }
                h.assertTrue(chain >= 0 && directive > chain && points > directive && (road < 0 || road > points),
                        "the chain's step, then the Directive, then points, then the road: " + ids);
            } finally {
                h.getLevel().getServer().getPlayerList().remove(p);
            }
            h.succeed();
        });
        AwakenTests.register("progression_first_thread", 100, h -> {
            net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
            try {
                dev.awaken.progression.Progression.awaken(p);
                dev.awaken.progression.Progression.count(p, "awaken:carillon_registered");
                String chain = FirstThread.chainId();
                h.assertTrue(chain != null, "The Last Delivery is in the quest book");
                dev.awaken.core.api.OpeningHooks.fireRegistered(p);
                var qs = dev.awaken.living.quest.QuestEngine.state(p);
                h.assertTrue(qs.has(chain) && qs.tracked().equals(chain), "registered: Bram's errand starts by itself, tracked");
                var letters = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(FirstScenes.LETTERS));
                h.assertTrue(p.getInventory().contains(letters), "his letters for Ilse in the pack (handed over at the Registry, or now if it was skipped)");
                dev.awaken.core.api.OpeningHooks.fireRegistered(p);
                h.assertTrue(qs.active().size() == 1, "and only once");
                var now = FirstThread.now(p);
                h.assertTrue(now.size() == 1 && now.getFirst().priority() > 55 && now.getFirst().text().getString().startsWith("Take the letters"),
                        "its step is on the Now line, over points to spend: " + now);
                dev.awaken.progression.Directives.tryOffer(p, "opening");
                var d = dev.awaken.progression.Progression.profile(p).directives();
                h.assertTrue(d.size() == 1 && d.getFirst().id().equals("awaken:first_floor") && d.getFirst().accepted(),
                        "the Registry's Directive: the first floor, taken on: " + d);
                h.assertTrue(dev.awaken.core.api.NowHooks.collect(p, 10).stream().anyMatch(s -> s.id().equals("progression:directive/awaken:first_floor")),
                        "and on the Now line");
                h.assertTrue(dev.awaken.gates.Director.upkeep(h.getLevel().getServer(), p, false) == null
                        && p.getData(dev.awaken.gates.GatesRegistry.MEMORY.get()).gatesRolled == 0, "no gate at level 1: the first one waits for you");
            } finally {
                h.getLevel().getServer().getPlayerList().remove(p);
            }
            h.succeed();
        });
        AwakenTests.register("progression_first_scenes", 100, h -> {
            var server = h.getLevel().getServer();
            net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
            dev.awaken.living.wayfarer.Wayfarer ilse = LivingContent.WAYFARER.get().create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.EVENT);
            dev.awaken.progression.Progression.awaken(p);
            dev.awaken.progression.Progression.count(p, "awaken:carillon_registered");
            String chain = FirstThread.chainId();
            h.assertTrue(chain != null && ilse != null, "The Last Delivery is in the quest book");
            var d = dev.awaken.living.quest.QuestBook.get(chain);
            var gate = d.steps().stream().filter(s -> s.id().equals("gate")).findFirst().orElseThrow();
            // out through the High Gate: the shot of the camp's smoke says Hale's line, once
            h.assertTrue(FirstScenes.stage(p, chain, gate) && FirstScenes.shielded(p), "the High Gate's shot goes, and nothing hurts you under it");
            h.assertFalse(FirstScenes.stage(p, chain, gate), "only the first time out");
            // the letters, counted on the Now line, the compass on Ilse
            h.assertTrue(dev.awaken.living.quest.QuestEngine.devStep(p, chain, "ilse"), "the errand at Ilse's step");
            var now = FirstThread.now(p);
            h.assertTrue(now.size() == 1 && now.getFirst().text().getString().equals("Deliver the letters · 0/1"), "the step counted: " + now);
            ilse.snapTo(p.getX() + 2, p.getY(), p.getZ(), 90, 0);
            ilse.become("ilse", ilse.blockPosition(), 2, 0);
            h.getLevel().addFreshEntity(ilse);
            var letters = new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(FirstScenes.LETTERS));
            p.getInventory().add(letters.copy());
            FirstScenes.begin(p, ilse);
            h.assertTrue(FirstScenes.shielded(p), "her scene holds you: nothing hunts you by the fire");
            FirstScenes.ready(p, LivingNet.FirstSceneReady.SKIP);
            h.runAfterDelay(4, () -> {
                var ember = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(FirstScenes.EMBER);
                try {
                    h.assertTrue(ilse.getMainHandItem().is(ember), "a held skip: she holds the coal out, in her hand");
                    h.assertFalse(p.getInventory().contains(letters), "the letters went from your pack into her hands");
                    FirstScenes.ready(p, LivingNet.FirstSceneReady.TOOK);
                    var qs = dev.awaken.living.quest.QuestEngine.state(p);
                    var a = qs.active().stream().filter(x -> x.chain().equals(chain)).findFirst().orElseThrow();
                    h.assertTrue(d.steps().get(a.step()).id().equals("ember"), "your hand closed on it: the next step is your own fire's");
                    h.assertTrue(p.getInventory().contains(new net.minecraft.world.item.ItemStack(ember)), "the coal is yours (the Ember)");
                    h.assertTrue(ilse.getMainHandItem().isEmpty(), "her hand is empty again");
                    h.assertTrue(FirstThread.now(p).isEmpty(), "the thread lets go at the Ember: the Hearth's line takes it from there");
                } finally {
                    ilse.discard();
                    server.getPlayerList().remove(p);
                }
                h.succeed();
            });
        });
        // SPEC §27.10: the current task, top right: Your body remembers ticks move by move, then the tracked errand
        AwakenTests.register("progression_checklist", 300, h -> {
            net.minecraft.server.level.ServerPlayer p = h.makeMockServerPlayerInLevel();
            boolean handedOn = false;
            try {
                dev.awaken.progression.Progression.awaken(p);
                dev.awaken.progression.Progression.count(p, "awaken:carillon_registered");
                var c = dev.awaken.progression.Checklist.now(p);
                h.assertTrue(c != null && c.title().equals("Your body remembers") && c.rows().size() == 4
                        && c.rows().stream().noneMatch(dev.awaken.progression.Checklist.Row::done), "first, the four moves, none done: " + c);
                h.assertTrue(c.rows().get(1).key().equals("key.sprint") && c.rows().get(1).text().contains("{key}"), "each with its key, drawn as a key");
                dev.awaken.progression.Progression.count(p, "awaken:body_roll");
                c = dev.awaken.progression.Checklist.now(p);
                h.assertTrue(c.rows().get(0).done() && !c.rows().get(1).done(), "a roll ticks the first");
                for (String m : new String[]{"awaken:body_sprint", "awaken:body_slide", "awaken:body_mantle"}) dev.awaken.progression.Progression.count(p, m);
                c = dev.awaken.progression.Checklist.now(p);
                h.assertTrue(c != null && c.rows().stream().allMatch(dev.awaken.progression.Checklist.Row::done), "all four ticked, shown for a moment");
                h.assertTrue(dev.awaken.progression.Progression.profile(p).counter(dev.awaken.progression.Checklist.MOVES_DONE) > 0, "and remembered as done");
                dev.awaken.core.api.OpeningHooks.fireRegistered(p);
                // its moment over (the real hold, not a faked clock: a young test server's "a minute ago" is before zero),
                // the errand takes the panel: its title, its steps, the letters counted
                h.runAfterDelay(dev.awaken.progression.Checklist.DONE_HOLD + 4, () -> {
                    try {
                        var t = dev.awaken.progression.Checklist.now(p);
                        var d = dev.awaken.living.quest.QuestBook.get(FirstThread.chainId());
                        h.assertTrue(t != null && t.title().equals(d.title()) && t.rows().getFirst().text().startsWith("Take the letters"),
                                "then the errand you're on: " + t);
                        h.assertTrue(t.rows().stream().anyMatch(r -> r.text().equals("Deliver the letters") && r.need() == 1), "a step with a count shows it");
                    } finally {
                        h.getLevel().getServer().getPlayerList().remove(p);
                    }
                    h.succeed();
                });
                handedOn = true;
            } finally {
                if (!handedOn) h.getLevel().getServer().getPlayerList().remove(p);
            }
        });
        // SPEC §27.6, "Control comes back": Ilse's scene ends whatever way you leave it, and lets go of you
        AwakenTests.register("progression_first_scene_exits", 200, h -> {
            var server = h.getLevel().getServer();
            var level = h.getLevel();
            var ember = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(FirstScenes.EMBER);
            net.minecraft.server.level.ServerPlayer walker = h.makeMockServerPlayerInLevel(), dier = h.makeMockServerPlayerInLevel(),
                    leaver = h.makeMockServerPlayerInLevel(), lost = h.makeMockServerPlayerInLevel();
            dev.awaken.living.wayfarer.Wayfarer ilse = LivingContent.WAYFARER.get().create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT),
                    gone = LivingContent.WAYFARER.get().create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            ilse.snapTo(walker.getX() + 2, walker.getY(), walker.getZ(), 90, 0);
            ilse.become("ilse", ilse.blockPosition(), 2, 0);
            gone.snapTo(lost.getX() - 2, lost.getY(), lost.getZ(), 90, 0);
            gone.become("ilse", gone.blockPosition(), 2, 0);
            level.addFreshEntity(ilse);
            level.addFreshEntity(gone);
            for (var p : java.util.List.of(walker, dier, leaver)) FirstScenes.begin(p, ilse);
            FirstScenes.begin(lost, gone);
            h.assertTrue(FirstScenes.shielded(walker) && FirstScenes.shielded(dier) && FirstScenes.shielded(leaver) && FirstScenes.shielded(lost),
                    "four scenes running");
            // the walker skips to the ember held out, then walks off mid-hand-over
            FirstScenes.ready(walker, LivingNet.FirstSceneReady.SKIP);
            h.runAfterDelay(4, () -> {
                h.assertTrue(ilse.getMainHandItem().is(ember), "the ember held out");
                walker.teleportTo(walker.getX() + 40, walker.getY(), walker.getZ());
                dier.setHealth(0);
                server.getPlayerList().remove(leaver);
                gone.discard();
                h.runAfterDelay(4, () -> {
                    try {
                        h.assertFalse(FirstScenes.shielded(walker), "walked off: the scene ends");
                        h.assertTrue(ilse.getMainHandItem().isEmpty(), "and her hand is empty again (the ember stays hers)");
                        h.assertFalse(FirstScenes.shielded(dier), "died: the scene ends");
                        h.assertFalse(FirstScenes.shielded(leaver), "logged out: the scene ends");
                        h.assertFalse(FirstScenes.shielded(lost), "Ilse gone: the scene ends");
                        // walking back starts it over from the top, and it can still be finished
                        FirstScenes.begin(walker, ilse);
                        h.assertTrue(FirstScenes.shielded(walker), "back again: her scene starts over");
                    } finally {
                        ilse.discard();
                        for (var p : java.util.List.of(walker, dier, lost)) server.getPlayerList().remove(p);
                    }
                    h.succeed();
                });
            });
        });
        AwakenTests.register("living_region_map", 200, h -> {
            RegionMap.Probe plain = (x, z) -> RegionMap.Land.PLAIN;
            RegionMap map = new RegionMap(1234L, 0, 0, plain);
            // the Commons: everything within its radius, level 1 by the wall, 8 at its edge
            h.assertTrue(map.regionAt(0, 0).commons() && map.regionAt(300, -200).commons(), "the centre is the Commons");
            h.assertTrue(map.levelAt(0, 0) == 1 && map.levelAt(240, 0) == 1, "level 1 by the wall: " + map.levelAt(240, 0));
            h.assertTrue(map.levelAt(690, 0) <= 8 && map.levelAt(690, 0) >= 7, "about 8 at the Commons' edge: " + map.levelAt(690, 0));
            // distance sets a ceiling (SPEC 21.10): nothing above it anywhere, nothing but the ceiling inside the apron
            int maxApron = 0, maxTo2500 = 0, lowFar = Integer.MAX_VALUE, highFar = 0;
            for (int i = -30; i <= 30; i++) {
                for (int j = -30; j <= 30; j++) {
                    int x = i * 190, z = j * 190;
                    double d = Math.hypot(x, z);
                    int lv = map.levelAt(x, z);
                    h.assertTrue(lv >= 1 && lv <= RegionMap.MAX_LEVEL, "levels stay 1-150: " + lv);
                    h.assertTrue(lv <= map.ceilingAt(d) + 1, "no land above its ceiling: " + lv + " at " + (int) d);
                    if (d <= 1000) maxApron = Math.max(maxApron, lv);
                    if (d <= 2500) maxTo2500 = Math.max(maxTo2500, lv);
                    if (d >= 3000 && d <= 4500) { lowFar = Math.min(lowFar, lv); highFar = Math.max(highFar, lv); }
                    RegionMap.Region r = map.regionAt(x, z);
                    if (!r.commons()) h.assertTrue(r.low() >= 1 && r.low() <= r.high() && r.high() <= RegionMap.MAX_LEVEL, "a region's span is ordered");
                }
            }
            h.assertTrue(maxApron <= 12, "the apron to 1,000 never passes 12: " + maxApron);
            h.assertTrue(maxTo2500 <= 42, "nothing above 42 within 2,500 (it was level 72 at 800): " + maxTo2500);
            h.assertTrue(highFar - lowFar >= 20, "it is still a patchwork far out: " + lowFar + " to " + highFar);
            // borders are slopes: no 100 blocks change by much, in any direction
            int worst = 0;
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8 + 0.05;
                int prev = -1;
                int[] ring = new int[(3000 - 650) / 25 + 1];
                for (int d = 650, n = 0; d <= 3000; d += 25, n++) ring[n] = map.levelAt((int) Math.round(Math.cos(a) * d), (int) Math.round(Math.sin(a) * d));
                for (int n = 4; n < ring.length; n++) worst = Math.max(worst, Math.abs(ring[n] - ring[n - 4]));
            }
            h.assertTrue(worst <= 16, "no 100 blocks change by more than 16 levels inside 3,000: " + worst);
            // the land tilts the temper (harsh up, gentle down), a region is worst at its heart
            RegionMap harsh = new RegionMap(1234L, 0, 0, (x, z) -> new RegionMap.Land(RegionMap.Group.DARK, 1));
            RegionMap gentle = new RegionMap(1234L, 0, 0, (x, z) -> new RegionMap.Land(RegionMap.Group.PLAINS, -1));
            long up = 0, down = 0;
            for (int i = 0; i < 400; i++) {
                int x = 3000 + (i % 20) * 160, z = 3000 + (i / 20) * 160;
                up += harsh.levelAt(x, z);
                down += gentle.levelAt(x, z);
            }
            h.assertTrue(up > down, "harsh land tilts levels up, gentle down: " + up + " vs " + down);
            RegionMap.Region far = map.regionAt(9000, 9000);
            h.assertTrue(map.levelAt(far.heartX(), far.heartZ()) >= far.low() && map.levelAt(far.heartX(), far.heartZ()) <= far.high(), "the heart is inside the region's span");
            // the same seed always gives the same world; another seed another one
            RegionMap again = new RegionMap(1234L, 0, 0, plain), other = new RegionMap(99L, 0, 0, plain);
            boolean differs = false;
            for (int i = 1; i < 30; i++) {
                h.assertTrue(again.regionAt(i * 1700, -i * 1300).name().equals(map.regionAt(i * 1700, -i * 1300).name()), "deterministic names");
                h.assertTrue(again.levelAt(i * 1700, -i * 1300) == map.levelAt(i * 1700, -i * 1300), "deterministic levels");
                differs |= !other.regionAt(i * 1700, -i * 1300).name().equals(map.regionAt(i * 1700, -i * 1300).name());
            }
            h.assertTrue(differs, "another seed, another patchwork");
            // a world made before keeps the old names but follows the same levels
            RegionMap old = new RegionMap(1234L, 0, 0, plain, 1);
            for (int i = 1; i < 30; i++) h.assertTrue(old.levelAt(i * 1700, -i * 1300) == map.levelAt(i * 1700, -i * 1300), "rules 1 has the same levels");
            h.assertTrue(old.regionAt(5000, 5000).band() >= 0 && old.regionAt(5000, 5000).band() <= 4, "rules 1 names by the old deal's band");
            // the server's map: centred once on Carillon or the world spawn, level 1 there
            RegionMap live = RegionService.map(h.getLevel().getServer());
            h.assertTrue(live.levelAt(live.centreX(), live.centreZ()) == 1, "level 1 at the world's centre");
            h.succeed();
        });

        AwakenTests.register("living_region_placed", 200, h -> {
            // SPEC §21.10: wherever the first hamlet goes (700-1,100 out, any way), the land is no harder than level 15 in any world
            RegionMap.Probe plain = (x, z) -> RegionMap.Land.PLAIN;
            int worst = 0;
            for (long seed = 1; seed <= 40; seed++) {
                RegionMap m = new RegionMap(seed * 7919, 100, -300, plain);
                for (int k = 0; k < 24; k++) {
                    double a = k * Math.PI / 12;
                    for (int d = 700; d <= 1100; d += 50) worst = Math.max(worst, m.levelAt(100 + (int) Math.round(Math.cos(a) * d), -300 + (int) Math.round(Math.sin(a) * d)));
                }
            }
            h.assertTrue(worst <= 15, "the land the first hamlet may stand on never passes 15: " + worst);
            h.assertTrue(dev.awaken.village.world.FirstHamlet.MAX_LEVEL == 12, "the first hamlet's cap is 12");
            // settled land and the roads between are calmer: the drop eases from 24 blocks to 60, a settlement's cap by its size
            h.assertTrue(dev.awaken.village.SettledCalm.dropShare(10) == 1 && dev.awaken.village.SettledCalm.dropShare(24) == 1, "all the drop within 24 of a road");
            h.assertTrue(Math.abs(dev.awaken.village.SettledCalm.dropShare(42) - 0.5) < 1e-9 && dev.awaken.village.SettledCalm.dropShare(60) == 0, "easing to nothing by 60");
            h.assertTrue(dev.awaken.village.SettledCalm.capOf(dev.awaken.village.plan.VillagePlan.Kind.HAMLET) == 10
                    && dev.awaken.village.SettledCalm.capOf(dev.awaken.village.plan.VillagePlan.Kind.VILLAGE) == 14
                    && dev.awaken.village.SettledCalm.capOf(dev.awaken.village.plan.VillagePlan.Kind.TOWN) == 18, "a hamlet 10, a village 14, a town 18");
            h.succeed();
        });

        AwakenTests.register("living_elite_by_rank", 20, h -> {
            h.assertTrue(LivingModule.eliteChance(1) == 0 && LivingModule.eliteChance(9) == 0, "none roam the heartland");
            h.assertTrue(LivingModule.eliteChance(10) == 0.02F && LivingModule.eliteChance(24) == 0.02F, "2% in D land");
            h.assertTrue(LivingModule.eliteChance(25) == 0.04F && LivingModule.eliteChance(44) == 0.04F, "4% in C land");
            h.assertTrue(LivingModule.eliteChance(45) == 0.05F && LivingModule.eliteChance(120) == 0.05F, "5% from B up");
            h.succeed();
        });

        AwakenTests.register("living_warning_ahead", 20, h -> {
            // the look ahead: 10 or more above you and rising
            h.assertTrue(dev.awaken.living.region.RegionTracker.aheadWarns(1, 8, 12), "level 12 ahead of a level-1 player at 8 warns");
            h.assertTrue(!dev.awaken.living.region.RegionTracker.aheadWarns(1, 8, 10), "level 10 is nine above: no");
            h.assertTrue(!dev.awaken.living.region.RegionTracker.aheadWarns(5, 30, 30), "already there, not rising: the caption's job");
            h.assertTrue(!dev.awaken.living.region.RegionTracker.aheadWarns(40, 30, 45), "five above is not ten");
            // the words, plain, with what the numbers mean against your own level (Euan's old Black Glades at level 72)
            String w = dev.awaken.living.region.RegionTracker.body("Black Glades", 72, 1);
            h.assertTrue(w.equals("Black Glades ahead. Creatures there are about six times as tough as ones at your level and hit nearly four times as hard. Turn back, or go in only to look."), w);
            h.assertTrue(dev.awaken.living.region.RegionTracker.times(2.0).equals("about twice") && dev.awaken.living.region.RegionTracker.times(1.5).equals("half again"), "small ratios in words");
            h.assertTrue(dev.awaken.living.region.RegionTracker.times(40).equals("many times"), "big ones too");
            h.succeed();
        });

        AwakenTests.register("living_region_rules_saved", 20, h -> {
            // a save from before §21.10 has no rules: it is rules 1 and keeps its old names; a new world is the current rules
            var old = new net.minecraft.nbt.CompoundTag();
            old.putBoolean("fixed", true);
            old.putInt("centre_x", 5);
            old.putInt("centre_z", -6);
            var parsed = RegionService.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, old).result();
            h.assertTrue(parsed.isPresent() && parsed.get().rules() == 1, "a save without rules is rules 1");
            h.assertTrue(new RegionService().rules() == RegionMap.RULES, "a new world is made under the current rules");
            var written = RegionService.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, new RegionService()).result();
            h.assertTrue(written.isPresent() && ((net.minecraft.nbt.CompoundTag) written.get()).getIntOr("rules", 0) == RegionMap.RULES, "and writes it down");
            h.succeed();
        });

        AwakenTests.register("living_kill_xp", 200, h -> {
            // the level difference: up to +40% above, a taper below
            h.assertTrue(Math.abs(KillXp.diff(0) - 1) < 1e-9 && Math.abs(KillXp.diff(5) - 1.4) < 1e-9 && Math.abs(KillXp.diff(12) - 1.4) < 1e-9, "above pays up to +40%");
            h.assertTrue(Math.abs(KillXp.diff(-8) - 0.5) < 1e-9, "eight levels below pays half");
            BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
            double zombie = kill(h, net.minecraft.world.entity.EntityTypes.ZOMBIE, CreatureTag.Kind.MONSTER, false, at);
            double sheep = kill(h, net.minecraft.world.entity.EntityTypes.SHEEP, CreatureTag.Kind.PASSIVE, false, at);
            double elite = kill(h, net.minecraft.world.entity.EntityTypes.ZOMBIE, CreatureTag.Kind.MONSTER, true, at);
            h.assertTrue(zombie > 0, "a monster's kill pays hunt XP");
            h.assertTrue(sheep > 0 && sheep < zombie, "a sheep pays a little: " + sheep + " vs " + zombie);
            h.assertTrue(elite > zombie * 2, "an elite pays several hunters: " + elite + " vs " + zombie);
            // a grind levels off: the twentieth kill in a row pays less than the first
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            Progression.awaken(p);
            double first = 0, last = 0;
            for (int i = 0; i < 20; i++) {
                double before = Progression.xpLedger(p).ofWorld(LivingModule.HUNT);
                killAs(h, p, net.minecraft.world.entity.EntityTypes.ZOMBIE, CreatureTag.Kind.MONSTER, false, at);
                double paid = Progression.xpLedger(p).ofWorld(LivingModule.HUNT) - before;
                if (i == 0) first = paid;
                last = paid;
            }
            h.assertTrue(last < first * 0.6, "the budget wears a grind down: " + last + " vs " + first);
            h.succeed();
        });

        AwakenTests.register("living_plate_rules", 20, h -> {
            long now = 1000;
            h.assertTrue(PlateRules.lookRange(5) == 24 && PlateRules.lookRange(20) == 32 && PlateRules.lookRange(45) == 40, "PER widens the read");
            List<PlateRules.Candidate> in = new ArrayList<>(), out = new ArrayList<>();
            in.add(new PlateRules.Candidate(1, 10, Long.MIN_VALUE, now, false, false));       // looked at
            for (int i = 0; i < 4; i++) in.add(new PlateRules.Candidate(10 + i, 5 + i, now - 20, Long.MIN_VALUE, false, false)); // fighting
            in.add(new PlateRules.Candidate(20, 30, now - 5, now, false, true));              // a player: never
            in.add(new PlateRules.Candidate(21, 12, Long.MIN_VALUE, Long.MIN_VALUE, true, false)); // a seen elite nearby
            PlateRules.pick(in, out, now, 24);
            h.assertTrue(out.size() == PlateRules.MAX, "never more than four plates: " + out.size());
            h.assertTrue(out.stream().allMatch(c -> c.id() >= 10 && c.id() < 14), "fights come first, nearest first");
            in.removeIf(c -> c.id() >= 10 && c.id() < 14);
            PlateRules.pick(in, out, now, 24);
            h.assertTrue(out.size() == 2 && out.get(0).id() == 1 && out.get(1).id() == 21, "then what you look at, then a notable creature");
            // fades: a fight's plate stays 6 s then fades over 3; a calm creature's fades 0.6 s after you look away
            var fight = new PlateRules.Candidate(2, 8, now - PlateRules.FIGHT_TICKS, Long.MIN_VALUE, false, false);
            var after = new PlateRules.Candidate(2, 8, now - PlateRules.FIGHT_TICKS - PlateRules.FIGHT_FADE, Long.MIN_VALUE, false, false);
            var look = new PlateRules.Candidate(3, 8, Long.MIN_VALUE, now - PlateRules.LOOK_FADE / 2, false, false);
            var far = new PlateRules.Candidate(4, 30, Long.MIN_VALUE, now, false, false);
            h.assertTrue(PlateRules.visibility(fight, now, 24) == 1 && PlateRules.visibility(after, now, 24) == 0, "a fight's plate: 6 s, then 3 s of fade");
            h.assertTrue(PlateRules.visibility(look, now, 24) > 0.4F && PlateRules.visibility(look, now, 24) < 0.6F, "looking away fades it");
            h.assertTrue(PlateRules.visibility(far, now, 24) == 0 && PlateRules.visibility(far, now, 32) == 1, "beyond the read, nothing (PER reads further)");
            h.succeed();
        });
    }

    /** XP one kill paid a fresh awakened player (its own budget), from a creature tagged as given. */
    private static double kill(net.minecraft.gametest.framework.GameTestHelper h, EntityType<? extends LivingEntity> type, CreatureTag.Kind kind, boolean elite, BlockPos at) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        Progression.awaken(p);
        killAs(h, p, type, kind, elite, at);
        return Progression.xpLedger(p).ofWorld(LivingModule.HUNT);
    }

    private static void killAs(net.minecraft.gametest.framework.GameTestHelper h, ServerPlayer p, EntityType<? extends LivingEntity> type, CreatureTag.Kind kind, boolean elite, BlockPos at) {
        LivingEntity e = type.create(h.getLevel(), EntitySpawnReason.COMMAND);
        if (e == null) return;
        e.setPos(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        h.getLevel().addFreshEntity(e);
        int lv = Progression.profile(p).level();
        e.setData(CoreApi.CREATURE_TAG.get(), new CreatureTag(lv, kind, elite, ""));
        e.hurtServer(h.getLevel(), p.damageSources().playerAttack(p), 10000);
        if (e.isAlive()) e.discard();
    }
}
