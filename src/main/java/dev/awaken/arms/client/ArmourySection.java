package dev.awaken.arms.client;

import dev.awaken.arms.Archetypes;
import dev.awaken.arms.ArmsArmoury;
import dev.awaken.progression.client.StatusSections;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * K, Powers, THE ARMOURY (docs/arms-progression/PLAN.md §3.2): the styles you know, with a Track button for each, the
 * weapon you are tracking and what it still needs, and where the doors are. Read only; buying and drawing need a door
 * (Hale's rack, a Forge). It lists only what you know and how many wait; never what you haven't met.
 */
final class ArmourySection extends UiSection {
    private String asked = "";

    static void register() {
        StatusSections.add(StatusSections.Tab.POWERS, new ArmourySection());
    }

    @Override
    public String title() {
        return "THE ARMOURY";
    }

    @Override
    public int order() {
        return 35; // under THE BODY (30)
    }

    @Override
    public boolean visible() {
        return ArmouryCache.view != null;
    }

    @Override
    protected void build(Ui ui) {
        var v = ArmouryCache.view;
        if (v == null) return;
        int known = 0;
        for (var e : v.styles()) if (e.state() == 0) known++;
        ui.pair("Styles you know", known + " of " + Archetypes.all().size(), Glass.LABEL, Glass.TEXT);
        ui.pair("Essence", String.valueOf(v.essence()), Glass.LABEL, Glass.LANTERN);
        ui.gap(4);
        for (var e : v.styles()) {
            if (e.state() != 0) continue;
            Archetypes.Loaded st = Archetypes.get(e.id());
            Ui.Flow row = ui.flow();
            row.text(st == null ? e.id() : st.def().name(), Glass.TITLE);
            boolean tracked = v.tracked().startsWith(e.id() + ":");
            if (tracked) row.chip("TRACKED", Glass.GOOD);
            else if (row.button("Track", Glass.EDGE, true)) ArmouryCache.ask(ArmsArmoury.TRACK, e.id(), "");
            row.end();
        }
        if (!v.tracked().isEmpty()) {
            ui.gap(4);
            var d = ArmouryCache.detail;
            String[] t = v.tracked().split(":");
            if (d != null && d.style().equals(t[0]) && d.tier().equals(t[1])) {
                var weapon = BuiltInRegistries.ITEM.getValue(dev.awaken.Awaken.id(prefix(t[1]) + "_" + t[0]));
                ui.text("Making: " + (weapon == net.minecraft.world.item.Items.AIR ? t[0] : weapon.getName(new ItemStack(weapon)).getString()), Glass.TITLE);
                for (var n : d.needs()) {
                    Identifier id = Identifier.parse(n.id());
                    String label = n.kind().equals("tag") ? Language.getInstance().getOrDefault("tag.item." + id.getNamespace() + "." + id.getPath())
                            : BuiltInRegistries.ITEM.getValue(id).getName(new ItemStack(BuiltInRegistries.ITEM.getValue(id))).getString();
                    ui.pair(label, Math.min(n.have(), n.need()) + "/" + n.need(), Glass.TEXT, n.have() >= n.need() ? Glass.GOOD : Glass.LANTERN);
                }
            } else if (ui.drawing() && !asked.equals(v.tracked())) {
                asked = v.tracked();
                ArmouryCache.ask(ArmsArmoury.DETAIL, t[0], t[1]);
            }
        }
        ui.gap(4);
        if (v.hidden() > 0) ui.text(v.hidden() + " more styles wait in the world. Gates teach them, and Hale's rack sells them as you rank up.", Glass.LABEL);
        ui.text("Sneak and talk to Hale, or use a Forge with an empty hand, to open the Armoury.", Glass.LABEL);
    }

    private static String prefix(String tier) {
        int i = 0;
        for (; i < ArmsArmoury.TIERS.length; i++) if (ArmsArmoury.TIERS[i].equals(tier)) break;
        return new String[] {"wildhide", "beastbound", "gatewrought", "veilwoven", "lordscale", "sovereign"}[Math.min(5, i)];
    }
}
