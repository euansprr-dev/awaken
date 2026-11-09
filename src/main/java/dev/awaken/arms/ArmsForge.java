package dev.awaken.arms;

import dev.awaken.Awaken;
import dev.awaken.core.Forces;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.powers.Fx;
import dev.awaken.powers.fx.FxKind;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Arms at the Forge (SPEC §25 "The Forge"): use Hearth's Forge with an empty hand for the Armoury and a Blueprint drawn
 * in lapis ink. Attunement: a weapon takes a Force named by a catalyst (the Lands' crystals and the deep's, SPEC §28.3:
 * Sunshard Heat, Frostspar Frost, a Fault Seam Gravity, a Copper Ingot Charge, a Deepglass Shard Spirit), for essence
 * and a Core Shard, set at a War Anvil since SPEC §46.8 (the Forge says so, and that weapons are tempered there). An
 * attuned weapon's riders take its Force, and it primes both ways: a landed hit makes the next power cost less, and a
 * power of its Force makes the next blow land harder with that Force's touch.
 */
public final class ArmsForge {
    private ArmsForge() {}

    static final Identifier FORGE = Awaken.id("forge");
    static final String PREFIX = "arms";

    /**
     * The catalysts, by Force (every region has a stone worth going for: PLAN 7.2): the Glass Steppe's Sunshard, the Frost
     * Spires' Frostspar, the Shattered Highlands' Fault Seam, copper, and the deep's Deepglass. By id: the crystals belong
     * to the terrain module and Deepglass to the deep's, and arms names neither.
     */
    static final Map<Forces.Force, Identifier> CATALYST_ID = new EnumMap<>(Map.of(
            Forces.Force.HEAT, Awaken.id("sunshard"), Forces.Force.CHARGE, Identifier.withDefaultNamespace("copper_ingot"),
            Forces.Force.GRAVITY, Awaken.id("fault_seam"), Forces.Force.SPIRIT, Awaken.id("deepglass_shard"), Forces.Force.FROST, Awaken.id("frostspar")));
    public static final int ATTUNE_ESSENCE = 6, ATTUNE_CORE_SHARDS = 1;
    /** A Blueprint is drawn in lapis ink (PLAN 7.2): paper, a lapis and an essence shard, no iron. */
    public static final int BLUEPRINT_PAPER = 4, BLUEPRINT_LAPIS = 1, BLUEPRINT_ESSENCE = 1;
    /** At a Hearth with a Study the paper is halved (SPEC §28.1). */
    public static final int BLUEPRINT_PAPER_STUDY = 2;

    /** The catalyst item of a Force (air if its module isn't loaded). */
    public static Item catalystItem(Forces.Force f) {
        return BuiltInRegistries.ITEM.getValue(CATALYST_ID.get(f));
    }
    /** Both ways: an attuned hit primes the next power for so long, at this share of its energy. */
    public static final int PRIME_TICKS = 60;
    public static final float POWER_PRIME = 0.85F;
    /** A power of the weapon's Force primes the next blow: times this, and the Force's touch. */
    public static final float BLADE_PRIME = 1.25F;
    /** Each Force's touch on a primed blow; {@link #touch} says them from these. */
    static final int HEAT_TICKS = 60, FROST_TICKS = 40, FROST_AMPLIFIER = 1, CHARGE_POSTURE = 6, GRAVITY_MAX_HEALTH = 150;
    static final double GRAVITY_LIFT = 0.45;
    static final float SPIRIT_HEAL = 1.5F;

    // ---- attunement in the fight --------------------------------------------------------------------------------------

    static Forces.@Nullable Force attunement(ServerPlayer p) {
        return Marks.of(p.getMainHandItem()).attunement().orElse(null);
    }

    /** The share of its energy the next power costs (ArmsWeaves.costScale). */
    static float costScale(ArmsState s, long now) {
        return now <= s.powerPrimedUntil ? POWER_PRIME : 1F;
    }

    /** A power landed: it spends the hit's prime; one of the weapon's Force primes the next blow. */
    static void cast(ServerPlayer p, ArmsState s, @Nullable String force, long now) {
        s.powerPrimedUntil = Long.MIN_VALUE;
        Forces.Force f = attunement(p);
        if (f != null && f.name().equals(force)) s.bladePrimedUntil = now + PRIME_TICKS;
    }

    /** Whether this blow is primed by a power (and spends it). */
    static boolean bladePrimed(ServerPlayer p, ArmsState s, long now) {
        if (now > s.bladePrimedUntil) return false;
        s.bladePrimedUntil = Long.MIN_VALUE;
        return attunement(p) != null;
    }

    /** A blow landed: an attuned weapon primes the next power; a primed blow leaves its Force's touch. */
    static void landed(ServerPlayer p, ArmsState s, LivingEntity t, long now, boolean primed) {
        Forces.Force f = attunement(p);
        if (f == null) return;
        s.powerPrimedUntil = now + PRIME_TICKS;
        if (!primed) return;
        switch (f) {
            case HEAT -> t.igniteForTicks(HEAT_TICKS);
            case FROST -> t.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FROST_TICKS, FROST_AMPLIFIER), p);
            case CHARGE -> ArmsServer.addPosture(t, CHARGE_POSTURE, p);
            case GRAVITY -> {
                if (t.getMaxHealth() < GRAVITY_MAX_HEALTH) {
                    t.push(0, GRAVITY_LIFT, 0);
                    t.needsSync = true;
                }
            }
            case SPIRIT -> p.heal(SPIRIT_HEAL);
        }
        Fx.at(p.level(), FxKind.IMPACT, Marks.look(f), t.position().add(0, t.getBbHeight() * 0.6, 0)).scale(1.4).send();
    }

    // ---- at the Forge -------------------------------------------------------------------------------------------------

    static boolean isForge(ServerLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).equals(FORGE);
    }

    /** Sneak and use the Forge: Arms' window (attunement, or a Blueprint); a plain use stays the Hearth's. */
    static void onUseBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(p.level() instanceof ServerLevel level) || !isForge(level, e.getPos())) return;
        if (!p.isShiftKeyDown()) {
            // a plain use with an empty hand opens the Armoury; one of our weapons is tempered at a War Anvil now (SPEC
            // §46.8); with anything else in it, the Hearth's tempering stays as it was
            if (ArmsItems.of(p.getMainHandItem().getItem()) != null) {
                Signal.toast(p, Component.literal("Weapons are tempered at a War Anvil."));
                e.setCanceled(true);
                e.setCancellationResult(InteractionResult.SUCCESS);
                return;
            }
            if (!p.getMainHandItem().isEmpty()) return;
            dev.awaken.progression.Progression.count(p, "awaken:armoury_forge"); // a trail step (docs/trails/PLAN.md §4.1)
            ArmsArmoury.open(p, ArmsArmoury.FORGE);
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        ItemStack held = p.getMainHandItem();
        if (held.isEmpty()) return; // sneak with an empty hand is the Hearth's window; the Armoury is a plain use (below)
        if (ArmsItems.of(held.getItem()) == null) return;
        // weapons are attuned at a War Anvil now (SPEC §46.8): its window says what each Force does
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Attuning"))
                .body(Component.literal("Weapons are attuned at a War Anvil. It shows you what each Force does.")).ttl(20 * 8).build());
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Attunes the weapon in hand (re-checked: an old window can't pay twice or attune the wrong thing). */
    static boolean attune(ServerPlayer p, Forces.Force f) {
        ItemStack weapon = p.getMainHandItem();
        if (ArmsItems.of(weapon.getItem()) == null || catalyst(p.getOffhandItem()) != f) return false;
        List<Cost> price = attunePrice(f);
        if (!affords(p, price)) return false;
        pay(p, price);
        attune(weapon, f);
        attuned(p, weapon, f);
        return true;
    }

    /** The weapon takes the Force (re-attuning replaces it; nothing paid here). */
    public static void attune(ItemStack weapon, Forces.Force f) {
        weapon.set(ArmsContent.INSTANCE.get(), Marks.of(weapon).attuned(f));
    }

    /** Said and counted wherever a weapon is attuned (here, or at a War Anvil): the sound, the ring and what it now does. */
    public static void attuned(ServerPlayer p, ItemStack weapon, Forces.Force f) {
        dev.awaken.progression.Progression.count(p, "awaken:weapons_attuned");
        ServerLevel level = p.level();
        level.playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.7F, 1.3F);
        level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F);
        Fx.at(level, FxKind.RING, Marks.look(f), p.position().add(0, 0.1, 0)).caster(p).scale(2.5).ticks(12).send();
        ArmsNotices.reward(p, "weapon/attuned/" + java.util.UUID.randomUUID(), "Weapon attuned", weapon,
                "Its Marks now carry " + forceName(f) + ", and it works together with your " + forceName(f) + " powers.",
                List.of(hitWords(), "Cast a " + forceName(f) + " power, and your next blow within " + primeTime() + " hits "
                        + BLADE_PRIME + " times as hard and " + touch(f) + "."));
    }

    /** What attuning does on a hit, as every window and the weapon's own tooltip say it. */
    public static String hitWords() {
        return "Land a hit, and your next power within " + primeTime() + " costs " + Math.round((1 - POWER_PRIME) * 100) + "% less energy.";
    }

    /** What a power of the weapon's Force does for its next blow; a list of {@link #touch}es follows it. */
    public static String powerWords() {
        return "Cast a power of its Force, and your next blow within " + primeTime() + " hits " + BLADE_PRIME + " times as hard and also:";
    }

    public static String primeTime() {
        return ArmsNotices.seconds(PRIME_TICKS / 20.0);
    }

    /** What this Force's touch does to whatever a primed blow hits, from the numbers {@link #landed} uses. */
    public static String touch(Forces.Force f) {
        return switch (f) {
            case HEAT -> "sets them on fire for " + ArmsNotices.seconds(HEAT_TICKS / 20.0);
            case FROST -> "slows them by " + (FROST_AMPLIFIER + 1) * 15 + "% for " + ArmsNotices.seconds(FROST_TICKS / 20.0);
            case CHARGE -> "does " + CHARGE_POSTURE + " extra posture damage";
            case GRAVITY -> "throws them into the air, unless they're huge";
            case SPIRIT -> "heals you for " + ArmsNotices.number(SPIRIT_HEAL) + " health";
        };
    }

    static boolean drawBlueprint(ServerPlayer p, String style) {
        var item = ArmsItems.BLUEPRINTS.get(style);
        List<Cost> price = blueprintPrice(p);
        if (item == null || !affords(p, price)) return false;
        pay(p, price);
        ArmsIssue.remember(p, style);
        dev.awaken.progression.Progression.count(p, "awaken:blueprint_copies");
        ItemStack bp = new ItemStack(item.get());
        if (!p.getInventory().add(bp)) p.drop(bp, false, net.minecraft.util.Prediction.SERVER_ONLY);
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.4F, 1.6F);
        return true;
    }

    static boolean owns(ServerPlayer p, String style) {
        var item = ArmsItems.BLUEPRINTS.get(style);
        return item != null && count(p, item.get()) > 0;
    }

    public static Forces.@Nullable Force catalyst(ItemStack stack) {
        if (stack.isEmpty()) return null;
        for (Forces.Force f : CATALYST_ID.keySet()) if (stack.is(catalystItem(f))) return f;
        return null;
    }

    public static String forceName(Forces.Force f) {
        return f.name().charAt(0) + f.name().substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    // ---- prices -------------------------------------------------------------------------------------------------------

    public record Cost(Item item, int n, Component label) {}

    public static List<Cost> attunePrice(Forces.Force f) {
        List<Cost> out = new ArrayList<>();
        out.add(cost(dev.awaken.core.api.CoreApi.ESSENCE_SHARD.get(), ATTUNE_ESSENCE));
        Item shard = BuiltInRegistries.ITEM.getValue(Awaken.id("core_shard"));
        if (shard != Items.AIR) out.add(cost(shard, ATTUNE_CORE_SHARDS));
        out.add(cost(catalystItem(f), 1));
        return out;
    }

    static List<Cost> blueprintPrice() {
        return blueprintPrice(false);
    }

    /** What this player pays at the Forge: the Study at their Hearth halves the paper. */
    static List<Cost> blueprintPrice(ServerPlayer p) {
        return blueprintPrice(HearthHooks.study(p));
    }

    private static List<Cost> blueprintPrice(boolean study) {
        return List.of(cost(Items.PAPER, study ? BLUEPRINT_PAPER_STUDY : BLUEPRINT_PAPER), cost(Items.LAPIS_LAZULI, BLUEPRINT_LAPIS),
                cost(dev.awaken.core.api.CoreApi.ESSENCE_SHARD.get(), BLUEPRINT_ESSENCE));
    }

    static Cost cost(Item item, int n) {
        return new Cost(item, n, item.getName(new ItemStack(item)));
    }

    /** Counted in the main inventory and the off hand (the catalyst is held there). */
    public static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (s.is(item)) n += s.getCount();
        if (p.getOffhandItem().is(item)) n += p.getOffhandItem().getCount();
        return n;
    }

    public static boolean affords(ServerPlayer p, List<Cost> price) {
        if (p.hasInfiniteMaterials()) return true;
        for (Cost c : price) if (count(p, c.item()) < c.n()) return false;
        return true;
    }

    static void pay(ServerPlayer p, List<Cost> price) {
        if (p.hasInfiniteMaterials()) return;
        for (Cost c : price) {
            int left = c.n();
            ItemStack off = p.getOffhandItem();
            if (off.is(c.item())) {
                int take = Math.min(left, off.getCount());
                off.shrink(take);
                left -= take;
            }
            for (ItemStack s : p.getInventory().getNonEquipmentItems()) {
                if (left <= 0) break;
                if (!s.is(c.item())) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
            }
        }
    }
}
