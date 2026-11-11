package dev.awaken.frontier;

import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.BellHooks;
import dev.awaken.core.api.CityHooks;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;
import java.util.UUID;

/**
 * The homecoming (SPEC §67). The morning after your Hundred Peal, Carillon sends the skiff for you: this one time it comes
 * to wherever you are, and takes you home to the ridge. You hear every bell in the city from there, and Ilse comes down
 * from her fire. When you walk into Carillon the bells ring out over you and the people who sent you out say thank you,
 * in their own words. Nothing waits on it: walk in on your own any time after the peal and it's the same welcome.
 */
final class Homecoming {
    private Homecoming() {}

    static final String ACTION = "frontier_home";
    /** Before noon. */
    static final long MORNING_END = 6000;
    static final String LINE = "Every bell in the city is ringing for you";

    static final List<String> RIDGE = List.of(
            "[You can hear Carillon from the ridge. Every bell in the city is ringing.]",
            "Ilse: I heard them start up from the fire and I couldn't sit still. Eleven years that city's been quiet.",
            "Ilse: Go on down. They're all waiting for you. I'm walking down behind you. I'm not missing this.");

    static final List<String> THANKS = List.of(
            "[Every bell in Carillon is ringing. People are out in the streets and up on the roofs to see you come in.]",
            "Bram: A hundred bells. I've written it in the Register three times to make sure it's real. The Registry's never had a hunter like you.",
            "Kestrel: Every Runner in the Roost is up on the rail. I told them you'd come home for this. I was right.",
            "Oren: I heard them from the Forge and put my hammer down. First time in thirty years. Don't tell anyone.",
            "Hale: You went out past the ridge and came back. Do you know how rare that is? Thank you. I mean it.",
            "Hettie: I cried at the fountain, in front of the whole market. I don't care. Thank you, love.",
            "Ilse: I walked down. I haven't been inside the walls in eleven years. Thank you for bringing the bells home.");

    static void install() {
        Signal.onAction(ACTION, Homecoming::answer);
    }

    /** Every few seconds, for a player in a Far Landing world. */
    static void check(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        FrontierData d = FrontierData.get(server);
        UUID u = p.getUUID();
        if (d.marked(u, "thanked") || !(BellHooks.pealed(p) || d.marked(u, "home_forced"))) return;
        long today = PlayDays.current(server);
        String seen = d.markStarting(u, "peal@");
        if (seen == null) {
            d.mark(u, "peal@" + today);
            return;
        }
        ServerLevel over = server.overworld();
        if (p.level() != over) return;
        if (CityHooks.inCity(over, p.blockPosition())) {
            thanks(p, d);
            return;
        }
        if (d.marked(u, "fetching")) {
            if (Flights.flying(u)) return;
            d.unmark(u, "fetching");
            FrontierData.Mooring ridge = d.ridge();
            if (ridge != null && p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(ridge.origin())) < 64 * 64) ridge(p, d);
            return;
        }
        if (d.marked(u, "called") || d.marked(u, "rang")) return;
        long pealDay = parse(seen.substring(5), today);
        long tod = Math.floorMod(over.getOverworldClockTime(), 24000L);
        // the morning after (or any time, once a morning has gone by unseen)
        boolean due = today > pealDay + 1 || today > pealDay && tod < MORNING_END;
        if (!due || !over.canSeeSky(p.blockPosition().above(2))) return;
        call(p, d);
    }

    private static long parse(String s, long fallback) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The skiff is here: the card. */
    private static void call(ServerPlayer p, FrontierData d) {
        d.mark(p.getUUID(), "called");
        p.level().playSound(null, p.getX(), p.getY() + 4, p.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.2F, 0.5F);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The skiff has come for you"))
                .body(Component.literal("Carillon's sent the skiff out for you, every lantern lit. It's waiting right here to take you home."
                        + " Every bell in the city is waiting to ring for you."))
                .choice(ACTION, "Go home", "Not yet")
                .ttl(20 * 120).priority(8).build());
        Awaken.LOG.info("[frontier] the skiff came for {} on the morning after the Hundred Peal", p.getName().getString());
    }

    static void answer(ServerPlayer p, String action, boolean accepted) {
        FrontierData d = FrontierData.get(p.level().getServer());
        if (!accepted) {
            Signal.toast(p, Component.literal("It's gone back to the landing to wait. Take it from there whenever you're ready."));
            return;
        }
        if (!fetch(p, d)) {
            // under a roof, or somewhere it can't reach: it comes again once you're out under the sky
            d.unmark(p.getUUID(), "called");
            Signal.toast(p, Component.literal("The skiff can't reach you here. Go out under the open sky and it'll come down again."));
        }
    }

    private static boolean fetch(ServerPlayer p, FrontierData d) {
        if (!Flights.fetch(p, LINE)) return false;
        d.mark(p.getUUID(), "fetching");
        return true;
    }

    /** Set down on the ridge: the city's bells from far off, and Ilse. */
    private static void ridge(ServerPlayer p, FrontierData d) {
        if (!d.mark(p.getUUID(), "rang")) return;
        bells(p, 0.7F);
        say(p, RIDGE);
    }

    /** In the city: every bell over you, and their thanks. Once. */
    private static void thanks(ServerPlayer p, FrontierData d) {
        if (!d.mark(p.getUUID(), "thanked")) return;
        d.mark(p.getUUID(), "rang");
        bells(p, 3.0F);
        say(p, THANKS);
        Progression.count(p, "awaken:homecoming");
        Awaken.LOG.info("[frontier] {} came home to Carillon after the Hundred Peal", p.getName().getString());
    }

    /** Every city bell at once, from high over the streets: four voices and their long hum. */
    private static void bells(ServerPlayer p, float volume) {
        float[] pitch = {0.5F, 0.63F, 0.75F, 0.94F};
        for (int i = 0; i < pitch.length; i++) {
            double a = i * Math.PI / 2;
            p.level().playSound(null, p.getX() + Math.cos(a) * 12, p.getY() + 14, p.getZ() + Math.sin(a) * 12, SoundEvents.BELL_BLOCK,
                    SoundSource.BLOCKS, volume, pitch[i]);
        }
        p.level().playSound(null, p.getX(), p.getY() + 14, p.getZ(), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, volume, 0.6F);
    }

    private static void say(ServerPlayer p, List<String> lines) {
        for (String l : lines) Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(l)).dialogue().ttl(20 * 6).build());
    }

    /** Dev and tours: the homecoming now, as if the Hundred Peal was yesterday; the skiff takes you at once. */
    static boolean begin(ServerPlayer p) {
        FrontierData d = FrontierData.get(p.level().getServer());
        UUID u = p.getUUID();
        for (String m : List.of("thanked", "rang", "called", "fetching")) d.unmark(u, m);
        d.mark(u, "home_forced");
        String seen = d.markStarting(u, "peal@");
        if (seen != null) d.unmark(u, seen);
        d.mark(u, "peal@" + (PlayDays.current(p.level().getServer()) - 1));
        d.mark(u, "called");
        return fetch(p, d);
    }

    /** Whether the city still waits to welcome this player home (the Tasks row). */
    static boolean waiting(ServerPlayer p) {
        FrontierData d = FrontierData.get(p.level().getServer());
        return !d.marked(p.getUUID(), "thanked") && (BellHooks.pealed(p) || d.marked(p.getUUID(), "home_forced"));
    }
}
