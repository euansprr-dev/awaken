package dev.awaken.kin.client;

import dev.awaken.kin.KinContent;
import dev.awaken.kin.KinKind;
import dev.awaken.kin.KinNet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Who lives with you and which kinds you know, for the Hearth place on the band's stage (SPEC §57.7): the same rows the
 * old Hearth page drew as cards ({@link KinPageSection}), read as things. The server builds the words; this asks for them
 * while the place shows, every two seconds.
 */
public final class KinStrip {
    private KinStrip() {}

    /** A beast that lives at your home: its kind, what it's called, what it's doing and how it feels. */
    public record Beast(String id, String kind, String title, String name, String doing, String feeling, String made) {
        public String shown() {
            return name.isEmpty() ? title : name;
        }
    }

    /** A kind you've met or heard of. */
    public record Kind(String id, String title, boolean met, String where, String job, String wants, String standing, String line, String rumour, int colour) {}

    /** Something the home can't do yet: what it asks for, the rumour that goes with it, and the kind to track ("" for none). */
    public record Want(String text, String rumour, String kind, String state) {}

    /** Asks the server for the page (at most every two seconds). */
    public static void ask() {
        KinPageSection.ask();
    }

    public static List<Beast> beasts() {
        KinNet.Page p = KinPageSection.page;
        List<Beast> out = new ArrayList<>();
        if (p != null) for (List<String> r : p.beasts()) {
            out.add(new Beast(r.size() > 5 ? r.get(5) : "", r.get(0), r.get(1), r.size() > 6 ? r.get(6) : "", r.get(2), r.get(3), r.get(4)));
        }
        return out;
    }

    public static List<Kind> kinds() {
        KinNet.Page p = KinPageSection.page;
        List<Kind> out = new ArrayList<>();
        if (p != null) for (List<String> r : p.kin()) {
            int colour;
            try {
                colour = Integer.parseInt(r.get(9), 16) & 0xFFFFFF;
            } catch (NumberFormatException e) {
                colour = 0x7FC3FF;
            }
            out.add(new Kind(r.get(0), r.get(1), r.get(2).equals("1"), r.get(3), r.get(4), r.get(5), r.get(6), r.get(7), r.get(8), colour));
        }
        return out;
    }

    public static List<Want> wants() {
        KinNet.Page p = KinPageSection.page;
        List<Want> out = new ArrayList<>();
        if (p != null) for (List<String> r : p.home()) out.add(new Want(r.get(0), r.get(1), r.get(2), r.get(3)));
        return out;
    }

    /** The body of a kind, to draw it as itself; null for an id that isn't a kind. */
    public static @Nullable EntityType<?> body(String kind) {
        if (kind.equals("family_aya")) return dev.awaken.kin.FamilyContent.AYA.get();
        if (kind.equals("family_dubu")) return dev.awaken.kin.FamilyContent.DUBU.get();
        for (KinKind k : KinKind.values()) if (k.id().equals(kind)) {
            var h = KinContent.TYPES.get(k);
            return h == null ? null : h.get();
        }
        return null;
    }

    public static void track(String kind) {
        if (!kind.isEmpty()) ClientPacketDistributor.sendToServer(new KinNet.Track(kind));
    }

    /** The little window that names a beast; Esc there comes back to the screen that opened it. */
    public static void name(Beast b) {
        Minecraft mc = Minecraft.getInstance();
        if (!b.id().isEmpty()) mc.gui.setScreen(new KinNameScreen(mc.gui.screen(), b.id(), b.title(), b.name()));
    }
}
