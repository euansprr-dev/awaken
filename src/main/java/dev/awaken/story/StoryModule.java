package dev.awaken.story;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.CoreModule;
import dev.awaken.core.api.StoryHooks;
import dev.awaken.progression.Progression;
import dev.awaken.progression.RankHooks;
import dev.awaken.signal.Signal;
import dev.awaken.story.net.StoryPayloads;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The story: prologue, Revelations, the Rival, the Hollow Court, the Seed and the endings (docs/STORY.md). */
public final class StoryModule {
    private StoryModule() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    public static final Supplier<AttachmentType<StoryProgress>> PROGRESS = ATTACHMENTS.register("story", () -> AttachmentType
            .builder(StoryProgress::new)
            .serialize(StoryProgress.CODEC.fieldOf("story"))
            .copyOnDeath()
            .sync((holder, to) -> holder == to, net.minecraft.network.codec.ByteBufCodecs.fromCodec(StoryProgress.CODEC))
            .build());

    public static final DeferredHolder<EntityType<?>, EntityType<VaelEntity>> VAEL = ENTITIES.registerEntityType(
            "vael", VaelEntity::new, MobCategory.MISC, b -> b.sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).noLootTable());

    public static final DeferredItem<SpawnEggItem> VAEL_EGG = ITEMS.registerItem("vael_spawn_egg", SpawnEggItem::new, p -> p.spawnEgg(VAEL.get()));

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        ENTITIES.register(modBus);
        RitualSounds.init(modBus);
        ITEMS.register(modBus);
        modBus.addListener(StoryModule::attributes);
        modBus.addListener(StoryModule::payloads);
        modBus.addListener(StoryModule::tab);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(StoryModule::reload);
        bus.addListener(PlayerTickEvent.Post.class, Prologue::onTick);
        bus.addListener(PlayerTickEvent.Post.class, Beats::onTick);
        bus.addListener(BreakBlockEvent.class, Prologue::onBreak);
        bus.addListener(LivingIncomingDamageEvent.class, Prologue::onIncomingDamage);
        bus.addListener(LivingIncomingDamageEvent.class, Ascension::onIncomingDamage);
        bus.addListener(net.neoforged.bus.api.EventPriority.LOWEST, net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre.class, Ritual::onDamagePre);
        bus.addListener(ServerTickEvent.Post.class, Ritual::onServerTick);
        bus.addListener(EntityJoinLevelEvent.class, Court::onJoin);
        bus.addListener(LivingDeathEvent.class, Court::onDeath);
        bus.addListener(ServerTickEvent.Post.class, e -> {
            Rival.tick(e.getServer());
            Court.tick(e.getServer());
            Court.courtTick(e.getServer());
        });
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                Prologue.sync(p);
                Prologue.onLogin(p);
                sendSeen(p);
                Court.reofferEnding(p);
            }
        });
        Signal.onAction("story", Prologue::onAnswer);
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) Court.reofferEnding(p);
        });
        bus.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> {
            Court.forget();
            Rival.forget();
            Ritual.forget();
        });
        RankHooks.onRankUp(Rival::onRankUp);
        Progression.onNewThing(Beats::onNewThing);
        StoryHooks.install(new StoryHooks.State() {
            @Override public boolean gatesCalm(MinecraftServer server) { return StoryWorld.get(server).gatesCalm; }
            @Override public boolean colourRestored(MinecraftServer server) { return StoryWorld.get(server).colourRestored; }
            @Override public int ascension(MinecraftServer server) { return StoryWorld.get(server).ascension; }
            @Override public int ascension(ServerPlayer p) { return StoryWorld.get(p.level().getServer()).ascensionOf(p.getUUID()); }
            @Override public boolean gatesCalm(MinecraftServer server, java.util.Optional<java.util.UUID> owner) {
                StoryWorld w = StoryWorld.get(server);
                // a gate rolled for someone who hasn't reached the world's ending still breaks: their story isn't over
                return w.gatesCalm && owner.map(w::reached).orElse(true);
            }
            @Override public java.util.Optional<String> refusesScriptedGate(ServerPlayer p, String gateId) {
                return Prologue.storyOn() ? Ritual.refuses(p, gateId) : java.util.Optional.empty();
            }
            @Override public java.util.Optional<String> seedSealed(ServerPlayer p) {
                if (!Prologue.storyOn()) return java.util.Optional.empty();
                int left = 8 - Prologue.progress(p).sovereignsDefeated().size();
                return left <= 0 ? java.util.Optional.empty()
                        : java.util.Optional.of("The Hollow Court still holds the Seed shut. " + left + " Sovereign" + (left == 1 ? "" : "s") + " remain.");
            }
        });
        // Now tracker: the ritual's hall (waiting for everyone; the bell calling the others)
        dev.awaken.core.api.NowHooks.register(Ritual::now);
        // Now tracker: the story's gate waits for you; say which floor is next
        dev.awaken.core.api.NowHooks.register(p -> {
            if (!Progression.profile(p).awakened() || dev.awaken.core.api.GateHooks.inGate(p)) return List.of();
            StoryProgress s = Prologue.progress(p);
            int floor = Prologue.cryptNextFloor(p, s);
            if (floor <= 0) return List.of();
            String text = "Bell Crypt · floor " + floor;
            var mouth = dev.awaken.core.api.GateHooks.get(p.level().getServer().overworld(), s.prologueGate).map(g -> g.mouth());
            return List.of(mouth.isPresent() && p.level().dimension() == net.minecraft.world.level.Level.OVERWORLD
                    ? dev.awaken.core.api.NowHooks.Suggestion.at("story:bell_crypt", 70, text, mouth.get())
                    : dev.awaken.core.api.NowHooks.Suggestion.of("story:bell_crypt", 70, text));
        });
        commands();
        StoryTests.register();
    }

    private static void reload(AddServerReloadListenersEvent e) {
        StoryContent.addListeners(e);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(VAEL.get(), VaelEntity.createAttributes().build());
    }

    private static void tab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey().equals(CoreModule.TAB_KEY)) e.accept(VAEL_EGG.get());
    }

    private static void payloads(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(StoryPayloads.OpenEnding.TYPE, StoryPayloads.OpenEnding.STREAM_CODEC);
        r.playToClient(StoryPayloads.Credits.TYPE, StoryPayloads.Credits.STREAM_CODEC);
        r.playToClient(StoryPayloads.Seen.TYPE, StoryPayloads.Seen.STREAM_CODEC);
        r.playToClient(StoryPayloads.RitualScene.TYPE, StoryPayloads.RitualScene.STREAM_CODEC);
        r.playToClient(StoryPayloads.RitualCue.TYPE, StoryPayloads.RitualCue.STREAM_CODEC);
        r.playToServer(StoryPayloads.ChooseEnding.TYPE, StoryPayloads.ChooseEnding.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) Court.choose(p, msg.which());
        });
    }

    /** Revelation titles/bodies the player has seen, for the client Codex. */
    static void sendSeen(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        for (String k : Prologue.progress(p).seen) {
            if (!k.startsWith("revelation:")) continue;
            var r = StoryContent.revelation(k.substring("revelation:".length()));
            if (r != null) out.add(r.title() + "\u0001" + r.body());
        }
        AwakenNet.send(p, new StoryPayloads.Seen(out));
    }

    private static void commands() {
        AwakenCommands.add((root, ctx) -> {
            var story = Commands.literal("story");
            story.then(Commands.literal("skip").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                Prologue.skip(p);
                c.getSource().sendSuccess(() -> Component.literal("Prologue skipped. You are awakened."), false);
                return 1;
            }));
            story.then(Commands.literal("status").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                StoryProgress s = Prologue.progress(p);
                StoryWorld w = StoryWorld.get(p.level().getServer());
                c.getSource().sendSuccess(() -> Component.literal("Prologue " + s.prologue + " · seen " + s.seen.size()
                        + " · Sovereigns " + s.sovereignsDefeated.size() + "/8 · Seed open " + Court.seedOpen(p)
                        + " · ending '" + w.ending + "'" + (w.endingByName.isEmpty() ? "" : " (chosen by " + w.endingByName + ")")
                        + " · ascension " + w.ascension + " (yours " + w.ascensionOf(p.getUUID()) + ")"), false);
                return 1;
            }));
            var admin = Commands.literal("admin").requires(AwakenCommands.admin());
            var adminStory = Commands.literal("story");
            adminStory.then(Commands.literal("revelation").then(Commands.argument("key", StringArgumentType.word()).executes(c -> {
                Beats.fire(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "key"));
                return 1;
            })));
            adminStory.then(Commands.literal("rival").then(Commands.argument("rank", StringArgumentType.word()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                String r = StringArgumentType.getString(c, "rank").toUpperCase();
                Prologue.progress(p).seen.remove("rival:" + (r.equals("SOVEREIGN") ? "sovereign" : r.toLowerCase()));
                Rival.onRankUp(p, dev.awaken.progression.Rank.valueOf(r));
                return 1;
            })));
            adminStory.then(Commands.literal("ending").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                Prologue.progress(p).seen.add("seed:defeated");
                Prologue.progress(p).seen.remove("ending:chosen");
                // the world keeps one ending: once chosen, this tells it ("ending reset" lets the world choose again)
                if (StoryWorld.get(p.level().getServer()).ended()) Court.tell(p);
                else AwakenNet.send(p, new StoryPayloads.OpenEnding());
                return 1;
            }).then(Commands.literal("reset").executes(c -> {
                StoryWorld w = StoryWorld.get(c.getSource().getServer());
                w.ending = "";
                w.endingBy = null;
                w.endingByName = "";
                w.endingDay = -1;
                w.ascension = 0;
                w.gatesCalm = false;
                w.colourRestored = false;
                w.reached.clear();
                w.setDirty();
                c.getSource().sendSuccess(() -> Component.literal("The world's story has no ending again."), true);
                return 1;
            })));
            adminStory.then(Commands.literal("prologue").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                StoryProgress s = Prologue.progress(p);
                s.prologue = StoryProgress.Prologue.GLITCHED;
                s.prologueSince = p.level().getGameTime() - 20 * 60 * 10;
                Prologue.openFirstGate(p, s);
                return 1;
            }));
            // the Ritual of the Bell (autopilot tours): close the doors now, jump to a beat, or say where it is
            var ritual = Commands.literal("ritual");
            ritual.then(Commands.literal("start").executes(c -> {
                boolean ok = Ritual.forceStart(c.getSource().getPlayerOrException());
                c.getSource().sendSuccess(() -> Component.literal(ok ? "The doors close." : "No ritual hall waiting here."), false);
                return ok ? 1 : 0;
            }));
            ritual.then(Commands.literal("beat").then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 7)).executes(c -> {
                int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "n");
                boolean ok = Ritual.jump(c.getSource().getPlayerOrException(), n);
                c.getSource().sendSuccess(() -> Component.literal(ok ? "Ritual beat " + Ritual.Beat.values()[n] : "No sealed ritual for you."), false);
                return ok ? 1 : 0;
            })));
            ritual.then(Commands.literal("hall").executes(c -> Ritual.place(c.getSource().getPlayerOrException(), -1) ? 1 : 0));
            ritual.then(Commands.literal("plate").then(Commands.argument("k", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 2))
                    .executes(c -> Ritual.place(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "k")) ? 1 : 0)));
            ritual.then(Commands.literal("status").executes(c -> {
                String st = Ritual.status(c.getSource().getPlayerOrException());
                c.getSource().sendSuccess(() -> Component.literal(st), false);
                return 1;
            }));
            adminStory.then(ritual);
            // walk into the story's gate without finding it (autopilot scripts): the gates module's own command does it
            adminStory.then(Commands.literal("enter").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                String gate = Prologue.progress(p).prologueGate;
                if (gate.isEmpty()) {
                    c.getSource().sendFailure(Component.literal("No story gate yet: /awaken admin story prologue opens it."));
                    return 0;
                }
                c.getSource().getServer().getCommands().performPrefixedCommand(c.getSource(), "awaken gate enter " + gate);
                return 1;
            }));
            admin.then(adminStory);
            root.then(story);
            root.then(admin);
        });
    }
}
