package dev.awaken.enchant.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.awaken.Awaken;
import dev.awaken.core.api.FirstPerson;
import dev.awaken.enchant.EnchantContent;
import dev.awaken.enchant.EnchantRules;
import dev.awaken.enchant.Known;
import dev.awaken.enchant.PageItem;
import dev.awaken.enchant.Pages;
import dev.awaken.enchant.net.EnchantPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Enchanting's client (SPEC §45.10): the table drawn (its floating book, the piece on the slate, the channel's light),
 * the writing scene (the view leaning in, your hands and the burin, the marks of light, the book's two pages), the
 * enchantments seen at work, the room line on tooltips and the Journal's ENCHANTMENTS category.
 */
public final class EnchantClient {
    private EnchantClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerBlockEntityRenderer(EnchantContent.TABLE_BE.get(), InscribingTableRenderer::new));
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent.class, LetterParticle::register);
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(EnchantPayloads.Open.TYPE, (msg, ctx) -> Minecraft.getInstance().execute(() -> InscribeScene.open(msg)));
            e.register(EnchantPayloads.State.TYPE, (msg, ctx) -> Minecraft.getInstance().execute(() -> InscribeScene.state(msg)));
            e.register(EnchantPayloads.Done.TYPE, (msg, ctx) -> Minecraft.getInstance().execute(() -> InscribeScene.done(msg)));
            e.register(EnchantPayloads.Closed.TYPE, (msg, ctx) -> Minecraft.getInstance().execute(() -> InscribeScene.closed(msg)));
            e.register(EnchantPayloads.Fx.TYPE, (msg, ctx) -> Minecraft.getInstance().execute(() -> EnchantFxClient.add(msg)));
        });
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOW, ViewportEvent.ComputeCameraAngles.class, InscribeScene::angles);
        bus.addListener(ComputeFovModifierEvent.class, InscribeScene::fov);
        bus.addListener(RenderGuiLayerEvent.Pre.class, EnchantClient::hideHud);
        bus.addListener(ExtractBlockOutlineRenderStateEvent.class, e -> {
            if (InscribeScene.weight(0) > 0) e.setCanceled(true);
        });
        bus.addListener(RenderPlayerEvent.Pre.class, InscribeScene::player);
        bus.addListener(SubmitCustomGeometryEvent.class, e -> {
            InscribeScene.submit(e);
            EnchantFxClient.submit(e);
        });
        bus.addListener(ClientTickEvent.Post.class, e -> {
            InscribeScene.tick();
            EnchantFxClient.tick();
        });
        bus.addListener(ItemTooltipEvent.class, EnchantClient::onTooltip);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            InscribeScene.reset();
            EnchantFxClient.clear();
        });
        bus.addListener(RegisterClientCommandsEvent.class, EnchantClient::commands);
        FirstPerson.world("enchant_table", 210, InscribeScene::pose);
        dev.awaken.core.api.EnchantApi.glow(EnchantGlow::bits);
        EnchantJournal.register();
    }

    /**
     * The autopilot's hand at a table (tours can't drag a mouse): {@code /awaken enchant pilot hover <no> | choose <no> |
     * memory <no> | trace | again | close}, numbers as in the Journal.
     */
    private static void commands(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal(Awaken.MODID).then(Commands.literal("enchant").then(Commands.literal("pilot")
                .then(Commands.literal("hover").then(Commands.argument("no", IntegerArgumentType.integer(0, 30)).executes(c -> {
                    InscribeScreen.pilotHover = IntegerArgumentType.getInteger(c, "no");
                    return 1;
                })))
                .then(Commands.literal("choose").then(Commands.argument("no", IntegerArgumentType.integer(1, 30)).executes(c -> pilotChoose(IntegerArgumentType.getInteger(c, "no"), false))))
                .then(Commands.literal("memory").then(Commands.argument("no", IntegerArgumentType.integer(1, 30)).executes(c -> pilotChoose(IntegerArgumentType.getInteger(c, "no"), true))))
                .then(Commands.literal("trace").executes(c -> {
                    InscribeScene.pilot = true;
                    return 1;
                }))
                .then(Commands.literal("again").executes(c -> {
                    InscribeScene.again();
                    return 1;
                }))
                .then(Commands.literal("close").executes(c -> {
                    if (Minecraft.getInstance().gui.screen() instanceof InscribeScreen s) s.onClose();
                    InscribeScene.pilot = false;
                    return 1;
                })))));
    }

    private static int pilotChoose(int no, boolean memory) {
        InscribeScreen.pilotHover = 0;
        boolean ok = Minecraft.getInstance().gui.screen() instanceof InscribeScreen s && s.pilotChoose(no, memory);
        Awaken.LOG.info("[enchant] pilot choose {}: {}", no, ok ? "chosen" : "not on the page");
        return ok ? 1 : 0;
    }

    /** The HUD steps aside while you write (subtitles and chat stay). */
    private static void hideHud(RenderGuiLayerEvent.Pre e) {
        if (InscribeScene.weight(0) <= 0.05F) return;
        String path = e.getName().getPath();
        if (path.equals("subtitle_overlay") || path.equals("chat")) return;
        e.setCanceled(true);
    }

    /**
     * Every piece that can take enchantments says its room plainly, under its name; a page you already know says so.
     * (Vanilla's own lines name what's on it.)
     */
    private static void onTooltip(ItemTooltipEvent e) {
        ItemStack s = e.getItemStack();
        var lines = e.getToolTip();
        int at = Math.min(1, lines.size());
        var player = Minecraft.getInstance().player;
        if (s.getItem() instanceof PageItem) {
            if (player == null) return;
            Known k = Known.of(player);
            boolean known = !Pages.teaches(s).isEmpty();
            for (var en : Pages.teaches(s)) known &= k.knows(en.id());
            if (known) lines.add(at, Component.literal("You know this one").withColor(0x8FD8A8));
            return;
        }
        if (!(s.isEnchantable() || s.isEnchanted()) || EnchantRules.room(s) <= 0) return;
        String room = EnchantRules.roomLine(s);
        int colour = room.startsWith("Room") ? 0x8AA9E6 : 0x9A8F84;
        lines.add(at, Component.literal(room).withColor(colour));
    }
}
