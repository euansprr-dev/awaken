package dev.awaken.finds;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import dev.awaken.finds.kit.Olden;
import dev.awaken.finds.verb.Ctx;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The relics (SPEC §62.7): one to a family of monuments, found nowhere else, each with one real use. A relic is given
 * the first time any monument of its family is finished, and it grows a step with each further one of the family's
 * five: its step is simply how many of them the player has done, so nothing about it is saved on the item and a relic
 * that was lost and given again is as strong as the one it replaces. None is a weapon and none is needed for anything:
 * they are what the old world left that still works.
 */
public final class Relics {
    private Relics() {}

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);
    private static final List<String> IDS = new java.util.ArrayList<>();

    private static Item.Properties relic(Item.Properties p, String... lore) {
        return p.stacksTo(1).rarity(Rarity.RARE).component(DataComponents.LORE,
                new ItemLore(java.util.Arrays.stream(lore).map(s -> (Component) Component.literal(s).withStyle(st -> st.withItalic(false).withColor(0xA9B8B0))).toList()));
    }

    private static DeferredItem<Item> plain(String id, String... lore) {
        IDS.add(id);
        return ITEMS.registerItem(id, Item::new, p -> relic(p, lore));
    }

    public static final DeferredItem<Item> COUNTERWEIGHT = plain("relic_counterweight", "From the stairs that stop.", "Carry it and a long fall is slowed", "for a moment. It rests after.");
    public static final DeferredItem<Item> LISTENING_STONE = used("relic_listening_stone", ListeningStone::new, "From the listening dishes.", "Use it and it leans toward the nearest", "monument of the old world.");
    public static final DeferredItem<Item> SLEEPERS_LAMP = plain("relic_sleepers_lamp", "From the sleepers' halls.", "Held in your off hand, it lets you", "see in the dark under a roof.");
    public static final DeferredItem<Item> WAYSTONE = plain("relic_waystone", "From the long road.", "Carry it and running on a road", "or a path tires you less.");
    public static final DeferredItem<Item> READING_GLASS = plain("relic_reading_glass", "From the archives.", "Carry it and every carving you read", "for the first time teaches you something.");
    public static final DeferredItem<Item> MOORING_RING = plain("relic_mooring_ring", "From the mooring masts.", "Carry it and blasts and blows", "push you back less.");
    public static final DeferredItem<Item> POCKET_RINGS = used("relic_pocket_rings", PocketRings::new, "From the orreries.", "Use them and they tell you the hour", "and the way home.");
    public static final DeferredItem<Item> NIGHT_BLOOM = plain("relic_night_bloom", "From the dream gardens.", "Carry it, and in the dark you mend", "while you stand still.");
    public static final DeferredItem<Item> FLOAT = plain("relic_float", "From the water clocks.", "Carry it and you last longer under", "water and move faster through it.");
    public static final DeferredItem<Item> SEAL_SHARD = plain("relic_seal_shard", "From the sealed shafts.", "Carry it and the first touch of fire", "does not burn you. It rests after.");
    public static final DeferredItem<Item> LINK = plain("relic_link", "From the chain works.", "Carry it and what lies on the ground", "comes to your hand from further off.");
    public static final DeferredItem<Item> DIGGERS_GLASS = plain("relic_diggers_glass", "From the doors in the sand.", "Carry it and sand, gravel and earth", "give way faster.");

    private static DeferredItem<Item> used(String id, java.util.function.Function<Item.Properties, Item> make, String... lore) {
        IDS.add(id);
        return ITEMS.registerItem(id, make, p -> relic(p, lore));
    }

    static String[] ids() {
        return IDS.toArray(String[]::new);
    }

    // ---- who carries what ---------------------------------------------------------------------------------------------

    private static Map<Item, Family> byItem;

    private static Map<Item, Family> byItem() {
        if (byItem == null) {
            Map<Item, Family> m = new java.util.IdentityHashMap<>();
            for (Family f : Family.values()) m.put(f.relic(), f);
            byItem = m;
        }
        return byItem;
    }

    /** What a player carries, looked up once a second: the step of each family's relic, 0 where it isn't carried. */
    private record Carried(int[] step, int hands, double x, double y, double z) {
        static final Carried NONE = new Carried(new int[Family.values().length], 0, 0, 0, 0);

        int of(Family f) {
            return step[f.ordinal()];
        }
    }

    private static final Map<UUID, Carried> CARRIED = new ConcurrentHashMap<>();

    private static Carried scan(Player p) {
        int[] step = new int[Family.values().length];
        var inv = p.getInventory();
        FindsData d = null;
        int hands = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            Family f = byItem().get(st.getItem());
            if (f == null || step[f.ordinal()] > 0) continue;
            if (d == null) d = p.getData(FindsContent.FINDS);
            step[f.ordinal()] = Math.max(1, Math.min(5, f.done(d)));
        }
        Family off = byItem().get(p.getOffhandItem().getItem()), main = byItem().get(p.getMainHandItem().getItem());
        if (off != null) hands |= 1 << off.ordinal();
        if (main != null) hands |= 1 << (16 + main.ordinal());
        return new Carried(step, hands, p.getX(), p.getY(), p.getZ());
    }

    /** The step (1..5) of a family's relic as a player carries it now, or 0. Works on either side: the client knows its own finds. */
    public static int step(Player p, Family f) {
        if (p instanceof ServerPlayer) {
            Carried c = CARRIED.get(p.getUUID());
            if (c != null) return c.of(f);
        }
        return scan(p).of(f);
    }

    // ---- two that are used ----------------------------------------------------------------------------------------------

    private static final String[] WAY = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};

    private static String way(double fromX, double fromZ, double toX, double toZ) {
        double a = Math.toDegrees(Math.atan2(toX - fromX, -(toZ - fromZ)));
        return WAY[Math.floorMod((int) Math.round(a / 45.0), 8)];
    }

    private static int about(double d) {
        return (int) (Math.round(d / 50.0) * 50);
    }

    /** Leans toward the nearest monument: which way and about how far. Each step it hears further; at three it knows whether you have been there, at five what it is. */
    static final class ListeningStone extends Item {
        ListeningStone(Properties p) {
            super(p);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
            int step = Math.max(1, scan(p).of(Family.DISH));
            p.getCooldowns().addCooldown(stack, 200);
            Site best = Sites.monumentFrom(sl, p.getBlockX(), p.getBlockZ(), 700 + 500 * step);
            sl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 0.7F);
            if (best == null) {
                p.sendOverlayMessage(Component.literal("The stone lies still. Nothing of the old world is near enough to hear."));
                return InteractionResult.SUCCESS;
            }
            double d = Math.hypot(best.x() - p.getX(), best.z() - p.getZ());
            String more = "";
            if (step >= 5 && best.kind().family() != null) more = " It sounds like one of " + best.kind().family().display.replaceFirst("^The ", "the ") + ".";
            else if (step >= 3) more = p.getData(FindsContent.FINDS).paidAt(best.key()) ? " You have been there." : " You have not been there.";
            p.sendOverlayMessage(Component.literal("The stone leans " + way(p.getX(), p.getZ(), best.x(), best.z()) + ". About " + about(d) + " blocks." + more));
            return InteractionResult.SUCCESS;
        }
    }

    /** The hour and the way home; at two the weather, at three how long the light lasts, at four the way to where you last fell. */
    static final class PocketRings extends Item {
        PocketRings(Properties p) {
            super(p);
        }

        private static String hour(long t) {
            t = Math.floorMod(t, 24000L);
            return t < 1000 ? "just after dawn" : t < 5000 ? "morning" : t < 7000 ? "the middle of the day" : t < 11000 ? "afternoon" : t < 13000 ? "dusk" : t < 17000 ? "evening" : t < 19000 ? "the middle of the night" : t < 23000 ? "the small hours" : "nearly dawn";
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
            int step = Math.max(1, scan(p).of(Family.ORRERY));
            p.getCooldowns().addCooldown(stack, 100);
            sl.playSound(null, p.blockPosition(), SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.9F, 0.8F);
            long t = Math.floorMod(sl.getOverworldClockTime(), 24000L);
            StringBuilder b = new StringBuilder("It is " + hour(t) + ".");
            var cfg = p.getRespawnConfig();
            var home = cfg != null ? cfg.respawnData().globalPos() : sl.getRespawnData().globalPos();
            if (home.dimension() == sl.dimension()) {
                double d = Math.hypot(home.pos().getX() - p.getX(), home.pos().getZ() - p.getZ());
                b.append(d < 40 ? " You are home." : " Home lies " + way(p.getX(), p.getZ(), home.pos().getX(), home.pos().getZ()) + ", about " + about(d) + " blocks.");
            }
            if (step >= 2) b.append(sl.isThundering() ? " There is a storm." : sl.isRaining() ? " It is raining." : " The sky is clear.");
            if (step >= 3) {
                long left = t < 12500 ? 12500 - t : 24000 - t + 0;
                b.append(t < 12500 ? " Dark in about " : " Light in about ").append(Math.max(1, Math.round(left / 1200.0))).append(" minutes.");
            }
            if (step >= 4) {
                var fell = p.getLastDeathLocation();
                if (fell.isPresent() && fell.get().dimension() == sl.dimension()) {
                    BlockPos f = fell.get().pos();
                    b.append(" You last fell ").append(way(p.getX(), p.getZ(), f.getX(), f.getZ())).append(", about ").append(about(Math.hypot(f.getX() - p.getX(), f.getZ() - p.getZ()))).append(" blocks.");
                }
            }
            p.sendOverlayMessage(Component.literal(b.toString()));
            return InteractionResult.SUCCESS;
        }
    }

    // ---- the ones that work while carried -------------------------------------------------------------------------------

    private static final int[] FALL_REST = {1200, 900, 600, 300, 0}, FIRE_REST = {3000, 2400, 1800, 1200, 600};

    private static void modifier(ServerPlayer p, net.minecraft.core.Holder<Attribute> attr, String id, double amount) {
        AttributeInstance a = p.getAttribute(attr);
        if (a == null) return;
        var key = Awaken.id(id);
        if (amount <= 0) {
            if (a.hasModifier(key)) a.removeModifier(key);
            return;
        }
        var has = a.getModifier(key);
        if (has == null || has.amount() != amount) a.addOrUpdateTransientModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private static boolean road(BlockState b) {
        return b.is(Blocks.DIRT_PATH) || b.is(Blocks.GRAVEL) || b.is(Blocks.COBBLESTONE) || b.is(BlockTags.STONE_BRICKS) || b.is(Blocks.COARSE_DIRT)
                || b.is(Olden.FLAG.get()) || b.is(Olden.FLAG_SLAB.get()) || b.is(Olden.MOSAIC.get());
    }

    private static ItemStack find(ServerPlayer p, Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return inv.getItem(i);
        return ItemStack.EMPTY;
    }

    private static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Carried c = CARRIED.get(p.getUUID());
        boolean second = c == null || p.tickCount % 20 == 0;
        Carried was = c;
        if (second) {
            c = scan(p);
            CARRIED.put(p.getUUID(), c);
            everySecond(p, c, was);
        }
        int fall = c.of(Family.STAIR), fire = c.of(Family.SHAFT), link = c.of(Family.CHAINS);
        // the Counterweight: only looked for once a fall is already long, so it costs nothing on the ground
        if (fall > 0 && p.fallDistance > 7 && !p.onGround() && !p.isFallFlying() && !p.getAbilities().flying && !p.hasEffect(MobEffects.SLOW_FALLING)) {
            ItemStack w = find(p, COUNTERWEIGHT.get());
            if (!w.isEmpty() && !p.getCooldowns().isOnCooldown(w)) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 70, 0, true, false, true));
                if (FALL_REST[fall - 1] > 0) p.getCooldowns().addCooldown(w, FALL_REST[fall - 1]);
                p.level().playSound(null, p.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
            }
        }
        // the Seal Shard: the first touch of fire
        if (fire > 0 && (p.isOnFire() || p.isInLava()) && !p.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            ItemStack w = find(p, SEAL_SHARD.get());
            if (!w.isEmpty() && !p.getCooldowns().isOnCooldown(w)) {
                p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 80 + 40 * fire, 0, true, false, true));
                p.clearFire();
                p.getCooldowns().addCooldown(w, FIRE_REST[fire - 1]);
                p.level().playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.7F);
            }
        }
        // the Link: what lies near comes to hand (not what you have just thrown down: that waits out its own delay)
        if (link > 0 && p.tickCount % 4 == 0 && !p.isShiftKeyDown() && !p.isSpectator()) {
            double r = 2.5 + 1.2 * link;
            for (ItemEntity it : p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(r), i -> i.isAlive() && !i.hasPickUpDelay())) {
                Vec3 to = p.position().add(0, 0.6, 0).subtract(it.position());
                double d = to.length();
                if (d < 1.2 || d > r) continue;
                it.setDeltaMovement(it.getDeltaMovement().scale(0.6).add(to.scale(0.22 / d)));
            }
        }
    }

    private static void everySecond(ServerPlayer p, Carried c, Carried was) {
        ServerLevel level = (ServerLevel) p.level();
        // the Mooring Ring and the Float are part of how the player stands and swims while they are carried
        int ring = c.of(Family.MAST), fl = c.of(Family.CLOCK);
        modifier(p, Attributes.KNOCKBACK_RESISTANCE, "relic_mooring", 0.12 * ring);
        modifier(p, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, "relic_mooring_blast", 0.15 * ring);
        modifier(p, Attributes.WATER_MOVEMENT_EFFICIENCY, "relic_float", 0.12 * fl);
        if (fl > 0 && p.isUnderWater() && p.getAirSupply() < p.getMaxAirSupply()) p.setAirSupply(Math.min(p.getMaxAirSupply(), p.getAirSupply() + 4 * fl));
        // the Waystone: a road gives back part of what running on it takes
        int way = c.of(Family.ROAD);
        if (way > 0 && p.isSprinting() && p.onGround() && (road(level.getBlockState(p.getOnPos())) || road(level.getBlockState(p.blockPosition())))) p.getFoodData().addExhaustion(-0.11F * way);
        // the Night Bloom: in the dark, standing still
        int bloom = c.of(Family.GARDEN);
        if (bloom > 0 && was != null && p.getHealth() < p.getMaxHealth() && p.tickCount % (20 * (6 - bloom)) == 0
                && Math.abs(was.x() - p.getX()) + Math.abs(was.y() - p.getY()) + Math.abs(was.z() - p.getZ()) < 0.05
                && level.getMaxLocalRawBrightness(p.blockPosition()) < 8) {
            p.heal(1.0F);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.3, 0.4, 0.3, 0.01);
        }
        // the Sleeper's Lamp: each step it asks less of you
        int lamp = c.of(Family.HALL);
        if (lamp > 0) {
            boolean off = (c.hands() & (1 << Family.HALL.ordinal())) != 0, main = (c.hands() & (1 << (16 + Family.HALL.ordinal()))) != 0;
            boolean held = off || (lamp >= 3 && main) || lamp >= 4;
            boolean roofed = !level.canSeeSky(p.blockPosition());
            boolean where = lamp >= 5 ? roofed || level.isDarkOutside() : lamp >= 2 ? roofed : roofed && p.getY() < level.getSeaLevel() - 3;
            if (held && where) p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
        }
    }

    /** The Digger's Glass: soft ground gives way faster. Both sides ask, so the dig looks as fast as it is. */
    private static void breakSpeed(PlayerEvent.BreakSpeed e) {
        if (!e.getState().is(BlockTags.MINEABLE_WITH_SHOVEL)) return;
        int s = step(e.getEntity(), Family.DOOR);
        if (s > 0) e.setNewSpeed(e.getNewSpeed() * (1 + 0.2F * s));
    }

    /** The Reading Glass: a carving read for the first time is worth a little, more each step. Called by the carvings. */
    public static void read(ServerPlayer p, Kind k, int index) {
        int s = scan(p).of(Family.ARCHIVE);
        if (s > 0) Progression.discover(p, "carving/" + k.id() + "/" + index, 0.008 * s);
    }

    // ---- earning them -------------------------------------------------------------------------------------------------

    /**
     * A monument was finished by this player for the first time at this site. The first of a family gives its relic;
     * each further one of the family's five grows it a step, and says so; all five are a whole level. A player who no
     * longer carries the relic is given it again.
     */
    static void earned(Ctx c, ServerPlayer p, Kind k, BlockPos from, boolean firstOfKind) {
        Family f = k.family();
        if (f == null) return;
        int step = Math.min(5, f.done(p.getData(FindsContent.FINDS)));
        boolean has = !find(p, f.relic()).isEmpty();
        if (!has) c.pop(from, new ItemStack(f.relic()));
        if (!firstOfKind) return;
        String name = f.relicName;
        if (step <= 1) p.sendOverlayMessage(Component.literal(name + " is yours. It is one of five steps."));
        else if (step < 5) p.sendOverlayMessage(Component.literal(name + " grows stronger. Step " + step + " of five."));
        else {
            p.sendOverlayMessage(Component.literal(name + " is whole. You have found all five of " + f.display.replaceFirst("^The ", "the ") + "."));
            Progression.discover(p, "family/" + f.name().toLowerCase(java.util.Locale.ROOT), 1.0);
        }
        CARRIED.remove(p.getUUID());
    }

    /** A line for a relic's tooltip: its step, as the holder's own finds have it. */
    public static @org.jspecify.annotations.Nullable Component stepLine(Player p, ItemStack stack) {
        Family f = byItem().get(stack.getItem());
        if (f == null) return null;
        int n = Math.max(1, Math.min(5, f.done(p.getData(FindsContent.FINDS))));
        return Component.literal("Step " + n + " of 5").withStyle(st -> st.withColor(0xE8C36A).withItalic(false));
    }

    static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (!e.getTabKey().equals(CoreModule.TAB_KEY)) return;
            for (Family f : Family.values()) e.accept(f.relic());
        });
        NeoForge.EVENT_BUS.addListener(Relics::tick);
        NeoForge.EVENT_BUS.addListener(Relics::breakSpeed);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> CARRIED.remove(e.getEntity().getUUID()));
    }
}
