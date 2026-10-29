package dev.awaken.kin;

import dev.awaken.bells.BellCatalog;
import dev.awaken.bells.Bells;
import dev.awaken.delve.DelveContent;
import dev.awaken.hearth.Hearth;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.kin.works.ChimePerchBlock;
import dev.awaken.kin.works.CrucibleBlock;
import dev.awaken.kin.works.DropBlock;
import dev.awaken.kin.works.DropBlockEntity;
import dev.awaken.kin.works.Storehouse;
import dev.awaken.kin.works.VentStoneBlock;
import dev.awaken.kin.works.WorkPlanBlockEntity;
import dev.awaken.kin.works.WorkPlans;
import dev.awaken.kin.works.WorkRooms;
import dev.awaken.kin.works.WorksContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The second eight's work at home (SPEC §43.12; JOURNEY part 3). Totters carry: they shelve what's tipped into the
 * Storehouse's Drop, fetch to its Ledger, and take the Smeltery's ingots to the Forge and the fields' harvest to the
 * kitchen. Thatchbacks build works from their plans a block at a time, from what's left in the chest by the plan.
 * Emberkits smelt the ore in the Smeltery's chests with no fuel, and heat the Foundry's crucible to cast the Deep's bells.
 * Puffwools give their wool when brushed, Dumplins leave eggs, Pondlings fish, Snufflets forage, and Chimewrens in a
 * Rookery sing a note the song is missing. Nothing comes from nothing: every ingot was ore you mined, every bell's metal
 * you brought.
 */
public final class KinWorks {
    private KinWorks() {}

    /** Errands: going to take something, carrying it to put it away, and the same for a Ledger fetch. */
    static final int NONE = 0, TO_TAKE = 1, TO_PUT = 2, FETCH_TAKE = 3, FETCH_PUT = 4;
    /** A Totter's armful off the heap: up to this many stacks. Between works it carries one stack. */
    static final int ARMFUL = 3;
    /** A fetch that hasn't reached the desk in this long is set there anyway (the Totter couldn't find a way). */
    static final int FETCH_PATIENCE = 20 * 60;
    /** An Emberkit smelts one item this often (ticks); each SMELT bell at home takes 15 off, never under 40. */
    static final int SMELT_EVERY = 100, SMELT_FASTEST = 40, SMELT_BELL = 15;
    /** A Thatchback places one block of a plan this often, from a plan within this reach of the Anchor. */
    static final int BUILD_EVERY = 10, PLAN_REACH = 64;
    /** Pondlings fish and Snufflets forage once in this many ticks, so many a day; Dumplins lay this many eggs a day. */
    static final int FISH_EVERY = 1200, FISH_DAY = 4, FORAGE_EVERY = 1200, FORAGE_DAY = 4, EGG_EVERY = 2400, EGGS_DAY = 2;
    /** A Puffwool's wool, once a day, when it's brushed. */
    public static final int WOOL = 3;
    /** A bell is cast over this many ticks; half that with a Thatchback at home to work the moulds. */
    static final int CAST_TICKS = 600;
    /** How much of a seed crop the fields' chest keeps back for planting (the Totter takes only what's over). */
    static final int SEED_KEEP = 32;

    static void work(ServerLevel level, KinBeast b) {
        if (KinHome.every(b, 1200)) renook(level, b);
        switch (b.kind()) {
            case TOTTER -> totter(level, b);
            case THATCHBACK -> thatchback(level, b);
            case EMBERKIT -> emberkit(level, b);
            case PUFFWOOL -> potter(b, 5);
            case DUMPLIN -> dumplin(level, b);
            case PONDLING -> pondling(level, b);
            case SNUFFLET -> snufflet(level, b);
            case CHIMEWREN -> chimewren(level, b);
            default -> {}
        }
    }

    /** A better nook since it came (a Smeltery built for the Emberkit, a Storehouse for the Totter): it moves in. */
    private static void renook(ServerLevel level, KinBeast b) {
        if (b.home == null || b.nook == null) return;
        Optional<Hearth.HearthInfo> h = Hearth.hearthAt(level, b.home);
        if (h.isEmpty()) return;
        BlockPos better = KinHome.nookFor(level, h.get(), b.kind());
        if (better == null || better.equals(b.nook)) return;
        b.nook = better.immutable();
        b.setHomeTo(better, 24);
        KinWorld.get(level.getServer()).home(b.home, new KinWorld.Homed(b.getUUID(), b.kind(), better, level.getGameTime()));
    }

    // ---- getting about -------------------------------------------------------------------------------------------------

    private static boolean near(KinBeast b, BlockPos at) {
        return b.distanceToSqr(Vec3.atCenterOf(at)) < 7;
    }

    private static void walk(KinBeast b, BlockPos at, double speed) {
        if (b.getNavigation().isDone() || KinHome.every(b, 20)) b.getNavigation().moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, speed);
    }

    /** Pottering round its nook: now and then a few steps somewhere near it. */
    private static void potter(KinBeast b, int r) {
        if (b.nook == null) return;
        if (b.distanceToSqr(Vec3.atCenterOf(b.nook)) > (r + 3) * (r + 3)) {
            walk(b, b.nook, 0.8);
            return;
        }
        if (KinHome.every(b, 200) && b.getNavigation().isDone()) {
            BlockPos to = b.nook.offset(b.getRandom().nextInt(2 * r + 1) - r, 0, b.getRandom().nextInt(2 * r + 1) - r);
            b.getNavigation().moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5, 0.6);
        }
    }

    private static ItemStack putOrDrop(ServerLevel level, KinBeast b, BlockPos near, ItemStack s) {
        Container c = KinHome.chest(level, near);
        ItemStack left = c == null ? s : KinHome.put(c, s);
        if (!left.isEmpty()) b.spawnAtLocation(level, left);
        return ItemStack.EMPTY;
    }

    // ---- Totters ---------------------------------------------------------------------------------------------------------

    private static void totter(ServerLevel level, KinBeast b) {
        long now = level.getGameTime();
        switch (b.task) {
            case FETCH_TAKE, FETCH_PUT -> {
                fetching(level, b, now);
                return;
            }
            case TO_TAKE -> {
                if (b.taskFrom == null) {
                    b.task = NONE;
                    return;
                }
                if (!near(b, b.taskFrom)) {
                    walk(b, b.taskFrom, 1.0);
                    return;
                }
                takeUp(level, b);
                return;
            }
            default -> {}
        }
        if (!b.carry.isEmpty()) {
            carryAway(level, b, now);
            return;
        }
        if (now < b.taskUntil || !KinHome.every(b, 20)) {
            if (now < b.taskUntil) KinHome.goTo(b, b.nook, 0.8);
            return;
        }
        // the next job: the Drop's heap first, then the works' chests
        Room store = WorkRooms.room(level, b.home, RoomType.STOREHOUSE);
        BlockPos drop = store == null ? null : WorkRooms.heart(level, store, DropBlock.class);
        if (drop != null && level.getBlockEntity(drop) instanceof DropBlockEntity be && be.size() > 0) {
            errand(b, drop, null, null);
            return;
        }
        if (between(level, b, drop)) return;
        b.held(ItemStack.EMPTY);
        KinHome.goTo(b, b.nook, 0.8);
    }

    private static void errand(KinBeast b, BlockPos from, @Nullable BlockPos to, @Nullable Item item) {
        b.task = TO_TAKE;
        b.taskFrom = from;
        b.taskTo = to;
        b.taskItem = item;
    }

    /** At the thing it went for: an armful off the heap, or a stack out of a work's chest. */
    private static void takeUp(ServerLevel level, KinBeast b) {
        BlockPos from = b.taskFrom;
        b.task = NONE;
        if (from == null) return;
        if (level.getBlockEntity(from) instanceof DropBlockEntity be) {
            for (int i = 0; i < ARMFUL && be.size() > 0; i++) {
                ItemStack s = be.take();
                if (!s.isEmpty()) b.carry.add(s);
            }
        } else if (b.taskItem != null && WorkRooms.container(level, from) instanceof Container c) {
            ItemStack s = takeUpTo(c, b.taskItem, spare(c, b.taskItem));
            if (!s.isEmpty()) b.carry.add(s);
        }
        if (b.carry.isEmpty()) {
            b.taskTo = null;
            return;
        }
        b.held(b.carry.getFirst());
        b.act(KinBeast.CARRY, true);
        b.task = TO_PUT;
    }

    /** Puts away what it carries: to the place it was taking it, else each stack to its bay, else the nearest chest. */
    private static void carryAway(ServerLevel level, KinBeast b, long now) {
        ItemStack s = b.carry.getFirst();
        BlockPos to = b.taskTo;
        Room store = null;
        if (to == null) {
            store = WorkRooms.room(level, b.home, RoomType.STOREHOUSE);
            if (store != null) {
                to = Storehouse.bayFor(level, store, s);
                if (to == null) {
                    // the hall's full: back on the heap, and a rest before trying again (the Hearth page asks for a bay)
                    BlockPos drop = WorkRooms.heart(level, store, DropBlock.class);
                    if (drop != null && level.getBlockEntity(drop) instanceof DropBlockEntity be)
                        for (int i = b.carry.size() - 1; i >= 0; i--) {
                            ItemStack left = be.add(b.carry.get(i));
                            if (!left.isEmpty()) b.spawnAtLocation(level, left);
                        }
                    else for (ItemStack c : b.carry) b.spawnAtLocation(level, c);
                    b.carry.clear();
                    b.held(ItemStack.EMPTY);
                    b.task = NONE;
                    b.taskUntil = now + 1200;
                    return;
                }
            } else to = b.nook;
        }
        if (to == null) return;
        if (!near(b, to)) {
            b.act(KinBeast.CARRY);
            walk(b, to, 1.0);
            return;
        }
        ItemStack left;
        if (level.getBlockEntity(to) instanceof DropBlockEntity be) left = be.add(s);
        else if (store != null) left = Storehouse.shelve(level, to, s);
        else if (WorkRooms.container(level, to) instanceof Container c) left = KinHome.put(c, s);
        else left = putOrDrop(level, b, to, s);
        b.carry.removeFirst();
        if (!left.isEmpty()) {
            // what didn't fit goes round again, to wherever has room
            b.carry.add(left);
            b.taskTo = null;
        } else b.made += s.getCount();
        if (b.carry.isEmpty()) {
            b.taskTo = null;
            b.task = NONE;
            b.held(ItemStack.EMPTY);
            b.act(KinBeast.WORK, true);
        } else b.held(b.carry.getFirst());
    }

    /**
     * Between works (the automation ladder's C): the Smeltery's ingots to a chest in the Forge, the fields' harvest to the
     * kitchen's, or either to the Storehouse's Drop when the home has no such room. True when it set off with a job.
     */
    private static boolean between(ServerLevel level, KinBeast b, @Nullable BlockPos drop) {
        Room smeltery = WorkRooms.room(level, b.home, RoomType.SMELTERY);
        if (smeltery != null) {
            BlockPos to = destination(level, b, RoomType.ARMORY, drop);
            if (to != null && job(level, b, WorkRooms.containers(level, smeltery), KinWorks::smelted, to)) return true;
        }
        List<BlockPos> fields = new ArrayList<>();
        for (KinWorld.Homed m : KinWorld.get(level.getServer()).at(b.home))
            if (m.kind() == KinKind.BURROWBUN && KinHome.chest(level, m.nook()) instanceof BlockEntity be) fields.add(be.getBlockPos());
        if (!fields.isEmpty()) {
            BlockPos to = destination(level, b, RoomType.KITCHEN, drop);
            return to != null && job(level, b, fields, s -> s.is(Tags.Items.CROPS) || s.has(net.minecraft.core.component.DataComponents.FOOD), to);
        }
        return false;
    }

    private static @Nullable BlockPos destination(ServerLevel level, KinBeast b, RoomType room, @Nullable BlockPos drop) {
        Room r = WorkRooms.room(level, b.home, room);
        if (r != null) for (BlockPos p : WorkRooms.containers(level, r)) return p;
        return drop;
    }

    private static boolean job(ServerLevel level, KinBeast b, List<BlockPos> from, Predicate<ItemStack> what, BlockPos to) {
        for (BlockPos p : from) {
            if (p.equals(to)) continue;
            Container c = WorkRooms.container(level, p);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty() || !what.test(s) || spare(c, s.getItem()) <= 0) continue;
                errand(b, p, to, s.getItem());
                return true;
            }
        }
        return false;
    }

    /** What the Smeltery made: ingots, nuggets and gems (never the ore still waiting). */
    private static boolean smelted(ItemStack s) {
        return s.is(Tags.Items.INGOTS) || s.is(Tags.Items.NUGGETS) || s.is(Tags.Items.GEMS);
    }

    /** How much of an item a chest can spare: all of it, but seed crops keep {@link #SEED_KEEP} back for planting. */
    private static int spare(Container c, Item item) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return Block.byItem(item) instanceof CropBlock ? n - SEED_KEEP : n;
    }

    private static ItemStack takeUpTo(Container c, Item item, int max) {
        ItemStack out = ItemStack.EMPTY;
        int want = Math.min(max, new ItemStack(item).getMaxStackSize());
        for (int i = 0; i < c.getContainerSize() && want > 0; i++) {
            ItemStack s = c.getItem(i);
            if (!s.is(item) || (!out.isEmpty() && !ItemStack.isSameItemSameComponents(out, s))) continue;
            int n = Math.min(want, s.getCount());
            if (out.isEmpty()) out = s.copyWithCount(n);
            else out.grow(n);
            s.shrink(n);
            want -= n;
        }
        c.setChanged();
        return out;
    }

    // ---- the Ledger's fetch ------------------------------------------------------------------------------------------------

    /**
     * From the Ledger: the home's nearest awake Totter goes to the shelf, takes the thing and brings it to the desk. False
     * when there's no Totter to send (none lives here, it's night, or none is loaded), and the Storehouse sets it out itself.
     */
    public static boolean fetch(ServerLevel level, ServerPlayer p, BlockPos shelf, BlockPos desk, Item item) {
        if (KinWatch.night(level)) return false;
        Optional<HearthRecord> h = HearthService.data(level.getServer()).containing(desk, HearthService.OVERWORLD);
        if (h.isEmpty()) return false;
        KinBeast best = null;
        for (KinBeast t : KinApi.loaded(level, h.get().pos, KinKind.TOTTER)) {
            if (t.task == FETCH_TAKE || t.task == FETCH_PUT || t.hideUntil > level.getGameTime()) continue;
            if (best == null || (t.carry.isEmpty() && !best.carry.isEmpty())
                    || (t.carry.isEmpty() == best.carry.isEmpty() && t.distanceToSqr(Vec3.atCenterOf(desk)) < best.distanceToSqr(Vec3.atCenterOf(desk)))) best = t;
        }
        if (best == null) return false;
        // whatever it was carrying goes back on the heap first
        if (!best.carry.isEmpty()) {
            Room store = WorkRooms.room(level, h.get().pos, RoomType.STOREHOUSE);
            BlockPos drop = store == null ? null : WorkRooms.heart(level, store, DropBlock.class);
            for (ItemStack s : best.carry) {
                ItemStack left = drop != null && level.getBlockEntity(drop) instanceof DropBlockEntity be ? be.add(s) : s;
                if (!left.isEmpty()) best.spawnAtLocation(level, left);
            }
            best.carry.clear();
        }
        best.task = FETCH_TAKE;
        best.taskFrom = shelf;
        best.taskTo = desk;
        best.taskItem = item;
        best.taskFor = p.getUUID();
        best.taskUntil = level.getGameTime() + FETCH_PATIENCE;
        best.held(ItemStack.EMPTY);
        return true;
    }

    private static void fetching(ServerLevel level, KinBeast b, long now) {
        BlockPos from = b.taskFrom, to = b.taskTo;
        if (from == null || to == null || b.taskItem == null) {
            b.task = NONE;
            return;
        }
        boolean late = now > b.taskUntil;
        if (b.task == FETCH_TAKE) {
            if (!late && !near(b, from)) {
                walk(b, from, 1.2);
                return;
            }
            Container c = WorkRooms.container(level, from);
            ItemStack got = c == null ? ItemStack.EMPTY : Storehouse.takeAll(c, b.taskItem);
            if (got.isEmpty()) {
                tell(level, b.taskFor, "[The Totter came back with nothing. The shelf was empty.]");
                endFetch(b);
                return;
            }
            b.carry.add(got);
            b.held(got);
            b.act(KinBeast.CARRY, true);
            b.task = FETCH_PUT;
            if (!late) return;
        }
        if (!late && !near(b, to)) {
            walk(b, to, 1.2);
            return;
        }
        for (ItemStack s : b.carry) {
            ItemEntity e = new ItemEntity(level, to.getX() + 0.5, to.getY() + 1.05, to.getZ() + 0.5, s, 0, 0.05, 0);
            e.setNoPickUpDelay();
            level.addFreshEntity(e);
        }
        level.playSound(null, to, WorksContent.SHELVED.get(), SoundSource.BLOCKS, 0.7F, 1.2F);
        b.act(KinBeast.TELL, true);
        tell(level, b.taskFor, "[The Totter set it on the desk for you.]");
        endFetch(b);
    }

    private static void endFetch(KinBeast b) {
        b.carry.clear();
        b.held(ItemStack.EMPTY);
        b.task = NONE;
        b.taskFrom = b.taskTo = null;
        b.taskItem = null;
        b.taskFor = null;
        b.taskUntil = 0;
    }

    private static void tell(ServerLevel level, @Nullable UUID who, String line) {
        if (who != null && level.getServer().getPlayerList().getPlayer(who) instanceof ServerPlayer p) KinWatch.say(p, line, 6);
    }

    // ---- Thatchbacks -----------------------------------------------------------------------------------------------------

    private static void thatchback(ServerLevel level, KinBeast b) {
        WorkPlanBlockEntity plan = WorkPlans.near(level, b.home, PLAN_REACH);
        if (plan != null && !HearthService.data(level.getServer()).containing(plan.getBlockPos(), HearthService.OVERWORLD)
                .map(h -> h.pos.equals(b.home)).orElse(false)) plan = null;
        if (plan == null) {
            b.held(ItemStack.EMPTY);
            potter(b, 4);
            return;
        }
        BlockPos at = plan.getBlockPos();
        if (b.distanceToSqr(Vec3.atCenterOf(at)) > 36) {
            walk(b, at, 1.0);
            return;
        }
        if (!KinHome.every(b, BUILD_EVERY)) return;
        if (WorkPlans.build(level, plan)) {
            b.made++;
            b.held(new ItemStack(Items.OAK_PLANKS));
            b.act(KinBeast.WORK, true);
        } else {
            b.held(ItemStack.EMPTY);
            if (b.act() == KinBeast.WORK) b.act(KinBeast.IDLE);
        }
    }

    // ---- Emberkits: the Smeltery and the Foundry ---------------------------------------------------------------------------

    private static void emberkit(ServerLevel level, KinBeast b) {
        if (foundry(level, b)) return;
        KinHome.goTo(b, b.nook, 0.9);
        BlockPos vent = b.nook.below();
        if (!(level.getBlockState(vent).getBlock() instanceof VentStoneBlock)) return; // curled up by its campfire
        int every = Math.max(SMELT_FASTEST, SMELT_EVERY - SMELT_BELL * Bells.voice(level, b.home, BellCatalog.Voice.SMELT));
        if (!KinHome.every(b, every)) return;
        Room smeltery = WorkRooms.room(level, b.home, RoomType.SMELTERY);
        if (smeltery == null) return;
        List<BlockPos> chests = WorkRooms.containers(level, smeltery);
        for (BlockPos cp : chests) {
            Container c = WorkRooms.container(level, cp);
            if (c == null) continue;
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!ore(s)) continue;
                ItemStack out = smelt(level, s);
                if (out.isEmpty()) continue;
                // the ingot goes in with the rest; if every chest is full it waits
                ItemStack in = s.split(1);
                c.setChanged();
                ItemStack left = out;
                for (BlockPos dp : chests) {
                    Container d = WorkRooms.container(level, dp);
                    if (d != null && !left.isEmpty()) left = KinHome.put(d, left);
                }
                if (!left.isEmpty()) {
                    KinHome.put(c, in);
                    return;
                }
                b.made++;
                b.act(KinBeast.WORK, true);
                level.playSound(null, vent, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.8F, 1.1F);
                level.sendParticles(ParticleTypes.FLAME, vent.getX() + 0.5, vent.getY() + 1.1, vent.getZ() + 0.5, 6, 0.25, 0.1, 0.25, 0.01);
                return;
            }
        }
    }

    /** What the Smeltery takes: ore and raw metal you brought, never anything else. */
    static boolean ore(ItemStack s) {
        return !s.isEmpty() && (s.is(Tags.Items.ORES) || s.is(Tags.Items.RAW_MATERIALS));
    }

    static ItemStack smelt(ServerLevel level, ItemStack s) {
        SingleRecipeInput in = new SingleRecipeInput(s.copyWithCount(1));
        return level.recipeAccess().getRecipeFor(RecipeType.SMELTING, in, level).map(h -> h.value().assemble(in)).orElse(ItemStack.EMPTY);
    }

    /** A bell's mould: what the Foundry's chests must hold to cast it (its metal and bell bronze). */
    public record Mould(String bell, Supplier<Item> metal, int count, int bronze) {
        String needs() {
            return count + " " + new ItemStack(metal.get()).getHoverName().getString() + " and " + bronze + " Bell Bronze Ingots";
        }
    }

    /** The five bells of the Singing Deep a Foundry casts, in the order it casts them (SPEC §43.12). The True Bell isn't cast. */
    public static final List<Mould> MOULDS = List.of(
            new Mould("sung_iron", () -> Items.IRON_INGOT, 12, 4),
            new Mould("sung_copper", () -> Items.COPPER_INGOT, 16, 4),
            new Mould("sung_gold", () -> Items.GOLD_INGOT, 12, 6),
            new Mould("deepglass", DelveContent.DEEPGLASS_SHARD, 16, 8),
            new Mould("starmetal", DelveContent.STARMETAL_INGOT, 4, 8));

    /** A cast under way at a home. Not saved: the metal is only taken when it's done, so a cast cut off starts again. */
    private record Cast(Mould mould, BlockPos crucible, long done) {}

    private static final Map<Long, Cast> CASTING = new HashMap<>();

    static void serverStopped() {
        CASTING.clear();
    }

    /** True while it's busy at the Foundry: a cast under way, or one it's just lit. */
    private static boolean foundry(ServerLevel level, KinBeast b) {
        long now = level.getGameTime();
        Cast c = CASTING.get(b.home.asLong());
        if (c != null) {
            if (b.distanceToSqr(Vec3.atCenterOf(c.crucible())) > 4) walk(b, c.crucible(), 1.0);
            b.act(KinBeast.WORK);
            if (now >= c.done()) finish(level, b.home, c);
            return true;
        }
        if (!KinHome.every(b, 100)) return false;
        Room f = WorkRooms.room(level, b.home, RoomType.FOUNDRY);
        BlockPos crucible = f == null ? null : WorkRooms.heart(level, f, CrucibleBlock.class);
        if (crucible == null) return false;
        Mould m = next(level, b.home, f);
        if (m == null || !has(level, f, m)) return false;
        int ticks = KinApi.loaded(level, b.home, KinKind.THATCHBACK).isEmpty() ? CAST_TICKS : CAST_TICKS / 2;
        CASTING.put(b.home.asLong(), new Cast(m, crucible, now + ticks));
        level.setBlock(crucible, level.getBlockState(crucible).setValue(CrucibleBlock.LIT, true), Block.UPDATE_ALL);
        level.playSound(null, crucible, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 1F, 0.8F);
        members(level, b.home, "[Your Emberkits have lit the Foundry's crucible. " + title(m) + " is being cast.]");
        return true;
    }

    /** The next bell this home's Foundry hasn't cast yet. */
    private static @Nullable Mould next(ServerLevel level, BlockPos anchor, Room f) {
        List<String> done = KinWorld.get(level.getServer()).cast.getOrDefault(anchor.asLong(), List.of());
        for (Mould m : MOULDS) if (!done.contains(m.bell())) return m;
        return null;
    }

    private static int count(ServerLevel level, Room f, Item item) {
        int n = 0;
        for (BlockPos p : WorkRooms.containers(level, f))
            if (WorkRooms.container(level, p) instanceof Container c)
                for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    private static boolean has(ServerLevel level, Room f, Mould m) {
        return count(level, f, m.metal().get()) >= m.count() && count(level, f, DelveContent.BELL_BRONZE_INGOT.get()) >= m.bronze();
    }

    private static void take(ServerLevel level, Room f, Item item, int n) {
        for (BlockPos p : WorkRooms.containers(level, f))
            if (n > 0 && WorkRooms.container(level, p) instanceof Container c) n -= takeUpTo(c, item, n).getCount();
    }

    private static void finish(ServerLevel level, BlockPos anchor, Cast c) {
        CASTING.remove(anchor.asLong());
        BlockState st = level.getBlockState(c.crucible());
        if (st.getBlock() instanceof CrucibleBlock) level.setBlock(c.crucible(), st.setValue(CrucibleBlock.LIT, false), Block.UPDATE_ALL);
        Room f = WorkRooms.room(level, anchor, RoomType.FOUNDRY);
        if (f == null || !has(level, f, c.mould())) {
            members(level, anchor, "[The crucible went cold. The metal for " + title(c.mould()) + " was taken out before it was cast.]");
            return;
        }
        take(level, f, c.mould().metal().get(), c.mould().count());
        take(level, f, DelveContent.BELL_BRONZE_INGOT.get(), c.mould().bronze());
        KinWorld w = KinWorld.get(level.getServer());
        w.cast.computeIfAbsent(anchor.asLong(), k -> new ArrayList<>()).add(c.mould().bell());
        w.setDirty();
        level.playSound(null, c.crucible(), WorksContent.CAST.get(), SoundSource.BLOCKS, 1.5F, 1F);
        level.sendParticles(ParticleTypes.LAVA, c.crucible().getX() + 0.5, c.crucible().getY() + 1, c.crucible().getZ() + 0.5, 20, 0.4, 0.3, 0.4, 0.1);
        for (UUID id : people(level, anchor))
            if (level.getServer().getPlayerList().getPlayer(id) instanceof ServerPlayer p) {
                Bells.ring(p, c.mould().bell(), "cast in your Foundry");
                Bells.ring(p, "founders", "the bell-founders' own, for your Foundry's first cast");
            }
    }

    /** Bells the home's Foundry cast while this member was away: theirs as soon as they're about again. */
    static void castFor(ServerPlayer p) {
        Optional<Hearth.HearthInfo> h = Hearth.home(p);
        if (h.isEmpty()) return;
        List<String> cast = KinWorld.get(p.level().getServer()).cast.get(h.get().anchor().asLong());
        if (cast == null) return;
        for (String id : cast) if (!Bells.has(p, id)) Bells.ring(p, id, "cast in your Foundry");
        if (!cast.isEmpty()) Bells.ring(p, "founders", "the bell-founders' own, for your Foundry's first cast");
    }

    /** What the crucible says when used: the next bell, what it needs, and who heats it. */
    public static void foundryStatus(ServerLevel level, ServerPlayer p, BlockPos crucible) {
        Room f = HearthService.data(level.getServer()).containing(crucible, HearthService.OVERWORLD).flatMap(h -> h.roomAt(crucible, RoomType.FOUNDRY)).orElse(null);
        if (f == null) {
            KinWatch.say(p, "[A crucible on its own is cold. It needs a Foundry: a room with it and a barrel, at your home.]", 8);
            return;
        }
        BlockPos anchor = HearthService.data(level.getServer()).containing(crucible, HearthService.OVERWORLD).map(h -> h.pos).orElse(crucible);
        Cast c = CASTING.get(anchor.asLong());
        if (c != null) {
            KinWatch.say(p, "[" + title(c.mould()) + " is being cast. Leave the metal where it is.]", 6);
            return;
        }
        Mould m = next(level, anchor, f);
        if (m == null) {
            KinWatch.say(p, "[Every bell of the Singing Deep this Foundry can cast has been cast.]", 6);
            return;
        }
        boolean ember = !KinApi.loaded(level, anchor, KinKind.EMBERKIT).isEmpty() || KinWorld.get(level.getServer()).at(anchor).stream().anyMatch(x -> x.kind() == KinKind.EMBERKIT);
        KinWatch.say(p, "[The next mould is for " + title(m) + ". It needs " + m.needs() + " in the Foundry's barrels."
                + (ember ? " An Emberkit will heat the crucible once it's all there.]" : " Only an Emberkit can heat the crucible, and none lives with you.]"), 12);
    }

    private static String title(Mould m) {
        BellCatalog.Def d = BellCatalog.of(m.bell());
        return d == null ? m.bell() : d.name();
    }

    private static List<UUID> people(ServerLevel level, BlockPos anchor) {
        return WorkRooms.home(level, anchor).map(HearthRecord::everyone).orElse(List.of());
    }

    private static void members(ServerLevel level, BlockPos anchor, String line) {
        for (UUID id : people(level, anchor))
            if (level.getServer().getPlayerList().getPlayer(id) instanceof ServerPlayer p) KinWatch.say(p, line, 10);
    }

    // ---- pens and ponds ------------------------------------------------------------------------------------------------

    private static void dumplin(ServerLevel level, KinBeast b) {
        potter(b, 6);
        if (b.made >= EGGS_DAY || !KinHome.every(b, EGG_EVERY)) return;
        putOrDrop(level, b, b.nook, new ItemStack(Items.EGG));
        b.made++;
        b.act(KinBeast.WORK, true);
        level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.8F, 1.2F);
    }

    private static void pondling(ServerLevel level, KinBeast b) {
        potter(b, 3);
        if (b.made >= FISH_DAY || !KinHome.every(b, FISH_EVERY)) return;
        int r = b.getRandom().nextInt(100);
        Item fish = r < 60 ? Items.COD : r < 90 ? Items.SALMON : r < 97 ? Items.TROPICAL_FISH : Items.PUFFERFISH;
        b.held(new ItemStack(fish));
        putOrDrop(level, b, b.nook, new ItemStack(fish));
        b.made++;
        b.act(KinBeast.WORK, true);
        level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.3F);
    }

    /** Snufflets forage: off to a spot in the grass, a snuffle, and back to the nearest chest with what it found. */
    private static void snufflet(ServerLevel level, KinBeast b) {
        if (b.task == TO_TAKE && b.taskFrom != null) {
            if (!near(b, b.taskFrom) && level.getGameTime() < b.taskUntil) {
                walk(b, b.taskFrom, 0.9);
                return;
            }
            b.act(KinBeast.SNIFF, true);
            int r = b.getRandom().nextInt(100);
            Item found = r < 30 ? Items.BROWN_MUSHROOM : r < 55 ? Items.RED_MUSHROOM : r < 85 ? Items.SWEET_BERRIES : r < 95 ? Items.FERN : Items.GLOW_BERRIES;
            b.carry.add(new ItemStack(found));
            b.held(new ItemStack(found));
            b.task = TO_PUT;
            return;
        }
        if (b.task == TO_PUT) {
            if (!near(b, b.nook)) {
                walk(b, b.nook, 0.9);
                return;
            }
            for (ItemStack s : b.carry) putOrDrop(level, b, b.nook, s);
            b.carry.clear();
            b.held(ItemStack.EMPTY);
            b.task = NONE;
            b.made++;
            return;
        }
        potter(b, 4);
        if (b.made >= FORAGE_DAY || !KinHome.every(b, FORAGE_EVERY)) return;
        BlockPos spot = KinHelp.ground(level, b.nook.getX() + b.getRandom().nextInt(21) - 10, b.nook.getZ() + b.getRandom().nextInt(21) - 10);
        b.task = TO_TAKE;
        b.taskFrom = spot;
        b.taskUntil = level.getGameTime() + 20 * 30;
    }

    /** In a Rookery, once a day: a Chimewren comes home singing a note the song is missing, for everyone who lives here. */
    private static void chimewren(ServerLevel level, KinBeast b) {
        potter(b, 3);
        if (!(level.getBlockState(b.nook).getBlock() instanceof ChimePerchBlock)) return;
        if (b.made > 0 || !KinHome.every(b, 2400)) return;
        b.made = 1;
        boolean any = false;
        for (UUID id : people(level, b.home))
            if (level.getServer().getPlayerList().getPlayer(id) instanceof ServerPlayer p) any |= Bells.sung(p, b.home);
        if (!any) return;
        b.act(KinBeast.TELL, true);
        var call = KinContent.CALL.get(KinKind.CHIMEWREN);
        if (call != null) level.playSound(null, b.getX(), b.getY(), b.getZ(), call.get(), SoundSource.NEUTRAL, 1F, 1.2F);
    }

    /** A Puffwool at home, brushed: its wool, once a day. True when it gave some. */
    static boolean brushed(ServerLevel level, KinBeast b, ServerPlayer p) {
        long day = level.getOverworldClockTime() / 24000L;
        if (b.brushedDay == day) {
            KinWatch.say(p, "[It's been brushed today already. It leans into the brush anyway.]", 5);
            return false;
        }
        b.brushedDay = day;
        b.spawnAtLocation(level, new ItemStack(Items.WOOL.white(), WOOL));
        if (b.madeDay != day) {
            b.madeDay = day;
            b.made = 0;
        }
        b.made += WOOL;
        b.act(KinBeast.TELL, true);
        level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.BRUSH_GENERIC, SoundSource.NEUTRAL, 1F, 1F);
        return true;
    }
}
