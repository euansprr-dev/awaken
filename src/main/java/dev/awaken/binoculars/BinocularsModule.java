package dev.awaken.binoculars;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Binoculars (SPEC §39), common half: the {@code awaken:binoculars} relay that lets others see yours up, the sounds,
 * spotting ({@link Spotter}: the view held on something unfound finds it, §39.10), and the display-only
 * {@code awaken:binoculars} item the hands and other players' models draw (in no loot, recipe or tab: you always have
 * them, on a key). Everything you see through them is client side ({@code binoculars.client}); the
 * side-agnostic rules it follows ({@link GlassRules}, {@link GlassKey}, {@link GlassState}, {@link GlassSpring},
 * {@link GlassFeel}) are checked by GameTests.
 */
public final class BinocularsModule {
    private BinocularsModule() {}

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    /** Drawn in the hands and at other players' eyes; never given to anyone. */
    public static final DeferredItem<Item> BINOCULARS = ITEMS.registerSimpleItem("binoculars", p -> p.stacksTo(1));

    /** {@code /awaken spotting aim}: for tours, faces the nearest thing the player could spot (§39.10). */
    private static int aim(CommandContext<CommandSourceStack> c, String what) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String said = Spotter.aim(p, what);
        c.getSource().sendSuccess(() -> Component.literal("Spotting: " + said), false);
        return 1;
    }

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        BinocularsSounds.register(modBus);
        modBus.addListener(BinocularsNet::register);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> {
            BinocularsRelay.flush(e.getServer(), BinocularsRelay.SEND);
            Spotter.tick(e.getServer());
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.StartTracking.class, e -> {
            if (e.getEntity() instanceof ServerPlayer tracker) BinocularsRelay.onStartTracking(tracker, e.getTarget());
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                BinocularsRelay.forget(p);
                Spotter.forget(p);
            }
        });
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) BinocularsRelay.reset(p, BinocularsRelay.SEND);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) BinocularsRelay.reset(p, BinocularsRelay.SEND);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) BinocularsRelay.reset(p, BinocularsRelay.SEND);
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> Spotter.clear());
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("spotting").requires(AwakenCommands.admin())
                .then(Commands.literal("aim").executes(c -> aim(c, "any"))
                        .then(Commands.argument("what", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(new String[]{"creature", "place"}, b))
                                .executes(c -> aim(c, StringArgumentType.getString(c, "what")))))));
        BinocularsTests.register();
    }
}
