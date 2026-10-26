package dev.awaken.body.ascent;

import dev.awaken.above.Above;
import dev.awaken.above.AboveBlocks;
import dev.awaken.above.ruin.PrecursorRuinPiece;
import dev.awaken.above.ruin.PrecursorRuinStructure;
import dev.awaken.core.AwakenNet;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Tether Vow (SPEC §17.7): the rope is earned at a Precursor vault the player has the rank to open. Its line answers
 * C-rank (Euan, 2026-10-26: walking into a vault under-ranked must not give it), or the vault's own door rank if
 * higher, and only once the sealed door stands open. Then, stepping into the forecourt (between its two pylons) without
 * the rope, the vault notices them (the call, 3 s), the line is given and pulls them up onto the nearer pylon (the
 * line), and the vow is kept by reaching the beacon mast over the roof by rope within two minutes (kept, or the mast
 * waits). Short of that the vault only stirs: its light rises and falls back, and it says what it waits for.
 * The vault's blueprint is fixed (RuinBuilder); its turn is read from the pylons, which stand only at the front.
 */
final class TetherVow {
    private TetherVow() {}

    /** Stages sent to the client ({@link AscentPayloads.Vow}). STIR: the vault notices someone it isn't ready for. */
    static final int CALL = 0, LINE = 1, KEPT = 2, LAPSED = 3, STIR = 4;
    /** Ticks of the call before the line, and how long the mast waits. */
    static final int CALL_TICKS = 60, MAST_TICKS = 2400;
    /** The lowest rank the line answers (a vault whose door asks more asks more). */
    static final Rank RANK = Rank.C;
    /** The same vault stirs for the same player, in the same state, at most this often. */
    static final int STIR_EVERY = 20 * 60 * 5;

    /** The last stir per player: which vault, whether they were short of its rank, and when. */
    private record Stirred(BlockPos vault, boolean short_, long at) {}

    private static final Map<UUID, Stirred> STIRRED = new HashMap<>();

    private static final class Live {
        final Site site;
        final long start;
        int stage = CALL;
        Live(Site site, long start) {
            this.site = site;
            this.start = start;
        }
    }

    private static final Map<UUID, Live> LIVE = new HashMap<>();

    /**
     * A vault in the world: its box (the blueprint's 23 x 24 x 23, local y 0 = the floor), its turn (0-3) and the rank its
     * door was built for (a {@link Rank} ordinal).
     */
    record Site(BoundingBox box, int rot, int doorRank) {
        /** Local blueprint coordinates to world (PrecursorRuinPiece's own turn inside the square footprint). */
        BlockPos world(int x, int y, int z) {
            int w = box.getXSpan() - 1, lx, lz;
            switch (rot) {
                case 1 -> { lx = w - z; lz = x; }
                case 2 -> { lx = w - x; lz = w - z; }
                case 3 -> { lx = z; lz = w - x; }
                default -> { lx = x; lz = z; }
            }
            return new BlockPos(box.minX() + lx, box.minY() + y, box.minZ() + lz);
        }

        /** World to local (x, y, z). */
        int[] local(double wx, double wy, double wz) {
            int w = box.getXSpan() - 1;
            int lx = (int) Math.floor(wx) - box.minX(), lz = (int) Math.floor(wz) - box.minZ(), y = (int) Math.floor(wy) - box.minY();
            return switch (rot) {
                case 1 -> new int[]{lz, y, w - lx};
                case 2 -> new int[]{w - lx, y, w - lz};
                case 3 -> new int[]{w - lz, y, lx};
                default -> new int[]{lx, y, lz};
            };
        }

        /** In the forecourt: between the pylons, in front of the portal, on the paving (feet up to four above it). */
        boolean inForecourt(double x, double y, double z) {
            int[] l = local(x, y, z);
            return l[0] >= 4 && l[0] <= 18 && l[2] >= 0 && l[2] <= 3 && l[1] >= 1 && l[1] <= 5;
        }

        /** The beacon mast's light (where the vow is kept). */
        BlockPos mast() {
            return world(11, 21, 12);
        }

        /** The sealed door's core (RuinBuilder: the 3 x 3 door in the wall at z 12). */
        BlockPos door() {
            return world(11, 2, 12);
        }

        /** The rank this vault's line answers: C, or its door's rank if that is higher. */
        Rank needs() {
            return Rank.of(Math.max(RANK.ordinal(), doorRank));
        }

        /** The sealed door has been opened (no panel or core left in its place). */
        boolean doorOpen(Level level) {
            for (int x = 10; x <= 12; x++) {
                for (int y = 1; y <= 3; y++) {
                    var s = level.getBlockState(world(x, y, 12));
                    if (s.is(AboveBlocks.DOOR.get()) || s.is(AboveBlocks.DOOR_CORE.get())) return false;
                }
            }
            return true;
        }
    }

    /** The vault whose blueprint holds {@code pos}, or null (not a vault, or its turn can't be read: an old layout). */
    static @Nullable Site siteAt(ServerLevel level, BlockPos pos) {
        StructureStart start = level.structureManager().getStructureWithPieceAt(pos, Above.RUINS_TAG);
        if (!start.isValid() || !(start.getStructure() instanceof PrecursorRuinStructure prs) || !prs.kind().isVault()) return null;
        // the piece's own box (the blueprint's): the start's is padded on every side
        var piece = start.getPieces().isEmpty() ? null : start.getPieces().getFirst();
        BoundingBox box = piece == null ? start.getBoundingBox() : piece.getBoundingBox();
        int doorRank = piece instanceof PrecursorRuinPiece prp ? prp.doorRank() : RANK.ordinal();
        int rot = rotationOf(level, box);
        return rot < 0 ? null : new Site(box, rot, doorRank);
    }

    /**
     * The vault's turn: the one that finds the pylons' own blocks at the front corners (the amber lights at their tips,
     * the trim under them, the cornice). Terrain can't fake those, and a vault dug into a hill keeps them. At least
     * four of the six (weathering may take one or two); -1 when no turn does (a layout from before the redesign).
     */
    static int rotationOf(Level level, BoundingBox box) {
        int best = -1, bestHits = 3;
        for (int rot = 0; rot < 4; rot++) {
            Site s = new Site(box, rot, RANK.ordinal());
            int hits = 0;
            for (int px : new int[]{2, 20}) {
                if (level.getBlockState(s.world(px, 20, 1)).is(AboveBlocks.WARNING_LIGHT.get())) hits++;
                if (level.getBlockState(s.world(px, 19, 1)).is(AboveBlocks.TRIM.get())) hits++;
                if (level.getBlockState(s.world(px, 11, 1)).is(net.minecraft.world.level.block.Blocks.POLISHED_DEEPSLATE)) hits++;
            }
            if (hits > bestHits) {
                best = rot;
                bestHits = hits;
            }
        }
        return best;
    }

    private static boolean solid(Level level, BlockPos p) {
        return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /**
     * Where the line takes them: the top of the nearer pylon, on one of the plates round its spire (inner one first;
     * weathering may have taken some), as an anchor point a hair over the plate. Null if every plate is gone.
     */
    static double @Nullable [] pylonTop(Level level, Site s, double px, double pz) {
        int[] l = s.local(px, 0, pz);
        boolean west = l[0] <= 11;
        int ox = west ? 1 : 19, inner = west ? 3 : 19;
        int[][] plates = {{inner, 1}, {ox + 1, 2}, {ox + 1, 0}, {west ? 1 : 21, 1}};
        for (int[] c : plates) {
            BlockPos b = s.world(c[0], 12, c[1]);
            if (solid(level, b)) return new double[]{b.getX() + 0.5, b.getY() + 1.05, b.getZ() + 0.5};
        }
        return null;
    }

    /** Reached the mast: over the crown (feet 15 above the floor or more) within five of the mast's line. */
    static boolean atMast(Site s, double x, double y, double z) {
        BlockPos m = s.mast();
        double dx = x - (m.getX() + 0.5), dz = z - (m.getZ() + 0.5);
        return dx * dx + dz * dz <= 25 && y >= s.box().minY() + 15;
    }

    // ---- the director (from AscentServer's player tick) -----------------------------------------------------------

    static void tick(ServerPlayer p, long now) {
        Live v = LIVE.get(p.getUUID());
        if (v == null) {
            // a look once a second, staggered by player
            if ((now + (p.getUUID().hashCode() & 15)) % 20 != 0 || !eligible(p)) return;
            Site s = siteAt(p.level(), p.blockPosition());
            if (s != null) look(p, s, now);
            return;
        }
        long age = now - v.start;
        if (v.stage == CALL && age >= CALL_TICKS) {
            v.stage = LINE;
            Ascent.grant(p, Ascent.Tier.TETHER, "tether vow");
            double[] top = pylonTop(p.level(), v.site, p.getX(), p.getZ());
            BlockPos m = v.site.mast();
            AwakenNet.send(p, top == null ? new AscentPayloads.Vow(LINE, Double.NaN, 0, 0, m.getX() + 0.5, m.getY() + 1.0, m.getZ() + 0.5)
                    : new AscentPayloads.Vow(LINE, top[0], top[1], top[2], m.getX() + 0.5, m.getY() + 1.0, m.getZ() + 0.5));
        } else if (v.stage == LINE) {
            // kept by rope: a tower to the mast reaches it, but the vow waits for the line (SPEC §17.25)
            if (atMast(v.site, p.getX(), p.getY(), p.getZ()) && dev.awaken.core.api.ArrivalHooks.byLine(p)) {
                end(p, v, KEPT);
            } else if (age > CALL_TICKS + MAST_TICKS || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(v.site.box().getCenter())) > 64 * 64) {
                end(p, v, LAPSED);
            }
        }
    }

    /** In a vault's forecourt: the vow begins if the line is theirs to take, else the vault stirs. */
    static void look(ServerPlayer p, Site s, long now) {
        if (!s.inForecourt(p.getX(), p.getY(), p.getZ())) return;
        if (ready(p, s)) begin(p, s, now);
        else stir(p, s, now);
    }

    /** The vault's line is theirs to take: their rank answers it and the door stands open. */
    static boolean ready(ServerPlayer p, Site s) {
        return Progression.rank(p).ordinal() >= s.needs().ordinal() && s.doorOpen(p.level());
    }

    /**
     * The vault notices someone it isn't ready for: short of its rank, its light rises toward the mast and falls back
     * ("Its line waits for a C-rank hunter."); ranked but with the door still sealed, its light drifts to the door. At
     * most once in {@link #STIR_EVERY} per vault and state.
     */
    static void stir(ServerPlayer p, Site s, long now) {
        boolean short_ = Progression.rank(p).ordinal() < s.needs().ordinal();
        BlockPos vault = s.box().getCenter();
        Stirred last = STIRRED.get(p.getUUID());
        if (last != null && last.vault().equals(vault) && last.short_() == short_ && now - last.at() < STIR_EVERY && now >= last.at()) return;
        STIRRED.put(p.getUUID(), new Stirred(vault, short_, now));
        BlockPos to = short_ ? s.mast() : s.door();
        AwakenNet.send(p, new AscentPayloads.Vow(STIR, s.needs().ordinal(), short_ ? 1 : 0, 0, to.getX() + 0.5, to.getY() + 0.5, to.getZ() + 0.5));
        dev.awaken.core.DirectorLog.log(p, "ascent", "a vault stirs at " + vault.toShortString() + (short_
                ? ": its line waits for " + s.needs().label + "-rank" : ": its door is still sealed"));
    }

    static boolean stirred(ServerPlayer p) {
        return STIRRED.containsKey(p.getUUID());
    }

    /** The vault notices the player (the call). */
    static void begin(ServerPlayer p, Site s, long now) {
        LIVE.put(p.getUUID(), new Live(s, now));
        BlockPos m = s.mast();
        AwakenNet.send(p, new AscentPayloads.Vow(CALL, 0, 0, 0, m.getX() + 0.5, m.getY() + 1.0, m.getZ() + 0.5));
        dev.awaken.core.DirectorLog.log(p, "ascent", "the Tether Vow begins at " + s.box().getCenter().toShortString());
    }

    private static void end(ServerPlayer p, Live v, int stage) {
        LIVE.remove(p.getUUID());
        AwakenNet.send(p, new AscentPayloads.Vow(stage, 0, 0, 0, 0, 0, 0));
        if (stage == KEPT) {
            p.setData(AscentContent.MEMORY, p.getData(AscentContent.MEMORY).withFlag(AscentMemory.VOW_KEPT));
            AscentServer.link(p, AscentMemory.Track.TETHER, 10);
            double toNext = dev.awaken.progression.Curve.xpToNext(Progression.profile(p).level());
            Progression.grantXp(p, toNext * 0.2, Progression.XpSource.DISCOVERY);
            Progression.count(p, "awaken:ascent_vow_kept");
        }
        dev.awaken.core.DirectorLog.log(p, "ascent", stage == KEPT ? "the Tether Vow is kept" : "the Tether Vow lapses (the mast waits)");
    }

    /** Awake, in the overworld, without the rope, the Ascent on, not riding or watching. */
    static boolean eligible(ServerPlayer p) {
        return p.level().dimension() == Level.OVERWORLD && !p.isSpectator() && !p.isPassenger() && Progression.profile(p).awakened()
                && !Ascent.has(p, Ascent.Tier.TETHER) && dev.awaken.body.BodyConfig.ascent();
    }

    static void forget(ServerPlayer p) {
        LIVE.remove(p.getUUID());
        STIRRED.remove(p.getUUID());
    }

    static boolean running(ServerPlayer p) {
        return LIVE.containsKey(p.getUUID());
    }
}
