package dev.awaken.living.pulse;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.core.PlayDays;
import dev.awaken.living.LivingContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Rumours (SPEC §21.6): how you stumble on more. A Wayfarer with nothing else to say may tell of a happening on now that
 * you haven't seen, a direction and a few words; it puts a "?" on your map there (attachment {@code awaken:rumours},
 * schema 1, synced to you, gone after two play-days). Never a list.
 */
public final class Rumours {
    private Rumours() {}

    public static final int SCHEMA = 1, KEEP = 8;
    static final long KEEP_DAYS = 2;

    /** What a player has heard: where, what (its title), and the play-day they heard it. */
    public record Heard(int x, int z, String title, long day) {
        public static final Codec<Heard> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("x").forGetter(Heard::x),
                Codec.INT.fieldOf("z").forGetter(Heard::z),
                Codec.STRING.fieldOf("title").forGetter(Heard::title),
                Codec.LONG.fieldOf("day").forGetter(Heard::day)
        ).apply(i, Heard::new));
    }

    public record State(int schemaVersion, List<Heard> heard) {
        public static final State EMPTY = new State(SCHEMA, List.of());
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(State::schemaVersion),
                Heard.CODEC.listOf().optionalFieldOf("heard", List.of()).forGetter(State::heard)
        ).apply(i, State::new));
    }

    /**
     * A rumour from {@code teller} ("Ilse"), standing at {@code at}, about something on now 160-2500 blocks off that
     * the player hasn't witnessed; null when there's no news (or half the time, so people still talk about themselves).
     */
    public static @Nullable String tell(ServerPlayer p, String teller, Entity at) {
        if (p.getRandom().nextFloat() < 0.5F) return null;
        List<Happening> news = Pulse.news(p, 160, 2500);
        if (news.isEmpty()) return null;
        Happening h = news.get(p.getRandom().nextInt(news.size()));
        HappeningDef def = HappeningBook.get(h.kind);
        if (def == null || def.rumours().isEmpty()) return null;
        String line = def.rumours().get(p.getRandom().nextInt(def.rumours().size()))
                .replace("{dir}", direction(h.centre.x - at.getX(), h.centre.z - at.getZ()));
        heard(p, (int) h.centre.x, (int) h.centre.z, def.title());
        return teller + ": " + line;
    }

    /** north, north-east, ... from a vector (north is -z). */
    static String direction(double dx, double dz) {
        String[] names = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double a = Math.atan2(-dx, dz); // 0 = +z (south), turning west
        int i = (int) Math.round(a / (Math.PI / 4));
        return names[Math.floorMod(i, 8)];
    }

    static void heard(ServerPlayer p, int x, int z, String title) {
        long today = PlayDays.current(p.level().getServer());
        State s = p.getData(LivingContent.RUMOURS.get());
        List<Heard> next = new ArrayList<>();
        for (Heard h : s.heard()) if (today - h.day() < KEEP_DAYS && !(Math.abs(h.x() - x) < 64 && Math.abs(h.z() - z) < 64)) next.add(h);
        next.add(new Heard(x, z, title, today));
        while (next.size() > KEEP) next.removeFirst();
        p.setData(LivingContent.RUMOURS.get(), new State(SCHEMA, List.copyOf(next)));
    }
}
