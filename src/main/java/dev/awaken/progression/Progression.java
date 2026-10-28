package dev.awaken.progression;

import dev.awaken.core.api.NoticeEvents.Kind;

import dev.awaken.Awaken;
import dev.awaken.core.AwakenConfig;
import dev.awaken.core.CoreModule;
import dev.awaken.core.DirectorLog;
import dev.awaken.progression.content.Content;
import dev.awaken.progression.content.Defs;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Public API for levels, stats, ranks, counters, Truths, Titles, Latents, Paths and perks (SPEC section 4). */
public final class Progression {
    private Progression() {}

    /**
     * Where XP came from (logging and the Day's End card). Repeatable world XP goes through {@link #worldXp}, which
     * applies diminishing returns per source; DISCOVERY is {@link #discover}; ANOMALY is for the anomaly director.
     */
    public enum XpSource { GATE_KILL, GATE_FLOOR, GATE_BOSS, HOME, REGIMEN, DIRECTIVE, TRUTH, DESCENT, DREAM, STORY, COMMAND,
        DISCOVERY, WORLD, ANOMALY }

    private static final Set<ServerPlayer> DIRTY = new HashSet<>();
    /** Counter-only changes sync less often: the client shows counters only in progress bars. */
    private static final Set<ServerPlayer> DIRTY_LAZY = new HashSet<>();

    public static Profile profile(Player player) {
        return player.getData(ProgressionModule.PROFILE.get());
    }

    public static Rank rank(Player player) {
        return profile(player).rank();
    }

    public static int stat(Player player, Stat stat) {
        return profile(player).stat(stat);
    }

    public static int contentLevel(Rank rank) {
        return rank.contentLevel;
    }

    /** The action prefix of a card whose yes opens the band ("band:ascent"). */
    public static final String BAND_ACTION = "band";
    /** {@link #openBand(ServerPlayer, int)}'s page for the ascent: Powers, scrolled to the Ascent (SPEC §30). */
    public static final int BAND_PAGE_ASCENT = -1;

    /** Opens the band (its Status page) on this player's client. */
    public static void openBand(ServerPlayer player) {
        openBand(player, 0);
    }

    /** Opens the band on a page (the rail's order, 0 Status; {@link #BAND_PAGE_ASCENT} for the Ascent). */
    public static void openBand(ServerPlayer player, int page) {
        dev.awaken.core.AwakenNet.send(player, new dev.awaken.progression.net.ProgressionPayloads.OpenBand(page));
    }

    /** Queue a sync of the profile to its owner (batched once per second). */
    public static void markDirty(ServerPlayer player) {
        DIRTY.add(player);
    }

    static void flushDirty() {
        for (ServerPlayer p : DIRTY) {
            if (!p.isRemoved()) p.syncData(ProgressionModule.PROFILE.get());
            DIRTY_LAZY.remove(p);
        }
        DIRTY.clear();
    }

    static void flushLazy() {
        for (ServerPlayer p : DIRTY_LAZY) {
            if (!p.isRemoved()) p.syncData(ProgressionModule.PROFILE.get());
        }
        DIRTY_LAZY.clear();
    }

    static void markLazy(ServerPlayer player) {
        if (!DIRTY.contains(player)) DIRTY_LAZY.add(player);
    }

    static void forget() {
        DIRTY.clear();
        DIRTY_LAZY.clear();
    }

    public static void syncNow(ServerPlayer player) {
        DIRTY.remove(player);
        player.syncData(ProgressionModule.PROFILE.get());
    }

    // ---- XP and levels ------------------------------------------------------------------------------------------------

    /**
     * Grants XP (scaled by the story length setting and XP perks) and handles level-ups. Returns levels gained.
     * Before awakening nothing is lost: the XP is banked in the profile and paid when the player awakens.
     */
    public static int grantXp(ServerPlayer player, double amount, XpSource source) {
        return grant(player, amount, source, null);
    }

    /** {@link #grantXp}, with world XP's own source key for the ledger ({@link XpLedger}). */
    private static int grant(ServerPlayer player, double amount, XpSource source, @org.jspecify.annotations.Nullable String worldKey) {
        Profile p = profile(player);
        if (amount <= 0 || !Double.isFinite(amount)) return 0;
        if (!p.awakened) {
            p.bankedXp += amount; // raw: the scale and perks apply when it's paid
            markLazy(player);
            return 0;
        }
        amount *= xpScale(player) * (1 + perk(player, Perks.XP_BONUS));
        xpLedger(player).add(source, worldKey, amount);
        int gained = addXp(player, p, amount);
        for (XpListener l : XP_LISTENERS) l.granted(player, amount, source, worldKey);
        return gained;
    }

    /** Every XP grant to an awakened player, after scaling (SPEC §21.3: the Living Wilds' XP stream). */
    @FunctionalInterface
    public interface XpListener {
        void granted(ServerPlayer player, double xp, XpSource source, @org.jspecify.annotations.Nullable String worldKey);
    }

    private static final List<XpListener> XP_LISTENERS = new ArrayList<>();

    /** Listens to every grant (after the story-length scale and perks), in the order they happen. */
    public static void onXpGranted(XpListener listener) {
        XP_LISTENERS.add(listener);
    }

    /** Where this player's XP came from so far, by source (pacing telemetry). */
    public static XpLedger xpLedger(Player player) {
        return player.getData(ProgressionModule.XP_LEDGER.get());
    }

    /**
     * Raises an awakened player to at least {@code level} with exactly the XP missing (no scaling), through the normal
     * level-ups. The story's Accept uses it: waking is worth level 2 even if the prologue banked little.
     */
    public static int topUpToLevel(ServerPlayer player, int level, XpSource source) {
        Profile p = profile(player);
        if (!p.awakened || p.level >= level) return 0;
        double missing = -p.xp;
        for (int lv = p.level; lv < level; lv++) missing += Curve.xpToNext(lv);
        DirectorLog.log(player, "progression", "topped up to level " + level + " (" + Math.round(missing) + " XP, " + source + ")");
        return addXp(player, p, missing + 1e-6);
    }

    private static int addXp(ServerPlayer player, Profile p, double amount) {
        p.xp += amount;
        int gained = 0;
        while (p.xp >= Curve.xpToNext(p.level)) {
            p.xp -= Curve.xpToNext(p.level);
            p.level++;
            p.unspent += Curve.POINTS_PER_LEVEL;
            gained++;
        }
        if (gained > 0) onLevelUp(player, p, gained);
        markDirty(player);
        return gained;
    }

    /** Grants a fraction of the player's current level (used by rewards: "0.25 levels"). */
    private static final String LEVEL_HOW = "Spend them on Status, where each stat says what it does.";
    private static final String LEVEL_NEW = "Anything this level unlocks has its own entry in your Journal.";

    public static int grantLevels(ServerPlayer player, double fraction, XpSource source) {
        return grantXp(player, fraction * Curve.xpToNext(profile(player).level) / xpScale(player), source);
    }

    /**
     * Global XP scale: the config multiplier and the Ascension tier this player meets (+15% per tier, up to 10 tiers;
     * the world's tier, never above their own: SPEC "Together").
     */
    static double xpScale(ServerPlayer player) {
        int tier = dev.awaken.core.api.StoryHooks.ascensionOf(player);
        return AwakenConfig.xpMultiplier() * (1 + 0.15 * tier);
    }

    private static final List<java.util.function.Consumer<ServerPlayer>> AWAKENED_LISTENERS = new ArrayList<>();
    private static final List<java.util.function.BiConsumer<ServerPlayer, Integer>> LEVEL_LISTENERS = new ArrayList<>();

    /** Called once, right after a player awakens (the story's Accept, a skip, or a command). */
    public static void onAwakened(java.util.function.Consumer<ServerPlayer> listener) {
        AWAKENED_LISTENERS.add(listener);
    }

    /** Called for every level reached (once per level, in order), after the level-up itself. */
    public static void onLevelReached(java.util.function.BiConsumer<ServerPlayer, Integer> listener) {
        LEVEL_LISTENERS.add(listener);
    }

    /**
     * A first: a creature, place, landmark or event this player has never met. Grants {@code fractionOfLevel} of a
     * level the first time only (counter {@code awaken:discovered/<key>}) and returns whether it was new. No
     * diminishing returns: discovery is the point. Before awakening it is banked. Suggested sizes (tools/sim):
     * a creature, biome or anomaly kind 0.1–0.15, a landmark or named beast 0.25.
     */
    public static boolean discover(ServerPlayer player, String key, double fractionOfLevel) {
        String id = "awaken:discovered/" + key;
        if (profile(player).counter(id) > 0) return false;
        setCounter(player, id, 1);
        count(player, "awaken:discoveries");
        if (fractionOfLevel > 0) grantLevels(player, fractionOfLevel, XpSource.DISCOVERY);
        return true;
    }

    /**
     * Repeatable world XP (kills, mining, building, or any module's own source key) with diminishing returns per
     * source per ~10 minutes ({@link XpBudget}). {@code fractionOfLevel} is the full-value grant; returns what was
     * actually granted. Out-levelled content should be tapered by the caller with {@link XpBudget#taper}.
     */
    public static double worldXp(ServerPlayer player, String source, double fractionOfLevel) {
        return worldXp(player, source, fractionOfLevel, 1.0);
    }

    /**
     * {@link #worldXp(ServerPlayer, String, double)} with {@code afterBudget} applied to what the budget pays (a taper or
     * a bonus by level difference: SPEC §21.3), so a grind still levels off at one budget per 10 minutes. Returns the
     * fraction of a level granted.
     */
    public static double worldXp(ServerPlayer player, String source, double fractionOfLevel, double afterBudget) {
        double paid = XpBudget.pay(player.getUUID(), source, fractionOfLevel, player.level().getGameTime()) * Math.max(0, afterBudget);
        if (paid > 0) grant(player, paid * Curve.xpToNext(profile(player).level) / xpScale(player), XpSource.WORLD, source);
        return paid;
    }

    /** Whether the level road has granted this flag ("awaken:veil", "awaken:meditation", ...). Server and client. */
    public static boolean hasFlag(Player player, String flag) {
        return LevelRoad.hasFlag(profile(player), flag);
    }

    private static void onLevelUp(ServerPlayer player, Profile p, int gained) {
        dev.awaken.core.Playtest.log(player, "level_up", "level", p.level);
        // the Living Wilds shows its own moment (and its own sound) when it can; otherwise the toast as ever (SPEC §21.1)
        boolean moment = dev.awaken.core.api.LivingHooks.levelMoment(player, p.level, gained);
        if (!moment) player.level().playSound(null, player.blockPosition(), CoreModule.LEVEL_UP.get(), SoundSource.PLAYERS, 0.9f, 1.0f);
        var levelEvent = new dev.awaken.core.api.NoticeEvents.Event("level/" + p.level, "Level gained", "Level " + p.level,
                "You have " + gained * Curve.POINTS_PER_LEVEL + " new stat points to spend on Status.",
                gained > 1 ? List.of("You went from level " + (p.level - gained) + " to " + p.level + " in one go.", LEVEL_HOW, LEVEL_NEW) : List.of(LEVEL_HOW, LEVEL_NEW),
                Kind.PERMANENT, 0x86C9F0);
        if (moment) dev.awaken.core.api.NoticeEvents.remember(player, levelEvent);
        else dev.awaken.core.api.NoticeEvents.tell(player, levelEvent);
        if (p.unspent > 0) dev.awaken.core.api.TipHooks.offer(player, dev.awaken.Awaken.id("first/level")); // where points go, and how you grow (SPEC §27.7)
        // after the level toast, so each level's unlock (the level road) reads as its consequence
        int top = p.level;
        for (int lv = top - gained + 1; lv <= top; lv++) {
            for (var l : LEVEL_LISTENERS) l.accept(player, lv);
        }
        Rank next = p.rank.next();
        if (next != null && !p.reassessmentReady && p.level >= next.levelRequired) {
            p.reassessmentReady = true;
            Signal.send(player, SignalWindow.builder(SignalWindow.Kind.REASSESSMENT, Component.literal("Reassessment available"))
                    .body(Component.literal("You have outgrown " + p.rank.label + "-rank. Take the trial at the Spire, or at any Reassessment Monolith."))
                    .footer(Component.literal("Next rank: " + next.label)).ttl(20 * 10).priority(5).build());
        }
        evaluate(player, Counters.LEVEL);
    }

    // ---- stats ------------------------------------------------------------------------------------------------------

    public static boolean spendPoint(ServerPlayer player, Stat stat) {
        Profile p = profile(player);
        if (p.unspent <= 0) return false;
        dev.awaken.core.api.TipHooks.done(player, dev.awaken.Awaken.id("first/level"));
        p.unspent--;
        p.stats[stat.ordinal()]++;
        applyAttributes(player);
        evaluate(player, Counters.STATS);
        markDirty(player);
        return true;
    }

    /**
     * Spends points placed on the band and confirmed in one go ("STR:1,AGI:2", SPEC §30.3): each as {@link #spendPoint},
     * so never more than you have; unknown stats and nonsense are skipped. Returns how many were spent.
     */
    public static int spendPoints(ServerPlayer player, String spec) {
        int spent = 0;
        for (String part : spec.split(",", 12)) {
            String[] kv = part.split(":", 2);
            if (kv.length != 2) continue;
            Stat stat;
            int n;
            try {
                stat = Stat.valueOf(kv[0].trim());
                n = Math.min(500, Integer.parseInt(kv[1].trim()));
            } catch (IllegalArgumentException e) {
                continue;
            }
            for (int i = 0; i < n && spendPoint(player, stat); i++) spent++;
        }
        return spent;
    }

    public static void addStat(ServerPlayer player, Stat stat, int amount) {
        Profile p = profile(player);
        p.stats[stat.ordinal()] += amount;
        applyAttributes(player);
        evaluate(player, Counters.STATS);
        markDirty(player);
    }

    private static final Identifier MOD_STR = Awaken.id("stat_str");
    private static final Identifier MOD_AGI_SPEED = Awaken.id("stat_agi_speed");
    private static final Identifier MOD_AGI_ATTACK = Awaken.id("stat_agi_attack");
    private static final Identifier MOD_VIT = Awaken.id("stat_vit");
    private static final Identifier MOD_PERK_SPEED = Awaken.id("perk_speed");
    private static final Identifier MOD_PERK_HEALTH = Awaken.id("perk_health");
    private static final Identifier MOD_FRACTURE = Awaken.id("fracture");

    /** Re-applies stat attribute modifiers. Call after any stat, perk or fracture change, and on login/respawn. */
    public static void applyAttributes(ServerPlayer player) {
        Profile p = profile(player);
        int str = p.stats[Stat.STR.ordinal()] - Stat.START;
        int agi = p.stats[Stat.AGI.ordinal()] - Stat.START;
        int vit = p.stats[Stat.VIT.ordinal()] - Stat.START;
        boolean fractured = p.fractureUntil > player.level().getGameTime();
        double strain = player.getData(ProgressionModule.STRAIN.get());
        double weaken = (fractured ? 0.15 : 0) + strain;
        modify(player, Attributes.ATTACK_DAMAGE, MOD_STR, p.awakened ? str * 0.025 - weaken : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(player, Attributes.MOVEMENT_SPEED, MOD_AGI_SPEED, p.awakened ? Math.min(0.40, agi * 0.004) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(player, Attributes.ATTACK_SPEED, MOD_AGI_ATTACK, p.awakened ? Math.min(0.60, agi * 0.01) : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(player, Attributes.MAX_HEALTH, MOD_VIT, p.awakened ? vit * 0.6 : 0, AttributeModifier.Operation.ADD_VALUE);
        modify(player, Attributes.MOVEMENT_SPEED, MOD_PERK_SPEED, perk(player, Perks.MOVE_SPEED), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        modify(player, Attributes.MAX_HEALTH, MOD_PERK_HEALTH, perk(player, Perks.MAX_HEALTH), AttributeModifier.Operation.ADD_VALUE);
        modify(player, Attributes.MOVEMENT_SPEED, MOD_FRACTURE, fractured ? -0.1 : 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void modify(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                               Identifier id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        var existing = inst.getModifier(id);
        if (Math.abs(amount) < 1e-9) {
            if (existing != null) inst.removeModifier(id);
            return;
        }
        if (existing != null && existing.amount() == amount && existing.operation() == op) return;
        // Permanent so max-health bonuses survive a reload: health is clamped to max on load before we re-apply.
        inst.removeModifier(id);
        inst.addPermanentModifier(new AttributeModifier(id, amount, op));
    }

    // ---- counters ---------------------------------------------------------------------------------------------------

    private static final java.util.Set<String> LOGGED = java.util.Set.of(Counters.GATES_CLEARED, Counters.FLOORS_CLEARED,
            "awaken:bosses_killed", Counters.LUCID_DREAMS, Counters.DREAMS, Counters.ECHOES_BOUND, Counters.MACHINES_BUILT,
            "awaken:waystones_lit", Counters.NIGHTMARES_BEATEN, "awaken:gates_scouted", Counters.SECRETS_FOUND);

    public static void count(ServerPlayer player, String counter, long amount) {
        if (amount == 0) return;
        Profile p = profile(player);
        p.counters.merge(counter, amount, Long::sum);
        if (LOGGED.contains(counter)) dev.awaken.core.Playtest.log(player, "counter", counter, p.counter(counter));
        evaluate(player, counter);
        markLazy(player);
    }

    public static void count(ServerPlayer player, String counter) {
        count(player, counter, 1);
    }

    /** Sets a counter (for streaks and records). */
    public static void setCounter(ServerPlayer player, String counter, long value) {
        Profile p = profile(player);
        if (p.counter(counter) == value) return;
        p.counters.put(counter, value);
        evaluate(player, counter);
        markLazy(player);
    }

    /** Raises a "best" counter if the new value is higher. Returns true when it's a new record. */
    public static boolean record(ServerPlayer player, String counter, long value) {
        if (value <= profile(player).counter(counter)) return false;
        setCounter(player, counter, value);
        return true;
    }

    // ---- condition evaluation ---------------------------------------------------------------------------------------

    static void evaluate(ServerPlayer player, String counter) {
        Profile p = profile(player);
        if (!p.awakened) return;
        Set<Identifier> ids = Content.INDEX.get(counter);
        if (ids != null) {
            for (Identifier id : List.copyOf(ids)) {
                String key = id.toString();
                Defs.TruthDef truth = Content.TRUTHS.get(id);
                if (truth != null) {
                    if (p.truthsDeclared.contains(key) && !p.truthsTrue.contains(key) && truth.condition().test(p, null)) {
                        makeTruthTrue(player, key, truth);
                    } else if (truth.emergent().isPresent() && !p.truthsTrue.contains(key) && !p.truthsOffered.contains(key)
                            && !p.truthsDeclared.contains(key) && truth.emergent().get().test(p, null)) {
                        p.truthsOffered.add(key);
                        ProgressionNotices.tell(player, "truth/offered/" + key, "Truth available", truth.text(),
                                "You can declare this Truth on the Truths page. Its reward comes once you make it true.", Kind.DISCOVERY,
                                truth.hint().orElse("Its entry on the Truths page says what it takes."), "Reward if you do: " + RewardWords.describe(truth.reward()) + ".");
                    }
                }
                Defs.TitleDef title = Content.TITLES.get(id);
                if (title != null && !p.titles.contains(key) && title.condition().test(p, null)) {
                    p.titles.add(key);
                    markNewThing(player, "title");
                    ProgressionNotices.tell(player, "title/" + key, "Title earned", title.name(), title.description(), Kind.PERMANENT,
                            title.perks().isEmpty() ? new String[]{"It has no perks. Equip it on the Truths page if you'd like to wear it."}
                                    : new String[]{"Equip it on the Truths page for its perks: " + RewardWords.perks(title.perks()) + ".",
                                            "Its perks only work while it's equipped."});
                }
                Defs.LatentDef latent = Content.LATENTS.get(id);
                if (latent != null && !p.latents.contains(key) && latent.condition().test(p, null)) {
                    p.latents.add(key);
                    markNewThing(player, "latent");
                    ProgressionNotices.tell(player, "latent/" + key, "Ability awakened", latent.name(), latent.description(), Kind.PERMANENT,
                            "It's yours for good and works on its own, with no title to equip.", "Reward: " + RewardWords.describe(latent.reward()) + ".");
                    RewardHooks.apply(player, latent.reward(), XpSource.TRUTH);
                    applyAttributes(player);
                }
            }
        }
        Directives.evaluate(player, p);
        Regimen.evaluate(player, p);
    }

    private static void makeTruthTrue(ServerPlayer player, String key, Defs.TruthDef truth) {
        Profile p = profile(player);
        p.truthsTrue.add(key);
        p.truthsDeclared.remove(key);
        markNewThing(player, "truth");
        player.level().playSound(null, player.blockPosition(), CoreModule.RANK_UP.get(), SoundSource.PLAYERS, 0.6f, 1.4f);
        ProgressionNotices.tell(player, "truth/true/" + key, "Truth fulfilled", truth.text(),
                "You made it true. Its reward and perks are yours for good.", Kind.PERMANENT,
                "Reward: " + RewardWords.describe(truth.reward()) + ".", "Its perks work on their own; they don't take a title slot.");
        RewardHooks.apply(player, truth.reward(), XpSource.TRUTH);
        applyAttributes(player);
        markDirty(player);
    }

    // ---- truths -----------------------------------------------------------------------------------------------------

    /** Offers up to {@code count} new Truths fitting the player's rank (first at level 5 via the level road, then at each rank-up). */
    public static void offerTruths(ServerPlayer player, int count) {
        Profile p = profile(player);
        var rnd = player.getRandom();
        List<Map.Entry<Identifier, Defs.TruthDef>> pool = new ArrayList<>();
        for (var e : Content.TRUTHS.map.entrySet()) {
            String key = e.getKey().toString();
            var d = e.getValue();
            if (d.emergent().isPresent()) continue;
            if (p.truthsTrue.contains(key) || p.truthsDeclared.contains(key) || p.truthsOffered.contains(key)) continue;
            if (p.rank.ordinal() < d.minRank().ordinal() || p.rank.ordinal() > d.maxRank().ordinal()) continue;
            pool.add(e);
        }
        for (int n = 0; n < count && !pool.isEmpty(); n++) {
            int total = pool.stream().mapToInt(e -> e.getValue().weight()).sum();
            int roll = rnd.nextInt(Math.max(1, total));
            for (int k = 0; k < pool.size(); k++) {
                roll -= pool.get(k).getValue().weight();
                if (roll < 0) {
                    p.truthsOffered.add(pool.remove(k).getKey().toString());
                    break;
                }
            }
        }
        if (!p.truthsOffered.isEmpty()) {
            // a long-term goal, not a quest: a calm toast; each Truth's reward is on the Truths tab
            Signal.send(player, SignalWindow.builder(SignalWindow.Kind.TRUTH, Component.literal("Truths: who are you becoming?"))
                    .body(Component.literal("Declare any you like. No rush · K"))
                    .presentation(SignalWindow.Presentation.TOAST).ttl(20 * 8).build());
        }
        markDirty(player);
    }

    public static boolean declareTruth(ServerPlayer player, String key) {
        Profile p = profile(player);
        if (!p.truthsOffered.contains(key) || p.truthsDeclared.size() >= 3) return false;
        Defs.TruthDef d = Content.TRUTHS.get(key);
        if (d == null) return false;
        p.truthsOffered.remove(key);
        p.truthsDeclared.add(key);
        if (d.condition().test(p, null)) makeTruthTrue(player, key, d);
        markDirty(player);
        return true;
    }

    public static boolean undeclareTruth(ServerPlayer player, String key) {
        Profile p = profile(player);
        if (!p.truthsDeclared.remove(key)) return false;
        p.truthsOffered.add(key);
        markDirty(player);
        return true;
    }

    // ---- titles -----------------------------------------------------------------------------------------------------

    public static boolean equipTitle(ServerPlayer player, String key) {
        Profile p = profile(player);
        if (!key.isEmpty() && !p.titles.contains(key)) return false;
        p.title = key;
        applyAttributes(player);
        markDirty(player);
        return true;
    }

    // ---- perks ------------------------------------------------------------------------------------------------------

    /** Sum of a perk across true Truths, the equipped Title, Latents and the Path. Cached per tick per player. */
    public static double perk(Player player, String perkId) {
        Profile p = profile(player);
        double sum = 0;
        for (String t : p.truthsTrue) {
            var d = Content.TRUTHS.get(t);
            if (d != null) for (Perk k : d.reward().perks()) if (k.id().equals(perkId)) sum += k.amount();
        }
        if (!p.title.isEmpty()) {
            var d = Content.TITLES.get(p.title);
            if (d != null) for (Perk k : d.perks()) if (k.id().equals(perkId)) sum += k.amount();
        }
        for (String l : p.latents) {
            var d = Content.LATENTS.get(l);
            if (d != null) for (Perk k : d.reward().perks()) if (k.id().equals(perkId)) sum += k.amount();
        }
        if (!p.path.isEmpty()) {
            var d = Content.PATHS.get(p.path);
            if (d != null) for (Perk k : d.perks()) if (k.id().equals(perkId)) sum += k.amount();
        }
        PerkSource[] sources = perkSources;
        for (int i = 0; i < sources.length; i++) {
            try {
                sum += sources[i].perk(player, perkId);
            } catch (RuntimeException e) {
                dev.awaken.Awaken.LOG.warn("[progression] a perk source failed: {}", e.toString());
            }
        }
        return sum;
    }

    /** Something outside progression that grants perks (worn gear, a machine nearby). Called on both sides. */
    @FunctionalInterface
    public interface PerkSource {
        /** This source's amount of {@code perkId} for the player right now (0 if none). Keep it cheap: no allocation. */
        double perk(Player player, String perkId);
    }

    private static volatile PerkSource[] perkSources = new PerkSource[0];

    /**
     * Adds a perk source: its amounts are summed into {@link #perk} with Truths, the Title, Latents and the Path, so
     * every reader of a perk (energy regen, cooldowns, cord length...) sees it with no other change.
     */
    public static synchronized void addPerkSource(PerkSource source) {
        PerkSource[] next = java.util.Arrays.copyOf(perkSources, perkSources.length + 1);
        next[next.length - 1] = source;
        perkSources = next;
    }

    public static boolean hasPerk(Player player, String perkId) {
        return perk(player, perkId) > 0;
    }

    // ---- rhythm -----------------------------------------------------------------------------------------------------

    /** Something new to use or show off landed. Feeds the second reward rhythm (SPEC section 2). */
    private static final List<java.util.function.BiConsumer<ServerPlayer, String>> NEW_THING_LISTENERS = new ArrayList<>();

    /** Listen for "new thing" moments (story uses this for the Signal's first-time lines). */
    public static void onNewThing(java.util.function.BiConsumer<ServerPlayer, String> listener) {
        NEW_THING_LISTENERS.add(listener);
    }

    public static void markNewThing(ServerPlayer player, String what) {
        dev.awaken.core.Playtest.log(player, "new_thing", "what", what);
        for (var l : NEW_THING_LISTENERS) l.accept(player, what);
        profile(player).lastNewThing = player.level().getGameTime();
        if (capability(what)) profile(player).counters.put(CAPABILITY_AT, player.level().getGameTime());
        DirectorLog.log(player, "new-thing", what);
        markDirty(player);
    }

    /**
     * The capability clock (SPEC §27.9): the game time of the last new thing the player can *do* (a power learned, a
     * rank, a Path, a rung of the Ascent, a Lord-forged weapon, a first Weave, a machine or blueprint, an Echo, a Hearth
     * or its tier, a latent, a townsperson). The new-thing clock above also counts sights (biomes, craft levels,
     * happenings), so it never noticed a player starved of new abilities.
     */
    public static final String CAPABILITY_AT = "awaken:capability_at";

    static boolean capability(String what) {
        if (what.startsWith("power core")) return false;
        String kind = what.split("[ :/]", 2)[0];
        return switch (kind) {
            case "power", "rank", "path", "ascent", "lord-forged", "weave", "machine", "blueprint", "echo", "hearth", "hearth_tier",
                 "latent", "townsfolk" -> true;
            default -> false;
        };
    }

    /** Minutes of play since the last new thing. */
    public static double minutesSinceNewThing(ServerPlayer player) {
        return (player.level().getGameTime() - profile(player).lastNewThing) / 1200.0;
    }

    // ---- awakening, rank, path --------------------------------------------------------------------------------------

    /**
     * The Signal chooses the player. Called by the story prologue (or /awaken admin awaken). Everything earned before
     * (the prologue's kills and floors) is paid now. Truths wait for level 5 (the level road), so the first minutes
     * aren't a list of long-term goals.
     */
    public static void awaken(ServerPlayer player) {
        Profile p = profile(player);
        if (p.awakened) return;
        p.awakened = true;
        p.lastNewThing = player.level().getGameTime();
        applyAttributes(player);
        double banked = p.bankedXp;
        p.bankedXp = 0;
        if (banked > 0) {
            dev.awaken.core.Playtest.log(player, "banked_xp_paid", "xp", Math.round(banked));
            DirectorLog.log(player, "progression", "awakened: paid " + Math.round(banked) + " banked XP");
            grantXp(player, banked, XpSource.STORY);
        }
        markDirty(player);
        for (var l : AWAKENED_LISTENERS) l.accept(player);
    }

    /** Called by the Reassessment trial when the player passes. */
    public static void passReassessment(ServerPlayer player) {
        Profile p = profile(player);
        Rank next = p.rank.next();
        if (next == null) return;
        p.rank = next;
        p.reassessmentReady = next.next() != null && p.level >= next.next().levelRequired;
        player.level().playSound(null, player.blockPosition(), CoreModule.RANK_UP.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        Signal.send(player, SignalWindow.builder(SignalWindow.Kind.REASSESSMENT, Component.literal(next.label + "-rank"))
                .body(Component.literal("Reassessment passed. The world will treat you differently now."))
                .row(Component.literal("New gates"), Component.literal(next.label + "-rank pillars will appear"))
                .priority(9).ttl(20 * 10).build());
        ProgressionNotices.remember(player, "rank/" + next.name(), "Rank advanced", next.label + "-rank",
                "You passed reassessment, so " + next.label + "-rank gates will start to appear for you.",
                "Three new Truths are waiting on the Truths page.");
        markNewThing(player, "rank");
        offerTruths(player, 3);
        evaluate(player, Counters.RANK);
        RankHooks.fire(player, next);
        markDirty(player);
    }

    /** Scores Paths from real counters and offers the top three plus any hidden Path whose condition is met. */
    public static List<PathOffer> offerPaths(ServerPlayer player) {
        Profile p = profile(player);
        double hours = Math.max(1.0, p.counter("awaken:play_ticks") / 72000.0);
        List<PathOffer> scored = new ArrayList<>();
        List<PathOffer> hidden = new ArrayList<>();
        for (var e : Content.PATHS.map.entrySet()) {
            var d = e.getValue();
            if (!d.available()) continue;
            if (d.hidden().isPresent()) {
                if (d.hidden().get().test(p, null)) hidden.add(new PathOffer(e.getKey().toString(), 999, evidence(p, d)));
                continue;
            }
            double score = 0;
            for (var w : d.scoring().entrySet()) score += w.getValue() * p.counter(w.getKey()) / hours;
            List<String> ev = evidence(p, d);
            if (ev.isEmpty()) continue; // never offer a Path without real evidence
            scored.add(new PathOffer(e.getKey().toString(), score, ev));
        }
        scored.sort(Comparator.comparingDouble(PathOffer::score).reversed());
        List<PathOffer> out = new ArrayList<>(scored.subList(0, Math.min(3, scored.size())));
        out.addAll(hidden);
        p.pathsOffered.clear();
        out.forEach(o -> p.pathsOffered.add(o.path()));
        DirectorLog.log(player, "path-offer", out.toString());
        markDirty(player);
        return out;
    }

    public record PathOffer(String path, double score, List<String> evidence) {}

    private static List<String> evidence(Profile p, Defs.PathDef d) {
        List<String> out = new ArrayList<>();
        for (var e : d.evidence().entrySet()) {
            long v = p.counter(e.getKey());
            if (v > 0) out.add(String.format(e.getValue(), v));
        }
        return out;
    }

    public static boolean choosePath(ServerPlayer player, String path) {
        Profile p = profile(player);
        if (!p.pathsOffered.contains(path)) return false;
        Defs.PathDef d = Content.PATHS.get(path);
        if (d == null) return false;
        p.path = path;
        p.pathsOffered.clear();
        d.signaturePower().ifPresent(pw -> RewardHooks.learnPower(player, pw));
        ProgressionNotices.remember(player, "path/" + path, "Path chosen", d.name(), d.description(),
                d.perks().isEmpty() ? "It has no perks of its own." : "Its perks: " + RewardWords.perks(d.perks()) + ".",
                "If it comes with a power, that power has its own entry, and you can put it on a key on the Powers page.");
        markNewThing(player, "path");
        Signal.send(player, SignalWindow.builder(SignalWindow.Kind.REVELATION, Component.literal(d.name()))
                .body(Component.literal(d.description())).priority(9).ttl(20 * 12).build());
        applyAttributes(player);
        markDirty(player);
        return true;
    }

    // ---- respec and directive slots (used by the Above's Recalibrator and Signal Relay) ----------------------------

    /** Refunds every spent stat point and resets stats to {@link Stat#START}. Returns points refunded. */
    public static int respec(ServerPlayer player) {
        Profile p = profile(player);
        int refund = 0;
        for (Stat s : Stat.values()) {
            refund += Math.max(0, p.stats[s.ordinal()] - Stat.START);
            p.stats[s.ordinal()] = Stat.START;
        }
        p.unspent += refund;
        applyAttributes(player);
        evaluate(player, Counters.STATS);
        markDirty(player);
        return refund;
    }

    private static final List<java.util.function.Predicate<ServerPlayer>> EXTRA_DIRECTIVE_SLOTS = new ArrayList<>();

    /** A module can grant +1 Directive slot while its condition holds (e.g. near a Signal Relay). */
    public static void addDirectiveSlotSource(java.util.function.Predicate<ServerPlayer> source) {
        EXTRA_DIRECTIVE_SLOTS.add(source);
    }

    static int extraDirectiveSlots(ServerPlayer player) {
        int n = 0;
        for (var s : EXTRA_DIRECTIVE_SLOTS) if (s.test(player)) n++;
        return n;
    }

    // ---- misc -------------------------------------------------------------------------------------------------------

    public static void addInsight(ServerPlayer player, String bossId) {
        profile(player).insight.merge(bossId, 1, Integer::sum);
        markDirty(player);
    }

    public static void fracture(ServerPlayer player, int ticks) {
        profile(player).fractureUntil = player.level().getGameTime() + ticks;
        applyAttributes(player);
        markDirty(player);
    }

    public static void clearFracture(ServerPlayer player) {
        profile(player).fractureUntil = 0;
        applyAttributes(player);
        markDirty(player);
    }

    /** Day's End tally: the player came out of one more gate today. */
    public static void addGateToday(ServerPlayer player) {
        Profile p = profile(player);
        if (p.day.day() < 0) return;
        p.day = new Profile.DayLog(p.day.day(), p.day.levelStart(), p.day.gates() + 1, p.day.kills(), p.day.best());
        markDirty(player);
    }

    /** For the Day's End card: remember the best moment of the day (longest/most impressive wins by score). */
    public static void bestMoment(ServerPlayer player, String moment) {
        Profile p = profile(player);
        p.day = new Profile.DayLog(p.day.day(), p.day.levelStart(), p.day.gates(), p.day.kills(), moment);
        markDirty(player);
    }

    static Map<String, Long> snapshot(Profile p, Set<String> counters) {
        Map<String, Long> m = new HashMap<>();
        for (String c : counters) m.put(c, p.counter(c));
        return m;
    }
}
