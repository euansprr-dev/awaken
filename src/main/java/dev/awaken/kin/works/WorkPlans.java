package dev.awaken.kin.works;

import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.kin.KinApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Work Plans (SPEC §43.12): what the Thatchbacks build. Each plan is one room round the plan block: a floor, walls with
 * a door toward where you stood and a window each side, a flat roof, and the work's furniture, its rare thing last in the
 * middle where the plan lay. They build it in that order from the chests and barrels within eight blocks of the
 * plan: planks, logs, stone, glass, a door, and whatever furniture it needs. A block already right is counted, never
 * pulled down; anything else in the way is left and built round. Stone and timber, glass and doors are yours to bring.
 * Two plans aren't rooms (SPEC §43.16 stage 3): the Belfry (an open timber belfry, four Bell Beams nine up) and the Bell
 * Tower (stone, an arch and a ladder, six Bell Beams and two Great Beams at the top); when either is done its plan goes.
 */
public final class WorkPlans {
    private WorkPlans() {}

    /** A material a plan's block takes from the chest: what counts, and its words for "it's waiting on". */
    enum Mat {
        PLANKS("planks", s -> s.is(ItemTags.PLANKS)),
        LOGS("logs", s -> s.is(ItemTags.LOGS)),
        STONE("stone (cobblestone, stone or stone bricks)", s -> s.is(Items.COBBLESTONE) || s.is(Items.STONE) || s.is(Items.STONE_BRICKS)
                || s.is(Items.COBBLED_DEEPSLATE) || s.is(Items.DEEPSLATE_BRICKS) || s.is(Items.BRICKS) || s.is(Items.POLISHED_ANDESITE)),
        GLASS("glass", s -> s.is(Tags.BlockItems.GLASS_BLOCKS.item())),
        DOOR("a wooden door", s -> s.is(ItemTags.WOODEN_DOORS)),
        LANTERN("lanterns", s -> s.is(Items.LANTERN)),
        BARREL("barrels", s -> s.is(Items.BARREL)),
        FURNACE("a furnace", s -> s.is(Items.FURNACE)),
        VENT("a vent stone", s -> s.is(WorksContent.VENT_STONE_ITEM.get())),
        PERCH("a Chime Perch", s -> s.is(WorksContent.CHIME_PERCH_ITEM.get())),
        CRUCIBLE("the Crucible", s -> s.is(WorksContent.CRUCIBLE_ITEM.get())),
        DROP("the Drop", s -> s.is(WorksContent.DROP_ITEM.get())),
        LEDGER("a Ledger desk", s -> s.is(WorksContent.LEDGER_ITEM.get())),
        HEART("a lode heart", s -> Block.byItem(s.getItem()) instanceof dev.awaken.delve.LodeHeartBlock),
        LADDER("ladders", s -> s.is(Items.LADDER)),
        BELL_BEAM("Bell Beams", s -> s.is(dev.awaken.bells.BellContent.BELL_BEAM_ITEM.get())),
        GREAT_BEAM("Great Beams", s -> s.is(dev.awaken.bells.BellContent.GREAT_BEAM_ITEM.get()));

        final String words;
        final Predicate<ItemStack> takes;

        Mat(String words, Predicate<ItemStack> takes) {
            this.words = words;
            this.takes = takes;
        }

        /** Whether a block in the world is already this (a plank wall where a plank wall goes). */
        boolean is(BlockState s) {
            Item i = s.getBlock().asItem();
            return i != Items.AIR && takes.test(new ItemStack(i));
        }

        /** Glass and lanterns can be done without: a window becomes wall, a lantern is left out. */
        boolean optional() {
            return this == GLASS || this == LANTERN;
        }
    }

    /** One of the works a plan can be: its room, its shell (stone or timber), its half sizes and furniture. */
    public record Plan(String id, String title, RoomType room, boolean stone, int hw, int hd, List<Fit> furniture, String says) {}

    /** A piece of furniture at (right, up, forward) from the middle, facing forward (toward the door) or back. */
    record Fit(int r, int u, int f, Mat mat) {}

    static final List<Plan> PLANS = List.of(
            new Plan("ore_shed", "Ore Shed", RoomType.MINE_HEAD, true, 3, 3, List.of(new Fit(2, 0, -2, Mat.BARREL), new Fit(-2, 0, -2, Mat.BARREL),
                    new Fit(0, 0, 0, Mat.HEART)), "a stone room with a lode heart in the middle and two barrels"),
            new Plan("smeltery", "Smeltery", RoomType.SMELTERY, true, 3, 3, List.of(new Fit(-2, 0, -2, Mat.FURNACE), new Fit(2, 0, -2, Mat.BARREL),
                    new Fit(0, 0, 0, Mat.VENT)), "a stone room with a vent stone in the middle, a furnace and a barrel"),
            new Plan("rookery", "Rookery", RoomType.ROOKERY, false, 3, 3, List.of(new Fit(-2, 3, 0, Mat.LANTERN), new Fit(2, 3, 0, Mat.LANTERN),
                    new Fit(0, 0, 0, Mat.PERCH)), "a timber room with a Chime Perch in the middle"),
            new Plan("foundry", "Foundry", RoomType.FOUNDRY, true, 3, 3, List.of(new Fit(2, 0, -2, Mat.BARREL), new Fit(-2, 0, -2, Mat.BARREL),
                    new Fit(0, 0, 0, Mat.CRUCIBLE)), "a stone room with the Crucible in the middle and barrels for the metal"),
            new Plan("storehouse", "Storehouse", RoomType.STOREHOUSE, false, 3, 4, List.of(
                    new Fit(-2, 0, -3, Mat.BARREL), new Fit(-1, 0, -3, Mat.BARREL), new Fit(1, 0, -3, Mat.BARREL), new Fit(2, 0, -3, Mat.BARREL),
                    new Fit(-2, 0, -1, Mat.BARREL), new Fit(2, 0, -1, Mat.BARREL), new Fit(-2, 0, 1, Mat.BARREL), new Fit(2, 0, 1, Mat.BARREL),
                    new Fit(-2, 3, -2, Mat.LANTERN), new Fit(2, 3, -2, Mat.LANTERN), new Fit(-2, 3, 2, Mat.LANTERN), new Fit(2, 3, 2, Mat.LANTERN),
                    new Fit(-2, 0, 3, Mat.LEDGER), new Fit(0, 0, 0, Mat.DROP)),
                    "a long timber hall with the Drop in the middle under a skylight, eight barrels for bays and the Ledger by the door"),
            new Plan("belfry", "Belfry", RoomType.BELL_TOWER, false, 2, 2, List.of(),
                    "an open timber belfry: four posts, a ladder, a roof, and four Bell Beams nine up, sixteen hooks for your bells"),
            new Plan("bell_tower", "Bell Tower", RoomType.BELL_TOWER, true, 2, 2, List.of(),
                    "a stone tower seventeen high with an arch and a ladder, six Bell Beams and two Great Beams at the top"));

    /** The plans that are a tower round their block, not a room: built up from it, and the plan goes when they're done. */
    static boolean tower(Plan p) {
        return p.id().equals("belfry") || p.id().equals("bell_tower");
    }

    static Plan plan(String id) {
        for (Plan p : PLANS) if (p.id().equals(id)) return p;
        return PLANS.get(1);
    }

    /** One block of a plan: where, of what, and facing which way (for furniture and doors). */
    record Entry(BlockPos pos, Mat mat, Direction facing, boolean door, boolean centre) {}

    /** The plan laid out round its block: floor, walls (door and windows), roof, then furniture, the middle last. */
    static List<Entry> layout(BlockPos c, Direction fwd, Plan p) {
        if (p.id().equals("belfry")) return belfry(c, fwd);
        if (p.id().equals("bell_tower")) return bellTower(c, fwd);
        Direction right = fwd.getClockWise();
        List<Entry> out = new ArrayList<>();
        Mat wall = p.stone() ? Mat.STONE : Mat.PLANKS, floor = p.stone() ? Mat.STONE : Mat.PLANKS;
        int hw = p.hw(), hd = p.hd();
        for (int f = -hd + 1; f <= hd - 1; f++)
            for (int r = -hw + 1; r <= hw - 1; r++) out.add(new Entry(at(c, right, fwd, r, -1, f), floor, fwd, false, false));
        for (int u = 0; u <= 3; u++)
            for (int f = -hd; f <= hd; f++)
                for (int r = -hw; r <= hw; r++) {
                    boolean edgeR = Math.abs(r) == hw, edgeF = Math.abs(f) == hd;
                    if (!edgeR && !edgeF) continue;
                    if (f == hd && r == 0 && u <= 1) {
                        if (u == 0) out.add(new Entry(at(c, right, fwd, r, u, f), Mat.DOOR, fwd, true, false));
                        continue;
                    }
                    Mat m = edgeR && edgeF ? Mat.LOGS : (u == 1 || u == 2) && ((edgeR && f == 0) || (edgeF && f == -hd && r == 0)) ? Mat.GLASS : wall;
                    out.add(new Entry(at(c, right, fwd, r, u, f), m, fwd, false, false));
                }
        for (int f = -hd; f <= hd; f++)
            for (int r = -hw; r <= hw; r++) {
                // the Storehouse's skylight over the Drop
                Mat m = p.room() == RoomType.STOREHOUSE && r == 0 && Math.abs(f) <= 1 ? Mat.GLASS : p.stone() ? Mat.STONE : Mat.PLANKS;
                out.add(new Entry(at(c, right, fwd, r, 4, f), m, fwd, false, false));
            }
        for (Fit fit : p.furniture()) {
            boolean centre = fit.r() == 0 && fit.u() == 0 && fit.f() == 0;
            out.add(new Entry(at(c, right, fwd, fit.r(), fit.u(), fit.f()), fit.mat(), fit.f() > 0 ? fwd.getOpposite() : fwd, false, centre));
        }
        return out;
    }

    /**
     * The Belfry, five by five round the plan: corner posts ten high with a ladder up the back left one, a beam ring and
     * cross beams on top, a Bell Beam under each arm of the cross nine up (each beam's hooks along its arm), a stepped roof
     * and its cap last.
     */
    static List<Entry> belfry(BlockPos c, Direction fwd) {
        Direction right = fwd.getClockWise();
        List<Entry> out = new ArrayList<>();
        for (int u = 0; u <= 10; u++)
            for (int r = -2; r <= 2; r += 4)
                for (int f = -2; f <= 2; f += 4) out.add(new Entry(at(c, right, fwd, r, u, f), Mat.LOGS, fwd, false, false));
        for (int u = 0; u <= 9; u++) out.add(new Entry(at(c, right, fwd, -1, u, -2), Mat.LADDER, right, false, false));
        for (int f = -2; f <= 2; f++)
            for (int r = -2; r <= 2; r++) {
                boolean corner = Math.abs(r) == 2 && Math.abs(f) == 2, edge = Math.abs(r) == 2 || Math.abs(f) == 2;
                if (!corner && (edge || r == 0 || f == 0)) out.add(new Entry(at(c, right, fwd, r, 10, f), Mat.PLANKS, fwd, false, false));
            }
        for (int k = -1; k <= 1; k += 2) {
            out.add(new Entry(at(c, right, fwd, k, 9, 0), Mat.BELL_BEAM, fwd, false, false));
            out.add(new Entry(at(c, right, fwd, 0, 9, k), Mat.BELL_BEAM, right, false, false));
        }
        roofs(out, c, right, fwd, 11, Mat.PLANKS, Mat.LOGS);
        return out;
    }

    /**
     * The Bell Tower, five by five round the plan: stone walls ten high with an arch three wide on the door side and a
     * window up each face, a ladder up the inside of the back wall through the deck, the deck, corner piers, the beam
     * course thirteen up (two Great Beams either side of the middle, six Bell Beams round them), a stepped stone roof.
     */
    static List<Entry> bellTower(BlockPos c, Direction fwd) {
        Direction right = fwd.getClockWise();
        List<Entry> out = new ArrayList<>();
        for (int u = 0; u <= 10; u++)
            for (int f = -2; f <= 2; f++)
                for (int r = -2; r <= 2; r++) {
                    if (Math.abs(r) != 2 && Math.abs(f) != 2) continue;
                    if (f == 2 && Math.abs(r) <= 1 && u <= 2) continue; // the arch
                    boolean window = (u == 5 || u == 6) && ((r == 0 && f != 2) || f == 0);
                    out.add(new Entry(at(c, right, fwd, r, u, f), window ? Mat.GLASS : Mat.STONE, fwd, false, false));
                }
        for (int u = 0; u <= 10; u++) out.add(new Entry(at(c, right, fwd, 0, u, -1), Mat.LADDER, fwd, false, false));
        for (int f = -1; f <= 1; f++)
            for (int r = -1; r <= 1; r++) if (!(r == 0 && f == -1)) out.add(new Entry(at(c, right, fwd, r, 10, f), Mat.PLANKS, fwd, false, false));
        for (int u = 11; u <= 12; u++)
            for (int r = -2; r <= 2; r += 4)
                for (int f = -2; f <= 2; f += 4) out.add(new Entry(at(c, right, fwd, r, u, f), Mat.STONE, fwd, false, false));
        for (int f = -2; f <= 2; f++)
            for (int r = -2; r <= 2; r++) {
                BlockPos p = at(c, right, fwd, r, 13, f);
                if (Math.abs(r) == 2 || Math.abs(f) == 2) out.add(new Entry(p, Mat.STONE, fwd, false, false));
                else if (r != 0 && f == 0) out.add(new Entry(p, Mat.GREAT_BEAM, fwd, false, false));
                else if (r != 0 || f != 0) out.add(new Entry(p, Mat.BELL_BEAM, r == 0 ? right : fwd, false, false));
                else out.add(new Entry(p, Mat.STONE, fwd, false, false));
            }
        roofs(out, c, right, fwd, 14, Mat.STONE, Mat.STONE);
        return out;
    }

    /** A stepped roof from {@code u}: five by five, three by three, then its cap in the middle (the plan's last block). */
    private static void roofs(List<Entry> out, BlockPos c, Direction right, Direction fwd, int u, Mat roof, Mat cap) {
        for (int h = 2; h >= 1; h--)
            for (int f = -h; f <= h; f++)
                for (int r = -h; r <= h; r++) out.add(new Entry(at(c, right, fwd, r, u + 2 - h, f), roof, fwd, false, false));
        out.add(new Entry(at(c, right, fwd, 0, u + 2, 0), cap, fwd, false, true));
    }

    private static BlockPos at(BlockPos c, Direction right, Direction fwd, int r, int u, int f) {
        return c.relative(right, r).relative(fwd, f).above(u);
    }

    // ---- the plans that stand in the world (server side, by position) ------------------------------------------------------

    private static final Set<BlockPos> STANDING = new HashSet<>();

    static void loaded(WorkPlanBlockEntity be) {
        STANDING.add(be.getBlockPos().immutable());
    }

    static void gone(WorkPlanBlockEntity be) {
        STANDING.remove(be.getBlockPos());
    }

    public static void serverStopped() {
        STANDING.clear();
    }

    /** The nearest plan standing within {@code r} of {@code at}, or null. */
    public static @Nullable WorkPlanBlockEntity near(ServerLevel level, BlockPos at, int r) {
        WorkPlanBlockEntity best = null;
        double bd = (double) r * r;
        for (BlockPos p : List.copyOf(STANDING)) {
            double d = p.distSqr(at);
            if (d > bd || !dev.awaken.core.api.Ready.loaded(level, p)) continue;
            if (level.getBlockEntity(p) instanceof WorkPlanBlockEntity be) {
                best = be;
                bd = d;
            } else STANDING.remove(p);
        }
        return best;
    }

    // ---- the player's side ---------------------------------------------------------------------------------------------

    static void choose(ServerLevel level, ServerPlayer p, WorkPlanBlockEntity be) {
        if (be.step > 0) {
            say(p, "[The building's started. Break the plan and lay it again to change it.]");
            return;
        }
        int i = 0;
        for (int k = 0; k < PLANS.size(); k++) if (PLANS.get(k).id().equals(be.work)) i = k;
        Plan next = PLANS.get((i + 1) % PLANS.size());
        be.work = next.id();
        be.setChanged();
        level.playSound(null, be.getBlockPos(), SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 0.8F, 1F);
        say(p, "[The plan is for " + aWork(next) + " now: " + next.says() + ".]");
    }

    static void status(ServerLevel level, ServerPlayer p, WorkPlanBlockEntity be) {
        Plan plan = plan(be.work);
        Direction fwd = be.getBlockState().getValue(WorkPlanBlock.FACING);
        List<Entry> all = layout(be.getBlockPos(), fwd, plan);
        StringBuilder b = new StringBuilder("[").append(capital(aWork(plan))).append(" plan: ").append(plan.says()).append(". ");
        if (be.step == 0) {
            // the bill, counting only what isn't already standing
            Map<Mat, Integer> bill = new EnumMap<>(Mat.class);
            for (Entry e : all) if (!done(level, e)) bill.merge(e.mat(), 1, Integer::sum);
            List<String> parts = new ArrayList<>();
            bill.forEach((m, n) -> parts.add((m.optional() ? "up to " : "") + n + " " + m.words));
            b.append("It needs ").append(String.join(", ", parts)).append(". Leave them in a chest within eight blocks.");
        } else {
            b.append(be.step).append(" of ").append(all.size()).append(" blocks done.");
            if (!be.waiting.isEmpty()) b.append(" It's waiting on ").append(be.waiting).append(".");
        }
        if (KinApi.livingWith(level, p, dev.awaken.kin.KinKind.THATCHBACK) == 0)
            b.append(" No Thatchback lives with you to build it; you can build ").append(tower(plan) ? "it" : "the room").append(" yourself.");
        say(p, b.append("]").toString());
    }

    /**
     * A whole plan raised at once (admin and tours): the plan set down, a chest to its right stocked with its bill, built
     * to the end the way a Thatchback would, then the chest taken away. False if it stopped short.
     */
    public static boolean raise(ServerLevel level, BlockPos c, Direction fwd, String work) {
        Plan p = plan(work);
        level.setBlock(c, WorksContent.WORK_PLAN.get().defaultBlockState().setValue(WorkPlanBlock.FACING, fwd), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(c) instanceof WorkPlanBlockEntity be)) return false;
        be.work = p.id();
        List<Entry> all = layout(c, fwd, p);
        Map<Mat, Integer> bill = new EnumMap<>(Mat.class);
        for (Entry e : all) bill.merge(e.mat(), 1, Integer::sum);
        BlockPos at = c.relative(fwd.getClockWise(), p.hw() + 2);
        level.setBlock(at, Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(at) instanceof Container chest)) return false;
        int slot = 0;
        for (Map.Entry<Mat, Integer> b : bill.entrySet()) {
            ItemStack one = new ItemStack(stock(b.getKey()));
            for (int n = b.getValue(); n > 0 && slot < chest.getContainerSize(); n -= one.getMaxStackSize())
                chest.setItem(slot++, one.copyWithCount(Math.min(n, one.getMaxStackSize())));
        }
        for (int i = 0; i < 1000 && build(level, be); i++) {}
        chest.clearContent();
        level.removeBlock(at, false);
        return be.step >= all.size();
    }

    /** What a raised plan is built of: stone bricks and spruce, as a careful builder would. */
    private static Item stock(Mat m) {
        return switch (m) {
            case PLANKS -> Items.SPRUCE_PLANKS;
            case LOGS -> Items.SPRUCE_LOG;
            case STONE -> Items.STONE_BRICKS;
            case GLASS -> Items.GLASS;
            case DOOR -> Items.SPRUCE_DOOR;
            case LANTERN -> Items.LANTERN;
            case BARREL -> Items.BARREL;
            case FURNACE -> Items.FURNACE;
            case VENT -> WorksContent.VENT_STONE_ITEM.get();
            case PERCH -> WorksContent.CHIME_PERCH_ITEM.get();
            case CRUCIBLE -> WorksContent.CRUCIBLE_ITEM.get();
            case DROP -> WorksContent.DROP_ITEM.get();
            case LEDGER -> WorksContent.LEDGER_ITEM.get();
            case HEART -> dev.awaken.delve.DelveContent.HEARTS.get(dev.awaken.delve.Ore.IRON).get().asItem();
            case LADDER -> Items.LADDER;
            case BELL_BEAM -> dev.awaken.bells.BellContent.BELL_BEAM_ITEM.get();
            case GREAT_BEAM -> dev.awaken.bells.BellContent.GREAT_BEAM_ITEM.get();
        };
    }

    // ---- a Thatchback's turn ---------------------------------------------------------------------------------------------

    /** One block of the plan placed (or found right), from the chest. False when it's waiting on something or done. */
    public static boolean build(ServerLevel level, WorkPlanBlockEntity be) {
        Plan plan = plan(be.work);
        BlockPos c = be.getBlockPos();
        Direction fwd = be.getBlockState().getValue(WorkPlanBlock.FACING);
        List<Entry> all = layout(c, fwd, plan);
        while (be.step < all.size()) {
            Entry e = all.get(be.step);
            if (done(level, e) || blocked(level, e)) {
                be.step++;
                continue;
            }
            ItemStack took = takeNear(level, c, e.mat());
            Mat used = e.mat();
            if (took.isEmpty() && e.mat() == Mat.GLASS) {
                // no glass: the window is walled up
                used = plan.stone() ? Mat.STONE : Mat.PLANKS;
                took = takeNear(level, c, used);
            }
            if (took.isEmpty() && e.mat().optional()) {
                be.step++;
                continue;
            }
            if (took.isEmpty()) {
                be.waiting = used.words;
                return false;
            }
            be.waiting = "";
            place(level, e, took, be);
            be.step++;
            be.setChanged();
            if (e.centre()) finished(level, plan, c);
            return true;
        }
        // a tower's last block was already there: it's done all the same
        if (tower(plan) && level.getBlockState(c).is(WorksContent.WORK_PLAN.get())) finished(level, plan, c);
        return false;
    }

    private static boolean done(ServerLevel level, Entry e) {
        BlockState s = level.getBlockState(e.pos());
        if (e.mat() == Mat.GLASS) return Mat.GLASS.is(s) || Mat.STONE.is(s) || Mat.PLANKS.is(s);
        return e.mat().is(s);
    }

    /** Something of yours already stands there (not air, plants or the plan itself): built round. */
    private static boolean blocked(ServerLevel level, Entry e) {
        if (e.centre()) return false;
        BlockState s = level.getBlockState(e.pos());
        if (e.door() && !level.getBlockState(e.pos().above()).canBeReplaced()) return true;
        return !s.canBeReplaced() && !floorSoil(e, s);
    }

    /** The floor replaces the ground it's laid on (grass, dirt, sand), never what you built. */
    private static boolean floorSoil(Entry e, BlockState s) {
        return e.mat() != Mat.DOOR && e.mat() != Mat.LANTERN && (s.is(net.minecraft.tags.BlockTags.DIRT) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL));
    }

    /**
     * One of what a block takes, from the nearest chest or barrel within eight blocks that has it. Not just the nearest
     * one: the plan's own barrels go up before its middle, and they'd hide the chest you filled.
     */
    private static ItemStack takeNear(ServerLevel level, BlockPos c, Mat m) {
        Container best = null;
        int slot = -1;
        double bd = 65;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 3; dy++)
            for (int dx = -8; dx <= 8; dx++)
                for (int dz = -8; dz <= 8; dz++) {
                    double d = dx * dx + dz * dz + dy * dy;
                    if (d >= bd) continue;
                    at.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    BlockState st = level.getBlockState(at);
                    if (!st.is(Blocks.CHEST) && !st.is(Blocks.BARREL) && !st.is(Blocks.TRAPPED_CHEST)) continue;
                    if (!(level.getBlockEntity(at) instanceof Container ct)) continue;
                    for (int i = 0; i < ct.getContainerSize(); i++)
                        if (!ct.getItem(i).isEmpty() && m.takes.test(ct.getItem(i))) {
                            best = ct;
                            slot = i;
                            bd = d;
                            break;
                        }
                }
        if (best == null) return ItemStack.EMPTY;
        ItemStack one = best.getItem(slot).split(1);
        best.setChanged();
        return one;
    }

    private static void place(ServerLevel level, Entry e, ItemStack item, WorkPlanBlockEntity be) {
        Block block = Block.byItem(item.getItem());
        BlockState s = block.defaultBlockState();
        if (e.door()) {
            BlockState lower = s.setValue(DoorBlock.FACING, e.facing().getOpposite()).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            level.setBlock(e.pos(), lower, Block.UPDATE_ALL);
            level.setBlock(e.pos().above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        } else {
            if (s.hasProperty(HorizontalDirectionalBlock.FACING)) s = s.setValue(HorizontalDirectionalBlock.FACING, e.facing());
            if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, e.facing());
            if (block instanceof LanternBlock) s = s.setValue(LanternBlock.HANGING, true);
            level.setBlock(e.pos(), s, Block.UPDATE_ALL);
            if (e.mat() == Mat.BELL_BEAM || e.mat() == Mat.GREAT_BEAM) dev.awaken.core.api.BellHooks.raised(level, e.pos());
        }
        var sound = s.getSoundType();
        level.playSound(null, e.pos(), sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1F) / 2F, sound.getPitch() * 0.9F);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, e.pos().getX() + 0.5, e.pos().getY() + 0.5, e.pos().getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.01);
    }

    private static void finished(ServerLevel level, Plan plan, BlockPos c) {
        // a belfry or tower stands round its plan: the plan goes, and the ground under it is clear again
        if (tower(plan) && level.getBlockState(c).is(WorksContent.WORK_PLAN.get())) level.removeBlock(c, false);
        HearthService.data(level.getServer()).containing(c, HearthService.OVERWORLD).ifPresent(h -> {
            h.scheduleScan(level.getGameTime(), 20);
            for (ServerPlayer p : level.players())
                if (h.belongs(p.getUUID())) say(p, "[The Thatchbacks finished the " + plan.title() + "." + (tower(plan) ? " Climb the ladder and hang your bells from its beams." : "") + "]");
        });
    }

    private static String aWork(Plan p) {
        return (p.id().equals("ore_shed") ? "an " : "a ") + p.title();
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    static void say(ServerPlayer p, String line) {
        p.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** For the Hearth page: every plan at a home with what it's waiting on. */
    public static List<String> lines(ServerLevel level, BlockPos anchor, int radius) {
        List<String> out = new ArrayList<>();
        for (BlockPos p : List.copyOf(STANDING)) {
            if (p.distSqr(anchor) > (double) radius * radius || !(level.getBlockEntity(p) instanceof WorkPlanBlockEntity be)) continue;
            Plan plan = plan(be.work);
            out.add("The " + plan.title() + " plan: " + (be.step == 0 ? "not started" : be.step + " blocks done")
                    + (be.waiting.isEmpty() ? "." : ", waiting on " + be.waiting + "."));
        }
        return out;
    }
}
