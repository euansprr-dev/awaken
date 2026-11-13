package dev.awaken.gates.realms.land;

import dev.awaken.gates.realms.kit.AirPlan;
import dev.awaken.gates.realms.kit.Mats;
import dev.awaken.gates.realms.kit.V;
import dev.awaken.gates.realms.Land.Family;
import dev.awaken.gates.realms.kit.crawl.BeatSkin;
import dev.awaken.gates.realms.kit.crawl.Beats.Beat;
import dev.awaken.gates.realms.kit.crawl.Composer;
import dev.awaken.gates.realms.kit.crawl.GeneratedCrawl;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The Unlit Stair (SPEC §50.9): the Abyssal Shrine's skin on a run rolled from the seed. A shrine cut downward into nothing
 * by people who worshipped the nothing. Built, severe and exact: one dark dressed stone in tall courses with a thin pale
 * band every sixth; round the rooms, tall narrow niches cut three high into the wall, a votive burning in the bottom of
 * every second one, plain piers between with a rune let into each at head height; dark flags, one pale line down the
 * middle of every way and a pale ring in every room. Bare roofs. Nothing stands on a floor anywhere: the emptiness is the
 * look. The light is pools at the votives and the runes, with the dark kept between them.
 */
public final class UnlitStair extends GeneratedCrawl<UnlitStair.M> {
    /**
     * The theme has no Realm family of its own. Its own stone and tiles (shrine_stone, abyss_tiles) were painted for the old gates at a
     * brightness of 17 in 255: black under any lamp, so a wall of them shows no pool at all. The walls and floors here are the game's dark
     * dressed stones, which do take light (the old palette's polished blackstone, at 50, still read lum 20 on the bench under full light; polished
     * deepslate is 73); the theme's blocks are every fitting (the pale band, lines and rings, the runes, the
     * votives, the eye) and whatever should stay black (the teeth, the doors, the Lord's pillars). When shrine_stone and abyss_tiles are
     * re-toned, {@link #stone} and {@link #flag} are the only two lines to change.
     */
    public static final class M extends Mats {
        final BlockState stone = Blocks.POLISHED_DEEPSLATE.defaultBlockState(), flag = Blocks.DEEPSLATE_BRICKS.defaultBlockState(),
                cracked = Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState(), flagSlab = Blocks.DEEPSLATE_BRICK_SLAB.defaultBlockState(),
                black = b("shrine_stone"), pale = b("abyss_pale"), rune = b("abyss_rune"), eye = b("abyss_pupil"), white = b("abyss_sclera"), votive = b("abyss_candles"),
                pool = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);

        M() {
            // "amber" only so the kit's own blocks (the cache) exist; nothing of Amber's is drawn here
            super("amber", new Accents(b("abyss_rune"), b("abyss_pale"), b("abyss_sclera"), Blocks.IRON_CHAIN.defaultBlockState(), b("abyss_candles"), b("abyss_wax_stubs"), b("abyss_offering"),
                    Blocks.IRON_BARS.defaultBlockState(), b("shrine_stone")));
        }
    }

    private static volatile M mats;

    static M m() {
        if (mats == null) synchronized (UnlitStair.class) {
            if (mats == null) mats = new M();
        }
        return mats;
    }

    /** A bay along a wall: a pier, a plain cell, a niche, a plain cell. */
    private static final int BAY = 4;
    private static final List<List<String>> HERDS = List.of(List.of("void_spawn*5", "abyss_watcher"), List.of("silent_stalker*2", "void_spawn*3"),
            List.of("abyss_watcher*2", "hollow_priest"), List.of("silent_stalker*3", "abyss_watcher"));

    private Arena lord;

    public UnlitStair(Ctx ctx) {
        super(ctx, 0x554E4C54L);
        generate();
        for (Part p : parts) if (p.name.equals("lord")) lord = (Arena) p;
    }

    @Override
    protected M mats() {
        return m();
    }

    /** Its beats (SPEC §72.3): stairs that turn round holds, niches to wall-jump between; the switchback up the black shaft. */
    @Override
    protected Composer.Accents accents() {
        return Composer.Accents.of(Beat.SWITCHBACK, Family.WALL, 2, Family.LEDGE, 2);
    }

    /** Its beats in its own blocks (SPEC §72.2); the rest is its rock, floor and half step. */
    @Override
    protected BeatSkin beatSkin(UnlitStair.M m) {
        return super.beatSkin(m).lip(m.pale).scuff(m.black).beam(net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState()).rail(m.flag).plinth(m.cracked).dust(m.cracked).glow(m.rune);
    }

    @Override
    protected List<String> herd(int index) {
        return HERDS.get(Math.floorMod(index + (int) seed, HERDS.size()));
    }

    @Override
    protected List<String> ambushPack() {
        return List.of("void_spawn*6", "silent_stalker");
    }

    @Override
    protected List<String> wardenPack() {
        return List.of("silent_stalker*2", "abyss_watcher");
    }

    /** A socket in the wall; the white of an eye shows in it before it fires. */
    @Override
    protected BlockState spitOn(M m) {
        return m.white;
    }

    @Override
    protected BlockState spitOff(M m) {
        return m.eye;
    }

    @Override
    protected BlockState tooth(M m) {
        return m.black;
    }

    @Override
    protected BlockState thinFloor(M m) {
        return m.cracked;
    }

    @Override
    protected double wild() {
        return 0.15;
    }

    /** A wall is one stone, with one thin pale band every sixth course. Nothing in it is chosen by chance. */
    @Override
    protected BlockState rock(M m, int x, int y, int z) {
        return Math.floorMod(y, 6) == 0 ? m.pale : m.stone;
    }

    @Override
    protected BlockState deep(M m) {
        return m.stone;
    }

    /** Whether a cell lies down the middle of its part: two cells of floor on every side of it. */
    private boolean middle(Part part, int x, int z) {
        return part.feet2(x + 2, z) != NONE && part.feet2(x - 2, z) != NONE && part.feet2(x, z + 2) != NONE && part.feet2(x, z - 2) != NONE;
    }

    @Override
    protected BlockState skinFloor(M m, Part part, int x, int y, int z) {
        if (part instanceof Cave c) {
            // dark flags and a pale ring round the room's middle; three in the Lord's hall
            double d = Math.hypot(x - c.cx, z - c.cz);
            boolean ring = part == lord ? Math.abs(d - 5.0) < 0.55 || Math.abs(d - 9.4) < 0.55 || Math.abs(d - 17.0) < 0.55 : Math.abs(d - 3.2) < 0.55;
            return ring ? m.pale : m.flag;
        }
        // one straight pale line down the middle of a way
        if (!(part instanceof Shaft) && !(part instanceof Chasm) && middle(part, x, z)) return m.pale;
        return m.flag;
    }

    @Override
    protected BlockState step(M m) {
        return m.flagSlab;
    }

    @Override
    protected BlockState doorBlock(M m) {
        return m.black;
    }

    @Override
    protected void adorn(V v, M m, Part part, int x, int y, int z, boolean onFloor, boolean underRoof, int wx, int wz) {
        boolean narrow = part.name.equals("slip") || part.name.startsWith("side_");
        boolean room = part instanceof Cave, climb = part instanceof Shaft || part instanceof Chasm;
        int up = y - Part.airFrom(part.feet2(x, z));
        boolean free = v.get(x, y, z) == null;
        // a roof is bare, whatever course it was cut at: the pale band is a line round a wall, not a ceiling
        if (underRoof && !onFloor && v.get(x, y + 1, z) == m.pale) v.set(x, y + 1, z, m.stone);
        if (wx == 0 && wz == 0) return;
        // the wall beside you is the shrine's own rock: a niche is cut out of it and a rune is let into it, and nothing is hung on anything
        int ax = x + wx, az = z + wz, along = wx != 0 ? az : ax, bay = Math.floorMod(along, BAY);
        if (room || climb) {
            // round a room, a niche in every bay, three high, with a votive in the bottom of every second one; down a shaft or over a chasm,
            // only the lit ones, one bay in two. Each votive has its pool a cell out
            boolean lit = Math.floorMod(along, BAY * 2) == 2;
            if (bay == 2 && up >= 0 && up <= 2 && (room || lit)) {
                v.set(ax, y, az, up == 0 && lit ? m.votive : null);
                if (lit && up == 1 && free) v.set(x, y, z, m.pool);
                return;
            }
        }
        // a rune at head height in every pier of a room, and every fourth cell along a way, with its pool a cell out; a room's piers carry
        // another every fifth course above it, so a tall wall is lit as far up as it goes
        if (!climb && bay == 0 && (up == 1 || room && up > 1 && up % 5 == 1)) {
            v.set(ax, y, az, m.rune);
            if (free) v.set(x, y, z, m.pool);
        }
        if (narrow) return;
    }

    @Override
    protected void skinSpecial(V v, M m) {
        // every room is lit at its middle, over its ring, and at eight places half way out to its walls: the bench draws the dark between
        // pools brighter than the game does, so no floor is left more than a few cells from a light
        for (Part p : parts) {
            if (!(p instanceof Cave r) || p.name.equals("root")) continue;
            if (v.in(r.cx, r.feet + 3, r.cz) && v.get(r.cx, r.feet + 3, r.cz) == null) v.set(r.cx, r.feet + 3, r.cz, m.pool);
            if (r.rx < 7) continue;
            for (int i = 0; i < 8; i++) {
                int x = r.cx + (int) Math.round(Math.cos(i * Math.PI / 4) * r.rx * 0.55), z = r.cz + (int) Math.round(Math.sin(i * Math.PI / 4) * r.rz * 0.55);
                if (v.in(x, r.feet + 3, z) && v.get(x, r.feet + 3, z) == null) v.set(x, r.feet + 3, z, m.pool);
            }
        }
        if (lord == null) return;
        for (int x = Math.max(lord.x0, v.x0 - 1); x <= Math.min(lord.x1, v.x0 + 16); x++) for (int z = Math.max(lord.z0, v.z0 - 1); z <= Math.min(lord.z1, v.z0 + 16); z++) {
            if (lord.out(x, z) >= 1) continue;
            double d = Math.hypot(x - lord.cx, z - lord.cz);
            // the hall stands on black pillars, each with one lit band at head height; its three rings carry a light every fourth cell
            if (lord.pillar(x, z)) {
                for (int y = lord.feet, roof = lord.roof(x, z); y < roof; y++) v.set(x, y, z, y == lord.feet + 1 ? m.rune : m.black);
            } else if ((Math.abs(d - 5.0) < 0.55 || Math.abs(d - 9.4) < 0.55 || Math.abs(d - 17.0) < 0.55) && Math.floorMod(x + z * 3, 4) == 0 && v.get(x, lord.feet + 2, z) == null) {
                v.set(x, lord.feet + 2, z, m.pool);
            }
        }
    }

    @Override
    public dev.awaken.gates.world.FloorAir air() {
        AirPlan air = new AirPlan().shaft(0, rockTop(), 4, 0, mouthFeet(), 4, 3, 0xc8c0ff, 0.6F);
        if (lord != null) air.mist(lord.feet + 1, 5, 0x1a1630, 0.5F).light(lord.cx, lord.feet + 4, lord.cz, 0xd8d0ff, 18, 0.7F);
        return airOf(air);
    }

    /** What this kind is cut like (SPEC §50.9, "No two alike"): a shrine cut downward: tall halls, crossings, a pit to look into, and steps down in the dark. */
    @Override
    protected Style style() {
        return new Style(List.of(Cave.Form.HALL, Cave.Form.CROSS, Cave.Form.PIT, Cave.Form.PIERS), List.of(Arena.Plan.SQUARE, Arena.Plan.NAVE),
                List.of(Arena.Plan.TIERS, Arena.Plan.CROSS, Arena.Plan.NAVE), List.of(Chasm.Kind.STEPS, Chasm.Kind.BRIDGE, Chasm.Kind.LEDGES, Chasm.Kind.FALLING), true, 2.6, 6);
    }

    /** Its small pieces (SPEC §50.9 "Depth"): black stone stepped at the foot of the walls and under the roof, stubs of wax, a few candles, and nothing else: emptiness is the look. */
    @Override
    protected Trim trim(UnlitStair.M m) {
        return new Trim(named("shrine_stone_stairs"), named("shrine_stone_stairs"), null, named("abyss_wax_stubs"),
                namedAll("abyss_candles"), namedAll("abyss_veil"), 0.3);
    }

    /** The colour of its light in the air (the same as its biome's). */
    @Override
    protected int airTint() {
        return 0xd8d0ff;
    }
}
