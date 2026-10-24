package dev.awaken.powers;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.Later;
import dev.awaken.core.api.NowHooks;
import dev.awaken.powers.net.StarterPayloads;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Requirements;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The first power (plan v2 §4.1): on awakening you choose one of three E-tier attack powers, each from a different
 * family. Closing the cards without choosing is fine: the Now tracker says "Choose your first power · K" and the
 * cards come back on login. The choice is saved before the power is learned, so it can never be granted twice.
 */
public final class Starter {
    private Starter() {}

    /** The pool, in display order. Never hidden powers (those are Latent and Path gifts). */
    public static final List<String> POOL = List.of("awaken:haymaker", "awaken:shockwave", "awaken:charged_shot",
            "awaken:beam", "awaken:spirit_palm", "awaken:shadow_step");

    /** One line per card; the full description is shown when you hover it. */
    public static final Map<String, String> BLURB = Map.of(
            "awaken:haymaker", "Hold, then release: a punch that launches.",
            "awaken:shockwave", "Stamp a wave that bowls foes over.",
            "awaken:charged_shot", "Charge a bolt of lightning that stuns.",
            "awaken:beam", "Pour a searing beam from your hand.",
            "awaken:spirit_palm", "A palm strike that hits twice.",
            "awaken:shadow_step", "Step behind a foe for a backstab.");

    /** offer = the three cards (rolled once, so a re-offer shows the same ones); chosen = "" until you pick. */
    public record State(int schema, List<String> offer, String chosen) {
        public static final int SCHEMA = 1;
        public static final State NEW = new State(SCHEMA, List.of(), "");
        /** Marks players who already had attack powers before the choice existed (v1 saves). */
        static final String HAD_POWERS = "-";
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schema", SCHEMA).forGetter(State::schema),
                Codec.STRING.listOf().optionalFieldOf("offer", List.of()).forGetter(State::offer),
                Codec.STRING.optionalFieldOf("chosen", "").forGetter(State::chosen)
        ).apply(i, State::new));
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    public static final Supplier<AttachmentType<State>> STATE = ATTACHMENTS.register("first_power", () -> AttachmentType
            .builder(() -> State.NEW)
            .serialize(State.CODEC.fieldOf("v"))
            .copyOnDeath()
            .build());

    static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(Starter::payloads);
        // let the Accept teleport and its toasts land first; the client also waits for any open screen to close
        // after the reveal (the world changing around you is the moment; the cards come once it has played)
        Progression.onAwakened(p -> Later.run(p.level().getServer(), 140, () -> offer(p)));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, e -> {
            if (!(e.getEntity() instanceof ServerPlayer p) || !Progression.profile(p).awakened()) return;
            migrate(p);
            if (pending(p)) Later.run(p.level().getServer(), 60, () -> offer(p));
        });
        NowHooks.register(p -> pending(p) && !dev.awaken.core.api.OpeningHooks.inOpening(p)
                ? List.of(NowHooks.Suggestion.of("powers:first_power", 85, "Choose your first power · K"))
                : List.of());
        Requirements.powerCheck = Starter::meets;
        commands();
    }

    private static void payloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(StarterPayloads.Open.TYPE, StarterPayloads.Open.STREAM_CODEC);
        r.playToClient(StarterPayloads.Done.TYPE, StarterPayloads.Done.STREAM_CODEC);
        r.playToServer(StarterPayloads.Choose.TYPE, StarterPayloads.Choose.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) choose(sp, msg.power());
        });
    }

    public static State state(ServerPlayer p) {
        return p.getData(STATE.get());
    }

    /** Awakened and not chosen yet. */
    public static boolean pending(ServerPlayer p) {
        return Progression.profile(p).awakened() && state(p).chosen().isEmpty();
    }

    /** Shows the three cards (rolling them the first time). Does nothing once chosen. */
    public static void offer(ServerPlayer p) {
        if (p.isRemoved() || !pending(p)) return;
        // Carillon's opening offers the first power at the Registry, after the licence and the Holo (SPEC §16.4)
        if (dev.awaken.core.api.OpeningHooks.inOpening(p) && !"registry".equals(dev.awaken.core.api.OpeningHooks.stage(p))) return;
        State st = state(p);
        if (st.offer().isEmpty()) {
            st = new State(State.SCHEMA, roll(p.getRandom()), "");
            p.setData(STATE.get(), st);
            DirectorLog.log(p, "powers", "first power offer: " + st.offer());
        }
        AwakenNet.send(p, new StarterPayloads.Open(st.offer()));
    }

    /** Three cards, each from a different family, shown in pool order. */
    static List<String> roll(RandomSource random) {
        Map<PowerType.Family, List<String>> byFamily = new LinkedHashMap<>();
        for (String id : POOL) {
            PowerType t = PowerRegistry.get(id);
            if (t != null) byFamily.computeIfAbsent(t.family, f -> new ArrayList<>()).add(id);
        }
        List<PowerType.Family> families = new ArrayList<>(byFamily.keySet());
        List<String> picked = new ArrayList<>();
        while (picked.size() < 3 && !families.isEmpty()) {
            List<String> ids = byFamily.get(families.remove(random.nextInt(families.size())));
            picked.add(ids.get(random.nextInt(ids.size())));
        }
        picked.sort((a, b) -> Integer.compare(POOL.indexOf(a), POOL.indexOf(b)));
        return List.copyOf(picked);
    }

    /** Learns and equips one of the offered cards. False if it isn't one of them or the choice was already made. */
    public static boolean choose(ServerPlayer p, String id) {
        if (!pending(p)) return false;
        State st = state(p);
        if (!st.offer().contains(id)) return false;
        PowerType t = PowerRegistry.get(id);
        if (t == null) return false;
        p.setData(STATE.get(), new State(State.SCHEMA, st.offer(), id)); // first: never twice, even if learning throws
        Powers.learn(p, t.id, 0);
        Loadout l = Powers.loadout(p);
        if (l.slotOf(t.id.toString()) < 0) {
            int slot = 0;
            for (int i = 0; i < 4; i++) if (l.slot(i).isEmpty()) { slot = i; break; }
            Powers.equip(p, slot, t.id.toString());
        }
        dev.awaken.core.Playtest.log(p, "first_power", "power", id);
        DirectorLog.log(p, "powers", "first power chosen: " + id);
        AwakenNet.send(p, new StarterPayloads.Done(id));
        return true;
    }

    /** v1 saves: an awakened player who already owns an attack power keeps what they have and isn't offered more. */
    static void migrate(ServerPlayer p) {
        State st = state(p);
        if (!st.chosen().isEmpty() || !st.offer().isEmpty()) return;
        for (String id : Powers.loadout(p).owned().keySet()) {
            PowerType t = PowerRegistry.get(id);
            if (t != null && t.slot == PowerType.Slot.ACTIVE) {
                p.setData(STATE.get(), new State(State.SCHEMA, List.of(), State.HAD_POWERS));
                return;
            }
        }
    }

    /** What Directives and drills may require (progression's Requirements power tokens). */
    static boolean meets(ServerPlayer p, String token) {
        Loadout l = Powers.loadout(p);
        if (token.startsWith("power:")) return l.owns(normal(token.substring("power:".length())));
        String family = token.startsWith("family:") ? token.substring("family:".length()).toUpperCase(Locale.ROOT) : null;
        for (String id : l.owned().keySet()) {
            PowerType t = PowerRegistry.get(id);
            if (t == null) continue;
            boolean ok = switch (token) {
                case "movement" -> t.slot == PowerType.Slot.MOVEMENT;
                case "dash" -> t.dashLike;
                case "active" -> !t.isPassive();
                case "forces" -> !t.forces.isEmpty();
                default -> family != null && t.family.name().equals(family);
            };
            if (ok) return true;
        }
        return false;
    }

    private static String normal(String id) {
        return id.contains(":") ? id : Awaken.MODID + ":" + id;
    }

    private static void commands() {
        AwakenCommands.add((root, ctx) -> {
            // /awaken powers starter [1|2|3]: show the cards again, or pick one (same as clicking it)
            root.then(Commands.literal("powers").then(Commands.literal("starter")
                    .executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        if (!pending(p)) {
                            c.getSource().sendFailure(Component.literal("Your first power is already chosen."));
                            return 0;
                        }
                        offer(p);
                        return 1;
                    })
                    .then(Commands.argument("card", IntegerArgumentType.integer(1, 3)).executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        if (pending(p) && state(p).offer().isEmpty()) offer(p);
                        List<String> cards = state(p).offer();
                        int n = IntegerArgumentType.getInteger(c, "card");
                        boolean ok = n <= cards.size() && choose(p, cards.get(n - 1));
                        if (!ok) c.getSource().sendFailure(Component.literal("Nothing to choose (already chosen, or not awakened)."));
                        return ok ? 1 : 0;
                    }))));
            root.then(Commands.literal("admin").requires(AwakenCommands.admin())
                    .then(Commands.literal("powers").then(Commands.literal("starter").then(Commands.literal("reset").executes(c -> {
                        ServerPlayer p = c.getSource().getPlayerOrException();
                        p.setData(STATE.get(), State.NEW);
                        c.getSource().sendSuccess(() -> Component.literal("First-power choice reset (powers you own are kept)."), false);
                        return 1;
                    })))));
        });
    }
}
