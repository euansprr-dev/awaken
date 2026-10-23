package dev.awaken.powers.content;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import dev.awaken.powers.Loadout;
import dev.awaken.powers.PowerRegistry;
import dev.awaken.powers.PowerType;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Registrations owned by the powers module: the Power Core, constructs, summons, effects, the loadout attachment. */
public final class PowerContent {
    private PowerContent() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Awaken.MODID);
    public static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(Awaken.MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Awaken.MODID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, Awaken.MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    public static final Supplier<AttachmentType<Loadout>> LOADOUT = ATTACHMENTS.register("loadout", () -> AttachmentType
            .builder(Loadout::new)
            .serialize(Loadout.MAP_CODEC)
            .copyOnDeath()
            .sync((holder, to) -> holder == to, Loadout.STREAM_CODEC)
            .build());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PowerCoreData>> CORE_DATA = COMPONENTS.registerComponentType(
            "power_core", b -> b.persistent(PowerCoreData.CODEC).networkSynchronized(PowerCoreData.STREAM_CODEC));

    public static final DeferredItem<PowerCoreItem> POWER_CORE = ITEMS.registerItem("power_core", PowerCoreItem::new,
            p -> p.stacksTo(16).rarity(Rarity.RARE).component(CORE_DATA.get(), PowerCoreData.EMPTY));

    public static final DeferredBlock<ConstructBlock> CONSTRUCT = BLOCKS.registerBlock("construct", ConstructBlock::new,
            p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.6f, 6.0f).sound(SoundType.AMETHYST).noLootTable()
                    .lightLevel(s -> 7).pushReaction(PushReaction.IMMOVEABLE).isValidSpawn((s, l, pos, t) -> false));

    public static final DeferredBlock<ConstructBlock> DREAM_CONSTRUCT = BLOCKS.registerBlock("dream_construct", ConstructBlock::new,
            p -> p.mapColor(MapColor.COLOR_PINK).strength(0.6f, 6.0f).sound(SoundType.AMETHYST).noLootTable()
                    .lightLevel(s -> 9).pushReaction(PushReaction.IMMOVEABLE).isValidSpawn((s, l, pos, t) -> false));

    public static final DeferredHolder<EntityType<?>, EntityType<ShadowSoldier>> SHADOW_SOLDIER = ENTITIES.registerEntityType(
            "shadow_soldier", ShadowSoldier::new, MobCategory.MISC,
            b -> b.sized(0.6f, 1.95f).eyeHeight(1.7f).clientTrackingRange(8).noSave().noSummon().noLootTable());

    public static final DeferredHolder<EntityType<?>, EntityType<PowerDrone>> DRONE = ENTITIES.registerEntityType(
            "power_drone", PowerDrone::new, MobCategory.MISC,
            b -> b.sized(0.5f, 0.6f).eyeHeight(0.3f).clientTrackingRange(8).noSave().noSummon().noLootTable().fireImmune());

    /** Stunned: cannot move, jump or swing (Charge + Water, Shatter, Thunderclap). */
    public static final DeferredHolder<MobEffect, MobEffect> STUNNED = EFFECTS.register("stunned", () -> new StunnedEffect()
            .addAttributeModifier(Attributes.MOVEMENT_SPEED, Awaken.id("effect.stunned.speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(Attributes.JUMP_STRENGTH, Awaken.id("effect.stunned.jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            .addAttributeModifier(Attributes.ATTACK_DAMAGE, Awaken.id("effect.stunned.damage"), -0.6, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    static final class StunnedEffect extends MobEffect {
        StunnedEffect() {
            super(MobEffectCategory.HARMFUL, 0x9AD8FF);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int tickCount, int amplifier) {
            return true;
        }

        @Override
        public boolean applyEffectTick(ServerLevel level, LivingEntity e, int amplifier) {
            if (e instanceof net.minecraft.world.entity.Mob m) {
                m.getNavigation().stop();
                m.setJumping(false);
            }
            if (e.tickCount % 6 == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(),
                        2, 0.25, 0.05, 0.25, 0.02);
            }
            return true;
        }
    }

    public static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        ENTITIES.register(modBus);
        COMPONENTS.register(modBus);
        EFFECTS.register(modBus);
        ATTACHMENTS.register(modBus);
        modBus.addListener(PowerContent::attributes);
        modBus.addListener(PowerContent::creativeTab);
    }

    private static void attributes(EntityAttributeCreationEvent e) {
        e.put(SHADOW_SOLDIER.get(), ShadowSoldier.createAttributes().build());
        e.put(DRONE.get(), PowerDrone.createAttributes().build());
    }

    public static ItemStack core(PowerType type, int variant) {
        ItemStack stack = new ItemStack(POWER_CORE.get());
        stack.set(CORE_DATA.get(), new PowerCoreData(type.id.toString(), Math.max(0, Math.min(type.variants.size() - 1, variant))));
        return stack;
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (!event.getTabKey().equals(CoreModule.TAB_KEY)) return;
        for (PowerType t : PowerRegistry.all()) {
            if (t.isPassive() && t.hidden) continue;
            event.accept(core(t, 0));
        }
    }
}
