package dev.awaken.finds.sets;

import dev.awaken.finds.Kind;
import dev.awaken.finds.Kind.Anchor;
import dev.awaken.finds.Kind.FSet;
import dev.awaken.finds.Kind.Made;
import dev.awaken.finds.Kind.Tell;
import dev.awaken.finds.Kind.Verb;
import dev.awaken.finds.Scene;
import dev.awaken.finds.kit.Dress;
import dev.awaken.finds.kit.Mats;
import dev.awaken.finds.kit.Props;
import dev.awaken.finds.verb.*;
import dev.awaken.living.region.RegionMap.Group;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Ringing Stones (ring): things that sound when struck, and the order they want. */
final class Ringing {
    private Ringing() {}

    private static final net.minecraft.world.entity.EntityType<?> FROG = net.minecraft.world.entity.EntityTypes.FROG;

    /** The Rock Gong: strike it and a stone somewhere within earshot rings back for twelve seconds. Find that stone and touch it. Numbers: 0 ringing. */
    private static final class Echo implements Script {
        private boolean strike(Ctx c, String part) {
            if (!part.equals("gong")) return false;
            c.v(0, 1);
            c.clock(c.now());
            c.sound(c.pos("gong", 0), net.minecraft.sounds.SoundEvents.BELL_BLOCK, 2.0F, 0.5F);
            return true;
        }

        @Override public boolean hit(Ctx c, net.minecraft.server.level.ServerPlayer p, String part, int i, net.minecraft.world.item.ItemStack held) {
            return strike(c, part);
        }

        @Override
        public boolean use(Ctx c, net.minecraft.server.level.ServerPlayer p, String part, int i, net.minecraft.world.item.ItemStack held) {
            if (part.equals("answer")) {
                if (c.v(0) == 1) c.complete(p);
                return true;
            }
            return strike(c, part);
        }

        @Override
        public void tick(Ctx c) {
            if (c.v(0) != 1) return;
            long t = c.now() - c.clock();
            if (t > 240) {
                c.v(0, 0);
                return;
            }
            if (t % 20 < 5) {
                c.sound(c.pos("answer", 0), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 2.0F, 0.7F);
                c.puff(c.pos("answer", 0).above(), net.minecraft.core.particles.ParticleTypes.NOTE, 1, 0.2, 0);
            }
        }
    }

    /** The Choir Rocks: a pillar holds its note for six seconds after you pass it. All nine at once finishes it. Nothing is kept: it's one breath of running. */
    private static final class Choir implements Script {
        private final long[] sung = new long[9];

        @Override public int every() { return 2; }

        @Override
        public void tick(Ctx c) {
            int n = c.count("pillar");
            net.minecraft.server.level.ServerPlayer last = null;
            for (var p : c.players(c.kind().reach() + 2))
                for (int i = 0; i < n; i++) {
                    var at = c.pos("pillar", i);
                    if (p.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) < 2.4 * 2.4) {
                        if (c.now() - sung[i] > 20) {
                            c.level.playSound(null, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME, net.minecraft.sounds.SoundSource.BLOCKS,
                                    1.2F, (float) (0.5 * Math.pow(2, i / 9.0)));
                        }
                        sung[i] = c.now();
                        last = p;
                    }
                }
            int singing = 0;
            for (int i = 0; i < n; i++) {
                if (sung[i] != 0 && c.now() - sung[i] <= 120) {
                    singing++;
                    if (c.now() % 10 == 0) c.puff(c.pos("pillar", i).above(), net.minecraft.core.particles.ParticleTypes.NOTE, 1, 0.15, 0);
                }
            }
            if (singing == n && last != null) c.complete(last);
        }
    }

    /** Every find that isn't finished, within half a mile, rings once from its own direction. */
    static void ringAll(Ctx c) {
        for (var p : c.players(24))
            for (var be : dev.awaken.finds.Hearts.all(c.level)) {
                var s = be.site();
                if (s == null || be.done() || be == c.heart) continue;
                double dx = s.x() - p.getX(), dz = s.z() - p.getZ(), d = Math.sqrt(dx * dx + dz * dz);
                if (d < 8 || d > 800) continue;
                c.level.playSound(null, p.getX() + dx / d * 10, p.getY() + 1, p.getZ() + dz / d * 10, net.minecraft.sounds.SoundEvents.BELL_BLOCK, net.minecraft.sounds.SoundSource.BLOCKS,
                        1.0F, (float) (1.6 - d / 800));
            }
    }

    static void init() {
        Kind.of(FSet.RINGING, 1, "three_stones", "The Three Stones").verb(Verb.RING).curio().rank(0).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("Three standing stones of different heights. Tap one and it rings.")
                .learned("The wind plays them. Play it back and the tallest opens.")
                .reach(6).tell(Tell.CHIME).icon(Props.STANDING_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    int[] xs = {-3, 0, 3}, zs = {0, 1, -1};
                    for (int i = 0; i < 3; i++) {
                        for (int k = 0; k < i; k++) s.up(xs[i], zs[i], k, k == 0 ? m.mossy() : m.stone());
                        s.partOn("stone", xs[i], zs[i], i, Props.STANDING_STONE.get().of(i == 1 ? 1 : 0, Direction.from2DDataValue(s.rnd.nextInt(4))));
                    }
                    s.partOn("cache", 3, 0, 0);
                    s.tell(0, 1, 3);
                    Dress.skirt(s, 6, m);
                })
                .script(() -> Notes.of("stone", 3))
                .play(t -> { for (int i : Notes.order(t.seed(), 3, 3)) t.use("stone", i); })
                .add();

        Kind.of(FSet.RINGING, 2, "chime_tree", "The Chime Tree").verb(Verb.RING).curio().rank(0).lands(Group.FOREST, Group.DARK, Group.SNOW, Group.PLAINS).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A dead tree hung with chimes of bone and shell. One of them has lost its middle string.")
                .learned("With a bone tied back on, the whole tree played, and the birds came to it.")
                .reach(6).tell(Tell.CHIME).icon(Props.WIND_CHIME)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    BlockState log = m.log(), bx = log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X) : log,
                            bz = log.hasProperty(RotatedPillarBlock.AXIS) ? log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z) : log;
                    s.column(0, 0, 0, 5, log);
                    for (int i = 1; i <= 2; i++) {
                        s.up(i, 0, 4, bx);
                        s.up(-i, 0, 3, bx);
                        s.up(0, i, 5, bz);
                        s.up(0, -i, 4, bz);
                    }
                    s.up(1, 0, 3, Props.WIND_CHIME.get().of(0));
                    s.up(2, 0, 3, Props.WIND_CHIME.get().of(0, Direction.EAST));
                    s.partOn("gap", -2, 0, 2, Props.WIND_CHIME.get().of(1));
                    s.up(-1, 0, 2, Props.WIND_CHIME.get().of(0, Direction.EAST));
                    s.up(0, 2, 4, Props.WIND_CHIME.get().of(0));
                    s.up(0, -2, 3, Props.WIND_CHIME.get().of(0, Direction.EAST));
                    s.tell(0, 0, 4);
                    s.partOn("cache", 1, 1, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Sockets.need("gap", st -> st.is(net.minecraft.world.item.Items.BONE) || st.is(net.minecraft.world.item.Items.NAUTILUS_SHELL) || st.is(net.minecraft.world.item.Items.BONE_MEAL),
                        i -> Props.WIND_CHIME.get().of(0)))
                .after(c -> {
                    c.sound(c.site.ground().above(4), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 1.1F);
                    Dry.birds(c, 3);
                })
                .play(t -> t.hold(net.minecraft.world.item.Items.BONE).use("gap", 0))
                .add();

        Kind.of(FSet.RINGING, 3, "rock_gong", "The Rock Gong").verb(Verb.RING).curio().rank(1).lands(Group.BADLANDS, Group.DESERT, Group.MOUNTAIN).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A slab of dark stone hung in a timber frame, with a dent worn in its middle.")
                .learned("Struck, something within earshot rang back. You found it before the echo died.")
                .reach(14).slope(4).icon(Props.GONG)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(3);
                    s.partOn("gong", 0, 0, 0, Props.GONG.get().of(0));
                    double a = s.rnd.nextDouble() * 6.283;
                    int x = (int) Math.round(Math.cos(a) * 11), z = (int) Math.round(Math.sin(a) * 11);
                    s.on(x, z, m.mossy());
                    s.partOn("answer", x, z, 1, Props.STANDING_STONE.get().of(2));
                    s.partOn("cache", x + (x > 0 ? -1 : 1), z, 0);
                    Dress.scatter(s, x, z, 2, 3, () -> Dress.pebbles(s));
                    Dress.skirt(s, 4, m);
                })
                .script(Echo::new)
                .play(t -> t.hit("gong", 0).use("answer", 0))
                .add();

        Kind.of(FSet.RINGING, 4, "frog_stones", "The Frog Stones").verb(Verb.RING).curio().rank(1).lands(Group.SWAMP, Group.JUNGLE, Group.FOREST).anchor(Anchor.FLAT).made(Made.BEAST)
                .see("Four flat stones across a pond, a frog on a pad by each, and a big toad on the bank that keeps starting a tune and never finishing it.")
                .learned("Each stone is one note. Stepped out in the toad's order, it coughed up something shiny.")
                .reach(7).slope(1).icon(() -> net.minecraft.world.item.Items.LILY_PAD)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(6);
                    for (int x = -4; x <= 4; x++)
                        for (int z = 1; z <= 6; z++) {
                            boolean rim = x == -4 || x == 4 || z == 1 || z == 6;
                            s.put(x, 0, z, rim ? (s.rnd.nextBoolean() ? Blocks.MUD.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState()) : Blocks.WATER.defaultBlockState());
                            s.put(x, -1, z, Blocks.MUD.defaultBlockState());
                        }
                    int[][] at = {{-2, 3}, {0, 4}, {2, 3}, {0, 2}};
                    for (int[] a : at) {
                        s.put(a[0], 0, a[1], m.mossy());
                        s.part("stone", a[0], 0, a[1]);
                    }
                    s.put(-3, 1, 4, Blocks.LILY_PAD.defaultBlockState());
                    s.put(3, 1, 4, Blocks.LILY_PAD.defaultBlockState());
                    s.put(1, 1, 5, Blocks.LILY_PAD.defaultBlockState());
                    s.spawn(FROG, -3, 4, Scene.Spawn.NO_AI, "frog");
                    s.spawn(FROG, 3, 4, Scene.Spawn.NO_AI, "frog");
                    s.spawn(Life.type("bog_toad", FROG), 0, 0, Scene.Spawn.NO_AI, "toad");
                    s.on(1, 0, Blocks.FERN.defaultBlockState());
                    s.partOn("cache", -1, 0, 0);
                    Dress.skirt(s, 3, m);
                })
                .script(() -> Notes.of("stone", 4).byFoot())
                .play(t -> { for (int i : Notes.order(t.seed(), 4, 4)) t.use("stone", i); })
                .add();

        Kind.of(FSet.RINGING, 5, "bell_in_the_well", "The Bell in the Well").verb(Verb.RING).find().rank(2).lands(Group.PLAINS, Group.FOREST, Group.DESERT).anchor(Anchor.FLAT).made(Made.BUILT)
                .see("A dry well with a winch and a rope going down. Something heavy is on the end of it.")
                .learned("A bell, hauled up hand over hand, with its tongue lying in the grass. Rung, the water came back.")
                .reach(7).icon(Props.OLD_BELL)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    for (int dy = -5; dy <= 0; dy++) s.put(2, dy, 0, Blocks.AIR.defaultBlockState());
                    s.put(2, -6, 0, Blocks.GRAVEL.defaultBlockState());
                    for (int x = 1; x <= 3; x++)
                        for (int z = -1; z <= 1; z++)
                            if (x != 2 || z != 0) {
                                s.on(x, z, x != 2 && z != 0 ? m.slab() : m.wall());
                                for (int dy = -5; dy <= 0; dy++) s.put(x, dy, z, dy % 2 == 0 ? m.stone() : m.mossy());
                            }
                    s.partOn("winch", 2, -1, 1, Props.WINCH.get().of(0, Direction.EAST));
                    s.part("load", 2, -5, 0);
                    s.put(2, -5, 0, Props.OLD_BELL.get().of(0));
                    for (int dy = -4; dy <= 1; dy++) s.part("load", 2, dy, 0);
                    s.part("bell", 2, 1, 0);
                    for (int dy = -5; dy <= -1; dy++) s.part("shaft", 2, dy, 0);
                    s.partOn("loose", -2, 2, 0, Props.BELL_TONGUE.get().of(0));
                    s.partOn("cache", 2, 2, 0);
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Steps.of(Haul.on("winch", "load").trail(Props.ROPE_LENGTH.get().of(0)),
                        Sockets.need("bell", st -> st.is(Props.BELL_TONGUE.get().asItem()), i -> Props.OLD_BELL.get().of(1)).carry("loose"),
                        Prize.at("bell").then((c, i) -> c.sound(c.pos("bell", 0), net.minecraft.sounds.SoundEvents.BELL_BLOCK, 2.0F, 0.8F))))
                .after(c -> { for (int i = 0; i < c.count("shaft"); i++) c.set("shaft", i, Blocks.WATER.defaultBlockState()); })
                .play(t -> { for (int i = 0; i < 6; i++) t.use("winch", 0); t.use("loose", 0).hold(Props.BELL_TONGUE.get()).use("bell", 0).use("bell", 0); })
                .add();

        Kind.of(FSet.RINGING, 6, "singing_ice", "The Singing Ice").verb(Verb.RING).curio().rank(2).lands(Group.SNOW, Group.MOUNTAIN).anchor(Anchor.FLAT).made(Made.NATURAL)
                .see("A row of icicles under a ledge of rock, graded short to long.")
                .learned("Struck short to long, they rang a scale, and the ice at the back of the ledge cracked open.")
                .reach(6).tell(Tell.DRIP).icon(() -> net.minecraft.world.item.Items.ICE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    for (int x = -3; x <= 3; x++) {
                        s.up(x, 0, 5, x % 2 == 0 ? m.stone() : m.mossy());
                        s.up(x, -1, 5, m.slab());
                        if (Math.abs(x) == 3) s.column(x, 0, 0, 4, m.stone());
                    }
                    for (int i = 0; i < 5; i++) {
                        int x = -2 + i, len = i + 1;
                        for (int k = 4; k > 4 - len; k--) s.up(x, 0, k, (k == 4 ? Blocks.PACKED_ICE : Blocks.ICE).defaultBlockState());
                        s.partOn("ice", x, 0, 5 - len);
                    }
                    s.tell(0, 0, 2);
                    s.partOn("cache", 0, -1, 0, Props.BUNDLE.get().of(2));
                    s.on(1, -1, Blocks.SNOW.defaultBlockState());
                    Dress.skirt(s, 4, m);
                })
                .script(() -> Notes.of("ice", 5).lowToHigh())
                .play(t -> { for (int i = 0; i < 5; i++) t.hit("ice", i); })
                .add();

        Kind.of(FSet.RINGING, 7, "valley_horn", "The Valley Horn").verb(Verb.RING).find().rank(3).lands(Group.MOUNTAIN, Group.SNOW, Group.BADLANDS).anchor(Anchor.SLOPE).made(Made.BUILT)
                .see("A stone horn as long as a house lying on a hillside, pointed at the valley. Something has nested in it.")
                .learned("Cleared and blown, everything in the valley answered, each from where it stands.")
                .reach(7).slope(12).icon(Props.STONE_HORN)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(5);
                    s.on(0, -1, m.stone());
                    s.on(0, 2, m.slab());
                    s.partOn("horn", 0, 0, 0, Props.STONE_HORN.get().front(1, Direction.NORTH));
                    s.on(1, 0, m.slab());
                    s.on(-1, 0, m.slab());
                    s.partOn("cache", 1, 1, 0);
                    Dress.scatter(s, 0, 0, 3, 4, () -> Dress.twigs(s));
                    Dress.skirt(s, 5, m);
                })
                .script(() -> Steps.of(Prize.at("horn").then((c, i) -> {
                    c.set("horn", 0, c.at("horn", 0).setValue(dev.awaken.finds.kit.PropBlock.STATE, 0));
                    c.pop(c.pos("horn", 0), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FEATHER, 3));
                }), Prize.at("horn").then((c, i) -> c.sound(c.pos("horn", 0), net.minecraft.sounds.SoundEvents.RAVAGER_STUNNED, 3.0F, 0.5F))))
                .after(Ringing::ringAll)
                .play(t -> t.use("horn", 0).use("horn", 0))
                .add();

        Kind.of(FSet.RINGING, 8, "choir_rocks", "The Choir Rocks").verb(Verb.RING).find().rank(4).lands(Group.DARK, Group.MOUNTAIN, Group.BADLANDS).anchor(Anchor.FLAT).made(Made.STRANGE)
                .see("Nine pillars in a ring. Walk past one and it holds a note for a few breaths, then lets it go.")
                .learned("All nine at once takes real speed, or a friend. You heard the whole chord.")
                .reach(9).needsMove().tell(Tell.HUM).icon(Props.STANDING_STONE)
                .scene(s -> {
                    Mats m = Mats.of(s.site.group());
                    s.clear(8);
                    for (int i = 0; i < 9; i++) {
                        double a = i * 6.283 / 9;
                        int x = (int) Math.round(Math.cos(a) * 7), z = (int) Math.round(Math.sin(a) * 7);
                        s.on(x, z, i % 2 == 0 ? m.mossy() : m.stone());
                        s.up(x, z, 1, Props.STANDING_STONE.get().of(i % 3 == 0 ? 2 : 0, Direction.from2DDataValue(i & 3)));
                        s.partOn("pillar", x, z, 0);
                    }
                    s.partOn("cache", 0, 1, 0, Props.CAP_STONE.get().of(0));
                    Dress.skirt(s, 8, m);
                })
                .script(Choir::new)
                .play(t -> { for (int i = 0; i < 9; i++) t.stand("pillar", i).ticks(1); })
                .add();
    }
}
