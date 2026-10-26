package dev.awaken.opening;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.NowHooks;
import dev.awaken.core.api.OpeningHooks;
import dev.awaken.core.audio.Intensity;
import dev.awaken.core.audio.MusicLayers;
import dev.awaken.opening.net.OpeningPayloads;
import dev.awaken.progression.Progression;
import dev.awaken.story.Story;
import dev.awaken.story.StoryProgress.Prologue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Carillon's opening (SPEC §16.4, Milestone 2): the first eight minutes of a new world. The card and the sky shot down
 * into your eyes on the Runners' Roost; the great bell ringing wrong and the light falling into Choir Square; the run
 * over the roofs in three acts (the Bell Bridge falls behind you, the market runs toward you); the arrival (alone, or
 * waiting for your friends); the Ritual and the Void (the story's own); the infirmary; the Registry; free play.
 *
 * <p>The beat is the story's (StoryProgress appends SKY, RUN, ARRIVAL, WAKE and REGISTRY after DONE, so players already
 * at DONE never see the opening). Skipped by the world option (game rule {@code awaken:skip_opening}), by holding jump
 * through the sky shot after the first viewing (the shot only), and by {@code /awaken opening skip}.
 */
public final class OpeningModule {
    private OpeningModule() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    public static final Supplier<AttachmentType<OpeningState>> STATE = ATTACHMENTS.register("opening", () -> AttachmentType
            .builder(OpeningState::new)
            .serialize(OpeningState.CODEC.fieldOf("opening"))
            .copyOnDeath()
            .build());

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    /** The Hunter's Licence (SPEC §16.4): signed at the Registry desk, your name on it in your own ink. */
    public static final net.neoforged.neoforge.registries.DeferredItem<net.minecraft.world.item.Item> LICENCE = ITEMS.registerSimpleItem(
            "hunters_licence", p -> p.stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON));

    static final DeferredRegister<GameRule<?>> RULES = DeferredRegister.create(Registries.GAME_RULE, Awaken.MODID);
    /** World option "Skip the opening" (Create World → Game Rules): new players start awakened on the Roost. */
    public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> SKIP = RULES.register("skip_opening",
            () -> new GameRule<>(GameRuleCategory.PLAYER, GameRuleType.BOOL, BoolArgumentType.bool(), (visitor, rule) -> visitor.visitBoolean(rule),
                    Codec.BOOL, b -> b ? 1 : 0, false, FeatureFlagSet.of()));

    /** The bell crypt's layered music carries the run (its tension, then its drums), the Lord stem the arrival. */
    static final Identifier RUN_MUSIC = Awaken.id("gate/bell_crypt");

    /**
     * Tours: the sky shot saves its frames (on by default under the autopilot, whose client shares this JVM); no
     * first-run questions (the view stays as the tour set it).
     */
    static volatile boolean tourShots = System.getProperty("awaken.autopilot") != null, noQuestions;

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        ITEMS.register(modBus);
        RULES.register(modBus);
        modBus.addListener(OpeningModule::payloads);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(PlayerTickEvent.Post.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) OpeningDirector.tick(p);
        });
        bus.addListener(ServerTickEvent.Post.class, e -> OpeningDirector.serverTick(e.getServer()));
        // after the story's own login (StoryModule registers first): the scene where it stands, or the beginning
        bus.addListener(EventPriority.LOW, PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) OpeningDirector.onLogin(p);
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p) OpeningDirector.onLogout(p);
        });
        bus.addListener(PlayerEvent.PlayerRespawnEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p && OpeningDirector.inOpening(p)) OpeningDirector.sendScene(p);
        });
        bus.addListener(EventPriority.LOWEST, LivingDamageEvent.Pre.class, OpeningModule::onDamage);
        bus.addListener((ServerStoppingEvent e) -> {
            OpeningDirector.forget();
            OpeningRegistry.forget();
        });
        OpeningHooks.install(new OpeningHooks.Access() {
            @Override public boolean inOpening(net.minecraft.world.entity.player.Player p) {
                return p instanceof ServerPlayer sp && OpeningDirector.inOpening(sp);
            }
            @Override public @Nullable String stage(net.minecraft.world.entity.player.Player p) {
                return p instanceof ServerPlayer sp ? OpeningDirector.stage(sp) : null;
            }
            @Override public net.minecraft.core.@Nullable BlockPos place(String name) {
                OpeningPlaces at = OpeningPlaces.get();
                if (at == null) return null;
                return switch (name) {
                    case "roost" -> at.roost();
                    case "registry" -> net.minecraft.core.BlockPos.containing(at.lensMark());
                    default -> null;
                };
            }
            @Override public void skip(ServerPlayer p) {
                OpeningDirector.skip(p);
            }
        });
        MusicLayers.register(MusicLayers.Slot.MUSIC, 160, "opening", OpeningModule::music);
        NowHooks.register(OpeningModule::now);
        commands();
        OpeningTests.register();
    }

    private static void payloads(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(OpeningPayloads.Scene.TYPE, OpeningPayloads.Scene.STREAM_CODEC);
        r.playToClient(OpeningPayloads.Cue.TYPE, OpeningPayloads.Cue.STREAM_CODEC);
        r.playToClient(OpeningPayloads.Drive.TYPE, OpeningPayloads.Drive.STREAM_CODEC);
        r.playToServer(OpeningPayloads.Ready.TYPE, OpeningPayloads.Ready.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer p) OpeningDirector.onReady(p, msg);
        });
    }

    static boolean skipRule(MinecraftServer server) {
        try {
            return server.overworld().getGameRules().get(SKIP.get());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** No death in the opening (SPEC §16.4): falls and hits leave at least one heart. */
    private static void onDamage(LivingDamageEvent.Pre e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Story.prologue(p).opening()) return;
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return; // /kill still works
        float keep = p.getHealth() + p.getAbsorptionAmount() - 2.0F;
        if (e.getNewDamage() > keep) e.setNewDamage(Math.max(0F, keep));
    }

    /** The run's music: the bell crypt's stems, tension in Bellwatch, its drums on the Quay and in the market. */
    private static @Nullable Identifier music(ServerPlayer p, Intensity.Signals s) {
        Prologue b = Story.prologue(p);
        if (b == Prologue.RUN) {
            s.forced = OpeningDirector.state(p).act >= 2 ? Intensity.COMBAT : Intensity.TENSION;
            return RUN_MUSIC;
        }
        if (b == Prologue.ARRIVAL) {
            OpeningWorld w = OpeningWorld.get(p.level().getServer());
            boolean playing = w.cinematicAt >= 0 && OpeningDirector.now(p) >= w.cinematicAt;
            s.forced = playing ? Intensity.LORD : Intensity.TENSION;
            return RUN_MUSIC;
        }
        return null;
    }

    /** The Now line: the hunters still running while you wait; where to report after the infirmary. */
    private static List<NowHooks.Suggestion> now(ServerPlayer p) {
        Prologue b = Story.prologue(p);
        if (b == Prologue.ARRIVAL) {
            OpeningWorld w = OpeningWorld.get(p.level().getServer());
            if (w.cinematicAt >= 0) return List.of();
            int n = OpeningDirector.runners(p.level().getServer()).size();
            if (n == 0) return List.of();
            return List.of(NowHooks.Suggestion.of("opening:waiting", 95, "The gate is drawing breath · waiting for " + n + " hunter" + (n == 1 ? "" : "s")));
        }
        if (b == Prologue.REGISTRY && OpeningDirector.state(p).step == OpeningRegistry.WALK) {
            OpeningPlaces at = OpeningPlaces.get();
            if (at != null) return List.of(NowHooks.Suggestion.at("opening:registry", 95, "Report to the Registry", net.minecraft.core.BlockPos.containing(at.lensMark())));
        }
        return List.of();
    }

    // ---- what the opening asks of other modules ---------------------------------------------------------------------

    static boolean ritualSealed(MinecraftServer server, String gate) {
        return Story.ritualSealed(server, gate);
    }

    /** Through the portal into the story's gate, once it has formed (false while it still forms). */
    static boolean enterGate(ServerPlayer p, String gate) {
        int id;
        try {
            id = Integer.parseInt(gate);
        } catch (NumberFormatException e) {
            return false;
        }
        var g = dev.awaken.gates.GateRegistry.get(p.level().getServer()).gate(id);
        if (g == null || !g.state.enterable() || !g.interiorReady || g.state == dev.awaken.gates.GateState.BUILDING) return false;
        return dev.awaken.gates.run.Runs.enter(p, g);
    }

    static void acceptInPlace(ServerPlayer p) {
        Story.acceptInPlace(p);
    }

    /** Sun the healer and an Association officer at your bedside (held there by the citizens while you wake). */
    static void infirmaryCast(MinecraftServer server, OpeningPlaces at, int bed, boolean on) {
        Vec3 side = at.bedside(Math.max(0, bed));
        dev.awaken.core.api.CitizenHooks.post(server.overworld(), Awaken.id(on ? "infirmary_wake" : "infirmary_done"),
                net.minecraft.core.BlockPos.containing(side));
    }

    static void offerFirstPower(ServerPlayer p) {
        dev.awaken.powers.Starter.offer(p);
    }

    static boolean firstPowerChosen(ServerPlayer p) {
        return !dev.awaken.powers.Starter.pending(p);
    }

    static void offerDirective(ServerPlayer p) {
        dev.awaken.progression.Directives.tryOffer(p, "opening");
    }

    /** The city is yours: the Awakened layer steps out, the first power waits if it wasn't chosen. */
    static void afterOpening(ServerPlayer p) {
        dev.awaken.world.fauna.FaunaModule.revealIfPending(p);
        OpeningHooks.fireRegistered(p); // the first thread out of the city starts here (SPEC §27.6)
        if (Progression.profile(p).awakened()) dev.awaken.core.Later.run(p.level().getServer(), 60, () -> dev.awaken.powers.Starter.offer(p));
    }

    // ---- commands ---------------------------------------------------------------------------------------------------

    private static void commands() {
        AwakenCommands.add((root, ctx) -> {
            var opening = Commands.literal("opening");
            // your own opening: skip it (the world option does it for everyone)
            opening.then(Commands.literal("skip").executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                OpeningDirector.skip(p);
                c.getSource().sendSuccess(() -> Component.literal("Opening skipped. The city is yours."), false);
                return 1;
            }).then(Commands.argument("player", EntityArgument.player()).requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = EntityArgument.getPlayer(c, "player");
                OpeningDirector.skip(p);
                return 1;
            })));
            opening.then(Commands.literal("status").executes(c -> status(c.getSource())));
            var admin = Commands.literal("restart").requires(AwakenCommands.admin()).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                restart(p);
                return 1;
            });
            opening.then(admin);
            opening.then(Commands.literal("beat").requires(AwakenCommands.admin())
                    .then(Commands.argument("beat", StringArgumentType.word()).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        String b = StringArgumentType.getString(c, "beat");
                        boolean ok = jump(p, b);
                        c.getSource().sendSuccess(() -> Component.literal(ok ? "Opening beat " + b : "No such beat (sky, run, act2, act3, square, arrival, wake, registry, lens, walk0..walk5, desk)"), false);
                        return ok ? 1 : 0;
                    })));
            opening.then(Commands.literal("reset").requires(AwakenCommands.admin()).executes(c -> {
                int n = resetWorld(c.getSource());
                c.getSource().sendSuccess(() -> Component.literal("Opening world state reset (bridge, stage, group); " + n + " chunks repainted."), true);
                return 1;
            }));
            // development: a route driven by its points on your own client (rec: recorded as a golden run)
            opening.then(Commands.literal("drive").requires(AwakenCommands.admin())
                    .then(Commands.argument("route", StringArgumentType.word()).executes(c -> drive(c.getSource(), StringArgumentType.getString(c, "route"), false))
                            .then(Commands.literal("rec").executes(c -> drive(c.getSource(), StringArgumentType.getString(c, "route"), true)))));
            opening.then(Commands.literal("tour").requires(AwakenCommands.admin())
                    .then(Commands.argument("shots", BoolArgumentType.bool()).executes(c -> {
                        tourShots = BoolArgumentType.getBool(c, "shots");
                        noQuestions = tourShots;
                        c.getSource().sendSuccess(() -> Component.literal("Opening tour mode " + tourShots), false);
                        return 1;
                    })));
            root.then(opening);
        });
    }

    private static int status(CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        OpeningState st = OpeningDirector.state(p);
        OpeningWorld w = OpeningWorld.get(p.level().getServer());
        String msg = "Opening: " + Story.prologue(p) + " (" + OpeningDirector.stage(p) + "), act " + st.act + ", sky seen " + st.skyViews
                + ", bed " + st.bed + ", step " + st.step + ", run " + (st.runTicks() >= 0 ? st.runTicks() / 20 + " s" : "-")
                + (st.pulled ? " (pulled)" : "") + " · world: gate '" + w.gate + "', bridge " + (w.bridgeDown ? "down" : "up")
                + ", stage v" + w.stage + ", first arrival " + w.firstArrival + ", cinematic " + w.cinematicAt;
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int drive(CommandSourceStack src, String route, boolean rec) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        OpeningPlaces at = OpeningPlaces.get();
        if (at == null || OpeningRoutes.get().route(route) == null) {
            src.sendFailure(Component.literal("No route " + route + " (or no city): " + OpeningRoutes.get().routes.stream().map(OpeningRoutes.Route::name).toList()));
            return 0;
        }
        OpeningStage.ensure(src.getServer().overworld(), at);
        AwakenNet.send(p, new OpeningPayloads.Drive(route, rec, at.origin()));
        return 1;
    }

    /** Back to the start of the opening (dev): unawakened, the story at NOT_STARTED, the sky again. */
    static void restart(ServerPlayer p) {
        OpeningDirector.state(p).reset();
        if (Progression.profile(p).awakened()) {
            p.sendSystemMessage(Component.literal("Awakened players keep their awakening; the opening restarts as a replay."));
        }
        Story.openingBeat(p, Prologue.NOT_STARTED, OpeningDirector.now(p));
        OpeningDirector.begin(p);
    }

    /** Jumps into a beat (tours and tests): the places and the world state as that beat expects them. */
    static boolean jump(ServerPlayer p, String beat) {
        OpeningPlaces at = OpeningPlaces.get();
        if (at == null) return false;
        MinecraftServer server = p.level().getServer();
        switch (beat) {
            case "sky" -> restart(p);
            case "run" -> OpeningDirector.ringBell(p);
            case "act2", "act3", "square" -> {
                if (Story.prologue(p) != Prologue.RUN) OpeningDirector.ringBell(p);
                Vec3 to = switch (beat) {
                    case "act2" -> at.atV(-105.5, 1, -26.5);
                    case "act3" -> at.atV(-48.5, 1, -8.5);
                    default -> Vec3.atBottomCenterOf(at.square()).add(0, 0, 8);
                };
                p.teleportTo(server.overworld(), to.x, to.y, to.z, java.util.Set.of(), 90F, 0F, true);
            }
            case "arrival" -> {
                if (Story.prologue(p) != Prologue.RUN) OpeningDirector.ringBell(p);
                OpeningDirector.arrive(p, true);
            }
            case "wake" -> {
                // as the Accept leaves it: awakened, in the infirmary's black
                OpeningDirector.state(p).arrived = true;
                Progression.awaken(p);
                Story.openingBeat(p, Prologue.WAKE, OpeningDirector.now(p));
                OpeningDirector.state(p).bed = -1;
            }
            case "walk0", "walk1", "walk2", "walk3", "walk4", "walk5" -> {
                // the Registry walk (tours): a step behind Bram's point k on the way, facing it
                if (Story.prologue(p) != Prologue.REGISTRY) {
                    Progression.awaken(p);
                    OpeningDirector.toRegistry(p);
                }
                Vec3[] way = at.walk();
                int k = Math.min(way.length - 1, beat.charAt(4) - '0');
                Vec3 to = way[k], from = k > 0 ? way[k - 1] : at.bedside(Math.max(0, OpeningDirector.state(p).bed));
                Vec3 back = to.subtract(from).normalize().scale(-2.5);
                Vec3 stand = to.add(back.x, 0, back.z);
                p.teleportTo(server.overworld(), stand.x, stand.y, stand.z, java.util.Set.of(), OpeningRegistry.yawTo(stand, to), 0F, true);
            }
            case "desk" -> {
                // the Registry's desk (tours, tests): the Lens has read you; the register waits
                Progression.awaken(p);
                OpeningStage.ensure(server.overworld(), at);
                if (Story.prologue(p) != Prologue.REGISTRY) OpeningDirector.toRegistry(p);
                OpeningRegistry.step(p, OpeningRegistry.SIGN);
                Vec3 to = at.deskStand();
                p.teleportTo(server.overworld(), to.x, to.y, to.z, java.util.Set.of(), -90F, 30F, true);
            }
            case "registry", "lens" -> {
                Progression.awaken(p);
                OpeningStage.ensure(server.overworld(), at);
                OpeningDirector.toRegistry(p);
                Vec3 to = beat.equals("lens") ? at.lensMark() : at.bedside(0);
                p.teleportTo(server.overworld(), to.x, to.y, to.z, java.util.Set.of(), beat.equals("lens") ? 180F : 90F, 0F, true);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Dev: the Bell Bridge back up (its chunks repainted), the stage rebuilt next time, the group's clock cleared. */
    static int resetWorld(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        OpeningWorld w = OpeningWorld.get(server);
        int n = 0;
        OpeningPlaces at = OpeningPlaces.get();
        if (at != null && w.bridgeDown) {
            Vec3 b = at.bellBridge();
            var s = server.createCommandSourceStack().withPosition(b).withLevel(server.overworld()).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(s, "awaken city repaint 1");
            n = 9;
        }
        w.bridgeDown = false;
        w.stage = 0;
        w.gate = "";
        w.newGroup();
        w.setDirty();
        return n;
    }

    /** A test or command wants the opening's client-facing parts to know (scene sent again). */
    static void resend(ServerPlayer p) {
        if (AwakenNet.canReceive(p, OpeningPayloads.Scene.TYPE)) OpeningDirector.sendScene(p);
    }
}
