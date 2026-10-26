package dev.awaken.wilds.ward;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.Throttle;
import dev.awaken.core.api.HorizonHooks;
import dev.awaken.core.api.Promises;
import dev.awaken.core.api.WardHooks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A climb refuses what you build (SPEC §17.27; the progression plan §5.5, Euan: "If I was someone who didn't know about
 * the grapple, I'd just look at that and use blocks to build up. So we should probably block that."). At a Tether
 * Spire, a sky platform or a hero tree's crown, a survival player's block (or bucket) is refused at the first one:
 * its ghost falls away in grains of its own colour ({@link WardFx.Kind#SHED}) and the place answers, worded by what
 * the player holds. Without the rope: what its way up answers to, and, the first time at each kind of climb, a card
 * with one choice, Show me (Y), which opens the band at The Ascent, with where a line comes from on the action bar; the
 * lock is kept as a promise ({@link Promises}). With the rope: how to climb it. A toast once a visit (the place's name
 * and its way up), the whole line on the action bar, and only the action bar after that.
 */
public final class ClimbWard {
    private ClimbWard() {}

    /** A climb's words: its ward kind, its name, and what it answers to. */
    enum Site {
        SPIRE(Wards.SPIRE, "spire", "Tether Spire", "A Tether Spire"),
        SKY(Wards.SKY, "sky", "Sky Platform", "A sky platform"),
        TREE(Wards.HERO_CROWN, "tree", "Great Tree", "A great tree's crown");

        final String kind, id, title, what;

        Site(String kind, String id, String title, String what) {
            this.kind = kind;
            this.id = id;
            this.title = title;
            this.what = what;
        }

        /**
         * What the place says to someone without the rope (lang {@code awaken.wilds.climb.<id>}), whole for the action
         * bar, or in its two halves for a card or a toast (they fit 28 letters a line): {@code .sheds}, {@code .way}.
         */
        Component line(String half) {
            return Component.translatable("awaken.wilds.climb." + id + half);
        }

        /** The rope-held tip with the player's own key for the rope: whole, or short for a toast ({@code .short}). */
        Component tip(String half) {
            return Component.translatable("awaken.wilds.climb.tip." + id + half, Component.keybind("key.awaken.movement"));
        }

        static @Nullable Site of(WardHooks.Ward w) {
            for (Site s : values()) if (s.kind.equals(w.kind())) return s;
            return null;
        }
    }

    /** The place speaks once a visit; a visit is over this long after the last refusal there (five minutes). */
    static final long VISIT = 20 * 60 * 5;
    /** After that, the action bar repeats the line at most this often (the shed look and sound answer every time). */
    static final int BAR_EVERY = 60;
    /** Once per player and kind of climb: the card with Show me, and where a line comes from. */
    static final String SHOWN = "awaken:climb_shown/";
    /** The Show me card's action ({@code ward:show_me:<kind>}). */
    static final String SHOW_ME = "ward:show_me";

    /** The visit each player was last told about: the ward, and when. */
    private record Told(String ward, long at) {}

    private static final Map<UUID, Told> TOLD = new HashMap<>();

    static void init() {
        Signal.onAction("ward", (p, action, accepted) -> {
            if (accepted && action.startsWith(SHOW_ME)) AwakenNet.send(p, ShowMe.INSTANCE);
        });
    }

    static void forget(UUID id) {
        TOLD.remove(id);
    }

    static void clear() {
        TOLD.clear();
    }

    /** The climb ward at {@code pos} whose rules bind this player (wards on, survival or adventure), if any. */
    static Optional<WardHooks.Ward> at(ServerPlayer p, ServerLevel level, BlockPos pos) {
        if (!Wards.binds(p)) return Optional.empty();
        Optional<WardHooks.Ward> w = Wards.enforced(level, pos);
        return w.isPresent() && Wards.climb(w.get()) ? w : Optional.empty();
    }

    /** The promise a climb keeps for someone without the rope ("landmark:ruin:3,-7", the Horizon's own id for it). */
    static String promiseKey(WardHooks.Ward w) {
        return "landmark:" + w.id();
    }

    /**
     * A player put a block ({@code placed}, or a bucket's fluid: null) at {@code pos}. At a climb it is refused: true,
     * and the caller cancels it. Its ghost sheds, and the place answers.
     */
    public static boolean refuses(ServerPlayer p, ServerLevel level, BlockPos pos, @Nullable BlockState placed) {
        Optional<WardHooks.Ward> ward = at(p, level, pos);
        if (ward.isEmpty()) return false;
        WardHooks.Ward w = ward.get();
        Site site = Site.of(w);
        if (site == null) return false;
        WardFx.play(level, WardFx.Kind.SHED, pos, placed != null ? placed : level.getBlockState(pos));
        answer(p, level, w, site);
        return true;
    }

    /** The place answers the refusal: the card the first time at this kind, a line once a visit, then the action bar. */
    static void answer(ServerPlayer p, ServerLevel level, WardHooks.Ward w, Site site) {
        boolean rope = HorizonHooks.can(p, HorizonHooks.TETHER);
        Component line = rope ? site.tip("") : site.line("");
        long now = level.getGameTime();
        Told told = TOLD.get(p.getUUID());
        boolean fresh = told == null || !told.ward().equals(w.id()) || now - told.at() > VISIT || now < told.at();
        TOLD.put(p.getUUID(), new Told(w.id(), now));
        if (!rope) Promises.seen(p, promiseKey(w), w.box().getCenter(), Promises.Need.tier(HorizonHooks.TETHER), site.what);
        if (!fresh) {
            if (Throttle.allow(p, "climb_bar", BAR_EVERY)) p.sendOverlayMessage(line);
            return;
        }
        Throttle.allow(p, "climb_bar", BAR_EVERY);
        String shown = SHOWN + site.id;
        if (!rope && Progression.profile(p).counter(shown) == 0) {
            // the first refusal at this kind of climb: what it answers to, where a line comes from, and one link
            Progression.setCounter(p, shown, 1);
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, site.line(".sheds")).body(site.line(".way"))
                    .choice(SHOW_ME + ":" + site.id, "Show me", "Not now").topic("climb").ttl(20 * 30).build());
            // where a line comes from, under the card while it is up
            p.sendOverlayMessage(Component.translatable("awaken.wilds.climb.vault"));
            DirectorLog.log(p, "wilds", "a climb refused a block, first at a " + site.id + " (" + w.id() + "): the card offers The Ascent");
            return;
        }
        // a fresh visit: the place says it once, as the answer to the block (untagged, so the director shows it at once)
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(site.title)).body(rope ? site.tip(".short") : site.line(".way"))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 7).build());
        p.sendOverlayMessage(line);
        DirectorLog.log(p, "wilds", "a climb refused a block (" + w.id() + (rope ? ", has the rope)" : ")"));
    }

    // ---- Show me: the band opens at The Ascent ------------------------------------------------------------------

    /** Server to client: open the band (K) at The Ascent (the Show me card's yes). */
    public record ShowMe() implements CustomPacketPayload {
        public static final ShowMe INSTANCE = new ShowMe();
        public static final Type<ShowMe> TYPE = new Type<>(Awaken.id("ward_show_me"));
        public static final StreamCodec<ByteBuf, ShowMe> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<ShowMe> type() {
            return TYPE;
        }
    }
}
