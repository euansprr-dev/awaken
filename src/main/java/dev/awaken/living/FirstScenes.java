package dev.awaken.living;

import dev.awaken.core.AwakenNet;
import dev.awaken.core.DirectorLog;
import dev.awaken.core.api.HearthHooks;
import dev.awaken.living.quest.QuestBook;
import dev.awaken.living.quest.QuestDef;
import dev.awaken.living.quest.QuestEngine;
import dev.awaken.living.quest.QuestState;
import dev.awaken.living.wayfarer.Camp;
import dev.awaken.living.wayfarer.Scenes;
import dev.awaken.living.wayfarer.Wayfarer;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The first thread's two staged moments (SPEC §27.6), server side; the client films them ({@code living.client.FirstFilm}).
 * <ul>
 * <li><b>The High Gate.</b> The first time a player goes out through it with Bram's letters, a short shot from the gate to
 * the Wayfarers' smoke rising over the trees, with Hale's line said over it. Once.</li>
 * <li><b>Ilse's fire, the Last Fire</b> (SPEC §43.1). Coming up to Ilse with the letters (or talking to her) starts her
 * scene by the fire instead of a bare click: she takes the letters and says her ten lines (JOURNEY part 1, verbatim, each
 * held for its reading time): the Lull, the stone people, Aldo, the holdfasts, what the licence means; at the eighth she
 * holds a coal from her fire out in her hand. The step ends as your hand closes on it (the Ember, W1's: one a player,
 * kept through death), and the first thread lets go: the Hearth's line takes over. A player who already has a home fire
 * or an Ember hears the other nine.</li>
 * </ul>
 * Nothing hunts or hurts a player while either plays.
 */
public final class FirstScenes {
    private FirstScenes() {}

    /** The gate's shot was sent (once a player). */
    static final String GATE_SEEN = "awaken:first/gate_shot";
    /** Ilse's ten lines at the Last Fire (JOURNEY part 1, verbatim). The eighth holds the coal out. */
    static final String[] ILSE = {
            "You're the one from the square. Sit down, the kettle's nearly on.",
            "This is the last fire. Past that ridge the bells don't carry, and that's where the Lull starts.",
            "Ordinary people who stay out there get tired. Then they sit down somewhere, and they don't get up again. That's why we all live in the city now.",
            "Some went back out anyway. For a dog, or a ring, or their mam's grave. You'll pass them on the road. They sat down, and they've gone to stone. Leave them be.",
            "The first one's on the milestone just past the ridge. That's Aldo. He went back for his dog. Say hello to him for me.",
            "They say a few walled towns far out kept their bells going and never left. I've never seen one.",
            "Not you, though. You died once and came back. Whatever the Lull does, it can't do it to you. That's what the licence really means.",
            "Here. It's a coal from this fire. Put it down wherever you want to live, and that's your home. Nobody will come and bother you out there. Nobody can.",
            "And don't believe everything the Association says. It isn't all monsters out there. Most of what lives out there just wants to be left alone. Watch before you draw.",
            "Go on, then. Good luck. Come back now and then and tell me what it looks like. I haven't seen past that ridge in eleven years."};
    /** The line that holds the coal out. */
    static final int COAL = 7;
    /** Her first line (ticks into the scene), the letters a moment after it, the pause between lines, and how long her hand stays out. */
    static final int L1 = 16, DELIVER = 70, GAP = 12, WAIT = 20 * 30;
    /** After the coal is taken, before her next line. */
    static final int AFTER_COAL = 30;
    /** How long a gate shot shields (it lasts about ten seconds). */
    static final int GATE_SHIELD = 20 * 14;
    static final Identifier EMBER = Identifier.fromNamespaceAndPath("awaken", "hearth_ember"),
            LETTERS = Identifier.fromNamespaceAndPath("awaken", "ilse_letters");

    private static final class Run {
        final Wayfarer ilse;
        final BlockPos fire;
        final boolean coal;
        int age, tookAt = -1, offeredAt = -1;
        /** The next of her lines to say, and when (the scene's age). */
        int next, nextAt = L1;
        boolean delivered, offered, completing;

        Run(Wayfarer ilse, BlockPos fire, boolean coal) {
            this.ilse = ilse;
            this.fire = fire;
            this.coal = coal;
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    /** Players under a gate shot, until (game time). */
    private static final Map<UUID, Long> SHIELD = new HashMap<>();

    static void init() {
        QuestEngine.stager(FirstScenes::stage);
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
        bus.addListener(LivingIncomingDamageEvent.class, FirstScenes::onDamage);
        bus.addListener(LivingChangeTargetEvent.class, FirstScenes::onTarget);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            Run r = RUNS.remove(e.getEntity().getUUID());
            if (r != null) emptyHand(r);
            SHIELD.remove(e.getEntity().getUUID());
        });
        bus.addListener(ServerStoppedEvent.class, e -> {
            RUNS.clear();
            SHIELD.clear();
            Camp.forget();
        });
    }

    /** A scene staging a step's ending: the gate's shot says Hale's line; Ilse's scene has said hers. */
    static boolean stage(ServerPlayer p, String chain, QuestDef.Step step) {
        if (!chain.equals(FirstThread.chainId())) return false;
        if (step.id().equals("gate")) return gateShot(p, step.done().isEmpty() ? "" : step.done().getFirst(), false);
        if (step.id().equals("ilse")) {
            Run r = RUNS.get(p.getUUID());
            return r != null && r.completing;
        }
        return false;
    }

    /** Whether a staged moment holds this player (nothing hunts or hurts them). */
    public static boolean shielded(ServerPlayer p) {
        return RUNS.containsKey(p.getUUID()) || SHIELD.containsKey(p.getUUID());
    }

    // ---- the High Gate -----------------------------------------------------------------------------------------------

    /** Sends the gate's shot of the smoke ({@code again}: even if seen, for tours). True when it went. */
    static boolean gateShot(ServerPlayer p, String line, boolean again) {
        if (!again && Progression.profile(p).counter(GATE_SEEN) > 0) return false;
        MinecraftServer server = p.level().getServer();
        BlockPos fire = smokeAt(server);
        if (fire == null) return false;
        Progression.setCounter(p, GATE_SEEN, 1);
        AwakenNet.send(p, new LivingNet.CampSmoke(fire));
        int dist = (int) Math.round(Math.sqrt(p.blockPosition().distSqr(fire.atY(p.getBlockY()))));
        // the land you're stepping into, named in the shot (the region caption is skipped for this first time out)
        var region = dev.awaken.living.api.Regions.regionAt(server.overworld(), fire.getX(), fire.getZ());
        String land = region.name() + "|" + region.low() + "|" + region.high();
        AwakenNet.send(p, new LivingNet.FirstScene(LivingNet.FirstScene.GATE, 0, fire, dist, said(line) + "\n" + land));
        SHIELD.put(p.getUUID(), p.level().getGameTime() + GATE_SHIELD);
        DirectorLog.log(p, "living", "the first thread: the High Gate's shot of the camp's smoke, " + dist + " m");
        return true;
    }

    /** Where the camp's smoke rises: its fire, or (before anyone has come near) its nominal spot on the ground. */
    public static @Nullable BlockPos smokeAt(MinecraftServer server) {
        BlockPos f = Camp.fire(server);
        if (f != null) return f;
        BlockPos c = Scenes.marker(server, 0, "camp");
        if (c == null) return null;
        ServerLevel level = server.overworld();
        BlockPos col = c.offset(-3, 0, 3);
        var src = level.getChunkSource();
        int y = level.hasChunkAt(col) ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ())
                : src.getGenerator().getBaseHeight(col.getX(), col.getZ(), Heightmap.Types.WORLD_SURFACE_WG, level, src.randomState());
        return col.atY(y);
    }

    /** "Hale: Licence? Fine." as the film's "Hale|Licence? Fine." */
    static String said(String line) {
        int c = line.indexOf(": ");
        return c > 0 ? line.substring(0, c) + "|" + line.substring(c + 2) : "|" + line;
    }

    // ---- Ilse's fire -------------------------------------------------------------------------------------------------

    /** Talking to someone the chains know: Ilse, while the letters are hers, starts her scene. True when it took the talk. */
    public static boolean talk(ServerPlayer p, String who) {
        if (!who.equals("ilse") || !atIlse(p)) return false;
        if (RUNS.containsKey(p.getUUID())) return true;
        Wayfarer ilse = Scenes.resident(p.level().getServer(), "ilse");
        if (ilse == null) return false;
        begin(p, ilse);
        return true;
    }

    /** Whether the first thread is at Ilse's step (her scene takes her talk: public for the issue, which waits for it). */
    public static boolean atIlseStep(ServerPlayer p) {
        return atIlse(p);
    }

    /** Whether this player's errand is at Ilse's step. */
    static boolean atIlse(ServerPlayer p) {
        String chain = FirstThread.chainId();
        if (chain == null) return false;
        for (QuestState.Active a : QuestEngine.state(p).active()) {
            if (!a.chain().equals(chain)) continue;
            QuestDef d = QuestBook.get(chain);
            return d != null && a.step() < d.steps().size() && d.steps().get(a.step()).id().equals("ilse") && !QuestEngine.blocked(p, d.steps().get(a.step()));
        }
        return false;
    }

    static void begin(ServerPlayer p, Wayfarer ilse) {
        if (RUNS.containsKey(p.getUUID())) return;
        BlockPos fire = Camp.fire(p.level().getServer());
        if (fire == null) fire = ilse.blockPosition();
        Run r = new Run(ilse, fire, wantsCoal(p));
        RUNS.put(p.getUUID(), r);
        ilse.getNavigation().stop();
        AwakenNet.send(p, new LivingNet.FirstScene(LivingNet.FirstScene.BEGIN, ilse.getId(), fire, r.coal ? 1 : 0, ""));
        DirectorLog.log(p, "living", "the first thread: Ilse's scene by her fire" + (r.coal ? "" : " (they have a fire already)"));
    }

    /** No Hearth of their own and no coal carried: Ilse's coal is for them. */
    static boolean wantsCoal(ServerPlayer p) {
        if (HearthHooks.hearthOf(p).isPresent()) return false;
        Item ember = BuiltInRegistries.ITEM.getValue(EMBER);
        if (ember == Items.AIR) return false;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(ember)) return false;
        return true;
    }

    /** The client's side: the coal taken, or a held skip (to the coal held out). */
    static void ready(ServerPlayer p, int what) {
        Run r = RUNS.get(p.getUUID());
        if (r == null) return;
        if (what == LivingNet.FirstSceneReady.TOOK && r.offered && r.tookAt < 0) took(p, r);
        else if (what == LivingNet.FirstSceneReady.SKIP && r.next < COAL) {
            // a held skip: on to the coal (with a fire already, to her last two lines)
            if (!r.delivered) delivered(p, r);
            r.next = COAL;
            r.nextAt = r.age + 1;
        }
    }

    // ---- the tick ----------------------------------------------------------------------------------------------------

    static void tick(MinecraftServer server) {
        if (!SHIELD.isEmpty()) {
            long now = server.overworld().getGameTime();
            SHIELD.values().removeIf(t -> t < now);
        }
        if (server.getTickCount() % 10 == 0) arrivals(server);
        if (RUNS.isEmpty()) return;
        for (var it = RUNS.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Run r = e.getValue();
            if (p != null && step(p, r)) continue;
            it.remove();
            emptyHand(r);
            if (p != null) AwakenNet.send(p, new LivingNet.FirstScene(LivingNet.FirstScene.END, r.ilse.getId(), r.fire, r.tookAt >= 0 ? 1 : 0, ""));
        }
    }

    /** Coming up to Ilse with the letters (within 7, in her sight) starts her scene. */
    private static void arrivals(MinecraftServer server) {
        Wayfarer ilse = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (RUNS.containsKey(p.getUUID()) || !atIlse(p)) continue;
            if (ilse == null) ilse = Scenes.resident(server, "ilse");
            if (ilse == null) return;
            if (p.level() == ilse.level() && p.distanceToSqr(ilse) < 7 * 7 && ilse.hasLineOfSight(p)) begin(p, ilse);
        }
    }

    /** One tick of Ilse's scene; false when it's over (done, or the player walked off). */
    private static boolean step(ServerPlayer p, Run r) {
        Wayfarer ilse = r.ilse;
        if (p.isRemoved() || !p.isAlive() || ilse.isRemoved() || p.level() != ilse.level() || p.distanceToSqr(ilse) > 18 * 18) return false;
        // she stays where she sits while she talks; with the coal held out she brings it over to you
        double d2 = p.distanceToSqr(ilse);
        if (r.offered && r.tookAt < 0 && d2 > 2.2 * 2.2) {
            if (r.age % 10 == 0) ilse.getNavigation().moveTo(p, 0.75);
        } else ilse.getNavigation().stop();
        ilse.getLookControl().setLookAt(p, 30, 30);
        int a = ++r.age;
        if (a == DELIVER && !r.delivered) delivered(p, r);
        if (r.offered && r.tookAt < 0) {
            // her hand stays out; after half a minute she presses the coal into yours
            if (a > r.offeredAt + WAIT) took(p, r);
            return true;
        }
        if (a < r.nextAt) return true;
        if (r.next >= ILSE.length) return false;
        int i = r.next++;
        if (i == COAL) {
            if (r.coal) {
                offer(p, r);
                return true;
            }
            // a fire of their own already: the step ends where the coal would be, and she goes on
            complete(p, r);
            i = r.next++;
        }
        line(p, "Ilse", ILSE[i], hold(ILSE[i]));
        r.nextAt = a + hold(ILSE[i]) + GAP;
        return true;
    }

    /** How long a line stays up: a moment to see it, then its words at an easy reading pace. */
    static int hold(String text) {
        return 30 + text.length() * 4 / 3;
    }

    private static void line(ServerPlayer p, String who, String text, int ticks) {
        AwakenNet.send(p, new LivingNet.FirstScene(LivingNet.FirstScene.LINE, 0, BlockPos.ZERO, ticks, who + "|" + text));
    }

    private static void delivered(ServerPlayer p, Run r) {
        r.delivered = true;
        // the letters leave your pack for her hands (none if they went missing on the way: she doesn't mind)
        Item letters = BuiltInRegistries.ITEM.getValue(LETTERS);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(letters)) {
                inv.getItem(i).shrink(1);
                break;
            }
        }
        AwakenNet.send(p, new LivingNet.FirstScene(LivingNet.FirstScene.DELIVERED, r.ilse.getId(), r.fire, 0, "Bram's letters"));
    }

    /** The coal in her hand, held out to you. */
    private static void offer(ServerPlayer p, Run r) {
        r.offered = true;
        r.offeredAt = r.age;
        Item ember = BuiltInRegistries.ITEM.getValue(EMBER);
        if (ember != Items.AIR) r.ilse.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ember));
        line(p, "Ilse", ILSE[COAL], hold(ILSE[COAL]));
        AwakenNet.send(p, new LivingNet.FirstScene(LivingNet.FirstScene.OFFER, r.ilse.getId(), r.fire, 0, ""));
    }

    private static void took(ServerPlayer p, Run r) {
        r.tookAt = r.age;
        r.nextAt = r.age + AFTER_COAL;
        emptyHand(r);
        complete(p, r);
    }

    /** The step ends (its lines were the scene's): the Ember comes with it, and the next step is the Hearth's. */
    private static void complete(ServerPlayer p, Run r) {
        r.completing = true;
        try {
            QuestEngine.advance(p, "talk", "ilse", 1);
        } finally {
            r.completing = false;
        }
        DirectorLog.log(p, "living", "the first thread: Ilse's coal taken; the thread lets go (the Hearth's line takes over)");
    }

    private static void emptyHand(Run r) {
        if (!r.ilse.isRemoved()) r.ilse.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }

    // ---- nothing hunts or hurts you while it plays --------------------------------------------------------------------

    static void onDamage(LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && shielded(p) && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) e.setCanceled(true);
    }

    static void onTarget(LivingChangeTargetEvent e) {
        if (e.getEntity() instanceof Enemy && e.getNewAboutToBeSetTarget() instanceof ServerPlayer p && shielded(p)) e.setCanceled(true);
    }

    /** Dev and tours: Ilse's scene now, for a player standing near her (their errand set to her step). */
    static boolean devIlse(ServerPlayer p) {
        Wayfarer ilse = Scenes.resident(p.level().getServer(), "ilse");
        if (ilse == null) return false;
        RUNS.remove(p.getUUID());
        begin(p, ilse);
        return true;
    }
}
