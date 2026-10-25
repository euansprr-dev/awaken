package dev.awaken.citizens;

import dev.awaken.Awaken;
import dev.awaken.citizens.block.LaundryBlock;
import dev.awaken.citizens.block.StreetLampBlock;
import dev.awaken.citizens.block.StreetLampBlock.Lamp;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;

import static dev.awaken.citizens.CitizensData.LAMP_BROKEN;
import static dev.awaken.citizens.CitizensData.LAMP_LIT;
import static dev.awaken.citizens.CitizensData.LAMP_OUT;
import static dev.awaken.citizens.CitizensData.STAND;
import static dev.awaken.citizens.CitizensData.WALK;
import static dev.awaken.citizens.Roster.*;

/**
 * The work people do that changes the city you can see (brief M3 §4): the lamplighters light the street lamps at dusk
 * and put them out at dawn; the bellringers ring the hours from the district towers and the Choir; stallkeepers open
 * the shutters at seven and close them at dusk, and stock rises with the porters' deliveries and falls with what people
 * buy; washers fill and empty the laundry lines; masons put damage back block by block. Everything is a wanted state
 * in {@link CitizensData} reconciled into the world a little each tick (which is also how shutters flip one after the
 * other), and nothing touches a chunk that isn't loaded. Also decides what each body visibly does and holds.
 */
final class CitizenWork implements Ledger.Work {
    final Citizens c;
    final Ledger l;
    final Roster r;
    final CitizensData d;
    final ServerLevel level;
    final int cx, cz, sy;
    private final BlockPos[] lampHead;
    private final List<BlockPos[]> lineCells = new ArrayList<>();
    private final Direction.Axis[] lineAxis;
    private int lampCursor, stallCursor, lineCursor, furnishCursor, bellScan, chimneyCursor;
    private long lastClock = Long.MIN_VALUE, lastGame;
    /** Strokes waiting to sound: bell index, game time, strokes left, interval, alarm (1/0). */
    private final List<long[]> peals = new ArrayList<>();
    /** Chunks (packed) within reach of a lamp that is dark at night: where the city lets monsters spawn. */
    final LongOpenHashSet darkChunks = new LongOpenHashSet();
    private boolean darkDirty = true;
    /** Which stall an open/close sequence is animating, and how far (cells done). */
    private final int[] stallAnim;
    long lampsLit, lampsOut, deliveries, sales, strokes, repairsDone;
    /**
     * Off where the city's graph is only borrowed (the events test places it in the shared GameTest world): stalls,
     * lines, anvils, tubs, crates and repairs would land on other tests' ground, and the scans would load its chunks.
     */
    boolean edits = true;

    static final int NIGHT_FROM = 13000, NIGHT_TO = 23200, STALLS_OPEN = 700, STALLS_CLOSE = 11800;

    CitizenWork(Citizens c) {
        this.c = c;
        this.l = c.ledger;
        this.r = c.roster;
        this.d = c.data;
        this.level = c.level;
        this.cx = l.cx;
        this.cz = l.cz;
        this.sy = l.streetY;
        lampHead = new BlockPos[r.lampPlace.length];
        dev.awaken.city.CityPlan plan = dev.awaken.city.CityPlan.get();
        for (int k = 0; k < lampHead.length; k++) {
            // the head stands four over the post's own ground (the painter's), not the lamplighter's spot beside it
            int lx = (int) Math.floor(r.lampX[k]), lz = (int) Math.floor(r.lampZ[k]);
            lampHead[k] = new BlockPos(cx + lx, sy + plan.lift(lx, lz) + 4, cz + lz);
        }
        lineAxis = new Direction.Axis[r.lines.size()];
        for (int k = 0; k < r.lines.size(); k++) {
            Line ln = r.lines.get(k);
            int n = Math.max(Math.abs(ln.bx() - ln.ax()), Math.abs(ln.bz() - ln.az())) + 1;
            boolean alongX = ln.bx() != ln.ax();
            lineAxis[k] = alongX ? Direction.Axis.X : Direction.Axis.Z;
            BlockPos[] cells = new BlockPos[n];
            for (int t = 0; t < n; t++) {
                int x = alongX ? Math.min(ln.ax(), ln.bx()) + t : ln.ax();
                int z = alongX ? ln.az() : Math.min(ln.az(), ln.bz()) + t;
                cells[t] = new BlockPos(cx + x, sy + ln.ry() + 4, cz + z);
            }
            lineCells.add(cells);
        }
        stallAnim = new int[r.stalls.size()];
        if (d.furnished.length < (furnitureCount() + 63) / 64) d.furnished = java.util.Arrays.copyOf(d.furnished, (furnitureCount() + 63) / 64);
        lastGame = level.getGameTime();
    }

    void stop() {
        peals.clear();
    }

    // ---- the ledger tells us where people got to -------------------------------------------------------------------

    @Override
    public void arrived(int i, int place, long now) {
        if (r.pKind[place] == P_STALL_KEEPER) stalls(c.tod());
    }

    /** A stall is open while its hours run and one of its keepers is behind the counter; closed otherwise. */
    private void stalls(int tod) {
        boolean hours = tod >= STALLS_OPEN && tod < STALLS_CLOSE;
        for (int s = 0; s < r.stalls.size(); s++) {
            int keeperPlace = r.stalls.get(s).keeper();
            boolean present = false;
            for (int i = 0; i < r.citizens && !present; i++) present = d.place[i] == keeperPlace && d.state[i] == STAND && d.role[i] == 0;
            byte want = (byte) (hours && present ? 1 : 0);
            if (d.stallOpen[s] != want) {
                d.stallOpen[s] = want;
                stallAnim[s] = 0;
                d.setDirty();
            }
        }
    }

    @Override
    public void dwellEnd(int i, int place, long now) {
        byte k = r.pKind[place];
        int seg = l.segIndex(i);
        if (k == P_LAMP && r.job[i] == LAMPLIGHTER && seg >= 0) {
            int lamp = r.pRef[place];
            if (lamp >= 0 && lamp < d.lamps.length && d.lamps[lamp] != LAMP_BROKEN) {
                boolean light = (r.segFlags[seg] & 2) != 0;
                byte want = light ? LAMP_LIT : LAMP_OUT;
                if (d.lamps[lamp] != want) {
                    d.lamps[lamp] = want;
                    if (light) lampsLit++;
                    else lampsOut++;
                    darkDirty = true;
                    d.setDirty();
                    lampNow(lamp, light);
                }
            }
        } else if (k == P_STALL_FRONT) {
            int s = stallOf(place, false);
            if (s < 0) return;
            if (d.carry[i] != 0) {
                d.stock[s] = (byte) Math.min(100, d.stock[s] + (r.job[i] == BAKER ? 18 : 14));
                deliveries++;
            } else if (r.job[i] != PORTER) {
                d.stock[s] = (byte) Math.max(0, d.stock[s] - 3);
                sales++;
            }
            d.setDirty();
        }
    }

    @Override
    public void left(int i, int place, long now) {
        // the last keeper out closes up (stalls() sees nobody behind the counter)
    }

    @Override
    public void roleArrived(int i, long now) {
        if (d.role[i] == Roles.REPAIR) return; // standing by the damage: masons() does the work
        c.events.roleArrived(i, now);
    }

    private int stallOf(int place, boolean keeper) {
        for (int s = 0; s < r.stalls.size(); s++) {
            Stall st = r.stalls.get(s);
            if (keeper ? st.keeper() == place : st.front() == place) return s;
        }
        return -1;
    }

    // ---- ticking -----------------------------------------------------------------------------------------------------

    void tick(long now, int tod) {
        long clk = c.clock();
        if (lastClock != Long.MIN_VALUE) {
            long dClock = clk - lastClock, dGame = now - lastGame;
            if (dClock < 0 || dClock > dGame + 200) byTime(tod);
        } else if (l.fresh) {
            byTime(tod);
        }
        lastClock = clk;
        lastGame = now;
        // hour bells as each hour comes round (06:00..22:00); never twice for one hour, never for a clock jump
        int hour = tod / 1000;
        if (hour != lastHour && lastHour >= 0 && tod % 1000 < 200 && tod <= 16200 && !jumped) ringHour(now, hour * 1000);
        lastHour = hour;
        jumped = false;
        if (now % 40 == 9) stalls(tod);
        peal(now);
        if (edits) {
            reconcileLamps(2);
            reconcileStalls(now);
            laundry(now, tod);
            furnish();
            findBells();
        }
        if (darkDirty) rebuildDark(tod);
        if (edits) {
            masons(now, tod);
            chimneys();
        }
        if (now % 600 == 0) darkDirty = true; // dusk and dawn change what "dark at night" means
    }

    private int lastHour = -1;
    private boolean jumped;

    /** The clock jumped (a command, a sleep): lamps, stalls and lines as that hour has them, as if the work were done. */
    void byTime(int tod) {
        jumped = true;
        boolean night = tod >= NIGHT_FROM && tod < NIGHT_TO;
        for (int k = 0; k < d.lamps.length; k++) if (d.lamps[k] != LAMP_BROKEN) d.lamps[k] = night ? LAMP_LIT : LAMP_OUT;
        boolean open = tod >= STALLS_OPEN + 300 && tod < STALLS_CLOSE;
        for (int s = 0; s < d.stallOpen.length; s++) {
            d.stallOpen[s] = (byte) (open ? 1 : 0);
            stallAnim[s] = 0;
        }
        boolean washing = tod >= 3600 && tod < 8200;
        for (int k = 0; k < d.cloths.length; k++) d.cloths[k] = (byte) (washing ? lineCells.get(k).length : 0);
        darkDirty = true;
        d.setDirty();
    }

    // ---- lamps ------------------------------------------------------------------------------------------------------

    private void reconcileLamps(int perTick) {
        for (int n = 0; n < perTick && lampHead.length > 0; n++) {
            int k = lampCursor;
            lampCursor = (lampCursor + 1) % lampHead.length;
            BlockPos pos = lampHead[k];
            if (!level.isLoaded(pos)) continue;
            BlockState s = level.getBlockState(pos);
            boolean ours = s.is(CitizensContent.STREET_LAMP.get());
            if (!ours && !s.is(Blocks.LANTERN)) {
                // the lamp's gone (broken by someone, with protection off): dark until a mason puts it back
                if (d.lamps[k] != LAMP_BROKEN && s.isAir()) {
                    d.lamps[k] = LAMP_BROKEN;
                    addRepair(pos, CitizensContent.STREET_LAMP.get().defaultBlockState(), 0);
                    darkDirty = true;
                }
                continue;
            }
            BlockState want = lampState(d.lamps[k]);
            if (s != want) level.setBlock(pos, want, Block.UPDATE_ALL);
        }
    }

    private BlockState lampState(byte state) {
        Lamp v = state == LAMP_LIT ? Lamp.LIT : state == LAMP_BROKEN ? Lamp.BROKEN : Lamp.OUT;
        return CitizensContent.STREET_LAMP.get().defaultBlockState().setValue(StreetLampBlock.LAMP, v);
    }

    /** The lamplighter's pole reaches the lamp: set it now, with a flare (or a wisp of smoke going out). */
    private void lampNow(int k, boolean light) {
        BlockPos pos = lampHead[k];
        if (!edits || !level.isLoaded(pos)) return;
        BlockState s = level.getBlockState(pos);
        if (s.is(CitizensContent.STREET_LAMP.get()) || s.is(Blocks.LANTERN)) {
            level.setBlock(pos, lampState(light ? LAMP_LIT : LAMP_OUT), Block.UPDATE_ALL);
            if (light) {
                level.sendParticles(CitizensContent.LAMP_FLARE.get(), pos.getX() + 0.5, pos.getY() + 0.45, pos.getZ() + 0.5, 6, 0.08, 0.08, 0.08, 0.01);
                level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.25f, 1.6f);
            } else {
                level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 4, 0.05, 0.05, 0.05, 0.005);
            }
        }
    }

    /** Breaks a lamp (an event knocked it over): dark until mended. */
    void breakLamp(int k, long now) {
        if (k < 0 || k >= d.lamps.length || d.lamps[k] == LAMP_BROKEN) return;
        d.lamps[k] = LAMP_BROKEN;
        addRepair(lampHead[k], CitizensContent.STREET_LAMP.get().defaultBlockState(), now + 2400);
        darkDirty = true;
        d.setDirty();
        BlockPos pos = lampHead[k];
        if (edits && level.isLoaded(pos) && (level.getBlockState(pos).is(CitizensContent.STREET_LAMP.get()) || level.getBlockState(pos).is(Blocks.LANTERN))) {
            level.setBlock(pos, lampState(LAMP_BROKEN), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1f, 1f);
        }
    }

    int nearestLamp(double x, double z, double within) {
        int best = -1;
        double bd = within * within;
        for (int k = 0; k < lampHead.length; k++) {
            double dd = (lampHead[k].getX() + 0.5 - x) * (lampHead[k].getX() + 0.5 - x) + (lampHead[k].getZ() + 0.5 - z) * (lampHead[k].getZ() + 0.5 - z);
            if (dd < bd) {
                bd = dd;
                best = k;
            }
        }
        return best;
    }

    BlockPos lampHead(int k) {
        return lampHead[k];
    }

    /** Monsters may spawn in the city only near a lamp that is dark at night (CitizensModule's spawn rule). */
    private void rebuildDark(int tod) {
        darkDirty = false;
        darkChunks.clear();
        boolean night = tod >= NIGHT_FROM - 500 && tod < NIGHT_TO;
        if (!night) return;
        for (int k = 0; k < lampHead.length; k++) {
            if (d.lamps[k] == LAMP_LIT) continue;
            BlockPos p = lampHead[k];
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                darkChunks.add(net.minecraft.world.level.ChunkPos.pack((p.getX() >> 4) + dx, (p.getZ() >> 4) + dz));
        }
    }

    boolean darkLampNear(BlockPos pos) {
        if (darkChunks.isEmpty() || !darkChunks.contains(net.minecraft.world.level.ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4))) return false;
        for (int k = 0; k < lampHead.length; k++) {
            if (d.lamps[k] == LAMP_LIT) continue;
            if (lampHead[k].distSqr(pos) < 14 * 14) return true;
        }
        return false;
    }

    // ---- stalls ------------------------------------------------------------------------------------------------------

    private static final String[] SIDES = {"north", "east", "south", "west"};
    /** The street kit's counter by facing, shut [0] and open [1] (M4 B3a). */
    private static final BlockState[][] COUNTER = new BlockState[4][2];

    static {
        for (int f = 0; f < 4; f++) {
            COUNTER[f][0] = parse("awaken:stall_counter[facing=" + SIDES[f] + ",open=false]");
            COUNTER[f][1] = parse("awaken:stall_counter[facing=" + SIDES[f] + ",open=true]");
        }
    }

    /** The kit's wares for a stall's good (never vanilla stand-ins: decision 26). */
    private static BlockState wares(String good) {
        return WARES.computeIfAbsent(good, g -> parse("awaken:wares[good=" + g + "]"));
    }

    private static final java.util.Map<String, BlockState> WARES = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * One stall cell every few ticks toward the stall's wanted state: the keeper drops the counter's board and lays the
     * wares out one cell after the other (the middle first), and folds it all away at dusk; wares show by stock.
     */
    private void reconcileStalls(long now) {
        if (now % 3 != 0 || r.stalls.isEmpty()) return;
        int s = stallCursor;
        stallCursor = (stallCursor + 1) % r.stalls.size();
        Stall st = r.stalls.get(s);
        int g = sy + r.pRy[st.keeper()];
        boolean open = d.stallOpen[s] != 0;
        int shown = d.stock[s] >= 50 ? 2 : d.stock[s] > 5 ? 1 : 0;
        BlockState ware = wares(st.good());
        for (int k = 0; k < 2; k++) {
            BlockPos low = new BlockPos(cx + st.counterX(k), g + 1, cz + st.counterZ(k));
            if (!level.isLoaded(low)) return;
            if (set(low, COUNTER[st.facing()][open ? 1 : 0], true)) return;
            BlockState want = open && k < shown ? ware : Blocks.AIR.defaultBlockState();
            if (set(low.above(), want, false)) return;
        }
    }

    /** Sets a block if it differs (and only over air or our own work); true when something changed. */
    private boolean set(BlockPos pos, BlockState want, boolean sound) {
        BlockState s = level.getBlockState(pos);
        if (s == want) return false;
        if (!s.isAir() && !mine(s)) return false;
        level.setBlock(pos, want, Block.UPDATE_ALL);
        if (sound && want.getBlock() instanceof dev.awaken.city.kit.StreetBlocks.StallCounter)
            level.playSound(null, pos, want.getValue(dev.awaken.city.kit.StreetBlocks.StallCounter.OPEN)
                    ? SoundEvents.WOODEN_TRAPDOOR_OPEN : SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5f, 1f);
        return true;
    }

    /** Blocks the citizens put in the world themselves (so the stalls can change them back). */
    private static boolean mine(BlockState s) {
        return MINE.contains(s.getBlock());
    }

    private static final java.util.Set<Block> MINE = new java.util.HashSet<>();

    static {
        MINE.add(Blocks.SPRUCE_TRAPDOOR);
        MINE.add(Blocks.STONE_SLAB);
        MINE.add(COUNTER[0][0].getBlock());
        MINE.add(wares("bread").getBlock());
    }

    // ---- laundry -------------------------------------------------------------------------------------------------------

    private void laundry(long now, int tod) {
        if (now % 50 != 0 || r.lines.isEmpty()) return;
        boolean hanging = tod >= 2400 && tod < 6000;
        for (int k = 0; k < r.lines.size(); k++) {
            Line ln = r.lines.get(k);
            int washers = 0;
            for (int i = 0; i < r.citizens; i++) if (d.place[i] == ln.place() && d.state[i] == STAND) washers++;
            if (washers == 0) continue;
            int full = lineCells.get(k).length;
            int want = hanging ? Math.min(full, d.cloths[k] + 1) : Math.max(0, d.cloths[k] - 1);
            if (want != d.cloths[k]) {
                d.cloths[k] = (byte) want;
                d.setDirty();
            }
        }
        // one line's blocks toward its count
        int k = lineCursor;
        lineCursor = (lineCursor + 1) % r.lines.size();
        if (!isFurnished(lineIndex(k))) return;
        BlockPos[] cells = lineCells.get(k);
        for (int t = 0; t < cells.length; t++) {
            BlockPos p = cells[t];
            if (!level.isLoaded(p)) return;
            BlockState s = level.getBlockState(p);
            if (!s.is(CitizensContent.LAUNDRY.get())) continue;
            int cloth = t < d.cloths[k] ? 1 + (int) Math.floorMod(mixHash(k * 131 + t), 6L) : 0;
            if (s.getValue(LaundryBlock.CLOTH) != cloth) level.setBlock(p, s.setValue(LaundryBlock.CLOTH, cloth), Block.UPDATE_CLIENTS);
        }
    }

    // ---- furniture: lines, anvils, tubs, crates (placed once) ------------------------------------------------------------

    private int furnitureCount() {
        return r.lines.size() + r.smithies.size() + r.washplaces.size() + r.crates.length;
    }

    private int lineIndex(int k) {
        return k;
    }

    private boolean isFurnished(int bit) {
        return (d.furnished[bit >> 6] & (1L << (bit & 63))) != 0;
    }

    private void markFurnished(int bit) {
        d.furnished[bit >> 6] |= 1L << (bit & 63);
        d.setDirty();
    }

    private void furnish() {
        int total = furnitureCount();
        if (total == 0) return;
        int bit = furnishCursor;
        furnishCursor = (furnishCursor + 1) % total;
        if (isFurnished(bit)) return;
        int k = bit;
        if (k < r.lines.size()) {
            BlockPos[] cells = lineCells.get(k);
            for (BlockPos p : cells) if (!level.isLoaded(p)) return;
            for (BlockPos p : cells) {
                if (level.getBlockState(p).isAir())
                    level.setBlock(p, CitizensContent.LAUNDRY.get().defaultBlockState().setValue(LaundryBlock.AXIS, lineAxis[k]), Block.UPDATE_CLIENTS);
            }
            markFurnished(bit);
            return;
        }
        k -= r.lines.size();
        if (k < r.smithies.size()) {
            Smithy s = r.smithies.get(k);
            BlockPos p = new BlockPos(cx + s.bx(), sy + s.bry() + 1, cz + s.bz());
            if (!level.isLoaded(p)) return;
            if (level.getBlockState(p).isAir()) {
                Direction face = Direction.fromYRot(r.pYaw[s.anvil()]);
                level.setBlock(p, Blocks.ANVIL.defaultBlockState().setValue(net.minecraft.world.level.block.AnvilBlock.FACING, face.getClockWise()), Block.UPDATE_ALL);
            }
            markFurnished(bit);
            return;
        }
        k -= r.smithies.size();
        if (k < r.washplaces.size()) {
            WashPlace w = r.washplaces.get(k);
            BlockPos p = new BlockPos(cx + w.tx(), sy + w.tRy() + 1, cz + w.tz());
            if (!level.isLoaded(p)) return;
            if (level.getBlockState(p).isAir())
                level.setBlock(p, Blocks.WATER_CAULDRON.defaultBlockState().setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, 3), Block.UPDATE_ALL);
            markFurnished(bit);
            return;
        }
        k -= r.washplaces.size();
        if (k < r.crates.length) {
            int[] cr = r.crates[k];
            BlockPos p = new BlockPos(cx + cr[0], sy + cr[1] + 1, cz + cr[2]);
            if (!level.isLoaded(p)) return;
            if (level.getBlockState(p).isAir()) {
                level.setBlock(p, CitizensContent.CARGO_CRATE.get().defaultBlockState(), Block.UPDATE_ALL);
                if ((k & 1) == 0 && level.getBlockState(p.above()).isAir())
                    level.setBlock(p.above(), CitizensContent.CARGO_CRATE.get().defaultBlockState(), Block.UPDATE_ALL);
            }
            markFurnished(bit);
        }
    }

    // ---- bells -------------------------------------------------------------------------------------------------------

    /** Finds each tower's bell block once (the painter hangs one in every belfry). */
    private void findBells() {
        if (r.bells.isEmpty()) return;
        int b = bellScan;
        bellScan = (bellScan + 1) % r.bells.size();
        if (d.bellPos[b] != 0) return;
        Bell bell = r.bells.get(b);
        BlockPos probe = new BlockPos(cx + (bell.x0() + bell.x1()) / 2, sy, cz + (bell.z0() + bell.z1()) / 2);
        if (!level.isLoaded(probe)) return;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int x = bell.x0(); x <= bell.x1(); x++)
            for (int z = bell.z0(); z <= bell.z1(); z++) {
                m.set(cx + x, sy, cz + z);
                if (!level.isLoaded(m)) return;
            }
        for (int y = sy + 150; y > sy; y--)
            for (int x = bell.x0(); x <= bell.x1(); x++)
                for (int z = bell.z0(); z <= bell.z1(); z++) {
                    if (level.getBlockState(m.set(cx + x, y, cz + z)).is(Blocks.BELL)) {
                        d.bellPos[b] = m.asLong();
                        d.setDirty();
                        return;
                    }
                }
        d.bellPos[b] = -1; // none
        d.setDirty();
    }

    /**
     * Ticks the hour waits at dawn, noon and dusk while the Choir's carillon plays its tune (citizens.client.CitySound,
     * 10.6-13.6 s): the tune first, then the hour struck, as a clock tower does (M4 B3b).
     */
    static final int AFTER_TUNE = 300;

    /** On the hour: every tower whose ringer is at the bell rings the hour, the Choir first, the districts after it. */
    private void ringHour(long now, int tod) {
        int hour = (6 + tod / 1000) % 24;
        int strokes = hour % 12 == 0 ? 12 : hour % 12;
        long wait = hour == 6 || hour == 12 || hour == 18 ? AFTER_TUNE : 0;
        for (int b = 0; b < r.bells.size(); b++) {
            if (!ringerAt(b)) continue;
            boolean choir = "choir".equals(r.bells.get(b).landmark());
            long start = now + wait + (choir ? 0 : 50 + 23L * b);
            peals.add(new long[]{b, start, strokes, choir ? 46 : 34, 0});
        }
    }

    /** An alarm on the tower nearest a place: rapid strokes for a while (fires, a gate tremor). */
    void alarm(double x, double z, int strokesCount) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int b = 0; b < r.bells.size(); b++) {
            Bell bell = r.bells.get(b);
            double bx = cx + (bell.x0() + bell.x1()) / 2.0, bz = cz + (bell.z0() + bell.z1()) / 2.0;
            double dd = (bx - x) * (bx - x) + (bz - z) * (bz - z);
            if (dd < bd && !"choir".equals(bell.landmark())) {
                bd = dd;
                best = b;
            }
        }
        if (best >= 0) peals.add(new long[]{best, level.getGameTime() + 10, strokesCount, 11, 1});
    }

    boolean ringerAt(int b) {
        int place = r.bells.get(b).place();
        for (int i = 0; i < r.citizens; i++) if (r.job[i] == BELLRINGER && d.place[i] == place && d.state[i] == STAND) return true;
        return false;
    }

    /** Whether bell b is mid-peal now (its ringer pulls the rope). */
    boolean pealing(int b, long now) {
        for (long[] p : peals) if (p[0] == b && p[1] <= now + 40) return true;
        return false;
    }

    private void peal(long now) {
        for (int k = peals.size() - 1; k >= 0; k--) {
            long[] p = peals.get(k);
            if (now < p[1]) continue;
            int b = (int) p[0];
            boolean alarm = p[4] != 0;
            if (d.bellPos[b] > 0) {
                BlockPos pos = BlockPos.of(d.bellPos[b]);
                if (level.isLoaded(pos)) {
                    if (level.getBlockEntity(pos) instanceof BellBlockEntity be) be.onHit(Direction.from2DDataValue((int) (p[2] & 3)));
                    boolean choir = "choir".equals(r.bells.get(b).landmark());
                    float pitch = alarm ? 1.15f : choir ? 0.84f : 0.92f + 0.05f * (b % 4);
                    level.playSound(null, pos, alarm ? CitizensContent.BELL_ALARM.get() : choir ? CitizensContent.BELL_HOUR.get()
                            : CitizensContent.BELL_DISTRICT.get(), SoundSource.AMBIENT, choir ? 6f : 4f, pitch);
                    strokes++;
                }
            }
            p[2]--;
            p[1] = now + p[3];
            if (p[2] <= 0) peals.remove(k);
        }
    }

    // ---- repairs (masons) ----------------------------------------------------------------------------------------------

    void addRepair(BlockPos pos, BlockState state, long after) {
        long key = pos.asLong();
        for (CitizensData.Repair rp : d.repairs) if (rp.pos() == key) return;
        d.repairs.add(new CitizensData.Repair(key, state, after));
        d.setDirty();
    }

    /** A mason at a site puts one block back (called by the mason's work, with or without a body). */
    boolean repairOne(double x, double z, long now) {
        for (int k = 0; k < d.repairs.size(); k++) {
            CitizensData.Repair rp = d.repairs.get(k);
            if (rp.after() > now) continue;
            BlockPos p = BlockPos.of(rp.pos());
            if ((p.getX() - x) * (p.getX() - x) + (p.getZ() - z) * (p.getZ() - z) > 12 * 12) continue;
            if (!level.isLoaded(p)) return false;
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.canBeReplaced() || s.is(CitizensContent.CITY_FIRE.get()) || burnt(s) || s.is(CitizensContent.STREET_LAMP.get())) {
                level.setBlock(p, rp.state(), Block.UPDATE_ALL);
                level.playSound(null, p, rp.state().getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.8f, 0.9f);
                level.sendParticles(ParticleTypes.POOF, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.01);
                if (rp.state().is(CitizensContent.STREET_LAMP.get())) {
                    int lamp = lampAt(p);
                    if (lamp >= 0) {
                        d.lamps[lamp] = LAMP_OUT;
                        darkDirty = true;
                    }
                }
            }
            d.repairs.remove(k);
            repairsDone++;
            d.setDirty();
            return true;
        }
        return false;
    }

    /**
     * Masons go to damage during their working hours: up to three at once, each putting one block back every 30 ticks
     * while standing by it (with or without a body). When the site is done they go back to their day.
     */
    private void masons(long now, int tod) {
        if (now % 30 != 7) return;
        boolean hours = tod >= 1000 && tod < 10600;
        int working = 0;
        for (int i = 0; i < r.citizens; i++) {
            if (r.job[i] != MASON || d.role[i] != Roles.REPAIR) continue;
            working++;
            if (!hours || d.repairs.isEmpty()) {
                d.role[i] = 0;
                d.seg[i] = -1;
                continue;
            }
            if (d.state[i] == STAND) {
                int at = d.at[i];
                if (!repairOne(l.g.x[at], l.g.z[at], now)) {
                    // nothing left here: the next site, or home to the yard
                    BlockPos next = nextRepair(now);
                    if (next == null) {
                        d.role[i] = 0;
                        d.seg[i] = -1;
                    } else sendTo(i, next);
                }
            }
        }
        if (!hours || d.repairs.isEmpty() || working >= 3 || now % 300 != 7) return;
        BlockPos site = nextRepair(now);
        if (site == null) return;
        for (int i = 0; i < r.citizens && working < 3; i++) {
            if (r.job[i] != MASON || d.role[i] != 0 || d.hurtUntil[i] > now || d.state[i] == CitizensData.INSIDE) continue;
            d.role[i] = Roles.REPAIR;
            sendTo(i, site);
            working++;
        }
    }

    private @org.jspecify.annotations.Nullable BlockPos nextRepair(long now) {
        for (CitizensData.Repair rp : d.repairs) if (rp.after() <= now) return BlockPos.of(rp.pos());
        return null;
    }

    private void sendTo(int i, BlockPos site) {
        l.release(i);
        d.place[i] = -1;
        d.goal[i] = l.g.nearest(site.getX() + 0.5, site.getZ() + 0.5, true);
        d.state[i] = WALK;
        d.setDirty();
    }

    static boolean burnt(BlockState s) {
        return s.is(Blocks.COAL_BLOCK) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.POLISHED_BLACKSTONE);
    }

    private int lampAt(BlockPos p) {
        for (int k = 0; k < lampHead.length; k++) if (lampHead[k].equals(p)) return k;
        return -1;
    }

    // ---- chimneys: the smoke the client puffs (signs of life) ------------------------------------------------------

    private final java.util.Map<Integer, Long> chimneyOf = new java.util.HashMap<>();
    private final java.util.Map<Integer, Byte> chimneyKind = new java.util.HashMap<>();
    private int[] firstPlots;
    private int housesCursor, firstCursor;

    /** Bakeries, taverns and smithies first; then houses near a player, one plot a tick, each once per world. */
    private void chimneys() {
        dev.awaken.city.CityPlan plan = dev.awaken.city.CityPlan.get();
        if (firstPlots == null) {
            for (int k = 0; k + 1 < d.chimneys.length; k += 2) chimneyOf.put((int) d.chimneys[k], d.chimneys[k + 1]);
            java.util.List<Integer> first = new java.util.ArrayList<>();
            for (Bakery b : r.bakeries) {
                first.add(b.plot());
                chimneyKind.put(b.plot(), (byte) 0);
            }
            for (Tavern t : r.taverns) {
                first.add(t.plot());
                chimneyKind.put(t.plot(), (byte) 2);
            }
            for (Smithy sm : r.smithies) {
                first.add(sm.plot());
                chimneyKind.put(sm.plot(), (byte) 3);
            }
            firstPlots = first.stream().mapToInt(Integer::intValue).toArray();
        }
        int plot = -1;
        for (int n = 0; n < firstPlots.length && plot < 0; n++) {
            int cand = firstPlots[firstCursor];
            firstCursor = (firstCursor + 1) % firstPlots.length;
            if (!chimneyOf.containsKey(cand)) plot = cand;
        }
        if (plot < 0) {
            for (int n = 0; n < 8 && plot < 0; n++) {
                int cand = housesCursor;
                housesCursor = (housesCursor + 1) % plan.plots.size();
                dev.awaken.city.CityPlan.Plot pl = plan.plots.get(cand);
                if (chimneyOf.containsKey(pl.id()) || !nearPlayer(cx + pl.x0(), cz + pl.z0(), 112)) continue;
                plot = pl.id();
            }
        }
        if (plot < 0) return;
        dev.awaken.city.CityPlan.Plot pl = plan.plots.get(plot);
        BlockPos a = new BlockPos(cx + pl.x0(), sy, cz + pl.z0()), b = new BlockPos(cx + pl.x1(), sy, cz + pl.z1());
        if (!level.isLoaded(a) || !level.isLoaded(b) || !level.isLoaded(new BlockPos(a.getX(), sy, b.getZ())) || !level.isLoaded(new BlockPos(b.getX(), sy, a.getZ())))
            return;
        long found = -1;
        int g = sy + pl.lift();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        outer:
        for (int y = g + pl.top() + 24; y > g + Math.max(2, pl.eave() - 1); y--) {
            for (int x = pl.x0(); x <= pl.x1(); x++) {
                for (int z = pl.z0(); z <= pl.z1(); z++) {
                    BlockState st = level.getBlockState(m.set(cx + x, y, cz + z));
                    if (st.is(Blocks.FLOWER_POT) || st.is(Blocks.STONE_SLAB)) {
                        BlockState below = level.getBlockState(m.set(cx + x, y - 1, cz + z));
                        if (below.is(Blocks.BRICKS) || below.is(Blocks.POLISHED_BLACKSTONE_BRICKS) || below.getBlock().getDescriptionId().contains("bricks")) {
                            found = BlockPos.asLong(cx + x, y + 1, cz + z);
                            if (st.is(Blocks.STONE_SLAB) && !chimneyKind.containsKey(plot)) chimneyKind.put(plot, (byte) 3);
                            break outer;
                        }
                    }
                }
            }
        }
        if (found == -1 && chimneyKind.containsKey(plot) && chimneyKind.get(plot) != 1) {
            // a bakery, tavern or forge without a painted chimney still smokes: from the ridge over its middle
            int x = cx + (pl.x0() + pl.x1()) / 2, z = cz + (pl.z0() + pl.z1()) / 2;
            for (int y = g + pl.top() + 4; y > g + 2; y--) {
                if (!level.getBlockState(m.set(x, y, z)).isAir()) {
                    found = BlockPos.asLong(x, y + 1, z);
                    break;
                }
            }
        }
        chimneyOf.put(plot, found);
        int n = d.chimneys.length;
        d.chimneys = java.util.Arrays.copyOf(d.chimneys, n + 2);
        d.chimneys[n] = plot;
        d.chimneys[n + 1] = found;
        d.setDirty();
        if (found != -1) CitizensNet.emitters(level.players(), false, new long[]{found}, new byte[]{chimneyKind.getOrDefault(plot, (byte) 1)});
    }

    private boolean nearPlayer(int x, int z, double range) {
        for (ServerPlayer p : level.players()) if (Math.abs(p.getX() - x) < range && Math.abs(p.getZ() - z) < range) return true;
        return false;
    }

    /** Every chimney found so far, for a player who just arrived. */
    void sendChimneys(ServerPlayer p) {
        if (firstPlots == null) chimneys();
        java.util.List<Long> ps = new java.util.ArrayList<>();
        java.util.List<Byte> ks = new java.util.ArrayList<>();
        for (var e : chimneyOf.entrySet()) {
            if (e.getValue() == -1) continue;
            ps.add(e.getValue());
            ks.add(chimneyKind.getOrDefault(e.getKey(), (byte) 1));
        }
        // (M4 B3a) the fountains' spray, from the upper bowl of each
        for (dev.awaken.city.CityPlan.Prop pr : dev.awaken.city.CityPlan.get().props) {
            if (!pr.kind().equals("fountain")) continue;
            ps.add(BlockPos.asLong(cx + pr.x0() + 2, sy + pr.ry() + 3, cz + pr.z0() + 2));
            ks.add((byte) 4);
        }
        // (M4 B3b) every street lamp's head: the client draws its pool of light while it's lit
        for (BlockPos lh : lampHead) {
            ps.add(lh.asLong());
            ks.add((byte) 5);
        }
        long[] pa = new long[ps.size()];
        byte[] ka = new byte[ks.size()];
        for (int k = 0; k < pa.length; k++) {
            pa[k] = ps.get(k);
            ka[k] = ks.get(k);
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new CitizensNet.Site(cx, sy, cz));
        CitizensNet.emitters(java.util.List.of(p), true, pa, ka);
    }

    // ---- what a body shows ------------------------------------------------------------------------------------------

    private static final ItemStack EMPTY = ItemStack.EMPTY;

    /** At spawn: what this citizen wears in hand from the start. */
    void dress(CitizenBody b, int i, long now) {
        show(b, i, now);
    }

    /** Each think: the clip, the props in hand and the flags for what this record is doing now. */
    void show(CitizenBody b, int i, long now) {
        if (d.role[i] == Roles.REPAIR) {
            b.setAct(d.state[i] == STAND ? Act.TROWEL : Act.NONE);
            b.hold(EquipmentSlot.MAINHAND, Props.TROWEL);
            b.hold(EquipmentSlot.OFFHAND, Props.NONE);
            b.setFlag(CitizenBody.F_CRATE, false);
            return;
        }
        if (d.role[i] != 0) {
            c.events.show(b, i, now);
            return;
        }
        int tod = c.tod();
        boolean night = tod >= 12800 || tod < 300;
        int job = r.job[i];
        byte st = d.state[i];
        int place = d.place[i];
        byte pk = place >= 0 ? r.pKind[place] : -1;
        Act act = Act.NONE;
        int main = Props.NONE, off = Props.NONE;
        boolean crate = false;
        boolean hurt = d.hurtUntil[i] > now;
        if (hurt) {
            act = Act.HURT;
        } else if (st == WALK) {
            switch (job) {
                case PORTER -> {
                    if (d.carry[i] != 0) {
                        act = Act.CARRY_BACK;
                        crate = true;
                    }
                }
                case BAKER -> {
                    if (d.carry[i] != 0) {
                        act = Act.TRAY;
                        main = Props.BREAD;
                    }
                }
                case WATCH -> {
                    act = Act.HALBERD;
                    main = Props.HALBERD;
                    if (night) off = Props.LANTERN;
                }
                case LAMPLIGHTER -> {
                    if (l.act(i) == ROUND) {
                        main = Props.POLE;
                        if (night || tod > 11000) off = Props.LANTERN;
                    }
                }
                case RUNNER -> {
                    if (l.act(i) == ROUND) {
                        act = Act.RUN;
                        off = Props.SATCHEL;
                    }
                }
                case CRIER -> off = Props.SCROLL;
                case WASHER -> {
                    if (pk == P_LINE || pk == P_WASH) main = Props.LAUNDRY;
                }
                default -> {
                    if (night && (job == TAVERNKEEPER || job == RESIDENT) && (i & 3) == 0)
                        off = Props.LANTERN;
                }
            }
        } else if (st == STAND) {
            switch (pk) {
                case P_LAMP -> {
                    main = Props.POLE;
                    act = d.dwellUntil[i] > 0 ? Act.LAMP_LIGHT : Act.HANDS_BEHIND;
                }
                case P_BELL -> {
                    int bell = bellOfPlace(place);
                    act = bell >= 0 && pealing(bell, now) ? Act.BELL_PULL : Act.HANDS_BEHIND;
                }
                case P_STALL_KEEPER -> {
                    int s = stallOf(place, true);
                    act = s >= 0 && stallBusy(s) ? Act.SHUTTER : s >= 0 && d.stallOpen[s] != 0 ? Act.SERVE : Act.ARMS_CROSSED;
                }
                case P_STALL_FRONT -> {
                    if (d.carry[i] != 0) {
                        act = Act.SET_DOWN;
                        crate = job == PORTER;
                    } else act = (i & 1) == 0 ? Act.TALK : Act.LOOK_AROUND;
                }
                case P_ANVIL -> {
                    act = Act.HAMMER;
                    main = Props.HAMMER;
                }
                case P_WASH -> act = Act.SCRUB;
                case P_LINE -> {
                    act = Act.HANG;
                    main = Props.LAUNDRY;
                }
                case P_PICKUP -> {
                    if (job == BOATMAN) act = Act.HAUL;
                    else {
                        act = Act.SET_DOWN;
                        crate = d.carry[i] != 0;
                    }
                }
                case P_POST -> {
                    act = Act.HALBERD_STAND;
                    main = Props.HALBERD;
                    if (night) off = Props.LANTERN;
                }
                case P_CRIER -> {
                    act = Act.PROCLAIM;
                    off = Props.SCROLL;
                }
                case P_SIT -> act = Act.SIT;
                case P_PLAY -> act = Act.PLAY;
                case P_MILL -> act = Act.HAUL;
                case P_TAVERN_OUT -> {
                    if (job == TAVERNKEEPER) {
                        // (M4 B3b) the house serves its tables in the lane: trays out, a pour at the door, a word between
                        int k = (int) Math.floorMod((now + i * 613L) / 400, 3L);
                        act = k == 0 ? Act.TRAY : k == 1 ? Act.SERVE : Act.TALK;
                        if (k == 0) main = Props.BREAD;
                    } else act = (i % 3) == 0 ? Act.LEAN : Act.TALK;
                }
                case P_SHOP_DOOR -> {
                    // (M4 B3b) a shopkeeper minds the door: wares carried out and set down, a word with a passer-by, a lean
                    int k = (int) Math.floorMod(now / 600 + i * 7L, 4L);
                    act = k == 0 ? Act.SET_DOWN : k == 1 ? Act.TALK : k == 2 ? Act.LEAN : Act.SET_DOWN;
                    crate = k == 0;
                }
                case P_MOORING -> {
                    // (M4 B3b) a boatman at his berth: the lines coiled and made fast, the boat bailed, a crate swung down
                    // into it, the boat fended off the quay with the pole; each spell its own length, out of step with the
                    // next berth's
                    int k = (int) Math.floorMod((now + i * 977L) / 520, 5L);
                    switch (k) {
                        case 0 -> act = Act.COIL;
                        case 1 -> {
                            act = Act.BAIL;
                            main = Props.BUCKET;
                        }
                        case 2 -> {
                            act = Act.SET_DOWN;
                            crate = true;
                        }
                        case 3 -> {
                            act = Act.POLE;
                            main = Props.POLE;
                        }
                        default -> act = Act.HAUL;
                    }
                }
                case P_NET -> act = Act.MEND;
                default -> {
                    if (job == MASON) {
                        act = Act.TROWEL;
                        main = Props.TROWEL;
                    } else if (job == WATCH) {
                        act = Act.HALBERD_STAND;
                        main = Props.HALBERD;
                        if (night) off = Props.LANTERN;
                    } else if (job == ARCHIVIST) {
                        act = Act.READ;
                        main = Props.BOOK;
                    } else if (job == CHILD) {
                        act = Act.PLAY;
                    } else if (job == BAKER) {
                        act = Act.SERVE;
                        main = Props.BREAD;
                    } else {
                        act = (i % 4) == 0 ? Act.LOOK_AROUND : (i % 4) == 1 ? Act.HANDS_BEHIND : Act.ARMS_CROSSED;
                    }
                }
            }
        }
        if (b.gesture != Act.NONE && b.tickCount < b.gestureUntil) act = b.gesture;
        else b.gesture = Act.NONE;
        b.setAct(act);
        b.hold(EquipmentSlot.MAINHAND, main);
        b.hold(EquipmentSlot.OFFHAND, off);
        b.setFlag(CitizenBody.F_CRATE, crate);
        b.setFlag(CitizenBody.F_HURT, hurt);
    }

    private int bellOfPlace(int place) {
        for (int b = 0; b < r.bells.size(); b++) if (r.bells.get(b).place() == place) return b;
        return -1;
    }

    /** True while the stall's counter is still being opened or shut (its keeper works it). */
    private boolean stallBusy(int s) {
        Stall st = r.stalls.get(s);
        BlockPos p = new BlockPos(cx + st.counterX(1), sy + r.pRy[st.keeper()] + 1, cz + st.counterZ(1));
        if (!level.isLoaded(p)) return false;
        return level.getBlockState(p) != COUNTER[st.facing()][d.stallOpen[s] != 0 ? 1 : 0];
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    static long mixHash(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    static BlockState parse(String spec) {
        String id = spec, props = null;
        int b = spec.indexOf('[');
        if (b >= 0) {
            id = spec.substring(0, b);
            props = spec.substring(b + 1, spec.length() - 1);
        }
        Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
        BlockState state = block.defaultBlockState();
        if (props != null && !props.isEmpty()) {
            for (String kv : props.split(",")) {
                String[] p = kv.split("=");
                Property<?> prop = block.getStateDefinition().getProperty(p[0].trim());
                if (prop == null) {
                    Awaken.LOG.warn("[citizens] {} has no property {}", id, p[0]);
                    continue;
                }
                state = with(state, prop, p[1].trim());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> prop, String value) {
        return prop.getValue(value).map(v -> state.setValue(prop, v)).orElse(state);
    }

    /** For commands: every player near a lamp sees it as the ledger has it. */
    void forceLamps() {
        for (int k = 0; k < lampHead.length; k++) {
            BlockPos pos = lampHead[k];
            if (!level.isLoaded(pos)) continue;
            BlockState s = level.getBlockState(pos);
            if (s.is(CitizensContent.STREET_LAMP.get()) || s.is(Blocks.LANTERN)) level.setBlock(pos, lampState(d.lamps[k]), Block.UPDATE_ALL);
        }
        darkDirty = true;
    }
}
