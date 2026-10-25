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
 * Arms (SPEC §24): weapons and combat. Three verbs on every melee weapon (strike, guard, dodge), told enemy blows
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
        modBus.addListener(ArmsModule::registerPayloads);
        modBus.addListener(EventPriority.LOW, ArmsModule::guardComponents);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ArmsServer::onPlayerTick);
        bus.addListener(ArmsServer::onServerTick);
        bus.addListener(ArmsServer::onEntityTick);
        bus.addListener(ArmsServer::onLeave);
        bus.addListener(EventPriority.HIGHEST, ArmsServer::onIncomingDamage);
        bus.addListener(ArmsServer::onShieldBlock);
        bus.addListener(EventPriority.HIGHEST, ArmsServer::onAttack);
        bus.addListener(ArmsServer::onSweep);
        bus.addListener(ArmsServer::onSound);
        bus.addListener(ArmsServer::onDamaged);
        bus.addListener(ArmsServer::onFall);
        bus.addListener(EventPriority.HIGH, ArmsModule::onUseStartOff);
        bus.addListener(ArmsServer::onUseStart);
        bus.addListener(ArmsServer::onUseStop);
        registerCommands();
        ArmsTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToServer(ArmsPayloads.Press.TYPE, ArmsPayloads.Press.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ArmsServer.onPress(sp, msg);
        });
        r.playToServer(ArmsPayloads.Dodge.TYPE, ArmsPayloads.Dodge.STREAM_CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) ArmsServer.onDodge(sp, msg);
        });
        r.playToClient(ArmsPayloads.Result.TYPE, ArmsPayloads.Result.STREAM_CODEC);
        r.playToClient(ArmsPayloads.Seen.TYPE, ArmsPayloads.Seen.STREAM_CODEC);
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
        })));
    }
}
