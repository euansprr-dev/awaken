package dev.awaken.signal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import dev.awaken.core.api.Notices;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One Signal message. Build with {@link #builder(Kind, Component)}. A message has at most one meaningful action pair,
 * answered with the Y and N keys; {@link Builder#choice} says in plain words what each key does.
 *
 * <p>How it appears is its {@link Presentation} (System v2): a small toast for information, a choice card for a real
 * decision, a story line for the Signal's voice, or a panel for rich cards. {@code AUTO} picks from the kind and
 * whether there is an action.
 */
public record SignalWindow(int id, Kind kind, Component title, Optional<Component> body, List<Row> rows,
                           Optional<Component> footer, Optional<String> action, int ttlTicks, int priority,
                           Presentation presentation, Optional<Component> yesLabel, Optional<Component> noLabel,
                           Notices.Tier tier, String topic, boolean moment, boolean fleeting) {

    /** How a message is shown (System v2). */
    public enum Presentation {
        AUTO, TOAST, CHOICE, STORY, PANEL;
        public static final Codec<Presentation> CODEC = Codec.STRING.xmap(Presentation::valueOf, Presentation::name);

        /** What AUTO resolves to for a message. */
        public static Presentation resolve(SignalWindow w) {
            if (w.presentation() != AUTO) return w.presentation();
            if (w.action().isPresent()) return CHOICE;
            return switch (w.kind()) {
                case REVELATION -> STORY;
                case GATE -> PANEL;
                default -> TOAST;
            };
        }
    }

    public enum Kind {
        NOTICE, GATE, DIRECTIVE, TRUTH, WARNING, REASSESSMENT, REVELATION, DAYS_END;
        public static final Codec<Kind> CODEC = Codec.STRING.xmap(Kind::valueOf, Kind::name);
    }

    public record Row(Component label, Component value) {
        public static final Codec<Row> CODEC = RecordCodecBuilder.create(i -> i.group(
                ComponentSerialization.CODEC.fieldOf("label").forGetter(Row::label),
                ComponentSerialization.CODEC.fieldOf("value").forGetter(Row::value)
        ).apply(i, Row::new));
    }

    /** This message at another tier (the Signal raises an untagged one inside a gate run). */
    public SignalWindow withTier(Notices.Tier t) {
        return new SignalWindow(id, kind, title, body, rows, footer, action, ttlTicks, priority, presentation, yesLabel, noLabel, t, topic, moment, fleeting);
    }

    /** Untagged: the default tier, no topic, not a moment (the director may treat it as a response to the player). */
    public boolean untagged() {
        return tier == Notices.Tier.IMPORTANT && topic.isEmpty() && !moment;
    }

    private static final Codec<Notices.Tier> TIER_CODEC = Codec.STRING.xmap(Notices.Tier::valueOf, Notices.Tier::name);

    /**
     * The tier a message gets unless its sender says (SPEC §26): a decision, a warning or a gate panel is CRITICAL;
     * everything else IMPORTANT. Discoveries and other ambient finds say AMBIENT themselves.
     */
    static Notices.Tier defaultTier(Kind kind, boolean decision, Presentation p) {
        if (decision || kind == Kind.WARNING || kind == Kind.GATE || p == Presentation.CHOICE) return Notices.Tier.CRITICAL;
        return Notices.Tier.IMPORTANT;
    }

    public static final Codec<SignalWindow> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(SignalWindow::id),
            Kind.CODEC.fieldOf("kind").forGetter(SignalWindow::kind),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(SignalWindow::title),
            ComponentSerialization.CODEC.optionalFieldOf("body").forGetter(SignalWindow::body),
            Row.CODEC.listOf().fieldOf("rows").forGetter(SignalWindow::rows),
            ComponentSerialization.CODEC.optionalFieldOf("footer").forGetter(SignalWindow::footer),
            Codec.STRING.optionalFieldOf("action").forGetter(SignalWindow::action),
            Codec.INT.fieldOf("ttl").forGetter(SignalWindow::ttlTicks),
            Codec.INT.fieldOf("priority").forGetter(SignalWindow::priority),
            Presentation.CODEC.optionalFieldOf("presentation", Presentation.AUTO).forGetter(SignalWindow::presentation),
            ComponentSerialization.CODEC.optionalFieldOf("yes").forGetter(SignalWindow::yesLabel),
            ComponentSerialization.CODEC.optionalFieldOf("no").forGetter(SignalWindow::noLabel),
            TIER_CODEC.optionalFieldOf("tier", Notices.Tier.IMPORTANT).forGetter(SignalWindow::tier),
            Codec.STRING.optionalFieldOf("topic", "").forGetter(SignalWindow::topic),
            Codec.BOOL.optionalFieldOf("moment", false).forGetter(SignalWindow::moment),
            Codec.BOOL.optionalFieldOf("fleeting", false).forGetter(SignalWindow::fleeting)
    ).apply(i, SignalWindow::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SignalWindow> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistriesTrusted(CODEC);

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    public static Builder builder(Kind kind, Component title) {
        return new Builder(kind, title);
    }

    public static final class Builder {
        private final Kind kind;
        private final Component title;
        private Component body;
        private final List<Row> rows = new ArrayList<>();
        private Component footer;
        private String action;
        private int ttl = 20 * 8;
        private int priority = 0;
        private Presentation presentation = Presentation.AUTO;
        private Component yes, no;
        private Notices.Tier tier;
        private String topic = "";
        private boolean moment, fleeting;

        private Builder(Kind kind, Component title) {
            this.kind = kind;
            this.title = title;
        }

        public Builder body(Component body) { this.body = body; return this; }
        public Builder row(Component label, Component value) { rows.add(new Row(label, value)); return this; }
        public Builder row(String label, String value) { return row(Component.literal(label), Component.literal(value)); }
        public Builder footer(Component footer) { this.footer = footer; return this; }
        /** An action id routed to the handler registered with {@link Signal#onAction}: a decision (choice card). Prefer {@link #choice}. */
        public Builder action(String actionId) { this.action = actionId; this.ttl = Math.max(ttl, 20 * 30); return this; }
        /**
         * A real decision: the action id plus what Y and N do, in plain words ("Leave with your loot", "Keep going").
         * Shown as a choice card; unanswered after 20 s it waits in the tracker and the Status screen until the ttl.
         */
        public Builder choice(String actionId, String yesDoes, String noDoes) {
            action(actionId);
            this.yes = Component.literal(yesDoes);
            this.no = Component.literal(noDoes);
            return this;
        }
        public Builder presentation(Presentation p) { this.presentation = p; return this; }
        /** Info: how long a card is shown. A decision: how long it stays answerable (keep it within the handler's own offer). */
        public Builder ttl(int ticks) { this.ttl = ticks; return this; }
        public Builder priority(int p) { this.priority = p; return this; }
        /** How much it may interrupt (SPEC §26); unset, {@link #defaultTier} decides. */
        public Builder tier(Notices.Tier t) { this.tier = t; return this; }
        /** Waiting messages with the same topic arrive as one ("3 new creatures"). */
        public Builder topic(String t) { this.topic = t; return this; }
        /** A first of its kind or a wonder: shown as a moment (the centre banner), and waits longer for its quiet. */
        public Builder moment() { this.moment = true; return this; }
        /** Shown now or never: if it can't find its moment it's dropped, not filed in the Journal (the System's voice in the
         *  opening, a lesson the checklist teaches instead). */
        public Builder fleeting() { this.fleeting = true; return this; }

        public SignalWindow build() {
            return new SignalWindow(NEXT_ID.getAndIncrement(), kind, title, Optional.ofNullable(body), List.copyOf(rows),
                    Optional.ofNullable(footer), Optional.ofNullable(action), ttl, priority, presentation,
                    Optional.ofNullable(yes), Optional.ofNullable(no),
                    tier != null ? tier : defaultTier(kind, action != null, presentation), topic, moment, fleeting);
        }
    }
}
