package dev.awaken.hearth.request;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A player's Anchor requests, saved on the player (kept on death) and synced to its owner for the Status screen.
 * Mutate on the server, then {@link Requests#sync}. {@code schema} versions the saved shape: older saves load with
 * defaults for whatever they lack.
 */
public final class RequestState {
    public static final int SCHEMA = 1;
    public static final int RECENT = 6;
    /** Below any build height: "has not been anywhere yet". */
    public static final int NO_DEPTH = 1024;

    /** A request offered and not yet answered. {@code nonce} ties the Signal decision to it. */
    public record Offer(int nonce, ActiveRequest request, long day) {
        public static final Codec<Offer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("nonce").forGetter(Offer::nonce),
                ActiveRequest.CODEC.fieldOf("request").forGetter(Offer::request),
                Codec.LONG.fieldOf("day").forGetter(Offer::day)
        ).apply(i, Offer::new));

        public String action() {
            return Requests.ACTION + ":take:" + nonce;
        }
    }

    public int schema = SCHEMA;
    public final List<ActiveRequest> active = new ArrayList<>();
    public final List<Offer> offers = new ArrayList<>();
    /** New requests owed: one per free slot each play-day, and one after each delivery. */
    public int credits;
    /** Play-day last looked at (-1 = never: the first request waits a little after the Anchor takes root). */
    public long day = -1;
    /** Game time before which no new request is offered (keeps offers out of bursts). */
    public long nextOfferAt;
    public int streak;
    public long lastDeliveryDay = -1;
    public int completed;
    public int reputation;
    /** Percent of the next temper's or Forge upgrade's price the Forge gives back (0..{@link RequestRules#FORGE_CREDIT_CAP}). */
    public int forgeCredit;
    /** Last seen temper counter and Forge tier, so a temper or upgrade made with credit is noticed (-1 = not yet seen). */
    public long seenTempered = -1;
    public int seenForgeTier = -1;
    /** Def ids of the last few requests, newest last: the Anchor doesn't ask for the same thing twice running. */
    public final List<String> recent = new ArrayList<>();
    /** Lowest Y stood on in the overworld ("ores at their depth"). */
    public int lowestY = NO_DEPTH;
    /** Play-day the town last left a gift at the Anchor (reputation: Beloved). */
    public long giftDay = -1;
    public int nonce;

    public RequestState() {}

    public Optional<Offer> offer(int nonce) {
        for (Offer o : offers) if (o.nonce() == nonce) return Optional.of(o);
        return Optional.empty();
    }

    public void remember(String id) {
        recent.remove(id);
        recent.add(id);
        while (recent.size() > RECENT) recent.remove(0);
    }

    public RequestState copy() {
        return fromSaved(toSaved());
    }

    // ---- saving -----------------------------------------------------------------------------------------------------

    private record Forge(int credit, long seenTempered, int seenForgeTier) {
        static final Codec<Forge> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("credit", 0).forGetter(Forge::credit),
                Codec.LONG.optionalFieldOf("seen_tempered", -1L).forGetter(Forge::seenTempered),
                Codec.INT.optionalFieldOf("seen_forge_tier", -1).forGetter(Forge::seenForgeTier)
        ).apply(i, Forge::new));
    }

    private record Pace(int credits, long day, long nextOfferAt, int streak, long lastDeliveryDay, long giftDay, int nonce) {
        static final Codec<Pace> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("credits", 0).forGetter(Pace::credits),
                Codec.LONG.optionalFieldOf("day", -1L).forGetter(Pace::day),
                Codec.LONG.optionalFieldOf("next_offer_at", 0L).forGetter(Pace::nextOfferAt),
                Codec.INT.optionalFieldOf("streak", 0).forGetter(Pace::streak),
                Codec.LONG.optionalFieldOf("last_delivery_day", -1L).forGetter(Pace::lastDeliveryDay),
                Codec.LONG.optionalFieldOf("gift_day", -1L).forGetter(Pace::giftDay),
                Codec.INT.optionalFieldOf("nonce", 0).forGetter(Pace::nonce)
        ).apply(i, Pace::new));
    }

    private record Saved(int schema, List<ActiveRequest> active, List<Offer> offers, Pace pace, int completed, int reputation,
                         Forge forge, List<String> recent, int lowestY) {}

    public static final MapCodec<RequestState> MAP_CODEC = RecordCodecBuilder.<Saved>mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(Saved::schema),
            ActiveRequest.CODEC.listOf().optionalFieldOf("active", List.of()).forGetter(Saved::active),
            Offer.CODEC.listOf().optionalFieldOf("offers", List.of()).forGetter(Saved::offers),
            Pace.CODEC.optionalFieldOf("pace", new Pace(0, -1, 0, 0, -1, -1, 0)).forGetter(Saved::pace),
            Codec.INT.optionalFieldOf("completed", 0).forGetter(Saved::completed),
            Codec.INT.optionalFieldOf("reputation", 0).forGetter(Saved::reputation),
            Forge.CODEC.optionalFieldOf("forge", new Forge(0, -1, -1)).forGetter(Saved::forge),
            Codec.STRING.listOf().optionalFieldOf("recent", List.of()).forGetter(Saved::recent),
            Codec.INT.optionalFieldOf("lowest_y", NO_DEPTH).forGetter(Saved::lowestY)
    ).apply(i, Saved::new)).xmap(RequestState::fromSaved, RequestState::toSaved);

    public static final Codec<RequestState> CODEC = MAP_CODEC.codec();
    public static final StreamCodec<ByteBuf, RequestState> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    private static RequestState fromSaved(Saved s) {
        RequestState r = new RequestState();
        r.schema = SCHEMA; // older shapes upgrade on load: every field they lack takes its default
        r.active.addAll(s.active);
        r.offers.addAll(s.offers);
        r.credits = s.pace.credits;
        r.day = s.pace.day;
        r.nextOfferAt = s.pace.nextOfferAt;
        r.streak = s.pace.streak;
        r.lastDeliveryDay = s.pace.lastDeliveryDay;
        r.giftDay = s.pace.giftDay;
        r.nonce = s.pace.nonce;
        r.completed = s.completed;
        r.reputation = s.reputation;
        r.forgeCredit = s.forge.credit;
        r.seenTempered = s.forge.seenTempered;
        r.seenForgeTier = s.forge.seenForgeTier;
        r.recent.addAll(s.recent);
        r.lowestY = s.lowestY;
        return r;
    }

    private Saved toSaved() {
        return new Saved(SCHEMA, List.copyOf(active), List.copyOf(offers),
                new Pace(credits, day, nextOfferAt, streak, lastDeliveryDay, giftDay, nonce), completed, reputation,
                new Forge(forgeCredit, seenTempered, seenForgeTier), List.copyOf(recent), lowestY);
    }
}
