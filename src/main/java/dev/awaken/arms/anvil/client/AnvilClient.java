package dev.awaken.arms.anvil.client;

import com.mojang.brigadier.arguments.BoolArgumentType;
import dev.awaken.arms.anvil.AnvilContent;
import dev.awaken.arms.anvil.AnvilPayloads;
import dev.awaken.arms.anvil.AnvilSong;
import dev.awaken.arms.anvil.WarAnvilBlockEntity;
import dev.awaken.arms.anvil.WeaponForged;
import dev.awaken.core.api.FirstPerson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jspecify.annotations.Nullable;

/**
 * The War Anvil's client (SPEC §46.3): the anvil drawn, the scene opened when the server says your work is waiting, the
 * view turned to it, the HUD and the block outline stepping aside while it runs, your hands at it, steam off the trough,
 * and a forged weapon's story on its tooltip. Called from {@code ArmsClient.init}.
 */
public final class AnvilClient {
    private AnvilClient() {}

    /** A Lord's anvil's runes and Heart (tint 0) in its Lord's colour, read from the block entity (SPEC §46.6); gold for none. */
    private static final BlockTintSource LORD = new BlockTintSource() {
        @Override
        public int color(BlockState state) {
            return ARGB.opaque(WarAnvilBlockEntity.GOLD);
        }

        @Override
        public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            return level.getBlockEntity(pos) instanceof WarAnvilBlockEntity be && be.lord() >= 0 ? ARGB.opaque(be.lord()) : color(state);
        }
    };

    /** The song plays itself on the beat (tours and demos: {@code /awaken arms anvil autoplay true}); the server judges it as any. */
    static boolean autoplay;

    public static void init(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerBlockEntityRenderer(AnvilContent.ANVIL_BE.get(), WarAnvilRenderer::new));
        modBus.addListener(RegisterColorHandlersEvent.BlockTintSources.class, e -> e.register(java.util.List.of(LORD), AnvilContent.ANVIL.get()));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(AnvilPayloads.Open.TYPE, (msg, ctx) -> Minecraft.getInstance().execute(() -> onOpen(msg)));
            e.register(AnvilPayloads.Done.TYPE, (msg, ctx) -> Minecraft.getInstance().execute(() -> onDone(msg)));
            e.register(AnvilPayloads.Held.TYPE, (msg, ctx) -> Minecraft.getInstance().execute(() -> onHeld(msg)));
        });
        FirstPerson.world("arms_anvil", 200, AnvilHands::pose);
        FirstPerson.camera(AnvilCamera::active);
        // what you held steps aside: your hands are on the hammer and tongs
        FirstPerson.view((pt, view) -> {
            if (sceneAnvil() == null) return;
            view.hideMain = true;
            view.hideOff = true;
        });
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOWEST, ViewportEvent.ComputeCameraAngles.class, AnvilScene::angles);
        bus.addListener(EventPriority.LOWEST, ViewportEvent.ComputeFov.class, AnvilScene::fov);
        bus.addListener(ExtractBlockOutlineRenderStateEvent.class, e -> {
            if (sceneAnvil() != null) e.setCanceled(true);
        });
        bus.addListener(RenderGuiLayerEvent.Pre.class, AnvilClient::hideHud);
        // the weapon has left your hand: it is the one hanging over the fire
        bus.addListener(net.neoforged.neoforge.client.event.RenderHandEvent.class, e -> {
            if (net.minecraft.client.Minecraft.getInstance().gui.screen() instanceof dev.awaken.hall.client.AnvilStage) e.setCanceled(true);
        });
        bus.addListener(RenderPlayerEvent.Pre.class, AnvilCamera::player);
        bus.addListener(ClientTickEvent.Post.class, e -> {
            tick();
            AnvilBodies.tick();
        });
        bus.addListener(ItemTooltipEvent.class, AnvilClient::onTooltip);
        bus.addListener(RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(Commands.literal("awaken")
                .then(Commands.literal("arms").then(Commands.literal("anvil").then(Commands.literal("autoplay")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            autoplay = BoolArgumentType.getBool(c, "on");
                            return 1;
                        })))))));
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> {
            AnvilScene.end();
            AnvilBodies.clear();
        });
    }

    // ---- messages ------------------------------------------------------------------------------------------------------

    /** Your work is on the anvil (just begun, or waiting where you left it): the scene opens, counted in again. */
    private static void onOpen(AnvilPayloads.Open o) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        send(o.pos(), AnvilPayloads.RESUME, 0, 0F, 0F);
        AnvilScene.reset();
        AnvilScene.pos = o.pos();
        AnvilScene.begin();
        mc.gui.setScreen(new AnvilSongScreen(o.pos()));
    }

    private static void onDone(AnvilPayloads.Done d) {
        if (Minecraft.getInstance().gui.screen() instanceof AnvilSongScreen s && s.pos.equals(d.pos())) s.done(d);
    }

    // ---- while the scene runs ----------------------------------------------------------------------------------------

    /** The anvil whose scene is open (its song screen showing), or null. */
    static @Nullable WarAnvilBlockEntity sceneAnvil() {
        BlockPos p = AnvilScene.pos;
        if (p == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AnvilSongScreen s) || !s.pos.equals(p)) return null;
        return anvil(p);
    }

    private static final AnvilScene.Placed STEAM = new AnvilScene.Placed();

    /** The scene ends with its screen (anything else that opens, Esc, a death): the server hears you stepped away. */
    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (AnvilScene.pos != null && !(mc.gui.screen() instanceof AnvilSongScreen s && s.pos.equals(AnvilScene.pos))) {
            if (mc.getConnection() != null) send(AnvilScene.pos, AnvilPayloads.LEAVE, 0, 0F, 0F);
            AnvilScene.end();
            return;
        }
        WarAnvilBlockEntity be = sceneAnvil();
        if (be == null || mc.level == null) return;
        AnvilCamera.begin();
        AnvilScene.place(be, now(0F), true, STEAM);
        if (!STEAM.steam) return;
        // steam off the water while the hot blade is in it
        BlockPos pos = be.getBlockPos();
        var r = mc.level.getRandom();
        for (int i = 0; i < 2; i++) {
            mc.level.addParticle(ParticleTypes.WHITE_SMOKE, pos.getX() + STEAM.steamAt.x + (r.nextFloat() - 0.5F) * 0.12F,
                    pos.getY() + STEAM.steamAt.y, pos.getZ() + STEAM.steamAt.z + (r.nextFloat() - 0.5F) * 0.5F, 0, 0.04 + r.nextFloat() * 0.03, 0);
        }
    }

    /**
     * A breath of the bellows an Emberkit works (§46.6): the fire flares out of the Forge's mouth and sparks lift. The
     * mouth is the Forge's front face.
     */
    static void puff(BlockPos forge) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockState fs = mc.level.getBlockState(forge);
        var face = fs.hasProperty(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING)
                ? fs.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING) : net.minecraft.core.Direction.NORTH;
        var r = mc.level.getRandom();
        double x = forge.getX() + 0.5 + face.getStepX() * 0.52, y = forge.getY() + 0.35, z = forge.getZ() + 0.5 + face.getStepZ() * 0.52;
        for (int i = 0; i < 6; i++) {
            double out = 0.03 + r.nextFloat() * 0.04;
            mc.level.addParticle(ParticleTypes.FLAME, x + (r.nextFloat() - 0.5F) * 0.4F, y + r.nextFloat() * 0.25F, z + (r.nextFloat() - 0.5F) * 0.4F,
                    face.getStepX() * out, 0.01 + r.nextFloat() * 0.02, face.getStepZ() * out);
        }
        for (int i = 0; i < 3; i++)
            mc.level.addParticle(ParticleTypes.LAVA, x, y + 0.2, z, 0, 0, 0);
    }

    /** The HUD steps aside while the scene holds the view (subtitles and chat stay). */
    /** The weapon held to the anvil: its window, in the Hall's own form unless the old screens are on. */
    private static void onHeld(AnvilPayloads.Held msg) {
        Minecraft mc = Minecraft.getInstance();
        if (dev.awaken.hall.client.HallConfig.classic() || !msg.rites()) mc.gui.setScreen(new AnvilHeldScreen(msg));
        else dev.awaken.hall.client.AnvilStage.show(msg);
    }

    private static void hideHud(RenderGuiLayerEvent.Pre e) {
        if (sceneAnvil() == null && !(Minecraft.getInstance().gui.screen() instanceof dev.awaken.hall.client.AnvilStage)) return;
        String path = e.getName().getPath();
        if (path.equals("subtitle_overlay") || path.equals("chat")) return;
        e.setCanceled(true);
    }

    // ---- the tooltip ---------------------------------------------------------------------------------------------------

    /** A weapon forged at the anvil says how well, plainly, its flaw if it has one, and who made it. */
    private static void onTooltip(ItemTooltipEvent e) {
        if (e.getItemStack().is(AnvilContent.ANVIL_ITEM.get())) {
            // said once, where it's true (SPEC §46.5)
            e.getToolTip().add(Math.min(1, e.getToolTip().size()), Component.literal("Forges weapons stronger than any crafted sword or axe.").withColor(0x9AA6B8));
            return;
        }
        WeaponForged f = e.getItemStack().get(AnvilContent.FORGED.get());
        if (f == null) return;
        var lines = e.getToolTip();
        int at = Math.min(1, lines.size());
        lines.add(at++, Component.literal(f.line()).withColor(AnvilSong.Quality.of(f.quality()).colour & 0xFFFFFF));
        if (!f.maker().isEmpty()) lines.add(at, Component.literal((f.reforged() ? "Reforged by " : "Forged by ") + f.maker()).withColor(0x6F8299));
    }

    // ---- shared ------------------------------------------------------------------------------------------------------

    public static void send(BlockPos pos, int act, int a, float t, float aim) {
        ClientPacketDistributor.sendToServer(new AnvilPayloads.Act(pos, act, a, t, aim));
    }

    static @Nullable WarAnvilBlockEntity anvil(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof WarAnvilBlockEntity be ? be : null;
    }

    /** The client's game time with the frame's part of a tick. */
    static float now(float partial) {
        var level = Minecraft.getInstance().level;
        return level == null ? 0F : level.getGameTime() + partial;
    }

    /** A sound the smith's own client plays the moment they act (the server plays it to everyone else). */
    /** A sound that can be stopped: what a held press starts and letting go ends (the colours, the pour). */
    static net.minecraft.client.resources.sounds.SoundInstance held(Vec3 at, SoundEvent sound, float volume, float pitch) {
        // looped: the game's fire is shorter than a long press, and letting go stops it either way
        var it = new net.minecraft.client.resources.sounds.SimpleSoundInstance(sound.location(), SoundSource.BLOCKS, volume, pitch,
                net.minecraft.client.resources.sounds.SoundInstance.createUnseededRandom(), true, 0,
                net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR, at.x, at.y, at.z, false);
        Minecraft.getInstance().getSoundManager().play(it);
        return it;
    }

    static void play(Vec3 at, SoundEvent sound, float volume, float pitch) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, pitch, false);
    }
}
