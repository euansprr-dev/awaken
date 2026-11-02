package dev.awaken.bells;

import dev.awaken.Awaken;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Hanging a bell (SPEC §43.16, stages 2 and 3). A bell hangs on a hook inside a Hearth you belong to: the Bell Frame by the
 * fire (three hooks, any bell but a Great Bell), a Bell Beam (four hooks, any bell but a Great Bell, 8 or more blocks above
 * the fire) or a Great Beam (one hook, Great Bells only, 12 or more above it). A home's song holds one of each bell. Whoever
 * hung a bell, or the home's owner, can take it down. Each home keeps an index of where its bells hang
 * ({@link BellWorld.Home#placed}) so its song knows them while their chunks are unloaded.
 */
final class Hanging {
    private Hanging() {}

    /** How far above the fire a Bell Beam's bells carry, and a Great Beam's. */
    static final int BEAM_ABOVE = 8, GREAT_ABOVE = 12;

    /** The Great Bells (the Provings') and the Seed: only a Great Beam holds one. */
    static boolean great(BellCatalog.Def d) {
        return d.source() == BellCatalog.Source.PROVING || d.source() == BellCatalog.Source.SEED;
    }

    /** How many hooks a hook block has. */
    static int hooks(BlockState s) {
        if (s.is(BellContent.BELL_FRAME.get())) return 3;
        if (s.is(BellContent.BELL_BEAM.get())) return 4;
        if (s.is(BellContent.GREAT_BEAM.get())) return 1;
        return 0;
    }

    /** Hook {@code i}'s point in model units (x along the beam, y where the bell hangs from, z across), facing north. */
    static float[] hookModel(BlockState s, int i) {
        if (s.is(BellContent.BELL_FRAME.get())) return new float[]{2.7F + 5.3F * i, 14F, 8F};
        if (s.is(BellContent.BELL_BEAM.get())) return new float[]{2.5F + 3.67F * i, 13F, 8F};
        return new float[]{8F, 12F, 8F};
    }

    /** Hook {@code i}'s point inside its block (0 to 1 on each axis), turned the way the block faces. */
    static Vec3 hookPoint(BlockState s, int i) {
        float[] m = hookModel(s, i);
        double x = m[0] / 16.0, y = m[1] / 16.0, z = m[2] / 16.0;
        Direction f = s.hasProperty(HorizontalDirectionalBlock.FACING) ? s.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        return switch (f) {
            case EAST -> new Vec3(1 - z, y, x);
            case SOUTH -> new Vec3(1 - x, y, 1 - z);
            case WEST -> new Vec3(z, y, 1 - x);
            default -> new Vec3(x, y, z);
        };
    }

    /** The hook nearest {@code hit} (a world point) that's free ({@code free}) or holds a bell. -1 if there's none. */
    static int nearest(HooksEntity be, Vec3 hit, boolean free) {
        BlockPos pos = be.getBlockPos();
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < be.hooks(); i++) {
            if ((be.bell(i) < 0) != free) continue;
            double d = hookPoint(be.getBlockState(), i).add(pos.getX(), pos.getY(), pos.getZ()).distanceToSqr(hit);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    // ---- hanging --------------------------------------------------------------------------------------------------

    /** A bell used on a hook block. True when it hung (the item is used up). */
    static boolean hang(ServerLevel level, BlockPos pos, ServerPlayer p, ItemStack stack, Vec3 hit) {
        return hang(level, pos, p, stack, hit, false);
    }

    /** {@code quiet}: no card (the admin's tower hangs dozens at once). */
    static boolean hang(ServerLevel level, BlockPos pos, ServerPlayer p, ItemStack stack, Vec3 hit, boolean quiet) {
        BellCatalog.Def d = BellItem.def(stack);
        if (d == null || !(level.getBlockEntity(pos) instanceof HooksEntity be)) return false;
        BlockState s = level.getBlockState(pos);
        String refused = refusal(level, pos, s, be, p, d);
        if (refused != null) {
            Bells.say(p, refused);
            return false;
        }
        int hook = nearest(be, hit, true);
        if (hook < 0) {
            Bells.say(p, s.is(BellContent.BELL_FRAME.get())
                    ? "[Your frame's full. Hang the next bell high, on a Bell Beam 8 or more above your fire. Build up over the frame, or anywhere at home.]"
                    : "[Every hook on this beam has a bell. Hang it on another.]");
            return false;
        }
        HearthRecord h = home(level, pos).orElseThrow();
        be.set(hook, d.no(), p.getUUID());
        if (!p.isCreative()) stack.shrink(1);
        index(level, h, pos, d.no());
        strike(level, pos, s, hook, d.no(), 2F);
        BellBook b = Bells.book(p);
        if (!b.held.containsKey(d.no())) {
            // a bell someone else won and gave you: it's yours now that it hangs at your home
            b.held.put(d.no(), new BellBook.Held(dev.awaken.core.PlayDays.current(level.getServer()), "given to you, and hung at your home"));
        }
        boolean first = b.once("signal");
        Bells.save(p, b);
        dev.awaken.progression.Progression.setCounter(p, Bells.COUNTER, b.count());
        String where = s.is(BellContent.BELL_FRAME.get()) ? "It's hanging by your fire." : s.is(BellContent.GREAT_BEAM.get()) ? "It's hanging high in your tower." : "It's hanging high over your home.";
        String body = where + " Its note is the " + ordinal(d.no() + 1) + " in your song." + (d.voice().words.isEmpty() ? "" : " While it hangs, " + lower(d.voice().words));
        if (!quiet) Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Bell " + d.number() + " · " + d.name()))
                .body(Component.literal(body)).priority(7).ttl(20 * 10).build());
        if (first) Bells.signalSoon(p);
        Awaken.LOG.info("[bells] {} hung {} {} at {}", p.getName().getString(), d.number(), d.id(), pos.toShortString());
        return true;
    }

    /** Why this bell can't hang here, in the System's words, or null if it can. */
    static @Nullable String refusal(ServerLevel level, BlockPos pos, BlockState s, HooksEntity be, ServerPlayer p, BellCatalog.Def d) {
        Optional<HearthRecord> h = home(level, pos);
        if (h.isEmpty() || !h.get().belongs(p.getUUID())) return "[Bells only hang inside your own Hearth.]";
        boolean greatBeam = s.is(BellContent.GREAT_BEAM.get());
        if (great(d) && !greatBeam) return "[A Great Bell's too heavy for this. It hangs on a Great Beam, 12 or more blocks above your fire.]";
        if (!great(d) && greatBeam) return "[A Great Beam is for a Great Bell. Hang this one on your frame or on a Bell Beam.]";
        int above = pos.getY() - h.get().pos.getY();
        if (!be.old && s.is(BellContent.BELL_BEAM.get()) && above < BEAM_ABOVE)
            return "[A bell hung this low won't carry. Hang it higher: 8 blocks or more above your fire.]";
        if (!be.old && greatBeam && above < GREAT_ABOVE) return "[A Great Bell has to hang high to carry: 12 blocks or more above your fire.]";
        BellWorld.Home home = BellWorld.get(level.getServer()).peek(h.get().pos);
        if (home != null && home.hung.contains(d.no())) return "[Your home's song already has this bell. This one's yours to keep.]";
        return null;
    }

    /** Sneak-use with an empty hand: the bell on the hook nearest where you touched comes down, if it's yours to take. */
    static boolean takeDown(ServerLevel level, BlockPos pos, ServerPlayer p, Vec3 hit) {
        if (!(level.getBlockEntity(pos) instanceof HooksEntity be)) return false;
        int hook = nearest(be, hit, false);
        if (hook < 0) return false;
        int no = be.bell(hook);
        Optional<HearthRecord> h = home(level, pos);
        boolean mayTake = p.getUUID().equals(be.hungBy(hook)) || h.map(r -> r.owner.equals(p.getUUID())).orElse(true) || be.hungBy(hook) == null;
        if (!mayTake) {
            Bells.say(p, "[Only whoever hung it, or the home's owner, can take it down.]");
            return true;
        }
        be.set(hook, -1, null);
        unindex(level, pos, no);
        Bells.giveBack(p, BellItem.of(no));
        level.playSound(null, pos, BellContent.note(no), SoundSource.BLOCKS, 0.4F, 1F);
        Bells.say(p, "[You took " + BellCatalog.of(no).name().replaceFirst("^The ", "the ") + " down. Its note is missing from your song until it hangs again.]");
        return true;
    }

    /** Used with an empty hand: you ring the bell nearest where you touched. True if there was one. */
    static boolean ringByHand(ServerLevel level, BlockPos pos, Vec3 hit) {
        if (!(level.getBlockEntity(pos) instanceof HooksEntity be)) return false;
        int hook = nearest(be, hit, false);
        if (hook < 0) return false;
        strike(level, pos, level.getBlockState(pos), hook, be.bell(hook), 1.2F);
        return true;
    }

    /** A hung bell rings: its note from its hook, and it swings (every client near sees it). */
    static void strike(ServerLevel level, BlockPos pos, BlockState s, int hook, int no, float volume) {
        Vec3 at = hookPoint(s, hook).add(pos.getX(), pos.getY(), pos.getZ());
        level.playSound(null, at.x, at.y, at.z, BellContent.note(no), SoundSource.BLOCKS, volume, 1F);
        level.blockEvent(pos, s.getBlock(), HooksEntity.SWING, hook);
    }

    // ---- the home's index ----------------------------------------------------------------------------------------

    static Optional<HearthRecord> home(ServerLevel level, BlockPos pos) {
        return HearthService.data(level.getServer()).containing(pos, level.dimension().identifier().toString());
    }

    static void index(ServerLevel level, HearthRecord h, BlockPos pos, int no) {
        BellWorld w = BellWorld.get(level.getServer());
        BellWorld.Home home = w.home(h.pos);
        home.placed.put(no, pos.immutable());
        home.rebuild();
        w.setDirty();
    }

    /** Bell {@code no} no longer hangs at {@code pos}: out of whichever home's song had it there. */
    static void unindex(ServerLevel level, BlockPos pos, int no) {
        BellWorld w = BellWorld.get(level.getServer());
        for (BellWorld.Home home : w.homes.values())
            if (pos.equals(home.placed.get(no))) {
                home.placed.remove(no);
                home.rebuild();
                w.setDirty();
            }
    }

    /** At song time: a placed bell whose hook is loaded and no longer holds it leaves the song. */
    static void check(ServerLevel level, BellWorld.Home home) {
        boolean changed = false;
        for (Iterator<Map.Entry<Integer, BlockPos>> it = home.placed.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, BlockPos> e = it.next();
            if (!level.isLoaded(e.getValue())) continue;
            if (level.getBlockEntity(e.getValue()) instanceof HooksEntity be && be.hookOf(e.getKey()) >= 0) continue;
            it.remove();
            changed = true;
        }
        if (changed) {
            home.rebuild();
            BellWorld.get(level.getServer()).setDirty();
        }
    }

    // ---- words ----------------------------------------------------------------------------------------------------

    private static String lower(String s) {
        return s.isEmpty() ? s : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static final String[] ONES = {"", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth",
            "eleventh", "twelfth", "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth"};
    private static final String[] TENS = {"", "", "twent", "thirt", "fort", "fift", "sixt", "sevent", "eight", "ninet"};

    /** 1 → "first", 13 → "thirteenth", 42 → "forty-second", 100 → "hundredth". */
    static String ordinal(int n) {
        if (n >= 100) return "hundredth";
        if (n < 20) return ONES[n];
        int t = n / 10, u = n % 10;
        return u == 0 ? TENS[t] + "ieth" : TENS[t] + "y-" + ONES[u];
    }
}
