package dev.awaken.hearth;

import dev.awaken.Awaken;
import dev.awaken.core.api.CoreApi;
import dev.awaken.hearth.room.Room;
import dev.awaken.hearth.room.RoomType;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Anchor tiers I-V, Forge tiers I-III and gear tempering. All priced through {@link Costs}. */
public final class Upgrades {
    private Upgrades() {}

    public static final Identifier TEMPER_ID = Awaken.id("temper");
    public static final int MAX_TEMPER = 6;

    /** An upgrade: what it's called, the rank it belongs to (for the discount and the unlock) and its full price. */
    public record Def(String id, String name, Rank tier, List<Costs.Part> base) {}

    private static Costs.Part part(Item item, int n) {
        return new Costs.Part(item, n);
    }

    /** Price of raising the Anchor to {@code target} (2..5). */
    public static Def anchorTier(int target) {
        Item essence = CoreApi.ESSENCE_SHARD.get();
        return switch (target) {
            case 2 -> new Def("anchor_2", "Hearth Tier II", Rank.D, List.of(part(Items.STONE_BRICKS, 48), part(Items.IRON_INGOT, 16), part(Items.LANTERN, 4), part(essence, 4)));
            case 3 -> new Def("anchor_3", "Hearth Tier III", Rank.C, List.of(part(Items.DEEPSLATE_TILES, 48), part(Items.GOLD_INGOT, 12), part(Items.AMETHYST_SHARD, 12), part(essence, 8)));
            case 4 -> new Def("anchor_4", "Hearth Tier IV", Rank.B, List.of(part(Items.POLISHED_BLACKSTONE_BRICKS, 48), part(Items.DIAMOND, 6), part(Items.COPPER_INGOT, 32), part(essence, 14)));
            default -> new Def("anchor_5", "Hearth Tier V", Rank.A, List.of(part(Items.OBSIDIAN, 32), part(Items.DIAMOND, 12), part(Items.AMETHYST_BLOCK, 8), part(essence, 22), part(CoreApi.LORD_HEART.get(), 1)));
        };
    }

    public static Def forgeTier(int target) {
        Item essence = CoreApi.ESSENCE_SHARD.get();
        return target <= 2
                ? new Def("forge_2", "Forge Tier II", Rank.D, List.of(part(Items.IRON_BLOCK, 4), part(Items.COAL_BLOCK, 4), part(essence, 4)))
                : new Def("forge_3", "Forge Tier III", Rank.B, List.of(part(Items.DIAMOND, 3), part(Items.GOLD_BLOCK, 2), part(Items.MAGMA_BLOCK, 8), part(essence, 10)));
    }

    /** Price of tempering gear to {@code level} (1..6). Oren brings his own essence. */
    public static Def temper(int level, boolean byOren) {
        List<Costs.Part> parts = new ArrayList<>();
        parts.add(part(Items.IRON_INGOT, 3 * level));
        if (level >= 3) parts.add(part(Items.GOLD_INGOT, 2 * (level - 2)));
        if (level >= 4) parts.add(part(Items.DIAMOND, level - 3));
        if (!byOren) {
            parts.add(part(CoreApi.ESSENCE_SHARD.get(), level));
            parts.add(part(Items.COAL, 1)); // the Forge burns a coal a temper (Oren brings his own fire)
        }
        return new Def("temper_" + level, "Temper " + roman(level), Rank.of((level - 1) / 2), parts);
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; case 6 -> "VI";
            default -> String.valueOf(n);
        };
    }

    // ---- the Anchor -------------------------------------------------------------------------------------------------

    public static void offerAnchor(ServerPlayer sp, ServerLevel level, HearthRecord h) {
        Rank rank = Progression.rank(sp);
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(h.hearthfire() ? "Hearthfire" : "Hearth · Tier " + roman(h.tier)));
        w.row(Component.literal("Radius"), Component.literal(h.radius() + " blocks"));
        List<String> rooms = new ArrayList<>();
        for (Room r : h.rooms) for (RoomType t : r.types()) if (!rooms.contains(t.label)) rooms.add(t.label);
        w.row(Component.literal("Rooms"), Component.literal(rooms.isEmpty() ? "none yet" : String.join(" · ", rooms)));
        if (!h.arrived.isEmpty()) {
            List<String> town = new ArrayList<>();
            for (String id : h.arrived) town.add(dev.awaken.hearth.town.Townsfolk.shortName(id));
            w.row(Component.literal("Town"), Component.literal(String.join(", ", town)));
        }
        w.row(Component.literal("Rest heals"), Component.literal(HearthService.restSeconds(h.tier) + "s still, out of combat"));
        if (h.hearthfire()) {
            // Tier I isn't bought: a Lord's Heart on the fire raises the Anchor in place (Embers.raise)
            w.row(Component.literal("Next"), Component.literal("Tier I · a Lord's Heart on the fire"));
            Signal.send(sp, w.ttl(20 * 10).build());
            return;
        }
        if (h.tier >= HearthRecord.MAX_TIER) {
            Signal.send(sp, w.footer(Component.literal("Your Hearth is complete.")).ttl(20 * 10).build());
            return;
        }
        Def next = anchorTier(h.tier + 1);
        if (rank.ordinal() < next.tier().ordinal()) {
            w.row(Component.literal("Next"), Component.literal("Tier " + roman(h.tier + 1) + " at " + next.tier().label + "-rank"));
            Signal.send(sp, w.ttl(20 * 10).build());
            return;
        }
        List<Costs.Part> price = Costs.price(next.base(), rank, next.tier());
        w.row(Component.literal("Next"), Component.literal("Tier " + roman(h.tier + 1) + " · radius " + HearthRecord.radiusForTier(h.tier + 1)));
        for (Costs.Part p : price) w.row(p.name(), Costs.have(sp, p));
        int off = Costs.discountPercent(rank, next.tier());
        if (off > 0) w.row(Component.literal("Rank discount"), Component.literal("−" + off + "%"));
        if (Costs.canAfford(sp, price)) {
            w.choice(Offers.offer(sp, "anchor", 20 * 30, p -> upgradeAnchor(p, h.owner)), "Build Tier " + roman(h.tier + 1), "Not now");
        } else {
            w.footer(Component.literal("Gather the materials and come back"));
        }
        Signal.send(sp, w.ttl(20 * 12).build());
    }

    /** Builds the next Anchor tier of {@code owner}'s Hearth; its owner or any member may (they pay). */
    public static void upgradeAnchor(ServerPlayer sp, java.util.UUID owner) {
        Optional<HearthRecord> ho = HearthData.get(sp.level().getServer()).of(owner).filter(r -> r.belongs(sp.getUUID()));
        if (ho.isEmpty() || ho.get().hearthfire() || ho.get().tier >= HearthRecord.MAX_TIER) return;
        HearthRecord h = ho.get();
        Def next = anchorTier(h.tier + 1);
        Rank rank = Progression.rank(sp);
        if (rank.ordinal() < next.tier().ordinal()) return;
        List<Costs.Part> price = Costs.price(next.base(), rank, next.tier());
        if (!Costs.canAfford(sp, price)) {
            sp.sendOverlayMessage(Component.literal("Not enough materials any more."));
            return;
        }
        Costs.pay(sp, price);
        h.tier++;
        h.scanAt = sp.level().getGameTime() + 1;
        HearthService.dirty(sp.level().getServer());
        ServerLevel level = HearthService.levelOf(sp.level().getServer(), h);
        HearthService.showTier(level, h);
        level.playSound(null, h.pos, HearthContent.SND_ANCHOR.get(), SoundSource.BLOCKS, 1f, 1.2f);
        level.sendParticles(HearthContent.HEARTH_EMBER.get(), h.pos.getX() + 0.5, h.pos.getY() + 1.5, h.pos.getZ() + 0.5, 60, 1.2, 1.2, 1.2, 0.03);
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Hearth Tier " + roman(h.tier)))
                .row(Component.literal("Radius"), Component.literal(h.radius() + " blocks"))
                .row(Component.literal("Rest heals"), Component.literal("in " + HearthService.restSeconds(h.tier) + "s"))
                .body(Component.literal("The Anchor burns brighter. Home got bigger.")).priority(4).build());
        Progression.markNewThing(sp, "hearth_tier");
        HearthViews.refresh(sp);
    }

    // ---- the Forge --------------------------------------------------------------------------------------------------

    public static void offerForgeUpgrade(ServerPlayer sp, ServerLevel level, BlockPos forgePos) {
        Optional<HearthRecord> ho = HearthData.get(level.getServer()).containing(forgePos, level.dimension().identifier().toString());
        if (ho.isEmpty() || !ho.get().belongs(sp.getUUID())) {
            Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Forge"))
                    .body(Component.literal("A forge works best at home. Inside your Hearth, with an anvil and an armour stand in the same room, it can temper your gear."))
                    .ttl(20 * 8).build());
            return;
        }
        HearthRecord h = ho.get();
        Rank rank = Progression.rank(sp);
        boolean armory = h.roomAt(forgePos.relative(net.minecraft.core.Direction.UP), RoomType.ARMORY).isPresent() || hasArmoryAround(h, forgePos);
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Forge · Tier " + roman(h.forgeTier)));
        w.row(Component.literal("Armory"), Component.literal(armory ? "yes" : "no · needs an anvil and an armour stand in this room"));
        w.row(Component.literal("Temper limit"), Component.literal(roman(temperCap(h, armory))));
        w.body(Component.literal("Hold a weapon or armour piece and use the Forge to temper it."));
        if (h.forgeTier >= HearthRecord.MAX_FORGE_TIER) {
            Signal.send(sp, w.ttl(20 * 10).build());
            return;
        }
        Def next = forgeTier(h.forgeTier + 1);
        if (rank.ordinal() < next.tier().ordinal()) {
            w.row(Component.literal("Next"), Component.literal("Tier " + roman(h.forgeTier + 1) + " at " + next.tier().label + "-rank"));
            Signal.send(sp, w.ttl(20 * 10).build());
            return;
        }
        List<Costs.Part> price = Costs.price(next.base(), rank, next.tier());
        w.row(Component.literal("Next"), Component.literal(next.name() + " · temper to " + roman((h.forgeTier + 1) * 2)));
        for (Costs.Part p : price) w.row(p.name(), Costs.have(sp, p));
        int off = Costs.discountPercent(rank, next.tier());
        if (off > 0) w.row(Component.literal("Rank discount"), Component.literal("−" + off + "%"));
        if (Costs.canAfford(sp, price)) {
            w.choice(Offers.offer(sp, "forge", 20 * 30, p -> upgradeForge(p, level, forgePos)), "Upgrade the Forge", "Not now");
        }
        Signal.send(sp, w.ttl(20 * 12).build());
    }

    static boolean hasArmoryAround(HearthRecord h, BlockPos forgePos) {
        for (var d : net.minecraft.core.Direction.values()) if (h.roomAt(forgePos.relative(d), RoomType.ARMORY).isPresent()) return true;
        return false;
    }

    static void upgradeForge(ServerPlayer sp, ServerLevel level, BlockPos forgePos) {
        Optional<HearthRecord> ho = HearthService.sharedAt(sp, forgePos);
        if (ho.isEmpty() || ho.get().forgeTier >= HearthRecord.MAX_FORGE_TIER) return;
        HearthRecord h = ho.get();
        Def next = forgeTier(h.forgeTier + 1);
        Rank rank = Progression.rank(sp);
        List<Costs.Part> price = Costs.price(next.base(), rank, next.tier());
        if (rank.ordinal() < next.tier().ordinal() || !Costs.canAfford(sp, price)) return;
        Costs.pay(sp, price);
        h.forgeTier++;
        HearthService.dirty(level.getServer());
        level.playSound(null, forgePos, HearthContent.SND_FORGE.get(), SoundSource.BLOCKS, 1f, 0.8f);
        level.sendParticles(ParticleTypes.LAVA, forgePos.getX() + 0.5, forgePos.getY() + 1, forgePos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0);
        Signal.send(sp, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("Forge Tier " + roman(h.forgeTier)))
                .body(Component.literal("Hotter fire. Your gear can take more now.")).build());
        HearthViews.refresh(sp);
    }

    // ---- tempering --------------------------------------------------------------------------------------------------

    public static boolean isTemperable(ItemStack stack) {
        if (stack.isEmpty() || stack.is(HearthContent.TROPHY_ITEM.get())) return false;
        return armourSlot(stack) != null || isWeapon(stack);
    }

    private static EquipmentSlot armourSlot(ItemStack stack) {
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        return eq != null && eq.slot().isArmor() && eq.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? eq.slot() : null;
    }

    private static boolean isWeapon(ItemStack stack) {
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (e.attribute().value() == Attributes.ATTACK_DAMAGE.value() && e.modifier().amount() > 0) return true;
        }
        return false;
    }

    public static int temperOf(ItemStack stack) {
        return stack.getOrDefault(HearthContent.TEMPER.get(), 0);
    }

    /** How far gear can be tempered here: one level anywhere, two per Forge tier in an Armory. */
    public static int temperCap(HearthRecord h, boolean armory) {
        return armory ? Math.min(MAX_TEMPER, h.forgeTier * 2) : 1;
    }

    /** Offers to temper the item in {@code hand}. source = "forge" (at a Forge block) or "oren" (the Blacksmith). */
    public static void offerTemper(ServerPlayer sp, ServerLevel level, BlockPos at, InteractionHand hand, String source) {
        ItemStack stack = sp.getItemInHand(hand);
        boolean oren = "oren".equals(source);
        Optional<HearthRecord> ho = oren ? HearthService.sharedAt(sp, at).or(() -> HearthService.homeOf(sp)) : HearthService.homeOf(sp);
        boolean armory = ho.isPresent() && (oren ? ho.get().hasRoom(RoomType.ARMORY) : hasArmoryAround(ho.get(), at)
                || ho.get().roomAt(at, RoomType.ARMORY).isPresent());
        int cap = ho.map(h -> temperCap(h, armory)).orElse(1);
        int current = temperOf(stack);
        var w = SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal(oren ? "Oren · Tempering" : "Temper"));
        w.row(Component.literal("Item"), stack.getHoverName());
        w.row(Component.literal("Temper"), Component.literal(current == 0 ? "none" : roman(current)));
        if (current >= cap) {
            w.body(Component.literal(current >= MAX_TEMPER ? "It can't take any more."
                    : armory ? "Upgrade the Forge to go further." : "Build an Armory (anvil, Forge and armour stand in one room) to go further."));
            Signal.send(sp, w.ttl(20 * 8).build());
            return;
        }
        // Oren's Mark (a Living Wilds relic, perk awaken:temper_start): a fresh blade starts that many levels up
        int next = current + 1;
        if (current == 0 && armourSlot(stack) == null) next = Math.min(cap, 1 + (int) Progression.perk(sp, "awaken:temper_start"));
        final int target = next;
        Def def = temper(target, oren);
        Rank rank = Progression.rank(sp);
        List<Costs.Part> price = Costs.price(def.base(), rank, def.tier());
        w.row(Component.literal("Next"), Component.literal(roman(target) + " · " + (armourSlot(stack) != null ? "+0.5 armour, +0.25 toughness" : "+1 damage") + " per level"));
        for (Costs.Part p : price) w.row(p.name(), Costs.have(sp, p));
        if (oren) w.body(Component.literal("I'll bring the essence. You bring the iron."));
        Item item = stack.getItem();
        if (Costs.canAfford(sp, price)) {
            w.choice(Offers.offer(sp, "temper", 20 * 30, p -> {
                ItemStack now = p.getItemInHand(hand);
                if (!now.is(item) || temperOf(now) != current) return;
                List<Costs.Part> again = Costs.price(def.base(), Progression.rank(p), def.tier());
                if (!Costs.canAfford(p, again)) return;
                Costs.pay(p, again);
                applyTemper(now, target);
                p.level().playSound(null, p.blockPosition(), HearthContent.SND_FORGE.get(), SoundSource.PLAYERS, 1f, 1.1f);
                ((ServerLevel) p.level()).sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 16, 0.4, 0.4, 0.4, 0.2);
                Signal.toast(p, Component.literal(now.getHoverName().getString() + " tempered to " + roman(target)));
                Progression.count(p, "awaken:gear_tempered");
                dev.awaken.living.api.Quests.advance(p, Identifier.parse("awaken:use/temper"));
            }), "Temper it to " + roman(target), "Not now");
        }
        Signal.send(sp, w.ttl(20 * 12).build());
    }

    /** Sets the temper level and its attribute bonus on a stack. */
    public static void applyTemper(ItemStack stack, int level) {
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        EquipmentSlot armour = armourSlot(stack);
        if (armour != null) {
            EquipmentSlotGroup g = EquipmentSlotGroup.bySlot(armour);
            mods = mods.withModifierAdded(Attributes.ARMOR, new AttributeModifier(TEMPER_ID, 0.5 * level, AttributeModifier.Operation.ADD_VALUE), g);
            mods = mods.withModifierAdded(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(TEMPER_ID, 0.25 * level, AttributeModifier.Operation.ADD_VALUE), g);
        } else {
            mods = mods.withModifierAdded(Attributes.ATTACK_DAMAGE, new AttributeModifier(TEMPER_ID, 1.0 * level, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, mods);
        stack.set(HearthContent.TEMPER.get(), level);
    }
}
