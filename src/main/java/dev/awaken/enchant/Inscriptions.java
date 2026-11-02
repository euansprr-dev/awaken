package dev.awaken.enchant;

import dev.awaken.core.Forces;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The thirty enchantments that can be written (SPEC §45.3), in the Journal's order: what each does in plain words, its
 * family and colour, what it fits, and where it is found (a kind of gate whose Lord carries its page, the market, old
 * books). Common code: the client's book, the Journal and the server's rules all read this one list. Anything not here
 * (Sharpness, Protection, Fire Aspect and the rest) can't be written or learned any more; items that carry it keep it.
 */
public final class Inscriptions {
    private Inscriptions() {}

    /** One writable enchantment. {@code gate} is the gate theme whose Lord keeps its page ("" for none). */
    public record Entry(int no, Identifier id, String name, Family family, int colour, String fits, String line,
                        String gate, boolean market, boolean books, Forces.@Nullable Force force) {
        public ResourceKey<Enchantment> key() {
            return ResourceKey.create(Registries.ENCHANTMENT, id);
        }

        /** Mending and Silk Touch cost more: one level of either is worth a lot. */
        public boolean dear() {
            return id.getPath().equals("mending") || id.getPath().equals("silk_touch");
        }
    }

    private static final int HEAT = 0xFF8A4C, FROST = 0x8FD8FF, CHARGE = 0xFFD84A, GRAVITY = 0xB993FF, SPIRIT = 0x4FE0C2;

    public static final List<Entry> ALL = List.of(
            e(1, "awaken:heat_ward", "Heat Ward", Family.WARD, HEAT, "armour", "You take less Heat damage, and fire and lava burn you for less time.", "ember_mines", false, false, Forces.Force.HEAT),
            e(2, "awaken:frost_ward", "Frost Ward", Family.WARD, FROST, "armour", "You take less Frost damage, and freezing hurts you less.", "frost_monastery", false, false, Forces.Force.FROST),
            e(3, "awaken:charge_ward", "Charge Ward", Family.WARD, CHARGE, "armour", "You take less Charge damage, lightning included.", "clockwork_foundry", false, false, Forces.Force.CHARGE),
            e(4, "awaken:gravity_ward", "Gravity Ward", Family.WARD, GRAVITY, "armour", "You take less Gravity damage.", "sky_archipelago", false, false, Forces.Force.GRAVITY),
            e(5, "awaken:spirit_ward", "Spirit Ward", Family.WARD, SPIRIT, "armour", "You take less Spirit damage, and less from magic.", "mirror_palace", false, false, Forces.Force.SPIRIT),
            e(6, "minecraft:blast_protection", "Blast Protection", Family.WARD, Family.WARD.colour, "armour", "Explosions hurt you less.", "precursor_lab", false, false, null),
            e(7, "minecraft:projectile_protection", "Projectile Protection", Family.WARD, Family.WARD.colour, "armour", "Arrows and thrown things hurt you less.", "glass_desert", false, false, null),
            e(8, "minecraft:feather_falling", "Feather Falling", Family.WARD, Family.WARD.colour, "boots", "Falls hurt you less.", "", true, true, null),
            e(9, "awaken:clear_blood", "Clear Blood", Family.WARD, 0x9FE07A, "armour", "Poison, wither and spores wear off twice as fast, and three times as fast at II.", "fungal_hollow", false, false, null),
            e(10, "awaken:bane_of_the_dead", "Bane of the Dead", Family.BANE, Family.BANE.colour, "weapons", "Your hits land 8% harder on the undead for each level. It never works on a Lord.", "bell_crypt", false, false, null),
            e(11, "awaken:bane_of_crawlers", "Bane of Crawlers", Family.BANE, Family.BANE.colour, "weapons", "Your hits land 8% harder on spiders, bees and other crawlers for each level. It never works on a Lord.", "hive", false, false, null),
            e(12, "awaken:bane_of_beasts", "Bane of Beasts", Family.BANE, Family.BANE.colour, "weapons", "Your hits land 8% harder on wild herds, packs and hunted quarry for each level. Never on your own beasts or a named one.", "", true, false, null),
            e(13, "awaken:bane_of_the_deep", "Bane of the Deep", Family.BANE, Family.BANE.colour, "weapons", "Your hits land 8% harder on the creatures of the Deep for each level. It never works on a Lord.", "", true, true, null),
            e(14, "minecraft:looting", "Looting", Family.SPOILS, Family.SPOILS.colour, "weapons", "Creatures drop more when you kill them.", "lantern_market", false, false, null),
            e(15, "minecraft:fortune", "Fortune", Family.SPOILS, Family.SPOILS.colour, "tools", "Ores give more when you break them.", "", false, true, null),
            e(16, "minecraft:silk_touch", "Silk Touch", Family.SPOILS, Family.SPOILS.colour, "tools", "Blocks come away whole.", "", false, true, null),
            e(17, "minecraft:efficiency", "Efficiency", Family.SPOILS, Family.SPOILS.colour, "tools", "You dig faster. It can't go on the Delver's Pick: its verses are its speed.", "", true, true, null),
            e(18, "awaken:harvest", "Harvest", Family.SPOILS, Family.SPOILS.colour, "weapons", "Wild beasts sometimes give one more hide, bone or pelt: one chance in two for each level.", "", true, false, null),
            e(19, "awaken:insight", "Insight", Family.SPOILS, Family.SPOILS.colour, "weapons and tools", "You get 15% more experience for each level from what you kill and mine.", "", true, true, null),
            e(20, "minecraft:respiration", "Respiration", Family.CARRYING, Family.CARRYING.colour, "helmets", "You can breathe longer under water.", "drowned_archive", false, false, null),
            e(21, "minecraft:aqua_affinity", "Aqua Affinity", Family.CARRYING, Family.CARRYING.colour, "helmets", "You dig at full speed under water.", "", true, true, null),
            e(22, "minecraft:depth_strider", "Depth Strider", Family.CARRYING, Family.CARRYING.colour, "boots", "You swim and walk faster under water.", "", true, true, null),
            e(23, "minecraft:swift_sneak", "Swift Sneak", Family.CARRYING, Family.CARRYING.colour, "leggings", "You move faster while sneaking.", "nightmare", false, false, null),
            e(24, "minecraft:unbreaking", "Unbreaking", Family.KEEPING, Family.KEEPING.colour, "anything", "It wears out slower.", "overgrown_temple", false, false, null),
            e(25, "minecraft:mending", "Mending", Family.KEEPING, Family.KEEPING.colour, "anything", "Experience you pick up mends it.", "abyssal_shrine", false, true, null),
            e(26, "minecraft:infinity", "Infinity", Family.KEEPING, Family.KEEPING.colour, "bows", "One arrow is enough.", "", false, true, null),
            e(27, "minecraft:loyalty", "Loyalty", Family.CARRYING, Family.CARRYING.colour, "tridents", "A thrown trident comes back to you.", "", false, true, null),
            e(28, "minecraft:riptide", "Riptide", Family.CARRYING, Family.CARRYING.colour, "tridents", "A thrown trident pulls you along in water and rain.", "", false, true, null),
            e(29, "minecraft:luck_of_the_sea", "Luck of the Sea", Family.SPOILS, Family.SPOILS.colour, "fishing rods", "You catch better things.", "", true, true, null),
            e(30, "minecraft:lure", "Lure", Family.SPOILS, Family.SPOILS.colour, "fishing rods", "Fish bite sooner.", "", true, true, null));

    private static final Map<Identifier, Entry> BY_ID = new HashMap<>();
    private static final Map<String, Entry> BY_GATE = new HashMap<>();

    static {
        for (Entry x : ALL) {
            BY_ID.put(x.id(), x);
            if (!x.gate().isEmpty()) BY_GATE.put(x.gate(), x);
        }
    }

    private static Entry e(int no, String id, String name, Family f, int colour, String fits, String line, String gate,
                           boolean market, boolean books, Forces.@Nullable Force force) {
        return new Entry(no, Identifier.parse(id), name, f, colour, fits, line, gate, market, books, force);
    }

    public static @Nullable Entry of(Identifier id) {
        return BY_ID.get(id);
    }

    public static @Nullable Entry of(Holder<Enchantment> h) {
        return h.unwrapKey().map(k -> BY_ID.get(k.identifier())).orElse(null);
    }

    /** The enchantment a kind of gate keeps: by the theme a Lord carries ("awaken:ember_mines" or "ember_mines"). */
    public static @Nullable Entry ofGate(String theme) {
        String t = theme.startsWith("awaken:") ? theme.substring(7) : theme;
        if (t.equals("lantern_night_market")) t = "lantern_market";
        return BY_GATE.get(t);
    }

    /** "the Ember Mines", "a Nightmare gate": a gate theme as you'd say it. */
    public static String gateName(String theme) {
        return switch (theme) {
            case "hive" -> "the Hive";
            case "ember_mines" -> "the Ember Mines";
            case "bell_crypt" -> "the Bell Crypt";
            case "fungal_hollow" -> "the Fungal Hollow";
            case "overgrown_temple" -> "the Overgrown Temple";
            case "drowned_archive" -> "the Drowned Archive";
            case "glass_desert" -> "the Glass Desert";
            case "lantern_market" -> "the Lantern Night Market";
            case "clockwork_foundry" -> "the Clockwork Foundry";
            case "frost_monastery" -> "the Frost Monastery";
            case "mirror_palace" -> "the Mirror Palace";
            case "precursor_lab" -> "the Precursor Lab";
            case "nightmare" -> "a Nightmare gate";
            case "sky_archipelago" -> "the Sky Archipelago";
            case "abyssal_shrine" -> "the Abyssal Shrine";
            default -> "a gate";
        };
    }

    /** The Lord who keeps a gate theme's page. */
    public static String lordName(String theme) {
        return switch (theme) {
            case "hive" -> "the Hive Regent";
            case "ember_mines" -> "the Ember Tyrant";
            case "bell_crypt" -> "the Bell Warden";
            case "fungal_hollow" -> "the Mother of Spores";
            case "overgrown_temple" -> "the Root Saint";
            case "drowned_archive" -> "the Drowned Archivist";
            case "glass_desert" -> "the Glass Serpent";
            case "lantern_market" -> "the Lantern Broker";
            case "clockwork_foundry" -> "the Gearwright Colossus";
            case "frost_monastery" -> "the Abbot of Frost";
            case "mirror_palace" -> "the Mirror Knight";
            case "precursor_lab" -> "Unit Zero";
            case "nightmare" -> "the Sleeper";
            case "sky_archipelago" -> "the Sky Leviathan";
            case "abyssal_shrine" -> "the Hollow Herald";
            default -> "a Lord";
        };
    }

    /** The highest level an enchantment can be written at (vanilla's max level, our own for ours). */
    public static int top(Holder<Enchantment> h) {
        return h.value().getMaxLevel();
    }

    public static String numeral(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }
}
