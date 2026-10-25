package dev.awaken.atlas;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.api.AtlasHooks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * The Atlas, common half (SPEC §16.8): what each player has explored, kept on the server and sent to its owner;
 * {@code AtlasHooks}; {@code /awaken atlas}. The table view, the tile cache and the renderer are client side
 * ({@code atlas.client}).
 */
public final class AtlasModule {
    private AtlasModule() {}

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    /** A player's Atlas: saved, kept through death, sent by {@link Discovery} (not the attachment sync: it would resend all). */
    public static final Supplier<AttachmentType<AtlasData>> ATLAS = ATTACHMENTS.register("atlas",
            () -> AttachmentType.builder(AtlasData::new).serialize(AtlasData.CODEC.fieldOf("atlas")).copyOnDeath().build());

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(AtlasNet::register);
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) Discovery.tick(sp);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) Discovery.login(sp);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) Discovery.forget(sp);
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) Discovery.sendAll(sp);
        });
        // a bell rung by a player (by hand, or by their arrow): its district clears
        NeoForge.EVENT_BUS.addListener(VanillaGameEvent.class, e -> {
            if (!e.getVanillaEvent().is(GameEvent.BLOCK_CHANGE) || e.getLevel().isClientSide()) return;
            ServerPlayer p = e.getCause() instanceof ServerPlayer sp ? sp
                    : e.getCause() != null && e.getCause() instanceof net.minecraft.world.entity.projectile.Projectile pr && pr.getOwner() instanceof ServerPlayer sp2 ? sp2 : null;
            if (p == null) return;
            BlockPos pos = BlockPos.containing(e.getEventPosition());
            if (e.getLevel().getBlockState(pos).is(Blocks.BELL)) Discovery.ring(p, pos);
        });
        AtlasHooks.install(new AtlasHooks.Access() {
            @Override public void reveal(ServerPlayer p, BlockPos center, int radius) {
                Discovery.reveal(p, center, radius);
            }

            @Override public boolean explored(Player p, BlockPos pos) {
                return Discovery.explored(p, pos);
            }
        });
        AwakenCommands.add((root, ctx) -> {
            LiteralArgumentBuilder<CommandSourceStack> atlas = Commands.literal("atlas");
            atlas.then(Commands.literal("stats").executes(c -> stats(c.getSource().getPlayerOrException())));
            atlas.then(Commands.literal("share").executes(c -> Discovery.share(c.getSource().getPlayerOrException())));
            atlas.then(Commands.literal("reveal").requires(AwakenCommands.admin())
                    .then(Commands.argument("radius", IntegerArgumentType.integer(1, 4096)).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        int n = Discovery.reveal(p, p.blockPosition(), IntegerArgumentType.getInteger(c, "radius"));
                        c.getSource().sendSuccess(() -> Component.literal("Atlas: " + n + " cells cleared"), false);
                        return n;
                    })));
            atlas.then(Commands.literal("ring").requires(AwakenCommands.admin())
                    .then(Commands.argument("bell", StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        String want = StringArgumentType.getString(c, "bell");
                        for (Bells.Bell b : Bells.all()) {
                            if (want.equals(b.wholeCity() ? "city" : b.district())) {
                                Discovery.live(p).lastBell = null;
                                int n = Discovery.ring(p, b);
                                c.getSource().sendSuccess(() -> Component.literal("Atlas: " + b.name() + " cleared " + n + " cells"), false);
                                return n;
                            }
                        }
                        c.getSource().sendFailure(Component.literal("No such bell. Try city, bellwatch, quay, terrace, works, garden."));
                        return 0;
                    })));
            atlas.then(Commands.literal("clear").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                Discovery.data(p).dim(Discovery.dim(p.level())).clear();
                Discovery.data(p).runShown = false;
                Discovery.data(p).trail.clear();
                Discovery.live(p).lastBell = null;
                Discovery.sendAll(p);
                return 1;
            }));
            root.then(atlas);
        });
        AtlasTests.register();
    }

    private static int stats(ServerPlayer p) {
        AtlasData d = Discovery.data(p);
        Explored here = d.peek(Discovery.dim(p.level()));
        p.sendSystemMessage(Component.literal(String.format(Locale.ROOT, "Atlas: %d cells here (%d tiles), %d in all, run trail %d points, reveal %s",
                here == null ? 0 : here.count(), here == null ? 0 : here.tileCount(), d.cells(), d.trail.size() / 3, d.runShown ? "shown" : "not yet")));
        return 1;
    }
}
