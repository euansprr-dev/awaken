package dev.awaken.hearth.guide;

import dev.awaken.core.test.AwakenTests;
import dev.awaken.hearth.Hearth;
import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.HearthData;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.room.Furniture;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.signal.Signal;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** GameTests for the Hearth guide: the cottage is a Sanctuary, the "why not" reasons, the data matches RoomType, the offer. */
public final class GuideTests {
    private GuideTests() {}

    public static void register() {
        AwakenTests.register("hearth_guide_matches_roomtype", 20, h -> {
            HearthRecord rec = new HearthRecord(UUID.randomUUID(), "test", BlockPos.ZERO, HearthService.OVERWORLD);
            for (RoomType t : RoomType.values()) {
                int mask = 0;
                for (Furniture f : t.furniture) mask |= f.bit();
                // nothing in the radius: nothing ticked; everything the room lists: exactly those ticked
                rec.furniture = new int[Furniture.COUNT];
                h.assertTrue(Guide.card(t, rec).have() == 0, t + ": empty radius ticks nothing");
                for (Furniture f : Furniture.values()) rec.furniture[f.ordinal()] = 1;
                GuideData.RoomCard card = Guide.card(t, rec);
                h.assertTrue(card.have() == mask, t + ": the checklist is RoomType's furniture, was " + card.have() + " vs " + mask);
                h.assertTrue(card.type() == t.ordinal() && card.built().isEmpty(), t + ": not built without a room");
                // classify reads the same list: all of it (and the kind's condition) makes the room, any one short doesn't
                int[] counts = new int[Furniture.COUNT];
                for (Furniture f : t.furniture) counts[f.ordinal()] = f.needed();
                int[] facts = conditionMet(t);
                h.assertTrue(Room.classify(counts, facts).contains(t), t + ": its furniture and condition classify as it");
                for (Furniture without : t.furniture) {
                    int[] less = counts.clone();
                    less[without.ordinal()] = without.needed() - 1;
                    h.assertFalse(Room.classify(less, facts).contains(t), t + " without " + without + " is not one");
                }
                if (t.condition != null) h.assertFalse(Room.classify(counts, new int[dev.awaken.hearth.room.Tally.F_COUNT]).contains(t),
                        t + ": not one without " + t.condition);
                if (!t.furniture.isEmpty()) h.assertTrue(t.needs.contains(t.furniture.get(0).label), t + ": the 'needs' words come from the furniture");
                // what the player reads fits: toast title and first sentence in 144 px, Now lines in the tracker
                h.assertTrue(Fit.px(t.label + " recognised") <= Fit.TOAST, t + ": toast title fits");
                h.assertTrue(Fit.px(Fit.firstSentence(t.toast + " " + t.does)) <= Fit.TOAST, t + ": toast line fits, " + Fit.px(t.toast));
                rec.furniture = new int[Furniture.COUNT];
                h.assertTrue(Fit.px(dev.awaken.hearth.HearthNow.progress(t, rec)) <= Fit.TRACKER, t + ": progress line fits");
                if (!t.furniture.isEmpty()) rec.furniture[t.furniture.get(0).ordinal()] = 1;
                h.assertTrue(Fit.px(dev.awaken.hearth.HearthNow.progress(t, rec)) <= Fit.TRACKER, t + ": progress line fits, half done");
                if (t.condition != null) h.assertTrue(Fit.px(WhyNot.condition(t, BlockPos.ZERO).tracker()) <= Fit.TRACKER, t + ": condition line fits");
                for (Furniture f : t.furniture) {
                    var leak = WhyNot.leak(t, new dev.awaken.hearth.room.Miss(f, BlockPos.ZERO, dev.awaken.hearth.room.Miss.Leak.OPENING, new BlockPos(-123, 64, 456)));
                    h.assertTrue(Fit.px(leak.tracker()) <= Fit.TRACKER_WITH_COMPASS, t + ": why line fits beside a compass: " + leak.tracker());
                    h.assertTrue(Fit.px(WhyNot.missing(t, t.furniture, BlockPos.ZERO).tracker()) <= Fit.TRACKER, t + ": missing line fits");
                    for (var big : new dev.awaken.hearth.room.Miss.Leak[]{dev.awaken.hearth.room.Miss.Leak.TOO_BIG, dev.awaken.hearth.room.Miss.Leak.TOO_WIDE}) {
                        var r = WhyNot.leak(t, new dev.awaken.hearth.room.Miss(f, BlockPos.ZERO, big, BlockPos.ZERO));
                        h.assertTrue(Fit.px(r.tracker()) <= Fit.TRACKER_WITH_COMPASS && !r.card().contains("32"), t + ": " + big + " says the real cap");
                    }
                }
            }
            h.assertTrue(Fit.px("Right-click your campfire") <= Fit.TRACKER && Fit.px("Right-click your Anchor") <= Fit.TRACKER, "the guide nudge fits");
            h.assertTrue(Fit.px("Within 48 blocks: yours.") <= Fit.TOAST && Fit.px("Free: your first Sanctuary.") <= Fit.TOAST, "welcome and offer lines fit");
            // the guide's data survives the wire
            GuideData d = new GuideData(2, 56, 26, 13, true, new BlockPos(1, 2, 3),
                    List.of(new GuideData.RoomCard(0, 3, true, List.of(new BlockPos(4, 5, 6)), "why", Optional.of(new BlockPos(7, 8, 9)), true)),
                    List.of(new GuideData.RecipeView(1, 3, 1, List.of(List.of(net.minecraft.resources.Identifier.withDefaultNamespace("white_wool")), List.of(),
                            List.of(net.minecraft.resources.Identifier.withDefaultNamespace("amethyst_shard"))), dev.awaken.Awaken.id("meditation_mat"), 1)),
                    List.of(new GuideData.Person("Oren", "Blacksmith", "")), List.of(new GuideData.Person("Mira", "Scout", "D-rank")),
                    new GuideData.Upgrade(false, 3, 64, 22, "C-rank", true, List.of(new GuideData.Part(net.minecraft.resources.Identifier.withDefaultNamespace("diamond"), 6, 2)), 15, false),
                    GuideData.COTTAGE_OFFER, "Oren is building your cottage.", new GuideData.Home("Ashford", 5, "timber and fieldstone", "a table and seats", List.of("a field"), 13,
                            List.of(new GuideData.RoomView("the snug timber bedroom", 1, List.of(3, 2, 1, 2, 2, 1), "Bedroom", "a lit fire", new BlockPos(1, 2, 3)))));
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            GuideData.STREAM_CODEC.encode(buf, d);
            h.assertTrue(GuideData.STREAM_CODEC.decode(buf).equals(d), "guide data round-trips");
            // the site rules: only terrain and plants give way
            h.assertTrue(Cottage.clearable(Blocks.SHORT_GRASS.defaultBlockState()) && Cottage.clearable(Blocks.POPPY.defaultBlockState()), "plants clear");
            h.assertTrue(Cottage.clearable(Blocks.OAK_LEAVES.defaultBlockState()), "natural leaves clear");
            h.assertFalse(Cottage.clearable(Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true)), "placed leaves don't");
            h.assertFalse(Cottage.clearable(Blocks.OAK_PLANKS.defaultBlockState()) || Cottage.natural(Blocks.OAK_PLANKS.defaultBlockState()), "planks are a build");
            h.assertFalse(Cottage.natural(Blocks.FARMLAND.defaultBlockState()) || Cottage.natural(Blocks.COBBLESTONE.defaultBlockState()), "farms and cobble are builds");
            h.assertTrue(Cottage.natural(Blocks.GRASS_BLOCK.defaultBlockState()) && Cottage.natural(Blocks.STONE.defaultBlockState()), "ground is terrain");
            h.succeed();
        });

        AwakenTests.registerIsolated("hearth_cottage_is_a_sanctuary", 600, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            // every palette, every turn, high above the test floor on its own patch of grass, one cottage per chunk around
            // the Anchor's chunk; each is dumped to run/structures/cottage_<palette>.json for the isometric previews.
            // Everything is taken down again after every attempt: other tests share these columns.
            int baseY = o.getY() + 120, cx = o.getX() & ~15, cz = o.getZ() & ~15;
            Rotation[] turns = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};
            UUID owner = UUID.randomUUID();
            HearthRecord rec = new HearthRecord(owner, "test", new BlockPos(cx + 8, baseY + 1, cz + 8), HearthService.OVERWORLD);
            HearthData data = HearthData.get(level.getServer());
            int n = CottagePlan.PALETTES.size();
            int[][] chunks = new int[n][];
            for (int i = 0; i < n; i++) chunks[i] = new int[]{cx + 16 * (i % 5 - 2), cz + 16 * (i < 5 ? -1 : 1)};
            h.succeedWhen(() -> {
                try {
                    grass(level, cx, baseY, cz);
                    level.setBlock(rec.pos, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
                    if (data.of(owner).isEmpty()) data.put(rec);
                    List<BlockPos> inside = new java.util.ArrayList<>(), origins = new java.util.ArrayList<>();
                    for (int i = 0; i < n; i++) {
                        grass(level, chunks[i][0], baseY, chunks[i][1]);
                        BlockPos origin = fitInChunk(chunks[i][0], baseY, chunks[i][1], turns[i % 4]);
                        origins.add(origin);
                        inside.add(Cottage.placeAll(level, origin, turns[i % 4], CottagePlan.PALETTES.get(i)));
                    }
                    rec.cottage = inside.get(0);
                    HearthService.scanNow(level, rec);
                    List<String> notRooms = new java.util.ArrayList<>();
                    for (int i = 0; i < n; i++) if (rec.roomAt(inside.get(i), RoomType.SANCTUARY).isEmpty()) notRooms.add(CottagePlan.PALETTES.get(i).id());
                    GuideData.RoomCard card = Guide.card(RoomType.SANCTUARY, rec);
                    h.assertTrue(notRooms.isEmpty(), "every cottage scans as a Sanctuary, not: " + notRooms + " (misses " + rec.misses + ")");
                    h.assertTrue(card.byOren() && !card.built().isEmpty() && card.why().isEmpty(), "the guide says Oren built one");
                    for (int i = 0; i < n; i++) {
                        BlockPos door = CottagePlan.at(origins.get(i), turns[i % 4], 4, 1, 0);
                        h.assertTrue(level.getBlockState(door).is(net.minecraft.tags.BlockTags.DOORS), CottagePlan.PALETTES.get(i).id() + ": a door in the front wall");
                        dump(level, "cottage_" + CottagePlan.PALETTES.get(i).id(), new BlockPos(chunks[i][0], baseY - 1, chunks[i][1]),
                                new BlockPos(chunks[i][0] + 15, baseY + 14, chunks[i][1] + 15));
                    }
                } finally {
                    data.remove(owner);
                    clearChunk(level, cx, baseY, cz);
                    for (int[] c : chunks) clearChunk(level, c[0], baseY, c[1]);
                }
            });
        });

        AwakenTests.registerIsolated("hearth_why_not_reasons", 600, h -> {
            ServerLevel level = h.getLevel();
            BlockPos o = h.absolutePos(BlockPos.ZERO);
            BlockPos base = new BlockPos((o.getX() & ~15) + 4, o.getY() + 120, (o.getZ() & ~15) + 4);
            UUID owner = UUID.randomUUID();
            HearthRecord rec = new HearthRecord(owner, "test", base.offset(2, 0, -3), HearthService.OVERWORLD);
            HearthData data = HearthData.get(level.getServer());
            BlockState foot = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
            h.succeedWhen(() -> {
                try {
                    // open sky above the test room, whatever other tests left up there
                    BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                    for (int x = -1; x <= 5; x++) for (int z = -1; z <= 5; z++) for (int y = base.getY() + 4; y <= level.getMaxY(); y++) {
                        if (!level.getBlockState(m.set(base.getX() + x, y, base.getZ() + z)).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
                    }
                    level.setBlock(rec.pos, HearthContent.HEARTH_ANCHOR.get().defaultBlockState(), 3);
                    if (data.of(owner).isEmpty()) data.put(rec);
                    // 1. a 5 × 4 × 5 stone room with no roof: it sees the sky
                    shell(level, base, false);
                    level.setBlock(base.offset(1, 1, 1), foot, 3);
                    level.setBlock(base.offset(1, 1, 2), foot.setValue(BedBlock.PART, BedPart.HEAD), 3);
                    level.setBlock(base.offset(3, 1, 3), HearthContent.MEDITATION_MAT.get().defaultBlockState(), 3);
                    HearthService.scanNow(level, rec);
                    Optional<WhyNot.Reason> roofless = WhyNot.of(RoomType.SANCTUARY, rooms(rec, base), misses(rec, base));
                    String rooflessScan = rec.misses.size() + " misses " + rec.misses.stream().limit(4).toList() + ", rooms " + rec.rooms.stream().map(r -> r.types() + "@" + r.min().toShortString()).toList();
                    // 2. roofed, one wall block missing: an opening there
                    shell(level, base, true);
                    BlockPos gap = base.offset(0, 1, 2);
                    level.setBlock(gap, Blocks.AIR.defaultBlockState(), 3);
                    HearthService.scanNow(level, rec);
                    Optional<WhyNot.Reason> opening = WhyNot.of(RoomType.SANCTUARY, rooms(rec, base), misses(rec, base));
                    // 3. closed, but a hole in the roof away from the furniture
                    level.setBlock(gap, Blocks.STONE.defaultBlockState(), 3);
                    BlockPos hole = base.offset(3, 3, 1);
                    level.setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
                    HearthService.scanNow(level, rec);
                    Optional<WhyNot.Reason> roofHole = WhyNot.of(RoomType.SANCTUARY, rooms(rec, base), misses(rec, base));
                    // 4. closed and roofed, the mat gone: missing a Meditation Mat
                    level.setBlock(hole, Blocks.GLASS.defaultBlockState(), 3);
                    level.setBlock(base.offset(3, 1, 3), Blocks.AIR.defaultBlockState(), 3);
                    HearthService.scanNow(level, rec);
                    Optional<WhyNot.Reason> missing = WhyNot.of(RoomType.SANCTUARY, rooms(rec, base), misses(rec, base));
                    // 5. and with the mat back: a Sanctuary, nothing to explain
                    level.setBlock(base.offset(3, 1, 3), HearthContent.MEDITATION_MAT.get().defaultBlockState(), 3);
                    HearthService.scanNow(level, rec);
                    boolean done = rooms(rec, base).stream().anyMatch(r -> r.types().contains(RoomType.SANCTUARY)) && WhyNot.of(RoomType.SANCTUARY, rooms(rec, base), misses(rec, base)).isEmpty();
                    h.assertTrue(roofless.isPresent() && roofless.get().kind() == WhyNot.Kind.SKY, "roofless: sees the sky, was " + roofless + " (" + rooflessScan + ")");
                    h.assertTrue(roofless.get().card().contains("sees the sky") && roofless.get().card().contains("no roof"), "says so: " + roofless.get().card());
                    h.assertTrue(opening.isPresent() && opening.get().kind() == WhyNot.Kind.OPENING && gap.equals(opening.get().at()),
                            "a gap: an opening at " + gap.toShortString() + ", was " + opening);
                    h.assertTrue(opening.get().card().contains(gap.getX() + " " + gap.getY() + " " + gap.getZ()), "names the spot: " + opening.get().card());
                    h.assertTrue(roofHole.isPresent() && roofHole.get().kind() == WhyNot.Kind.ROOF_HOLE && hole.getX() == roofHole.get().at().getX()
                            && hole.getZ() == roofHole.get().at().getZ(), "a hole in the roof there, was " + roofHole);
                    h.assertTrue(missing.isPresent() && missing.get().kind() == WhyNot.Kind.MISSING && missing.get().card().contains("missing: a Meditation Mat"),
                            "missing: a Meditation Mat, was " + missing);
                    h.assertTrue(done, "fixed: a Sanctuary");
                } finally {
                    data.remove(owner);
                    level.removeBlock(rec.pos, false);
                    for (int x = 0; x < 5; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++) {
                        level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            });
        });
    }

    /** A chunk of grass on dirt at {@code y} (the floor level), cleared above (other tests share these heights). */
    private static void grass(ServerLevel level, int cx, int y, int cz) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            level.setBlock(m.set(cx + x, y - 1, cz + z), Blocks.DIRT.defaultBlockState(), 2);
            level.setBlock(m.set(cx + x, y, cz + z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            for (int dy = 1; dy <= 16; dy++) {
                if (!level.getBlockState(m.set(cx + x, y + dy, cz + z)).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    /** Takes a test chunk down to air from the dirt under the floor to 16 above it. */
    private static void clearChunk(ServerLevel level, int cx, int y, int cz) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) for (int dy = -1; dy <= 16; dy++) {
            if (!level.getBlockState(m.set(cx + x, y + dy, cz + z)).isAir()) level.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    /** The local origin that puts the whole turned cottage (overhangs, path, chimney) inside the chunk at cx, cz. */
    private static BlockPos fitInChunk(int cx, int y, int cz, Rotation rot) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        for (int x : new int[]{CottagePlan.MIN_X, CottagePlan.MAX_X}) for (int z : new int[]{CottagePlan.MIN_Z, CottagePlan.MAX_Z}) {
            BlockPos r = new BlockPos(x, 0, z).rotate(rot);
            minX = Math.min(minX, r.getX());
            minZ = Math.min(minZ, r.getZ());
        }
        return new BlockPos(cx + 1 - minX, y, cz + 1 - minZ);
    }

    /** Writes the blocks between lo and hi to run/structures/&lt;name&gt;.json for tools/art/specs/structures.py preview. */
    static void dump(ServerLevel level, String name, BlockPos lo, BlockPos hi) {
        StringBuilder sb = new StringBuilder("{\"name\":\"" + name + "\",\"kind\":\"house\",\"blocks\":[");
        boolean first = true;
        for (BlockPos q : BlockPos.betweenClosed(lo, hi)) {
            BlockState st = level.getBlockState(q);
            if (st.isAir()) continue;
            if (!first) sb.append(',');
            first = false;
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(st.getBlock());
            sb.append('[').append(q.getX() - lo.getX()).append(',').append(q.getY() - lo.getY() - 1).append(',').append(q.getZ() - lo.getZ())
                    .append(",\"").append(id.getNamespace().equals("minecraft") ? id.getPath() : id.toString()).append("\",\"");
            boolean firstProp = true;
            for (net.minecraft.world.level.block.state.properties.Property<?> pr : st.getProperties()) {
                if (!firstProp) sb.append(',');
                firstProp = false;
                sb.append(pr.getName()).append('=').append(value(st, pr));
            }
            sb.append("\"]");
        }
        sb.append("]}");
        try {
            java.nio.file.Path dir = java.nio.file.Path.of("structures");
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Files.writeString(dir.resolve(name + ".json"), sb.toString());
        } catch (java.io.IOException e) {
            dev.awaken.Awaken.LOG.warn("[hearth] could not write {}: {}", name, e.toString());
        }
    }

    private static <T extends Comparable<T>> String value(BlockState s, net.minecraft.world.level.block.state.properties.Property<T> p) {
        return p.getName(s.getValue(p));
    }

    /** Count of {@code f} in a room holding exactly {@code t}'s furniture, minus {@code without}. */
    private static int c(RoomType t, Furniture without, Furniture f) {
        return t.uses(f) && f != without ? 1 : 0;
    }

    /** This test's own rooms and misses (rooms other tests left within the radius don't count). */
    private static List<Room> rooms(HearthRecord rec, BlockPos base) {
        return rec.rooms.stream().filter(r -> ours(r.min(), base)).toList();
    }

    private static List<dev.awaken.hearth.room.Miss> misses(HearthRecord rec, BlockPos base) {
        return rec.misses.stream().filter(m -> ours(m.seed(), base)).toList();
    }

    private static boolean ours(BlockPos p, BlockPos base) {
        return p.getX() >= base.getX() - 1 && p.getX() <= base.getX() + 5 && p.getZ() >= base.getZ() - 1 && p.getZ() <= base.getZ() + 5
                && Math.abs(p.getY() - base.getY()) <= 5;
    }

    /** A 5 × 4 × 5 stone shell (air inside); with a glass roof or open to the sky. */
    private static void shell(ServerLevel level, BlockPos base, boolean roof) {
        for (int x = 0; x < 5; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 5; z++) {
            boolean wall = x == 0 || x == 4 || y == 0 || z == 0 || z == 4;
            BlockPos p = base.offset(x, y, z);
            BlockState here = level.getBlockState(p);
            if (y == 3) level.setBlock(p, roof ? Blocks.GLASS.defaultBlockState() : wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            else if (wall) level.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
            else if (!here.is(net.minecraft.tags.BlockTags.BEDS) && !here.is(HearthContent.MEDITATION_MAT.get())) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** Facts that meet a kind's condition (a glass roof, a big floor, height over the ground, windows round). */
    private static int[] conditionMet(RoomType t) {
        int[] f = new int[dev.awaken.hearth.room.Tally.F_COUNT];
        f[dev.awaken.hearth.room.Tally.F_FLOOR] = 64;
        f[dev.awaken.hearth.room.Tally.F_CEILING] = 10;
        f[dev.awaken.hearth.room.Tally.F_GLASS_CEILING] = 10;
        f[dev.awaken.hearth.room.Tally.F_ELEVATION] = 14;
        f[dev.awaken.hearth.room.Tally.F_WINDOW_DIRS] = 0b1111;
        f[dev.awaken.hearth.room.Tally.F_SHAFT] = RoomType.SHAFT_DEPTH; // (W6) the Mine head's shaft
        return f;
    }
}
