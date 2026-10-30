package dev.awaken.tasks.client;

import dev.awaken.Awaken;
import dev.awaken.arms.ArmsSources;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.mobs.def.Theme;
import dev.awaken.world.fauna.client.CreatureCodex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where a need comes from, in a line (SPEC §27.13): the page's own words for it ({@code awaken.where.*}), else the
 * source line the Armoury and the tooltips already use ({@code awaken.source.*}), else the creatures that drop it and
 * where they live, "near you" when you stand in their land. Client only: the words live in the lang files.
 */
final class WhereFrom {
    private WhereFrom() {}

    private static final Map<String, String> CACHE = new HashMap<>();

    /** A need's item ("ns:path", "#ns:path" for a tag, "" for a condition) → one plain line, or "". */
    static String line(String item) {
        if (item.isEmpty()) return "";
        String base = CACHE.computeIfAbsent(item, WhereFrom::lookup);
        String near = near(item);
        return near.isEmpty() ? base : base.isEmpty() ? near : base + " " + near;
    }

    private static String lookup(String item) {
        boolean tag = item.startsWith("#");
        Identifier id = Identifier.tryParse(tag ? item.substring(1) : item);
        if (id == null) return "";
        if (tag) {
            String own = said("awaken.where.tag." + id.getPath());
            return own.isEmpty() ? said("awaken.source.tag." + id.getPath()) : own;
        }
        String own = said("awaken.where." + id.getNamespace() + "." + id.getPath());
        if (!own.isEmpty()) return own;
        String source = said(ArmsSources.langKey(id));
        if (!source.isEmpty()) return source;
        List<String> who = new ArrayList<>();
        for (Identifier c : ArmsSources.creatures(id)) {
            String name = I18n.get("entity." + c.getNamespace() + "." + c.getPath());
            MobDef d = Bestiary.byAnyId(c.toString());
            who.add(d == null || d.theme == null ? name : name + " (" + land(d.theme) + ")");
        }
        if (who.isEmpty()) return "";
        return (who.size() == 1 ? who.getFirst() + " drops it." : String.join(", ", who) + " drop it.");
    }

    private static String said(String key) {
        return Language.getInstance().has(key) ? I18n.get(key) : "";
    }

    /** Where a creature lives: its land on the surface, or the gate it guards. */
    private static String land(Theme t) {
        if (t.isWild()) return CreatureCodex.where(t);
        if (t.isGate()) return "the " + t.display + " gate";
        return t.display;
    }

    /** "Some live round here." when a creature that drops it lives in the land you stand in. */
    private static String near(String item) {
        if (item.startsWith("#")) return "";
        Identifier id = Identifier.tryParse(item);
        var mc = Minecraft.getInstance();
        if (id == null || mc.level == null || mc.player == null) return "";
        List<Identifier> who = ArmsSources.creatures(id);
        if (who.isEmpty()) return "";
        Holder<Biome> here = mc.level.getBiome(BlockPos.containing(mc.player.position()));
        for (Identifier c : who) {
            MobDef d = Bestiary.byAnyId(c.toString());
            if (d == null || d.theme == null || !d.theme.isWild()) continue;
            TagKey<Biome> land = TagKey.create(Registries.BIOME, Awaken.id("fauna/" + d.theme.name().toLowerCase(Locale.ROOT)));
            if (here.is(land)) return "Some live round here.";
        }
        return "";
    }

    /** What a need shows as its icon: the item, or one of a tag's items in turn (a second each). */
    static ItemStack icon(String item) {
        if (item.isEmpty()) return ItemStack.EMPTY;
        if (!item.startsWith("#")) {
            Identifier id = Identifier.tryParse(item);
            return id == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.getValue(id));
        }
        Identifier id = Identifier.tryParse(item.substring(1));
        if (id == null) return ItemStack.EMPTY;
        List<Holder<Item>> items = new ArrayList<>();
        for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, id))) items.add(h);
        if (items.isEmpty()) return ItemStack.EMPTY;
        int at = (int) (System.currentTimeMillis() / 1000 % items.size());
        return new ItemStack(items.get(at));
    }

    /**
     * A need's name: the server's words ("Six seats (stairs to sit on)" says more than the icon's "Oak Stairs"), unless
     * they came untranslated (a mod item's key on a server without the mod's language), then the item's own name here.
     */
    static String name(String item, String label) {
        if (item.isEmpty() || item.startsWith("#") || !label.startsWith("item.") && !label.startsWith("block.")) return label;
        ItemStack s = icon(item);
        return s.isEmpty() ? label : s.getHoverName().getString();
    }

    static void reset() {
        CACHE.clear();
    }

    /** "About 310 blocks north-east", "Close by", or "" without a place. */
    static String direction(@Nullable BlockPos at) {
        var p = Minecraft.getInstance().player;
        if (at == null || p == null) return "";
        double dx = at.getX() + 0.5 - p.getX(), dz = at.getZ() + 0.5 - p.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 16) return "Close by.";
        String[] ways = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        double deg = Math.toDegrees(Math.atan2(dx, -dz)); // 0 is north, turning east
        String way = ways[(int) Math.floorMod(Math.round(deg / 45.0), 8L)];
        long round = d < 100 ? Math.round(d / 5) * 5 : Math.round(d / 10) * 10;
        return "About " + round + " blocks " + way + ".";
    }
}
