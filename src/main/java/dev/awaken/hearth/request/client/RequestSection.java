package dev.awaken.hearth.request.client;

import dev.awaken.hearth.HearthContent;
import dev.awaken.hearth.request.ActiveRequest;
import dev.awaken.hearth.request.RequestContent;
import dev.awaken.hearth.request.RequestDef;
import dev.awaken.hearth.request.RequestNet;
import dev.awaken.hearth.request.RequestPools;
import dev.awaken.hearth.request.RequestRules;
import dev.awaken.hearth.request.RequestState;
import dev.awaken.hearth.request.Requests;
import dev.awaken.signal.client.Glass;
import dev.awaken.signal.client.SystemHud;
import dev.awaken.signal.client.Ui;
import dev.awaken.signal.client.UiSection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Status screen section "ANCHOR REQUESTS": each request with its icon, what's in the Anchor and in your pack, where to
 * look and a way to let it go; offers waiting for an answer; then the streak, Forge credit and the town's regard with
 * the next touch it unlocks.
 */
final class RequestSection extends UiSection {
    private static final int ICON = 18;

    private static RequestState state() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? new RequestState() : p.getData(RequestContent.STATE.get());
    }

    @Override
    public String title() {
        return "ANCHOR REQUESTS";
    }

    @Override
    public int order() {
        return 41;
    }

    @Override
    public boolean visible() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return false;
        RequestState st = state();
        return !st.active.isEmpty() || !st.offers.isEmpty() || st.completed > 0 || p.getData(HearthContent.VIEW.get()).has();
    }

    @Override
    protected void build(Ui ui) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        RequestState st = state();
        if (st.active.isEmpty() && st.offers.isEmpty()) {
            ui.text(st.completed == 0 ? "The Anchor will ask for something you can find near home. A request comes each play-day."
                    : "Nothing asked right now. A new request comes with the next play-day.", Glass.a(Glass.LABEL, 1f));
        }
        for (int i = 0; i < st.active.size(); i++) {
            ActiveRequest r = st.active.get(i);
            int carried = RequestRules.carried(p, r.target());
            int progress = Math.min(r.count(), r.delivered() + carried);
            boolean ready = progress >= r.count();
            row(ui, r, Requests.name(r).getString() + "  " + progress + "/" + r.count(),
                    "In the Anchor " + r.delivered() + " · carrying " + carried, ready ? Glass.GOOD : Glass.AMBER);
            ui.bar(r.count() == 0 ? 0 : (double) progress / r.count(), ready ? Glass.GOOD : Glass.LANTERN);
            String hint = hint(r);
            ui.text(ready ? "All in hand: use your Anchor to deliver." : hint.isEmpty() ? "Use your Anchor with some in your pack: part counts too." : hint,
                    Glass.a(Glass.LABEL, 1f), 0, false);
            var flow = ui.flow();
            if (flow.button("Drop it", Glass.LABEL, true)) ClientPacketDistributor.sendToServer(new RequestNet.Drop(i, r.id()));
            flow.end();
            ui.gap(2);
        }
        for (RequestState.Offer o : st.offers) {
            ActiveRequest r = o.request();
            row(ui, r, "Offered · " + r.count() + " " + Requests.name(r).getString(), hint(r), Glass.EDGE);
            var flow = ui.flow();
            if (flow.button("Take it on", Glass.GOOD, true)) SystemHud.answer(o.action(), true);
            if (flow.button("Not now", Glass.LABEL, true)) SystemHud.answer(o.action(), false);
            flow.end();
            ui.gap(2);
        }
        ui.rule();
        int streak = st.streak; // the server lets a lapsed streak fall to 0 at the start of each play-day
        String pace = "Done " + st.completed + (streak > 1 ? " · streak " + streak + " (+" + Math.round((RequestRules.streakMultiplier(streak) - 1) * 100) + "%)" : "")
                + (st.forgeCredit > 0 ? " · Forge credit " + st.forgeCredit + "%" : "");
        ui.text(pace, Glass.a(Glass.TEXT, 1f));
        if (st.forgeCredit > 0) ui.text("Your next temper or Forge upgrade gives " + st.forgeCredit + "% of its price back.", Glass.a(Glass.LABEL, 1f));
        RequestRules.Standing s = RequestRules.Standing.of(st.reputation);
        RequestRules.Standing next = s.next();
        ui.pair("Town · " + s.label, next == null ? String.valueOf(st.reputation) : st.reputation + " / " + next.at, Glass.a(Glass.LANTERN, 1f), Glass.a(Glass.LABEL, 1f));
        if (next != null) {
            int from = s.at;
            ui.bar((double) (st.reputation - from) / Math.max(1, next.at - from), Glass.LANTERN);
            ui.text("Next: " + next.touch + ".", Glass.a(Glass.LABEL, 1f));
        }
        if (s != RequestRules.Standing.NEWCOMER) ui.text("Now: " + s.touch + ".", Glass.a(Glass.TEXT, 0.9f));
    }

    /** An icon and two lines beside it. */
    private static void row(Ui ui, ActiveRequest r, String line1, String line2, int colour) {
        int y = ui.y();
        if (ui.drawing()) {
            ItemStack icon = RequestRules.icon(r.target());
            if (!icon.isEmpty()) ui.g().item(icon, ui.x, y + 1);
        }
        ui.textAt(line1, ui.x + ICON + 2, y + 1, ui.width - ICON - 2, Glass.a(colour, 1f), false);
        ui.textAt(line2, ui.x + ICON + 2, y + 10, ui.width - ICON - 2, Glass.a(Glass.LABEL, 1f), false);
        ui.gap(ICON + 2);
    }

    private static String hint(ActiveRequest r) {
        RequestDef d = RequestPools.get(r.id());
        return d == null ? "" : d.hint();
    }
}
