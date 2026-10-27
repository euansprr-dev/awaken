package dev.awaken.arms;

import dev.awaken.Awaken;
import dev.awaken.core.api.ArmsApi;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** GameTests for the weapon progression (docs/arms-progression/PLAN.md): one per mechanism, `arms_` prefix. */
final class ArmsProgressionTests {
    private ArmsProgressionTests() {}

    static void register() {
        AwakenTests.register("arms_issue", 40, ArmsProgressionTests::issue);
        AwakenTests.register("arms_shard_pity", 40, ArmsProgressionTests::shardPity);
        AwakenTests.register("arms_rack", 40, ArmsProgressionTests::rack);
        AwakenTests.register("arms_armoury", 40, ArmsProgressionTests::armoury);
        AwakenTests.register("arms_gate_pattern", 60, ArmsProgressionTests::gatePattern);
        AwakenTests.register("arms_homes", 40, ArmsProgressionTests::homes);
        AwakenTests.register("arms_boss_weapon", 60, ArmsProgressionTests::bossWeapon);
        AwakenTests.register("arms_marked_pity", 40, ArmsProgressionTests::markedPity);
        AwakenTests.register("arms_raise", 40, ArmsProgressionTests::raise);
        AwakenTests.register("arms_tier_cards", 60, ArmsProgressionTests::tierCards);
        AwakenTests.register("arms_stand", 60, ArmsProgressionTests::stand);
        AwakenTests.register("arms_village_patterns", 40, ArmsProgressionTests::villagePatterns);
    }

    /** A mock player in survival (the helper's are creative, where nothing is paid). */
    private static ServerPlayer survivor(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        sp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Progression.awaken(sp);
        return sp;
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Awaken.id(id));
    }

    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.is(item)) n += s.getCount();
        return n;
    }

    /** Hale's issue: waiting for a new hunter, one pick gives the finished weapon, its Blueprint and a Fang, once. */
    static void issue(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Progression.awaken(sp);
        h.assertTrue(ArmsApi.issuePending(sp), "a new awakened hunter with no styled weapon is waiting for the issue");
        ArmsApi.openIssue(sp);
        ArmsIssue.onPick(sp, "katana");
        h.assertTrue(count(sp, item("katana_blueprint")) == 0 && ArmsApi.issuePending(sp), "a style that wasn't offered gives nothing");
        ArmsIssue.onPick(sp, "maul");
        h.assertTrue(count(sp, item("wildhide_maul")) == 1, "the finished Wildhide Maul");
        h.assertTrue(count(sp, item("maul_blueprint")) == 1, "and its Blueprint");
        h.assertTrue(count(sp, item("wildhide_blade")) == 1, "and a spare Fang");
        h.assertTrue(ArmsApi.knows(sp, "maul") && !ArmsApi.knows(sp, "axe"), "the Maul is known, the Axe isn't");
        h.assertTrue(!ArmsApi.issuePending(sp), "the issue is taken");
        ArmsApi.openIssue(sp);
        ArmsIssue.onPick(sp, "daggers");
        h.assertTrue(count(sp, item("wildhide_daggers")) == 0 && count(sp, item("wildhide_maul")) == 1, "once");

        ServerPlayer armed = h.makeMockServerPlayerInLevel();
        Progression.awaken(armed);
        armed.getInventory().add(new ItemStack(item("wildhide_longsword")));
        h.assertTrue(!ArmsApi.issuePending(armed), "a hunter who already carries a styled weapon needs no issue");
        h.succeed();
    }

    /** The no-drought rule: the fifth kill in a row of an awakened creature that left no shard drops one. */
    static void shardPity(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = h.makeMockServerPlayerInLevel();
        Progression.awaken(sp);
        var mob = h.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        mob.setData(CoreApi.MOB_ROLE, new MobRole(MobRole.Role.NORMAL, 3, "awaken:greenwild", Optional.empty(), ""));
        var source = h.getLevel().damageSources().playerAttack(sp);
        int dropped = -1;
        for (int kill = 1; kill <= 5; kill++) {
            List<ItemEntity> drops = new ArrayList<>();
            ArmsIssue.onDrops(new LivingDropsEvent(mob, source, drops, true));
            boolean shard = drops.stream().anyMatch(d -> d.getItem().is(CoreApi.ESSENCE_SHARD.get()));
            if (shard) dropped = kill;
            h.assertTrue(shard == (kill == 5), "the shard comes on the fifth kill with none, not before: kill " + kill + " shard " + shard);
        }
        h.assertTrue(dropped == 5 && ArmsProgress.of(sp).shardMisses == 0, "and the count starts again");
        List<ItemEntity> good = new ArrayList<>(List.of(new ItemEntity(h.getLevel(), 0, 0, 0, new ItemStack(CoreApi.ESSENCE_SHARD.get()))));
        ArmsProgress.of(sp).shardMisses = 3;
        ArmsIssue.onDrops(new LivingDropsEvent(mob, source, good, true));
        h.assertTrue(ArmsProgress.of(sp).shardMisses == 0 && good.size() == 1, "a shard that dropped by itself resets the count");
        h.succeed();
    }

    /** Hale's rack: what a rank opens, the price in essence, a refusal below the rank or without the essence. */
    static void rack(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = survivor(h);
        sp.getInventory().add(new ItemStack(CoreApi.ESSENCE_SHARD.get(), 10));
        var view = ArmsArmoury.view(sp, ArmsArmoury.HALE, false);
        long onOffer = view.styles().stream().filter(e -> e.state() == 1).count();
        h.assertTrue(onOffer == 5 && view.hidden() == 10 && view.hiddenRank().equals("D"), "an E-rank hunter sees the five E patterns and ten more waiting from D: " + onOffer + " / " + view.hidden() + " / " + view.hiddenRank());
        h.assertTrue(view.styles().stream().allMatch(e -> e.state() == 0 || e.price() == 4), "an E pattern is 4 essence");
        h.assertTrue(!ArmsArmoury.buy(sp, "spear") && count(sp, CoreApi.ESSENCE_SHARD.get()) == 10, "a D pattern is refused at E and costs nothing");
        h.assertTrue(ArmsArmoury.buy(sp, "scythe"), "an E pattern is bought");
        h.assertTrue(count(sp, CoreApi.ESSENCE_SHARD.get()) == 6 && count(sp, item("scythe_blueprint")) == 1 && ArmsApi.knows(sp, "scythe"), "4 essence paid, its Blueprint in the pack, the style known");
        h.assertTrue(!ArmsArmoury.buy(sp, "scythe"), "never twice");
        ServerPlayer poor = survivor(h);
        h.assertTrue(!ArmsArmoury.buy(poor, "daggers") && !ArmsApi.knows(poor, "daggers"), "without the essence nothing is bought");
        Progression.passReassessment(sp);
        var d = ArmsArmoury.view(sp, ArmsArmoury.HALE, false);
        h.assertTrue(d.styles().stream().filter(e -> e.state() == 1).count() == 8 && d.hiddenRank().equals("C"), "D rank opens its four: eight on offer, the rest from C");
        h.assertTrue(d.styles().stream().filter(e -> e.id().equals("spear")).findFirst().map(e -> e.price() == 6).orElse(false), "a D pattern is 6 essence");
        h.succeed();
    }

    /** The Armoury's doors, what a weapon needs, drawing a copy at a Forge, and Track. */
    static void armoury(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = survivor(h);
        ArmsIssue.INSTANCE.learn(sp, "daggers", "a test");
        var needs = ArmsArmoury.needs(sp, "daggers", 0);
        h.assertTrue(needs.size() == 4 && needs.stream().allMatch(n -> n.have() == 0 || n.id().endsWith("daggers_blueprint")), "the Wildhide Daggers need a Fang, a Part, a Binding and the Blueprint: " + needs);
        sp.getInventory().add(new ItemStack(item("wildhide_blade")));
        sp.getInventory().add(new ItemStack(Items.BONE));
        sp.getInventory().add(new ItemStack(Items.STRING));
        h.assertTrue(ArmsArmoury.needs(sp, "daggers", 0).stream().allMatch(n -> n.have() >= n.need()), "with the parts in the pack nothing is missing");
        // a door has to be opened to use it
        sp.getInventory().add(new ItemStack(Items.PAPER, 4));
        sp.getInventory().add(new ItemStack(Items.LAPIS_LAZULI));
        sp.getInventory().add(new ItemStack(CoreApi.ESSENCE_SHARD.get()));
        int before = count(sp, item("daggers_blueprint"));
        ArmsArmoury.onAct(sp, new dev.awaken.arms.net.ArmsPayloads.ArmouryAct(ArmsArmoury.DRAW, "daggers", ""));
        h.assertTrue(count(sp, item("daggers_blueprint")) == before, "no copy is drawn without the Forge's door");
        ArmsArmoury.open(sp, ArmsArmoury.FORGE);
        ArmsArmoury.onAct(sp, new dev.awaken.arms.net.ArmsPayloads.ArmouryAct(ArmsArmoury.DRAW, "daggers", ""));
        h.assertTrue(count(sp, item("daggers_blueprint")) == before + 1, "at the Forge a known pattern is drawn again");
        ArmsArmoury.onAct(sp, new dev.awaken.arms.net.ArmsPayloads.ArmouryAct(ArmsArmoury.BUY, "axe", ""));
        h.assertTrue(!ArmsApi.knows(sp, "axe"), "and nothing is bought at the Forge");
        // Track: the weapon, its rows, and it lets go when it is made
        ArmsArmoury.onAct(sp, new dev.awaken.arms.net.ArmsPayloads.ArmouryAct(ArmsArmoury.TRACK, "daggers", "E"));
        h.assertTrue(ArmsProgress.of(sp).tracked.equals("daggers:E"), "tracked");
        var task = ArmsTrack.task(sp);
        h.assertTrue(task != null && task.rows().size() == 4 && task.title().contains("Wildhide Daggers"), "the task card lists the four things the weapon needs");
        sp.getInventory().add(new ItemStack(item("wildhide_daggers")));
        h.assertTrue(ArmsTrack.task(sp) == null && ArmsProgress.of(sp).tracked.isEmpty(), "and lets go once the weapon is in the pack");
        ArmsArmoury.onAct(sp, new dev.awaken.arms.net.ArmsPayloads.ArmouryAct(ArmsArmoury.TRACK, "katana", "E"));
        h.assertTrue(ArmsProgress.of(sp).tracked.isEmpty(), "a style you don't know can't be tracked");
        h.succeed();
    }

    private static net.minecraft.world.entity.LivingEntity bearer(net.minecraft.gametest.framework.GameTestHelper h, String gate, boolean elite, BlockPos at) {
        var mob = h.spawn(EntityTypes.ZOMBIE, at);
        mob.setData(CoreApi.MOB_ROLE, new MobRole(elite ? MobRole.Role.ELITE : MobRole.Role.NORMAL, 3, "awaken:hive", Optional.empty(), gate));
        return mob;
    }

    /** A gate's bearer: chosen on the first floor, wielding the style, and its death teaches each fighter near it once; the Lord's hoard is the fallback. */
    static void gatePattern(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = survivor(h);
        var first = bearer(h, "gate-1", true, new BlockPos(1, 1, 1));
        ArmsApi.gateSpawn(first, "gate-1", 0, 1, true);
        h.assertTrue(first.entityTags().contains(ArmsPatterns.BEARER) && first.getMainHandItem().is(item("wildhide_daggers")), "the first elite of the first floor is the bearer and wields the Hive's daggers");
        var second = bearer(h, "gate-1", true, new BlockPos(2, 1, 1));
        ArmsApi.gateSpawn(second, "gate-1", 0, 1, true);
        h.assertTrue(!second.entityTags().contains(ArmsPatterns.BEARER), "only one bearer to a gate");
        var deeper = bearer(h, "gate-9", true, new BlockPos(3, 1, 1));
        ArmsApi.gateSpawn(deeper, "gate-9", 0, 2, true);
        h.assertTrue(!deeper.entityTags().contains(ArmsPatterns.BEARER), "nothing on the second floor");
        var crowd = new net.minecraft.world.entity.LivingEntity[3];
        for (int i = 0; i < 3; i++) {
            crowd[i] = bearer(h, "gate-2", false, new BlockPos(1 + i, 1, 3));
            ArmsApi.gateSpawn(crowd[i], "gate-2", 0, 1, false);
        }
        h.assertTrue(!crowd[0].entityTags().contains(ArmsPatterns.BEARER) && !crowd[1].entityTags().contains(ArmsPatterns.BEARER) && crowd[2].entityTags().contains(ArmsPatterns.BEARER),
                "with no elite, the third creature is the bearer");

        ArmsPatterns.bearerFell(first, List.of(sp));
        h.assertTrue(ArmsApi.knows(sp, "daggers") && count(sp, item("daggers_blueprint")) == 1, "its fall teaches the fighter the Hive's Daggers, Blueprint in the pack");
        ArmsPatterns.lordPattern(sp, "gate-1", "awaken:hive");
        h.assertTrue(count(sp, item("daggers_blueprint")) == 1 && ArmsProgress.of(sp).styles.size() == 1, "once for the gate: the Lord adds nothing");

        ServerPlayer skipped = survivor(h);
        ArmsPatterns.lordPattern(skipped, "gate-1", "awaken:hive");
        h.assertTrue(ArmsApi.knows(skipped, "daggers"), "a fighter who never downed the bearer gets it from the Lord's hoard");

        // a style you know gives another of its rank, and a whole rank known gives essence
        ArmsPatterns.bearerFell(crowd[2], List.of(sp));
        int known = (int) java.util.stream.Stream.of("daggers", "axe", "maul", "scythe", "warstaff").filter(st -> ArmsApi.knows(sp, st)).count();
        h.assertTrue(known == 2, "a style you know hands over another E pattern instead: " + known);
        for (String st : List.of("axe", "maul", "scythe", "warstaff")) ArmsIssue.INSTANCE.learn(sp, st, "a test");
        int essence = count(sp, CoreApi.ESSENCE_SHARD.get());
        ArmsPatterns.teach(sp, ArmsHomes.of("daggers"), "a test");
        h.assertTrue(count(sp, CoreApi.ESSENCE_SHARD.get()) == essence + 4, "all five E patterns known: 4 essence instead");
        h.assertTrue(ArmsPatterns.patternLine(skipped, "awaken:hive") == null && ArmsPatterns.patternLine(h.makeMockServerPlayerInLevel(), "awaken:ember_mines") != null,
                "the gate card names a pattern only for a style you don't know");
        h.succeed();
    }

    /** The people who teach early: a chain's end pays its style once, or its price if it is already yours. */
    static void homes(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = survivor(h);
        ArmsApi.install(ArmsIssue.INSTANCE);
        ArmsPatterns.questDone(sp, "ten_sheep_short");
        h.assertTrue(ArmsApi.knows(sp, "spear") && count(sp, item("spear_blueprint")) == 1, "Hettie teaches the Spear at the end of Ten Sheep Short");
        ArmsPatterns.questDone(sp, "ten_sheep_short");
        h.assertTrue(count(sp, item("spear_blueprint")) == 1, "once");
        ServerPlayer knows = survivor(h);
        ArmsIssue.INSTANCE.learn(knows, "greatblade", "a test");
        ArmsPatterns.questDone(knows, "orens_first_steel");
        h.assertTrue(count(knows, CoreApi.ESSENCE_SHARD.get()) == ArmsHomes.rackPrice(ArmsHomes.of("greatblade").rank()), "a style you know pays its price instead");
        h.assertTrue(ArmsHomes.all().stream().filter(x -> x.teacher() != null).count() == 6, "six people teach");
        h.succeed();
    }

    /** Boss weapons: the cooler the weapon the rarer, a pity at six, a Lord-forged weapon replaces the Spoils, two distinct Marks. */
    static void bossWeapon(net.minecraft.gametest.framework.GameTestHelper h) {
        h.assertTrue(ArmsHomes.spoilChance(dev.awaken.progression.Rank.E) > ArmsHomes.spoilChance(dev.awaken.progression.Rank.D)
                && ArmsHomes.spoilChance(dev.awaken.progression.Rank.D) > ArmsHomes.spoilChance(dev.awaken.progression.Rank.C)
                && ArmsHomes.spoilChance(dev.awaken.progression.Rank.C) > ArmsHomes.spoilChance(dev.awaken.progression.Rank.B)
                && ArmsHomes.spoilChance(dev.awaken.progression.Rank.B) > ArmsHomes.spoilChance(dev.awaken.progression.Rank.A), "Spoils fall rarer from E to A");
        h.assertTrue(ArmsSpoils.falls(0, 0.15F, 0.14F) && !ArmsSpoils.falls(0, 0.15F, 0.16F), "a roll under the chance falls");
        h.assertTrue(!ArmsSpoils.falls(4, 0.15F, 0.99F) && ArmsSpoils.falls(5, 0.15F, 0.99F), "and the sixth Lord without a weapon is certain");
        var rng = net.minecraft.util.RandomSource.create(7);
        ItemStack spear = ArmsSpoils.stack(dev.awaken.progression.Rank.D, ArmsHomes.of("spear"), rng);
        h.assertTrue(spear.is(item("beastbound_spear")) && Marks.of(spear).marks().size() == 2 && !Marks.of(spear).marks().get(0).equals(Marks.of(spear).marks().get(1)),
                "a D Lord's Spoils: a Beastbound Spear with two different Marks");
        ItemStack any = ArmsSpoils.stack(dev.awaken.progression.Rank.S, null, rng);
        h.assertTrue(!any.isEmpty() && BuiltInRegistries.ITEM.getKey(any.getItem()).getPath().startsWith("sovereign_"), "an S Lord with no style of its own: a Sovereign weapon of some style");

        ServerPlayer lucky = survivor(h);
        ArmsProgress.of(lucky).lordsWithoutWeapon = 5;
        ArmsSpoils.lordKill(lucky, "E", "awaken:hive");
        ServerPlayer forged = survivor(h);
        ArmsProgress.of(forged).lordsWithoutWeapon = 5;
        ArmsSpoils.noteForged(forged);
        ArmsSpoils.lordKill(forged, "E", "awaken:hive");
        h.runAfterDelay(8, () -> {
            h.assertTrue(count(lucky, item("wildhide_daggers")) == 1 && ArmsProgress.of(lucky).lordsWithoutWeapon == 0, "the pity hands over the Hive's Daggers and starts again");
            h.assertTrue(count(forged, item("wildhide_daggers")) == 0 && ArmsProgress.of(forged).lordsWithoutWeapon == 0, "a Lord-forged weapon replaces the Spoils");
            h.succeed();
        });
    }

    /** Marked weapons: a pity on the 25th, and the first one certain from a Warden. */
    static void markedPity(net.minecraft.gametest.framework.GameTestHelper h) {
        h.assertTrue(!MarkedDrops.falls(23, 0.04F, 0.99F) && MarkedDrops.falls(24, 0.04F, 0.99F), "certain on the 25th without one");
        ServerPlayer sp = survivor(h);
        var warden = h.spawn(EntityTypes.ZOMBIE, new BlockPos(1, 1, 1));
        warden.setData(CoreApi.MOB_ROLE, new MobRole(MobRole.Role.WARDEN, 3, "awaken:hive", Optional.empty(), ""));
        var source = h.getLevel().damageSources().playerAttack(sp);
        h.assertTrue(!ArmsProgress.of(sp).firstMarked, "none yet");
        MarkedDrops.onDeath(new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(warden, source));
        h.assertTrue(ArmsProgress.of(sp).firstMarked && ArmsProgress.of(sp).markedWithout == 0, "the first Warden you down drops one for certain");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(warden.blockPosition()).inflate(6));
        h.assertTrue(drops.stream().anyMatch(d -> !Marks.of(d.getItem()).marks().isEmpty()), "and it carries its Marks");
        h.succeed();
    }

    /** Raise: a Marked forged weapon becomes the next tier's, keeping its Marks; a Lord-forged one keeps its body and gains damage. */
    static void raise(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = survivor(h);
        for (int i = 0; i < 10 && Progression.profile(sp).level() < 13; i++) Progression.grantLevels(sp, 6, Progression.XpSource.COMMAND);
        h.assertTrue(Progression.profile(sp).level() >= 13, "level 13 reached for the test: " + Progression.profile(sp).level());
        ItemStack marked = new ItemStack(item("wildhide_daggers"));
        marked.set(ArmsContent.INSTANCE.get(), Marks.Instance.EMPTY.withMark(Marks.random(net.minecraft.util.RandomSource.create(3)).id()));
        sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, marked);
        h.assertTrue(!ArmsRaise.raise(sp), "without the next tier's parts nothing is raised");
        giveNeeds(sp, ArmsRaise.plan(sp).needs());
        h.assertTrue(ArmsRaise.raise(sp), "with them it is");
        ItemStack up = sp.getMainHandItem();
        h.assertTrue(up.is(item("beastbound_daggers")) && Marks.of(up).marks().equals(Marks.of(marked).marks()), "a Beastbound Daggers with the same Marks");
        h.assertTrue(ArmsArmoury.needs(sp, "daggers", 1).stream().filter(n -> !n.id().endsWith("_blueprint")).allMatch(n -> n.have() == 0), "and the parts are spent");

        ItemStack lord = new ItemStack(item("hive_regent_daggers"));
        sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, lord);
        giveNeeds(sp, ArmsRaise.plan(sp).needs());
        h.assertTrue(ArmsRaise.raise(sp), "a Lord-forged weapon is raised too");
        ItemStack lf = sp.getMainHandItem();
        h.assertTrue(lf.is(item("hive_regent_daggers")) && ArmsRaise.raised(lf) == 1, "it keeps its own body, marked as raised once");
        h.assertTrue(lf.getOrDefault(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY)
                .modifiers().stream().anyMatch(m -> m.modifier().id().getPath().equals("raise_1")), "and a point of damage");
        h.succeed();
    }

    private static void giveNeeds(ServerPlayer sp, List<dev.awaken.arms.net.ArmsPayloads.NeedRow> needs) {
        for (var n : needs) {
            net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.parse(n.id());
            Item give = n.kind().equals("tag")
                    ? BuiltInRegistries.ITEM.get(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, id)).orElseThrow().stream().findFirst().orElseThrow().value()
                    : BuiltInRegistries.ITEM.getValue(id);
            sp.getInventory().add(new ItemStack(give, n.need()));
        }
    }

    private static void levelTo(ServerPlayer sp, int level) {
        for (int i = 0; i < 20 && Progression.profile(sp).level() < level; i++) Progression.grantLevels(sp, 6, Progression.XpSource.COMMAND);
    }

    /** A tier is announced: its part job is on Hale's board first, the lock shows what it waits for, a part you hold files it. */
    static void tierCards(net.minecraft.gametest.framework.GameTestHelper h) {
        String[] jobs = {"part_job_beastbound", "part_job_gatewrought", "part_job_veilwoven", "part_job_lordscale", "part_job_sovereign"};
        for (int i = 0; i < jobs.length; i++) {
            var d = dev.awaken.living.bounty.Bounties.all().get("awaken:" + jobs[i]);
            h.assertTrue(d != null && d.featured() && d.keep(), jobs[i] + " is a featured notice that Hale only looks at");
            Item part = BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(d.objective().target()));
            String tier = ArmsArmoury.TIERS[i + 1].toLowerCase();
            h.assertTrue(part != Items.AIR && new ItemStack(part).is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, Awaken.id("hunter_core_" + tier))),
                    jobs[i] + " asks for a part of its own tier: " + d.objective().target());
            h.assertTrue(d.requires().contains("level:" + ArmsArmoury.LEVEL[i + 1]), jobs[i] + " opens at its tier's level");
            h.assertTrue(ArmsLang.get("awaken.armoury.pull." + ArmsArmoury.TIERS[i + 1]) != null && ArmsLang.get("awaken.armoury.open." + ArmsArmoury.TIERS[i + 1]) != null,
                    "the tier has its two lines");
        }
        // the lock: the tier after the one you have is seen, the ones beyond only once you hold one of their parts
        ServerPlayer sp = survivor(h);
        levelTo(sp, 3);
        int best = ArmsTiers.best(sp);
        h.assertTrue(best == 0, "Wildhide is open at level 3: " + best);
        h.assertTrue(!ArmsTiers.pull(sp, 1, best).isEmpty() && ArmsTiers.pull(sp, 1, best).contains("level 13"), "Beastbound's lock says what it waits for");
        h.assertTrue(ArmsTiers.pull(sp, 2, best).isEmpty(), "Gatewrought stays a silhouette");
        ArmsTiers.seen(sp, new ItemStack(item("mainspring")));
        h.assertTrue(ArmsTiers.pull(sp, 2, best).contains("level 22"), "until you hold its part");
        h.assertTrue(ArmsTiers.pull(sp, 0, best).isEmpty() && ArmsTiers.pull(sp, 1, 1).isEmpty(), "an open tier needs no lock line");
        ArmsTiers.onLevel(sp, 13);

        // the job: first on the board at the tier's level, kept after it is shown, and not put up again
        levelTo(sp, 13);
        dev.awaken.progression.Progression.awaken(sp);
        h.assertTrue(dev.awaken.living.bounty.Bounties.board(sp), "Hale has a board");
        String id = "awaken:part_job_beastbound";
        h.assertTrue(dev.awaken.living.bounty.Bounties.state(sp).offered().getFirst().equals(id), "the tier's job is the first notice: " + dev.awaken.living.bounty.Bounties.state(sp).offered());
        dev.awaken.living.bounty.Bounties.answer(sp, dev.awaken.living.bounty.Bounties.ACTION + ":" + id, true);
        sp.getInventory().add(new ItemStack(item("briar_tusk")));
        dev.awaken.living.bounty.Bounties.board(sp);
        h.assertTrue(dev.awaken.living.bounty.Bounties.state(sp).taken().isEmpty() && dev.awaken.living.bounty.Bounties.state(sp).done() == 1, "shown, so it is done");
        h.assertTrue(count(sp, item("briar_tusk")) == 1, "and the tusk goes back to the hunter");
        h.assertTrue(Progression.profile(sp).counter("awaken:featured_done/" + id) == 1, "a featured notice is done for good");
        h.succeed();
    }

    /** A Pattern Stand: its pattern is drawn from its rank's band by the seed, the same every time; taken once per fighter; a style you know pays essence. */
    static void stand(net.minecraft.gametest.framework.GameTestHelper h) {
        var level = h.getLevel();
        BlockPos at = h.absolutePos(new BlockPos(2, 1, 2));
        h.assertTrue(ArmsApi.placeStand(level, at, net.minecraft.core.Direction.NORTH, "lectern", "C", 5L), "a stand is placed");
        var be = (PatternStandBlockEntity) level.getBlockEntity(at);
        var home = ArmsHomes.of(be.style());
        h.assertTrue(home != null && home.rank() == dev.awaken.progression.Rank.C && be.tier() == 2, "a C-rank stand holds a C-rank pattern at the C tier: " + be.style() + " " + be.tier());
        String first = be.style();
        ArmsApi.placeStand(level, at, net.minecraft.core.Direction.NORTH, "lectern", "C", 5L);
        h.assertTrue(((PatternStandBlockEntity) level.getBlockEntity(at)).style().equals(first), "the same seed shows the same pattern");
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (long seed = 1; seed <= 24; seed++) {
            ArmsApi.placeStand(level, at, net.minecraft.core.Direction.NORTH, "rack", "E", seed);
            seen.add(((PatternStandBlockEntity) level.getBlockEntity(at)).style());
        }
        h.assertTrue(seen.size() >= 3, "different seeds draw different E patterns: " + seen);
        ArmsApi.placeStand(level, at, net.minecraft.core.Direction.NORTH, "lectern", "C", 5L);
        be = (PatternStandBlockEntity) level.getBlockEntity(at);

        ServerPlayer a = survivor(h), b = survivor(h);
        h.assertTrue(!ArmsApi.knows(a, first), "unknown to start with");
        ArmsStands.take(a, level, at, be);
        h.assertTrue(ArmsApi.knows(a, first) && count(a, ArmsItems.blueprintItem(first)) == 1, "the first take teaches the pattern and hands over its Blueprint");
        ArmsStands.take(a, level, at, be);
        h.assertTrue(count(a, ArmsItems.blueprintItem(first)) == 1 && ArmsStands.taken(a, level, at), "and only once per fighter");
        ArmsIssue.remember(b, first);
        ArmsStands.take(b, level, at, be);
        h.assertTrue(count(b, CoreApi.ESSENCE_SHARD.get()) == ArmsHomes.rackPrice(dev.awaken.progression.Rank.C), "a fighter who knows it is paid its price in essence");

        // no rank given: the pattern is drawn from the land's rank when someone first comes near
        BlockPos lazy = h.absolutePos(new BlockPos(4, 1, 2));
        h.assertTrue(ArmsApi.placeStand(level, lazy, net.minecraft.core.Direction.EAST, "plinth", "", 99L), "a stand with no rank is placed");
        var lazyBe = (PatternStandBlockEntity) level.getBlockEntity(lazy);
        h.assertTrue(lazyBe.pending() && lazyBe.style().isEmpty(), "and waits");
        ArmsStands.resolve(level, lazy, lazyBe);
        h.assertTrue(!lazyBe.pending() && ArmsHomes.of(lazyBe.style()) != null, "until someone comes near: " + lazyBe.style());
        h.succeed();
    }

    /** The smith's rack: two patterns of the village's rank, the same every time, never one you know; paying teaches it. */
    static void villagePatterns(net.minecraft.gametest.framework.GameTestHelper h) {
        ServerPlayer sp = survivor(h);
        var near = ArmsStands.villagePatterns(sp, 12345L, 5);
        h.assertTrue(near.size() == 2 && near.stream().allMatch(o -> ArmsHomes.of(o.style()).rank() == dev.awaken.progression.Rank.E && o.emeralds() == 6),
                "a level 5 village sells two E patterns at 6 emeralds: " + near);
        h.assertTrue(near.stream().map(ArmsApi.VillageOffer::style).toList().equals(ArmsStands.villagePatterns(sp, 12345L, 5).stream().map(ArmsApi.VillageOffer::style).toList()), "and the same two every time");
        var far = ArmsStands.villagePatterns(sp, 12345L, 30);
        h.assertTrue(far.size() == 2 && far.stream().allMatch(o -> ArmsHomes.of(o.style()).rank() == dev.awaken.progression.Rank.C && o.emeralds() == 16),
                "a level 30 village sells two C patterns at 16 emeralds: " + far);
        var top = ArmsStands.villagePatterns(sp, 12345L, 150);
        h.assertTrue(top.size() == 2 && top.stream().allMatch(o -> ArmsHomes.of(o.style()) != null), "a land above every band still has two patterns to sell: " + top);
        String first = near.getFirst().style();
        ArmsIssue.remember(sp, first);
        h.assertTrue(ArmsStands.villagePatterns(sp, 12345L, 5).stream().noneMatch(o -> o.style().equals(first)), "a pattern you know is not on the rack");
        String bought = far.getFirst().style();
        ArmsApi.villageBought(sp, bought);
        h.assertTrue(ArmsApi.knows(sp, bought), "paying teaches it");
        h.succeed();
    }
}
