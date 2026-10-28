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
                        // a first power still to choose (the cards were closed) comes before the errand, then goes
                        var t = dev.awaken.progression.Checklist.now(p);
                        h.assertTrue(t != null && t.id().equals("powers:first"), "the first power before the errand: " + t);
                        dev.awaken.powers.Starter.offer(p);
                        dev.awaken.powers.Starter.choose(p, dev.awaken.powers.Starter.state(p).offer().getFirst());
                        t = dev.awaken.progression.Checklist.now(p);
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
            // rules 2 (a world made before the wide world, and the GameTest server's own): the ceiling by distance, SPEC §21.10
            RegionMap map = new RegionMap(1234L, 0, 0, plain, 2);
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
            // quiet vales: some far regions are named like shelters and are calm, and a world from before keeps its old names
            int quietSeen = 0;
            for (int i = -60; i <= 60; i++) for (int j = -60; j <= 60; j++) {
                var r = map.regionAt(i * 160, j * 160);
                if (!r.commons() && r.name().matches(".*(Rest|Vale|Hush|Haven|Shelter|Stillwater)$")) quietSeen++;
            }
            h.assertTrue(quietSeen > 0, "quiet vales are named like shelters");
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
            RegionMap harsh = new RegionMap(1234L, 0, 0, (x, z) -> new RegionMap.Land(RegionMap.Group.DARK, 1), 2);
            RegionMap gentle = new RegionMap(1234L, 0, 0, (x, z) -> new RegionMap.Land(RegionMap.Group.PLAINS, -1), 2);
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
            RegionMap again = new RegionMap(1234L, 0, 0, plain, 2), other = new RegionMap(99L, 0, 0, plain, 2);
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
            h.assertTrue(live.rules() == RegionMap.TEST_RULES && RegionService.newWorldRules(h.getLevel().getServer()) == RegionMap.TEST_RULES,
                    "the GameTest server's world keeps 900-block regions: " + live.rules());
            h.succeed();
        });

        AwakenTests.register("living_region_wide", 400, h -> {
            // SPEC §21.12, the wide world (rules 3): bigger regions, ranks rolled by odds that shift with distance, calm ways everywhere
            RegionMap.Probe plain = (x, z) -> RegionMap.Land.PLAIN;
            RegionMap map = new RegionMap(1234L, 0, 0, plain, 3);
            h.assertTrue(RegionMap.RULES == 3 && map.pitch() == 1600, "a new world's regions are 1,600 apart: " + map.pitch());
            h.assertTrue(map.levelAt(0, 0) == 1 && map.levelAt(240, 0) == 1, "the Commons' ramp starts at 1");
            int worstNear = 0, lowest800 = 99;
            for (int k = 0; k < 24; k++) {
                double a = k * Math.PI / 12;
                lowest800 = Math.min(lowest800, map.levelAt((int) Math.round(Math.cos(a) * 800), (int) Math.round(Math.sin(a) * 800)));
                for (int d = 700; d <= 1300; d += 50) worstNear = Math.max(worstNear, map.levelAt((int) Math.round(Math.cos(a) * d), (int) Math.round(Math.sin(a) * d)));
            }
            h.assertTrue(lowest800 >= 8, "800 out is 8 or more, so camps and dens still seed by the wall: " + lowest800);
            h.assertTrue(worstNear <= 12, "the safe start to 1,300 never passes 12: " + worstNear);
            // a low player is never walled in: land of 9 or less runs from the wall to 12,000 out (a flood over a 100-block grid)
            int n = 241, c = n / 2;
            boolean[] seen = new boolean[n * n];
            int[] queue = new int[n * n];
            int head = 0, tail = 0, reach = 0;
            seen[c * n + c] = true;
            queue[tail++] = c * n + c;
            while (head < tail) {
                int q = queue[head++], qi = q / n, qj = q % n;
                reach = Math.max(reach, (int) (Math.hypot(qi - c, qj - c) * 100));
                for (int di = -1; di <= 1; di++)
                    for (int dj = -1; dj <= 1; dj++) {
                        int a = qi + di, b = qj + dj;
                        if (a < 0 || b < 0 || a >= n || b >= n || seen[a * n + b]) continue;
                        if (Math.hypot(a - c, b - c) * 100 > 12000 || map.levelAt((a - c) * 100, (b - c) * 100) > 9) continue;
                        seen[a * n + b] = true;
                        queue[tail++] = a * n + b;
                    }
            }
            h.assertTrue(reach >= 11500, "a level 1 player can walk calm land to 12,000 out: " + reach);
            // mixed, yet harder further out: far land holds both calm and the worst, and is harder on the whole than near land
            int calmFar = 0, sFar = 0, highFar = 0, countFar = 0;
            long sumNear = 0, sumFar = 0;
            int countNear = 0;
            for (int i = -150; i <= 150; i++)
                for (int j = -150; j <= 150; j++) {
                    int x = i * 100, z = j * 100;
                    double d = Math.hypot(x, z);
                    if (d >= 3000 && d <= 5000) { sumNear += map.levelAt(x, z); countNear++; }
                    if (d >= 11000 && d <= 15000) {
                        int lv = map.levelAt(x, z);
                        sumFar += lv;
                        countFar++;
                        if (lv <= 9) calmFar++;
                        if (lv >= 70) highFar++;
                        if (lv >= 100) sFar++;
                    }
                }
            double calmShare = calmFar / (double) countFar, highShare = highFar / (double) countFar, sShare = sFar / (double) countFar;
            dev.awaken.Awaken.LOG.info("[living] wide world census (seed 1234): calm walk to {} out; 11-15 km E {}% A+S {}% S {}%; mean level 3-5 km {}, 11-15 km {}",
                    reach, Math.round(calmShare * 100), Math.round(highShare * 100), Math.round(sShare * 100), sumNear / countNear, sumFar / countFar);
            h.assertTrue(calmShare >= 0.06 && calmShare <= 0.30, "far out some land is still calm, not most: " + calmShare);
            h.assertTrue(highShare >= 0.20 && sShare >= 0.04, "far out A and S land are common: " + highShare + ", S " + sShare);
            h.assertTrue(sumFar / (double) countFar > sumNear / (double) countNear + 20, "harder further out: " + sumNear / countNear + " near, " + sumFar / countFar + " far");
            // borders and the calm ways' edges are slopes, not walls: gentle where a new player walks, never a cliff far out
            int worst = 0, worstNear5k = 0;
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8 + 0.05;
                int[] ring = new int[(14000 - 700) / 25 + 1];
                for (int d = 700, m = 0; d <= 14000; d += 25, m++) ring[m] = map.levelAt((int) Math.round(Math.cos(a) * d), (int) Math.round(Math.sin(a) * d));
                for (int m = 4; m < ring.length; m++) {
                    int step = Math.abs(ring[m] - ring[m - 4]);
                    worst = Math.max(worst, step);
                    if (700 + m * 25 <= 5000) worstNear5k = Math.max(worstNear5k, step);
                }
            }
            h.assertTrue(worstNear5k <= 25, "inside 5,000 no 100 blocks change by more than 25 levels: " + worstNear5k);
            h.assertTrue(worst <= 50, "nowhere do 100 blocks change by more than 50 levels: " + worst);
            // regions: spans ordered, the heart inside its span, the same seed the same world
            RegionMap.Region far = map.regionAt(9000, 9000);
            int heart = map.levelAt(far.heartX(), far.heartZ());
            h.assertTrue(heart >= far.low() && heart <= far.high() && far.band() == RegionMap.rankOf(heart), "the heart is inside the region's span");
            RegionMap again = new RegionMap(1234L, 0, 0, plain, 3);
            for (int i = 1; i < 30; i++) {
                h.assertTrue(again.levelAt(i * 1700, -i * 1300) == map.levelAt(i * 1700, -i * 1300), "deterministic levels");
                h.assertTrue(again.regionAt(i * 1700, -i * 1300).name().equals(map.regionAt(i * 1700, -i * 1300).name()), "deterministic names");
            }
            h.assertTrue(new RegionMap(1234L, 0, 0, plain, 2).pitch() == RegionMap.PITCH_2, "a world made before keeps 900-block regions");
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
            // the ring just past the wall is level 8 or more, so riftborn camps and dens still seed there (Camps.seedCell, SPEC §21.8): the Arms planner's E-theme camps stay reachable
            int lowest = 99;
            for (long seed = 1; seed <= 20; seed++) {
                RegionMap m = new RegionMap(seed * 104729, 0, 0, plain);
                for (int k = 0; k < 16; k++) lowest = Math.min(lowest, m.levelAt((int) Math.round(Math.cos(k * Math.PI / 8) * 800), (int) Math.round(Math.sin(k * Math.PI / 8) * 800)));
            }
            h.assertTrue(lowest >= 8, "800 blocks out is level 8 or more in every direction: " + lowest);
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
            // every creature's plate carries its level, a meek cow in E land too (Euan 2026-10-27)
            h.assertTrue(PlateRules.showsLevel(new CreatureTag(3, CreatureTag.Kind.PASSIVE, false, "")) && PlateRules.showsLevel(new CreatureTag(2, CreatureTag.Kind.AMBIENT, false, ""))
                    && !PlateRules.showsLevel(CreatureTag.NONE), "animals and wildlife show their level; an untagged creature does not");
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

        AwakenTests.register("living_wild_kin", 100, h -> {
            var pig = net.minecraft.world.entity.EntityTypes.PIG;
            // who changes: a wild pig in D land or higher, never in E land, bred, a baby, or on a settlement's land
            h.assertTrue(!dev.awaken.living.combat.WildKin.fights(pig, true, false, false, 9), "E land is exactly as before");
            h.assertTrue(dev.awaken.living.combat.WildKin.fights(pig, true, false, false, 10), "D land: a wild pig fights back");
            h.assertTrue(!dev.awaken.living.combat.WildKin.fights(pig, false, false, false, 60) && !dev.awaken.living.combat.WildKin.fights(pig, true, true, false, 60)
                    && !dev.awaken.living.combat.WildKin.fights(pig, true, false, true, 60), "bred, a baby, a settlement's: meek");
            h.assertTrue(!dev.awaken.living.combat.WildKin.fights(net.minecraft.world.entity.EntityTypes.ZOMBIE, true, false, false, 60), "only the farm animals change");
            h.assertTrue(dev.awaken.living.combat.WildKin.wild(net.minecraft.world.entity.EntitySpawnReason.NATURAL) && !dev.awaken.living.combat.WildKin.wild(net.minecraft.world.entity.EntitySpawnReason.BREEDING)
                    && !dev.awaken.living.combat.WildKin.wild(net.minecraft.world.entity.EntitySpawnReason.SPAWN_ITEM_USE), "natural spawns only");
            h.assertTrue(Math.abs(dev.awaken.living.combat.WildKin.growth(1) - 0.04) < 1e-9 && dev.awaken.living.combat.WildKin.growth(9) == 0.2, "bigger with the rank, up to a fifth");
            h.assertTrue(!dev.awaken.living.combat.WildKin.rallies(1) && dev.awaken.living.combat.WildKin.rallies(2), "the herd rallies from C land");
            // the animal itself: it has a bite now, and armed it has a fight and a grudge that lets go
            var p = h.spawn(pig, 1, 2, 1);
            h.assertTrue(p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) != null, "a pig has an attack damage attribute");
            dev.awaken.living.combat.WildKin.arm(p, 40);
            boolean melee = false, grudge = false;
            for (var w : p.goalSelector.getAvailableGoals()) melee |= w.getGoal() instanceof net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
            for (var w : p.targetSelector.getAvailableGoals()) grudge |= w.getGoal() instanceof net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
            h.assertTrue(melee && grudge, "armed: it bites back");
            h.succeed();
        });

        AwakenTests.register("living_form_mix", 100, h -> {
            // SPEC 21.11 decision 22: nothing in E land (the first hour), then 70% the land's rung, 25% below (half in a rank's first three levels), 5% above
            var rnd = net.minecraft.util.RandomSource.create(7);
            for (int lv : new int[]{1, 5, 9}) for (int i = 0; i < 500; i++) h.assertTrue(dev.awaken.living.combat.Forms.rungFor(lv, rnd) == 0, "E land wears no form: " + lv);
            int[][] counts = new int[4][7];
            int[] levels = {10, 18, 40, 130};
            for (int k = 0; k < levels.length; k++) for (int i = 0; i < 20000; i++) counts[k][dev.awaken.living.combat.Forms.rungFor(levels[k], rnd)]++;
            // level 10 is the first of D: half and half with vanilla, a twentieth of C
            h.assertTrue(Math.abs(counts[0][0] / 20000.0 - 0.50) < 0.03 && Math.abs(counts[0][2] / 20000.0 - 0.05) < 0.015 && counts[0][1] > 0.4 * 20000, "the first levels of D: half vanilla, half D, a taste of C");
            h.assertTrue(Math.abs(counts[1][0] / 20000.0 - 0.25) < 0.03 && Math.abs(counts[1][1] / 20000.0 - 0.70) < 0.03, "mid D: a quarter vanilla, seven tenths D");
            h.assertTrue(Math.abs(counts[2][1] / 20000.0 - 0.25) < 0.03 && Math.abs(counts[2][2] / 20000.0 - 0.70) < 0.03 && Math.abs(counts[2][3] / 20000.0 - 0.05) < 0.015, "C land: a quarter D, seven tenths C, a twentieth B");
            h.assertTrue(counts[3][5] / 20000.0 > 0.7 && counts[3][4] / 20000.0 > 0.2 && counts[3][6] == 0, "S land: S and A only, never past the fifth rung");
            h.succeed();
        });

        AwakenTests.register("living_form_state", 100, h -> {
            var level = h.getLevel();
            // an alpha wears at least its land's rung and the crown; a baby and a creature that is not on the list wear nothing
            var zombie = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, 1, 2, 1);
            dev.awaken.living.combat.Forms.roll(level, zombie, 40, true);
            var f = dev.awaken.living.combat.Forms.of(zombie);
            h.assertTrue(f.rung() >= 2 && f.crowned(), "an elite in C land wears C or more, and the crown: " + f);
            var plain = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, 2, 2, 1);
            dev.awaken.living.combat.Forms.roll(level, plain, 5, false);
            h.assertTrue(!dev.awaken.living.combat.Forms.of(plain).worn() && plain.hasData(dev.awaken.living.LivingContent.FORM.get()), "E land: rolled once, vanilla");
            var bat = h.spawn(net.minecraft.world.entity.EntityTypes.BAT, 3, 2, 1);
            dev.awaken.living.combat.Forms.roll(level, bat, 90, false);
            h.assertTrue(!bat.hasData(dev.awaken.living.LivingContent.FORM.get()), "a bat is not on the list");
            var calf = h.spawn(net.minecraft.world.entity.EntityTypes.COW, 4, 2, 1);
            calf.setBaby(true);
            dev.awaken.living.combat.Forms.roll(level, calf, 90, false);
            h.assertTrue(!calf.hasData(dev.awaken.living.LivingContent.FORM.get()), "a baby wears nothing");
            // saved and sent: the codec and the stream codec round-trip
            var form = new dev.awaken.living.combat.Forms.Form(4, 2, 1);
            var enc = dev.awaken.living.combat.Forms.Form.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, form).result().orElseThrow();
            h.assertTrue(dev.awaken.living.combat.Forms.Form.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, enc).result().orElseThrow().equals(form), "saved form round-trips");
            var buf = io.netty.buffer.Unpooled.buffer();
            dev.awaken.living.combat.Forms.Form.STREAM_CODEC.encode(buf, form);
            h.assertTrue(dev.awaken.living.combat.Forms.Form.STREAM_CODEC.decode(buf).equals(form), "sent form round-trips");
            // the materials: cold land wears rime, hot land sun-baked bone
            var biomes = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME);
            h.assertTrue(dev.awaken.living.combat.Forms.family(biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS).value()) == dev.awaken.living.combat.Forms.FROST
                    && dev.awaken.living.combat.Forms.family(biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.DESERT).value()) == dev.awaken.living.combat.Forms.SUN
                    && dev.awaken.living.combat.Forms.family(biomes.getOrThrow(net.minecraft.world.level.biome.Biomes.PLAINS).value()) == dev.awaken.living.combat.Forms.GREEN, "the family follows the climate");
            // every species the client dresses has the forms' eyes for its vanilla body (D, and LITE): the sockets and each rung's light
            for (var sp : dev.awaken.living.combat.Forms.Species.values()) {
                h.assertTrue(LivingTests.class.getResource("/assets/awaken/textures/entity/forms/" + sp.layout + "/o1.png") != null, sp.layout + " has its sockets");
                for (int r = 1; r <= 5; r++)
                    h.assertTrue(LivingTests.class.getResource("/assets/awaken/textures/entity/forms/" + sp.layout + "/g" + r + ".png") != null, sp.layout + " has rung " + r + "'s eyes");
            }
            h.succeed();
        });

        AwakenTests.register("living_form_moves", 100, h -> {
            var M = dev.awaken.living.combat.Moves.class;
            var Sp = dev.awaken.living.combat.Forms.Species.class;
            // the tell shortens with the rung, 1.2 s in D to 0.7 s in S
            h.assertTrue(dev.awaken.living.combat.Moves.windup(1) == 24 && dev.awaken.living.combat.Moves.windup(5) == 14, "tells run 24 down to 14 ticks");
            // every species but the creeper has a move at every rung but the two that are not goals (a revenant rises, a bonepicker keeps its distance); none hits harder than 1.3 times a normal blow
            for (var sp : dev.awaken.living.combat.Forms.Species.values()) {
                for (int r = 1; r <= 5; r++) {
                    var moves = dev.awaken.living.combat.Moves.of(sp, r, false);
                    boolean none = sp.layout.equals("creeper") || (sp.layout.equals("zombie") && r == 4) || (sp.layout.equals("skeleton") && r == 1);
                    h.assertTrue(moves.isEmpty() == none, sp.id + " rung " + r + (none ? " has no goal move" : " has a move"));
                    for (var d : moves) h.assertTrue(d.power() <= 1.3F && d.windup() >= 14 && d.windup() <= 40, sp.id + " " + d.name() + " keeps to the caps");
                }
            }
            h.assertTrue(dev.awaken.living.combat.Moves.of(dev.awaken.living.combat.Forms.Species.PIG, 4, true).size() == 2
                    && dev.awaken.living.combat.Moves.of(dev.awaken.living.combat.Forms.Species.PIG, 1, true).size() == 1, "a crowned one from the third rung has the rung below's move too");
            // a formed creature gets its goal once, however many times it joins
            var level = h.getLevel();
            var pig = h.spawn(net.minecraft.world.entity.EntityTypes.PIG, 1, 2, 1);
            pig.setData(dev.awaken.living.LivingContent.FORM.get(), new dev.awaken.living.combat.Forms.Form(3, 0, 0));
            dev.awaken.living.combat.Moves.arm(pig);
            dev.awaken.living.combat.Moves.arm(pig);
            int goals = 0;
            for (var w : pig.goalSelector.getAvailableGoals()) if (w.getGoal().getClass().getSimpleName().equals("MarkGoal")) goals++;
            h.assertTrue(goals == 1, "one move goal, not two: " + goals);
            // a bash lands as a blow: it hurts what it hits and shoves it away
            var zombie = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, 3, 2, 1);
            var victim = h.spawn(net.minecraft.world.entity.EntityTypes.SHEEP, 4, 2, 1);
            var bash = dev.awaken.living.combat.Moves.of(dev.awaken.living.combat.Forms.Species.ZOMBIE, 2, false).get(0);
            float before = victim.getHealth();
            h.assertTrue(dev.awaken.living.combat.Moves.hit(level, zombie, victim, bash, 1, 0, 0.9, 0.35) && victim.getHealth() < before, "the bash hurts");
            h.assertTrue(victim.getDeltaMovement().x > 0.3, "and shoves it away");
            // a revenant stands up from a lethal blow once, at two fifths; a finishing blow (two health bars, 400 to 900) and a second blow do not
            var rev = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, 5, 2, 1);
            rev.setData(dev.awaken.living.LivingContent.FORM.get(), new dev.awaken.living.combat.Forms.Form(4, 0, 0));
            float lethal = rev.getHealth() + 1F;
            h.assertTrue(!dev.awaken.living.combat.MarkEvents.shouldRise(rev, rev.getHealth() - 1F), "a blow that does not kill is just a blow");
            h.assertTrue(dev.awaken.living.combat.MarkEvents.shouldRise(rev, lethal), "a lethal blow raises it");
            h.assertTrue(!dev.awaken.living.combat.MarkEvents.shouldRise(rev, 1_000_000F) && !dev.awaken.living.combat.MarkEvents.shouldRise(rev, dev.awaken.living.combat.MarkEvents.finishing(rev)), "a finishing blow does not");
            dev.awaken.living.combat.MarkEvents.rise(level, rev);
            h.assertTrue(rev.getHealth() > 0 && rev.getHealth() <= rev.getMaxHealth() * 0.45F, "it rises at two fifths");
            h.assertTrue(!dev.awaken.living.combat.MarkEvents.shouldRise(rev, rev.getHealth() + 1F), "and only once");
            var plain = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, 6, 2, 1);
            plain.setData(dev.awaken.living.LivingContent.FORM.get(), new dev.awaken.living.combat.Forms.Form(3, 0, 0));
            h.assertTrue(!dev.awaken.living.combat.MarkEvents.shouldRise(plain, plain.getHealth() + 1F), "only from the fourth rung");
            // the ordinary tests' blow: one thousand kills a zombie outright, whatever it wears
            rev.setData(dev.awaken.living.LivingContent.FORM.get(), new dev.awaken.living.combat.Forms.Form(5, 0, 0));
            h.assertTrue(!dev.awaken.living.combat.MarkEvents.shouldRise(rev, 1000F) && dev.awaken.living.combat.MarkEvents.finishing(rev) <= 900F, "a thousand finishes a revenant, however tough");
            // voices drop with the rung
            h.assertTrue(dev.awaken.living.combat.MarkEvents.pitch(0) == 1F && Math.abs(dev.awaken.living.combat.MarkEvents.pitch(5) - 0.6F) < 1e-6, "S speaks two fifths lower");
            h.succeed();
        });

        AwakenTests.register("forms_bodies", 20, h -> {
            // every species has a body at every rung from C to S: a clean rig within the bone budget, the clips the game asks for (its
            // moves at that rung, the crowned's move below, an alpha's roar, the creeper's cues), and its paint in all three families
            for (var sp : dev.awaken.living.combat.Forms.Species.values()) {
                for (int r = dev.awaken.living.forms.FormRigs.FIRST; r <= dev.awaken.living.forms.FormRigs.LAST; r++) {
                    var rig = dev.awaken.living.forms.FormRigs.get(sp, r);
                    h.assertTrue(rig != null, sp.id + " has a body at rung " + r);
                    h.assertTrue(rig.problems().isEmpty(), rig.id + ": " + rig.problems());
                    h.assertTrue(rig.bones.size() <= 60, rig.id + " keeps to 60 bones: " + rig.bones.size());
                    for (String need : java.util.List.of("idle", "walk", "run", "angry", "bite", "hurt", "death", "roar"))
                        h.assertTrue(rig.clips.containsKey(need), rig.id + " has " + need);
                    for (var d : dev.awaken.living.combat.Moves.of(sp, r, true)) {
                        String clip = d.kind().name().toLowerCase(java.util.Locale.ROOT);
                        h.assertTrue(rig.clips.containsKey(clip) || rig.clips.containsKey(clip + "_windup") && rig.clips.containsKey(clip + "_strike"),
                                rig.id + " acts out " + d.name());
                    }
                    if (sp.layout.equals("creeper"))
                        h.assertTrue(rig.clips.containsKey(new String[]{"sidestep", "seed", "ignite", "pull"}[r - 2]), rig.id + " acts out its cue");
                    String base = "/assets/awaken/textures/entity/forms/body/" + rig.id + "_" + sp.id;
                    for (int f = 0; f < 3; f++) {
                        h.assertTrue(LivingTests.class.getResource(base + "_" + f + ".png") != null, base + " family " + f + " is painted");
                        if (sp.layout.equals("sheep")) h.assertTrue(LivingTests.class.getResource(base + "_" + f + "_wool.png") != null, base + " has its wool");
                        if (sp.layout.equals("pig")) h.assertTrue(LivingTests.class.getResource(base + "_" + f + "_saddle.png") != null, base + " has its saddle");
                    }
                    h.assertTrue(LivingTests.class.getResource(base + "_glow.png") != null, base + " has its light");
                }
            }
            h.succeed();
        });

        AwakenTests.register("forms_size", 100, h -> {
            // animals and spiders truly grow; the walkers stay under two blocks; a creature that loses its form goes back to its size
            var pig = h.spawn(net.minecraft.world.entity.EntityTypes.PIG, 1, 2, 1);
            float w0 = pig.getBbWidth();
            pig.setData(dev.awaken.living.LivingContent.FORM.get(), new dev.awaken.living.combat.Forms.Form(5, 0, 0));
            dev.awaken.living.combat.FormShape.refresh(pig);
            h.assertTrue(Math.abs(pig.getBbWidth() - w0 * dev.awaken.living.combat.FormShape.widthFactor("pig", 5)) < 1e-3 && pig.getBbWidth() > w0 * 1.5F, "an S boar is wider: " + pig.getBbWidth());
            pig.setData(dev.awaken.living.LivingContent.FORM.get(), new dev.awaken.living.combat.Forms.Form(0, 0, 0));
            dev.awaken.living.combat.FormShape.refresh(pig);
            h.assertTrue(Math.abs(pig.getBbWidth() - w0) < 1e-3, "and back to a pig's size without its form");
            for (String walker : new String[]{"zombie", "skeleton"})
                for (int r = 2; r <= 5; r++)
                    h.assertTrue(dev.awaken.living.combat.FormShape.height(walker, r) < 2.0F, walker + " stays under two blocks at rung " + r);
            h.succeed();
        });

        AwakenTests.register("forms_cue", 20, h -> {
            // the cue a body acts a move out from round-trips (late joiners place the phase from its start tick)
            var cue = new LivingNet.FormCue(42, "lunge", 123_456L, 20, 18);
            var buf = io.netty.buffer.Unpooled.buffer();
            LivingNet.FormCue.STREAM_CODEC.encode(buf, cue);
            h.assertTrue(LivingNet.FormCue.STREAM_CODEC.decode(buf).equals(cue), "the cue round-trips");
            // the two-hit from B: the second blow waits out the hurt cooldown and is never more than a normal blow
            h.assertTrue(dev.awaken.living.combat.Combos.SECOND_AFTER >= 10, "the second blow comes after the hurt cooldown");
            for (int r = 3; r <= 5; r++) h.assertTrue(dev.awaken.living.combat.Combos.secondPower(r) <= 0.7F, "the second blow keeps to the cap at " + r);
            h.succeed();
        });

        AwakenTests.register("living_signs", 100, h -> {
            // SPEC 21.11 decision 24: none in E land, thicker with each rank; each kind builds something real that stands on its ground
            var rnd = net.minecraft.util.RandomSource.create(11);
            int[] total = new int[6];
            for (int rank = 0; rank <= 5; rank++) for (int i = 0; i < 10000; i++) total[rank] += dev.awaken.living.signs.Signs.attempts(rank, rnd);
            h.assertTrue(total[0] == 0, "E land has no signs");
            for (int rank = 1; rank <= 5; rank++) h.assertTrue(Math.abs(total[rank] / 10000.0 - dev.awaken.living.signs.Signs.PER_CHUNK[rank]) < 0.05, "rank " + rank + " has its density: " + total[rank] / 10000.0);
            for (int rank = 1; rank <= 5; rank++) h.assertTrue(!dev.awaken.living.signs.Signs.of(rank).isEmpty(), "rank " + rank + " has signs");
            var level = h.getLevel();
            int fam = 0;
            for (var kind : dev.awaken.living.signs.Signs.Kind.values()) {
                for (int family = 0; family < 3; family++) {
                    // a stone floor, clear air over it
                    BlockPos g = h.absolutePos(new BlockPos(5, 2, 5));
                    for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
                        level.setBlockAndUpdate(g.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                        for (int y = 1; y <= 14; y++) level.setBlockAndUpdate(g.offset(dx, y, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    }
                    int placed = dev.awaken.living.signs.Signs.build(level, net.minecraft.util.RandomSource.create(kind.ordinal() * 31L + family), g, kind, family);
                    h.assertTrue(placed >= 3, kind + " builds something: " + placed);
                    // everything above the floor is joined to it (a block touching another, the lowest touching the floor): nothing floats
                    java.util.Set<BlockPos> blocks = new java.util.HashSet<>();
                    for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) for (int y = 1; y <= 14; y++) {
                        BlockPos p = g.offset(dx, y, dz);
                        if (!level.getBlockState(p).isAir()) blocks.add(p);
                    }
                    java.util.Deque<BlockPos> todo = new java.util.ArrayDeque<>();
                    java.util.Set<BlockPos> seen = new java.util.HashSet<>();
                    for (BlockPos p : blocks) if (p.getY() == g.getY() + 1) { todo.add(p); seen.add(p); }
                    while (!todo.isEmpty()) {
                        BlockPos p = todo.poll();
                        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                            BlockPos q = p.offset(dx, dy, dz);
                            if (blocks.contains(q) && seen.add(q)) todo.add(q);
                        }
                    }
                    h.assertTrue(seen.size() == blocks.size(), kind + " (family " + family + ") is all one piece standing on the ground: " + seen.size() + " of " + blocks.size());
                    h.assertTrue(blocks.size() <= 90, kind + " stays small: " + blocks.size());
                }
            }
            h.succeed();
        });

        AwakenTests.register("living_quiet_land", 100, h -> {
            // SPEC 21.11 decision 23: animals thin by rank, the ambient life more so, the shyness grows with the rank
            var Q = dev.awaken.living.combat.QuietLand.class;
            h.assertTrue(dev.awaken.living.combat.QuietLand.keep(0) == 1F && dev.awaken.living.combat.QuietLand.keep(1) == 0.8F && dev.awaken.living.combat.QuietLand.keep(5) == 0.1F, "E all, D four fifths, S a tenth");
            for (int r = 1; r <= 5; r++) h.assertTrue(dev.awaken.living.combat.QuietLand.keep(r) < dev.awaken.living.combat.QuietLand.keep(r - 1), "thinner each rank");
            h.assertTrue(dev.awaken.living.combat.QuietLand.wild(-1) == 1F && dev.awaken.living.combat.QuietLand.wild(5) == 1F && dev.awaken.living.combat.QuietLand.wild(130) < 0.05F, "birdsong all through E and nearly gone in S");
            h.assertTrue(dev.awaken.living.combat.QuietLand.wild(80) < dev.awaken.living.combat.QuietLand.keep(4), "A: more birds go than animals");
            h.assertTrue(dev.awaken.living.combat.QuietLand.shy(1) == 10F && dev.awaken.living.combat.QuietLand.shy(5) == 26F, "10 blocks in D, 26 in S");
            // a real spawn: none turned away in E land (the test land), a pig in a hearthless D land world is thinned about as often as the table says
            var level = h.getLevel();
            var rnd = net.minecraft.util.RandomSource.create(5);
            var pig = h.spawn(net.minecraft.world.entity.EntityTypes.PIG, 1, 2, 1);
            int turned = 0;
            for (int i = 0; i < 400; i++) if (dev.awaken.living.combat.QuietLand.thins(level, pig, net.minecraft.world.entity.EntitySpawnReason.NATURAL, rnd)) turned++;
            int rank = dev.awaken.living.region.RegionMap.rankOf(dev.awaken.living.api.Regions.creatureLevel(level, pig.blockPosition(), dev.awaken.living.api.Regions.Role.NEUTRAL));
            double expect = rank <= 0 ? 0 : 1 - dev.awaken.living.combat.QuietLand.keep(rank);
            if (dev.awaken.living.api.Regions.landCap(level, pig.blockPosition()) != Integer.MAX_VALUE) expect = 0;
            h.assertTrue(Math.abs(turned / 400.0 - expect) < 0.12, "here (rank " + rank + ") " + turned + " of 400 turned away, expected about " + Math.round(expect * 400));
            h.assertTrue(!dev.awaken.living.combat.QuietLand.thins(level, pig, net.minecraft.world.entity.EntitySpawnReason.BREEDING, rnd), "bred animals are never turned away");
            h.succeed();
        });

        AwakenTests.register("living_wonder_sites", 200, h -> {
            // SPEC 21.11 decision 27: one wonder per region, a pure function of the seed, in land that fits its kind, inside its region, with a plan that stands
            var level = h.getLevel();
            RegionMap map = RegionService.map(level.getServer());
            long seed = level.getSeed();
            dev.awaken.living.wonder.Wonders.reset();
            var flat = new dev.awaken.living.wonder.Wonders.Terrain() {
                public int ground(int x, int z) { return 70; }
                public int seaLevel() { return 63; }
            };
            java.util.Set<Long> seen = new java.util.HashSet<>();
            java.util.Set<dev.awaken.living.wonder.Wonders.Kind> kinds = new java.util.HashSet<>();
            int made = 0;
            for (int gx = -7; gx <= 7; gx++) for (int gz = -7; gz <= 7; gz++) {
                RegionMap.Region r = map.regionAt(gx * 800, gz * 800);
                if (!seen.add(r.id())) continue;
                var s = dev.awaken.living.wonder.Wonders.siteOf(seed, map, r, flat);
                if (r.commons()) { h.assertTrue(s == null, "the Commons has no wonder"); continue; }
                if (s == null) continue;
                made++;
                kinds.add(s.kind());
                int rank = RegionMap.rankOf(map.levelAt(r.heartX(), r.heartZ()));
                h.assertTrue(rank >= s.kind().rankFrom && rank <= s.kind().rankTo, s.kind() + " fits rank " + rank);
                h.assertTrue(map.regionAt(s.x(), s.z()).id() == r.id(), "the site is inside its region");
                h.assertTrue(s.plan().blocks().get(net.minecraft.core.BlockPos.asLong(0, 0, 0)) != null, "the heart is marked");
                for (long k : s.plan().blocks().keySet()) h.assertTrue(Math.abs(net.minecraft.core.BlockPos.getX(k)) <= dev.awaken.living.wonder.Wonders.REACH && Math.abs(net.minecraft.core.BlockPos.getZ(k)) <= dev.awaken.living.wonder.Wonders.REACH, "inside its reach");
                int blocks = s.plan().blocks().size();
                dev.awaken.living.wonder.Wonders.reset();
                var again = dev.awaken.living.wonder.Wonders.siteOf(seed, map, r, flat);
                h.assertTrue(again != null && again.key().equals(s.key()) && again.plan().blocks().size() == blocks, "the same from the seed every time");
                h.assertTrue(dev.awaken.living.wonder.Wonders.near(seed, map, flat, s.x() - 3, s.z() - 3, s.x() + 3, s.z() + 3, dev.awaken.living.wonder.Wonders.REACH).stream().anyMatch(o -> o.key().equals(s.key())), "found from a chunk beside it");
            }
            h.assertTrue(made >= 25, "most regions have one: " + made);
            h.assertTrue(kinds.size() >= 4, "and they differ: " + kinds);
            // with the real generator's ground and the real structure sets: every kind can be found somewhere within 9,000 blocks of the centre
            dev.awaken.living.wonder.Wonders.reset();
            var real = dev.awaken.living.wonder.WonderFeature.terrain(level, level.getChunkSource().getGenerator());
            java.util.Map<dev.awaken.living.wonder.Wonders.Kind, Integer> census = new java.util.EnumMap<>(dev.awaken.living.wonder.Wonders.Kind.class);
            java.util.Set<Long> seen2 = new java.util.HashSet<>();
            int cx = map.regionAt(0, 0).heartX(), cz = map.regionAt(0, 0).heartZ();
            int considered = 0, wet = 0;
            for (int dx = -9000; dx <= 9000; dx += 500) for (int dz = -9000; dz <= 9000; dz += 500) {
                RegionMap.Region r = map.regionAt(dx, dz);
                if (!seen2.add(r.id()) || r.commons()) continue;
                considered++;
                var s2 = dev.awaken.living.wonder.Wonders.siteOf(seed, map, r, real);
                if (s2 == null) wet++;
                else census.merge(s2.kind(), 1, Integer::sum);
            }
            for (var kind : dev.awaken.living.wonder.Wonders.Kind.values()) h.assertTrue(census.getOrDefault(kind, 0) > 0, kind + " is found somewhere in 9,000 blocks (regions " + considered + ", without a site " + wet + ", by kind " + census + ")");
            // every kind's plan is one piece standing on the ground (everything joined to the blocks at ground level, in every family)
            for (var kind : dev.awaken.living.wonder.Wonders.Kind.values()) for (int fam = 0; fam < 3; fam++) {
                var plan = dev.awaken.living.wonder.Wonders.planOf(kind, 99L + fam, fam);
                var blocks = plan.blocks();
                h.assertTrue(blocks.size() >= 40 && blocks.size() <= 6000, kind + " has a sensible size: " + blocks.size());
                java.util.Deque<Long> todo = new java.util.ArrayDeque<>();
                java.util.Set<Long> reached = new java.util.HashSet<>();
                for (long k : blocks.keySet()) if (net.minecraft.core.BlockPos.getY(k) <= 1) { todo.add(k); reached.add(k); }
                while (!todo.isEmpty()) {
                    long k = todo.poll();
                    int x = net.minecraft.core.BlockPos.getX(k), y = net.minecraft.core.BlockPos.getY(k), z = net.minecraft.core.BlockPos.getZ(k);
                    for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                        long q = net.minecraft.core.BlockPos.asLong(x + dx, y + dy, z + dz);
                        if (blocks.containsKey(q) && reached.add(q)) todo.add(q);
                    }
                }
                h.assertTrue(reached.size() == blocks.size(), kind + " (family " + fam + ") stands as one piece: " + reached.size() + " of " + blocks.size() + " joined");
            }
            h.succeed();
        });

        AwakenTests.register("living_secret_sites", 200, h -> {
            // SPEC 21.11 decision 28: three secrets a region at most, inside it, small and joined, each with a cache that has a loot table at every rank
            var level = h.getLevel();
            RegionMap map = RegionService.map(level.getServer());
            long seed = level.getSeed();
            dev.awaken.living.wonder.Secrets.reset();
            var flat = new dev.awaken.living.wonder.Wonders.Terrain() {
                public int ground(int x, int z) { return 70; }
                public int seaLevel() { return 63; }
            };
            java.util.Set<Long> seen = new java.util.HashSet<>();
            int made = 0;
            for (int gx = -6; gx <= 6; gx++) for (int gz = -6; gz <= 6; gz++) {
                RegionMap.Region r = map.regionAt(gx * 800, gz * 800);
                if (!seen.add(r.id())) continue;
                var list = dev.awaken.living.wonder.Secrets.of(seed, map, r, flat);
                if (r.commons()) { h.assertTrue(list.isEmpty(), "none in the Commons"); continue; }
                h.assertTrue(list.size() <= 3, "at most three a region: " + list.size());
                java.util.Set<String> kinds = new java.util.HashSet<>();
                for (var s : list) {
                    made++;
                    h.assertTrue(kinds.add(s.kind().id), "no kind twice in a region");
                    h.assertTrue(map.regionAt(s.x(), s.z()).id() == r.id(), "inside its region");
                    h.assertTrue(!s.plan().caches().isEmpty(), "holds a cache");
                    for (var c : s.plan().caches()) {
                        var st = s.plan().blocks().get(net.minecraft.core.BlockPos.asLong(c.dx(), c.dy(), c.dz()));
                        h.assertTrue(st != null && (c.brushable() ? st.is(net.minecraft.world.level.block.Blocks.SUSPICIOUS_GRAVEL) : st.is(net.minecraft.world.level.block.Blocks.BARREL) || st.is(net.minecraft.world.level.block.Blocks.CHEST)), "the cache is where the plan says");
                    }
                }
            }
            h.assertTrue(made >= 50, "regions have their secrets: " + made);
            for (int rank = 0; rank <= 5; rank++) for (boolean brush : new boolean[]{false, true}) {
                var table = level.getServer().reloadableRegistries().getLootTable(dev.awaken.living.wonder.Secrets.table(rank, brush));
                h.assertTrue(table != net.minecraft.world.level.storage.loot.LootTable.EMPTY, "a loot table for rank " + rank + (brush ? " (brushed)" : ""));
            }
            for (var kind : dev.awaken.living.wonder.Secrets.Kind.values()) {
                var plan = dev.awaken.living.wonder.Secrets.planOf(kind, 5L);
                h.assertTrue(plan.blocks().size() >= 5 && plan.blocks().size() <= 40, kind + " is small: " + plan.blocks().size());
            }
            h.succeed();
        });

        AwakenTests.register("living_plate_heat", 20, h -> {
            // the chip's colour by how a creature measures against you: grey far below, blue about your level, orange a good fight, red an insane one
            var P = dev.awaken.living.combat.PlateRules.Heat.class;
            h.assertTrue(dev.awaken.living.combat.PlateRules.heat(5, 20) == dev.awaken.living.combat.PlateRules.Heat.TRIVIAL, "fifteen below is trivial");
            h.assertTrue(dev.awaken.living.combat.PlateRules.heat(10, 20) == dev.awaken.living.combat.PlateRules.Heat.TRIVIAL, "ten below is trivial");
            h.assertTrue(dev.awaken.living.combat.PlateRules.heat(11, 20) == dev.awaken.living.combat.PlateRules.Heat.EASY && dev.awaken.living.combat.PlateRules.heat(24, 20) == dev.awaken.living.combat.PlateRules.Heat.EASY, "below you and up to four above: blue");
            h.assertTrue(dev.awaken.living.combat.PlateRules.heat(25, 20) == dev.awaken.living.combat.PlateRules.Heat.FIGHT && dev.awaken.living.combat.PlateRules.heat(34, 20) == dev.awaken.living.combat.PlateRules.Heat.FIGHT, "five to fourteen above: orange");
            h.assertTrue(dev.awaken.living.combat.PlateRules.heat(35, 20) == dev.awaken.living.combat.PlateRules.Heat.HARD && dev.awaken.living.combat.PlateRules.heat(72, 1) == dev.awaken.living.combat.PlateRules.Heat.HARD, "fifteen or more above: red");
            h.succeed();
        });

        AwakenTests.register("living_game_drops", 20, h -> {
            // one more of the food per rank above E, never more than five (a pig in B land: three more chops)
            h.assertTrue(dev.awaken.living.combat.GameDrops.extra(0) == 0 && dev.awaken.living.combat.GameDrops.extra(1) == 1 && dev.awaken.living.combat.GameDrops.extra(3) == 3, "one more per rank");
            h.assertTrue(dev.awaken.living.combat.GameDrops.extra(9) == dev.awaken.living.combat.GameDrops.MAX_EXTRA, "never more than five");
            h.assertTrue(dev.awaken.living.combat.GameDrops.hideTier(1).equals("d") && dev.awaken.living.combat.GameDrops.hideTier(5).equals("s") && dev.awaken.living.combat.GameDrops.hideTier(0).equals("e"), "an alpha's hide is its land's tier");
            for (int r = 1; r <= 5; r++) {
                var tag = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, dev.awaken.Awaken.id("hunter_hide_" + dev.awaken.living.combat.GameDrops.hideTier(r)));
                h.assertTrue(net.minecraft.core.registries.BuiltInRegistries.ITEM.getTagOrEmpty(tag).iterator().hasNext(), "the tier's hide tag has items: " + tag.location());
            }
            h.succeed();
        });

        AwakenTests.register("living_dread_gap", 20, h -> {
            // the land's weight by the gap (SPEC 21.10): nothing under 5, a little under half at 10, everything at 20, never backwards
            h.assertTrue(dev.awaken.living.combat.DreadRules.strength(-10) == 0 && dev.awaken.living.combat.DreadRules.strength(4) == 0, "nothing under five levels above");
            h.assertTrue(Math.abs(dev.awaken.living.combat.DreadRules.strength(5) - 0.15F) < 1e-6, "faint at five");
            h.assertTrue(Math.abs(dev.awaken.living.combat.DreadRules.strength(10) - 0.45F) < 1e-6, "a little under half at ten");
            h.assertTrue(dev.awaken.living.combat.DreadRules.strength(20) == 1F && dev.awaken.living.combat.DreadRules.strength(90) == 1F, "everything at twenty");
            float last = 0;
            for (int g = 0; g <= 40; g++) {
                float s2 = dev.awaken.living.combat.DreadRules.strength(g);
                h.assertTrue(s2 >= last && s2 <= 1F, "it only grows with the gap: " + g);
                last = s2;
            }
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
        e.setData(LivingContent.FORM.get(), dev.awaken.living.combat.Forms.Form.NONE); // the test land is far out: no revenant should stand up
        e.hurtServer(h.getLevel(), p.damageSources().playerAttack(p), 10000);
        if (e.isAlive()) e.discard();
    }
}
