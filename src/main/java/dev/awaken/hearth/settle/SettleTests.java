package dev.awaken.hearth.settle;

import com.mojang.serialization.JsonOps;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.Comfort;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthData;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.TierRules;
import dev.awaken.hearth.block.HearthAnchorBlock;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.Reading;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.hearth.room.Tally;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.item.DyeColor;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * GameTests for W6 (SPEC §28.8), one per mechanism: someone you know moves into the spare Snug bedroom (and waits for one
 * before it stands, and for a tier past the cap), takes it as "Oren's room" and lives on the people engine; the tiers read
 * the home (four rooms with a Fine one and a field, a Grand room, a bell tower and four people, eight) and the Anchor's
 * flame follows the tier; a Mine head is a hatch over a climbable shaft whose depth decides the evening's haul.
 */
public final class SettleTests {
    private SettleTests() {}

    public static void register() {
        AwakenTests.registerIsolated("hearth_settler_moves_in", 900, h -> {
            ServerLevel level = h.getLevel();
            BlockPos origin = h.absolutePos(BlockPos.ZERO);
            BlockPos base = new BlockPos((origin.getX() & ~15) + 4, origin.getY() + 110, (origin.getZ() & ~15) + 4);
            HearthData data = HearthData.get(level.getServer());
            UUID owner = UUID.randomUUID();
            HearthRecord rec = new HearthRecord(owner, "test", base.offset(10, 12, 4), HearthService.OVERWORLD);
            level.setBlock(rec.pos, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
            data.put(rec);
            BlockPos spare = base.offset(12, 0, 0);
            try {
                room(level, base);
                AwakenTests.awaitLight(h, base.offset(-1, -1, -1), base.offset(9, 6, 9));
                HearthService.scanNow(level, rec);
                List<Settlers.Bedroom> beds = Settlers.bedrooms(level, rec);
                h.assertTrue(beds.size() == 1, "one bedroom read (" + beds.size() + ")");
                rec.folk.ownBeds.put(owner, beds.getFirst().bed());
                Folk.Person oren = Settlers.forceKnow(level, rec, "oren");
                h.assertTrue(oren != null, "Oren is known");
                // your own bed is yours: no spare yet, so Oren waits, and says why
                h.assertTrue(Settlers.spare(level, rec).isEmpty(), "your own bedroom is never a spare");
                List<Settlers.Waiting> waiting = Settlers.waiting(level, rec);
                h.assertTrue(!waiting.isEmpty() && waiting.getFirst().line().equals("Oren will come when there's a bed for him"),
                        "Oren waits for a bed: " + (waiting.isEmpty() ? "nobody" : waiting.getFirst().line()));
                h.assertFalse(Settlers.invite(level, rec, null), "nobody comes without a spare bedroom");
                // a second Snug bedroom: he comes and takes it
                room(level, spare);
                AwakenTests.awaitLight(h, spare.offset(-1, -1, -1), spare.offset(9, 6, 9));
                HearthService.scanNow(level, rec);
                h.assertTrue(Settlers.spare(level, rec).size() == 1, "the new bedroom is a spare");
                h.assertTrue(Settlers.invite(level, rec, null), "Oren sets out");
                Folk.Settler s = rec.folk.settler(oren.id());
                h.assertTrue(s != null && s.bed != null && s.bed.getX() >= spare.getX(), "he takes the spare bedroom, not yours");
                if (s.state == Folk.Settler.COMING) Arrival.movedIn(level, rec, s, true); // the walk itself is the tour's
                h.assertTrue(s.home(), "moved in");
                Room his = null;
                for (Room r : rec.rooms) if (r.contains(s.bed)) his = r;
                h.assertTrue(his != null && Settlers.roomName(rec, his).equals("Oren's room"), "his room is Oren's room");
                // the tier holds one at Tier I: the next one waits for Tier II
                Settlers.forceKnow(level, rec, "mira");
                boolean tierWait = false;
                for (Settlers.Waiting w : Settlers.waiting(level, rec)) tierWait |= w.who().given().equals("Mira") && w.why().startsWith("Tier II");
                h.assertTrue(tierWait, "Mira waits for Tier II");
                // he lives a day on the people engine: the home is a settlement, his record walks to the anvil, the bed
                HomePeople.changed(rec);
                HomePeople.sync(level, rec, level.getGameTime());
                for (int i = 0; i < 400 && HomePeople.settlement(rec) == null; i++) HomePeople.tick(level);
                var settlement = HomePeople.settlement(rec);
                h.assertTrue(settlement != null && settlement.key().equals(HomePeople.key(owner)), "the Hearth runs as a settlement");
                int r = HomePeople.recordOf(rec, oren.id());
                h.assertTrue(r >= 0 && settlement.personName(r).equals("Oren") && settlement.job(r).equals("smith"),
                        "Oren is its smith (" + (r < 0 ? "no record" : settlement.personName(r) + ", " + settlement.job(r)) + ")");
                // saved with the Hearth
                var json = HearthRecord.CODEC.encodeStart(JsonOps.INSTANCE, rec).getOrThrow();
                HearthRecord back = HearthRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                Folk.Settler again = back.folk.settler(oren.id());
                h.assertTrue(again != null && again.home() && s.bed.equals(again.bed) && back.folk.known.containsKey("mira_scout"),
                        "who lives here, their beds and who you know survive a save");
                h.succeed();
            } finally {
                HomePeople.stop(rec);
                data.remove(owner);
                level.removeBlock(rec.pos, false);
                clear(level, base, 9, 6, 9);
                clear(level, spare, 9, 6, 9);
            }
        });

        AwakenTests.registerIsolated("hearth_tier_reading", 400, h -> {
            ServerLevel level = h.getLevel();
            BlockPos origin = h.absolutePos(BlockPos.ZERO);
            BlockPos at = new BlockPos((origin.getX() & ~15) + 8, origin.getY() + 100, (origin.getZ() & ~15) + 8);
            UUID owner = UUID.randomUUID();
            HearthRecord rec = new HearthRecord(owner, "test", at, HearthService.OVERWORLD);
            try {
                // II: four rooms, one Fine, a field or a pen
                rec.rooms = List.of(fake(Reading.Grade.SNUG, RoomType.BEDROOM), fake(Reading.Grade.SNUG, RoomType.KITCHEN),
                        fake(Reading.Grade.BARE, RoomType.SHELTER));
                h.assertFalse(TierRules.met(rec, 2), "three rooms aren't a Steading");
                rec.rooms = List.of(fake(Reading.Grade.SNUG, RoomType.BEDROOM), fake(Reading.Grade.SNUG, RoomType.KITCHEN),
                        fake(Reading.Grade.BARE, RoomType.SHELTER), fake(Reading.Grade.FINE, RoomType.ARMORY));
                h.assertFalse(TierRules.met(rec, 2), "and four with a Fine one want a field or a pen");
                rec.land = Comfort.FIELD;
                h.assertTrue(TierRules.met(rec, 2), "four rooms, one Fine, a field: a Steading");
                // III: a Grand room
                h.assertFalse(TierRules.met(rec, 3), "no Grand room yet");
                rec.rooms = List.of(fake(Reading.Grade.GRAND, RoomType.HALL), fake(Reading.Grade.SNUG, RoomType.BEDROOM));
                h.assertTrue(TierRules.met(rec, 3), "a Grand room: a Hall");
                // IV: a bell tower and four people; V: eight
                rec.rooms = List.of(fake(Reading.Grade.GRAND, RoomType.HALL), fake(Reading.Grade.SNUG, RoomType.BELL_TOWER));
                for (int i = 0; i < 3; i++) rec.folk.settlers.add(new Folk.Settler(Settlers.outsider("t" + i, "here", i), null, Folk.Settler.HOME, 0));
                h.assertFalse(TierRules.met(rec, 4), "three people aren't a village");
                rec.folk.settlers.add(new Folk.Settler(Settlers.outsider("t3", "here", 3), null, Folk.Settler.HOME, 0));
                h.assertTrue(TierRules.met(rec, 4), "a bell tower and four people: a Village");
                h.assertFalse(TierRules.met(rec, 5), "four aren't a town");
                h.assertTrue(Settlers.cap(1) == 1 && Settlers.cap(2) == 2 && Settlers.cap(3) == 4 && Settlers.cap(4) >= 8, "settlers' caps by tier");
                h.assertTrue(HearthRecord.radiusForTier(4) == 80 && HearthRecord.radiusForTier(5) == 96, "Village 80, Town 96");
                // the Anchor's flame follows the tier
                level.setBlock(at, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
                rec.tier = 4;
                HearthService.showTier(level, rec);
                BlockState s = level.getBlockState(at);
                h.assertTrue(s.is(HearthContent.HEARTH_ANCHOR.get()) && s.getValue(HearthAnchorBlock.TIER) == 4, "the Anchor burns at Tier IV");
                h.assertTrue(TierRules.village(rec) && TierRules.name(4).equals("Village"), "Tier IV is a village");
                h.succeed();
            } finally {
                level.removeBlock(at, false);
            }
        });

        AwakenTests.registerIsolated("hearth_mine_head", 900, h -> {
            ServerLevel level = h.getLevel();
            BlockPos origin = h.absolutePos(BlockPos.ZERO);
            BlockPos base = new BlockPos((origin.getX() & ~15) + 4, origin.getY() + 120, (origin.getZ() & ~15) + 4);
            HearthData data = HearthData.get(level.getServer());
            UUID owner = UUID.randomUUID();
            HearthRecord rec = new HearthRecord(owner, "test", base.offset(3, 8, 3), HearthService.OVERWORLD);
            level.setBlock(rec.pos, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
            data.put(rec);
            BlockPos hatch = base.offset(2, 0, 2);
            int depth = 20;
            try {
                // a 5 x 5 shed (3 x 3 inside, 3 high) with a door, a lantern and a chest; a trapdoor in its floor
                for (int x = 0; x < 5; x++) for (int y = 0; y < 5; y++) for (int z = 0; z < 5; z++) {
                    boolean shell = x == 0 || x == 4 || y == 0 || y == 4 || z == 0 || z == 4;
                    level.setBlock(base.offset(x, y, z), shell ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
                level.setBlock(base.offset(1, 3, 1), Blocks.LANTERN.defaultBlockState().setValue(BlockStateProperties.HANGING, true), 3);
                level.setBlock(base.offset(3, 1, 3), Blocks.CHEST.defaultBlockState(), 3);
                level.setBlock(hatch, Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF, Half.TOP), 3);
                // the shaft: rock round it, a ladder all the way down, a floor at its foot
                shaft(level, hatch, depth);
                AwakenTests.awaitLight(h, base.offset(-1, -1, -1), base.offset(5, 5, 5));
                HearthService.scanNow(level, rec);
                Room head = null;
                for (Room r : rec.rooms) if (r.types().contains(RoomType.MINE_HEAD)) head = r;
                h.assertTrue(head != null, "a hatch over a ladder shaft 20 deep is a Mine head (rooms: " + kinds(rec) + ")");
                h.assertTrue(head.fact(Tally.F_SHAFT) == depth && head.fact(Tally.F_SHAFT_Y) == hatch.getY(), "its shaft read 20 deep");
                int[] shallow = new int[Tally.F_COUNT];
                shallow[Tally.F_SHAFT] = 15;
                h.assertFalse(RoomType.MINE_HEAD.metBy(new int[Furniture.COUNT], shallow), "15 deep isn't a mine");
                List<MineHead.Shaft> shafts = MineHead.shafts(level, rec);
                h.assertTrue(shafts.size() == 1 && shafts.getFirst().foot() == hatch.getY() - depth, "one shaft, its foot at the hatch less 20");
                // the depth decides what comes up; a lode it cut adds its ore (never more than is left of it)
                RandomSource rnd = RandomSource.create(42);
                h.assertTrue(has(MineHead.haul(rnd, 50, null, 0), Items.COAL) && !has(MineHead.haul(rnd, 50, null, 0), Items.REDSTONE),
                        "high up: coal, no redstone");
                h.assertTrue(has(MineHead.haul(rnd, -30, null, 0), Items.REDSTONE), "far down: redstone");
                List<ItemStack> lode = MineHead.haul(rnd, 10, dev.awaken.delve.Ore.DIAMOND, 1);
                int diamonds = 0;
                for (ItemStack st : lode) if (st.is(Items.DIAMOND)) diamonds += st.getCount();
                h.assertTrue(diamonds == 1, "a diamond lode's last one, and no more (" + diamonds + ")");
                // a miner who lives here brings the evening's haul up into the chest by the hatch
                rec.folk.settlers.add(new Folk.Settler(Settlers.forceKnow(level, rec, "miner"), null, Folk.Settler.HOME, 0));
                Work.evening(level, rec, Settlers.today(level.getServer()));
                int in = 0;
                if (level.getBlockEntity(base.offset(3, 1, 3)) instanceof ChestBlockEntity chest) {
                    for (int i = 0; i < chest.getContainerSize(); i++) in += chest.getItem(i).getCount();
                }
                h.assertTrue(in > 0, "the haul is in the chest by the shaft (" + in + " items)");
                h.assertTrue(rec.folk.haulDay == Settlers.today(level.getServer()), "once an evening");
                h.succeed();
            } finally {
                data.remove(owner);
                level.removeBlock(rec.pos, false);
                clear(level, base, 5, 5, 5);
                for (int k = 1; k <= depth + 1; k++) clear(level, hatch.offset(-1, -k, -1), 3, 1, 3);
            }
        });
    }

    /** A 7 x 3 x 7 timber bedroom like HomeTests' (lit, a window, a bed, soft things): Fine. */
    static void room(ServerLevel level, BlockPos base) {
        for (int x = 0; x < 9; x++) for (int y = 0; y < 5; y++) for (int z = 0; z < 9; z++) {
            boolean shell = x == 0 || x == 8 || y == 0 || y == 4 || z == 0 || z == 8;
            BlockState s = !shell ? Blocks.AIR.defaultBlockState() : y == 4 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState();
            level.setBlock(base.offset(x, y, z), s, 3);
        }
        level.setBlock(base.offset(0, 2, 4), Blocks.GLASS_PANE.defaultBlockState(), 3);
        level.setBlock(base.offset(4, 4, 4), Blocks.OAK_TRAPDOOR.defaultBlockState(), 3);
        BlockState foot = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
        level.setBlock(base.offset(2, 1, 1), foot, 3);
        level.setBlock(base.offset(2, 1, 2), foot.setValue(BedBlock.PART, BedPart.HEAD), 3);
        level.setBlock(base.offset(4, 1, 4), Blocks.LANTERN.defaultBlockState(), 3);
        level.setBlock(base.offset(6, 1, 6), Blocks.LANTERN.defaultBlockState(), 3);
        level.setBlock(base.offset(5, 1, 2), Blocks.CARPET.pick(DyeColor.RED).defaultBlockState(), 3);
        level.setBlock(base.offset(6, 1, 1), Blocks.POTTED_POPPY.defaultBlockState(), 3);
        level.setBlock(base.offset(2, 1, 6), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH), 3);
        level.setBlock(base.offset(7, 1, 3), Blocks.CAMPFIRE.defaultBlockState(), 3);
    }

    static void shaft(ServerLevel level, BlockPos hatch, int depth) {
        for (int k = 1; k <= depth; k++) {
            BlockPos c = hatch.below(k);
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (dx != 0 || dz != 0) level.setBlock(c.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 3);
            }
            level.setBlock(c, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH), 3);
        }
        level.setBlock(hatch.below(depth + 1), Blocks.STONE.defaultBlockState(), 3);
    }

    static Room fake(Reading.Grade g, RoomType type) {
        EnumSet<RoomType> types = EnumSet.of(RoomType.SHELTER, type);
        int v = g.ordinal();
        Reading reading = new Reading(v, v, v, v, v, v, g, "the " + g.word + " room", "", type);
        return new Room(types, BlockPos.ZERO, BlockPos.ZERO, new LongOpenHashSet(), new int[Furniture.COUNT], new int[Tally.F_COUNT], Set.of(), reading);
    }

    static boolean has(List<ItemStack> l, net.minecraft.world.item.Item item) {
        for (ItemStack s : l) if (s.is(item)) return true;
        return false;
    }

    static String kinds(HearthRecord h) {
        StringBuilder b = new StringBuilder();
        for (Room r : h.rooms) b.append(r.types()).append(' ');
        return b.toString();
    }

    static void clear(ServerLevel level, BlockPos base, int w, int hgt, int d) {
        for (int x = 0; x < w; x++) for (int y = 0; y < hgt; y++) for (int z = 0; z < d; z++) {
            level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
    }
}
