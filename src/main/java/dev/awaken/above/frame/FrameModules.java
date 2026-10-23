package dev.awaken.above.frame;

import dev.awaken.Awaken;
import dev.awaken.above.AboveSounds;
import dev.awaken.above.power.EssenceCellItem;
import dev.awaken.signal.Signal;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs socketed Frame modules for players wearing Frame pieces. Ticks every 10 ticks per player, allocation-free in the
 * common path; essence comes from the cells the player carries.
 */
public final class FrameModules {
    private FrameModules() {}

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final Identifier STEP = Awaken.id("frame_step_assist");
    private static final Identifier SHIELD = Awaken.id("frame_shield");
    private static final Identifier SAFE_FALL = Awaken.id("frame_jet_safe_fall");
    private static final int SHIELD_EVERY = 400;
    private static final float SHIELD_HP = 8.0f;

    private static final Map<UUID, Long> LAST_SHIELD = new HashMap<>();
    private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();
    private static final Map<UUID, Integer> LAST_MASK = new HashMap<>();

    public static void init() {
        NeoForge.EVENT_BUS.addListener(PlayerTickEvent.Post.class, FrameModules::onTick);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            UUID id = e.getEntity().getUUID();
            LAST_SHIELD.remove(id);
            LAST_NOTICE.remove(id);
            LAST_MASK.remove(id);
        });
    }

    /** Bitmask of {@link FrameModule#bit()} for every module in worn Frame pieces. Works on both sides. */
    public static int mask(Player player) {
        int mask = 0;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack s = player.getItemBySlot(slot);
            if (!(s.getItem() instanceof FrameItem)) continue;
            List<String> mods = FrameItem.modules(s);
            for (int i = 0; i < mods.size(); i++) {
                FrameModule m = FrameModule.byId(mods.get(i));
                if (m != null) mask |= m.bit();
            }
        }
        return mask;
    }

    public static boolean has(int mask, FrameModule m) {
        return (mask & m.bit()) != 0;
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 10 != 0 || sp.isSpectator()) return;
        if (!dev.awaken.core.AwakenConfig.ABOVE.get()) return;
        int mask = mask(sp);
        Integer last = LAST_MASK.get(sp.getUUID());
        if (last == null || last != mask) {
            applyAttributes(sp, mask);
            LAST_MASK.put(sp.getUUID(), mask);
        }
        if (mask == 0) return;
        long now = sp.level().getGameTime();
        if (has(mask, FrameModule.NIGHT_VISION)) {
            MobEffectInstance nv = sp.getEffect(MobEffects.NIGHT_VISION);
            if (nv == null || nv.getDuration() < 220) {
                if (draw(sp, 2)) sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
            }
        }
        if (has(mask, FrameModule.STEALTH_FIELD) && sp.isShiftKeyDown() && sp.getDeltaMovement().horizontalDistanceSqr() < 0.0004) {
            if (draw(sp, 1)) sp.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, true, false, true));
        }
        if (has(mask, FrameModule.MAGNET)) magnet(sp);
        if (has(mask, FrameModule.SHIELD_EMITTER)) {
            long lastShield = LAST_SHIELD.getOrDefault(sp.getUUID(), 0L);
            if (now - lastShield >= SHIELD_EVERY && sp.getAbsorptionAmount() < SHIELD_HP - 0.5f) {
                if (draw(sp, 40)) {
                    sp.setAbsorptionAmount(SHIELD_HP);
                    LAST_SHIELD.put(sp.getUUID(), now);
                    sp.level().playSound(null, sp.blockPosition(), AboveSounds.SCAN.get(), SoundSource.PLAYERS, 0.4f, 1.6f);
                }
            }
        }
    }

    private static void magnet(ServerPlayer sp) {
        AABB box = sp.getBoundingBox().inflate(7.0);
        List<ItemEntity> items = sp.level().getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay());
        if (items.isEmpty() || !draw(sp, 1)) return;
        Vec3 target = sp.position().add(0, 0.6, 0);
        for (ItemEntity e : items) {
            Vec3 d = target.subtract(e.position());
            double len = d.length();
            if (len < 0.8) continue;
            e.setDeltaMovement(d.scale(Math.min(0.6, 0.35 + 1.0 / len) / len));
        }
    }

    private static void applyAttributes(ServerPlayer sp, int mask) {
        modify(sp, Attributes.STEP_HEIGHT, STEP, has(mask, FrameModule.STEP_ASSIST) ? 0.6 : 0);
        modify(sp, Attributes.MAX_ABSORPTION, SHIELD, has(mask, FrameModule.SHIELD_EMITTER) ? SHIELD_HP : 0);
        modify(sp, Attributes.SAFE_FALL_DISTANCE, SAFE_FALL, has(mask, FrameModule.JET_BOOTS) ? 6.0 : 0);
        if (!has(mask, FrameModule.SHIELD_EMITTER) && sp.getAbsorptionAmount() > sp.getMaxAbsorption()) {
            sp.setAbsorptionAmount(sp.getMaxAbsorption());
        }
    }

    private static void modify(ServerPlayer sp, Holder<Attribute> attr, Identifier id, double amount) {
        AttributeInstance inst = sp.getAttribute(attr);
        if (inst == null) return;
        if (amount == 0) inst.removeModifier(id);
        else inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static boolean draw(ServerPlayer sp, int amount) {
        if (sp.isCreative()) return true;
        if (EssenceCellItem.drawCarried(sp, amount)) return true;
        long now = sp.level().getGameTime();
        if (now - LAST_NOTICE.getOrDefault(sp.getUUID(), -10_000L) > 1200) {
            LAST_NOTICE.put(sp.getUUID(), now);
            Signal.toast(sp, Component.literal("Frame: no essence. Charge your cells."));
            sp.level().playSound(null, sp.blockPosition(), AboveSounds.MODULE_OFF.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
        }
        return false;
    }

    /** Client reported jet thrust. Pays for it and forgives the fall. */
    public static void onJet(ServerPlayer sp, int ticks) {
        int t = Math.max(1, Math.min(8, ticks));
        if (!has(mask(sp), FrameModule.JET_BOOTS)) return;
        if (draw(sp, t)) {
            sp.resetFallDistance();
            if (sp.tickCount % 4 == 0) sp.level().playSound(null, sp.blockPosition(), AboveSounds.JET.get(), SoundSource.PLAYERS, 0.35f, 1.4f);
        }
    }
}
