package dev.awaken.living.client;

import dev.awaken.core.api.CreatureTag;
import dev.awaken.lens.client.LensClient;
import dev.awaken.living.LivingContent;
import dev.awaken.living.LivingNet;
import dev.awaken.living.combat.PlateRules;
import dev.awaken.progression.Progression;
import dev.awaken.progression.Stat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Living Wilds on the client (SPEC §21.1): the band's combat layer. Each tick it decides which creatures wear a plate
 * ({@link PlateRules}), finds rare drops for their pillars, and starts a waiting level moment once a fight is over; the
 * server's small events start glyphs, streams, captions and moments; {@link LivingFx} draws the world's light and
 * {@link LivingHud} the screen's words. Pure survival ({@code awaken:pure_survival}) hides all of it but the captions.
 */
public final class LivingClient {
    private LivingClient() {}

    static final net.minecraft.resources.Identifier TIP_PLATE = dev.awaken.Awaken.id("living/first_plate"), TIP_REGION = dev.awaken.Awaken.id("living/first_region");
    static long ticks;
    static boolean shown = true;
    static double lookRange = PlateRules.lookRange(0);
    static final List<PlateRules.Candidate> PICKED = new ArrayList<>();
    private static final List<PlateRules.Candidate> CANDIDATES = new ArrayList<>();
    static final List<ItemEntity> PILLARS = new ArrayList<>();
    /** This player's open scenes, each sending up a flare now and then (from the server every 5 s). */
    static volatile List<net.minecraft.core.BlockPos> TELLS = List.of();
    private static final Map<Integer, Long> FIGHT = new HashMap<>(), LOOK = new HashMap<>(), SCAN = new HashMap<>(), FIRST_PICK = new HashMap<>();
    private static final Map<Integer, float[]> DRAIN = new HashMap<>();
    private static final java.util.Set<Integer> SEEN = new java.util.HashSet<>();
    private static long lastFight = Long.MIN_VALUE / 2, lastScanSound, lastTickSound;
    private static int pendingLevel, pendingPoints;
    private static String pendingRank = "";
    private static long pendingSince = -1, momentAt = Long.MIN_VALUE / 2;

    /** The level of the land under the player as the server last said (-1 away from the overworld), for the layers that follow the land. */
    public static int landLevel() {
        return DreadFx.land();
    }

    public static void init(IEventBus modBus) {
        LivingClientConfig.register();
        PulseSound.init();
        JournalSection.register();
        AlmanacSection.register();
        CraftsSection.register();
        // rumours: a "?" on the map where you heard something is happening
        dev.awaken.core.api.MapMarks.register(dev.awaken.living.wonder.WonderWatch::marks);
        dev.awaken.core.api.MapMarks.register(p -> {
            var heard = p.getData(dev.awaken.living.LivingContent.RUMOURS.get()).heard();
            if (heard.isEmpty()) return java.util.List.of();
            java.util.List<dev.awaken.core.api.MapMarks.Mark> out = new java.util.ArrayList<>(heard.size());
            for (var h : heard) out.add(new dev.awaken.core.api.MapMarks.Mark(new net.minecraft.core.BlockPos(h.x(), 64, h.z()), 0xE7C27A, "? " + h.title(), false));
            return out;
        });
        modBus.addListener(RegisterGuiLayersEvent.class, LivingHud::register);
        FormLayers.init(modBus);
        dev.awaken.living.client.forms.FormBodies.init(modBus);
        modBus.addListener(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers.class,
                e -> e.registerEntityRenderer(dev.awaken.living.LivingContent.WAYFARER.get(), WayfarerRenderer::new));
        modBus.addListener(RegisterClientPayloadHandlersEvent.class, e -> {
            e.register(LivingNet.RegionMsg.TYPE, (m, ctx) -> onRegion(m));
            e.register(LivingNet.LandMsg.TYPE, (m, ctx) -> DreadFx.onLand(m));
            e.register(LivingNet.HitFx.TYPE, (m, ctx) -> onHit(m));
            e.register(LivingNet.FormCue.TYPE, (m, ctx) -> dev.awaken.living.client.forms.FormBodies.onCue(m));
            e.register(LivingNet.XpFx.TYPE, (m, ctx) -> onXp(m));
            e.register(LivingNet.LevelUp.TYPE, (m, ctx) -> onLevelUp(m));
            e.register(LivingNet.Shown.TYPE, (m, ctx) -> shown = m.shown());
            e.register(LivingNet.Journal.TYPE, (m, ctx) -> JournalSection.entries = List.copyOf(m.entries()));
            e.register(LivingNet.Tells.TYPE, (m, ctx) -> TELLS = List.copyOf(m.at()));
            e.register(LivingNet.CampSmoke.TYPE, (m, ctx) -> PulseFx.camp(m.at()));
            e.register(LivingNet.FirstScene.TYPE, (m, ctx) -> FirstFilm.onScene(m));
            e.register(LivingNet.Happenings.TYPE, (m, ctx) -> PulseFx.onTells(m));
            e.register(LivingNet.BossBind.TYPE, (m, ctx) -> BossPlate.onBind(m));
            e.register(LivingNet.BossLine.TYPE, (m, ctx) -> BossPlate.onLine(m));
        });
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(ClientTickEvent.Post.class, e -> tick());
        bus.addListener(ClientTickEvent.Post.class, e -> FirstFilm.tick());
        FirstFilm.init();
        bus.addListener(SubmitCustomGeometryEvent.class, LivingFx::submit);
        bus.addListener(SubmitCustomGeometryEvent.class, PulseFx::submit);
        bus.addListener(SubmitCustomGeometryEvent.class, CraftFx::submit);
        bus.addListener(ClientTickEvent.Post.class, CraftFx::tick);
        bus.addListener(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent.class, LivingPlates::extract);
        // the Boss Plate (SPEC §35.1): before anyone else reads a bar, so ours are cancelled and sized first
        bus.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent.BossEventProgress.class, BossPlate::onBar);
        bus.addListener(net.neoforged.neoforge.client.event.RenderGuiEvent.Pre.class, e -> BossPlate.frameStart());
        bus.addListener(net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent.class, BossPlate::extract);
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> reset());
        bus.addListener(ClientPlayerNetworkEvent.LoggingOut.class, e -> BossPlate.reset());
    }

    private static void reset() {
        PICKED.clear();
        CANDIDATES.clear();
        PILLARS.clear();
        TELLS = List.of();
        PulseFx.reset();
        PulseSound.reset();
        CraftFx.reset();
        JournalSection.entries = List.of();
        FIGHT.clear();
        LOOK.clear();
        SCAN.clear();
        FIRST_PICK.clear();
        DRAIN.clear();
        SEEN.clear();
        pendingSince = -1;
        shown = true;
        LivingFx.reset();
        LivingHud.reset();
    }

    /** Whether plates show creatures' levels now (Jade leaves its level line out then: one source of truth). */
    public static boolean platesOn() {
        return shown && LivingClientConfig.plates && dev.awaken.holo.client.HoloWorld.allowed();
    }

    // ---- events -----------------------------------------------------------------------------------------------------

    /** A place caption waiting for its moment (SPEC §26): the director decides; past {@link #CAPTION_WAIT} it is filed. */
    private static LivingNet.RegionMsg pendingCaption;
    private static long captionSince;
    static final int CAPTION_WAIT = 200;

    private static void onRegion(LivingNet.RegionMsg m) {
        if (!m.caption()) {
            pendingCaption = null; // walked on before it showed: the newer place wins
            return;
        }
        pendingCaption = m;
        captionSince = ticks;
        caption();
    }

    /** Shows the waiting caption if the director gives it the moment (danger always gets it), or files it when stale. */
    private static void caption() {
        LivingNet.RegionMsg m = pendingCaption;
        if (m == null) return;
        var notices = dev.awaken.core.api.Notices.client();
        var tier = m.danger() ? dev.awaken.core.api.Notices.Tier.CRITICAL : dev.awaken.core.api.Notices.Tier.IMPORTANT;
        if (notices.claim(tier, "region")) {
            pendingCaption = null;
            LivingHud.region(m);
            play(LivingContent.REGION_ENTER.get(), 1.0F, 0.7F);
            if (dev.awaken.core.api.TipHooks.client().wants(TIP_REGION)) dev.awaken.core.api.TipHooks.client().offer(TIP_REGION);
        } else if (ticks - captionSince > CAPTION_WAIT) {
            pendingCaption = null;
            notices.file(dev.awaken.core.api.Notices.Tier.AMBIENT, net.minecraft.network.chat.Component.literal("Entered · " + m.name()),
                    net.minecraft.network.chat.Component.literal(m.commons() ? "Home · Lv " + m.low() + "–" + m.high() : "Lv " + m.low() + "–" + m.high()));
        }
    }

    private static void onHit(LivingNet.HitFx m) {
        FIGHT.put(m.entity(), ticks);
        lastFight = ticks;
        if (m.incoming() || !shown) return;
        Minecraft mc = Minecraft.getInstance();
        Entity target = mc.level == null ? null : mc.level.getEntity(m.entity());
        if (target == null) return;
        var numbers = LivingClientConfig.numbers;
        if (numbers == LivingClientConfig.Numbers.FULL || numbers == LivingClientConfig.Numbers.CRITS && m.crit()) {
            LivingFx.glyph(target, m.amount(), m.crit(), m.force());
        }
        if (m.crit()) play(LivingContent.CRIT.get(), 1.0F, 0.55F);
    }

    private static void onXp(LivingNet.XpFx m) {
        if (!shown || !LivingClientConfig.xpStream) return;
        if (m.origin()) LivingFx.stream(m.x(), m.y(), m.z(), m.xp(), m.kind());
        else arrived(m.xp(), m.kind());
    }

    /** The stream's lead reached the band (or XP came from nowhere in particular). */
    static void arrived(int xp, int kind) {
        LivingHud.pop(xp, kind);
        if (ticks - lastTickSound >= 3) {
            lastTickSound = ticks;
            play(LivingContent.XP_TICK.get(), 1.0F + Math.min(0.25F, xp / 4000F), 0.35F);
        }
    }

    private static void onLevelUp(LivingNet.LevelUp m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !shown) return;
        if (m.entity() != mc.player.getId()) {
            Entity who = mc.level.getEntity(m.entity());
            if (who != null) LivingFx.moment(who, false);
            return;
        }
        pendingLevel = m.level();
        pendingPoints = pendingPoints + m.points();
        pendingRank = m.rankLine();
        pendingSince = ticks;
    }

    // ---- the tick ---------------------------------------------------------------------------------------------------

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        LivingClientConfig.read();
        PulseSound.tick();
        Player p = mc.player;
        if (p == null || mc.level == null) return;
        moment(mc, p);
        if (pendingCaption != null && ticks % 5 == 0) caption();
        if (ticks % 5 == 0) pillars(mc, p);
        plates(mc, p);
        DreadFx.tick(mc, p);
        AmbientFx.tick(mc, p);
        if (ticks % 100 == 0) forgetOld();
    }

    /** A waiting level moment plays once the fight is over (a fight: a hit either way in the last 5 s). */
    private static void moment(Minecraft mc, Player p) {
        if (momentAt != Long.MIN_VALUE / 2 && ticks - momentAt == 44) LensClient.clearOverride(20);
        if (pendingSince < 0) return;
        if (ticks - lastFight < 100 && ticks - pendingSince < 600) return;
        // the director's quiet (a screen, a scene, a moment on screen) also waits, as long as the fight rule does
        if (dev.awaken.core.api.Notices.client().quiet() && ticks - pendingSince < 600) return;
        dev.awaken.core.api.Notices.client().claim(dev.awaken.core.api.Notices.Tier.CRITICAL, "level"); // takes the moment
        boolean full = LivingClientConfig.moment == LivingClientConfig.Moment.FULL;
        LivingHud.pane(pendingLevel, pendingPoints, pendingRank);
        if (full) {
            LivingFx.moment(p, true);
            LensClient.override("awaken:living_level", 6);
            momentAt = ticks;
        }
        play(LivingContent.LEVEL_UP.get(), 1.0F, 0.85F);
        pendingSince = -1;
        pendingPoints = 0;
    }

    private static void pillars(Minecraft mc, Player p) {
        PILLARS.clear();
        if (!shown) return;
        AABB box = p.getBoundingBox().inflate(48);
        PILLARS.addAll(mc.level.getEntitiesOfClass(ItemEntity.class, box, e -> pillarTier(e) > 0));
    }

    static int pillarTier(ItemEntity e) {
        return e.getExistingData(LivingContent.LOOT_PILLAR).orElse(0);
    }

    private static void plates(Minecraft mc, Player p) {
        if (!shown || !LivingClientConfig.plates) {
            PICKED.clear();
            return;
        }
        lookRange = PlateRules.lookRange(Progression.profile(p).stat(Stat.PER));
        // what the band reads: the creature under the crosshair, if no wall stands between
        Vec3 eye = p.getEyePosition(), to = eye.add(p.getViewVector(1).scale(lookRange));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, to, new AABB(eye, to).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && CreatureTag.of(e).present(), lookRange * lookRange);
        if (hit != null) {
            var block = mc.level.clip(new ClipContext(eye, hit.getLocation(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
            if (block.getType() != HitResult.Type.MISS && block.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye)) hit = null;
        }
        if (hit != null) {
            int id = hit.getEntity().getId();
            Long was = LOOK.get(id);
            if (was == null || ticks - was > 40) {
                SCAN.put(id, ticks);
                if (ticks - lastScanSound > 10) {
                    lastScanSound = ticks;
                    play(LivingContent.SCAN.get(), 1.0F, 0.18F);
                }
            }
            LOOK.put(id, ticks);
            if (dev.awaken.core.api.TipHooks.client().wants(TIP_PLATE)) dev.awaken.core.api.TipHooks.client().offer(TIP_PLATE);
        }
        CANDIDATES.clear();
        int los = 0;
        for (LivingEntity e : mc.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(40), e -> e != p && e.isAlive())) {
            CreatureTag tag = CreatureTag.of(e);
            if (!tag.present()) continue;
            boolean excluded = e instanceof Player || e instanceof TamableAnimal t && t.isTame();
            double d = e.distanceTo(p);
            boolean notable = tag.elite() || tag.kind() == CreatureTag.Kind.BEAST;
            if (notable && !SEEN.contains(e.getId()) && d <= PlateRules.NOTABLE_RANGE && los < 4) {
                los++;
                if (p.hasLineOfSight(e)) SEEN.add(e.getId());
            }
            CANDIDATES.add(new PlateRules.Candidate(e.getId(), d, FIGHT.getOrDefault(e.getId(), Long.MIN_VALUE),
                    LOOK.getOrDefault(e.getId(), Long.MIN_VALUE), notable && SEEN.contains(e.getId()), excluded));
        }
        PlateRules.pick(CANDIDATES, PICKED, ticks, lookRange);
        for (PlateRules.Candidate c : PICKED) {
            FIRST_PICK.putIfAbsent(c.id(), ticks);
            if (mc.level.getEntity(c.id()) instanceof LivingEntity e) drainTick(e);
        }
        FIRST_PICK.keySet().removeIf(id -> PICKED.stream().noneMatch(c -> c.id() == id));
    }

    // ---- per-plate state --------------------------------------------------------------------------------------------

    /** A plate grows in over 4 ticks from the moment it is picked. */
    static float fadeIn(int id) {
        Long at = FIRST_PICK.get(id);
        return at == null ? 0 : Math.min(1, (ticks - at + 1) / 4F);
    }

    /** Ticks since the band began reading this creature (large when it never did). */
    static float scanAge(int id) {
        Long at = SCAN.get(id);
        return at == null ? 1000 : ticks - at;
    }

    /** Health just lost drains over about 300 ms: what the amber part of the bar shows. */
    static float drained(int id, float frac) {
        float[] d = DRAIN.get(id);
        return d == null ? frac : d[0];
    }

    private static void drainTick(LivingEntity e) {
        float frac = Math.max(0, Math.min(1, e.getHealth() / Math.max(1F, e.getMaxHealth())));
        float[] d = DRAIN.computeIfAbsent(e.getId(), k -> new float[]{frac, 0});
        if (frac >= d[0]) {
            d[0] = frac;
            d[1] = 0;
        } else if (++d[1] > 4) {
            d[0] = Math.max(frac, d[0] - 0.05F);
        }
    }

    private static void forgetOld() {
        FIGHT.values().removeIf(t -> ticks - t > 400);
        LOOK.values().removeIf(t -> ticks - t > 400);
        SCAN.values().removeIf(t -> ticks - t > 400);
        Minecraft mc = Minecraft.getInstance();
        DRAIN.keySet().removeIf(id -> mc.level == null || mc.level.getEntity(id) == null);
        SEEN.removeIf(id -> mc.level == null || mc.level.getEntity(id) == null);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
