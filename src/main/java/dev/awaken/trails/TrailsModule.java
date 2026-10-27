package dev.awaken.trails;

import dev.awaken.core.api.NowHooks;
import dev.awaken.core.api.TrailsApi;
import dev.awaken.progression.Checklist;
import dev.awaken.trails.net.TrailPayloads;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jspecify.annotations.Nullable;

/**
 * Trails (docs/trails/PLAN.md, SPEC §31): optional guidance for every part of the game. Short lists of things to try open
 * at a moment, wait in the K menu's Journal page, and only reach the task card and the Now line when the player follows
 * one. Each area of the game writes its own trails as data and adds its own tokens through {@link TrailsApi}.
 */
public final class TrailsModule {
    private TrailsModule() {}

    public static void init(IEventBus modBus) {
        TrailsContent.register(modBus);
        modBus.addListener(TrailsModule::registerPayloads);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(Trails::addReload);
        bus.addListener(TrailsModule::onPlayerTick);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> TrailEngine.forget(e.getEntity().getUUID()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) TrailEngine.forget(sp.getUUID()); // the screen's copy is sent afresh
        });
        Checklist.registerLeading(TrailEngine::task);
        NowHooks.register(TrailEngine::now);
        TrailsApi.install(new TrailsApi.Hooks() {
            @Override
            public void unlock(ServerPlayer p, String id) {
                TrailDef def = Trails.get(id);
                TrailState st = TrailState.of(p);
                if (def == null || st.opened.contains(id) || st.finished.contains(id)) return;
                TrailEngine.open(p, st, id, def, true);
                st.save(p);
                TrailEngine.update(p);
            }

            @Override
            public @Nullable String suggest(ServerPlayer p) {
                return TrailEngine.suggest(p);
            }

            @Override
            public void unfollow(ServerPlayer p) {
                TrailState st = TrailState.of(p);
                if (st.followed.isEmpty()) return;
                st.followed = "";
                st.save(p);
            }

            @Override
            public void poke(ServerPlayer p) {
                TrailEngine.update(p);
            }
        });
        TrailsCommands.register();
        TrailsTests.register();
    }

    /** Every two seconds per player, staggered by entity id. */
    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer p && (p.tickCount + p.getId()) % 40 == 0) TrailEngine.update(p);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(TrailPayloads.Act.TYPE, TrailPayloads.Act.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) TrailEngine.act(sp, msg);
        });
        r.playToClient(TrailPayloads.View.TYPE, TrailPayloads.View.STREAM_CODEC);
    }
}
