package dev.awaken.finds.verb;

import dev.awaken.finds.FindsContent;
import dev.awaken.finds.FindsData;
import dev.awaken.finds.Leads;
import dev.awaken.finds.Site;
import dev.awaken.finds.Sites;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/**
 * What a remain does (SPEC §62.6): it has one carving, and the carving points. Reading it has the System say its line
 * and then which way the monument it belongs to lies and about how far; the way is kept as a lead on the Tasks page.
 * It can be read as often as anyone likes; the first reading is what finishes it.
 */
public final class Points implements Script {
    private final String part, line;

    private Points(String part, String line) {
        this.part = part;
        this.line = line;
    }

    public static Points to(String part, String line) {
        return new Points(part, line);
    }

    @Override
    public boolean use(Ctx c, ServerPlayer p, String name, int i, ItemStack held) {
        if (!name.equals(part)) return false;
        Site to = Sites.monumentFrom(c.level, c.site.x(), c.site.z(), Sites.REMAINS_REACH + 200);
        String where = "It does not say where.";
        if (to != null) {
            String way = Leads.way(c.site.x(), c.site.z(), to.x(), to.z());
            int far = (int) (Math.round(Math.hypot(to.x() - c.site.x(), to.z() - c.site.z()) / 50.0) * 50);
            where = "It points " + way + ". About " + far + " blocks.";
            FindsData d = p.getData(FindsContent.FINDS);
            String lead = c.kind().name() + "|" + c.site.x() + "|" + c.site.z() + "|" + way + "|" + to.key();
            if (!d.paidAt(to.key()) && !d.leads().contains(lead)) p.setData(FindsContent.FINDS, d.withLead(lead));
        }
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("[" + line + " " + where + "]")).topic("monument").ttl(20 * 14).build());
        c.sound(c.pos(part, i), SoundEvents.BOOK_PAGE_TURN, 0.7F, 0.7F);
        c.puff(c.pos(part, i), ParticleTypes.ENCHANT, 10, 0.4, 0.2);
        if (!c.done()) c.complete(p);
        return true;
    }
}
