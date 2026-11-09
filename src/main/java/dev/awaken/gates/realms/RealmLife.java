package dev.awaken.gates.realms;

import dev.awaken.core.api.EconomyHooks;
import dev.awaken.core.api.MobHooks;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.run.Interactions;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A Realm's peaceful life (SPEC §50.8). Herds, flies and folk are kept up around each player by density, every two
 * seconds, always out of sight distance, and go when the player does. The folk are people to overhear and trade with,
 * nothing more: they never fight beside you, set tasks or give directions.
 */
public final class RealmLife {
    static final int HERD_WANT = 5, HERD_NEAR = 64, FLY_WANT = 6, FLY_NEAR = 40, FOLK_WANT = 9, FOLK_ROAM = 30, FOLK_WAKE = 130, UNSEEN = 28;
    /** Honey a Realm's folk can spare, and what a sale costs. */
    static final int TRADES = 6, PRICE = 2, BOTTLES = 3;
    private static final int OVERHEAR = 12, OVERHEAR_TICKS = 20 * 40, TALK_TICKS = 30;

    static final String FOLK = "wax_folk";

    /** Things they say when spoken to: what their lives are like, never where to go or what to do. */
    private static final List<String> SAY = List.of(
            "We stopped counting the harvests a long time ago. The honey just keeps coming.",
            "I used to be taller. Everyone here used to be taller.",
            "The grazers are no trouble. They eat the moss and they leave us alone.",
            "The bees used to be friendlier than this. We keep our doors shut now.",
            "If your light goes out, someone lights it again. That's most of what we do for each other.",
            "You're very warm for something with no wick.",
            "Sit down if you're tired. Nobody here is in a hurry.",
            "We don't get many visitors. I can't remember the last one, but then I can't remember much.");
    private static final List<String> OVERHEARD = List.of(
            "A wax-folk mutters, \"That's the third time this week my hand has come off.\"",
            "Two wax-folk are arguing about whose turn it is to stir the honey.",
            "A wax-folk somewhere nearby is humming, badly.",
            "Someone close by says, \"Is it still harvest? I've lost track.\"",
            "A wax-folk says to nobody in particular, \"My wick's getting short.\"",
            "\"Don't stand so near the lamp,\" one wax-folk tells another. \"You know what happens.\"");

    private static final Map<ServerPlayer, Long> NEXT_OVERHEAR = new WeakHashMap<>(), NEXT_TALK = new WeakHashMap<>();

    private RealmLife() {}

    /** Called each half second for a player in a Realm; the work runs for each player once every two seconds. */
    static void tick(ServerLevel level, ServerPlayer p, GateRecord g, RealmPlan plan, RealmRef ref, int lx, int lz, int t) {
        if ((t / 10 + p.getId()) % 4 != 0) return;
        Blueprint bp = plan.bp();
        for (String herd : bp.life().herds()) herd(level, p, g, plan.land(), ref, herd, lx, lz);
        for (String fly : bp.life().ambient()) flies(level, p, g, plan.land(), ref, fly, lx, lz);
        if (bp.folk().villages() > 0) folk(level, p, g, plan.land(), ref, lx, lz);
    }

    /** A herd turns up together, a good way off: three to five animals on the open ground. */
    private static void herd(ServerLevel level, ServerPlayer p, GateRecord g, Land land, RealmRef ref, String id, int lx, int lz) {
        if (count(level, p.blockPosition(), id, HERD_NEAR) >= HERD_WANT) return;
        RandomSource r = level.getRandom();
        double a = r.nextDouble() * Math.PI * 2;
        int d = 36 + r.nextInt(22), cx = lx + (int) (Math.cos(a) * d), cz = lz + (int) (Math.sin(a) * d);
        int group = 3 + r.nextInt(3);
        for (int i = 0; i < group; i++) {
            BlockPos at = open(level, land, ref, cx - 5 + r.nextInt(11), cz - 5 + r.nextInt(11));
            if (at != null && clearOfPlayers(level, at)) MobHooks.spawnCreature(level, id, at, 0, g.key());
        }
    }

    private static void flies(ServerLevel level, ServerPlayer p, GateRecord g, Land land, RealmRef ref, String id, int lx, int lz) {
        if (count(level, p.blockPosition(), id, FLY_NEAR) >= FLY_WANT) return;
        RandomSource r = level.getRandom();
        double a = r.nextDouble() * Math.PI * 2;
        int d = 16 + r.nextInt(20);
        BlockPos at = open(level, land, ref, lx + (int) (Math.cos(a) * d), lz + (int) (Math.sin(a) * d));
        if (at != null) MobHooks.spawnCreature(level, id, at.above(2 + r.nextInt(4)), 0, g.key());
    }

    /** The village has people in it before you reach it, and they stay near their own doors. */
    private static void folk(ServerLevel level, ServerPlayer p, GateRecord g, Land land, RealmRef ref, int lx, int lz) {
        Land.Site v = land.site(Land.SiteKind.VILLAGE);
        if (v == null || Math.hypot(lx - v.x(), lz - v.z()) > FOLK_WAKE) return;
        BlockPos centre = Realms.world(ref.cell(), v.x(), v.y(), v.z());
        if (!level.isLoaded(centre) || count(level, centre, FOLK, v.radius()) >= FOLK_WANT) {
            overhear(level, p, centre, v);
            return;
        }
        RandomSource r = level.getRandom();
        for (int tries = 0; tries < 8; tries++) {
            int x = v.x() - FOLK_ROAM + r.nextInt(FOLK_ROAM * 2 + 1), z = v.z() - FOLK_ROAM + r.nextInt(FOLK_ROAM * 2 + 1);
            BlockPos at = standing(level, land, ref, x, z);
            if (at == null || !clearOfPlayers(level, at)) continue;
            LivingEntity e = MobHooks.spawnCreature(level, FOLK, at, 0, g.key());
            if (e instanceof Mob m) m.setHomeTo(at, 14);
            return;
        }
    }

    private static void overhear(ServerLevel level, ServerPlayer p, BlockPos centre, Land.Site v) {
        long now = level.getGameTime();
        if (now < NEXT_OVERHEAR.getOrDefault(p, 0L)) return;
        if (count(level, p.blockPosition(), FOLK, OVERHEAR) == 0) return;
        NEXT_OVERHEAR.put(p, now + OVERHEAR_TICKS + level.getRandom().nextInt(OVERHEAR_TICKS));
        Signal.toast(p, Component.literal(OVERHEARD.get(level.getRandom().nextInt(OVERHEARD.size()))));
    }

    /** Valley floor only: not on a rock's top, not a roof, not in the honey, and well inside the land's edge. */
    private static BlockPos open(ServerLevel level, Land land, RealmRef ref, int x, int z) {
        if (land.edge(x, z) > -8) return null;
        BlockPos top = Realms.world(ref.cell(), x, 0, z);
        if (!level.isLoaded(top)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, top.getX(), top.getZ());
        if (Math.abs(y - (land.ground(x, z) + 1)) > 1) return null;
        BlockPos at = top.atY(y);
        BlockState under = level.getBlockState(at.below());
        return under.is(RealmsModule.HONEY_FLOW.get()) || !level.getFluidState(at.below()).isEmpty() ? null : at;
    }

    /** A spot on the ground with room to stand, indoors or out (a hut's roof doesn't count). */
    private static BlockPos standing(ServerLevel level, Land land, RealmRef ref, int x, int z) {
        BlockPos at = Realms.world(ref.cell(), x, land.ground(x, z) + 1, z);
        if (!level.isLoaded(at) || land.edge(x, z) > -8) return null;
        for (int y = 0; y < 3; y++) {
            BlockPos here = at.above(y);
            if (level.getBlockState(here).getCollisionShape(level, here).isEmpty() && level.getBlockState(here.above()).getCollisionShape(level, here.above()).isEmpty()
                    && !level.getBlockState(here.below()).getCollisionShape(level, here.below()).isEmpty() && !level.getBlockState(here.below()).is(RealmsModule.HONEY_FLOW.get())) return here;
        }
        return null;
    }

    private static boolean clearOfPlayers(ServerLevel level, BlockPos at) {
        for (ServerPlayer o : level.players()) if (o.blockPosition().closerThan(at, UNSEEN)) return false;
        return true;
    }

    private static int count(ServerLevel level, BlockPos around, String id, int r) {
        return level.getEntitiesOfClass(FaunaMob.class, new AABB(around).inflate(r, 40, r), e -> e.isAlive() && id.equals(e.def().id)).size();
    }

    // ---- talking and trading ---------------------------------------------------------------------------------------------

    /** A hand on a wax-folk: they say something about their lives, and offer honey while they have some to spare. */
    public static boolean talk(ServerPlayer p, GateRecord g, Entity target) {
        if (!(target instanceof FaunaMob f) || !FOLK.equals(f.def().id)) return false;
        ServerLevel level = (ServerLevel) p.level();
        long now = level.getGameTime();
        if (now < NEXT_TALK.getOrDefault(p, 0L)) return true;
        NEXT_TALK.put(p, now + TALK_TICKS);
        f.getLookControl().setLookAt(p);
        f.getNavigation().stop();
        RealmState st = RealmData.get(level.getServer()).state(g.id);
        String line = SAY.get(Math.floorMod(target.getId() + (int) (now / 200), SAY.size()));
        var window = SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal("A wax-folk says")).body(Component.literal(line));
        if (st.folkTrades < TRADES) window.row("Honey to spare", BOTTLES + " bottles for " + PRICE + " emeralds")
                .choice(Interactions.WAX_TRADE, "Buy the honey", "Not today");
        Signal.send(p, window.priority(3).ttl(20 * 12).build());
        return true;
    }

    public static void trade(ServerPlayer p, GateRecord g) {
        RealmData data = RealmData.get(p.level().getServer());
        RealmState st = data.state(g.id);
        if (st.folkTrades >= TRADES) {
            Signal.toast(p, Component.literal("They've already sold all the honey they had to spare."));
            return;
        }
        if (!EconomyHooks.charge(p, PRICE)) {
            Signal.toast(p, Component.literal("You don't have " + PRICE + " emeralds."));
            return;
        }
        st.folkTrades++;
        data.setDirty();
        ItemStack honey = new ItemStack(Items.HONEY_BOTTLE, BOTTLES);
        p.getInventory().placeItemBackInInventory(honey, net.minecraft.util.Prediction.SERVER_ONLY);
        Signal.toast(p, Component.literal("Three bottles of honey, still warm."));
    }
}
