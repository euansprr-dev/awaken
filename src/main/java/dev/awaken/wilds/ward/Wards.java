package dev.awaken.wilds.ward;

import dev.awaken.Awaken;
import dev.awaken.above.ruin.RuinKind;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.core.api.WardHooks;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Precursor wards (SPEC §17 "The Wilds"): the rules, and the ward every structure in {@code #awaken:warded} or
 * {@code #awaken:warded_sky} gets. A ward is the site's own box grown by {@link #MARGIN} on every side (a sky site's
 * reaches {@link #SKY_BELOW} further down, so a tower stalls well under the deck). Inside it, outside any Hearth's
 * radius, blocks a survival player places crumble ({@link Crumble}) and {@code #awaken:ward_sealed} blocks (the site's
 * walls) can't be broken by hand, explosion or piston. At a {@link #climb climb} (a Tether Spire, a sky platform, a hero
 * tree's crown) a block is refused outright instead ({@link ClimbWard}), and no Hearth suspends it. Existing ruins get
 * wards as soon as this loads: the box comes from the structure start already saved with the chunk.
 */
public final class Wards {
    private Wards() {}

    /** Blocks a ward grows past its site on every side. One chunk ring of structure references covers it. */
    public static final int MARGIN = 12;
    /** A sky site's ward reaches this much further down. */
    public static final int SKY_BELOW = 48;

    public static final TagKey<Structure> WARDED = TagKey.create(Registries.STRUCTURE, Awaken.id("warded"));
    public static final TagKey<Structure> WARDED_SKY = TagKey.create(Registries.STRUCTURE, Awaken.id("warded_sky"));
    /** A site's own walls: what can't be broken inside a ward (the Precursor blocks and the dressing ruins build with). */
    public static final TagKey<Block> SEALED = TagKey.create(Registries.BLOCK, Awaken.id("ward_sealed"));

    /** The wards setting (on when the config isn't loaded yet, as in GameTests). */
    public static boolean on() {
        try {
            return AwakenConfig.WARDS.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    /** Whether the ward rules bind this player: survival and adventure; creative and spectators build freely. */
    public static boolean binds(Player p) {
        return !p.isCreative() && !p.isSpectator();
    }

    /**
     * The ward whose rules hold at {@code pos}: none with wards off, and none inside a Hearth's radius (home is home),
     * except a climb's: a Hearth Anchor on a spire's plaza must not make stairs to its crown (SPEC §17.25).
     */
    public static Optional<WardHooks.Ward> enforced(ServerLevel level, BlockPos pos) {
        if (!on()) return Optional.empty();
        Optional<WardHooks.Ward> w = WardHooks.at(level, pos);
        if (w.isEmpty() || !climb(w.get()) && HearthHooks.inside(level, pos)) return Optional.empty();
        return w;
    }

    /**
     * Whether this block is part of a site's walls and stands inside a ward that holds them. A Hearth's radius gives
     * the walls to its owner, except a vault's: they keep its rank door honest, so they hold even there (Euan,
     * 2026-10-26: an under-ranked hunter must not get into a vault; a Hearth Anchor beside one let them dig round it).
     */
    public static boolean sealed(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(SEALED) || !on()) return false;
        Optional<WardHooks.Ward> w = WardHooks.at(level, pos);
        return w.isPresent() && (!HearthHooks.inside(level, pos) || rankDoored(w.get()) || climb(w.get()));
    }

    /** Ward kinds of the climbs (a hero tree's crown registers its own, {@link #HERO_CROWN}). */
    public static final String SPIRE = "precursor_tether_spire", SKY = "precursor_sky_platform", HERO_CROWN = "hero_crown";

    /**
     * A climb: a site whose way up is the line (a Tether Spire's petals, a sky platform's rings, a hero tree's crown).
     * A survival player's block is refused there at once, not crumbled: the crumble's three seconds are a nerd-pole.
     */
    public static boolean climb(WardHooks.Ward w) {
        return w.kind().equals(SPIRE) || w.kind().equals(SKY) || w.kind().equals(HERO_CROWN);
    }

    /** A site whose sealed chamber waits behind a rank door: the vaults. */
    static boolean rankDoored(WardHooks.Ward w) {
        return w.kind().equals(RuinKind.VAULT.structure.identifier().getPath())
                || w.kind().equals(RuinKind.SPAWN_VAULT.structure.identifier().getPath());
    }

    // ---- the structure source -------------------------------------------------------------------------------------

    private enum Kind { NONE, GROUND, SKY }

    /** Which structures are warded, by instance (a server's worldgen registry is fixed while it runs). */
    private static final Map<Structure, Kind> KINDS = new IdentityHashMap<>();

    static void clear() {
        KINDS.clear();
    }

    private static Kind kind(ServerLevel level, Structure s) {
        Kind k = KINDS.get(s);
        if (k == null) {
            var holder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).wrapAsHolder(s);
            k = holder.is(WARDED_SKY) ? Kind.SKY : holder.is(WARDED) ? Kind.GROUND : Kind.NONE;
            KINDS.put(s, k);
        }
        return k;
    }

    /** The ward around a site's box. */
    public static BoundingBox around(BoundingBox site, boolean sky) {
        return new BoundingBox(site.minX() - MARGIN, site.minY() - MARGIN - (sky ? SKY_BELOW : 0), site.minZ() - MARGIN,
                site.maxX() + MARGIN, site.maxY() + MARGIN, site.maxZ() + MARGIN);
    }

    /** The ward id of a structure site: its start chunk ("ruin:3,-7" for Precursor ruins, as the landmarks name them). */
    public static String siteId(Structure s, ChunkPos start) {
        return (s instanceof dev.awaken.above.ruin.PrecursorRuinStructure ? "ruin:" : "site:") + start.x() + "," + start.z();
    }

    /**
     * Every warded structure whose ward holds {@code pos}: the structure references of the loaded chunks round it (a
     * map read each; most hold none), then each referenced start's box. No searches, no chunk loads.
     */
    static void structureWards(ServerLevel level, BlockPos pos, Consumer<WardHooks.Ward> out) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (!level.hasChunk(cx + dx, cz + dz)) continue;
                LevelChunk chunk = level.getChunk(cx + dx, cz + dz);
                Map<Structure, LongSet> refs = chunk.getAllReferences();
                if (refs.isEmpty()) continue;
                for (Map.Entry<Structure, LongSet> e : refs.entrySet()) {
                    Kind k = kind(level, e.getKey());
                    if (k == Kind.NONE) continue;
                    for (long ref : e.getValue()) {
                        ChunkPos start = ChunkPos.unpack(ref);
                        if (!level.hasChunk(start.x(), start.z())) continue;
                        StructureStart s = level.getChunk(start.x(), start.z()).getStartForStructure(e.getKey());
                        if (s == null || !s.isValid()) continue;
                        BoundingBox box = around(s.getBoundingBox(), k == Kind.SKY);
                        if (!box.isInside(pos)) continue;
                        var key = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getKey(e.getKey());
                        out.accept(new WardHooks.Ward(siteId(e.getKey(), start), key == null ? "site" : key.getPath(), box));
                        return; // one ward is all a lookup needs
                    }
                }
            }
        }
    }
}
