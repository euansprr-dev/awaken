package dev.awaken.armour;

import dev.awaken.Awaken;
import dev.awaken.armour.net.ArmourPayloads;
import dev.awaken.core.Forces;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.gear.GearItems;
import dev.awaken.gear.HunterTier;
import dev.awaken.gear.Piece;
import dev.awaken.hearth.Upgrades;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Armour GameTests (SPEC §44): the roster's shape, the Hunter items' looks, every made set's art shipped; the Forge Song's
 * rules, its saves, a piece made by hand start to finish and one at the Forge with a helper; the made sets' numbers,
 * patterns, and what wearing a set does (abilities, mastery).
 */
final class ArmourTests {
    private ArmourTests() {}

    static void register() {
        AwakenTests.register("armour_roster_shape", 20, ArmourTests::roster);
        AwakenTests.register("armour_hunter_looks", 20, ArmourTests::looks);
        AwakenTests.register("armour_art_shipped", 20, ArmourTests::art);
        AwakenTests.register("armour_forge_rules", 20, ArmourTests::forgeRules);
        AwakenTests.register("armour_forge_saves", 20, ArmourTests::forgeSaves);
        AwakenTests.register("armour_forge_by_hand", 40, ArmourTests::byHand);
        AwakenTests.register("armour_forge_at_the_forge", 40, ArmourTests::atTheForge);
        AwakenTests.register("armour_numbers", 20, ArmourTests::numbers);
        AwakenTests.register("armour_patterns", 20, ArmourTests::patterns);
        AwakenTests.register("armour_set_abilities", 40, ArmourTests::abilities);
        AwakenTests.register("armour_set_twists_cb", 40, ArmourTests::twistsCB);
        AwakenTests.register("armour_forge_tune_inlay", 40, ArmourTests::tuneInlay);
        AwakenTests.register("armour_looks_accents", 20, ArmourTests::looksAccents);
    }

    private static final List<Rank> RANKS = List.of(Rank.E, Rank.D, Rank.C, Rank.B, Rank.A, Rank.S);

    /** Four sets at E (one per archetype), five at each rank D-S (one per archetype and a Lord-forged one); ids unique. */
    private static void roster(GameTestHelper h) {
        h.assertTrue(ArmourSet.values().length == 29, "29 sets, got " + ArmourSet.values().length);
        Set<String> ids = new HashSet<>();
        for (ArmourSet s : ArmourSet.values()) {
            h.assertTrue(ids.add(s.id), "set id unique: " + s.id);
            h.assertTrue(ArmourSet.byId(s.id) == s, "byId finds " + s.id);
            h.assertTrue(RANKS.contains(s.rank), s.id + " sits on a rank from E to S");
        }
        for (Rank r : RANKS) {
            EnumSet<Archetype> seen = EnumSet.noneOf(Archetype.class);
            int n = 0;
            for (ArmourSet s : ArmourSet.values()) {
                if (s.rank != r) continue;
                n++;
                h.assertTrue(seen.add(s.archetype), r.label + " has one " + s.archetype.label + " set");
            }
            EnumSet<Archetype> want = r == Rank.E ? EnumSet.complementOf(EnumSet.of(Archetype.LORD)) : EnumSet.allOf(Archetype.class);
            h.assertTrue(seen.equals(want) && n == want.size(), r.label + ": the archetypes " + want + ", got " + seen);
        }
        for (HunterTier tier : HunterTier.values()) {
            ArmourSet s = ArmourSet.of(tier);
            h.assertTrue(s.rank == tier.rank && s.id.equals(tier.id) && s.setName.equals(tier.setName),
                    tier + " is the set " + s.id + " at its own rank and name");
        }
        h.succeed();
    }

    /** Every Hunter armour piece is drawn as its tier's set and its own piece; blades, foci and vanilla armour are not. */
    private static void looks(GameTestHelper h) {
        for (HunterTier tier : HunterTier.values()) {
            for (Piece piece : Piece.values()) {
                ArmourLooks.Look look = ArmourLooks.of(GearItems.stack(tier, piece));
                if (piece.armorType == null) {
                    h.assertTrue(look == null, tier.itemId(piece) + " is not armour");
                    continue;
                }
                h.assertTrue(look != null && look.set() == ArmourSet.of(tier) && look.piece() == piece.armorType,
                        tier.itemId(piece) + " is drawn as " + ArmourSet.of(tier).id + " " + piece.armorType.getName());
            }
        }
        h.assertTrue(ArmourLooks.of(new ItemStack(Items.IRON_CHESTPLATE)) == null, "vanilla armour stays vanilla's");
        h.assertTrue(ArmourLooks.of(ItemStack.EMPTY) == null, "nothing worn, nothing drawn");
        h.succeed();
    }

    /**
     * Each made set ships a texture and a mesh for every piece on its main bone, so none falls back to the shell; a
     * Channeler its rune; a set with items of its own an icon for each.
     */
    private static void art(GameTestHelper h) {
        String[][] main = {{"helmet", "head"}, {"chestplate", "body"}, {"leggings", "right_leg"}, {"boots", "right_leg"}};
        for (ArmourSet s : ArmourSet.values()) {
            if (!s.made()) continue;
            h.assertTrue(exists("/assets/awaken/textures/entity/armour/" + s.id + ".png"), s.id + " has its texture");
            if (s.archetype == Archetype.CHANNELER) h.assertTrue(exists("/assets/awaken/armour/" + s.id + "/rune.mesh"), s.id + " has its rune");
            if (s.hunter == null) {
                for (ArmorType type : ForgeSong.PIECES)
                    h.assertTrue(exists("/assets/awaken/textures/item/armour/" + s.id + "_" + ArmourItems.suffix(type) + ".png"), s.id + " " + type + " has its icon");
                if (s.rank != Rank.E) h.assertTrue(exists("/assets/awaken/textures/item/armour/" + s.id + "_pattern.png"), s.id + "'s pattern has its icon");
            }
            for (String[] pb : main) {
                String file = "/assets/awaken/armour/" + s.id + "/" + pb[0] + "_" + pb[1] + ".mesh";
                h.assertTrue(exists(file), s.id + " ships " + file);
            }
            for (String[] pb : new String[][]{{"chestplate", "right_arm"}, {"chestplate", "left_arm"}, {"boots", "left_leg"}}) {
                h.assertTrue(exists("/assets/awaken/armour/" + s.id + "/" + pb[0] + "_" + pb[1] + ".mesh"), s.id + " dresses " + pb[1]);
            }
        }
        h.succeed();
    }

    private static boolean exists(String path) {
        try (InputStream in = ArmourTests.class.getResourceAsStream(path)) {
            return in != null && in.read() >= 0;
        } catch (IOException e) {
            return false;
        }
    }

    // ---- the Forge Song ------------------------------------------------------------------------------------------------

    /** The rhythm counts in one bar; the judging, quality and costs hold their numbers; every set has a song. */
    private static void forgeRules(GameTestHelper h) {
        ForgeSong.Rhythm r = new ForgeSong.Rhythm(4, "x.x.xx..");
        h.assertTrue(r.beats() == 4 && r.due(0) == 32F && r.due(1) == 40F && r.due(3) == 52F && r.due(4) == 64F,
                "one bar to count in, then strikes on the x's");
        h.assertTrue(!r.offBeat(4F) && r.offBeat(36F) && !r.offBeat(32F), "the rests after the count-in are a helper's");
        for (ArmourSet set : ArmourSet.values()) {
            ForgeSong.Song song = ForgeSong.song(set);
            h.assertTrue(song != null && song.melody().length == 8 && song.rhythm().beats() >= 3
                    && song.rhythm().bar().chars().allMatch(c -> c == 'x' || c == '.') && song.rhythm().bar().charAt(0) == 'x', set.id + " has its song");
            for (int note : song.melody()) {
                float pitch = ForgeSong.notePitch(note);
                h.assertTrue(pitch >= 0.5F && pitch < 2F, set.id + "'s note " + note + " can be played");
            }
            for (ArmorType piece : ForgeSong.PIECES) {
                List<ForgeSong.Need> cost = ForgeSong.cost(set, piece);
                if (!set.made()) {
                    h.assertTrue(cost == null, set.id + " isn't forged yet");
                    continue;
                }
                h.assertTrue(cost != null && cost.stream().anyMatch(n -> Awaken.id("essence_shard").equals(n.item())), set.id + " " + piece + " takes essence");
                for (ForgeSong.Need n : cost) {
                    if (n.tag() != null) h.assertTrue(BuiltInRegistries.ITEM.get(n.tag()).map(t -> t.size() > 0).orElse(false), n.tag().location() + " has items");
                    else h.assertTrue(BuiltInRegistries.ITEM.getValue(n.item()) != Items.AIR, n.item() + " exists");
                }
                h.assertTrue(!ArmourForge.make(set, piece).isEmpty(), set.id + " " + piece + " is an item");
                for (float[] m : ForgeSong.marks(piece, ForgeSong.strikes(piece, ForgeSong.byHand(set))))
                    h.assertTrue(Math.abs(m[0]) <= 1F && Math.abs(m[1]) <= 1F && m[0] == m[0] && m[1] == m[1], "marks lie on the work");
            }
        }
        h.assertTrue(ForgeSong.timing(0F) == 1F && ForgeSong.timing(6F) == 0F && ForgeSong.aim(0.4F) == 1F && ForgeSong.aim(2F) == 0F, "timing and aim");
        h.assertTrue(ForgeSong.clean(ForgeSong.strike(0F, 0F)) && !ForgeSong.clean(ForgeSong.strike(5F, 0F)), "on the beat and on the mark is clean");
        h.assertTrue(ForgeSong.quality(0.95F, false) == ForgeSong.Quality.FLAWLESS && ForgeSong.quality(0.95F, true) == ForgeSong.Quality.MASTERWORK
                && ForgeSong.quality(0.76F, false) == ForgeSong.Quality.MASTERWORK && ForgeSong.quality(0.6F, false) == ForgeSong.Quality.FINE
                && ForgeSong.quality(0.3F, false) == ForgeSong.Quality.PLAIN, "the quality bands, a flaw caps at Masterwork");
        h.assertTrue(ForgeSong.heatScore(0.65F) == 1F && ForgeSong.flawed(0.9F) && !ForgeSong.flawed(0.7F), "the gold band, and too hot keeps a flaw");
        h.assertTrue(ForgeSong.forgeScore(0.65F, 1F, 1F) > 0.99F && ForgeSong.forgeScore(0.3F, 1F, 1F) < 0.85F, "pulled early, the strikes count less");
        for (long seed = 0; seed < 24; seed++) h.assertTrue(ForgeSong.quenchCue(seed) >= 24 && ForgeSong.quenchCue(seed) < 36, "the cue comes in 24-35 ticks");
        h.succeed();
    }

    /** A forged piece's story, a piece in the making and the Armoury's memory all come back as they were saved. */
    private static void forgeSaves(GameTestHelper h) {
        var ops = h.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Forged f = new Forged(Forged.SCHEMA, "gatewrought", 2, 0, true, false, "Smith");
        h.assertTrue(Forged.CODEC.encodeStart(ops, f).flatMap(t -> Forged.CODEC.parse(ops, t)).getOrThrow().equals(f), "Forged round-trips");
        Forged two = f.withInlay("FROST", ArmourInlay.colour(Forces.Force.FROST)).withLook("bellguard").withTrueRing(true);
        Forged back = Forged.CODEC.encodeStart(ops, two).flatMap(t -> Forged.CODEC.parse(ops, t)).getOrThrow();
        h.assertTrue(back.equals(two) && back.inlayForce() == Forces.Force.FROST && back.trueRing(), "schema 2's inlay, look, accent and ring too");
        var one = new net.minecraft.nbt.CompoundTag();
        one.putInt("schemaVersion", 1);
        one.putString("set", "gatewrought");
        one.putInt("quality", 2);
        Forged old = Forged.CODEC.parse(ops, one).getOrThrow();
        h.assertTrue(old.inlay().isEmpty() && old.look().isEmpty() && old.accent() == 0 && !old.trueRing() && old.inlayForce() == null,
                "a schema 1 story reads with nothing inlaid, its own look");
        UUID who = UUID.randomUUID();
        ForgeWork w = new ForgeWork(ArmourSet.BEASTBOUND, ArmorType.LEGGINGS, ForgeSong.SHAPE, 1234L, 2, List.of(new ItemStack(Items.IRON_INGOT, 3)),
                0.66F, -1F, 0.4F, List.of(1F, 0.5F, 0.9F), 1, who, false, new BlockPos(1, 2, 3), Optional.of(new BlockPos(4, 5, 6)));
        w.tune = 0.75F;
        w.inlayScore = 1F;
        w.taps = 3;
        w.inlay = Forces.Force.SPIRIT.ordinal() + 1;
        ForgeWork b = ForgeWork.CODEC.encodeStart(ops, w).flatMap(t -> ForgeWork.CODEC.parse(ops, t)).getOrThrow();
        h.assertTrue(b.tune == 0.75F && b.inlayScore == 1F && b.taps == 3 && b.inlay == w.inlay, "the tune and the inlay round-trip");
        h.assertTrue(b.set == w.set && b.piece == w.piece && b.stage == w.stage && b.stageStart == 1234L && b.strikeBase == 2 && b.heat == 0.66F
                && b.cut == -1F && b.quench == 0.4F && b.scores.equals(w.scores) && b.twin == 1 && b.forger.equals(who) && !b.memory
                && b.work.equals(w.work) && b.forge.equals(w.forge), "a piece in the making round-trips");
        h.assertTrue(b.materials.size() == 1 && b.materials.get(0).is(Items.IRON_INGOT) && b.materials.get(0).getCount() == 3, "its materials too");
        ArmouryMemory m = ArmouryMemory.fresh();
        m.patterns.add("halo_choir");
        m.forged.add("wildhide");
        m.masterwork.add(ArmouryMemory.key(ArmourSet.WILDHIDE, ArmorType.BOOTS));
        var codec = ArmouryMemory.CODEC.codec();
        ArmouryMemory mb = codec.encodeStart(ops, m).flatMap(t -> codec.parse(ops, t)).getOrThrow();
        h.assertTrue(mb.patterns.equals(m.patterns) && mb.forged.equals(m.forged) && mb.fromMemory(ArmourSet.WILDHIDE, ArmorType.BOOTS)
                && mb.forgedWhole(ArmourSet.WILDHIDE), "the Armoury's memory round-trips");
        h.succeed();
    }

    private static ServerPlayer smith(GameTestHelper h, Rank rank, BlockPos at) {
        ServerPlayer p = survivor(h);
        Progression.awaken(p);
        while (Progression.rank(p).ordinal() < rank.ordinal()) Progression.passReassessment(p);
        p.getInventory().clearContent(); // awakening and rank gifts leave things in the pack; the tests count what they give
        p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 1.5, 0F, 0F);
        return p;
    }

    /** A survival player (a mock player is always creative, and creative makes anything for nothing), gone at the end. */
    private static ServerPlayer survivor(GameTestHelper h) {
        AwakenTests.Live live = AwakenTests.survivalPlayer(h, "armour-test", BlockPos.ZERO);
        AwakenTests.onEnd(h, live::leave);
        return live.player();
    }

    private static FittingStandBlockEntity stand(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, ArmourContent.STAND.get().defaultBlockState());
        return (FittingStandBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(rel));
    }

    private static void act(ServerPlayer p, BlockPos stand, int act, int a, float t, float aim) {
        ArmourForge.onAct(p, new ArmourPayloads.Act(stand, act, a, t, aim));
    }

    /** Strikes the next mark dead on its beat (the stage's clock set so the server sees it on time). */
    private static void strikeOnBeat(GameTestHelper h, ServerPlayer p, FittingStandBlockEntity be) {
        ForgeWork w = be.work();
        float due = w.nextDue();
        w.stageStart = h.getLevel().getGameTime() - (long) due;
        act(p, be.getBlockPos(), ArmourPayloads.STRIKE, w.scores.size(), due, 0F);
    }

    /**
     * Wildhide by hand (E): the stand offers the E sets and says it needs a works table; with one near and the hide in
     * the pack the Now line names the vest; the cut and six rivets on the beat make it Flawless (Temper 3) on the stand,
     * remembered by heart; from memory it comes Fine and the first goes back to the pack; a smith who steps away holds the
     * work; breaking the stand gives the materials back.
     */
    private static void byHand(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        FittingStandBlockEntity be = stand(h, new BlockPos(1, 1, 1));
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = smith(h, Rank.E, pos);
        var offers = ArmourForge.offers(p, be);
        h.assertTrue(offers.offers().size() == 16 && offers.offers().stream().allMatch(o -> ArmourSet.values()[o.piece() / 4].rank == Rank.E),
                "E offers the four E sets' pieces, got " + offers.offers().size());
        h.assertTrue(offers.offers().stream().noneMatch(o -> (o.flags() & ArmourPayloads.CAN) != 0) && !offers.hand().isEmpty(),
                "nothing to make yet, and it says a works table is wanted");
        h.setBlock(new BlockPos(3, 1, 1), Blocks.CRAFTING_TABLE.defaultBlockState());
        p.getInventory().add(new ItemStack(Items.LEATHER, 5));
        p.getInventory().add(new ItemStack(Items.STRING, 2));
        p.getInventory().add(new ItemStack(CoreApi.ESSENCE_SHARD.get(), 1));
        ItemStack hint = ArmourNow.next(p);
        h.assertTrue(hint != null && hint.is(GearItems.get(HunterTier.E, Piece.CHESTPLATE)), "the Now line names the vest, got " + hint);
        h.assertTrue(ArmourForge.offers(p, be).offers().stream().anyMatch(o -> o.piece() == ArmourSet.WILDHIDE.ordinal() * 4 + 1
                && (o.flags() & ArmourPayloads.CAN) != 0), "now the vest can be made");

        int vest = ArmourSet.WILDHIDE.ordinal() * 4 + 1;
        act(p, pos, ArmourPayloads.START, vest, 0F, 0F);
        ForgeWork w = be.work();
        h.assertTrue(w != null && w.stage == ForgeSong.CUT && w.work.equals(h.absolutePos(new BlockPos(3, 1, 1))) && w.forge.isEmpty(),
                "by hand: the cut, at the works table");
        h.assertTrue(ArmourForge.count(p.getInventory(), s -> s.is(Items.LEATHER) || s.is(Items.STRING)) == 0
                && w.materials.stream().mapToInt(ItemStack::getCount).sum() == 8, "the hide, sinew and shard wait on the work");
        act(p, pos, ArmourPayloads.CUT, 0, 0F, 1F);
        h.assertTrue(w.stage == ForgeSong.RIVET, "then the rivets");
        for (int k = 0; k < ForgeSong.strikes(ArmorType.CHESTPLATE, true); k++) strikeOnBeat(h, p, be);
        h.assertTrue(be.work() == null, "six rivets finish it");
        ItemStack made = be.suit(1);
        Forged f = made.get(ArmourContent.FORGED.get());
        h.assertTrue(made.is(GearItems.get(HunterTier.E, Piece.CHESTPLATE)) && f != null && f.quality() == ForgeSong.Quality.FLAWLESS.ordinal()
                && !f.flaw() && Upgrades.temperOf(made) == 3, "a Flawless vest on the stand, Temper 3, got " + f + " temper " + Upgrades.temperOf(made));
        h.assertTrue(ArmouryMemory.of(p).fromMemory(ArmourSet.WILDHIDE, ArmorType.CHESTPLATE), "known by heart now");

        // from memory: one rivet, always Fine; the Flawless one comes back to the pack
        p.getInventory().add(new ItemStack(Items.LEATHER, 5));
        p.getInventory().add(new ItemStack(Items.STRING, 2));
        p.getInventory().add(new ItemStack(CoreApi.ESSENCE_SHARD.get(), 1));
        act(p, pos, ArmourPayloads.MEMORY, vest, 0F, 0F);
        act(p, pos, ArmourPayloads.CUT, 0, 0F, 0F);
        h.assertTrue(be.work() != null && be.work().strikesNeeded() == 1, "from memory: one strike");
        strikeOnBeat(h, p, be);
        Forged again = be.suit(1).get(ArmourContent.FORGED.get());
        h.assertTrue(be.work() == null && again != null && again.quality() == ForgeSong.FROM_MEMORY.ordinal(), "from memory it comes Fine");
        h.assertTrue(ArmourForge.count(p.getInventory(), s -> s.is(GearItems.get(HunterTier.E, Piece.CHESTPLATE))) == 1, "the first went back to the pack");

        // stepping away holds the work; breaking the stand returns what was taken in, and the suit
        p.getInventory().add(new ItemStack(Items.LEATHER, 4));
        p.getInventory().add(new ItemStack(CoreApi.ESSENCE_SHARD.get(), 1));
        act(p, pos, ArmourPayloads.START, ArmourSet.WILDHIDE.ordinal() * 4, 0F, 0F);
        h.assertTrue(be.work() != null && ArmourForge.at(p.getUUID(), pos), "the helm begun, its smith at it");
        act(p, pos, ArmourPayloads.LEAVE, 0, 0F, 0F);
        h.assertTrue(!ArmourForge.at(p.getUUID(), pos) && be.work() != null, "stepped away: the work waits");
        level.removeBlock(pos, false);
        int leather = 0, vests = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3))) {
            if (e.getItem().is(Items.LEATHER)) leather += e.getItem().getCount();
            if (e.getItem().is(GearItems.get(HunterTier.E, Piece.CHESTPLATE))) vests++;
        }
        h.assertTrue(leather == 4 && vests == 1, "breaking the stand drops the helm's hide and the fitted vest, got " + leather + " hide, " + vests);
        h.succeed();
    }

    /**
     * Beastbound at the Forge (D): the bar pulled in the gold, twelve strikes on the beat with a helper's three off-beats
     * (twin-struck), quenched early (it hissed): still Flawless, fitted on the stand. A helper can't strike on the beat.
     */
    private static void atTheForge(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        FittingStandBlockEntity be = stand(h, new BlockPos(1, 1, 1));
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = smith(h, Rank.D, pos);
        p.setGameMode(GameType.CREATIVE); // the materials are the hand's test; here the song
        ServerPlayer helper = smith(h, Rank.E, pos.east());
        h.setBlock(new BlockPos(3, 1, 1), Blocks.ANVIL.defaultBlockState());
        h.setBlock(new BlockPos(1, 1, 3), BuiltInRegistries.BLOCK.getValue(Awaken.id("forge")).defaultBlockState());
        h.assertTrue(ArmourForge.offers(p, be).place().isEmpty(), "an anvil and a Forge near: nothing more wanted");
        act(p, pos, ArmourPayloads.START, ArmourSet.BEASTBOUND.ordinal() * 4 + 1, 0F, 0F);
        ForgeWork w = be.work();
        h.assertTrue(w != null && w.stage == ForgeSong.HEAT && w.forge.isPresent() && w.work.equals(h.absolutePos(new BlockPos(3, 1, 1))),
                "at the Forge: heat first, worked on the anvil");
        float gold = 0.65F * ForgeSong.HEAT_FULL;
        w.stageStart = level.getGameTime() - (long) gold;
        act(p, pos, ArmourPayloads.PULL, 0, gold, 0F);
        h.assertTrue(w.stage == ForgeSong.SHAPE && Math.abs(w.heat - 0.65F) < 0.01F, "pulled in the gold, onto the anvil");

        ForgeSong.Rhythm r = ForgeSong.song(ArmourSet.BEASTBOUND).rhythm();
        int len = r.bar().length(), twins = 0;
        for (int i = len; twins < 3; i++) {
            if (r.bar().charAt(i % len) != '.') continue;
            float t = i * r.step();
            w.stageStart = level.getGameTime() - (long) t;
            act(helper, pos, ArmourPayloads.TWIN, 0, t, 0F);
            twins++;
        }
        h.assertTrue(w.twin == 3, "three off-beats struck by the helper, got " + w.twin);
        int before = w.scores.size();
        w.stageStart = level.getGameTime() - (long) w.nextDue();
        act(helper, pos, ArmourPayloads.STRIKE, w.scores.size(), w.nextDue(), 0F);
        h.assertTrue(w.scores.size() == before, "a helper's blow on the beat isn't taken");

        for (int k = 0; k < ForgeSong.strikes(ArmorType.CHESTPLATE, false); k++) strikeOnBeat(h, p, be);
        h.assertTrue(w.stage == ForgeSong.QUENCH, "twelve strikes, then the quench");
        act(p, pos, ArmourPayloads.QUENCH, 0, 0F, 0F);
        Forged f = be.suit(1).get(ArmourContent.FORGED.get());
        h.assertTrue(be.work() == null && f != null && be.suit(1).is(GearItems.get(HunterTier.D, Piece.CHESTPLATE)), "fitted on the stand");
        h.assertTrue(w.quench < 0.5F && f.quality() == ForgeSong.Quality.FLAWLESS.ordinal() && f.twin(),
                "quenched too soon, yet Flawless and twin-struck, got " + f + " quench " + w.quench);
        h.succeed();
    }

    // ---- the rank's verbs (P3) ---------------------------------------------------------------------------------------

    /**
     * B's Tune and C's Inlay: a Veilwoven cuirass started with Frostspar to inlay is heated, shaped and quenched, then
     * tuned: a tap far off rings dull and the tuning goes on, a tap at the point rings true; the Frostspar is seated on the
     * key. The piece comes Masterwork or better, rung true, inlaid with Frost and glowing its colour, and worn it gives
     * Frost's toughness.
     */
    private static void tuneInlay(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        FittingStandBlockEntity be = stand(h, new BlockPos(1, 1, 1));
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = smith(h, Rank.B, pos);
        p.setGameMode(GameType.CREATIVE); // the materials are the hand's test; here the verbs
        h.setBlock(new BlockPos(3, 1, 1), Blocks.ANVIL.defaultBlockState());
        h.setBlock(new BlockPos(1, 1, 3), BuiltInRegistries.BLOCK.getValue(Awaken.id("forge")).defaultBlockState());
        h.assertTrue(ForgeSong.tunes(ArmourSet.VEILWOVEN) && ForgeSong.inlays(ArmourSet.VEILWOVEN) && ForgeSong.inlays(ArmourSet.GATEWROUGHT)
                && !ForgeSong.tunes(ArmourSet.GATEWROUGHT) && !ForgeSong.inlays(ArmourSet.BEASTBOUND), "C inlays, B also tunes");
        act(p, pos, ArmourPayloads.START, ArmourSet.VEILWOVEN.ordinal() * 4 + 1, 0F, Forces.Force.FROST.ordinal() + 1);
        ForgeWork w = be.work();
        h.assertTrue(w != null && w.inlay == Forces.Force.FROST.ordinal() + 1, "the Frostspar chosen goes in with the work");
        float gold = 0.65F * ForgeSong.HEAT_FULL;
        w.stageStart = level.getGameTime() - (long) gold;
        act(p, pos, ArmourPayloads.PULL, 0, gold, 0F);
        for (int k = 0; k < ForgeSong.strikes(ArmorType.CHESTPLATE, false); k++) strikeOnBeat(h, p, be);
        h.assertTrue(w.stage == ForgeSong.QUENCH, "shaped, then the quench");
        w.stageStart = level.getGameTime() - 34;
        act(p, pos, ArmourPayloads.QUENCH, 0, ForgeSong.quenchCue(w.stageStart) + 1, 0F);
        h.assertTrue(w.stage == ForgeSong.TUNE && be.work() == w, "B's Deepglass is tuned after the quench");

        float[] at = ForgeSong.truePoint(w.stageStart, w.piece);
        float farX = at[0] > 0 ? -1F : 1F, farZ = at[1] > 0 ? -1F : 1F;
        act(p, pos, ArmourPayloads.TAP, 0, farX, farZ);
        h.assertTrue(w.stage == ForgeSong.TUNE && w.taps == 1 && w.tune >= 0F && w.tune < 0.5F, "a far tap rings dull, got " + w.tune);
        act(p, pos, ArmourPayloads.TAP, 0, at[0], at[1]);
        h.assertTrue(w.taps == 1, "a tap out of turn isn't taken");
        act(p, pos, ArmourPayloads.TAP, 1, at[0] + 0.05F, at[1]);
        h.assertTrue(w.stage == ForgeSong.INLAY && w.tune == 1F, "at the point it rings true, then the inlay");

        w.stageStart = level.getGameTime() - 20;
        h.assertTrue(Math.abs(ForgeSong.inlayAngle(20F)) < 0.01F && ForgeSong.inlayScore(40F) < 1F, "the notch meets the key every turn");
        act(p, pos, ArmourPayloads.SEAT, 0, 20F, 0F);
        ItemStack made = be.suit(1);
        Forged f = made.get(ArmourContent.FORGED.get());
        h.assertTrue(be.work() == null && f != null && f.inlayForce() == Forces.Force.FROST && f.trueRing()
                && f.accent() == ArmourInlay.colour(Forces.Force.FROST), "fitted rung true, inlaid with Frost and its colour, got " + f);
        h.assertTrue(f.quality() >= ForgeSong.Quality.MASTERWORK.ordinal(), "every verb done well: Masterwork or better, got " + f.quality());

        p.setItemSlot(ArmorType.CHESTPLATE.getSlot(), made.copy());
        ArmourAbilities.count(p, ArmourAbilities.state(p));
        var tough = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS).getModifier(Awaken.id("armour_inlay_frost"));
        h.assertTrue(tough != null && Math.abs(tough.amount() - ArmourInlay.FROST_TOUGHNESS) < 1e-6, "worn, the Frostspar gives its toughness");
        h.succeed();
    }

    /**
     * Looks and accents: a Bellguard cuirass can't take Amberwing's look until Amberwing has been forged whole; then it draws
     * as Amberwing and stays Bellguard; a dye you carry colours its glow and is used up; none gives the inlay's colour back.
     */
    private static void looksAccents(GameTestHelper h) {
        FittingStandBlockEntity be = stand(h, new BlockPos(1, 1, 1));
        BlockPos pos = be.getBlockPos();
        ServerPlayer p = smith(h, Rank.E, pos);
        ItemStack chest = ArmourItems.stack(ArmourSet.BELLGUARD, ArmorType.CHESTPLATE);
        chest.set(ArmourContent.FORGED.get(), new Forged(Forged.SCHEMA, "bellguard", 0, 0, false, false, "")
                .withInlay("HEAT", ArmourInlay.colour(Forces.Force.HEAT)));
        var slot = ArmorType.CHESTPLATE.getSlot();
        p.setItemSlot(slot, chest);
        act(p, pos, ArmourPayloads.LOOK, 1, 0F, ArmourSet.AMBERWING.ordinal() + 1);
        h.assertTrue(ArmourLooks.drawn(p.getItemBySlot(slot)).set() == ArmourSet.BELLGUARD, "Amberwing not forged whole: the look isn't taken");
        ArmouryMemory.of(p).forged.add(ArmourSet.AMBERWING.id);
        act(p, pos, ArmourPayloads.LOOK, 1, 0F, ArmourSet.AMBERWING.ordinal() + 1);
        ItemStack worn = p.getItemBySlot(slot);
        h.assertTrue(ArmourLooks.drawn(worn).set() == ArmourSet.AMBERWING && ArmourLooks.of(worn).set() == ArmourSet.BELLGUARD,
                "drawn as Amberwing, still a Bellguard cuirass");
        act(p, pos, ArmourPayloads.LOOK, 1, 0F, 0F);
        h.assertTrue(ArmourLooks.drawn(p.getItemBySlot(slot)).set() == ArmourSet.BELLGUARD, "and back to its own");

        p.getInventory().add(new ItemStack(Items.DYE.pick(net.minecraft.world.item.DyeColor.RED), 1));
        act(p, pos, ArmourPayloads.ACCENT, 0, 0F, net.minecraft.world.item.DyeColor.RED.getId() + 1);
        Forged f = p.getItemBySlot(slot).get(ArmourContent.FORGED.get());
        h.assertTrue(f != null && f.accent() == (0xFF000000 | net.minecraft.world.item.DyeColor.RED.getTextureDiffuseColor())
                && p.getInventory().countItem(Items.DYE.pick(net.minecraft.world.item.DyeColor.RED)) == 0, "a red dye used for a red glow, got " + f);
        act(p, pos, ArmourPayloads.ACCENT, 0, 0F, net.minecraft.world.item.DyeColor.BLUE.getId() + 1);
        h.assertTrue(p.getItemBySlot(slot).get(ArmourContent.FORGED.get()).accent() == f.accent(), "no blue dye carried: unchanged");
        act(p, pos, ArmourPayloads.ACCENT, 0, 0F, 0F);
        h.assertTrue(p.getItemBySlot(slot).get(ArmourContent.FORGED.get()).accent() == ArmourInlay.colour(Forces.Force.HEAT),
                "its own glow back: the Sunshard's");
        h.succeed();
    }

    // ---- the made sets (P2) ------------------------------------------------------------------------------------------

    private record Row(ArmourSet set, int helm, int chest, int legs, int boots, float tough, float kb, double speed, double power) {}

    /** The made sets' numbers are SPEC §44.3's table; each has its four pieces, and above E its pattern. */
    private static void numbers(GameTestHelper h) {
        List<Row> table = List.of(
                new Row(ArmourSet.BELLGUARD, 2, 5, 4, 1, 0.5F, 0.05F, 0, 0),
                new Row(ArmourSet.ROOTWARDEN, 2, 3, 3, 1, 0F, 0F, 0, 0.03),
                new Row(ArmourSet.AMBERWING, 2, 4, 3, 1, 0F, 0F, 0, 0),
                new Row(ArmourSet.CLEFTRUNNER, 2, 5, 5, 2, 0.5F, 0F, 0.05, 0),
                new Row(ArmourSet.LANTERNLIGHT, 2, 5, 5, 2, 0.5F, 0F, 0, 0.03),
                new Row(ArmourSet.BLADEWARD, 2, 6, 5, 2, 0.5F, 0F, 0, 0),
                new Row(ArmourSet.HORNLORD, 2, 7, 5, 2, 0.5F, 0F, 0, 0),
                new Row(ArmourSet.VAULTBREAKER, 3, 9, 7, 3, 2.5F, 0.05F, 0, 0),
                new Row(ArmourSet.FROSTWALKER, 3, 7, 6, 3, 2F, 0F, 0.05, 0),
                new Row(ArmourSet.SUNWARD, 3, 7, 6, 3, 2F, 0F, 0, 0.03),
                new Row(ArmourSet.SHARDCROWN, 3, 9, 6, 3, 2F, 0F, 0, 0),
                new Row(ArmourSet.DEEPCARAPACE, 3, 9, 8, 3, 3F, 0.1F, 0, 0),
                new Row(ArmourSet.CLOUDRUNNER, 3, 7, 7, 3, 2.5F, 0.05F, 0.05, 0),
                new Row(ArmourSet.TIDEBREAKER, 3, 8, 7, 3, 2.5F, 0.05F, 0, 0),
                new Row(ArmourSet.HOLLOWHEART, 3, 9, 7, 3, 2.5F, 0.05F, 0, 0));
        for (Row r : table) {
            ArmourItems.Numbers n = ArmourItems.numbers(r.set());
            h.assertTrue(n.helm() == r.helm() && n.chest() == r.chest() && n.legs() == r.legs() && n.boots() == r.boots()
                    && Math.abs(n.toughness() - r.tough()) < 1e-6 && Math.abs(n.knockback() - r.kb()) < 1e-6
                    && Math.abs(n.speed() - r.speed()) < 1e-9 && Math.abs(n.power() - r.power()) < 1e-9, r.set().id + " is " + r + ", got " + n);
            for (ArmorType type : ForgeSong.PIECES) {
                h.assertTrue(ArmourItems.item(r.set(), type) instanceof ForgedArmourItem, r.set().id + " " + type + " is an item of its own");
                ArmourLooks.Look look = ArmourLooks.of(ArmourItems.stack(r.set(), type));
                h.assertTrue(look != null && look.set() == r.set() && look.piece() == type, r.set().id + " " + type + " is drawn as itself");
            }
            h.assertTrue((ArmourItems.pattern(r.set()) == null) == (r.set().rank == Rank.E), r.set().id + ": a pattern above E only");
        }
        h.succeed();
    }

    /** A gate's kill gives a pattern of its rank you don't know; a Lord its Lord-forged set first; using one learns it. */
    private static void patterns(GameTestHelper h) {
        ArmouryMemory m = ArmouryMemory.fresh();
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(7);
        h.assertTrue(ArmourPatterns.pick(m, Rank.E, true, r) == null, "the E sets need no pattern");
        h.assertTrue(ArmourPatterns.pick(m, Rank.D, true, r) == ArmourSet.HORNLORD, "a D Lord gives Hornlord's first");
        for (int i = 0; i < 40; i++) {
            ArmourSet s = ArmourPatterns.pick(m, Rank.D, false, r);
            h.assertTrue(s != null && s.rank == Rank.D && s.hunter == null && s.archetype != Archetype.LORD, "a D creature gives a D set's, never the Lord's: " + s);
        }
        m.patterns.add("hornlord");
        ArmourSet next = ArmourPatterns.pick(m, Rank.D, true, r);
        h.assertTrue(next != null && next != ArmourSet.HORNLORD, "a known Lord pattern isn't given again");
        m.patterns.addAll(List.of("cleftrunner", "lanternlight", "bladeward"));
        h.assertTrue(ArmourPatterns.pick(m, Rank.D, true, r) == null && ArmourPatterns.pick(m, Rank.D, false, r) == null, "none once all are known");
        h.assertTrue(ArmourPatterns.pick(ArmouryMemory.fresh(), Rank.C, true, r) == ArmourSet.SHARDCROWN, "a C Lord gives Shardcrown's first");
        h.assertTrue(ArmourPatterns.pick(ArmouryMemory.fresh(), Rank.B, true, r) == ArmourSet.HOLLOWHEART, "a B Lord gives Hollowheart's first");
        h.assertTrue(ArmourPatterns.pick(ArmouryMemory.fresh(), Rank.A, true, r) == null, "A isn't made yet");

        ServerPlayer p = survivor(h);
        ArmouryMemory mine = ArmouryMemory.of(p);
        h.assertTrue(ArmourForge.known(mine, ArmourSet.AMBERWING) && ArmourForge.known(mine, ArmourSet.BEASTBOUND)
                && !ArmourForge.known(mine, ArmourSet.BLADEWARD), "E and Hunter sets known from the start, the others not");
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ArmourItems.pattern(ArmourSet.BLADEWARD), 2));
        ArmourItems.pattern(ArmourSet.BLADEWARD).use(h.getLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(ArmourForge.known(ArmouryMemory.of(p), ArmourSet.BLADEWARD) && p.getMainHandItem().getCount() == 1, "using a pattern learns it");
        ArmourItems.pattern(ArmourSet.BLADEWARD).use(h.getLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
        h.assertTrue(p.getMainHandItem().getCount() == 1, "a known pattern stays to be given away");
        h.succeed();
    }

    /**
     * Wearing sets: two Bellguard pieces give a heart; four fill Bastion from guards and a deflect bursts it on a zombie
     * close by; blows earn mastery written onto every worn piece; a full Cleftrunner's afterimage takes a blow whole.
     */
    private static void abilities(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = survivor(h);
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
        ArmourAbilities.State s = ArmourAbilities.state(p);
        double base = p.getMaxHealth();
        wear(p, ArmourSet.BELLGUARD, 2);
        h.assertTrue(s.full() == null && Math.abs(p.getMaxHealth() - base - ArmourAbilities.HALF_HEALTH) < 1e-6, "two pieces: a heart, no whole set");
        wear(p, ArmourSet.BELLGUARD, 4);
        h.assertTrue(s.full() == ArmourSet.BELLGUARD && Math.abs(p.getMaxHealth() - base - ArmourAbilities.HALF_HEALTH) < 1e-6, "four: whole, the heart once");
        for (int i = 0; i < 5; i++) dev.awaken.core.api.FightMoments.report(p, dev.awaken.core.api.FightMoments.Moment.GUARD, null);
        h.assertTrue(s.charge() == ArmourAbilities.FULL, "five guards fill Bastion, got " + s.charge());
        net.minecraft.world.entity.monster.zombie.Zombie z = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(2, 1, 1));
        float before = z.getHealth();
        dev.awaken.core.api.FightMoments.report(p, dev.awaken.core.api.FightMoments.Moment.DEFLECT, z);
        h.assertTrue(s.charge() == 0 && z.getHealth() < before, "a deflect bursts it: the zombie hurt (" + before + " -> " + z.getHealth() + ")");
        for (int i = 0; i < 12; i++) dev.awaken.core.api.FightMoments.report(p, dev.awaken.core.api.FightMoments.Moment.HIT, z);
        ArmourAbilities.flush(p, s);
        for (net.minecraft.world.entity.EquipmentSlot slot : List.of(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.FEET)) {
            Forged f = p.getItemBySlot(slot).get(ArmourContent.FORGED.get());
            h.assertTrue(f != null && f.mastery() == 12, "twelve blows of mastery on the " + slot + ", got " + f);
        }
        h.assertTrue(new Forged(Forged.SCHEMA, "bellguard", 0, 299, false, false, "").masteryStep() == 0
                && new Forged(Forged.SCHEMA, "bellguard", 0, 300, false, false, "").masteryStep() == 1
                && new Forged(Forged.SCHEMA, "bellguard", 0, 99999, false, false, "").masteryStep() == Forged.MASTERY_BUILT, "the mastery steps");
        z.discard();

        wear(p, ArmourSet.CLEFTRUNNER, 4);
        h.assertTrue(s.full() == ArmourSet.CLEFTRUNNER && s.charge() == 0, "another set: its own charge, empty");
        s.charge = ArmourAbilities.FULL;
        net.minecraft.world.entity.monster.zombie.Zombie z2 = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(1, 1, 2));
        float hp = p.getHealth();
        p.hurtServer(level, p.damageSources().mobAttack(z2), 4F);
        h.assertTrue(p.getHealth() == hp && s.charge() == 0, "the afterimage takes the blow whole");
        h.succeed();
    }

    /**
     * P3's sets: Featherfall takes no fall, the closed Shell takes less and fills Bastion twice, a C rune stops an arrow,
     * Vault Wall throws a striker back, Hollow Pulse beats on the fifth blow.
     */
    private static void twistsCB(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        ServerPlayer p = survivor(h);
        BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
        p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
        ArmourAbilities.State s = ArmourAbilities.state(p);
        net.minecraft.world.entity.monster.zombie.Zombie z = h.spawn(net.minecraft.world.entity.EntityTypes.ZOMBIE, new BlockPos(3, 1, 1));
        z.setNoAi(true);

        wear(p, ArmourSet.CLOUDRUNNER, 4);
        float hp = p.getHealth();
        p.hurtServer(level, p.damageSources().fall(), 6F);
        h.assertTrue(p.getHealth() == hp, "Featherfall: a fall doesn't hurt");

        wear(p, ArmourSet.DEEPCARAPACE, 4);
        p.setShiftKeyDown(true);
        hp = p.getHealth();
        p.damageCooldownTime = 0;
        p.hurtServer(level, p.damageSources().mobAttack(z), 8F);
        float shut = hp - p.getHealth();
        h.assertTrue(s.charge() == ArmourAbilities.BASTION_TAKEN * 2, "a blow on the closed shell fills Bastion twice, got " + s.charge());
        p.setShiftKeyDown(false);
        p.setHealth(p.getMaxHealth());
        hp = p.getHealth();
        p.damageCooldownTime = 0;
        p.hurtServer(level, p.damageSources().mobAttack(z), 8F);
        float open = hp - p.getHealth();
        h.assertTrue(shut > 0 && shut < open, "the shell takes less: " + shut + " shut, " + open + " open");

        wear(p, ArmourSet.SUNWARD, 4);
        s.runes = 2;
        p.setHealth(p.getMaxHealth());
        hp = p.getHealth();
        p.damageCooldownTime = 0;
        p.hurtServer(level, level.damageSources().source(net.minecraft.world.damagesource.DamageTypes.ARROW, z), 5F);
        h.assertTrue(p.getHealth() == hp && s.runes() == 1, "a C rune stops an arrow and is spent, runes " + s.runes());

        wear(p, ArmourSet.VAULTBREAKER, 4);
        z.snapTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5, 0F, 0F);
        z.setDeltaMovement(Vec3.ZERO);
        dev.awaken.core.api.FightMoments.report(p, dev.awaken.core.api.FightMoments.Moment.GUARD, z);
        h.assertTrue(z.getDeltaMovement().x > 0.5, "Vault Wall throws the striker back, got " + z.getDeltaMovement());

        wear(p, ArmourSet.HOLLOWHEART, 4);
        float zhp = z.getHealth();
        for (int i = 0; i < ArmourAbilities.PULSE_BEATS - 1; i++)
            dev.awaken.core.api.FightMoments.report(p, dev.awaken.core.api.FightMoments.Moment.HIT, z);
        h.assertTrue(z.getHealth() == zhp && s.charge() == ArmourAbilities.FULL * 4 / 5, "four blows: the heart fills, got " + s.charge());
        dev.awaken.core.api.FightMoments.report(p, dev.awaken.core.api.FightMoments.Moment.HIT, z);
        h.assertTrue(z.getHealth() < zhp && s.charge() == 0, "the fifth beats: the zombie hurt, the heart emptied");
        h.succeed();
    }

    /** Puts the first n pieces of a set on (helm, cuirass, greaves, boots) and the rest off, and counts them. */
    private static void wear(ServerPlayer p, ArmourSet set, int n) {
        for (int i = 0; i < 4; i++) {
            ArmorType type = ForgeSong.PIECES[i];
            p.setItemSlot(type.getSlot(), i < n ? ArmourItems.stack(set, type) : ItemStack.EMPTY);
        }
        ArmourAbilities.count(p, ArmourAbilities.state(p));
    }
}
