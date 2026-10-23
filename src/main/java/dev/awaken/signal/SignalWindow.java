package dev.awaken.signal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One Signal window. Build with {@link #builder(Kind, Component)}. A window has at most one meaningful action pair
 * (accept / dismiss), answered with the Y and N keys.
 */
public record SignalWindow(int id, Kind kind, Component title, Optional<Component> body, List<Row> rows,
                           Optional<Component> footer, Optional<String> action, int ttlTicks, int priority) {

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

    public static final Codec<SignalWindow> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("id").forGetter(SignalWindow::id),
            Kind.CODEC.fieldOf("kind").forGetter(SignalWindow::kind),
            ComponentSerialization.CODEC.fieldOf("title").forGetter(SignalWindow::title),
            ComponentSerialization.CODEC.optionalFieldOf("body").forGetter(SignalWindow::body),
            Row.CODEC.listOf().fieldOf("rows").forGetter(SignalWindow::rows),
            ComponentSerialization.CODEC.optionalFieldOf("footer").forGetter(SignalWindow::footer),
            Codec.STRING.optionalFieldOf("action").forGetter(SignalWindow::action),
            Codec.INT.fieldOf("ttl").forGetter(SignalWindow::ttlTicks),
            Codec.INT.fieldOf("priority").forGetter(SignalWindow::priority)
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

        private Builder(Kind kind, Component title) {
            this.kind = kind;
            this.title = title;
        }

        public Builder body(Component body) { this.body = body; return this; }
        public Builder row(Component label, Component value) { rows.add(new Row(label, value)); return this; }
        public Builder row(String label, String value) { return row(Component.literal(label), Component.literal(value)); }
        public Builder footer(Component footer) { this.footer = footer; return this; }
        /** An action id routed to the handler registered with {@link Signal#onAction}. Shows [Y] / [N] hints. */
        public Builder action(String actionId) { this.action = actionId; this.ttl = Math.max(ttl, 20 * 30); return this; }
        public Builder ttl(int ticks) { this.ttl = ticks; return this; }
        public Builder priority(int p) { this.priority = p; return this; }

        public SignalWindow build() {
            return new SignalWindow(NEXT_ID.getAndIncrement(), kind, title, Optional.ofNullable(body), List.copyOf(rows),
                    Optional.ofNullable(footer), Optional.ofNullable(action), ttl, priority);
        }
    }
}
