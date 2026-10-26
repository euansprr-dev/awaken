package dev.awaken.hearth;

import com.mojang.serialization.JsonOps;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.room.Reading;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.UUID;

/**
 * GameTests for the carried flame (SPEC §28.2), one per mechanism: the Ember founds a Hearthfire (and its refusals, the
 * lifting, the Lord's Heart raising it to the Anchor); a room is read and graded (a Bare dirt hole, a timber bedroom
 * held at Fine by its weakest reading until a fire is lit, the name, the reading saved); waking in your own bed at home
 * makes you Rested with its perks.
 */
final class HomeTests {
    private HomeTests() {}

    static void register() {
        AwakenTests.registerIsolated("hearth_ember_founds", 400, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            HearthData data = HearthData.get(level.getServer());
            BlockPos origin = h.absolutePos(BlockPos.ZERO);
            BlockPos ground = new BlockPos((origin.getX() & ~15) + 8, origin.getY() + 100, (origin.getZ() & ~15) + 8);
            level.setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
            BlockPos at = ground.above();
            try {
                // the camp hands over one Ember; a second never stacks up
                h.assertTrue(Embers.give(sp) && Embers.carries(sp), "an Ember is given");
                h.assertFalse(Embers.give(sp), "only one Ember a player");
                // refused on thin air, with a plain reason
                h.assertTrue(Embers.refusal(sp, level, ground.above(3), ground.above(4)).orElse("").contains("firm"), "not on air");
                // set down: a Tier 0 Hearthfire, radius 24, named from the land's words
                clearEmbers(sp);
                sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(HearthContent.EMBER_ITEM.get()));
                Embers.setDown(sp, level, ground, InteractionHand.MAIN_HAND);
                HearthRecord fire = data.of(sp.getUUID()).orElse(null);
                h.assertTrue(fire != null && fire.hearthfire() && fire.radius() == HearthRecord.HEARTHFIRE_RADIUS, "a Tier 0 Hearth, radius 24");
                h.assertTrue(level.getBlockState(at).is(HearthContent.HEARTHFIRE.get()), "the ring of stones stands");
                h.assertFalse(Embers.carries(sp), "the coal went into the ring");
                h.assertFalse(fire.name.isBlank(), "a name is offered from the land's words");
                h.assertTrue(HearthHooks.inside(level, at.offset(20, 0, 0)) && !HearthHooks.inside(level, at.offset(30, 0, 0)),
                        "calm land reaches 24 (the rules that key off any Hearth hold)");
                h.assertTrue(HearthService.player(sp).respawnAtHearth, "you wake here");
                // nobody else's coal takes on this ground; yours burns already
                h.assertTrue(Embers.refusal(sp, level, ground, at).orElse("").contains("already"), "one fire a player");
                h.assertTrue(Embers.cleanName("Ash—ford!!   Rest") .equals("Ash-ford Rest"), "names are cleaned");
                // before the first room stands, the empty hand lifts the coal again
                Embers.useFire(sp, level, at);
                h.assertTrue(data.of(sp.getUUID()).isEmpty() && level.getBlockState(at).isAir() && Embers.carries(sp), "lifted: carried on");
                // set down again, then a Lord's Heart raises it to the Anchor in place
                HearthRecord again = Embers.found(sp, level, at);
                clearEmbers(sp);
                sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CoreApi.LORD_HEART.get()));
                Embers.raise(sp, level, at, InteractionHand.MAIN_HAND);
                h.assertTrue(again.tier == 1 && again.radius() == 48 && level.getBlockState(at).is(HearthContent.HEARTH_ANCHOR.get()),
                        "the Heart raises the fire to the Anchor, Tier I, radius 48");
                h.assertTrue(data.of(sp.getUUID()).orElse(null) == again, "the same Hearth, raised in place");
                h.succeed();
            } finally {
                data.of(sp.getUUID()).ifPresent(r -> dev.awaken.hearth.town.Town.dissolve(level, r));
                data.remove(sp.getUUID());
                level.removeBlock(at, false);
                level.removeBlock(ground, false);
            }
        });

        AwakenTests.registerIsolated("hearth_room_grades", 900, h -> {
            // the pure rules first: the weakest reading holds the grade down, never a stack
            h.assertTrue(Reading.grade(new int[]{3, 3, 1, 3, 3, 3}) == Reading.Grade.FINE, "a grand room with one weak reading is Fine");
            h.assertTrue(Reading.grade(new int[]{3, 3, 2, 3, 3, 3}) == Reading.Grade.GRAND, "lift the weak one and it's Grand");
            h.assertTrue(Reading.grade(new int[]{3, 2, 0, 1, 0, 0}) == Reading.Grade.BARE, "a hole is Bare");
            h.assertTrue(Reading.light(97, 100, 1) == 3 && Reading.light(90, 100, 1) == 1, "one dark corner keeps the light at 1");
            h.assertTrue(Reading.detail(1, 2) == 3 && Reading.detail(6, 0) == 2, "the best of each kind, not a stack");
            ServerLevel level = h.getLevel();
            BlockPos origin = h.absolutePos(BlockPos.ZERO);
            BlockPos base = new BlockPos((origin.getX() & ~15) + 4, origin.getY() + 110, (origin.getZ() & ~15) + 4);
            HearthData data = HearthData.get(level.getServer());
            UUID owner = UUID.randomUUID();
            HearthRecord rec = new HearthRecord(owner, "test", base.offset(4, 12, 4), HearthService.OVERWORLD);
            level.setBlock(rec.pos, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
            data.put(rec);
            BlockPos hole = base.offset(1, 7, 1);
            try {
                fineRoom(level, base);
                bareHole(level, hole);
                AwakenTests.awaitLight(h, base.offset(-1, -1, -1), base.offset(9, 12, 9));
                HearthService.scanNow(level, rec);
                Room fine = at(rec, base.offset(3, 1, 3)), bare = at(rec, hole.offset(1, 1, 1));
                h.assertTrue(fine != null && bare != null, "both rooms read (rooms " + rec.rooms.size() + ")");
                Reading f = fine.reading();
                h.assertTrue(f.grade() == Reading.Grade.FINE && f.warmth() == 1 && f.weakest() == 1,
                        "the timber bedroom is Fine, held by its cold stove: " + describe(f));
                h.assertTrue(f.name().equals("the fine timber bedroom"), "named by grade, material and purpose: " + f.name());
                h.assertTrue(f.lift().contains("fire"), "the lift names the fire: " + f.lift());
                h.assertTrue(fine.types().contains(RoomType.BEDROOM) && fine.types().contains(RoomType.SHELTER), "a bedroom, and a shelter");
                Reading b = bare.reading();
                h.assertTrue(b.grade() == Reading.Grade.BARE && b.name().equals("the bare earthen shelter"), "the dirt hole: " + b.name() + " " + describe(b));
                // light the stove: the weakest reading rises and the grade with it
                BlockPos stove = base.offset(7, 1, 3);
                level.setBlock(stove, level.getBlockState(stove).setValue(BlockStateProperties.LIT, true), 3);
                HearthService.scanNow(level, rec);
                Room lit = at(rec, base.offset(3, 1, 3));
                h.assertTrue(lit != null && lit.reading().grade() == Reading.Grade.GRAND && lit.reading().name().equals("the grand timber bedroom"),
                        "a lit stove makes it Grand: " + (lit == null ? "gone" : describe(lit.reading())));
                h.assertTrue(rec.comfort >= 5 && !rec.style.isEmpty(), "Comfort " + rec.comfort + " and Style '" + rec.style + "'");
                // the reading is saved (schema 3): a restart doesn't re-read every home at once
                var json = HearthRecord.CODEC.encodeStart(JsonOps.INSTANCE, rec).getOrThrow();
                HearthRecord back = HearthRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
                Room saved = at(back, base.offset(3, 1, 3));
                h.assertTrue(saved != null && saved.reading().name().equals("the grand timber bedroom") && back.lastScan < 0 && back.comfort == rec.comfort,
                        "the reading survives a save");
                h.succeed();
            } finally {
                data.remove(owner);
                level.removeBlock(rec.pos, false);
                clear(level, base, 9, 6, 9);
                clear(level, hole, 5, 4, 5);
            }
        });

        AwakenTests.registerIsolated("hearth_rested", 600, h -> {
            ServerLevel level = h.getLevel();
            ServerPlayer sp = h.makeMockServerPlayerInLevel();
            Progression.awaken(sp);
            BlockPos origin = h.absolutePos(BlockPos.ZERO);
            BlockPos base = new BlockPos((origin.getX() & ~15) + 4, origin.getY() + 110, (origin.getZ() & ~15) + 4);
            HearthData data = HearthData.get(level.getServer());
            HearthRecord rec = new HearthRecord(sp.getUUID(), "test", base.offset(4, 12, 4), HearthService.OVERWORLD);
            level.setBlock(rec.pos, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
            data.put(rec);
            try {
                fineRoom(level, base);
                AwakenTests.awaitLight(h, base.offset(-1, -1, -1), base.offset(9, 6, 9));
                HearthService.scanNow(level, rec);
                h.assertTrue(rec.hasRoom(RoomType.BEDROOM), "the bedroom is read");
                h.assertTrue(Progression.perk(sp, Rested.ENERGY_REGEN) < Rested.ENERGY && Progression.perk(sp, Rested.CRAFT_RATE) < Rested.CRAFT,
                        "not Rested yet");
                // waking in a bed outside any bedroom rests nobody
                sp.setSleepingPos(base.offset(4, 30, 4));
                Rested.onWake(sp, level);
                h.assertFalse(Rested.active(sp), "a bed out in the open is no rest");
                // waking in your own bed at home: Rested, 8 minutes and one a point of Comfort
                sp.setSleepingPos(base.offset(2, 1, 2));
                Rested.onWake(sp, level);
                long left = Rested.left(sp);
                long expect = 20L * 60 * Comfort.restedMinutes(rec.comfort);
                h.assertTrue(Rested.active(sp) && Math.abs(left - expect) <= 2, "Rested for " + (8 + rec.comfort) + " min, was " + left + " ticks");
                h.assertTrue(Progression.perk(sp, Rested.ENERGY_REGEN) >= Rested.ENERGY && Progression.perk(sp, Rested.CRAFT_RATE) >= Rested.CRAFT,
                        "energy and crafts come quicker");
                // it ends
                HearthService.player(sp).restedUntil = level.getGameTime() - 1;
                HearthViews.refresh(sp);
                h.assertFalse(Rested.active(sp) || Progression.perk(sp, Rested.CRAFT_RATE) >= Rested.CRAFT, "and it wears off");
                h.succeed();
            } finally {
                data.remove(sp.getUUID());
                level.removeBlock(rec.pos, false);
                clear(level, base, 9, 6, 9);
            }
        });
    }

    /**
     * A 7×3×7 timber bedroom (oak plank walls and floor, a spruce plank roof with a trapdoor, a glass pane window), lit
     * all over by two lanterns, a bed, a carpet, a potted flower and a stair to sit on, and a cold stove in the wall: every
     * reading 3 but its warmth (1).
     */
    static void fineRoom(ServerLevel level, BlockPos base) {
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
        level.setBlock(base.offset(7, 1, 3), Blocks.FURNACE.defaultBlockState(), 3);
        level.setBlock(base.offset(8, 1, 3), Blocks.OAK_PLANKS.defaultBlockState(), 3);
    }

    /** A 3×2×3 hole walled in dirt with a chest in it (something a home has): closed, and nothing more. */
    static void bareHole(ServerLevel level, BlockPos base) {
        for (int x = 0; x < 5; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++) {
            boolean shell = x == 0 || x == 4 || y == 0 || y == 3 || z == 0 || z == 4;
            level.setBlock(base.offset(x, y, z), shell ? Blocks.DIRT.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(base.offset(1, 1, 1), Blocks.CHEST.defaultBlockState(), 3);
    }

    static Room at(HearthRecord h, BlockPos p) {
        for (Room r : h.rooms) if (r.contains(p)) return r;
        return null;
    }

    static String describe(Reading r) {
        return r.grade() + " [closed " + r.closed() + ", light " + r.light() + ", warmth " + r.warmth() + ", room " + r.space() + ", care "
                + r.care() + ", detail " + r.detail() + "] " + r.name();
    }

    static void clear(ServerLevel level, BlockPos base, int w, int hgt, int d) {
        for (int x = 0; x < w; x++) for (int y = 0; y < hgt; y++) for (int z = 0; z < d; z++) {
            level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    static void clearEmbers(ServerPlayer sp) {
        var inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(HearthContent.EMBER_ITEM.get())) inv.setItem(i, ItemStack.EMPTY);
    }
}
