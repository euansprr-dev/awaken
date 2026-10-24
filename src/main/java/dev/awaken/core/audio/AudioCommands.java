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
            audio.then(Commands.literal("layers").executes(c -> layers(c.getSource()))
                    .then(Commands.literal("force").requires(AwakenCommands.admin())
                            .then(Commands.argument("level", StringArgumentType.word())
                                    .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                            java.util.stream.Stream.concat(Arrays.stream(Intensity.NAMES), java.util.stream.Stream.of("auto")), b))
                                    .executes(c -> force(c.getSource(), StringArgumentType.getString(c, "level")))))
                    .then(Commands.literal("play").requires(AwakenCommands.admin())
                            .then(Commands.argument("context", StringArgumentType.greedyString())
                                    .executes(c -> play(c.getSource(), StringArgumentType.getString(c, "context"))))));
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

    /** The layered music as the server sees it for you: context, level, why, and what it hears around you. */
    private static int layers(net.minecraft.commands.CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        long now = p.level().getServer().getTickCount();
        LayerWatch.evaluate(p, now);
        LayerWatch.PlayerState st = LayerWatch.state(p);
        Intensity.Signals s = st.signals;
        StringBuilder sb = new StringBuilder("Layered music:");
        for (LayerWatch.SlotState slot : st.slots) {
            sb.append("\n  ").append(slot.slot.name().toLowerCase(Locale.ROOT)).append(": ")
                    .append(slot.sent == null ? "nothing" : slot.sent + " at " + Intensity.name(slot.sentLevel) + " (" + slot.intensity.why() + ")")
                    .append(slot.slot == MusicLayers.Slot.MUSIC && st.override != null ? " [auditioning]" : "")
                    .append(slot.slot == MusicLayers.Slot.MUSIC && st.pinned >= 0 ? " [pinned]" : "");
        }
        sb.append(String.format(Locale.ROOT, "\n  around you: %d hunting (%d close), %d near, pressure %d, %s, last blow %s",
                s.hunting, s.huntingClose, s.near, s.pressure,
                s.lordAlive ? String.format(Locale.ROOT, "a Lord %.0f blocks off%s", s.lordDistance, s.lordHunting ? " hunting you" : "") : "no Lord",
                now - s.lastCombat < 20L * 3600 ? (now - s.lastCombat) / 20 + " s ago" : "none"));
        for (var e : DirectorLog.recent(p.getUUID(), 40)) {
            if (e.system().equals("audio") && e.detail().startsWith("layers")) sb.append("\n  · ").append(e.detail());
        }
        src.sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private static int force(net.minecraft.commands.CommandSourceStack src, String level) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        int l = level.equals("auto") ? -1 : Intensity.byName(level);
        if (l < 0 && !level.equals("auto")) {
            src.sendFailure(Component.literal("Levels: " + String.join(", ", Intensity.NAMES) + ", auto"));
            return 0;
        }
        LayerWatch.state(p).pinned = l;
        LayerWatch.evaluate(p, p.level().getServer().getTickCount());
        src.sendSuccess(() -> Component.literal(l < 0 ? "Layered music follows the game again." : "Layered music pinned at " + level + "."), false);
        return 1;
    }

    private static int play(net.minecraft.commands.CommandSourceStack src, String context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        net.minecraft.resources.Identifier id = context.equals("off") ? null : net.minecraft.resources.Identifier.tryParse(context);
        if (id == null && !context.equals("off")) {
            src.sendFailure(Component.literal("Not a context id: " + context + " (e.g. awaken:gate/frost_monastery, or off)"));
            return 0;
        }
        LayerWatch.state(p).override = id;
        LayerWatch.evaluate(p, p.level().getServer().getTickCount());
        src.sendSuccess(() -> Component.literal(id == null ? "Back to the game's own music." : "Auditioning " + id
                + " (the layers follow what happens; /awaken audio layers force <level> pins one)."), false);
        return 1;
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
