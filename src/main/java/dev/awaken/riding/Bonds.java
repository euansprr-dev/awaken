package dev.awaken.riding;

import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.RidingHooks;
import dev.awaken.gates.Gates;
import dev.awaken.hearth.trial.Trials;
import dev.awaken.powers.Powers;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who rides with whom (SPEC §55). A bond is one rider and one anchor, agreed by both. It lasts until both let go, either
 * leaves the run, either goes down or either logs off; it isn't saved, only the run's {@code riding} mark is. The server
 * decides everything: an answer only counts if this class asked that player that question.
 */
public final class Bonds {
    private Bonds() {}

    record Bond(UUID rider, UUID anchor) {
        UUID other(UUID me) {
            return me.equals(rider) ? anchor : rider;
        }
    }

    /** A question put to a player, so a client can't answer one it was never asked. */
    private record Asked(UUID who, long until) {}

    /** Counter: this player has ridden or been ridden with (the Tasks page's step goes once it is set). */
    static final String RODE = "awaken:riding/rode";
    static final int CARD_TICKS = 20 * 30;
    static final double DOOR_RANGE = 12;
    private static final int NO_GATE = -1;

    private static final Map<UUID, Bond> BONDS = new HashMap<>();
    private static final Map<String, Asked> ASKED = new HashMap<>();
    /** Who asked to let go, and the server tick it goes through if their partner says nothing. */
    private static final Map<UUID, Long> LETTING_GO = new HashMap<>();
    /** Riders already told they've gone past the thread's reach, so the toast comes once per crossing. */
    private static final Map<UUID, Boolean> PAST_REACH = new HashMap<>();
    /** Riders to bring out next tick: their anchor's run is still in the middle of ending. */
    private static final List<Runnable> PULL_OUTS = new ArrayList<>();

    // ---- reading ------------------------------------------------------------------------------------------------------

    static @Nullable Bond of(UUID player) {
        return BONDS.get(player);
    }

    public static boolean bonded(Player p) {
        return BONDS.containsKey(p.getUUID());
    }

    public static boolean rider(Player p) {
        Bond b = BONDS.get(p.getUUID());
        return b != null && b.rider().equals(p.getUUID());
    }

    static @Nullable ServerPlayer partner(ServerPlayer p) {
        Bond b = BONDS.get(p.getUUID());
        return b == null ? null : player(p.level().getServer(), b.other(p.getUUID()));
    }

    private static @Nullable ServerPlayer player(MinecraftServer s, UUID id) {
        return s.getPlayerList().getPlayer(id);
    }

    public static boolean inReach(ServerPlayer a, ServerPlayer b) {
        return a.level() == b.level() && a.distanceToSqr(b) <= RidingHooks.RANGE * RidingHooks.RANGE;
    }

    /** Every bond once, for the wire. */
    static List<Bond> all() {
        List<Bond> out = new ArrayList<>();
        for (Map.Entry<UUID, Bond> e : BONDS.entrySet()) if (e.getKey().equals(e.getValue().rider())) out.add(e.getValue());
        return out;
    }

    private static int gate(ServerPlayer p) {
        return Gates.activeRun(p).map(Gates.RunInfo::gateId).orElse(NO_GATE);
    }

    private static boolean fighting(ServerPlayer a, ServerPlayer b) {
        return Powers.inCombat(a) || Powers.inCombat(b);
    }

    private static Component line(String s) {
        return Component.literal(s);
    }

    private static String name(ServerPlayer p) {
        return p.getGameProfile().name();
    }

    private static void play(ServerPlayer p, Holder<SoundEvent> sound) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, 0.7F, 1F);
    }

    // ---- asking -------------------------------------------------------------------------------------------------------

    /** What stands in the way of this bond, in words for the player, or null if nothing does. */
    static @Nullable String whyNot(ServerPlayer rider, ServerPlayer anchor) {
        if (rider == anchor) return "It takes two people.";
        if (!Progression.profile(rider).awakened() || !Progression.profile(anchor).awakened()) return "You both need to be awakened for that.";
        if (bonded(rider) || bonded(anchor)) return "One of you is already tied to someone.";
        if (!inReach(rider, anchor)) return "You need to be within " + (int) RidingHooks.RANGE + " blocks of each other.";
        if (fighting(rider, anchor)) return "Not in the middle of a fight. Get clear of it first.";
        if (Trials.inTrial(rider) || Trials.inTrial(anchor)) return "Not during a trial.";
        int a = gate(rider), b = gate(anchor);
        if (a != b) return "You need to be in the same gate, or both outside one.";
        if (a != NO_GATE) {
            if (Gates.inScriptedGate(rider)) return "This gate is one to walk on your own.";
            if (Gates.hasRider(a)) return "Someone is already riding along in this gate. It only holds one.";
        }
        return null;
    }

    private static boolean asked(String action, ServerPlayer who, long now) {
        Asked a = ASKED.get(action);
        return a != null && a.who().equals(who.getUUID()) && a.until() > now;
    }

    /** {@code asker} wants this bond, riding themselves or bringing {@code other} along. The other has to say yes. */
    static void ask(ServerPlayer asker, ServerPlayer other, boolean askerRides) {
        ServerPlayer rider = askerRides ? asker : other, anchor = askerRides ? other : asker;
        String no = whyNot(rider, anchor);
        if (no != null) {
            Signal.toast(asker, line(no));
            return;
        }
        String action = "riding:on:" + rider.getUUID() + ":" + anchor.getUUID();
        long now = asker.level().getServer().getTickCount();
        if (asked(action, asker, now)) { // they had already asked for this very thing
            Signal.answer(asker, action, true);
            return;
        }
        if (asked(action, other, now)) {
            Signal.toast(asker, line("Still waiting for " + name(other) + " to answer."));
            return;
        }
        String title = askerRides ? name(asker) + " wants to ride along with you" : name(asker) + " wants you to ride along with them";
        String body = askerRides
                ? "They keep their powers and weapons, and can't go down while they stay within " + (int) RidingHooks.RANGE
                        + " blocks of you. Their hits count for 40%, they won't make a gate any harder, and monsters will come for you first."
                : "You keep your powers and weapons, and you can't go down while you stay within " + (int) RidingHooks.RANGE
                        + " blocks of them. Your hits count for 40%. You get your own loot and half the XP, but nothing towards your rank.";
        ASKED.put(action, new Asked(other.getUUID(), now + CARD_TICKS));
        Signal.send(other, SignalWindow.builder(SignalWindow.Kind.NOTICE, line(title)).body(line(body))
                .choice(action, "Yes", "Not now").priority(5).ttl(CARD_TICKS).build());
        Signal.toast(asker, line("Asked " + name(other) + ". They have 30 seconds to answer."));
    }

    /** A right click on a gate's door: let go if tied, otherwise offer to go in with whoever is beside you. */
    static void door(ServerPlayer p) {
        if (bonded(p)) {
            letGo(p);
            return;
        }
        ServerPlayer near = nearestFree(p);
        if (near == null) {
            Signal.toast(p, line("There's nobody close enough to go in with you."));
            return;
        }
        String action = "riding:door:" + p.getUUID() + ":" + near.getUUID();
        long now = p.level().getServer().getTickCount();
        if (asked(action, p, now)) return;
        ASKED.put(action, new Asked(p.getUUID(), now + CARD_TICKS));
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, line("Go in with " + name(near) + "?"))
                .body(line("One of you rides along: they can't go down near the other, but their hits count for 40% and they earn no rank. Which of you is it?"))
                .choice(action, "I'll ride with " + name(near), name(near) + " rides with me").priority(5).ttl(CARD_TICKS).build());
    }

    private static @Nullable ServerPlayer nearestFree(ServerPlayer p) {
        ServerPlayer best = null;
        double bestD = DOOR_RANGE * DOOR_RANGE;
        for (ServerPlayer o : p.level().players()) {
            if (o == p || o.isSpectator() || bonded(o)) continue;
            double d = o.distanceToSqr(p);
            if (d <= bestD) {
                best = o;
                bestD = d;
            }
        }
        return best;
    }

    /** {@code asker} wants to let go. Both have to agree, but silence for 30 seconds counts as yes. */
    static void letGo(ServerPlayer asker) {
        Bond b = BONDS.get(asker.getUUID());
        if (b == null) {
            Signal.toast(asker, line("You aren't tied to anyone."));
            return;
        }
        MinecraftServer s = asker.level().getServer();
        ServerPlayer other = player(s, b.other(asker.getUUID()));
        if (other == null) {
            release(s, b);
            return;
        }
        if (fighting(asker, other)) {
            Signal.toast(asker, line("Not in the middle of a fight. Get clear of it first."));
            return;
        }
        if (LETTING_GO.containsKey(other.getUUID())) { // they had already asked
            Signal.withdraw(asker, "riding:off:" + other.getUUID());
            release(s, b);
            return;
        }
        if (LETTING_GO.containsKey(asker.getUUID())) {
            Signal.toast(asker, line("Still waiting for " + name(other) + " to answer."));
            return;
        }
        long now = s.getTickCount();
        String action = "riding:off:" + asker.getUUID();
        LETTING_GO.put(asker.getUUID(), now + CARD_TICKS);
        ASKED.put(action, new Asked(other.getUUID(), now + CARD_TICKS));
        Signal.send(other, SignalWindow.builder(SignalWindow.Kind.NOTICE, line(name(asker) + " wants to let go of the thread"))
                .body(line("If you don't answer in 30 seconds, it lets go."))
                .choice(action, "Let go", "Not yet").priority(5).ttl(CARD_TICKS).build());
        Signal.toast(asker, line("Asked " + name(other) + ". If they don't answer in 30 seconds, it lets go."));
    }

    // ---- answers ------------------------------------------------------------------------------------------------------

    static void onAnswer(ServerPlayer p, String action, boolean accepted) {
        MinecraftServer s = p.level().getServer();
        if (!asked(action, p, s.getTickCount())) return; // and it stays asked: a wrong answer can't cancel someone's invitation
        ASKED.remove(action);
        String[] part = action.split(":");
        try {
            switch (part[1]) {
                case "on" -> answerOn(p, player(s, UUID.fromString(part[2])), player(s, UUID.fromString(part[3])), accepted);
                case "off" -> answerOff(p, UUID.fromString(part[2]), accepted);
                case "door" -> {
                    ServerPlayer other = player(s, UUID.fromString(part[3]));
                    if (other != null) ask(p, other, accepted);
                }
                default -> {}
            }
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            dev.awaken.Awaken.LOG.warn("[riding] a bad answer: {}", action);
        }
    }

    private static void answerOn(ServerPlayer p, @Nullable ServerPlayer rider, @Nullable ServerPlayer anchor, boolean yes) {
        if (rider == null || anchor == null) {
            Signal.toast(p, line("They've gone."));
            return;
        }
        ServerPlayer asker = rider == p ? anchor : rider;
        if (!yes) {
            Signal.toast(asker, line(name(p) + " said not now."));
            return;
        }
        String no = whyNot(rider, anchor);
        if (no != null) {
            Signal.toast(rider, line(no));
            Signal.toast(anchor, line(no));
            return;
        }
        form(rider, anchor);
    }

    private static void answerOff(ServerPlayer p, UUID asker, boolean yes) {
        Long deadline = LETTING_GO.get(asker);
        if (deadline == null) return;
        MinecraftServer s = p.level().getServer();
        ServerPlayer who = player(s, asker);
        if (!yes) {
            LETTING_GO.remove(asker);
            if (who != null) Signal.toast(who, line(name(p) + " wants to keep going for now."));
            return;
        }
        Bond b = BONDS.get(asker);
        if (b == null) {
            LETTING_GO.remove(asker);
            return;
        }
        if (who != null && fighting(who, p)) {
            LETTING_GO.put(asker, (long) s.getTickCount()); // goes through the moment the fight is over
            return;
        }
        release(s, b);
    }

    // ---- making and ending ---------------------------------------------------------------------------------------------

    static void form(ServerPlayer rider, ServerPlayer anchor) {
        Bond b = new Bond(rider.getUUID(), anchor.getUUID());
        BONDS.put(b.rider(), b);
        BONDS.put(b.anchor(), b);
        Gates.setRiding(rider, true);
        Progression.setCounter(rider, RODE, 1);
        Progression.setCounter(anchor, RODE, 1);
        Signal.toast(rider, line("You're riding along with " + name(anchor) + ". Stay within " + (int) RidingHooks.RANGE + " blocks and you can't go down."));
        Signal.toast(anchor, line(name(rider) + " is riding along with you."));
        play(rider, RidingContent.BOND);
        play(anchor, RidingContent.BOND);
        DirectorLog.log(rider, "riding", "riding along with " + name(anchor));
        RidingNet.sync(rider.level().getServer());
    }

    private static void forget(Bond b) {
        BONDS.remove(b.rider());
        BONDS.remove(b.anchor());
        LETTING_GO.remove(b.rider());
        LETTING_GO.remove(b.anchor());
        PAST_REACH.remove(b.rider());
        Fold.standUp(b.rider());
    }

    /** Both have let go. */
    private static void release(MinecraftServer s, Bond b) {
        forget(b);
        ServerPlayer rider = player(s, b.rider()), anchor = player(s, b.anchor());
        if (rider != null) {
            Gates.setRiding(rider, false);
            Signal.withdraw(rider, "riding:off:" + b.anchor());
            Signal.toast(rider, line("The thread has let go. You're on your own feet now."));
            play(rider, RidingContent.LET_GO);
        }
        if (anchor != null) {
            Signal.withdraw(anchor, "riding:off:" + b.rider());
            Signal.toast(anchor, line("The thread has let go."));
            play(anchor, RidingContent.LET_GO);
        }
        RidingNet.sync(s);
    }

    /**
     * One of them is out: gone from the run, gone down or logged off. If it was the anchor and the rider is in a gate, the
     * rider is brought out with their Satchel rather than left alone in there without the thread. Next tick, because this
     * can be called from the middle of ending a run.
     */
    private static void ended(ServerPlayer p, Bond b, String what) {
        MinecraftServer s = p.level().getServer();
        boolean anchorOut = b.anchor().equals(p.getUUID());
        ServerPlayer other = player(s, b.other(p.getUUID()));
        forget(b);
        RidingNet.sync(s);
        if (other == null) return;
        Signal.withdraw(other, "riding:off:" + p.getUUID());
        if (!anchorOut) {
            Signal.toast(other, line(name(p) + " " + what + ", so the thread has let go."));
            return;
        }
        int gate = gate(other);
        if (gate == NO_GATE) {
            Signal.toast(other, line(name(p) + " " + what + ", so the thread has let go."));
            return;
        }
        String why = name(p) + " " + what + ", so the thread brought you out. You kept your Satchel.";
        UUID riderId = other.getUUID();
        PULL_OUTS.add(() -> {
            ServerPlayer rider = player(s, riderId);
            if (rider != null && gate(rider) == gate) Gates.pullOut(rider, why);
        });
    }

    static void leftRun(ServerPlayer p) {
        Bond b = BONDS.get(p.getUUID());
        if (b != null) ended(p, b, p.isDeadOrDying() ? "went down" : "left the gate");
    }

    static void loggedOut(ServerPlayer p) {
        Bond b = BONDS.get(p.getUUID());
        if (b != null) ended(p, b, "left the game");
    }

    /** A real death that nothing caught, gate or no gate. */
    static void wentDown(ServerPlayer p) {
        Bond b = BONDS.get(p.getUUID());
        if (b != null) ended(p, b, "went down");
    }

    // ---- every second -------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer s) {
        if (!PULL_OUTS.isEmpty()) {
            List<Runnable> due = List.copyOf(PULL_OUTS);
            PULL_OUTS.clear();
            for (Runnable r : due) r.run();
        }
        Fold.tick(s);
        if (s.getTickCount() % 20 == 0) everySecond(s);
    }

    static void everySecond(MinecraftServer s) {
        long now = s.getTickCount();
        ASKED.values().removeIf(a -> a.until() <= now);
        if (BONDS.isEmpty()) return;
        for (UUID asker : List.copyOf(LETTING_GO.keySet())) {
            if (LETTING_GO.get(asker) > now) continue;
            Bond b = BONDS.get(asker);
            if (b == null) {
                LETTING_GO.remove(asker);
                continue;
            }
            ServerPlayer one = player(s, b.rider()), two = player(s, b.anchor());
            if (one != null && two != null && fighting(one, two)) continue; // never mid-fight: it waits
            release(s, b);
        }
        for (Bond b : all()) {
            ServerPlayer rider = player(s, b.rider()), anchor = player(s, b.anchor());
            if (rider == null || anchor == null) continue;
            boolean past = !inReach(rider, anchor);
            if (past != PAST_REACH.getOrDefault(b.rider(), false)) {
                PAST_REACH.put(b.rider(), past);
                if (past) Signal.toast(rider, line("You're too far from " + name(anchor) + ". Past " + (int) RidingHooks.RANGE + " blocks the thread can't catch you."));
            }
            RidingEvents.sweep(rider);
        }
    }

    static void clear() {
        BONDS.clear();
        ASKED.clear();
        LETTING_GO.clear();
        PAST_REACH.clear();
        PULL_OUTS.clear();
        Fold.clear();
    }

    // ---- tests ----------------------------------------------------------------------------------------------------------

    /** Makes every let-go request's 30 seconds run out (tests only). */
    static void runOutLettingGoForTest() {
        LETTING_GO.replaceAll((id, until) -> 0L);
    }
}
