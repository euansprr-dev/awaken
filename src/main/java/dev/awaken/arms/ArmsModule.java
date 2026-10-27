package dev.awaken.arms;

import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.AwakenCommands;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.BlocksAttacks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Arms (SPEC §25): weapons and combat. Three verbs on every melee weapon (strike, guard, dodge), told enemy blows
 * answered by deflect, block and dodge, stances that break, and the styles' moves on one timing table. Owns package
 * {@code dev.awaken.arms}; reads blows' classes and creatures' tells through {@code core.api.Strikes}.
 */
public final class ArmsModule {
    private ArmsModule() {}

    public static void init(IEventBus modBus) {
        ArmsConfig.register();
        ArmsContent.register(modBus);
        ArmsSounds.register(modBus);
        ArmsItems.register(modBus);
        Archetypes.all(); // fail fast on a broken timing table
        dev.awaken.core.api.WeaveHooks.install(ArmsWeaves.INSTANCE);
        ArmsForge.init();
        dev.awaken.core.api.ArmsApi.install(ArmsIssue.INSTANCE);
        ArmsTrack.init();
        ArmsTrails.init();
        modBus.addListener(ArmsModule::registerPayloads);
        modBus.addListener(EventPriority.LOW, ArmsModule::guardComponents);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ArmsServer::onPlayerTick);
        bus.addListener(ArmsServer::onServerTick);
        bus.addListener(ArmsServer::onEntityTick);
        bus.addListener(ArmsServer::onLeave);
        bus.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                ArmsWeaves.sync(sp);
                ArmsMastery.sync(sp);
                ArmsIssue.migrate(sp);
                ArmsArmoury.sendView(sp);
            }
        });
        bus.addListener(EventPriority.HIGHEST, ArmsServer::onIncomingDamage);
        bus.addListener(EventPriority.LOWEST, ArmsServer::onIncomingPinned);
        bus.addListener(ArmsServer::onShieldBlock);
        bus.addListener(EventPriority.HIGHEST, ArmsServer::onAttack);
        bus.addListener(ArmsServer::onSweep);
        bus.addListener(ArmsServer::onSound);
        bus.addListener(ArmsServer::onDamaged);
        bus.addListener(ArmsServer::onFall);
        bus.addListener(EventPriority.LOW, LordForged::onDeath);
        bus.addListener(EventPriority.LOW, ArmsMastery::onDeath);
        bus.addListener(EventPriority.LOW, MarkedDrops::onDeath);
        bus.addListener(EventPriority.LOW, ArmsIssue::onDrops);
        bus.addListener(EventPriority.LOW, ArmsPatterns::onDeath);
        bus.addListener(EventPriority.LOW, ArmsSpoils::onDeath);
        bus.addListener(ArmsTiers::onPickup);
        bus.addListener(ArmsStands::onPlayerTick);
        dev.awaken.progression.Progression.onLevelReached(ArmsTiers::onLevel);
        bus.addListener(ArmsPatterns::onJoin);
        bus.addListener(ArmsPatterns::onServerTick);
        bus.addListener(ArmsIssue::onPlayerTick);
        bus.addListener(EventPriority.HIGH, ArmsForge::onUseBlock);
        bus.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) -> { ArmsForge.forget(e.getEntity().getUUID()); ArmsArmoury.forget(e.getEntity().getUUID()); ArmsStands.forget(e.getEntity().getUUID()); });
        bus.addListener(EventPriority.HIGH, ArmsModule::onUseStartOff);
        bus.addListener(ArmsServer::onUseStart);
        bus.addListener(ArmsServer::onUseStop);
        registerCommands();
        ArmsTests.register();
        ArmsProgressionTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(ArmsPayloads.Press.TYPE, ArmsPayloads.Press.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ArmsServer.onPress(sp, msg);
        });
        r.playToServer(ArmsPayloads.Dodge.TYPE, ArmsPayloads.Dodge.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ArmsServer.onDodge(sp, msg);
        });
        r.playToServer(ArmsPayloads.IssuePick.TYPE, ArmsPayloads.IssuePick.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ArmsIssue.onPick(sp, msg.style());
        });
        r.playToServer(ArmsPayloads.ArmouryAct.TYPE, ArmsPayloads.ArmouryAct.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ArmsArmoury.onAct(sp, msg);
        });
        r.playToClient(ArmsPayloads.ArmouryView.TYPE, ArmsPayloads.ArmouryView.STREAM_CODEC);
        r.playToClient(ArmsPayloads.ArmouryDetail.TYPE, ArmsPayloads.ArmouryDetail.STREAM_CODEC);
        r.playToClient(ArmsPayloads.IssueOffer.TYPE, ArmsPayloads.IssueOffer.STREAM_CODEC);
        r.playToClient(ArmsPayloads.Result.TYPE, ArmsPayloads.Result.STREAM_CODEC);
        r.playToClient(ArmsPayloads.Seen.TYPE, ArmsPayloads.Seen.STREAM_CODEC);
        r.playToClient(ArmsPayloads.Woven.TYPE, ArmsPayloads.Woven.STREAM_CODEC);
        r.playToClient(ArmsPayloads.Mastery.TYPE, ArmsPayloads.Mastery.STREAM_CODEC);
    }

    /**
     * Every sword raises a guard on right mouse: the blocking component, no delay, a 120° front. Arms judges what the
     * guard does with each blow (deflect, block, a broken guard), so the component's own numbers only mark the front.
     * Tags don't exist this early, so swords are found by name (vanilla's, the Hunter blades, other mods' "_sword").
     */
    private static void guardComponents(ModifyDefaultComponentsEvent event) {
        event.modifyMatching((item, components) -> Arms.plainStyle(BuiltInRegistries.ITEM.getKey(item)) != null && !components.has(DataComponents.BLOCKS_ATTACKS),
                (components, context, item) -> {
                    GUARDED.add(item);
                    Archetypes.Loaded style = Arms.styleOf(item);
                    float angle = style == null ? 120F : style.def().guard().angle();
                    components.set(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(0F, 1F,
                            List.of(new BlocksAttacks.DamageReduction(angle, Optional.empty(), 0F, 0.7F)),
                            new BlocksAttacks.ItemDamageFunction(4F, 0F, 0.25F),
                            Optional.of(context.lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypeTags.BYPASSES_SHIELD)),
                            Optional.empty(), Optional.empty()));
                });
    }

    /** The items whose guard is ours (a sword another mod made to block keeps its own, and keeps it with Arms off). */
    static final Set<Item> GUARDED = Collections.newSetFromMap(new IdentityHashMap<>());

    /** With Arms off a sword is vanilla's again: the guard we gave it never rises (both sides, so nothing is predicted). */
    private static void onUseStartOff(LivingEntityUseItemEvent.Start e) {
        if (!ArmsConfig.enabled() && GUARDED.contains(e.getItem().getItem())) e.setCanceled(true);
    }

    private static void registerCommands() {
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("arms").executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            var style = Arms.styleOf(p.getMainHandItem());
            ArmsState s = ArmsServer.state(p);
            String line = style == null ? "No fighting style in hand." : style.def().name() + " · resonance " + (int) s.resonance
                    + " · dodges " + Math.max(0, s.dodgeCharges) + (s.move == null ? "" : " · " + s.move.key + " (cadence " + s.cadence + ")");
            c.getSource().sendSuccess(() -> Component.literal(line), false);
            return 1;
        }).then(Commands.literal("weave").requires(AwakenCommands.admin())
                // dev: open a Weave's beat and weave a family at once (tours shoot each shape): /awaken arms weave <1..4> <FAMILY>
                .then(Commands.argument("opening", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 4))
                        .then(Commands.argument("family", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            ArmsState s = ArmsServer.state(p);
                            long now = ArmsServer.now(p);
                            int opening = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "opening");
                            if (opening == ArmsWeaves.DODGE) {
                                s.dodgeFromX = p.getX();
                                s.dodgeFromY = p.getY();
                                s.dodgeFromZ = p.getZ();
                            }
                            ArmsWeaves.open(p, s, opening, now, -1);
                            String fam = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "family").toUpperCase(java.util.Locale.ROOT);
                            ArmsWeaves.INSTANCE.cast(p, fam, null, 3);
                            return 1;
                        }))))
                // dev: kills with a style (mastery), a Mark on the weapon in hand, its attunement
                .then(Commands.literal("mastery").requires(AwakenCommands.admin())
                        .then(Commands.argument("style", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.argument("kills", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1)).executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    Archetypes.Loaded st = Archetypes.get(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "style"));
                                    if (st == null) return 0;
                                    int step = ArmsMastery.count(p, st, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "kills"));
                                    c.getSource().sendSuccess(() -> Component.literal(st.def().name() + " mastery " + step), false);
                                    return 1;
                                }))))
                .then(Commands.literal("mark").requires(AwakenCommands.admin())
                        .then(Commands.argument("mark", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            String id = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "mark");
                            var stack = p.getMainHandItem();
                            if (Marks.get(id) == null || Arms.styleOf(stack) == null) return 0;
                            stack.set(ArmsContent.INSTANCE.get(), Marks.of(stack).withMark(id));
                            return 1;
                        })))
                .then(Commands.literal("attune").requires(AwakenCommands.admin())
                        .then(Commands.argument("force", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            var stack = p.getMainHandItem();
                            dev.awaken.core.Forces.Force f;
                            try {
                                f = dev.awaken.core.Forces.Force.valueOf(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "force").toUpperCase(java.util.Locale.ROOT));
                            } catch (IllegalArgumentException e) {
                                return 0;
                            }
                            if (Arms.styleOf(stack) == null) return 0;
                            stack.set(ArmsContent.INSTANCE.get(), Marks.of(stack).attuned(f));
                            return 1;
                        })))
                // dev: a Lord-forged weapon's reveal (to the hand) or its Art letting go: /awaken arms lord <lord> reveal|art
                .then(Commands.literal("lord").requires(AwakenCommands.admin())
                        .then(Commands.argument("lord", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .then(Commands.literal("reveal").executes(c -> lordDev(c, true)))
                                .then(Commands.literal("art").executes(c -> lordDev(c, false)))))));
    }

    private static int lordDev(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> c, boolean reveal)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        LordForged.Forged f = LordForged.byBoss("awaken:" + com.mojang.brigadier.arguments.StringArgumentType.getString(c, "lord"));
        Item item = f == null ? null : ArmsItems.item(f.weapon());
        if (item == null) {
            c.getSource().sendFailure(Component.literal("No Lord-forged weapon for that Lord."));
            return 0;
        }
        if (reveal) LordForged.reveal(p, LordForged.stack(f, item), f);
        else LordForged.unleash(p, f);
        return 1;
    }
}
