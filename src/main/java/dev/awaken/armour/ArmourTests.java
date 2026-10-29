package dev.awaken.armour;

import dev.awaken.Awaken;
import dev.awaken.armour.net.ArmourPayloads;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Armour GameTests (SPEC §44): the roster's shape, the Hunter items' looks, the Hunter sets' art shipped; the Forge Song's
 * rules, its saves, a piece made by hand start to finish and one at the Forge with a helper.
 */
final class ArmourTests {
    private ArmourTests() {}

    static void register() {
        AwakenTests.register("armour_roster_shape", 20, ArmourTests::roster);
        AwakenTests.register("armour_hunter_looks", 20, ArmourTests::looks);
        AwakenTests.register("armour_hunter_art_shipped", 20, ArmourTests::art);
        AwakenTests.register("armour_forge_rules", 20, ArmourTests::forgeRules);
        AwakenTests.register("armour_forge_saves", 20, ArmourTests::forgeSaves);
        AwakenTests.register("armour_forge_by_hand", 40, ArmourTests::byHand);
        AwakenTests.register("armour_forge_at_the_forge", 40, ArmourTests::atTheForge);
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

    /** Each Hunter set ships a texture and a mesh for every piece on its main bone, so none falls back to the shell. */
    private static void art(GameTestHelper h) {
        String[][] main = {{"helmet", "head"}, {"chestplate", "body"}, {"leggings", "right_leg"}, {"boots", "right_leg"}};
        for (HunterTier tier : HunterTier.values()) {
            ArmourSet s = ArmourSet.of(tier);
            h.assertTrue(exists("/assets/awaken/textures/entity/armour/" + s.id + ".png"), s.id + " has its texture");
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
                if (set.hunter == null) {
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
        UUID who = UUID.randomUUID();
        ForgeWork w = new ForgeWork(ArmourSet.BEASTBOUND, ArmorType.LEGGINGS, ForgeSong.SHAPE, 1234L, 2, List.of(new ItemStack(Items.IRON_INGOT, 3)),
                0.66F, -1F, 0.4F, List.of(1F, 0.5F, 0.9F), 1, who, false, new BlockPos(1, 2, 3), Optional.of(new BlockPos(4, 5, 6)));
        ForgeWork b = ForgeWork.CODEC.encodeStart(ops, w).flatMap(t -> ForgeWork.CODEC.parse(ops, t)).getOrThrow();
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
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Progression.awaken(p);
        while (Progression.rank(p).ordinal() < rank.ordinal()) Progression.passReassessment(p);
        p.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 1.5, 0F, 0F);
        return p;
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
     * Wildhide by hand (E): the stand offers only Wildhide and says it needs a works table; with one near and the hide in
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
        h.assertTrue(offers.offers().size() == 4 && offers.offers().stream().allMatch(o -> o.piece() / 4 == ArmourSet.WILDHIDE.ordinal()),
                "E offers Wildhide's four pieces");
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
}
