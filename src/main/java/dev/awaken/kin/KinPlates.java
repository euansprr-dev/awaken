package dev.awaken.kin;

import dev.awaken.mobs.entity.WildMount;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * The kin plate (SPEC §43.17; Euan 2026-11-02: "kins like lampwing don't have a card? They just have the vanilla hearts"):
 * who wears it, when it shows, and what it says about you and the creature. Every kin wears it (at home, wild, or Gnawed),
 * and so does every creature that is someone's: a tamed pet, a mount that trusts its rider. The client draws it
 * ({@code kin.client.KinPlate}) and asks the server for the bond's words with {@link KinNet.Read}; health it reads itself.
 */
public final class KinPlates {
    private KinPlates() {}

    /** At most this many kin plates at once. */
    public static final int MAX = 2;
    /** A look's plate fades this long after you look away (as every plate); a hurt one shows this long, then fades. */
    public static final int LOOK_FADE = 12, HURT_TICKS = 80, HURT_FADE = 20;
    /** A hurt one shows its plate within this of you; the server answers a read within {@link #READ_RANGE}. */
    public static final double HURT_RANGE = 16, READ_RANGE = 64;
    /** A name given on the Hearth page, at most. */
    public static final int NAME_MAX = 24;
    /** The bond line's tone: yours (the kind's warmth), wild kin (the green of a maybe), Gnawed (ember), someone else's. */
    public static final int WARM = 0, WILD = 1, GNAWED = 2, OTHER = 3;
    /** Kin need this many helps from you before they trust you (SPEC §43.5). */
    public static final int HELPS_TO_TRUST = 3;

    /** Who wears the kin plate instead of a wild one (and gets no Jade card while plates are on). */
    public static boolean wears(@Nullable Entity e) {
        return e instanceof KinBeast || e instanceof TamableAnimal t && t.isTame() || e instanceof WildMount m && m.trusted();
    }

    /**
     * How visible its plate is now (0..1): full while you look at it, fading over {@link #LOOK_FADE} after; full for
     * {@link #HURT_TICKS} after it's hurt within {@link #HURT_RANGE} of you, fading over {@link #HURT_FADE}. Ticks are
     * game ticks, {@code Long.MIN_VALUE} for never.
     */
    public static float visibility(long now, long lastLook, long lastHurt, double distance, double lookRange) {
        float v = 0;
        if (lastLook != Long.MIN_VALUE && distance <= lookRange) {
            long since = now - lastLook;
            v = since <= 0 ? 1 : Math.max(0, 1 - since / (float) LOOK_FADE);
        }
        if (lastHurt != Long.MIN_VALUE && distance <= HURT_RANGE) {
            long since = now - lastHurt;
            v = Math.max(v, since <= HURT_TICKS ? 1 : Math.max(0, 1 - (since - HURT_TICKS) / (float) HURT_FADE));
        }
        return v;
    }

    /** The ride line shows this long after you climb on (ticks). */
    public static final int RIDE_SHOW = 60;

    /**
     * Whether the ride line shows (SPEC §43.17, the clear screen only): for {@link #RIDE_SHOW} after you climb on, and
     * while the mount is hurt (below full; low is a third or less, and breathes amber) or you're in a fight.
     */
    public static boolean rideShows(long sinceMount, float health, boolean fighting) {
        return sinceMount <= RIDE_SHOW || fighting || health < 0.999F;
    }

    // ---- the words (server) -------------------------------------------------------------------------------------------

    /**
     * The bond as {@code p} reads it. {@code who}: "lives with you", "lives with Ana", "Could live with you", "Gnawed",
     * "your mount", "Ana's", (the client puts the kind's name before a line that starts in lower case); {@code step}: how
     * close you and its kind are (0 Wary .. 3 Kin, -1 none); {@code note}: how far off its trust is, or what it's doing.
     */
    static KinNet.Bond read(ServerPlayer p, LivingEntity e) {
        String who = "", note = "";
        int tone = WARM, step = -1;
        if (e instanceof KinBeast b) {
            step = Kinship.of(p).step(b.kind()).ordinal();
            if (b.gnawed()) {
                who = "Gnawed";
                tone = GNAWED;
                note = "Beat it down and the Deep lets go of it.";
            } else if (b.keeper != null) {
                boolean mine = b.keeper.equals(p.getUUID());
                who = mine ? "lives with you" : "lives with " + nameOf(p, b.keeper);
                tone = mine ? WARM : OTHER;
                note = KinPage.doing(b);
            } else {
                who = "Could live with you";
                tone = WILD;
                note = trust(p, b);
            }
        } else if (e instanceof TamableAnimal t && t.isTame()) {
            boolean mine = t.isOwnedBy(p);
            var ref = t.getOwnerReference();
            who = mine ? "yours" : ref == null ? "someone's" : nameOf(p, ref.getUUID()) + "'s";
            tone = mine ? WARM : OTHER;
            note = t.isOrderedToSit() ? "sitting where it was told to" : "";
        } else if (e instanceof WildMount m && m.trusted()) {
            boolean mine = m.ownedBy(p);
            who = mine ? "your mount" : m.owner() == null ? "trusts its rider" : nameOf(p, m.owner()) + "'s mount";
            tone = mine ? WARM : OTHER;
            note = m.resting() ? "resting" : "";
        }
        return new KinNet.Bond(e.getId(), who, tone, step, note);
    }

    /** How far off a wild one's trust is from you, or that it won't come near you. */
    private static String trust(ServerPlayer p, KinBeast b) {
        if (Kinship.of(p).afraid(b.kind(), p.level().getGameTime())) return "It won't come near you. Not after what happened.";
        if (b.trusted.contains(p.getUUID())) return "It trusts you. It will follow you home to a nook it likes.";
        int left = HELPS_TO_TRUST - b.helps.getOrDefault(p.getUUID(), 0);
        return switch (left) {
            case 1 -> "One more help and it will trust you.";
            case 2 -> "Two more helps and it will trust you.";
            default -> "Help it three times and it will trust you.";
        };
    }

    private static String nameOf(ServerPlayer p, UUID id) {
        ServerPlayer online = p.level().getServer().getPlayerList().getPlayer(id);
        if (online != null) return online.getScoreboardName();
        return p.level().getServer().services().nameToIdCache().get(id).map(NameAndId::name).orElse("someone");
    }

    /** A Read from the client: a creature near the player that wears the plate gets its words back. */
    static void answer(ServerPlayer p, int entity) {
        Entity e = p.level().getEntity(entity);
        if (!(e instanceof LivingEntity le) || !wears(le) || le.distanceToSqr(p) > READ_RANGE * READ_RANGE) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, read(p, le));
    }

    // ---- naming (the Hearth page) ---------------------------------------------------------------------------------------

    /**
     * A name for one of your beasts, from the Hearth page: only its keeper names it; the name is cleaned and cut to
     * {@link #NAME_MAX}; an empty name takes its name away. False when it isn't yours or isn't loaded.
     */
    static boolean name(ServerPlayer p, String id, String raw) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        if (!(p.level().getEntity(uuid) instanceof KinBeast b) || b.keeper == null || !b.keeper.equals(p.getUUID())) return false;
        String n = clean(raw);
        b.setCustomName(n.isEmpty() ? null : Component.literal(n));
        return true;
    }

    /** Chat's characters only, spaces folded, at most {@link #NAME_MAX}. */
    static String clean(String raw) {
        String s = StringUtil.filterText(raw == null ? "" : raw).strip().replaceAll("\\s+", " ");
        if (s.codePointCount(0, s.length()) > NAME_MAX) s = s.substring(0, s.offsetByCodePoints(0, NAME_MAX)).strip();
        return s;
    }
}
