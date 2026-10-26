package dev.awaken.progression;

import com.mojang.serialization.Codec;
import dev.awaken.Awaken;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.PlayDays;
import dev.awaken.progression.content.Content;
import dev.awaken.progression.net.ProgressionPayloads;
import dev.awaken.signal.Signal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Levels, stats, ranks, Truths, Titles, Latents, Paths, Directives, Regimen (SPEC section 4). */
public final class ProgressionModule {
    private ProgressionModule() {}

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);

    public static final Supplier<AttachmentType<Profile>> PROFILE = ATTACHMENTS.register("profile", () -> AttachmentType
            .builder(Profile::new)
            .serialize(Profile.MAP_CODEC)
            .copyOnDeath()
            .sync((holder, to) -> holder == to, Profile.STREAM_CODEC)
            .build());

    /** Where this player's XP came from, by source (SPEC §17 telemetry; read by /awaken xp, never synced). */
    public static final Supplier<AttachmentType<XpLedger>> XP_LEDGER = ATTACHMENTS.register("xp_ledger", () -> AttachmentType
            .builder(XpLedger::new)
            .serialize(XpLedger.MAP_CODEC, l -> !l.isEmpty())
            .copyOnDeath()
            .build());

    /** The locks this player has met and not opened (SPEC §27): saved, kept through death, synced to its owner. */
    public static final Supplier<AttachmentType<PromiseBook>> PROMISES = ATTACHMENTS.register("promises", () -> AttachmentType
            .builder(PromiseBook::new)
            .serialize(PromiseBook.MAP_CODEC, b -> !b.isEmpty())
            .copyOnDeath()
            .sync((holder, to) -> holder == to, PromiseBook.STREAM_CODEC)
            .build());

    /** Fraction of stats lost to the Strain of climbing (set by the Descent, decays over time). */
    public static final Supplier<AttachmentType<Double>> STRAIN = ATTACHMENTS.register("strain", () -> AttachmentType
            .builder(() -> 0.0)
            .serialize(Codec.DOUBLE.fieldOf("v"))
            .copyOnDeath()
            .build());

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(ProgressionModule::registerPayloads);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ProgressionModule::addReloadListeners);
        bus.addListener(ProgressionModule::onDatapackSync);
        bus.addListener(ProgressionModule::onLogin);
        bus.addListener(ProgressionModule::onRespawn);
        bus.addListener(ProgressionModule::onServerTick);
        bus.addListener(ProgressionModule::onPlayerTick);
        bus.addListener(ProgressionModule::onDeath);
        bus.addListener(ProgressionModule::onBreak);
        bus.addListener(ProgressionModule::onPlace);
        bus.addListener(ProgressionModule::onIncomingDamage);
        bus.addListener(ProgressionModule::onCrit);
        bus.addListener(ProgressionModule::onFall);
        bus.addListener(ProgressionModule::onWake);
        bus.addListener(ProgressionModule::onTame);
        bus.addListener((net.neoforged.neoforge.event.server.ServerStoppingEvent e) -> {
            XpBudget.forgetAll();
            ACTION_RATE.clear();
            Progression.forget();
            WorldLatents.forget();
            RecipeUnlocks.forget();
            Lessons.forgetAll();
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> Lessons.forget(e.getEntity().getUUID()));
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            // playtests: where the session's XP came from, for tools/playtest/report.py
            if (!(e.getEntity() instanceof ServerPlayer sp) || !dev.awaken.core.Playtest.enabled()) return;
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            Progression.xpLedger(sp).bySource().forEach((k, v) -> o.addProperty(k.name().toLowerCase(), Math.round(v)));
            Progression.xpLedger(sp).world().forEach((k, v) -> o.addProperty("world/" + k, Math.round(v)));
            dev.awaken.core.Playtest.log(sp, "xp_sources", o);
        });
        bus.addListener(RecipeUnlocks::onPlayerTick);
        LevelRoad.init();
        // Truths wait for level 5 (plan v2 §4.6). Players awakened before v2 already hold theirs: never a second set.
        LevelRoad.onFlag("awaken:truths", p -> {
            Profile prof = Progression.profile(p);
            if (prof.truthsOffered().isEmpty() && prof.truthsDeclared().isEmpty() && prof.truthsTrue().isEmpty()) {
                Progression.offerTruths(p, 3);
            }
        });
        ProgressionNow.register();
        PromiseKeeper.init(); // the locks you met, and the call back when you hold their key (SPEC §27)
        // "Show me" on a card (a climb site's first refusal): the band opens (SPEC §27)
        Signal.onAction(Progression.BAND_ACTION, (p, action, yes) -> {
            if (yes) Progression.openBand(p, action.endsWith(":ascent") ? Progression.BAND_PAGE_ASCENT : 0);
        });
        Lessons.init(); // teaching in play and the level road's goals (P3)
        Signal.onAction(Directives.ACTION, Directives::answer);
        PlayDays.onMorning((server, day) -> {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                startDay(p, day);
                Regimen.morning(p);
                Directives.tryOffer(p, "morning");
            }
        });
        ProgressionCommands.register();
        WorldLatents.init();
        ProgressionTests.register();
        OnboardingTests.register();
        RoadTests.register();
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(ProgressionPayloads.ContentSync.TYPE, ProgressionPayloads.ContentSync.STREAM_CODEC);
        r.playToClient(ProgressionPayloads.OpenBand.TYPE, ProgressionPayloads.OpenBand.STREAM_CODEC);
        r.playToServer(ProgressionPayloads.StatusAction.TYPE, ProgressionPayloads.StatusAction.STREAM_CODEC, (msg, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            long now = sp.level().getGameTime();
            long[] window = ACTION_RATE.computeIfAbsent(sp.getUUID(), k -> new long[]{now, 0});
            if (now - window[0] >= 20) { window[0] = now; window[1] = 0; }
            if (++window[1] > 10) return; // at most 10 Status actions a second
            switch (msg.kind()) {
                case "spend" -> {
                    try { Progression.spendPoint(sp, Stat.valueOf(msg.arg())); } catch (IllegalArgumentException ignored) {}
                }
                case "spend_many" -> Progression.spendPoints(sp, msg.arg()); // the band's Confirm (SPEC §30.3)
                case "declare" -> Progression.declareTruth(sp, msg.arg());
                case "undeclare" -> Progression.undeclareTruth(sp, msg.arg());
                case "title" -> Progression.equipTitle(sp, msg.arg());
                case "path" -> Progression.choosePath(sp, msg.arg());
                default -> {}
            }
            Progression.syncNow(sp);
        });
    }

    private static void addReloadListeners(AddServerReloadListenersEvent event) {
        for (Content.Store<?> s : Content.ALL) event.addListener(Awaken.id("progression_" + s.folder), s.loader());
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        var msg = ProgressionPayloads.ContentSync.build();
        event.getRelevantPlayers().forEach(p -> dev.awaken.core.AwakenNet.send(p, msg));
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Progression.applyAttributes(sp);
        // older saves get the level-road steps they missed (ids, not positions: nothing is granted twice)
        LevelRoad.catchUp(sp, true);
        Progression.syncNow(sp);
        startDay(sp, PlayDays.current(sp.level().getServer()));
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            Progression.applyAttributes(sp);
            if (!event.isEndConquered()) sp.setHealth(sp.getMaxHealth()); // respawn at your real maximum, not 20
            Progression.syncNow(sp);
        }
    }

    static void startDay(ServerPlayer player, long day) {
        Profile p = Progression.profile(player);
        // Weekly counters ("killed_by_week/...", used to pick nightmares) reset every seven play-days.
        long week = day / 7;
        if (p.counter("awaken:week_epoch") != week + 1) {
            p.counters.keySet().removeIf(k -> k.startsWith("awaken:killed_by_week/"));
            p.counters.put("awaken:week_epoch", week + 1);
            Progression.markDirty(player);
        }
        if (p.day.day() == day) return;
        p.day = new Profile.DayLog(day, p.level, 0, 0, "");
        Progression.markDirty(player);
    }

    // ---- ticking ----------------------------------------------------------------------------------------------------

    private static final Map<UUID, long[]> ACTION_RATE = new HashMap<>();

    private static void onServerTick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 20 == 0) Progression.flushDirty();
        if (server.getTickCount() % 100 == 0) Progression.flushLazy();
        if (server.getTickCount() % 1200 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Profile prof = Progression.profile(p);
                if (!prof.awakened()) continue;
                Progression.count(p, "awaken:play_ticks", 1200);
                if (prof.fractureUntil > 0 && prof.fractureUntil <= p.level().getGameTime()) {
                    prof.fractureUntil = 0;
                    Progression.applyAttributes(p);
                    Signal.toast(p, Component.literal("Fracture healed"));
                }
                if (Progression.minutesSinceNewThing(p) > 20 && p.getRandom().nextInt(4) == 0) {
                    Directives.tryOffer(p, "gap since last new thing");
                }
            }
        }
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 20 != 0) return;
        Profile p = Progression.profile(sp);
        if (!p.awakened()) return;
        Lessons.evaluate(sp);
        if (sp.isSprinting()) {
            double dist = Math.hypot(sp.getX() - sp.xo, sp.getZ() - sp.zo) * 20; // approx blocks/second → per second
            Progression.count(sp, Counters.SPRINT_CM, Math.round(dist * 100));
        }
        double strain = sp.getData(STRAIN.get());
        if (strain > 0) {
            double next = Math.max(0, strain - 0.05 / 600.0); // each 5% of Strain fades over 10 minutes (SPEC 8)
            sp.setData(STRAIN.get(), next);
            // re-apply in 1% steps so the penalty eases off gradually instead of vanishing at once
            if (next == 0 || (int) (next * 100) != (int) (strain * 100)) Progression.applyAttributes(sp);
        }
    }

    /**
     * Home activity XP (SPEC 4.3, v2): diminishing returns per source per ~10 minutes instead of a flat cap, tapered
     * when you've out-levelled the thing (vanilla mobs and blocks are content level 0). Before awakening it's banked.
     */
    static void homeXp(ServerPlayer player, String source, double fractionOfLevel, int contentLevel) {
        double taper = XpBudget.taper(Progression.profile(player).level(), contentLevel);
        double paid = XpBudget.pay(player.getUUID(), source, fractionOfLevel, player.level().getGameTime()) * taper;
        if (paid > 0) Progression.grantLevels(player, paid, Progression.XpSource.HOME);
    }

    /** Full-value world XP per kill of a 20-HP mob (× min(2, max health / 20)), mined ore (× its {@link OreXp} weight) and placed block. */
    static final double KILL_XP = 0.04, ORE_XP = 0.01, BLOCK_XP = 0.0005;
    /** The first of each ore is a discovery (SPEC §28.3): this share of a level, once. */
    static final double ORE_DISCOVERY = 0.1;

    /**
     * Ore XP by rarity and depth (PLAN 7.6, SPEC §28.3): each ore's weight on {@link #ORE_XP}, the content level its
     * taper starts from (an ore under y 0 is level 15's, diamond and Deepglass 25's), and the line its first find says.
     * Ores under y 0 (and Deepglass, always) pay through their own budget, {@link XpBudget#DEEP}, so a deep trip isn't
     * worn out by the coal on the way down. A lode's heart pays its own lump (the deep module).
     */
    public enum OreXp {
        COAL("Coal", 0.5, 0, 0xB8C2CE, "Fuel for the Forge's every temper"),
        COPPER("Copper", 0.7, 0, 0xFFB07A, "Bell bronze, the Tuning Fork and the miner's lamp"),
        IRON("Iron", 1.0, 0, 0xEAD2BC, "D-rank gear is bound in iron"),
        GOLD("Gold", 1.5, 0, 0xFFE27A, "The power's metal: every focus from D up needs it"),
        REDSTONE("Redstone", 1.5, 0, 0xFF7A7A, "The Precursors' circuits run on it"),
        LAPIS("Lapis", 1.5, 0, 0x7FA0FF, "Ink: Blueprints are drawn in it"),
        EMERALD("Emerald", 4.0, 0, 0x7FFFA8, "The coin of every village and road"),
        DIAMOND("Diamond", 6.0, 25, 0x8FF6FF, "C-rank gear is cut from diamond"),
        DEEPGLASS("Deepglass", 6.0, 25, 0xB48CFF, "Black glass from the Deep: B-rank's resonance, the Spirit catalyst"),
        QUARTZ("Quartz", 1.0, 0, 0xF2EEE6, "Nether stone"),
        DEBRIS("Ancient debris", 6.0, 25, 0xC9A38A, "Netherite: A-rank's metal"),
        AMETHYST("Amethyst", 0.0, 0, 0xC79BFF, "With Deepglass, B-rank's resonance");

        public final String title, hint;
        public final double weight;
        public final int contentLevel, colour;

        OreXp(String title, double weight, int contentLevel, int colour, String hint) {
            this.title = title;
            this.weight = weight;
            this.contentLevel = contentLevel;
            this.colour = colour;
            this.hint = hint;
        }

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        /** The content level an ore at this height counts as (deep ones taper later). */
        public int contentLevel(int y) {
            return Math.max(contentLevel, y < 0 ? 15 : 0);
        }

        /** Which ore a block is (vanilla's and the common tags, Deepglass by id), or null; a lode's heart is not an ore here. */
        public static OreXp of(net.minecraft.world.level.block.state.BlockState s) {
            var id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock());
            if (id.getNamespace().equals(Awaken.MODID)) {
                if (id.getPath().equals("deepglass")) return DEEPGLASS;
                if (id.getPath().endsWith("_lode_heart")) return null;
            }
            if (s.is(net.minecraft.world.level.block.Blocks.AMETHYST_CLUSTER)) return AMETHYST;
            if (s.is(net.minecraft.world.level.block.Blocks.ANCIENT_DEBRIS)) return DEBRIS;
            if (!s.is(BlockTags.ORES) && !s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES.block())) return null;
            if (s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES_DIAMOND.block())) return DIAMOND;
            if (s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES_EMERALD.block())) return EMERALD;
            if (s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES_GOLD.block())) return GOLD;
            if (s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES_IRON.block())) return IRON;
            if (s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES_COPPER.block())) return COPPER;
            if (s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES_COAL.block())) return COAL;
            if (s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES_REDSTONE.block())) return REDSTONE;
            if (s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES_LAPIS.block())) return LAPIS;
            if (s.is(net.neoforged.neoforge.common.Tags.BlockItems.ORES_QUARTZ.block())) return QUARTZ;
            return null;
        }

        /** Mined under y 0 (or Deepglass, which is always deep): pays through the deep budget. */
        public boolean deep(int y) {
            return this == DEEPGLASS || y < 0;
        }
    }

    private static boolean isHome(Level level) {
        return level.dimension() == Level.OVERWORLD;
    }

    // ---- activity counters ------------------------------------------------------------------------------------------

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (dead instanceof ServerPlayer sp) {
            Progression.count(sp, Counters.DEATHS);
            var killer = event.getSource().getEntity();
            if (killer != null) {
                String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(killer.getType()).toString();
                Progression.count(sp, "awaken:killed_by/" + type);
                Progression.count(sp, "awaken:killed_by_week/" + type);
            }
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer sp)) return;
        Profile p = Progression.profile(sp);
        if (dead instanceof Enemy && isHome(sp.level()) && !dev.awaken.core.api.LivingHooks.paysKills()) {
            var role = dev.awaken.core.api.MobRole.of(dead);
            int contentLevel = role.role() == dev.awaken.core.api.MobRole.Role.NONE ? 0 : Math.max(0, role.contentLevel());
            double xp = KILL_XP * Math.min(2.0, dead.getMaxHealth() / 20.0);
            homeXp(sp, XpBudget.KILL, xp, contentLevel);
            // the party nearby shares the kill (SPEC "Together"), each through their own home budget
            for (ServerPlayer mate : dev.awaken.core.api.Parties.sharing(sp, dead)) homeXp(mate, XpBudget.KILL, xp, contentLevel);
        }
        if (!p.awakened()) return;
        Progression.count(sp, Counters.KILLS);
        p.day = new Profile.DayLog(p.day.day(), p.day.levelStart(), p.day.gates(), p.day.kills() + 1, p.day.best());
        var direct = event.getSource().getDirectEntity();
        boolean byEcho = direct != null && direct != sp && dev.awaken.core.api.MobRole.of(direct).role() == dev.awaken.core.api.MobRole.Role.ECHO;
        if (sp.getMainHandItem().isEmpty() && !byEcho && (direct == sp || direct == null)) Progression.count(sp, Counters.KILLS_UNARMED);
        if (dead instanceof Enemy && isHome(sp.level())) Progression.count(sp, Counters.KILLS_OVERWORLD);
    }

    private static void onBreak(BreakBlockEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer sp)) return;
        OreXp ore = OreXp.of(event.getState());
        int y = event.getPos().getY();
        // banked before awakening
        if (ore != null && ore.weight > 0 && isHome(sp.level())) homeXp(sp, ore.deep(y) ? XpBudget.DEEP : XpBudget.ORE, ORE_XP * ore.weight, ore.contentLevel(y));
        if (ore != null && Progression.discover(sp, "ore/" + ore.id(), ORE_DISCOVERY) && Progression.profile(sp).awakened()) {
            // a paced toast that says what the ore is for (ores aren't Journal entries, so not Finds' "kept in your Journal")
            Signal.send(sp, dev.awaken.signal.SignalWindow.builder(dev.awaken.signal.SignalWindow.Kind.NOTICE,
                            Component.literal("New ore · " + ore.title).withColor(ore.colour))
                    .body(Component.literal(ore.hint)).presentation(dev.awaken.signal.SignalWindow.Presentation.TOAST)
                    .tier(dev.awaken.core.api.Notices.Tier.IMPORTANT).topic("ore").ttl(20 * 6).build());
        }
        if (!Progression.profile(sp).awakened()) return;
        if (ore != null && ore != OreXp.AMETHYST) Progression.count(sp, Counters.ORES_MINED);
        Progression.count(sp, "awaken:blocks_broken");
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !Progression.profile(sp).awakened()) return;
        Progression.count(sp, Counters.BLOCKS_PLACED);
        if (isHome(sp.level())) homeXp(sp, XpBudget.BUILD, BLOCK_XP, 0);
    }

    private static void onTame(net.neoforged.neoforge.event.entity.living.AnimalTameEvent event) {
        if (event.getTamer() instanceof ServerPlayer sp && event.getAnimal() instanceof Wolf) {
            Progression.count(sp, Counters.WOLVES_TAMED);
        }
    }

    private static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !Progression.profile(sp).awakened()) return;
        double resist = Progression.perk(sp, Perks.FALL_RESIST);
        if (resist > 0) event.setDamageMultiplier((float) (event.getDamageMultiplier() * Math.max(0, 1 - resist)));
        double dist = event.getDistance();
        // check on the next tick, after the fall damage has been applied
        if (dist > 3) dev.awaken.core.Later.run(sp.level().getServer(), 1, () -> {
            if (sp.isAlive() && !sp.isRemoved()) Progression.record(sp, Counters.FALL_SURVIVED_MAX, Math.round(dist));
        });
    }

    // ---- combat stats -----------------------------------------------------------------------------------------------

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            Profile p = Progression.profile(sp);
            if (!p.awakened()) return;
            double vit = p.stat(Stat.VIT) - Stat.START;
            double resist = Math.min(0.35, vit * 0.005);
            if (event.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) resist += Progression.perk(sp, Perks.EXPLOSION_RESIST);
            event.setAmount((float) (event.getAmount() * Math.max(0.2, 1 - resist)));
            // count what the hit could take from you (a /kill or the void is "infinite" damage, not progress)
            float taken = Math.min(event.getAmount(), sp.getHealth() + sp.getAbsorptionAmount());
            Progression.count(sp, Counters.DAMAGE_TAKEN, Math.max(1, Math.round(taken)));
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            Profile p = Progression.profile(attacker);
            if (!p.awakened()) return;
            double bonus = Progression.perk(attacker, Perks.DAMAGE_ABOVE_HALF_HP);
            if (bonus > 0 && attacker.getHealth() > attacker.getMaxHealth() / 2) {
                event.setAmount((float) (event.getAmount() * (1 + bonus)));
            }
        }
    }

    private static void onCrit(CriticalHitEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Profile p = Progression.profile(sp);
        if (!p.awakened() || event.isCriticalHit()) return;
        double chance = Math.min(0.35, (p.stat(Stat.PER) - Stat.START) * 0.004 + Progression.perk(sp, Perks.CRIT_CHANCE));
        if (sp.getRandom().nextDouble() < chance) {
            event.setCriticalHit(true);
            event.setDamageMultiplier(1.6f);
        }
    }

    // ---- day's end --------------------------------------------------------------------------------------------------

    private static void onWake(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || event.wakeImmediately()) return;
        Profile p = Progression.profile(sp);
        if (!p.awakened() || p.day.day() < 0) return;
        DaysEnd.send(sp, p);
    }

    static ServerLevel overworld(ServerPlayer p) {
        return p.level().getServer().overworld();
    }

    static void debug(ServerPlayer p, String what) {
        DirectorLog.log(p, "progression", what);
    }

}
