package dev.awaken.gates.realms;

import dev.awaken.core.api.EconomyHooks;
import dev.awaken.core.api.MobHooks;
import dev.awaken.gates.GateRecord;
import dev.awaken.gates.run.Interactions;
import dev.awaken.mobs.entity.FaunaMob;
import dev.awaken.progression.Progression;
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

    /** A people's own talk and what they'll part with, by the kit their blueprint names. */
    private record Folk(String says, List<String> lines, List<String> overheard, String stockRow, net.minecraft.world.item.Item stock, String sold, String soldOut) {}

    private static final Folk WAX = new Folk("A wax-folk says", SAY, OVERHEARD, "Honey to spare", Items.HONEY_BOTTLE, "Three bottles of honey, still warm.", "They've already sold all the honey they had to spare.");
    private static final Folk TENDERS = new Folk("A moss-tender says", List.of(
            "Don't stand about on the stones. The roots aren't unkind, they just don't know you're not a wall.",
            "We've moved the village four times now. Well, the tree moved it, and we went along.",
            "Sit if you like, but sit on something that isn't growing, and get up every so often.",
            "The roots across the doors will pull back if you walk at them as though you mean it.",
            "You can't get lost in there for long. It closes up behind you, so there's only ever the one way to go.",
            "Nobody planted it. It was just there one morning, and then it was all there was.",
            "We keep the moss off the steps. It's not for anyone. It's just that somebody ought to.",
            "If you hear it creak, it's only growing. If you stop hearing it, I'd start walking."), List.of(
            "A moss-tender mutters, \"That root wasn't there yesterday.\"",
            "Two moss-tenders are arguing about which way the door used to be.",
            "Somewhere close by, someone says, \"Keep your feet moving, I said.\"",
            "A moss-tender sighs, \"Another whole step gone under.\""), "Bark bread to spare", Items.BREAD, "Three loaves of bark bread. It's better than it sounds.", "They've already sold all the bread they can spare.");
    private static final Folk HOBS = new Folk("A wall-hob says", List.of(
            "The front gate hasn't opened since before my grandmother was born. Nobody has the key and nobody's looking for it.",
            "If you want to get up on the walls, go to where the roots came through. They did more harm than any army ever did, and they left a good ladder behind.",
            "Mind the wet stone up top. It's a long way down on the outside.",
            "Those fires along the walls? We keep them going. If they went out we'd have to admit nobody's coming back.",
            "It rains. It has always rained. I've heard about the other thing, but I don't believe it.",
            "We eat well enough. Frogs, mostly. Sometimes you get a bad frog.",
            "The ones up at the top don't come down, and we don't go up. It's worked so far.",
            "Those roots are still moving, you know. Slowly. We measure them against the wall every spring."), List.of(
            "A wall-hob mutters, \"Who let that fire go out on the east side?\"",
            "Two wall-hobs are arguing over who owns a bucket.",
            "Somewhere close by, someone says, \"It's your turn on the wall. It was my turn last year.\"",
            "A wall-hob sighs, \"Leak's got worse.\""), "Smoked eel to spare", Items.COOKED_SALMON, "Three smoked eels. They were in the moat, so don't ask.", "They've already sold all the eel they can spare.");
    private static final Folk KEEPERS = new Folk("A candle-keeper says", List.of(
            "We light them. They burn down. We light the next ones. There are a lot of candles down here.",
            "You get used to the bell. I think you do. I haven't yet.",
            "Mind the doors when it rings. One of us lost a hand that way, but we made him another.",
            "I've tended the same row of niches for longer than I can tell you. I still don't know who's in them.",
            "When the bell stops, listen. There's a little while where you can hear everything.",
            "Nobody has told us to stop, so we haven't stopped.",
            "Some of the tombs are empty now. We try not to think about where they went.",
            "Wax is warmer than stone. You'll want to remember that if you sleep down here."), List.of(
            "A candle-keeper says quietly, \"That one's guttering. Fetch me another.\"",
            "Two candle-keepers are counting something, and have lost count.",
            "A candle-keeper mutters, \"Nearly time for the bell.\"",
            "Somewhere close by, someone says, \"Who keeps moving the good wick?\"",
            "A candle-keeper sighs, \"Another whole row went out while I wasn't looking.\""),
            "Candles to spare", Items.CANDLE, "Three good candles, still soft from the mould.", "They've already handed over all the candles they can spare.");

    private static Folk folkOf(GateRecord g) {
        RealmPlan plan = Realms.plan(g);
        if (plan != null && plan.bp().folk().kit().equals("moss_tenders")) return TENDERS;
        if (plan != null && plan.bp().folk().kit().equals("wall_hobs")) return HOBS;
        return plan != null && plan.bp().folk().kit().equals("candle_keepers") ? KEEPERS : WAX;
    }

    private static final Map<ServerPlayer, Long> NEXT_OVERHEAR = new WeakHashMap<>(), NEXT_TALK = new WeakHashMap<>();

    private RealmLife() {}

    /** Called each half second for a player in a Realm; the work runs for each player once every two seconds. */
    static void tick(ServerLevel level, ServerPlayer p, GateRecord g, RealmPlan plan, RealmRef ref, int lx, int lz, int t) {
        if ((t / 10 + p.getId()) % 4 != 0) return;
        Blueprint bp = plan.bp();
        for (String herd : bp.life().herds()) herd(level, p, g, plan.land(), ref, herd, lx, lz);
        for (String fly : bp.life().ambient()) flies(level, p, g, plan.land(), ref, fly, lx, lz);
        if (bp.folk().villages() > 0) folk(level, p, g, plan.land(), ref, lx, lz, folkOf(g));
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
    private static void folk(ServerLevel level, ServerPlayer p, GateRecord g, Land land, RealmRef ref, int lx, int lz, Folk kit) {
        Land.Site v = land.site(Land.SiteKind.VILLAGE);
        if (v == null || Math.hypot(lx - v.x(), lz - v.z()) > FOLK_WAKE) return;
        BlockPos centre = Realms.world(ref.cell(), v.x(), v.y(), v.z());
        if (!level.isLoaded(centre) || count(level, centre, FOLK, v.radius()) >= FOLK_WANT) {
            overhear(level, p, centre, v, kit);
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

    private static void overhear(ServerLevel level, ServerPlayer p, BlockPos centre, Land.Site v, Folk kit) {
        long now = level.getGameTime();
        if (now < NEXT_OVERHEAR.getOrDefault(p, 0L)) return;
        if (count(level, p.blockPosition(), FOLK, OVERHEAR) == 0) return;
        NEXT_OVERHEAR.put(p, now + OVERHEAR_TICKS + level.getRandom().nextInt(OVERHEAR_TICKS));
        Signal.toast(p, Component.literal(kit.overheard().get(level.getRandom().nextInt(kit.overheard().size()))));
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
        // once you've spoken to one, the plate over their heads says what they are
        Progression.setCounter(p, "awaken:discovered/creature/" + FOLK, 1);
        RealmState st = RealmData.get(level.getServer()).state(g.id);
        Folk kit = folkOf(g);
        String line = kit.lines().get(Math.floorMod(target.getId() + (int) (now / 200), kit.lines().size()));
        var window = SignalWindow.builder(SignalWindow.Kind.GATE, Component.literal(kit.says())).body(Component.literal(line));
        if (st.folkTrades < TRADES) window.row(kit.stockRow(), BOTTLES + " for " + PRICE + " emeralds")
                .choice(Interactions.WAX_TRADE, "Buy them", "Not today");
        Signal.send(p, window.priority(3).ttl(20 * 12).build());
        return true;
    }

    public static void trade(ServerPlayer p, GateRecord g) {
        RealmData data = RealmData.get(p.level().getServer());
        RealmState st = data.state(g.id);
        if (st.folkTrades >= TRADES) {
            Signal.toast(p, Component.literal(folkOf(g).soldOut()));
            return;
        }
        if (!EconomyHooks.charge(p, PRICE)) {
            Signal.toast(p, Component.literal("You don't have " + PRICE + " emeralds."));
            return;
        }
        st.folkTrades++;
        data.setDirty();
        Folk kit = folkOf(g);
        ItemStack goods = new ItemStack(kit.stock(), BOTTLES);
        p.getInventory().placeItemBackInInventory(goods, net.minecraft.util.Prediction.SERVER_ONLY);
        Signal.toast(p, Component.literal(kit.sold()));
    }
}
