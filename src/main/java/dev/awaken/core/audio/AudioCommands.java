package dev.awaken.core.audio;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.DirectorLog;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.Locale;

/**
 * {@code /awaken audio ...}: {@code why} (anyone: each cue's cooldown for you, the ambience emitters near you, the
 * last audio decisions); game masters: {@code cue <id> [checked]} (play a cue now; {@code checked} obeys the
 * cooldowns), {@code ui <sound>}, {@code emitters} (resend the ambience emitters).
 */
final class AudioCommands {
    private AudioCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var audio = Commands.literal("audio");
            audio.then(Commands.literal("why").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                long now = p.level().getServer().getTickCount();
                CueClock clock = AudioCues.clock(p);
                StringBuilder sb = new StringBuilder("Music cues:");
                for (Cue cue : Cue.values()) sb.append("\n  ").append(cue.id).append(": ").append(clock.why(cue, now));
                var em = AudioWatch.compute(p);
                sb.append("\nAmbience near you: ").append(em.isEmpty() ? "nothing" : em.size() + " emitter(s)");
                for (var e : DirectorLog.recent(p.getUUID(), 40)) {
                    if (e.system().equals("audio")) sb.append("\n  · ").append(e.detail());
                }
                c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                return 1;
            }));
            audio.then(Commands.literal("cue").requires(AwakenCommands.admin())
                    .then(Commands.argument("cue", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Cue.values()).map(q -> q.id), b))
                            .executes(c -> cue(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "cue"), false, c.getSource()))
                            .then(Commands.literal("checked")
                                    .executes(c -> cue(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "cue"), true, c.getSource())))));
            audio.then(Commands.literal("ui").requires(AwakenCommands.admin())
                    .then(Commands.argument("sound", StringArgumentType.word())
                            .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(UiSound.values()).map(u -> u.name().toLowerCase(Locale.ROOT)), b))
                            .executes(c -> {
                                String s = StringArgumentType.getString(c, "sound").toUpperCase(Locale.ROOT);
                                UiSound u = Arrays.stream(UiSound.values()).filter(v -> v.name().equals(s)).findFirst().orElse(null);
                                if (u == null) {
                                    c.getSource().sendFailure(Component.literal("No UI sound " + s.toLowerCase(Locale.ROOT)));
                                    return 0;
                                }
                                AudioCues.ui(c.getSource().getPlayerOrException(), u);
                                return 1;
                            })));
            audio.then(Commands.literal("emitters").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                AudioWatch.forget(p.getUUID());
                AudioWatch.emitters(p);
                int n = AudioWatch.compute(p).size();
                c.getSource().sendSuccess(() -> Component.literal(n + " ambience emitter(s) sent."), false);
                return n;
            }));
            root.then(audio);
        });
    }

    private static int cue(ServerPlayer p, String id, boolean checked, net.minecraft.commands.CommandSourceStack src) {
        Cue cue = Cue.byId(id);
        if (cue == null) {
            src.sendFailure(Component.literal("No cue " + id + ". Cues: " + String.join(", ", Arrays.stream(Cue.values()).map(q -> q.id).toList())));
            return 0;
        }
        if (checked) {
            boolean sent = AudioCues.play(p, cue);
            long now = p.level().getServer().getTickCount();
            src.sendSuccess(() -> Component.literal(sent ? "Cue " + id + " sent." : "Cue " + id + " held back: " + AudioCues.clock(p).why(cue, now)), false);
            return sent ? 1 : 0;
        }
        AudioCues.force(p, cue);
        src.sendSuccess(() -> Component.literal("Cue " + id + " sent (the client plays it when the music slot is free)."), false);
        return 1;
    }
}
