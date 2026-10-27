package dev.awaken.trails;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Admin tools for trails: {@code /awaken admin trails list | open <id> | done <id> | reset | follow <id>}. Ids are the file names
 * ("your_hearth"); {@code open} opens a trail whatever its tokens say, {@code done} ticks every step, {@code reset} forgets all of it.
 */
final class TrailsCommands {
    private TrailsCommands() {}

    static void register() {
        AwakenCommands.add((root, ctx) -> {
            var arg = Commands.argument("id", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(
                    Trails.all().keySet().stream().map(k -> k.substring(k.indexOf(':') + 1)), b));
            root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(Commands.literal("trails")
                    .then(Commands.literal("list").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        TrailState st = TrailState.of(p);
                        for (var e : Trails.all().entrySet()) {
                            String s = st.finished.contains(e.getKey()) ? "finished" : st.opened.contains(e.getKey()) ? (e.getKey().equals(st.followed) ? "following" : "open") : "closed";
                            c.getSource().sendSuccess(() -> Component.literal(e.getKey() + "  " + s), false);
                        }
                        return Trails.all().size();
                    }))
                    .then(Commands.literal("open").then(arg.executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        dev.awaken.core.api.TrailsApi.unlock(p, "awaken:" + StringArgumentType.getString(c, "id"));
                        return 1;
                    })))
                    .then(Commands.literal("follow").then(arg.executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        TrailEngine.act(p, new dev.awaken.trails.net.TrailPayloads.Act(dev.awaken.trails.net.TrailPayloads.FOLLOW, "awaken:" + StringArgumentType.getString(c, "id"), ""));
                        return 1;
                    })))
                    .then(Commands.literal("done").then(arg.executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        String id = "awaken:" + StringArgumentType.getString(c, "id");
                        TrailDef def = Trails.get(id);
                        if (def == null) return 0;
                        TrailState st = TrailState.of(p);
                        st.opened.add(id);
                        for (TrailDef.Step s : def.steps()) st.latched.add(TrailState.key(id, s.id()));
                        st.save(p);
                        TrailEngine.update(p);
                        return 1;
                    })))
                    .then(Commands.literal("reset").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        p.setData(TrailsContent.STATE, TrailState.fresh());
                        TrailEngine.forget(p.getUUID());
                        TrailEngine.update(p);
                        return 1;
                    }))));
        });
    }
}
