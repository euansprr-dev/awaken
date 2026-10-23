package dev.awaken.world.fauna;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.Later;
import dev.awaken.core.api.NowHooks;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.FaunaRole;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.entity.FaunaHooks;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.mobs.entity.NamedBeast;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRuleType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The Awakened layer's creatures (plan v2 section 4.2): natural spawning in three biome families through NeoForge
 * biome modifiers and spawn placements under their own category and cap, the awakening reveal, first sightings
 * (discovery XP, toasts, the Codex), named beasts and their registry, and the Now tracker line for a beast nearby.
 * The creatures themselves are in the mobs module ({@code mobs.def.FaunaBestiary}, {@code mobs.entity.FaunaMob}).
 * See docs/modules/fauna.md.
 */
public final class FaunaModule {
    private FaunaModule() {}

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    public static final Supplier<AttachmentType<FaunaState>> STATE = ATTACHMENTS.register("fauna_state",
            () -> AttachmentType.builder(() -> FaunaState.EMPTY).serialize(FaunaState.MAP_CODEC).copyOnDeath().build());

    static final DeferredRegister<GameRule<?>> RULES = DeferredRegister.create(Registries.GAME_RULE, Awaken.MODID);
    /** {@code /gamerule awaken:fauna_spawning false} switches the overworld creatures off (plan v2 section 6, rollback). */
    public static final DeferredHolder<GameRule<?>, GameRule<Boolean>> SPAWNING = RULES.register("fauna_spawning",
            () -> new GameRule<>(GameRuleCategory.SPAWNING, GameRuleType.BOOL, BoolArgumentType.bool(), (visitor, rule) -> visitor.visitBoolean(rule),
                    Codec.BOOL, b -> b ? 1 : 0, true, FeatureFlagSet.of()));

    public static void init(IEventBus modBus) {
        Awaken.LOG.info("[fauna] {} overworld creatures", Bestiary.fauna().size());
        ATTACHMENTS.register(modBus);
        RULES.register(modBus);
        modBus.addListener(FaunaModule::placements);
        modBus.addListener(FaunaNet::register);
        FaunaHooks.install(new Hooks());
        Progression.onAwakened(Reveal::onAwakened);
        NowHooks.register(FaunaNow::suggest);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post e) -> Sightings.tick(e.getServer()));
        bus.addListener(FaunaModule::onLogin);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> Sightings.forget(e.getEntity().getUUID()));
        bus.addListener(FaunaModule::onDimension);
        bus.addListener((ServerStoppedEvent e) -> {
            FaunaCensus.clear();
            Sightings.clear();
        });
        FaunaCommands.register();
        FaunaTests.register();
    }

    /** The {@code awaken:fauna_spawning} game rule (on by default). */
    public static boolean spawningOn(ServerLevel level) {
        return level.getGameRules().get(SPAWNING.get());
    }

    private static void placements(RegisterSpawnPlacementsEvent e) {
        for (MobDef d : Bestiary.fauna()) {
            EntityType<? extends Mob> type = MobTypes.type(d);
            if (type != null) placement(e, type);
        }
    }

    private static <T extends Mob> void placement(RegisterSpawnPlacementsEvent e, EntityType<T> type) {
        e.register(type, SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, FaunaRules::check, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        FaunaState s = sp.getData(STATE.get());
        // Worlds awakened before the Awakened layer existed get their reveal once, on the next login.
        if (!s.revealed() && !s.revealPending() && Progression.profile(sp).awakened()) sp.setData(STATE.get(), s.pending());
        if (sp.getData(STATE.get()).revealPending()) Later.run(sp.level().getServer(), 60, () -> Reveal.tryReveal(sp));
    }

    private static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && e.getTo() == Level.OVERWORLD && sp.getData(STATE.get()).revealPending()) {
            Later.run(sp.level().getServer(), 20, () -> Reveal.tryReveal(sp));
        }
    }

    /** What the creatures tell the world module. */
    private static final class Hooks implements FaunaHooks.Server {
        @Override
        public void added(FaunaMob mob) {
            FaunaCensus.added(mob);
        }

        @Override
        public void removed(FaunaMob mob) {
            FaunaCensus.removed(mob);
        }

        @Override
        public int contentLevel(ServerLevel level, BlockPos pos, FaunaRole role) {
            return FaunaRules.contentLevel(level, pos, role);
        }

        @Override
        public void beastSeen(NamedBeast beast) {
            if (!(beast.level() instanceof ServerLevel level)) return;
            BlockPos at = beast.blockPosition();
            boolean fresh = BeastRegistry.get(level.getServer()).seen(beast.getUUID(), beast.def().id,
                    level.dimension().identifier().toString(), at, level.getGameTime());
            if (fresh) announce(level, beast);
        }

        @Override
        public void beastGone(NamedBeast beast) {
            if (beast.level() instanceof ServerLevel level) BeastRegistry.get(level.getServer()).gone(beast.getUUID());
        }

        @Override
        public void beastFelled(ServerLevel level, NamedBeast beast, @Nullable ServerPlayer killer) {
            if (killer == null) return;
            MobDef d = beast.def();
            Progression.count(killer, "awaken:beasts_felled");
            boolean first = Progression.discover(killer, "beast_felled/" + d.id, 0.5);
            Progression.markNewThing(killer, "beast:" + d.id);
            Progression.bestMoment(killer, (first ? "First kill: " : "Felled ") + d.name);
            Signal.send(killer, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(d.name + " falls").withColor(d.glow))
                    .body(Component.literal(first ? "A named beast, felled · +XP" : "Its territory is quiet"))
                    .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 6).build());
            DirectorLog.log(killer, "fauna", "felled " + d.id + " lv" + beast.contentLevel() + (first ? " (first)" : ""));
        }

        @Override
        public void beastAngered(NamedBeast beast, ServerPlayer player) {
            if (!Sightings.hasSeen(player, beast.def().id)) Sightings.discover(player, beast.def());
            DirectorLog.log(player, "fauna", beast.def().id + " turned on you");
        }
    }

    /** A new named beast: the nearest awakened player senses it (the Now tracker then points at it). */
    private static void announce(ServerLevel level, NamedBeast beast) {
        ServerPlayer near = null;
        double best = 128 * 128;
        for (ServerPlayer p : level.players()) {
            double d = p.distanceToSqr(beast);
            if (d < best && Progression.profile(p).awakened()) {
                best = d;
                near = p;
            }
        }
        if (near == null) return;
        MobDef d = beast.def();
        Signal.send(near, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(d.name + " is near").withColor(d.glow))
                .body(Component.literal("A named beast holds this ground. It warns before it fights."))
                .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 8).build());
        DirectorLog.log(near, "fauna", "a named beast appeared: " + d.id + " at " + beast.blockPosition().toShortString());
    }
}
