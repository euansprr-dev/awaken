package dev.awaken.arms;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.MobRole;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Rank;
import dev.awaken.signal.Signal;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The gates give the pattern (docs/arms-progression/PLAN.md §3.4): each gate has a <b>bearer</b>, a creature on its first
 * floor that wields the gate's style and glints, and its death gives every fighter nearby that style's Blueprint straight
 * into the pack (never the Satchel: a pattern is knowledge). A riftborn camp's heart is a bearer too, and the Lord's hoard
 * carries the pattern for anyone who walked past the bearer, so every gate you clear pays. A pattern you already know gives
 * an unknown one of the same rank, and when you know them all its price in essence. Once per gate or camp per fighter.
 * The people who teach early are here too (a chain's last step pays its pattern).
 */
final class ArmsPatterns {
    private ArmsPatterns() {}

    static final String BEARER = "awaken_bearer";
    static final String KEY = "awaken_bearer_key:";
    private static final int CLAIMS = 64;
    /** Creatures spawned on each gate's first floor so far; the third (or the first elite) is the bearer. */
    private static final Map<String, Integer> SEEN = new HashMap<>();
    private static final Set<String> HAS = new HashSet<>();
    private static final List<LivingEntity> BEARERS = new ArrayList<>();

    // ---- choosing the bearer ------------------------------------------------------------------------------------------

    /** A gate creature spawned: on the first floor of the main gate, the first elite or the third creature is the bearer. */
    static void gateSpawn(LivingEntity mob, String gateKey, int area, int floor, boolean elite) {
        if (area != 0 || floor != 1 || HAS.contains(gateKey)) return;
        int n = SEEN.merge(gateKey, 1, Integer::sum);
        if (elite || n >= 3) {
            HAS.add(gateKey);
            SEEN.remove(gateKey);
            markBearer(mob, gateKey);
        }
    }

    /** This creature carries the pattern of its theme, claimed once per fighter under the key (a gate or a camp). */
    static void markBearer(LivingEntity mob, String claimKey) {
        MobRole role = MobRole.of(mob);
        ArmsHomes.Home home = ArmsHomes.ofTheme(role.theme());
        if (home == null || mob.entityTags().contains(BEARER)) return;
        mob.addTag(BEARER);
        mob.addTag(KEY + claimKey);
        wield(mob, home);
        BEARERS.add(mob);
    }

    private static void wield(LivingEntity mob, ArmsHomes.Home home) {
        Item weapon = ArmsItems.item(ArmsArmoury.PREFIX[Math.min(5, home.rank().ordinal())] + "_" + home.style());
        if (weapon == null) return;
        ItemStack stack = new ItemStack(weapon);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY); // a look, never strength (SPEC §25 S0)
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        EquipmentSlot slot = mob.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty() ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        mob.setItemSlot(slot, stack);
        if (mob instanceof Mob m) m.setDropChance(slot, 0F);
    }

    /** A bearer that was saved with its gate and loaded again keeps its glint. */
    static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof LivingEntity le) || !le.entityTags().contains(BEARER)) return;
        if (!BEARERS.contains(le)) BEARERS.add(le);
    }

    /** Once a second the bearers let a few motes go, so you can tell which one it is across the room. */
    static void onServerTick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 20 != 0 || BEARERS.isEmpty()) return;
        BEARERS.removeIf(b -> !b.isAlive() || b.isRemoved());
        for (LivingEntity b : BEARERS) {
            if (b.level() instanceof ServerLevel level) level.sendParticles(ParticleTypes.END_ROD, b.getX(), b.getY() + b.getBbHeight() * 0.7, b.getZ(), 2, 0.25, 0.3, 0.25, 0.01);
        }
    }

    // ---- the reward ---------------------------------------------------------------------------------------------------

    /** The bearer fell: every fighter near it learns what it carried, once for this gate or camp. */
    static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!dead.entityTags().contains(BEARER) || !(dead.level() instanceof ServerLevel level)) return;
        BEARERS.remove(dead);
        if (!(e.getSource().getEntity() instanceof ServerPlayer) && !(dead.getKillCredit() instanceof ServerPlayer)) return;
        bearerFell(dead, level.getEntitiesOfClass(ServerPlayer.class, dead.getBoundingBox().inflate(48)));
    }

    /** What the bearer's fall gives each of these fighters (those near it, in play). */
    static void bearerFell(LivingEntity dead, List<ServerPlayer> fighters) {
        String key = null;
        for (String t : dead.entityTags()) if (t.startsWith(KEY)) key = t.substring(KEY.length());
        ArmsHomes.Home home = ArmsHomes.ofTheme(MobRole.of(dead).theme());
        if (key == null || home == null) return;
        for (ServerPlayer p : fighters) award(p, home, key, home.gate() + "'s bearer");
    }

    /** The Lord's hoard, for a fighter who never downed the bearer. */
    static void lordPattern(ServerPlayer p, String gateKey, String themeId) {
        ArmsHomes.Home home = ArmsHomes.ofTheme(themeId);
        if (home != null) award(p, home, gateKey, home.gate() + "'s Lord");
    }

    private static void award(ServerPlayer p, ArmsHomes.Home home, String key, String source) {
        if (!claimGate(p, key)) return;
        dev.awaken.progression.Progression.count(p, "awaken:patterns_from_gates"); // trail steps (docs/trails/PLAN.md §4.2)
        teach(p, home, source);
    }

    /** Once per gate or camp per fighter (the last 64 are remembered). */
    static boolean claimGate(ServerPlayer p, String key) {
        ArmsProgress g = ArmsProgress.of(p);
        Integer id = key.hashCode();
        if (g.claimedGates.contains(id)) return false;
        g.claimedGates.add(id);
        while (g.claimedGates.size() > CLAIMS) g.claimedGates.remove(0);
        p.setData(ArmsContent.PROGRESS, g);
        return true;
    }

    /** The home's style if it is new to the fighter; else an unknown one of the same rank; else its price in essence. */
    static void teach(ServerPlayer p, ArmsHomes.Home home, String source) {
        if (ArmsIssue.INSTANCE.learn(p, home.style(), source)) return;
        List<ArmsHomes.Home> unknown = new ArrayList<>();
        for (ArmsHomes.Home h : ArmsHomes.atRank(home.rank())) if (!ArmsIssue.INSTANCE.knows(p, h.style())) unknown.add(h);
        if (!unknown.isEmpty() && ArmsIssue.INSTANCE.learn(p, unknown.get(p.getRandom().nextInt(unknown.size())).style(), source)) return;
        int price = ArmsHomes.rackPrice(home.rank());
        ArmsIssue.giveItem(p, new ItemStack(CoreApi.ESSENCE_SHARD.get(), price));
        Signal.toast(p, Component.literal("PATTERN · you know every " + home.rank().label + "-rank pattern · " + price + " essence instead").withColor(0xC8C2B0));
    }

    // ---- the people who teach early -----------------------------------------------------------------------------------

    /** A chain finished: the person who teaches a style at its end pays it (once per person). */
    static void questDone(ServerPlayer p, String chain) {
        for (ArmsHomes.Home h : ArmsHomes.all()) {
            ArmsHomes.Teacher t = h.teacher();
            if (t == null || !t.chain().equals(chain)) continue;
            ArmsProgress g = ArmsProgress.of(p);
            if (!g.claimedPeople.add(t.person())) return;
            p.setData(ArmsContent.PROGRESS, g);
            dev.awaken.progression.Progression.count(p, "awaken:patterns_from_people");
            dev.awaken.core.api.ArmsApi.say(p, t.line());
            if (!ArmsIssue.INSTANCE.learn(p, h.style(), cap(t.person()) + "'s teaching")) {
                int price = ArmsHomes.rackPrice(h.rank());
                ArmsIssue.giveItem(p, new ItemStack(CoreApi.ESSENCE_SHARD.get(), price));
                Signal.toast(p, Component.literal("You already know it · " + price + " essence instead").withColor(0xC8C2B0));
            }
            return;
        }
    }

    /** A left chest opened (SPEC §43.3): the first early-found style, in file order, this fighter doesn't know yet. False when they know all of them. */
    static boolean leftChest(ServerPlayer p) {
        for (ArmsHomes.Home h : ArmsHomes.all()) {
            if (!h.leftChest() || ArmsIssue.INSTANCE.knows(p, h.style())) continue;
            if (!ArmsIssue.INSTANCE.learn(p, h.style(), "a chest someone left behind")) continue;
            dev.awaken.progression.Progression.count(p, "awaken:patterns_from_left_chests");
            return true;
        }
        return false;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---- what a card says ---------------------------------------------------------------------------------------------

    /** For a gate's card: "Daggers, carried by one creature on the first floor", or null (the style is known, or this theme teaches none). */
    static @Nullable String patternLine(ServerPlayer p, String themeId) {
        ArmsHomes.Home home = ArmsHomes.ofTheme(themeId);
        if (home == null || ArmsIssue.INSTANCE.knows(p, home.style())) return null;
        Archetypes.Loaded st = Archetypes.get(home.style());
        return (st == null ? home.style() : st.def().name()) + ", carried by one creature on the first floor";
    }

    static Rank rankOf(ArmsHomes.Home h) {
        return h.rank();
    }

    static boolean awakened(ServerPlayer p) {
        return Progression.profile(p).awakened();
    }
}
