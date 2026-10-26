package dev.awaken.living.quest;

import dev.awaken.Awaken;
import dev.awaken.core.PlayDays;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.progression.Progression;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The relics a chain ends with (SPEC §21.0.8, Phase C): one per chain, bound to the player who earned it and kept
 * through death. Carried, a relic adds its perk (read by the module that owns the perk); two are used (the Lumen Tonic,
 * the Herdcaller Horn). Pell's letter is an ordinary errand item.
 */
public final class Relics {
    private Relics() {}

    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    /** A relic's perk while carried: id and amount (read through {@link Progression#perk}). */
    public record Perk(String id, double amount) {}

    public static final DeferredItem<Relic> SHEPHERDS_LANTERN = relic("shepherds_lantern", new Perk("awaken:night_ward", 8));
    public static final DeferredItem<Relic> LUMEN_TONIC = ITEMS.registerItem("lumen_tonic", p -> new Tonic(p.stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<Relic> WRENS_HOOK = relic("wrens_hook", new Perk("awaken:rope_reach", 6));
    public static final DeferredItem<Relic> CARAVAN_WRIT = relic("caravan_writ", new Perk("awaken:shop_discount", 0.15));
    public static final DeferredItem<Relic> HERDCALLER_HORN = ITEMS.registerItem("herdcaller_horn", p -> new Horn(p.stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<Relic> PELLS_SPURS = relic("pells_spurs", new Perk("awaken:mount_sprint", 0.15), new Perk("awaken:whistle_range", 80));
    /** Kestrel's (The Last Delivery): tactical sprint recharges faster (the Ascent: off the rank's time, capped). */
    public static final DeferredItem<Relic> COURIERS_KNOT = relic("couriers_knot", new Perk("awaken:sprint_recharge", 0.25));
    /** Oren's (Oren's First Steel): a fresh blade tempered at a Forge starts a level up (hearth Upgrades reads it). */
    public static final DeferredItem<Relic> ORENS_MARK = relic("orens_mark", new Perk("awaken:temper_start", 1));
    public static final DeferredItem<Item> PELLS_LETTER = ITEMS.registerSimpleItem("pells_letter", p -> p.stacksTo(1));
    /** Bram's letters for Ilse (SPEC §27.6): handed over at the Registry, taken by Ilse at the camp. An errand item. */
    public static final DeferredItem<Item> ILSE_LETTERS = ITEMS.registerSimpleItem("ilse_letters", p -> p.stacksTo(1));

    private static DeferredItem<Relic> relic(String id, Perk... perks) {
        return ITEMS.registerItem(id, p -> new Relic(p.stacksTo(1).rarity(Rarity.RARE), List.of(perks)));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        Progression.addPerkSource(Relics::perk);
    }

    /** The perk source: the sum of a perk over relics in the main inventory and the offhand (no allocation). */
    static double perk(Player player, String perkId) {
        if (!perkId.startsWith("awaken:")) return 0;
        var inv = player.getInventory();
        double sum = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty() || !(st.getItem() instanceof Relic r)) continue;
            for (int k = 0; k < r.perks.size(); k++) if (r.perks.get(k).id.equals(perkId)) sum += r.perks.get(k).amount;
        }
        return sum;
    }

    /** A relic: kept through death, describes its perk under its name. */
    public static class Relic extends Item {
        final List<Perk> perks;

        Relic(Item.Properties p, List<Perk> perks) {
            super(p);
            this.perks = perks;
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
            out.accept(Component.translatable(getDescriptionId() + ".desc").withStyle(net.minecraft.ChatFormatting.GRAY));
            out.accept(Component.translatable("item.awaken.relic.bound").withStyle(net.minecraft.ChatFormatting.DARK_AQUA));
        }
    }

    /** Ilse's Lumen Tonic: a sip for night eyes, refilled every few minutes. */
    static final class Tonic extends Relic {
        static final int SIGHT = 20 * 120, REFILL = 20 * 300;

        Tonic(Item.Properties p) {
            super(p, List.of());
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack st = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(st)) return InteractionResult.FAIL;
            if (level instanceof ServerLevel sl) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, SIGHT, 0, true, false, true));
                player.getCooldowns().addCooldown(st, REFILL);
                sl.playSound(null, player.blockPosition(), SoundEvents.HONEY_DRINK.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
            }
            return InteractionResult.SUCCESS;
        }
    }

    /**
     * Gus's Herdcaller Horn, once a play-day: whatever is charging you within 24 blocks stops and loses interest, and
     * the herd animals within 32 run the way you face.
     */
    static final class Horn extends Relic {
        static final double CALM = 24, HERD = 32;

        Horn(Item.Properties p) {
            super(p, List.of());
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            long day = PlayDays.current(sl.getServer());
            String key = "awaken:living/horn_day";
            if (Progression.profile(sp).counter(key) == day + 1) {
                sp.sendOverlayMessage(Component.translatable("item.awaken.herdcaller_horn.spent"));
                return InteractionResult.FAIL;
            }
            Progression.setCounter(sp, key, (int) (day + 1));
            sl.playSound(null, sp.blockPosition(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.getFirst().value(), SoundSource.PLAYERS, 3.0F, 0.8F);
            Vec3 aim = sp.getLookAngle().multiply(1, 0, 1).normalize();
            for (Mob m : sl.getEntitiesOfClass(Mob.class, new AABB(sp.blockPosition()).inflate(HERD), Mob::isAlive)) {
                if (m.getTarget() == sp && m.distanceTo(sp) <= CALM) {
                    m.setTarget(null);
                    m.setLastHurtByMob(null);
                    m.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 20 * 6, 1));
                    continue;
                }
                CreatureTag.Kind kind = CreatureTag.of(m).kind();
                boolean herd = kind == CreatureTag.Kind.PASSIVE || kind == CreatureTag.Kind.NEUTRAL;
                if (herd && !(m instanceof Monster) && m instanceof PathfinderMob pm) {
                    Vec3 to = pm.position().add(aim.scale(24));
                    pm.getNavigation().moveTo(to.x, to.y, to.z, 1.8);
                }
            }
            return InteractionResult.SUCCESS;
        }
    }

    // ---- kept through death -------------------------------------------------------------------------------------------

    private static final Map<UUID, List<ItemStack>> HELD = new HashMap<>();

    /** A player's relics never drop: they wait for the respawn. */
    public static void onDrops(LivingDropsEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        List<ItemStack> kept = new ArrayList<>();
        e.getDrops().removeIf(ie -> {
            ItemStack st = ie.getItem();
            if (!(st.getItem() instanceof Relic)) return false;
            kept.add(st.copy());
            return true;
        });
        if (!kept.isEmpty()) HELD.computeIfAbsent(p.getUUID(), u -> new ArrayList<>()).addAll(kept);
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        List<ItemStack> kept = HELD.remove(p.getUUID());
        if (kept == null) return;
        for (ItemStack st : kept) p.getInventory().placeItemBackInInventory(st, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    // ---- the Shepherd's Lantern -------------------------------------------------------------------------------------

    /**
     * Twice a second: at night, hunters and monsters within the ward of a player carrying the lantern give up on them
     * and back off (gate creatures, wardens and named beasts don't care about a lantern).
     */
    public static void wardTick(net.minecraft.server.MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            double ward = Progression.perk(p, "awaken:night_ward");
            if (ward <= 0 || p.isSpectator() || !p.level().isDarkOutside()) continue;
            for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(ward), m -> m.isAlive() && warded(m))) {
                if (m.getTarget() == p) m.setTarget(null);
                if (m instanceof PathfinderMob pm && pm.distanceTo(p) < ward) {
                    Vec3 away = pm.position().subtract(p.position()).multiply(1, 0, 1).normalize().scale(ward + 4);
                    pm.getNavigation().moveTo(pm.getX() + away.x, pm.getY(), pm.getZ() + away.z, 1.2);
                }
            }
        }
    }

    private static boolean warded(Mob m) {
        CreatureTag.Kind kind = CreatureTag.of(m).kind();
        return kind == CreatureTag.Kind.HUNTER || kind == CreatureTag.Kind.MONSTER || (kind == CreatureTag.Kind.NONE && m instanceof Monster);
    }
}
