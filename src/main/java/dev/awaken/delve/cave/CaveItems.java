package dev.awaken.delve.cave;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.Notices;
import dev.awaken.delve.Ore;
import dev.awaken.delve.TuningForkItem;
import dev.awaken.delve.api.Lodes;
import dev.awaken.progression.Progression;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * The Delvers' things found in their mines (PLAN 8.2): their logs (read like a book; each one read joins the Codex),
 * the lode map (read with lapis: the nearest lodes come up on your Gate Map) and an old pick (a relic: carried, the
 * Tuning Fork reaches {@value #PICK_REACH} further). The logs tell the Delvers' story: they dug Carillon's bell metal,
 * followed the copper down, and broke into a seep.
 */
public final class CaveItems {
    private CaveItems() {}

    /** How many logs there are (lang keys {@code item.awaken.delvers_log.<i>.title}, {@code .page<n>}). */
    public static final int LOGS = 3, PAGES = 2;
    public static final int PICK_REACH = 16;
    /** How far the map's lines reach, and how many lodes it shows. */
    static final int MAP_REACH = 224, MAP_SHOWS = 3;

    /** Opens a log's pages on the client (installed by delve.client.CaveClient; nothing on a server). */
    public static volatile IntConsumer openLog = i -> {};

    public static ItemStack log(int i) {
        ItemStack st = new ItemStack(CaveContent.DELVERS_LOG.get());
        st.set(CaveContent.LOG_INDEX.get(), Math.floorMod(i, LOGS));
        return st;
    }

    public static ItemStack map() {
        return new ItemStack(CaveContent.LODE_MAP.get());
    }

    public static ItemStack pick() {
        return new ItemStack(CaveContent.DELVERS_PICK.get());
    }

    static void register() {
        Progression.addPerkSource((p, perk) -> perk.equals(TuningForkItem.REACH_PERK) && carries(p) ? PICK_REACH : 0);
    }

    /** Whether the player carries the old pick (main inventory or offhand), without allocating. */
    static boolean carries(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(CaveContent.DELVERS_PICK.get())) return true;
        return false;
    }

    /** Logs read (bits), and marking one read: the Codex learns it, the client is told. */
    public static int read(Player p) {
        return p.getData(CaveContent.LOGS_READ);
    }

    static void markRead(ServerPlayer p, int i) {
        int before = read(p), after = before | (1 << i);
        if (after == before) return;
        p.setData(CaveContent.LOGS_READ, after);
        AwakenNet.send(p, new CaveNet.Logs(after));
        Progression.count(p, "awaken:delver_logs_read");
        Progression.markNewThing(p, "delver_log/" + i);
        dev.awaken.signal.Signal.toast(p, Component.literal("Codex · " + Component.translatable("item.awaken.delvers_log." + i + ".title").getString())
                .withColor(0xD8C49A), Notices.Tier.AMBIENT, "codex");
    }

    // ---- the log ----------------------------------------------------------------------------------------------------

    public static final class LogItem extends Item {
        public LogItem(Item.Properties p) {
            super(p);
        }

        static int index(ItemStack st) {
            Integer i = st.get(CaveContent.LOG_INDEX.get());
            return i == null ? 0 : Math.floorMod(i, LOGS);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            int i = index(player.getItemInHand(hand));
            if (level.isClientSide()) openLog.accept(i);
            else if (player instanceof ServerPlayer sp) {
                markRead(sp, i);
                level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.9F);
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        public Component getName(ItemStack st) {
            return Component.translatable("item.awaken.delvers_log.named", Component.translatable("item.awaken.delvers_log." + index(st) + ".title"));
        }

        @Override
        public void appendHoverText(ItemStack st, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
            out.accept(Component.translatable("item.awaken.delvers_log.desc").withStyle(ChatFormatting.GRAY));
        }
    }

    // ---- the lode map -----------------------------------------------------------------------------------------------

    public static final class MapItem extends Item {
        public MapItem(Item.Properties p) {
            super(p);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            if (!player.hasInfiniteMaterials() && !takeLapis(player)) {
                sp.sendOverlayMessage(Component.translatable("item.awaken.lode_map.needs").withColor(0x7FA0FF));
                return InteractionResult.FAIL;
            }
            List<CaveNet.Mark> marks = read(sl, player.blockPosition());
            AwakenNet.send(sp, new CaveNet.LodeMarks(marks));
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.7F);
            if (marks.isEmpty()) {
                sp.sendOverlayMessage(Component.translatable("item.awaken.lode_map.none").withColor(0x9FB4C8));
                return InteractionResult.SUCCESS_SERVER;
            }
            StringBuilder sb = new StringBuilder();
            for (CaveNet.Mark m : marks) {
                Ore o = Ore.values()[m.ore()];
                if (!sb.isEmpty()) sb.append(" · ");
                sb.append(o.word()).append(' ').append(distance(player.blockPosition(), m.pos()));
            }
            dev.awaken.signal.Signal.toast(sp, Component.literal("Lode map · " + sb).withColor(0x9FB8FF), Notices.Tier.IMPORTANT, "lode_map");
            Progression.count(sp, "awaken:lode_maps_read");
            return InteractionResult.SUCCESS_SERVER;
        }

        @Override
        public void appendHoverText(ItemStack st, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
            out.accept(Component.translatable("item.awaken.lode_map.desc").withStyle(ChatFormatting.GRAY));
        }
    }

    /** The nearest lodes of every ore within the map's reach, nearest first, at most {@link #MAP_SHOWS}. */
    static List<CaveNet.Mark> read(ServerLevel level, BlockPos at) {
        List<Lodes.View> found = new ArrayList<>();
        for (Ore o : Ore.values()) {
            Optional<Lodes.View> v = Lodes.nearest(level, at, o, MAP_REACH);
            v.filter(x -> !x.heartBroken()).ifPresent(found::add);
        }
        found.sort(Comparator.comparingDouble(Lodes.View::distance));
        List<CaveNet.Mark> out = new ArrayList<>();
        for (int i = 0; i < Math.min(MAP_SHOWS, found.size()); i++) out.add(new CaveNet.Mark(found.get(i).ore().ordinal(), found.get(i).heart()));
        return out;
    }

    private static boolean takeLapis(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(Items.LAPIS_LAZULI)) {
                st.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** "140 NE" (blocks and the compass point). */
    static String distance(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        String[] pts = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        double a = Math.toDegrees(Math.atan2(-dx, dz));
        int i = Math.floorMod((int) Math.round(a / 45.0), 8);
        return Math.round(Math.sqrt(dx * dx + dz * dz)) + " " + pts[i];
    }

    // ---- the old pick -----------------------------------------------------------------------------------------------

    public static final class PickItem extends Item {
        public PickItem(Item.Properties p) {
            super(p);
        }

        @Override
        public void appendHoverText(ItemStack st, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
            out.accept(Component.translatable("item.awaken.delvers_pick.desc").withStyle(ChatFormatting.GRAY));
            out.accept(Component.translatable("item.awaken.delvers_pick.perk", PICK_REACH).withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
