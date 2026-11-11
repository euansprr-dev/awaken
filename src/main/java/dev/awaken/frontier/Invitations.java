package dev.awaken.frontier;

import dev.awaken.Awaken;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.FarPost;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Carillon's bells, rung for you while you're out on the land (SPEC §67). Each of the four city bells is yours wherever you
 * are once it's due, and the city writes about it. Two of those letters ask you to come and hear it: the first bell of the
 * Roost and the Square (around C rank), and the first of the Market and the Quay (around B). Going is optional and nothing
 * waits on it. When you next stand in Carillon the bell rings out once more for you, and people come and talk.
 */
final class Invitations {
    private Invitations() {}

    /** A city bell's letter: who writes, what they say, and the line that asks you to come. */
    record Letter(String bell, int round, String name, String from, String text, String ask) {}

    static final List<Letter> LETTERS = List.of(
            new Letter("roost", 1, "Roost's bell", "Kestrel at the Roost",
                    "The Runners rang the Roost's bell for you today. We ring it for every Runner who makes C rank, and you did it out there on your own. It's yours now, and it'll answer your song from up here.",
                    "Come and hear it when you can. The skiff knows the way, and nobody minds how long you take."),
            new Letter("square", 1, "Square's bell", "The Choir at Choir Square",
                    "Twenty bells in your tower. The Choir rang the Square's bell for you this evening, the way we do for anyone who gets to twenty. Half the city came out to listen.",
                    "Come and hear it yourself one day. The skiff will bring you."),
            new Letter("market", 2, "market bell", "Hettie at the market",
                    "The traders rang the market bell for you this morning. B rank! Somebody read your name out and the whole row cheered.",
                    "Come and hear it when you've got a day. The skiff's still up on the ridge, and I've saved you a seat by the fountain."),
            new Letter("harbour", 2, "Quay's bell", "The dockers at the Quay",
                    "Fifty bells. We rang the Quay's bell for you at the turn of the tide. It hadn't sounded in eleven years.",
                    "Come and walk the Quay one day and hear it properly. The skiff will bring you."));

    /** What you hear on arriving, by round: people who come to find you. */
    static final List<List<String>> HEARD = List.of(
            List.of("Kestrel: You came! Stand still a minute. Listen.",
                    "Kestrel: That's yours. It'll ring like that for you every time you come home.",
                    "Hale: Back from the far country, are you? I've put new patterns on the rack since you left. Have a look before you go."),
            List.of("Hettie: There you are! The whole market's been talking about you. Somebody's made up a song. It isn't very good.",
                    "Oren: Let's see what you've been hitting things with out there.",
                    "Oren: Hm. You've kept the edge honest. I'll say that much, and I won't say it twice."));

    static void install() {
        FarPost.installCityBells(Invitations::rung);
    }

    static @Nullable Letter of(String bell) {
        for (Letter l : LETTERS) if (l.bell().equals(bell)) return l;
        return null;
    }

    /** A city bell was rung for a player out on the land: the letter, asking them to come if it's the first of its round. */
    static void rung(ServerPlayer p, String bell) {
        Letter l = of(bell);
        if (l == null) return;
        FrontierData d = FrontierData.get(p.level().getServer());
        UUID u = p.getUUID();
        boolean asks = !asked(d, u, l.round());
        if (asks) d.mark(u, "invite" + l.round() + ":" + bell);
        FarPost.letter(p, l.from(), asks ? l.text() + " " + l.ask() : l.text());
        Awaken.LOG.info("[frontier] {}'s {} rung from afar{}", p.getName().getString(), bell, asks ? ", with an invitation" : "");
    }

    private static boolean asked(FrontierData d, UUID u, int round) {
        for (Letter l : LETTERS) if (l.round() == round && d.marked(u, "invite" + round + ":" + l.bell())) return true;
        return false;
    }

    /** Dev and tours: the letter for {@code bell} as if it had just been rung. */
    static boolean send(ServerPlayer p, String bell) {
        if (of(bell) == null) return false;
        rung(p, bell);
        return true;
    }

    /** The bell an invitation still stands for, by its name ("Roost's bell"), or null. */
    static @Nullable String standing(ServerPlayer p) {
        Letter l = standingLetter(p);
        return l == null ? null : l.name();
    }

    private static @Nullable Letter standingLetter(ServerPlayer p) {
        FrontierData d = FrontierData.get(p.level().getServer());
        UUID u = p.getUUID();
        for (Letter l : LETTERS) if (d.marked(u, "invite" + l.round() + ":" + l.bell()) && !d.marked(u, "heard" + l.round())) return l;
        return null;
    }

    /** Every few seconds: a player with an invitation standing who's in Carillon hears their bell, and people come. */
    static void check(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        if (p.level() != server.overworld() || !CityHooks.inCity(p.level(), p.blockPosition())) return;
        Letter l = standingLetter(p);
        if (l == null) return;
        FrontierData d = FrontierData.get(server);
        d.mark(p.getUUID(), "heard" + l.round());
        // the bell itself, once more, and its long hum; heard by everyone near, as a real bell would be
        p.level().playSound(null, p.getX(), p.getY() + 6, p.getZ(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 3.0F, 0.72F);
        p.level().playSound(null, p.getX(), p.getY() + 6, p.getZ(), SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 2.0F, 0.8F);
        Signal.toast(p, Component.literal("[The " + l.name() + " rings out across Carillon. It's ringing for you.]"));
        for (String line : HEARD.get(l.round() - 1)) {
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(line)).dialogue().ttl(20 * 6).build());
        }
        Progression.count(p, "awaken:far_invitation_heard");
        Awaken.LOG.info("[frontier] {} came home to hear the {}", p.getName().getString(), l.name());
    }
}
