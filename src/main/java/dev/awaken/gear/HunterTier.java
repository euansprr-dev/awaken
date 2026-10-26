package dev.awaken.gear;

import dev.awaken.Awaken;
import dev.awaken.progression.Rank;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;

import java.util.EnumMap;
import java.util.Map;

/**
 * Hunter gear tiers (plan v2 §4.5), one per rank. Each sits on a vanilla equivalent (E stone/copper, D iron, C diamond,
 * B netherite) and adds a twist through its set bonus ({@link SetBonus}); A (Lordscale) and S (Sovereign Weave) go past
 * netherite, the late-game sets. From D up every piece needs its tier's metal ({@link #metalTag}, SPEC §28.3): Gatewrought
 * matches diamond armour (20, toughness 2.0, its durability) so a diamond spent on it is never wasted, and B and A sit
 * a point above to keep the ladder climbing.
 * Numbers are the source for the stats test and the doc; change them together.
 */
public enum HunterTier {
    //     id             set name       rank    armour boots,legs,chest,helm  dur tough  kb     ench  sound
    E("wildhide", "Wildhide", Rank.E, Rarity.COMMON, new Armour(1, 3, 4, 2, 13, 0.0f, 0.0f, 15, SoundEvents.ARMOR_EQUIP_LEATHER),
            //        dmg  speed  dur        melee power dur          speed  melee dur  mines below
            new Blade(4.0f, -2.2f, 220), new Focus(2.0f, 0.06, 180), new Mattock(4.5f, 2.0f, 240, BlockTags.INCORRECT_FOR_STONE_TOOL)),
    D("beastbound", "Beastbound", Rank.D, Rarity.UNCOMMON, new Armour(2, 5, 6, 2, 18, 0.5f, 0.0f, 12, SoundEvents.ARMOR_EQUIP_TURTLE),
            new Blade(5.0f, -2.3f, 500), new Focus(3.0f, 0.09, 400), new Mattock(6.0f, 3.0f, 520, BlockTags.INCORRECT_FOR_IRON_TOOL)),
    C("gatewrought", "Gatewrought", Rank.C, Rarity.RARE, new Armour(3, 6, 8, 3, 33, 2.0f, 0.0f, 12, SoundEvents.ARMOR_EQUIP_IRON),
            new Blade(6.0f, -2.3f, 1400), new Focus(4.0f, 0.12, 1100), new Mattock(7.5f, 4.0f, 1450, BlockTags.INCORRECT_FOR_DIAMOND_TOOL)),
    B("veilwoven", "Veilwoven", Rank.B, Rarity.EPIC, new Armour(3, 7, 8, 3, 35, 2.5f, 0.05f, 15, SoundEvents.ARMOR_EQUIP_DIAMOND),
            new Blade(7.0f, -2.3f, 1900), new Focus(5.0f, 0.15, 1600), new Mattock(8.5f, 5.0f, 1950, BlockTags.INCORRECT_FOR_NETHERITE_TOOL)),
    // past netherite: the Lords' own scales, then the Hollow Court's weave
    A("lordscale", "Lordscale", Rank.A, Rarity.EPIC, new Armour(3, 7, 9, 3, 40, 3.0f, 0.1f, 15, SoundEvents.ARMOR_EQUIP_NETHERITE),
            new Blade(8.0f, -2.3f, 2300), new Focus(6.0f, 0.18, 2000), new Mattock(9.5f, 6.0f, 2350, BlockTags.INCORRECT_FOR_NETHERITE_TOOL)),
    S("sovereign", "Sovereign Weave", Rank.S, Rarity.EPIC, new Armour(4, 7, 9, 4, 48, 3.5f, 0.15f, 18, SoundEvents.ARMOR_EQUIP_NETHERITE),
            new Blade(9.0f, -2.2f, 2800), new Focus(7.0f, 0.22, 2500), new Mattock(11.0f, 7.0f, 2850, BlockTags.INCORRECT_FOR_NETHERITE_TOOL));

    /** Armour points per piece, the durability multiplier (vanilla style), toughness and knockback resistance per piece. */
    public record Armour(int boots, int legs, int chest, int helm, int durability, float toughness, float knockback, int enchantability,
                         Holder<SoundEvent> sound) {
        public int defense(ArmorType type) {
            return switch (type) {
                case BOOTS -> boots;
                case LEGGINGS -> legs;
                case CHESTPLATE -> chest;
                case HELMET -> helm;
                default -> 0;
            };
        }

        public int total() {
            return boots + legs + chest + helm;
        }
    }

    /** Melee blade: attack damage added to the player's 1 (vanilla "baseline + bonus"), attack speed modifier, durability. */
    public record Blade(float damage, float speed, int durability) {}

    /** Power focus: a club-like melee damage (so the Forge can temper it) and the power damage bonus it gives in either hand. */
    public record Focus(float melee, double powerBonus, int durability) {}

    /** Mattock: one head for stone, wood and earth; mines like the vanilla pickaxe whose "incorrect" tag it shares. */
    public record Mattock(float speed, float melee, int durability, TagKey<Block> incorrect) {}

    public final String id;
    public final String setName;
    public final Rank rank;
    public final Rarity rarity;
    public final Armour armour;
    public final Blade blade;
    public final Focus focus;
    public final Mattock mattock;

    HunterTier(String id, String setName, Rank rank, Rarity rarity, Armour armour, Blade blade, Focus focus, Mattock mattock) {
        this.id = id;
        this.setName = setName;
        this.rank = rank;
        this.rarity = rarity;
        this.armour = armour;
        this.blade = blade;
        this.focus = focus;
        this.mattock = mattock;
    }

    public String itemId(Piece piece) {
        return id + "_" + piece.id;
    }

    // ---- hook tags (data/awaken/tags/item/hunter_*_<tier>.json, merged from every module's src/main/merge folder) ----

    /** Armour bulk: hides, scutes, membranes. New creature families add their hides here. */
    public TagKey<Item> hideTag() {
        return tag("hunter_hide_" + name().toLowerCase());
    }

    /** The tier's signature part: bone and quartz (E), beast trophies (D), Gate Lord relics (C, B), Lord and Descent relics (A, S). */
    public TagKey<Item> coreTag() {
        return tag("hunter_core_" + name().toLowerCase());
    }

    /** Bindings and fittings: sinew (E), metal (D), gate treasure (C), Veil-touched (B), sky and abyss (A), the Court's (S). Also repairs the tier. */
    public TagKey<Item> bindTag() {
        return tag("hunter_bind_" + name().toLowerCase());
    }

    /** The tier's signature mineral from the deep (D iron, C diamond, B Deepglass, A netherite, S starmetal); null for E. */
    public TagKey<Item> metalTag() {
        return this == E ? null : tag("tier_metal_" + name().toLowerCase());
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, Awaken.id(path));
    }

    public ResourceKey<EquipmentAsset> asset() {
        return ResourceKey.create(EquipmentAssets.ROOT_ID, Awaken.id(id));
    }

    /** E armour mends with hide; every other piece with the tier's bind (E: bone and flint). */
    public TagKey<Item> repairTag(Piece piece) {
        if (this == E) return piece.isArmour() ? hideTag() : coreTag();
        return bindTag();
    }

    public ArmorMaterial armorMaterial() {
        Map<ArmorType, Integer> defense = new EnumMap<>(ArmorType.class);
        for (ArmorType t : ArmorType.values()) defense.put(t, armour.defense(t));
        return new ArmorMaterial(armour.durability, defense, armour.enchantability, armour.sound, armour.toughness, armour.knockback,
                repairTag(Piece.HELMET), asset());
    }

    public HunterTier next() {
        return this == S ? null : values()[ordinal() + 1];
    }
}
