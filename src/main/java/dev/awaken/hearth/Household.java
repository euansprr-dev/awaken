package dev.awaken.hearth;

import dev.awaken.core.DirectorLog;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A shared Hearth (SPEC "Together"): the owner invites friends as members and a member can leave. Members use the
 * Anchor, wake and heal there, build there with powers, see with spirit sight there, and share the town, the requests
 * and the breaks near home. From tier III a member may found a sister Hearth: their own Anchor elsewhere, shared by the
 * whole household (one sister at III, two at IV, three at V). Server thread only.
 */
public final class Household {
    private Household() {}

    /** The Hearth tier from which a member's own Anchor founds a sister home. */
    public static final int SISTER_TIER = 3;
    /** How long an invitation stays open. */
    static final int INVITE_TTL = 20 * 120;
    /** A stranger asking at an Anchor reaches the owner at most this often. */
    static final int ASK_COOLDOWN = 20 * 30;

    /** Open invitations: invitee → (owner → game time it lapses). */
    private static final Map<UUID, Map<UUID, Long>> INVITES = new HashMap<>();
    /** The last time each stranger asked at someone's Anchor. */
    private static final Map<UUID, Long> ASKED = new HashMap<>();

    static void init() {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> {
            INVITES.clear();
            ASKED.clear();
        });
    }

    /** Sister homes this Hearth may still found: none below tier III, then one per tier. */
    public static int sisterSlots(HearthRecord h) {
        return Math.max(0, h.tier - (SISTER_TIER - 1));
    }

    // ---- inviting ---------------------------------------------------------------------------------------------------

    /** The owner invites {@code who}: a choice card for them (Y joins). Returns a reason it can't happen, or empty. */
    public static Optional<String> invite(ServerPlayer owner, ServerPlayer who) {
        MinecraftServer server = owner.level().getServer();
        Optional<HearthRecord> ho = HearthService.hearthOf(owner);
        if (ho.isEmpty()) return Optional.of("You have no Hearth of your own to share.");
        HearthRecord h = ho.get();
        if (who == owner) return Optional.of("It is already yours.");
        if (h.belongs(who.getUUID())) return Optional.of(who.getGameProfile().name() + " already shares your Hearth.");
        long until = server.overworld().getGameTime() + INVITE_TTL;
        INVITES.computeIfAbsent(who.getUUID(), k -> new HashMap<>()).put(owner.getUUID(), until);
        String ownerName = owner.getGameProfile().name();
        String action = Offers.offer(who, "invite:" + owner.getUUID(), INVITE_TTL, p -> accept(p, h.owner));
        Signal.send(who, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(ownerName + " invites you home"))
                .body(Component.literal("Share " + ownerName + "'s Hearth: wake and heal there, build there, use its Anchor, its town and its requests."))
                .row("Hearth", "Tier " + Upgrades.roman(h.tier) + " · " + h.radius() + " blocks")
                .choice(action, "Share the Hearth", "Not now").priority(5).ttl(INVITE_TTL).build());
        Signal.toast(owner, Component.literal("Invited " + who.getGameProfile().name() + " to share your Hearth"));
        DirectorLog.log(owner, "hearth", "invited " + who.getGameProfile().name());
        return Optional.empty();
    }

    /** Whether {@code owner} has an open invitation out to {@code who}. */
    public static boolean invited(MinecraftServer server, UUID who, UUID owner) {
        Map<UUID, Long> mine = INVITES.get(who);
        Long until = mine == null ? null : mine.get(owner);
        return until != null && server.overworld().getGameTime() <= until;
    }

    /** {@code who} takes up an invitation from {@code owner}. Returns whether they joined. */
    public static boolean accept(ServerPlayer who, UUID owner) {
        MinecraftServer server = who.level().getServer();
        if (!invited(server, who.getUUID(), owner)) {
            Signal.toast(who, Component.literal("That invitation has lapsed. Ask again."));
            return false;
        }
        INVITES.get(who.getUUID()).remove(owner);
        Optional<HearthRecord> ho = HearthData.get(server).of(owner);
        if (ho.isEmpty()) {
            Signal.toast(who, Component.literal("That Hearth's Anchor has gone cold."));
            return false;
        }
        return join(server, ho.get(), who);
    }

    /** Adds a member (no invitation needed: commands, tests, sister homes). Returns whether they were added. */
    public static boolean join(MinecraftServer server, HearthRecord h, ServerPlayer who) {
        if (h.belongs(who.getUUID())) return false;
        h.members.put(who.getUUID(), who.getGameProfile().name());
        HearthService.dirty(server);
        Signal.send(who, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(h.ownerName + "'s Hearth is yours too"))
                .body(Component.literal("Use its Anchor to see the house and to wake there. Leave any time: /awaken hearth leave."))
                .ttl(20 * 8).priority(4).build());
        tellHousehold(server, h, who.getUUID(), who.getGameProfile().name() + " shares the Hearth now");
        if (Progression.profile(who).awakened()) Progression.markNewThing(who, "shared hearth");
        DirectorLog.log(who, "hearth", "joined " + h.ownerName + "'s Hearth");
        refresh(server, h);
        HearthViews.refresh(who);
        // a member arriving can bring the town forward (it follows the household's highest rank)
        dev.awaken.hearth.town.Town.arrivals(HearthService.levelOf(server, h), h, false);
        return true;
    }

    // ---- leaving ----------------------------------------------------------------------------------------------------

    /** A member leaves {@code h}. Returns whether they were a member. */
    public static boolean leave(MinecraftServer server, HearthRecord h, UUID who, boolean removed) {
        String name = h.members.remove(who);
        if (name == null) return false;
        h.memberCelebrations.remove(who);
        HearthService.dirty(server);
        ServerPlayer sp = server.getPlayerList().getPlayer(who);
        if (sp != null) {
            name = sp.getGameProfile().name();
            // waking at the Hearth they left would be odd: wake by the bed again unless another Hearth is home
            if (HearthService.homeOf(sp).isEmpty()) HearthService.player(sp).respawnAtHearth = false;
            Signal.toast(sp, Component.literal(removed ? h.ownerName + " has taken back their Hearth." : "You no longer share " + h.ownerName + "'s Hearth."));
            dev.awaken.hearth.trophy.Trophies.applyHallBonus(sp, HearthService.homeOf(sp).orElse(null));
            HearthViews.refresh(sp);
            dev.awaken.hearth.guide.Guide.refresh(sp);
        }
        tellHousehold(server, h, who, (name == null || name.isEmpty() ? "Someone" : name) + " no longer shares the Hearth");
        refresh(server, h);
        return true;
    }

    // ---- sister homes -----------------------------------------------------------------------------------------------

    /**
     * The shared Hearth a member's own new Anchor founds a sister home from: the first they are a member of with a
     * sister slot free, or null.
     */
    static HearthRecord sisterBase(HearthData d, UUID member) {
        for (HearthRecord r : d.memberOf(member)) if (d.sistersOf(r.owner) < sisterSlots(r)) return r;
        return null;
    }

    /** Why a member can't raise an Anchor of their own here, or empty if they can (alone, or as a sister home). */
    static Optional<String> refusesOwnAnchor(HearthData d, UUID player) {
        List<HearthRecord> shared = d.memberOf(player);
        if (shared.isEmpty() || sisterBase(d, player) != null) return Optional.empty();
        HearthRecord first = shared.get(0);
        if (first.tier < SISTER_TIER) {
            return Optional.of("You share " + first.ownerName + "'s Hearth. At Tier " + Upgrades.roman(SISTER_TIER)
                    + " it can raise a second home. To found your own instead: /awaken hearth leave");
        }
        return Optional.of(first.ownerName + "'s Hearth has all the homes its tier allows. A higher tier allows another.");
    }

    /** A member's Anchor took root as a sister home: the whole household shares it. */
    static void founded(MinecraftServer server, HearthRecord sister, HearthRecord base) {
        sister.foundedFrom = base.owner;
        if (!sister.owner.equals(base.owner)) sister.members.put(base.owner, base.ownerName);
        base.members.forEach((id, name) -> { if (!id.equals(sister.owner)) sister.members.put(id, name); });
        HearthService.dirty(server);
        tellHousehold(server, sister, sister.owner, sister.ownerName + " founded a second home of " + base.ownerName + "'s Hearth");
    }

    /** The owner placed their Anchor again: the household it had comes back. */
    static void restoreKept(MinecraftServer server, HearthData d, HearthRecord h) {
        for (HearthRecord.Member m : d.takeKept(h.owner)) if (!m.id().equals(h.owner)) h.members.putIfAbsent(m.id(), m.name());
        if (!h.members.isEmpty()) tellHousehold(server, h, h.owner, h.ownerName + "'s Hearth took root again. It is still yours too.");
    }

    // ---- asking at the Anchor ---------------------------------------------------------------------------------------

    /** Someone who doesn't share this Hearth used its Anchor: the owner (if online) may let them in with Y. */
    static void askAtAnchor(ServerPlayer stranger, HearthRecord h) {
        MinecraftServer server = stranger.level().getServer();
        ServerPlayer owner = server.getPlayerList().getPlayer(h.owner);
        if (owner == null) {
            stranger.sendOverlayMessage(Component.literal("This is " + h.ownerName + "'s Hearth. It's warm here."));
            return;
        }
        long now = server.overworld().getGameTime();
        Long last = ASKED.get(stranger.getUUID());
        if (last != null && now - last < ASK_COOLDOWN) {
            stranger.sendOverlayMessage(Component.literal("You asked " + h.ownerName + " to share it. Give them a moment."));
            return;
        }
        ASKED.put(stranger.getUUID(), now);
        String name = stranger.getGameProfile().name();
        UUID who = stranger.getUUID();
        String action = Offers.offer(owner, "letin:" + who, 20 * 60, o -> {
            ServerPlayer s = o.level().getServer().getPlayerList().getPlayer(who);
            HearthService.hearthOf(o).ifPresent(r -> { if (s != null && r == h) join(o.level().getServer(), r, s); });
        });
        Signal.send(owner, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(name + " is at your Anchor"))
                .body(Component.literal(name + " asks to share your Hearth."))
                .choice(action, "Share it with " + name, "Not now").priority(5).ttl(20 * 60).build());
        stranger.sendOverlayMessage(Component.literal("This is " + h.ownerName + "'s Hearth. You asked to share it."));
    }

    // ---- telling and refreshing -------------------------------------------------------------------------------------

    /** The online players of a household (owner and members), optionally leaving one out. */
    public static List<ServerPlayer> online(MinecraftServer server, HearthRecord h) {
        List<ServerPlayer> out = new ArrayList<>(1 + h.members.size());
        ServerPlayer o = server.getPlayerList().getPlayer(h.owner);
        if (o != null) out.add(o);
        for (UUID m : h.members.keySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(m);
            if (p != null) out.add(p);
        }
        return out;
    }

    static void tellHousehold(MinecraftServer server, HearthRecord h, UUID except, String text) {
        for (ServerPlayer p : online(server, h)) if (!p.getUUID().equals(except)) Signal.toast(p, Component.literal(text));
    }

    static void refresh(MinecraftServer server, HearthRecord h) {
        for (ServerPlayer p : online(server, h)) {
            HearthViews.refresh(p);
            dev.awaken.hearth.guide.Guide.refresh(p);
        }
    }

    /** Lines for "/awaken hearth members". */
    static List<String> describe(MinecraftServer server, HearthRecord h) {
        List<String> out = new ArrayList<>();
        out.add(h.ownerName + "'s Hearth · Tier " + Upgrades.roman(h.tier) + " at " + h.pos.toShortString()
                + (h.foundedFrom != null ? " · a home of " + HearthData.get(server).of(h.foundedFrom).map(r -> r.ownerName + "'s Hearth").orElse("a Hearth gone cold") : ""));
        out.add("  " + h.ownerName + " (owner" + (server.getPlayerList().getPlayer(h.owner) != null ? ", here" : "") + ")");
        h.members.forEach((id, name) -> out.add("  " + (name == null || name.isEmpty() ? id.toString() : name)
                + (server.getPlayerList().getPlayer(id) != null ? " (here)" : "")));
        int slots = sisterSlots(h), used = HearthData.get(server).sistersOf(h.owner);
        out.add(slots > 0 ? "  Second homes: " + used + " of " + slots + " (a member's own Anchor founds one)"
                : "  Second homes: from Tier " + Upgrades.roman(SISTER_TIER));
        return out;
    }

    /** Login: members' names stay current in every Hearth they share. */
    static void onLogin(ServerPlayer sp) {
        String name = sp.getGameProfile().name();
        for (HearthRecord r : HearthData.get(sp.level().getServer()).memberOf(sp.getUUID())) {
            if (!name.equals(r.members.get(sp.getUUID()))) {
                r.members.put(sp.getUUID(), name);
                HearthService.dirty(sp.level().getServer());
            }
        }
    }
}
