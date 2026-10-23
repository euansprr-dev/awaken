package dev.awaken.above.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A machine screen, built on the server and drawn by the client in the AWAKEN glass style. Every machine uses the
 * same simple shape: a title, a power line and rows. A row may show item parts (with how many the player has) and one
 * button; pressing it sends {@code (pos, action, index)} back to the server, which re-validates everything.
 */
public record Panel(BlockPos pos, Component title, Component subtitle, int accent, int essence, int reactors,
                    boolean needsPower, List<Row> rows, Component footer) {

    public record Row(boolean header, Component label, Component detail, int colour, List<ItemStack> parts,
                      List<Integer> have, String action, int index, Component button, boolean enabled) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.of((buf, r) -> {
            buf.writeBoolean(r.header);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, r.label);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, r.detail);
            buf.writeInt(r.colour);
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.encode(buf, r.parts);
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).encode(buf, r.have);
            buf.writeUtf(r.action);
            buf.writeVarInt(r.index);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, r.button);
            buf.writeBoolean(r.enabled);
        }, buf -> new Row(buf.readBoolean(),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                buf.readInt(),
                ItemStack.OPTIONAL_LIST_STREAM_CODEC.decode(buf),
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).decode(buf),
                buf.readUtf(),
                buf.readVarInt(),
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                buf.readBoolean()));

        public boolean hasButton() {
            return !action.isEmpty();
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, Panel> STREAM_CODEC = StreamCodec.of((buf, p) -> {
        buf.writeBlockPos(p.pos);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.title);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.subtitle);
        buf.writeInt(p.accent);
        buf.writeVarInt(p.essence);
        buf.writeVarInt(p.reactors);
        buf.writeBoolean(p.needsPower);
        buf.writeVarInt(p.rows.size());
        for (Row r : p.rows) Row.STREAM_CODEC.encode(buf, r);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, p.footer);
    }, buf -> {
        BlockPos pos = buf.readBlockPos();
        Component title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        Component subtitle = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        int accent = buf.readInt();
        int essence = buf.readVarInt();
        int reactors = buf.readVarInt();
        boolean needs = buf.readBoolean();
        int n = Math.min(256, buf.readVarInt());
        List<Row> rows = new ArrayList<>(n);
        for (int i = 0; i < n; i++) rows.add(Row.STREAM_CODEC.decode(buf));
        Component footer = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
        return new Panel(pos, title, subtitle, accent, essence, reactors, needs, rows, footer);
    });

    public static Builder builder(BlockPos pos, String title, int accent) {
        return new Builder(pos, Component.literal(title), accent);
    }

    /** Fluent builder used by every machine. */
    public static final class Builder {
        private final BlockPos pos;
        private final Component title;
        private final int accent;
        private Component subtitle = Component.empty();
        private int essence = -1;
        private int reactors;
        private boolean needsPower;
        private Component footer = Component.empty();
        private final List<Row> rows = new ArrayList<>();

        private Builder(BlockPos pos, Component title, int accent) {
            this.pos = pos;
            this.title = title;
            this.accent = accent;
        }

        public Builder subtitle(String s) { this.subtitle = Component.literal(s); return this; }
        public Builder footer(String s) { this.footer = Component.literal(s); return this; }

        /** Shows the power line. {@code needs}: this machine refuses to work without a reactor. */
        public Builder power(int essence, int reactors, boolean needs) {
            this.essence = essence;
            this.reactors = reactors;
            this.needsPower = needs;
            return this;
        }

        public Builder header(String label) {
            rows.add(new Row(true, Component.literal(label), Component.empty(), 0, List.of(), List.of(), "", 0, Component.empty(), false));
            return this;
        }

        public Builder text(String label, String detail, int colour) {
            rows.add(new Row(false, Component.literal(label), Component.literal(detail), colour, List.of(), List.of(), "", 0, Component.empty(), false));
            return this;
        }

        public Builder button(String label, String detail, int colour, String action, int index, String button, boolean enabled) {
            rows.add(new Row(false, Component.literal(label), Component.literal(detail), colour, List.of(), List.of(), action, index,
                    Component.literal(button), enabled));
            return this;
        }

        /** A row with visible parts (Fabricator, Frame Forge). */
        public Builder parts(String label, String detail, int colour, List<ItemStack> parts, List<Integer> have, String action, int index,
                             String button, boolean enabled) {
            rows.add(new Row(false, Component.literal(label), Component.literal(detail), colour, List.copyOf(parts), List.copyOf(have),
                    action, index, Component.literal(button), enabled));
            return this;
        }

        public Panel build() {
            return new Panel(pos, title, subtitle, accent, essence, reactors, needsPower, List.copyOf(rows), footer);
        }
    }
}
