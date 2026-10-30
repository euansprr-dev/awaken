package dev.awaken.signal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.Destinations;
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The player's chosen destination (SPEC §16.8, §27.11; Euan 2026-10-27: "if I choose a destination on the map"): one
 * place, saved on the player (schemaVersion 1, kept through death), sent to their client when it changes and on every
 * login, respawn and dimension change. It leads the Now line until they arrive (within {@link Destinations#ARRIVED}
 * blocks across and {@link Destinations#ARRIVED_UP} up or down), then clears itself with a word that they're there.
 */
public final class Destination {
    private Destination() {}

    public static final int SCHEMA = 1;
    /** How often arrival is checked (ticks). */
    static final int CHECK = 20;
    static final int NAME_MAX = 48;
    static final Set<String> KINDS = Set.of("gate", "mark", "rumour", "region", "pin", "spot", "home", "place");

    /** The saved form: no target means none set. */
    public record Data(int schemaVersion, Optional<Saved> target) {
        public static final Data NONE = new Data(SCHEMA, Optional.empty());
        public static final MapCodec<Data> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(Data::schemaVersion),
                Saved.CODEC.optionalFieldOf("target").forGetter(Data::target)
        ).apply(i, Data::new));
    }

    /** {@code goal}: what it was set for ("kin/lampwing"), "" for a place the player picked (SPEC §27.12). */
    public record Saved(String name, Identifier dimension, BlockPos at, String kind, String goal) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("name").forGetter(Saved::name),
                Identifier.CODEC.fieldOf("dimension").forGetter(Saved::dimension),
                BlockPos.CODEC.fieldOf("at").forGetter(Saved::at),
                Codec.STRING.optionalFieldOf("kind", "place").forGetter(Saved::kind),
                Codec.STRING.optionalFieldOf("goal", "").forGetter(Saved::goal)
        ).apply(i, Saved::new));

        Destinations.Target target() {
            return new Destinations.Target(name, dimension, at, kind);
        }

        static Saved of(Destinations.Target t, String goal) {
            return new Saved(t.name(), t.dimension(), t.at().immutable(), t.kind(), goal);
        }
    }

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    public static final Supplier<AttachmentType<Data>> DATA = ATTACHMENTS.register("destination", () -> AttachmentType
            .builder(() -> Data.NONE)
            .serialize(Data.MAP_CODEC, d -> d.target().isPresent())
            .copyOnDeath()
            .build());

    static void init() {
        Destinations.install(new Destinations.Server() {
            @Override public Optional<Destinations.Target> get(ServerPlayer p) {
                return p.getData(DATA).target().map(Saved::target);
            }

            @Override public void set(ServerPlayer p, Destinations.@Nullable Target t) {
                Destination.set(p, t, "");
            }

            @Override public void set(ServerPlayer p, Destinations.@Nullable Target t, String goal) {
                Destination.set(p, t, goal);
            }

            @Override public String goal(ServerPlayer p) {
                return p.getData(DATA).target().map(Saved::goal).orElse("");
            }
        });
    }

    static void set(ServerPlayer p, Destinations.@Nullable Target t, String goal) {
        Optional<Saved> next = Optional.ofNullable(t).map(x -> Saved.of(x, goal));
        if (p.getData(DATA).target().equals(next)) return;
        p.setData(DATA, new Data(SCHEMA, next));
        sync(p);
    }

    /** The client asked (from the Atlas): a place in the dimension they stand in, named in a few words; or none. */
    static void request(ServerPlayer p, SignalPayloads.SetDestination msg) {
        if (!msg.set()) {
            set(p, null, "");
            return;
        }
        String name = msg.name().strip();
        if (name.isEmpty() || !p.level().isInWorldBounds(msg.at())) return;
        if (name.length() > NAME_MAX) name = name.substring(0, NAME_MAX - 1) + "…";
        String kind = KINDS.contains(msg.kind()) ? msg.kind() : "place";
        set(p, new Destinations.Target(name, p.level().dimension().identifier(), msg.at(), kind), "");
    }

    static void sync(ServerPlayer p) {
        if (!AwakenNet.canReceive(p, SignalPayloads.Destination.TYPE)) return;
        AwakenNet.send(p, SignalPayloads.Destination.of(p.getData(DATA).target().map(Saved::target)));
    }

    /** Whether standing at {@code feet} counts as being there (a rumour's height is a guess: only across counts). */
    public static boolean arrived(BlockPos feet, BlockPos at, String kind) {
        int dx = feet.getX() - at.getX(), dz = feet.getZ() - at.getZ();
        return dx * dx + dz * dz <= Destinations.ARRIVED * Destinations.ARRIVED
                && (kind.equals("rumour") || Math.abs(feet.getY() - at.getY()) <= Destinations.ARRIVED_UP);
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK != 5) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) check(p);
    }

    /**
     * Arrived: the destination has done its job, and says so in passing (a toast that isn't filed). One set for a goal
     * also goes, quietly, the moment the goal is met by any route (SPEC §27.12): the Lampwing tracked but met at your fire.
     */
    static boolean check(ServerPlayer p) {
        Optional<Saved> d = p.getData(DATA).target();
        if (d.isEmpty()) return false;
        if (!d.get().goal().isEmpty() && Destinations.done(p, d.get().goal())) {
            set(p, null, "");
            return true;
        }
        if (p.isSpectator() || !p.level().dimension().identifier().equals(d.get().dimension())) return false;
        if (!arrived(p.blockPosition(), d.get().at(), d.get().kind())) return false;
        set(p, null, "");
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Destination reached · " + d.get().name()))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 4).fleeting().build());
        return true;
    }
}
