package dev.awaken.veil.net;

import dev.awaken.Awaken;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Every message the Veil sends. Server is authoritative: the client only sends intent (keys held, things looked at). */
public final class VeilPayloads {
    private VeilPayloads() {}

    private static Vec3 readVec(FriendlyByteBuf b) {
        return new Vec3(b.readDouble(), b.readDouble(), b.readDouble());
    }

    private static void writeVec(FriendlyByteBuf b, Vec3 v) {
        b.writeDouble(v.x);
        b.writeDouble(v.y);
        b.writeDouble(v.z);
    }

    // ---- client -> server -------------------------------------------------------------------------------------------

    /** Focus held (with sneak) or released; justSit = the player pressed N to sit without going anywhere. */
    public record MeditateInput(boolean holding, boolean justSit) implements CustomPacketPayload {
        public static final Type<MeditateInput> TYPE = new Type<>(Awaken.id("veil_meditate"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MeditateInput> STREAM_CODEC = StreamCodec.ofMember(
                (m, b) -> { b.writeBoolean(m.holding); b.writeBoolean(m.justSit); },
                b -> new MeditateInput(b.readBoolean(), b.readBoolean()));
        @Override public Type<MeditateInput> type() { return TYPE; }
    }

    /** A spirit's intent: talk to / possess an entity, touch a block, return, or act while possessing. */
    public record Action(int kind, int entityId, BlockPos pos) implements CustomPacketPayload {
        public static final int TALK = 0, POSSESS = 1, RELEASE = 2, USE = 3, RETURN = 4, ATTACK = 5;
        public static final Type<Action> TYPE = new Type<>(Awaken.id("veil_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.ofMember(
                (m, b) -> { b.writeVarInt(m.kind); b.writeVarInt(m.entityId); b.writeBlockPos(m.pos); },
                b -> new Action(b.readVarInt(), b.readVarInt(), b.readBlockPos()));
        @Override public Type<Action> type() { return TYPE; }
    }

    /** Movement intent for a possessed creature, sent every tick while possessing. */
    public record PossessInput(float forward, float strafe, boolean jump, boolean sprint, float yaw, float pitch) implements CustomPacketPayload {
        public static final Type<PossessInput> TYPE = new Type<>(Awaken.id("veil_possess_input"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PossessInput> STREAM_CODEC = StreamCodec.ofMember(
                (m, b) -> { b.writeFloat(m.forward); b.writeFloat(m.strafe); b.writeBoolean(m.jump); b.writeBoolean(m.sprint);
                    b.writeFloat(m.yaw); b.writeFloat(m.pitch); },
                b -> new PossessInput(b.readFloat(), b.readFloat(), b.readBoolean(), b.readBoolean(), b.readFloat(), b.readFloat()));
        @Override public Type<PossessInput> type() { return TYPE; }
    }

    // ---- server -> client -------------------------------------------------------------------------------------------

    /**
     * The spirit's situation. possessLevel: 0 none, 1 animals (C-rank), 2 any non-boss mob (A-rank).
     * Sent on change and once a second while out.
     */
    public record State(boolean spirit, int ticksLeft, int maxTicks, int cord, int awareness, Vec3 anchor, boolean bodyHere, Vec3 body,
                        int bodyId, int possessedId, boolean inPocket, boolean dreaming, boolean bodyHurt, int possessLevel) implements CustomPacketPayload {
        public static final State NONE = new State(false, 0, 1, 0, 0, Vec3.ZERO, false, Vec3.ZERO, -1, -1, false, false, false, 0);
        public static final Type<State> TYPE = new Type<>(Awaken.id("veil_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.ofMember(State::write, State::read);

        private void write(RegistryFriendlyByteBuf b) {
            b.writeBoolean(spirit);
            b.writeVarInt(ticksLeft);
            b.writeVarInt(maxTicks);
            b.writeVarInt(cord);
            b.writeVarInt(awareness);
            writeVec(b, anchor);
            b.writeBoolean(bodyHere);
            writeVec(b, body);
            b.writeVarInt(bodyId);
            b.writeVarInt(possessedId);
            b.writeBoolean(inPocket);
            b.writeBoolean(dreaming);
            b.writeBoolean(bodyHurt);
            b.writeVarInt(possessLevel);
        }

        private static State read(RegistryFriendlyByteBuf b) {
            return new State(b.readBoolean(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), readVec(b), b.readBoolean(),
                    readVec(b), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readBoolean(), b.readVarInt());
        }

        @Override public Type<State> type() { return TYPE; }
    }

    /** Meditation progress for the HUD ring. ticks = 0 means not meditating. */
    public record Meditation(int ticks, boolean justSit, boolean inStillpoint, boolean canTravel) implements CustomPacketPayload {
        public static final Type<Meditation> TYPE = new Type<>(Awaken.id("veil_meditation"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Meditation> STREAM_CODEC = StreamCodec.ofMember(
                (m, b) -> { b.writeVarInt(m.ticks); b.writeBoolean(m.justSit); b.writeBoolean(m.inStillpoint); b.writeBoolean(m.canTravel); },
                b -> new Meditation(b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readBoolean()));
        @Override public Type<Meditation> type() { return TYPE; }
    }

    /** A gate seen from the Veil: where, its rank colour, and whether this spirit already scouted it. */
    public record GateMark(BlockPos pos, int colour, String label, boolean scouted) {}

    /**
     * What spirit sight shows right now. Only positions inside the spirit's Awareness are ever sent, and never anything
     * near another player's base (see {@code RevealFilter}). oreKinds[i] belongs to ores[i]. awake = the small secret
     * sense of an awakened player who is not out of their body.
     */
    public record Sight(boolean awake, long[] ores, byte[] oreKinds, long[] secrets, long[] rifts, List<GateMark> gates,
                        int[] mobIds, int[] mobColours) implements CustomPacketPayload {
        public static final Sight EMPTY = new Sight(false, new long[0], new byte[0], new long[0], new long[0], List.of(), new int[0], new int[0]);
        public static final Type<Sight> TYPE = new Type<>(Awaken.id("veil_sight"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Sight> STREAM_CODEC = StreamCodec.ofMember(Sight::write, Sight::read);

        private void write(RegistryFriendlyByteBuf b) {
            b.writeBoolean(awake);
            b.writeLongArray(ores);
            b.writeByteArray(oreKinds);
            b.writeLongArray(secrets);
            b.writeLongArray(rifts);
            b.writeVarInt(gates.size());
            for (GateMark g : gates) {
                b.writeBlockPos(g.pos());
                b.writeInt(g.colour());
                b.writeUtf(g.label(), 64);
                b.writeBoolean(g.scouted());
            }
            b.writeVarIntArray(mobIds);
            b.writeVarIntArray(mobColours);
        }

        private static Sight read(RegistryFriendlyByteBuf b) {
            boolean awake = b.readBoolean();
            long[] ores = b.readLongArray();
            byte[] kinds = b.readByteArray(4096);
            long[] secrets = b.readLongArray();
            long[] rifts = b.readLongArray();
            int n = b.readVarInt();
            List<GateMark> gates = new ArrayList<>(Math.min(n, 64));
            for (int i = 0; i < n; i++) gates.add(new GateMark(b.readBlockPos(), b.readInt(), b.readUtf(64), b.readBoolean()));
            return new Sight(awake, ores, kinds, secrets, rifts, gates, b.readVarIntArray(), b.readVarIntArray());
        }

        @Override public Type<Sight> type() { return TYPE; }
    }

    /** One Codex card from the Veil (spirits heard and what they gave). */
    public record CodexCard(String id, String category, String title, String body, boolean found, List<String> links) {}

    public record Codex(List<CodexCard> cards) implements CustomPacketPayload {
        public static final Type<Codex> TYPE = new Type<>(Awaken.id("veil_codex"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Codex> STREAM_CODEC = StreamCodec.ofMember(
                (m, b) -> {
                    b.writeVarInt(m.cards.size());
                    for (CodexCard c : m.cards) {
                        b.writeUtf(c.id());
                        b.writeUtf(c.category());
                        b.writeUtf(c.title());
                        b.writeUtf(c.body());
                        b.writeBoolean(c.found());
                        b.writeVarInt(c.links().size());
                        for (String l : c.links()) b.writeUtf(l);
                    }
                },
                b -> {
                    int n = b.readVarInt();
                    List<CodexCard> cards = new ArrayList<>(Math.min(n, 256));
                    for (int i = 0; i < n; i++) {
                        String id = b.readUtf(), cat = b.readUtf(), title = b.readUtf(), body = b.readUtf();
                        boolean found = b.readBoolean();
                        int ln = b.readVarInt();
                        List<String> links = new ArrayList<>(Math.min(ln, 16));
                        for (int k = 0; k < ln; k++) links.add(b.readUtf());
                        cards.add(new CodexCard(id, cat, title, body, found, List.copyOf(links)));
                    }
                    return new Codex(List.copyOf(cards));
                });
        @Override public Type<Codex> type() { return TYPE; }
    }

    /** The first near-death: for a moment you see yourself from outside. */
    public record Glimpse(int ticks) implements CustomPacketPayload {
        public static final Type<Glimpse> TYPE = new Type<>(Awaken.id("veil_glimpse"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Glimpse> STREAM_CODEC = StreamCodec.ofMember(
                (m, b) -> b.writeVarInt(m.ticks), b -> new Glimpse(b.readVarInt()));
        @Override public Type<Glimpse> type() { return TYPE; }
    }
}
