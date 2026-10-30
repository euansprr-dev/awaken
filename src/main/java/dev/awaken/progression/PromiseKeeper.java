package dev.awaken.progression;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.HorizonHooks;
import dev.awaken.core.api.Promises;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The progression module's side of {@link Promises} (SPEC §27): records the locks a player meets in their
 * {@link PromiseBook}, answers rank and level needs itself, re-reads the book when a rank, a level or a rung arrives,
 * and calls the player back to the nearest lock that now answers them: one toast, and the Horizon line raises it for
 * {@link #RAISED_FOR}. The very first lock a player meets says once where it's kept; after that recording is silent.
 */
public final class PromiseKeeper {
    private PromiseKeeper() {}

    /** How long a lock that just answered the player is raised on the Horizon line. */
    static final long RAISED_FOR = 20 * 60 * 10;
    /** The once-per-player flag for the first lock met (it says where they're kept). */
    static final String TAUGHT = "awaken:promise_taught";

    static void init() {
        Promises.install(new Promises.Store() {
            @Override
            public void seen(ServerPlayer p, String key, @Nullable BlockPos where, Promises.Need need, String what) {
                PromiseKeeper.seen(p, key, where, need, what);
            }

            @Override
            public void kept(ServerPlayer p, String key) {
                PromiseBook b = book(p);
                if (b.remove(key)) changed(p, b, "kept " + key);
            }

            @Override
            public void keptAll(ServerPlayer p, String prefix) {
                PromiseBook b = book(p);
                if (b.removeAll(prefix)) changed(p, b, "kept every " + prefix + "*");
            }

            @Override
            public void recheck(ServerPlayer p) {
                PromiseKeeper.recheck(p, true);
            }
        });
        // ranks and levels are this module's own keys
        Promises.addAnswer((p, need) -> switch (need.kind()) {
            case Promises.RANK -> {
                Rank r = rankByLabel(need.value());
                yield r != null && Progression.rank(p).ordinal() >= r.ordinal();
            }
            case Promises.LEVEL -> {
                try {
                    yield Progression.profile(p).level() >= Integer.parseInt(need.value());
                } catch (NumberFormatException e) {
                    yield false;
                }
            }
            default -> false;
        });
        RankHooks.onRankUp((p, r) -> recheck(p, true));
        Progression.onLevelReached((p, level) -> recheck(p, true));
        HorizonHooks.register(PromiseKeeper::horizon);
        // Saved for later, as the band lists it (SPEC §27.12)
        dev.awaken.core.api.Shown.register("saved", p -> {
            List<String> out = new ArrayList<>();
            for (PromiseBook.Entry e : book(p).entries()) out.add(e.answered() ? ready(e) : e.what() + " · needs " + e.need().words());
            return out;
        });
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.tick.ServerTickEvent.Post.class, e -> {
            if (e.getServer().getTickCount() % 40 != 17) return;
            for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) walkedIn(p);
        });
    }

    /**
     * A land that was too dangerous, answered, and now stood in: you've gone, so it's done and leaves the list (SPEC
     * §27.12). A land you're in only while it's still too dangerous stays.
     */
    static boolean walkedIn(ServerPlayer p) {
        PromiseBook b = book(p);
        for (PromiseBook.Entry e : b.entries) {
            if (!e.answered() || !inside(p, e)) continue;
            if (b.remove(e.key())) changed(p, b, "walked into " + e.key());
            return true;
        }
        return false;
    }

    public static PromiseBook book(ServerPlayer p) {
        return p.getData(ProgressionModule.PROMISES.get());
    }

    static void seen(ServerPlayer p, String key, @Nullable BlockPos where, Promises.Need need, String what) {
        if (!Progression.profile(p).awakened() || Promises.answered(p, need)) return;
        PromiseBook b = book(p);
        if (b.get(key) != null) return;
        b.add(new PromiseBook.Entry(key, p.level().dimension().identifier().toString(), Optional.ofNullable(where),
                need.kind(), need.value(), what, 0));
        changed(p, b, "seen " + key + " (" + need.words() + ")");
        if (Progression.profile(p).counter(TAUGHT) == 0) {
            Progression.setCounter(p, TAUGHT, 1);
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Saved for later"))
                    .body(Component.literal(what + ": you'll need " + need.words() + " first. Your band (K) keeps a list of "
                            + "things like this, and you'll get a reminder when you're ready."))
                    .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 9).build());
        }
    }

    /** Marks every lock the player now meets as answered; if {@code call}, calls them back to the nearest new one. */
    static void recheck(ServerPlayer p, boolean call) {
        PromiseBook b = book(p);
        if (b.isEmpty()) return;
        long now = p.level().getGameTime();
        List<PromiseBook.Entry> fresh = new ArrayList<>();
        for (int i = 0; i < b.entries.size(); i++) {
            PromiseBook.Entry e = b.entries.get(i);
            if (e.answered() || !Promises.answered(p, e.need())) continue;
            PromiseBook.Entry a = e.answer(now);
            b.set(i, a);
            fresh.add(a);
        }
        if (fresh.isEmpty()) return;
        changed(p, b, fresh.size() + " answered");
        if (!call) return;
        // the nearest one with a place in this dimension is the call; the rest are counted
        String dim = p.level().dimension().identifier().toString();
        PromiseBook.Entry near = null;
        double best = Double.MAX_VALUE;
        for (PromiseBook.Entry e : fresh) {
            if (e.where().isEmpty() || !e.dim().equals(dim) || inside(p, e)) continue;
            double d = horizontal(p.blockPosition(), e.where().get());
            if (d < best) {
                best = d;
                near = e;
            }
        }
        PromiseBook.Entry lead = near != null ? near : fresh.getFirst();
        String body = ready(lead)
                + (near != null ? " · " + bearing(p.blockPosition(), near.where().get()) : "")
                + (fresh.size() > 1 ? " · and " + (fresh.size() - 1) + " more in your band (K)" : "");
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Ready now"))
                .body(Component.literal(body)).presentation(SignalWindow.Presentation.TOAST).ttl(20 * 9).build());
        Progression.markNewThing(p, "promise_answered");
    }

    /** The Horizon line: a lock that just answered the player, raised over everything near, for {@link #RAISED_FOR}. */
    static List<HorizonHooks.Site> horizon(ServerPlayer p) {
        PromiseBook b = book(p);
        if (b.isEmpty()) return List.of();
        long now = p.level().getGameTime();
        String dim = p.level().dimension().identifier().toString();
        List<HorizonHooks.Site> out = new ArrayList<>(2);
        for (PromiseBook.Entry e : b.entries) {
            if (!e.answered() || e.where().isEmpty() || !e.dim().equals(dim) || now - e.answeredAt() > RAISED_FOR || now < e.answeredAt() || inside(p, e)) continue;
            out.add(new HorizonHooks.Site("promise/" + e.key(), ready(e), e.where().get(), "", true));
        }
        return out;
    }

    private static void changed(ServerPlayer p, PromiseBook b, String why) {
        p.setData(ProgressionModule.PROMISES.get(), b);
        p.syncData(ProgressionModule.PROMISES.get());
        DirectorLog.log(p, "progression", "promises: " + why);
    }

    static @Nullable Rank rankByLabel(String label) {
        for (Rank r : Rank.values()) if (r.label.equalsIgnoreCase(label)) return r;
        return null;
    }

    private static double horizontal(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** "640 m NE", with how high or low when it matters, "58 m above you" straight over your head (north is -Z). */
    static String bearing(BlockPos from, BlockPos to) {
        return dev.awaken.signal.NowWay.named(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    /**
     * What an answered lock says in plain words (SPEC §27.1): a land is "no longer too dangerous for you", anything else "open
     * to you now". Never "answers you": that told the player nothing about what was there or what to do.
     */
    static String ready(PromiseBook.Entry e) {
        String what = e.what().isEmpty() ? e.what() : Character.toUpperCase(e.what().charAt(0)) + e.what().substring(1);
        return e.key().startsWith(REGION) ? what + " is no longer too dangerous for you" : what + " is open to you now";
    }

    static final String REGION = "region:";

    /**
     * Whether the player already stands in the land this lock is (a region's place is only where they first met its danger,
     * on its edge: with them in it there is nothing to point at, and nothing to walk to).
     */
    static boolean inside(ServerPlayer p, PromiseBook.Entry e) {
        if (!e.key().startsWith(REGION) || !(p.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !dev.awaken.living.api.Regions.on(level)) return false;
        try {
            return dev.awaken.living.api.Regions.regionAt(level, p.getBlockX(), p.getBlockZ()).id() == Long.parseLong(e.key().substring(REGION.length()));
        } catch (NumberFormatException ex) {
            return false;
        }
    }
}
