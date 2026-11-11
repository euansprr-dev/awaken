package dev.awaken.frontier;

import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.FarPost;
import dev.awaken.core.api.TheLull;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The skiff's post (SPEC §67). A letter reaches you wherever you are, as a card in the writer's words, and a copy waits
 * in the hold at the landing's mooring. The hold also takes what Carillon's people asked you to bring them (their step
 * moves on as if you'd handed it over) and sends a keepsake home to its family. Hale's board stands beside it.
 */
final class FrontierPost {
    private FrontierPost() {}

    /** How many of the newest letters the hold shows. */
    static final int HOLD_SHOWS = 3;

    static void install() {
        FarPost.installLetters(new FarPost.Letters() {
            @Override
            public void letter(ServerPlayer p, String from, String text) {
                FrontierPost.letter(p, from, text);
            }

            @Override
            public void keep(ServerPlayer p, String from, String text) {
                FrontierPost.keep(p, from, text);
            }
        });
        Invitations.install();
        Homecoming.install();
    }

    /** The hold's copy of a letter. */
    static void keep(ServerPlayer p, String from, String text) {
        MinecraftServer server = p.level().getServer();
        FrontierData.get(server).letter(p.getUUID(), new FrontierData.Letter(from, text, PlayDays.current(server)));
    }

    /** A letter: the card now, a copy in the hold. */
    static void letter(ServerPlayer p, String from, String text) {
        keep(p, from, text);
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("A letter from " + from))
                .body(Component.literal(text)).dialogue().ttl(readTicks(text)).priority(7).build());
        Awaken.LOG.info("[frontier] a letter from {} to {}", from, p.getName().getString());
    }

    /** Long enough to read it twice over. */
    static int readTicks(String text) {
        int words = text.split("\\s+").length;
        return 20 * Math.max(10, 4 + words / 3);
    }

    /** The hold: a keepsake goes home; else whatever Carillon asked for; else your last letters. */
    static void hold(ServerPlayer p) {
        if (FarPost.keepsake(p, p.getMainHandItem())) return;
        if (FarPost.hold(p) > 0) return;
        List<FrontierData.Letter> all = FrontierData.get(p.level().getServer()).letters(p.getUUID());
        if (all.isEmpty()) {
            Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The skiff's hold"))
                    .body(Component.literal("Empty. Anything Carillon's people want brought to them goes in here, and the skiff takes it. Their letters come back the same way, and a copy of each is kept here."))
                    .ttl(20 * 10).build());
            return;
        }
        StringBuilder body = new StringBuilder();
        for (int i = all.size() - 1; i >= Math.max(0, all.size() - HOLD_SHOWS); i--) {
            FrontierData.Letter l = all.get(i);
            if (!body.isEmpty()) body.append("\n\n");
            body.append(l.from()).append(": ").append(l.text());
        }
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Letters in the hold"))
                .body(Component.literal(body.toString())).dialogue().ttl(readTicks(body.toString())).build());
    }

    /** The Association's board at the landing: Hale's notices, as at his post in the city. */
    static void board(ServerPlayer p) {
        if (!FarPost.board(p))
            Signal.toast(p, Component.literal("Nothing pinned to the board yet."));
    }

    /** Every five seconds: the city's due invitations and the homecoming (SPEC §67). */
    static void tick(MinecraftServer server) {
        if (!TheLull.farLanding(server)) return;
        FrontierData data = FrontierData.get(server);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (Flights.flying(p.getUUID())) continue;
            Invitations.check(p);
            Homecoming.check(p);
            if (data.marked(p.getUUID(), "landed") && p.level() == server.overworld()
                    && !dev.awaken.core.api.CityHooks.inCity(p.level(), p.blockPosition())) FarPost.out(p);
        }
    }
}
