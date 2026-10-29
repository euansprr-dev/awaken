package dev.awaken.arms;

import dev.awaken.Awaken;
import dev.awaken.arms.net.ArmsPayloads;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.api.ArmsApi;
import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The first hour's weapons (docs/arms-progression/PLAN.md §3.3): what a fighter knows, teaching a style, Hale's issue (one
 * of three finished Wildhide weapons with its Blueprint and a spare Fang) and the two things that keep essence from
 * drying up: a card that says what a shard is, and the no-drought rule. Hale's lines, his place and the Now line are the
 * living module's, through {@link ArmsApi}.
 */
public final class ArmsIssue implements ArmsApi.Hooks {
    /** The three weapons Hale offers: the three other feels beside the Longsword everyone has (fast, wide, heavy). */
    public static final List<String> OPTIONS = List.of("daggers", "axe", "maul");
    /** The fifth kill in a row with no shard drops one. */
    static final int DROUGHT = 5;
    static final String SHARD_CARD = "awaken:first/shard_card";
    private static final long OFFER_TICKS = 20L * 60 * 5;

    static final ArmsIssue INSTANCE = new ArmsIssue();
    /** When each fighter was shown the pick (not saved: closing the screen just lets them be asked again). */
    private static final Map<UUID, Long> OFFERED = new HashMap<>();

    private ArmsIssue() {}

    // ---- what a fighter knows -----------------------------------------------------------------------------------------

    @Override
    public boolean knows(ServerPlayer p, String style) {
        migrate(p);
        return ArmsProgress.of(p).knows(style) || ArmsForge.owns(p, style);
    }

    /** Knows it from now on, without giving the Blueprint (drawn at a Forge, handed over by hand). */
    static void remember(ServerPlayer p, String style) {
        ArmsProgress g = ArmsProgress.of(p);
        if (g.styles.add(style)) p.setData(ArmsContent.PROGRESS, g);
    }

    @Override
    public boolean learn(ServerPlayer p, String style, String source) {
        Item blueprint = ArmsItems.blueprintItem(style);
        if (blueprint == null || knows(p, style)) return false;
        remember(p, style);
        giveItem(p, new ItemStack(blueprint));
        Archetypes.Loaded st = Archetypes.get(style);
        dev.awaken.core.api.NoticeEvents.tell(p, new dev.awaken.core.api.NoticeEvents.Event("pattern/" + style,
                "Pattern learned", (st == null ? style : st.def().name()) + " Blueprint", "You can make weapons in this style now.",
                java.util.List.of("You learned it from " + source.replaceFirst("^from ", "") + ".",
                        "The Blueprint is in your inventory. Use it at a crafting table with the weapon's materials, and you get the Blueprint back.",
                        "You won't forget it. The Forge Armoury lists the styles you know and what each one costs."),
                dev.awaken.core.api.NoticeEvents.Kind.PERMANENT, 0x6FB3FF));
        Progression.markNewThing(p, "pattern:" + style);
        ArmsArmoury.sendView(p);
        return true;
    }

    static void giveItem(ServerPlayer p, ItemStack stack) {
        p.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
    }

    /** The one-time backfill for old saves: the Blueprints in the pack and the styles already used are known; a fighter who already carries a styled weapon needs no issue. */
    static void migrate(ServerPlayer p) {
        ArmsProgress g = ArmsProgress.of(p);
        if (g.migrated) return;
        g.migrated = true;
        for (Archetypes.Loaded st : Archetypes.all()) {
            if (ArmsForge.owns(p, st.id())) g.styles.add(st.id());
        }
        g.styles.addAll(p.getData(ArmsContent.MEMORY).kills().keySet());
        if (holdsStyledWeapon(p)) g.issued = true;
        p.setData(ArmsContent.PROGRESS, g);
    }

    static boolean holdsStyledWeapon(ServerPlayer p) {
        for (ItemStack s : p.getInventory().getNonEquipmentItems()) if (!s.isEmpty() && ArmsItems.of(s.getItem()) != null) return true;
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack s = p.getItemBySlot(slot);
            if (!s.isEmpty() && ArmsItems.of(s.getItem()) != null) return true;
        }
        return false;
    }

    // ---- Hale's issue -------------------------------------------------------------------------------------------------

    @Override
    public boolean issuePending(ServerPlayer p) {
        if (!Progression.profile(p).awakened() || p.level().dimension() != Level.OVERWORLD) return false;
        migrate(p);
        ArmsProgress g = ArmsProgress.of(p);
        return !g.issued && !holdsStyledWeapon(p);
    }

    @Override
    public void gateSpawn(LivingEntity mob, String gateKey, int area, int floor, boolean elite) {
        ArmsPatterns.gateSpawn(mob, gateKey, area, floor, elite);
    }

    @Override
    public void markBearer(LivingEntity mob, String claimKey) {
        ArmsPatterns.markBearer(mob, claimKey);
    }

    @Override
    public void lordPattern(ServerPlayer p, String gateKey, String themeId) {
        ArmsPatterns.lordPattern(p, gateKey, themeId);
    }

    @Override
    public @Nullable String patternLine(ServerPlayer p, String themeId) {
        return ArmsPatterns.patternLine(p, themeId);
    }

    @Override
    public void questDone(ServerPlayer p, String chain) {
        ArmsPatterns.questDone(p, chain);
    }

    @Override
    public boolean leftChestPattern(ServerPlayer p) {
        return ArmsPatterns.leftChest(p);
    }

    @Override
    public boolean placeStand(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos pos, net.minecraft.core.Direction facing, String kind, String rankLabel, long seed) {
        return ArmsStands.place(level, pos, facing, kind, rankLabel, seed);
    }

    @Override
    public String rankOfLevel(int level) {
        return ArmsHomes.rankOfLevel(level).label;
    }

    @Override
    public java.util.List<ArmsApi.VillageOffer> villagePatterns(ServerPlayer p, long seed, int landLevel) {
        return ArmsStands.villagePatterns(p, seed, landLevel);
    }

    @Override
    public void villageBought(ServerPlayer p, String style) {
        ArmsStands.villageBought(p, style);
    }

    @Override
    public boolean placeHaleRack(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos hale) {
        return ArmsStands.placeHaleRack(level, hale);
    }

    @Override
    public void lordSpoils(ServerPlayer p, String rankLabel, String themeId) {
        ArmsSpoils.lordKill(p, rankLabel, themeId);
    }

    @Override
    public void lordWields(LivingEntity lord) {
        ArmsSpoils.wield(lord);
    }

    @Override
    public boolean issueTaken(ServerPlayer p) {
        migrate(p);
        return ArmsProgress.of(p).issued;
    }

    @Override
    public void openRack(ServerPlayer p) {
        ArmsArmoury.open(p, ArmsArmoury.HALE);
    }

    @Override
    public void openIssue(ServerPlayer p) {
        OFFERED.put(p.getUUID(), p.level().getGameTime());
        AwakenNet.send(p, new ArmsPayloads.IssueOffer(OPTIONS));
    }

    /** The pick: the finished Wildhide weapon, its Blueprint and a spare Fang, once. */
    static void onPick(ServerPlayer p, String style) {
        Long at = OFFERED.get(p.getUUID());
        if (at == null || p.level().getGameTime() - at > OFFER_TICKS || !OPTIONS.contains(style) || !INSTANCE.issuePending(p)) return;
        OFFERED.remove(p.getUUID());
        Item weapon = ArmsItems.item("wildhide_" + style);
        Item fang = BuiltInRegistries.ITEM.getValue(Awaken.id("wildhide_blade"));
        if (weapon == null || fang == net.minecraft.world.item.Items.AIR) return;
        ArmsProgress g = ArmsProgress.of(p);
        g.issued = true;
        p.setData(ArmsContent.PROGRESS, g);
        giveItem(p, new ItemStack(weapon));
        giveItem(p, new ItemStack(fang));
        if (!INSTANCE.learn(p, style, "the Association's issue")) {
            Item bp = ArmsItems.blueprintItem(style);
            if (bp != null && !ArmsForge.owns(p, style)) giveItem(p, new ItemStack(bp));
        }
        Archetypes.Loaded st = Archetypes.get(style);
        ArmsNotices.reward(p, "weapon/hale/issue", "Weapon issued", new ItemStack(weapon),
                "Hale gave you this weapon, its crafting pattern, and a spare Fang blade.",
                java.util.List.of("To make another, use the pattern at a crafting table with its materials. You keep the pattern.",
                        "Crouch and talk to Hale to see his other styles and patterns."));
        ArmsApi.say(p, "Hale: Good choice. That one's yours. I've put its pattern in your pack too, so you can make more of them later. "
                + "The spare blade is a Fang, and everything you forge starts from one. When you want a different style, crouch and talk to me, and I'll show you the rack. "
                + "I sell the patterns for essence, and the more you've done, the more I'll show you.");
        Progression.markNewThing(p, "issue");
    }

    // ---- essence never dries up ---------------------------------------------------------------------------------------

    /** The no-drought rule: the fifth kill in a row of an awakened creature that left no shard drops one (E and D only). */
    static void onDrops(LivingDropsEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        LivingEntity dead = e.getEntity();
        if (MobRole.of(dead).role() != MobRole.Role.NORMAL) return;
        ArmsProgress g = ArmsProgress.of(p);
        boolean gave = false;
        for (ItemEntity d : e.getDrops()) if (d.getItem().is(CoreApi.ESSENCE_SHARD.get())) gave = true;
        if (gave) {
            g.shardMisses = 0;
        } else if (Progression.rank(p).ordinal() <= Rank.D.ordinal() && ++g.shardMisses >= DROUGHT) {
            g.shardMisses = 0;
            e.getDrops().add(new ItemEntity(dead.level(), dead.getX(), dead.getY() + 0.5, dead.getZ(), new ItemStack(CoreApi.ESSENCE_SHARD.get())));
        }
        p.setData(ArmsContent.PROGRESS, g);
    }

    /** Once, the first time a shard is in the pack: what it is. */
    static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || (p.tickCount + p.getId()) % 40 != 0) return;
        if (Progression.profile(p).counter(SHARD_CARD) > 0 || !Progression.profile(p).awakened()) return;
        if (!p.getInventory().contains(s -> s.is(CoreApi.ESSENCE_SHARD.get()))) return;
        Progression.setCounter(p, SHARD_CARD, 1);
        dev.awaken.core.api.NoticeEvents.tell(p, new dev.awaken.core.api.NoticeEvents.Event("item/essence_shard", "Material found", "Essence Shard",
                "You spend these on weapons, armour and Hale's patterns.",
                java.util.List.of("Awakened creatures drop them. Night hunters drop about one for every two you kill.",
                        "Recipes and Hale's rack show how many you need."), dev.awaken.core.api.NoticeEvents.Kind.DISCOVERY, 0xE2B450));
    }

    static @Nullable String optionName(String style) {
        Archetypes.Loaded st = Archetypes.get(style);
        return st == null ? null : st.def().name();
    }
}
