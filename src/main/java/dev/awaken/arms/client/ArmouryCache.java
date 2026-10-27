package dev.awaken.arms.client;

import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.net.ArmsPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** What the server last said of the Armoury (the band reads it too), and the asking. */
final class ArmouryCache {
    private ArmouryCache() {}

    static ArmsPayloads.ArmouryView view;
    static ArmsPayloads.ArmouryDetail detail;

    static void onView(ArmsPayloads.ArmouryView msg) {
        view = msg;
        if (msg.open()) Minecraft.getInstance().gui.setScreen(new ArmouryScreen());
    }

    static void onDetail(ArmsPayloads.ArmouryDetail msg) {
        detail = msg;
    }

    static void ask(int op, String style, String tier) {
        ClientPacketDistributor.sendToServer(new ArmsPayloads.ArmouryAct(op, style, tier));
    }

    static void reset() {
        view = null;
        detail = null;
    }

    static boolean known(String style) {
        if (view == null) return false;
        for (var e : view.styles()) if (e.id().equals(style)) return e.state() == 0;
        return false;
    }

    static boolean tracked(String style, String tier) {
        return view != null && view.tracked().equals(style + ":" + tier);
    }

    static final int FORGE = ArmsArmoury.FORGE, HALE = ArmsArmoury.HALE;
}
