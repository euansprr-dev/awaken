package dev.awaken.finds;

import dev.awaken.Awaken;
import dev.awaken.core.CoreModule;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * The relics (SPEC §61.3): one to a monument, found nowhere else, each with one real use. None is a weapon and none is
 * needed for anything: they are what the old world left that still works.
 * <ul>
 * <li>The Counterweight (the Stair That Stops): while you carry it, a long fall is slowed for a moment, once a minute.</li>
 * <li>The Listening Stone (the Listening Dish): used, it tells you which way the nearest monument lies and how far.</li>
 * <li>The Sleeper's Lamp (the Sleepers' Hall): in the off hand, it lets you see in the dark below ground.</li>
 * </ul>
 */
public final class Relics {
    private Relics() {}

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Awaken.MODID);

    private static Item.Properties relic(Item.Properties p, String... lore) {
        return p.stacksTo(1).rarity(Rarity.RARE).component(DataComponents.LORE,
                new ItemLore(java.util.Arrays.stream(lore).map(s -> (Component) Component.literal(s).withStyle(st -> st.withItalic(false).withColor(0xA9B8B0))).toList()));
    }

    public static final DeferredItem<Item> COUNTERWEIGHT = ITEMS.registerItem("relic_counterweight", Item::new,
            p -> relic(p, "From the Stair That Stops.", "Carry it and a long fall is slowed", "for a moment. It rests a minute after."));
    public static final DeferredItem<Item> LISTENING_STONE = ITEMS.registerItem("relic_listening_stone", ListeningStone::new,
            p -> relic(p, "From the Listening Dish.", "Use it and it leans toward the nearest", "monument of the old world."));
    public static final DeferredItem<Item> SLEEPERS_LAMP = ITEMS.registerItem("relic_sleepers_lamp", Item::new,
            p -> relic(p, "From the Sleepers' Hall.", "Held in your off hand, it lets you", "see in the dark below ground."));

    static String[] ids() {
        return new String[] {"relic_counterweight", "relic_listening_stone", "relic_sleepers_lamp"};
    }

    /** Leans toward the nearest monument: says which way and about how far, then rests ten seconds. */
    static final class ListeningStone extends Item {
        ListeningStone(Properties p) {
            super(p);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            ItemStack stack = player.getItemInHand(hand);
            if (!(level instanceof ServerLevel sl) || !(player instanceof ServerPlayer p)) return InteractionResult.SUCCESS;
            p.getCooldowns().addCooldown(stack, 200);
            Site best = null;
            double bestD = Double.MAX_VALUE;
            for (Kind k : Kinds.monuments()) {
                Site s = Sites.nearestOf(sl, k, p.getBlockX(), p.getBlockZ(), 5);
                if (s == null) continue;
                double d = Math.hypot(s.x() - p.getX(), s.z() - p.getZ());
                if (d > s.kind().reach() + 8 && d < bestD) {      // not the one you are standing in
                    bestD = d;
                    best = s;
                }
            }
            sl.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 0.7F);
            if (best == null) {
                p.sendOverlayMessage(Component.literal("The stone lies still. Nothing of the old world is near."));
                return InteractionResult.SUCCESS;
            }
            double a = Math.toDegrees(Math.atan2(best.x() - p.getX(), -(best.z() - p.getZ())));
            String[] way = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
            int far = (int) (Math.round(bestD / 50.0) * 50);
            p.sendOverlayMessage(Component.literal("The stone leans " + way[Math.floorMod((int) Math.round(a / 45.0), 8)] + ". About " + far + " blocks."));
            return InteractionResult.SUCCESS;
        }
    }

    private static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        // the Counterweight: only looked for once a fall is already long, so it costs nothing on the ground
        if (p.fallDistance > 7 && !p.onGround() && !p.isFallFlying() && !p.getAbilities().flying && !p.hasEffect(MobEffects.SLOW_FALLING)) {
            ItemStack w = find(p, COUNTERWEIGHT.get());
            if (!w.isEmpty() && !p.getCooldowns().isOnCooldown(w)) {
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 70, 0, true, false, true));
                p.getCooldowns().addCooldown(w, 20 * 60);
                p.level().playSound(null, p.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
            }
        }
        // the Sleeper's Lamp: once a second, in the off hand, under a roof and below the sea's level
        if (p.tickCount % 20 == 0 && p.getOffhandItem().is(SLEEPERS_LAMP.get()) && p.getY() < p.level().getSeaLevel() - 3
                && !p.level().canSeeSky(p.blockPosition())) {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, true, false, false));
        }
    }

    private static ItemStack find(ServerPlayer p, Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return inv.getItem(i);
        return ItemStack.EMPTY;
    }

    static void init(IEventBus modBus) {
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (!e.getTabKey().equals(CoreModule.TAB_KEY)) return;
            for (var i : List.of(COUNTERWEIGHT, LISTENING_STONE, SLEEPERS_LAMP)) e.accept(i.get());
        });
        NeoForge.EVENT_BUS.addListener(Relics::tick);
    }
}
