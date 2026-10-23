package dev.awaken.signal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenNet;
import dev.awaken.signal.net.SignalPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Decisions waiting for a player (System v2). A message with an action stays pending, saved with the player, until it
 * is answered, withdrawn, or its time-to-live runs out (counted in world game time, so a single-player relog keeps the
 * time it had left). The client shows it as a card, then folded in the tracker and the Status screen.
 */
public final class PendingChoices {
    private PendingChoices() {}

    public static final int SCHEMA = 1;
    /** Never keep more than this many; the oldest go first. */
    public static final int MAX = 12;

    public record Entry(SignalWindow window, long expiresAt) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                SignalWindow.CODEC.fieldOf("window").forGetter(Entry::window),
                Codec.LONG.fieldOf("expires_at").forGetter(Entry::expiresAt)
        ).apply(i, Entry::new));

        public String action() {
            return window.action().orElse("");
        }
    }

    /** The saved list (immutable; replace it with setData). */
    public record Data(int schemaVersion, List<Entry> entries) {
        public static final Data EMPTY = new Data(SCHEMA, List.of());
        public static final MapCodec<Data> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(Data::schemaVersion),
                Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(Data::entries)
        ).apply(i, Data::new));
    }

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    public static final Supplier<AttachmentType<Data>> PENDING = ATTACHMENTS.register("pending_choices", () -> AttachmentType
            .builder(() -> Data.EMPTY)
            .serialize(Data.MAP_CODEC, d -> !d.entries().isEmpty())
            .copyOnDeath()
            .build());

    static long now(ServerPlayer p) {
        return p.level().getServer().overworld().getGameTime();
    }

    /** Live entries; expired ones are dropped (and saved) on the way. */
    public static List<Entry> live(ServerPlayer p) {
        Data d = p.getData(PENDING);
        if (d.entries().isEmpty()) return List.of();
        long now = now(p);
        List<Entry> keep = new ArrayList<>(d.entries().size());
        for (Entry e : d.entries()) if (e.expiresAt() > now) keep.add(e);
        if (keep.size() != d.entries().size()) p.setData(PENDING, new Data(SCHEMA, List.copyOf(keep)));
        return keep;
    }

    /** Adds a decision; a newer copy of the same action replaces the older one. */
    static void add(ServerPlayer p, SignalWindow w) {
        if (w.action().isEmpty()) return;
        String action = w.action().get();
        List<Entry> list = new ArrayList<>(live(p));
        list.removeIf(e -> e.action().equals(action));
        list.add(new Entry(w, now(p) + Math.max(20, w.ttlTicks())));
        while (list.size() > MAX) list.remove(0);
        p.setData(PENDING, new Data(SCHEMA, List.copyOf(list)));
    }

    /** Removes a decision. Returns whether it was pending. */
    static boolean remove(ServerPlayer p, String action) {
        List<Entry> list = new ArrayList<>(live(p));
        if (!list.removeIf(e -> e.action().equals(action))) return false;
        p.setData(PENDING, new Data(SCHEMA, List.copyOf(list)));
        return true;
    }

    /** Sends the whole pending list to the player's client. */
    static void sync(ServerPlayer p) {
        long now = now(p);
        List<SignalPayloads.PendingEntry> out = new ArrayList<>();
        for (Entry e : live(p)) out.add(new SignalPayloads.PendingEntry(e.window(), (int) Math.min(Integer.MAX_VALUE, e.expiresAt() - now)));
        AwakenNet.send(p, new SignalPayloads.Pending(out));
    }

    /** Slow sweep: drops expired entries and tells the client when something went. */
    static void sweep(ServerPlayer p) {
        Data d = p.getData(PENDING);
        if (d.entries().isEmpty()) return;
        int before = d.entries().size();
        if (live(p).size() != before) sync(p);
    }
}
