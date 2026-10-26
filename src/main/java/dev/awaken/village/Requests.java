package dev.awaken.village;

import dev.awaken.Awaken;
import dev.awaken.citizens.Lines;
import dev.awaken.core.api.EconomyHooks;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The notice board's requests (SPEC §28.6, PLAN 5.5): real needs from the village's own state, never a random table. A
 * smithy wants iron, the inn's table wants bread, fields want bone meal, a pen wants wheat for the winter, damage wants
 * timber and stone to mend it, a frightened village wants torches for its edge, a village with a bell wants copper for
 * its frame, nets want string. Up to three at a time, renewed every other play-day; counts shown and brought a
 * handful at a time; paid in emeralds (EconomyHooks, once per request) and standing when filled.
 */
public final class Requests {
    private Requests() {}

    static final int MAX = 3, KEEP_DAYS = 6;

    /** One kind of need: what makes a village want it, the item and count, the pay and the standing, the note's words. */
    record Need(String id, String item, int count, int pay, int standing, String text) {}

    static List<Need> needs(VillageService.Live l) {
        VillagePlan p = l.plan();
        VillageState.Village v = l.rec();
        List<Need> out = new ArrayList<>();
        boolean smithy = p.forge != null, inn = p.buildings.stream().anyMatch(b -> b.use().equals("inn"));
        if (!v.damage.isEmpty() && !dev.awaken.village.trouble.TroubleDirector.busy(l.key())) {
            String log = BuiltInRegistries.ITEM.getKey(Trade.log(p.family)).toString();
            out.add(new Need("mend_timber", log, 32, 4, 8, "Timber to mend what was broken. The masons are waiting on it."));
            out.add(new Need("mend_stone", "minecraft:cobblestone", 64, 3, 6, "Stone for the walls that came down."));
        }
        if (smithy) out.add(new Need("smith_iron", "minecraft:iron_ingot", 12, 5, 6, "Iron for the forge. Hinges, nails, a new hoe or two."));
        if (inn) out.add(new Need("inn_bread", "minecraft:bread", 16, 3, 4, "Bread for the inn's table. The road brings more mouths than flour."));
        if (!p.fields.isEmpty()) out.add(new Need("fields_bone", "minecraft:bone_meal", 24, 2, 4, "Bone meal for the fields before the rain."));
        if (!p.pens.isEmpty()) out.add(new Need("pen_wheat", "minecraft:wheat", 32, 2, 4, "Wheat to see the animals through the winter."));
        if (v.mood < 45) out.add(new Need("edge_torches", "minecraft:torch", 32, 3, 6, "Torches for the edge of the village. The nights have been loud."));
        if (p.bell != null) out.add(new Need("bell_copper", "minecraft:copper_ingot", 9, 4, 6, "Copper for the bell's frame. It groans in the wind."));
        if (p.dock != null) out.add(new Need("net_string", "minecraft:string", 12, 2, 4, "String to mend the nets."));
        if (p.lamps.size() > 4) out.add(new Need("lamp_glass", "minecraft:glass_pane", 8, 2, 4, "Glass for the lamps the wind broke."));
        out.add(new Need("hides", "minecraft:leather", 10, 3, 4, "Leather for boots and harness. The carter's wearing through his."));
        return out;
    }

    /** The board as it stands today: unfinished notices kept (a week at most), new ones posted every other day. */
    static List<VillageState.Request> board(MinecraftServer server, VillageService.Live l) {
        VillageState.Village v = l.rec();
        long today = VillageService.today(server);
        if (v.requestDay < 0 || today - v.requestDay >= 2) {
            v.requests.removeIf(r -> today - r.posted > KEEP_DAYS);
            List<Need> needs = needs(l);
            Random r = new Random(l.plan().seed ^ today * 0x2545F4914F6CDD1DL);
            java.util.Collections.shuffle(needs, r);
            // damage first: what's broken is the village's first want
            needs.sort((a, b) -> Boolean.compare(!a.id().startsWith("mend"), !b.id().startsWith("mend")));
            for (Need n : needs) {
                if (v.requests.size() >= MAX) break;
                if (v.requests.stream().anyMatch(q -> q.id.startsWith(n.id()))) continue;
                v.requests.add(new VillageState.Request(n.id() + "@" + today, n.item(), n.count(), 0, n.pay(), n.standing(), today, n.text()));
            }
            v.requestDay = today;
            VillageState.get(server).setDirty();
        }
        return v.requests;
    }

    /** Hands over what the player carries toward a notice; filled, it pays and the village remembers. */
    static void deliver(ServerPlayer p, VillageService.Live l, String id) {
        MinecraftServer server = p.level().getServer();
        VillageState.Request q = null;
        for (VillageState.Request r : l.rec().requests) if (r.id.equals(id)) q = r;
        if (q == null) return;
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(q.item));
        int want = q.need - q.have, took = 0;
        var inv = p.getInventory();
        for (int s = 0; s < inv.getContainerSize() && took < want; s++) {
            ItemStack st = inv.getItem(s);
            if (!st.is(item)) continue;
            int n = Math.min(st.getCount(), want - took);
            st.shrink(n);
            took += n;
        }
        if (took == 0) {
            p.sendOverlayMessage(Component.translatable("awaken.village.request.none_carried", new ItemStack(item).getHoverName()));
            return;
        }
        q.have += took;
        VillageState.get(server).setDirty();
        if (q.have < q.need) {
            p.sendOverlayMessage(Component.translatable("awaken.village.request.progress", took, new ItemStack(item).getHoverName(), q.have, q.need));
            return;
        }
        // filled: paid once, standing, the village better off, a line in its history
        l.rec().requests.remove(q);
        boolean paid = EconomyHooks.pay(p, q.pay, Awaken.id(("village/" + l.key() + "/" + q.id).toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_")));
        VillageService.standing(p, l, q.standing, "request " + q.id);
        l.rec().prosperity = Math.min(100, l.rec().prosperity + 3);
        l.rec().mood = Math.min(100, l.rec().mood + (q.id.startsWith("edge") || q.id.startsWith("mend") ? 8 : 2));
        // (SPEC §28.7) mending notices put blocks back (the masons' work, W4's Rebuild); a trouble's notices move it on
        onFilled.filled(p, l, q.id);
        VillageService.history(server, l, p.getName().getString() + " brought " + q.need + " " + new ItemStack(item).getHoverName().getString().toLowerCase(java.util.Locale.ROOT)
                + ": " + q.text);
        String thanks = Lines.get().pick(p.level().getGameTime(), (int) Math.floorMod(p.level().getOverworldClockTime(), 24000L), "village.request.done");
        p.sendSystemMessage(Component.literal(VillageService.fill(l, p, Lines.fill(thanks, p.getName().getString(), l.plan().name, "", VillageService.hour(p))))
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        if (paid) p.sendOverlayMessage(Component.translatable("awaken.village.request.paid", q.pay));
        Awaken.LOG.info("[village] {} filled {} at {} (paid {} emeralds: {})", p.getName().getString(), q.id, l.plan().name, q.pay, paid);
        VillageNet.openBoard(p, l);
    }

    // ---- (SPEC §28.7) a trouble's notices: the Rebuild's timber, stone and iron -----------------------------------------------

    /** Someone filled a notice (W4's troubles hear of it). */
    public interface Filled {
        void filled(ServerPlayer p, VillageService.Live l, String id);
    }

    private static Filled onFilled = (p, l, id) -> {};

    public static void onFilled(Filled f) {
        onFilled = f;
    }

    /**
     * Pins a trouble's notice on a village's board (in front of the others; the board holds four while one is up): an item
     * id, or "timber" for the land's own logs. Returns the notice's id.
     */
    public static String post(MinecraftServer server, VillageService.Live l, String id, String item, int count, int pay, int standing, String text) {
        if (item.equals("timber")) item = BuiltInRegistries.ITEM.getKey(Trade.log(l.plan().family)).toString();
        long today = VillageService.today(server);
        String full = id + "@" + today;
        l.rec().requests.removeIf(r -> r.id.startsWith(id + "@"));
        l.rec().requests.addFirst(new VillageState.Request(full, item, count, 0, pay, standing, today, text));
        VillageState.get(server).setDirty();
        return full;
    }

    /** How much of a notice is in (have, need), or null if it's not on the board (filled, or taken down). */
    public static int @org.jspecify.annotations.Nullable [] progress(VillageService.Live l, String id) {
        for (VillageState.Request r : l.rec().requests) if (r.id.equals(id)) return new int[]{r.have, r.need};
        return null;
    }

    /** Takes a trouble's notice down (it ended another way). */
    public static void unpost(MinecraftServer server, VillageService.Live l, String id) {
        if (l.rec().requests.removeIf(r -> r.id.equals(id))) VillageState.get(server).setDirty();
    }

    static ItemStack icon(VillageState.Request q) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(q.item));
        return new ItemStack(item == Items.AIR ? Items.PAPER : item);
    }
}
