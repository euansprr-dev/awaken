package dev.awaken.atlas;

import dev.awaken.compat.Instanced;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.OpeningHooks;
import dev.awaken.core.api.PartyHooks;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server's half of the Atlas (SPEC §16.8). Every few ticks it clears the cells within {@link #RADIUS} blocks of
 * each player's path, once they've moved a few blocks; bells clear districts (and the Choir's the city); the opening
 * run's path is kept for the Registry reveal. Changed tiles go to their owner in one batch every {@link #FLUSH} ticks.
 * Nothing is recorded inside Awaken's instanced dimensions (gates, dreams, trials): those are learned by walking and by
 * Perception, and a reused cell would show an earlier gate's rooms.
 */
public final class Discovery {
    private Discovery() {}

    /** Blocks cleared round the path. */
    public static final int RADIUS = 64;
    /** Ticks between path checks, blocks moved before a new check clears anything, ticks between batches. */
    static final int PERIOD = 5, STEP = 4, FLUSH = 10, BELL_PERIOD = 20, TRAIL_PERIOD = 10, REGION_PERIOD = 40;

    /** Per-player working state, never saved (the attachment is the record). */
    static final class Live {
        String dim;
        int lx = Integer.MIN_VALUE, lz;
        final LongOpenHashSet dirty = new LongOpenHashSet();
        boolean ripple;
        int ox, oz;
        String stage;
        Bells.Bell lastBell;
    }

    private static final Map<UUID, Live> LIVE = new HashMap<>();

    /** The client's mirror of its own player's record for the dimension it is in (filled by the client's handler). */
    public static final Explored CLIENT = new Explored();
    public static volatile String clientDim = "";

    public static AtlasData data(Player p) {
        return p.getData(AtlasModule.ATLAS.get());
    }

    static String dim(Level level) {
        return level.dimension().identifier().toString();
    }

    /** Whether the Atlas records and opens in this dimension. */
    public static boolean mapped(String dimension) {
        return !Instanced.is(dimension);
    }

    static Live live(ServerPlayer p) {
        return LIVE.computeIfAbsent(p.getUUID(), k -> new Live());
    }

    static void forget(ServerPlayer p) {
        LIVE.remove(p.getUUID());
    }

    // ---- the tick -----------------------------------------------------------------------------------------------------

    static void tick(ServerPlayer p) {
        int phase = p.tickCount + p.getId();
        if (phase % PERIOD != 0 || p.isSpectator()) return;
        Live l = live(p);
        String dim = dim(p.level());
        if (!dim.equals(l.dim)) {
            l.dim = dim;
            l.lx = Integer.MIN_VALUE;
            l.dirty.clear();
            sendAll(p);
            sendPins(p);
        }
        trail(p, l, phase);
        if (!mapped(dim)) return;
        int bx = p.getBlockX(), bz = p.getBlockZ();
        if (l.lx == Integer.MIN_VALUE || Math.abs(bx - l.lx) >= STEP || Math.abs(bz - l.lz) >= STEP) {
            l.lx = bx;
            l.lz = bz;
            around(p, l, bx, bz, RADIUS);
        }
        if (phase % BELL_PERIOD == 0) belfry(p, l);
        if (phase % REGION_PERIOD == 0) region(p);
        if (phase % FLUSH == 0) flush(p, l);
    }

    /** Clears the cells the circle touches; returns how many were new. */
    static int around(ServerPlayer p, Live l, int bx, int bz, int radius) {
        Explored e = data(p).dim(l.dim);
        int c0x = (bx - radius) >> 4, c1x = (bx + radius) >> 4, c0z = (bz - radius) >> 4, c1z = (bz + radius) >> 4, n = 0;
        for (int cz = c0z; cz <= c1z; cz++)
            for (int cx = c0x; cx <= c1x; cx++)
                if (Explored.touches(cx, cz, bx, bz, radius) && e.mark(cx, cz)) {
                    l.dirty.add(Explored.key(cx >> Explored.TILE_SHIFT, cz >> Explored.TILE_SHIFT));
                    n++;
                }
        return n;
    }

    /** {@code AtlasHooks.reveal}: clears a circle now and sends it at once, rippling out from its centre. */
    public static int reveal(ServerPlayer p, BlockPos center, int radius) {
        Live l = live(p);
        l.dim = dim(p.level());
        if (!mapped(l.dim)) return 0;
        int n = around(p, l, center.getX(), center.getZ(), radius);
        ripple(l, center.getX(), center.getZ());
        flush(p, l);
        return n;
    }

    private static void ripple(Live l, int x, int z) {
        l.ripple = true;
        l.ox = x;
        l.oz = z;
    }

    /** {@code AtlasHooks.explored}: the server's record, or the client's mirror on the client. */
    public static boolean explored(Player p, BlockPos pos) {
        if (p.level().isClientSide()) return dim(p.level()).equals(clientDim) && CLIENT.explored(pos.getX() >> 4, pos.getZ() >> 4);
        Explored e = data(p).peek(dim(p.level()));
        return e != null && e.explored(pos.getX() >> 4, pos.getZ() >> 4);
    }

    // ---- bells --------------------------------------------------------------------------------------------------------

    /** Standing in a belfry counts as ringing its bell. */
    static void belfry(ServerPlayer p, Live l) {
        BlockPos sq = CityHooks.square(p.level());
        if (sq == null) return;
        int dx = p.getBlockX() - sq.getX(), dz = p.getBlockZ() - sq.getZ();
        Bells.Bell b = Bells.at(dx, dz, 1);
        if (b == null || p.getY() < sq.getY() + b.belfry() - 2) return;
        ring(p, b);
    }

    /** A bell in the city was rung by this player (or they stand in its belfry): clear its district, or the city. */
    static int ring(ServerPlayer p, BlockPos bellPos) {
        BlockPos sq = CityHooks.square(p.level());
        if (sq == null) return 0;
        Bells.Bell b = Bells.at(bellPos.getX() - sq.getX(), bellPos.getZ() - sq.getZ(), 2);
        return b == null ? 0 : ring(p, b);
    }

    static int ring(ServerPlayer p, Bells.Bell b) {
        Live l = live(p);
        l.dim = dim(p.level());
        if (l.lastBell == b) return 0; // already cleared from here; nothing new until another bell
        l.lastBell = b;
        BlockPos sq = CityHooks.square(p.level());
        Level level = p.level();
        Explored e = data(p).dim(l.dim);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int reach = 360, n = 0; // past the wall (radius 240) and the yards outside it
        int c0x = (sq.getX() - reach) >> 4, c1x = (sq.getX() + reach) >> 4, c0z = (sq.getZ() - reach) >> 4, c1z = (sq.getZ() + reach) >> 4;
        for (int cz = c0z; cz <= c1z; cz++)
            for (int cx = c0x; cx <= c1x; cx++) {
                at.set((cx << 4) + 8, sq.getY(), (cz << 4) + 8);
                String d = CityHooks.district(level, at);
                boolean clears = b.wholeCity() ? d != null || CityHooks.inCity(level, at) : b.district().equals(d);
                if (clears && e.mark(cx, cz)) {
                    l.dirty.add(Explored.key(cx >> Explored.TILE_SHIFT, cz >> Explored.TILE_SHIFT));
                    n++;
                }
            }
        if (n > 0) {
            ripple(l, sq.getX() + b.cx(), sq.getZ() + b.cz());
            flush(p, l);
            p.sendSystemMessage(Component.translatable(b.wholeCity() ? "atlas.awaken.bell.city" : "atlas.awaken.bell.district", b.name()), true);
        }
        return n;
    }

    // ---- regions and pins ---------------------------------------------------------------------------------------------

    /** The Living Wilds region you stand in joins your Atlas's lettering the first time you're in it. */
    static void region(ServerPlayer p) {
        if (!dev.awaken.living.api.Regions.on(p.level())) return;
        var r = dev.awaken.living.api.Regions.regionAt(p.level(), p.getBlockX(), p.getBlockZ());
        AtlasData d = data(p);
        for (AtlasData.Region known : d.regions) if (known.id() == r.id()) return;
        d.regions.add(new AtlasData.Region(r.id(), r.name(), r.heartX(), r.heartZ(), r.low(), r.high()));
        AwakenNet.send(p, new AtlasNet.Regions(List.copyOf(d.regions)));
    }

    /** Drops a pin (on ground you know), or takes back your own pin nearest the spot; your party sees the change. */
    public static boolean pin(ServerPlayer p, int x, int y, int z, boolean remove) {
        String dim = dim(p.level());
        AtlasData d = data(p);
        boolean changed = false;
        if (remove) {
            AtlasData.Pin best = null;
            long bestD = 24L * 24;
            for (AtlasData.Pin pin : d.pins) {
                long dx = pin.x() - x, dz = pin.z() - z;
                if (pin.dim().equals(dim) && dx * dx + dz * dz <= bestD) {
                    bestD = dx * dx + dz * dz;
                    best = pin;
                }
            }
            changed = best != null && d.pins.remove(best);
        } else if (mapped(dim) && explored(p, new BlockPos(x, y, z))) {
            if (d.pins.size() >= AtlasData.PINS_MAX) d.pins.removeFirst(); // the oldest goes
            d.pins.add(new AtlasData.Pin(dim, x, y, z));
            changed = true;
        }
        if (changed) for (ServerPlayer m : party(p)) sendPins(m);
        return changed;
    }

    /** The party members online, this player first. */
    static List<ServerPlayer> party(ServerPlayer p) {
        List<ServerPlayer> out = new ArrayList<>();
        out.add(p);
        for (UUID id : PartyHooks.members(p)) {
            if (id.equals(p.getUUID())) continue;
            ServerPlayer m = p.level().getServer().getPlayerList().getPlayer(id);
            if (m != null) out.add(m);
        }
        return out;
    }

    /** A pin colour of its own for each player: a bright hue from their id. */
    static int colour(UUID id) {
        float h = (id.hashCode() & 0xFFFF) / 65536f * 6f, f = h - (int) h, lo = 0.45f, up = lo + (1f - lo) * f, dn = 1f - (1f - lo) * f;
        float[] rgb = switch ((int) h) {
            case 0 -> new float[]{1f, up, lo};
            case 1 -> new float[]{dn, 1f, lo};
            case 2 -> new float[]{lo, 1f, up};
            case 3 -> new float[]{lo, dn, 1f};
            case 4 -> new float[]{up, lo, 1f};
            default -> new float[]{1f, lo, dn};
        };
        return 0xFF000000 | ((int) (rgb[0] * 255) << 16) | ((int) (rgb[1] * 255) << 8) | (int) (rgb[2] * 255);
    }

    /** Every pin of this player's party in their dimension. */
    static void sendPins(ServerPlayer viewer) {
        String dim = dim(viewer.level());
        List<AtlasNet.PinView> out = new ArrayList<>();
        for (ServerPlayer m : party(viewer))
            for (AtlasData.Pin pin : data(m).pins)
                if (pin.dim().equals(dim))
                    out.add(new AtlasNet.PinView(m.getName().getString(), colour(m.getUUID()), pin.x(), pin.y(), pin.z(), m == viewer));
        AwakenNet.send(viewer, new AtlasNet.Pins(out));
    }

    // ---- the opening run ----------------------------------------------------------------------------------------------

    /** Keeps the run's path while the opening's run is on; when the opening ends, sends it for the Registry reveal. */
    static void trail(ServerPlayer p, Live l, int phase) {
        String stage = OpeningHooks.stage(p);
        AtlasData d = data(p);
        if (stage != null && (stage.equals("run") || stage.equals("arrival")) && phase % TRAIL_PERIOD == 0 && d.trail.size() < AtlasData.TRAIL_MAX * 3) {
            int x = p.getBlockX(), y = p.getBlockY(), z = p.getBlockZ(), s = d.trail.size();
            boolean moved = s < 3 || Math.abs(x - d.trail.getInt(s - 3)) + Math.abs(z - d.trail.getInt(s - 1)) >= 3;
            if (moved) {
                d.trail.add(x);
                d.trail.add(y);
                d.trail.add(z);
            }
        }
        if (l.stage != null && stage == null && !d.runShown) {
            d.runShown = true;
            AwakenNet.send(p, new AtlasNet.Trail(List.copyOf(d.trail), true));
        }
        l.stage = stage;
    }

    // ---- sending ------------------------------------------------------------------------------------------------------

    static void flush(ServerPlayer p, Live l) {
        if (l.dirty.isEmpty()) return;
        Explored e = data(p).dim(l.dim);
        List<Explored.TileBits> tiles = new ArrayList<>(l.dirty.size());
        for (LongIterator it = l.dirty.iterator(); it.hasNext(); ) {
            long k = it.nextLong();
            long[] bits = e.tile(Explored.keyX(k), Explored.keyZ(k));
            if (bits != null) tiles.add(new Explored.TileBits(Explored.keyX(k), Explored.keyZ(k), bits.clone()));
        }
        l.dirty.clear();
        AwakenNet.send(p, new AtlasNet.Sync(l.dim, false, tiles, l.ripple, l.ox, l.oz));
        l.ripple = false;
    }

    /** Everything known of the player's current dimension, replacing what the client had. */
    static void sendAll(ServerPlayer p) {
        String dim = dim(p.level());
        Explored e = data(p).peek(dim);
        AwakenNet.send(p, new AtlasNet.Sync(dim, true, e == null ? List.of() : e.tiles(), false, 0, 0));
    }

    static void login(ServerPlayer p) {
        Live l = live(p);
        l.dim = dim(p.level());
        l.stage = OpeningHooks.stage(p);
        sendAll(p);
        AtlasData d = data(p);
        if (!d.trail.isEmpty()) AwakenNet.send(p, new AtlasNet.Trail(List.copyOf(d.trail), false));
        if (!d.regions.isEmpty()) AwakenNet.send(p, new AtlasNet.Regions(List.copyOf(d.regions)));
        for (ServerPlayer m : party(p)) sendPins(m);
    }

    // ---- the party ----------------------------------------------------------------------------------------------------

    /** Shares what this player knows of their current dimension with each party member online; returns members told. */
    public static int share(ServerPlayer p) {
        String dim = dim(p.level());
        Explored mine = data(p).peek(dim);
        if (mine == null || !mapped(dim)) return 0;
        int told = 0;
        for (UUID id : PartyHooks.members(p)) {
            if (id.equals(p.getUUID())) continue;
            ServerPlayer m = p.level().getServer().getPlayerList().getPlayer(id);
            if (m == null) continue;
            int added = data(m).dim(dim).merge(mine);
            told++;
            if (added > 0) {
                if (dim(m.level()).equals(dim)) sendAll(m);
                m.sendSystemMessage(Component.translatable("atlas.awaken.shared.in", p.getDisplayName(), added), true);
            }
        }
        p.sendSystemMessage(Component.translatable(told == 0 ? "atlas.awaken.shared.none" : "atlas.awaken.shared.out", told), true);
        return told;
    }
}
