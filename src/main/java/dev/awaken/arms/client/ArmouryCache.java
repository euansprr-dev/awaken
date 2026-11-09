package dev.awaken.arms.client;

import dev.awaken.arms.ArmsArmoury;
import dev.awaken.arms.net.ArmsPayloads;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** What the server last said of the Armoury (the band reads it too), and the asking. */
public final class ArmouryCache {
    private ArmouryCache() {}

    public static ArmsPayloads.ArmouryView view;
    public static ArmsPayloads.ArmouryDetail detail;

    static void onView(ArmsPayloads.ArmouryView msg) {
        view = msg;
        if (msg.open()) Minecraft.getInstance().gui.setScreen(dev.awaken.hall.client.HallConfig.classic() ? new ArmouryScreen() : new dev.awaken.hall.client.HallScreen());
    }

    static void onDetail(ArmsPayloads.ArmouryDetail msg) {
        detail = msg;
    }

    public static void ask(int op, String style, String tier) {
        ClientPacketDistributor.sendToServer(new ArmsPayloads.ArmouryAct(op, style, tier));
    }

    static void reset() {
        view = null;
        detail = null;
    }

    public static boolean known(String style) {
        if (view == null) return false;
        for (var e : view.styles()) if (e.id().equals(style)) return e.state() == 0;
        return false;
    }

    public static boolean tracked(String style, String tier) {
        return view != null && view.tracked().equals(style + ":" + tier);
    }

    static final int FORGE = ArmsArmoury.FORGE, HALE = ArmsArmoury.HALE;
}
