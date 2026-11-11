package dev.awaken.enchant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.Awaken;
import dev.awaken.core.api.CreatureTag;
import dev.awaken.core.api.MobRole;
import dev.awaken.core.api.RoadHooks;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The rest of the new words (SPEC §45.16; Lantern has its own class): Regrowth mends a piece under open sky by day or
 * in rain, Remembrance keeps the names of the Lords and named beasts a piece has brought down, Gather brings what you
 * break and kill to your pack, Wayfarer speeds you on roads and paths and takes the hunger out of running there. Long
 * Arm is data alone (a reach attribute). Each makes something happen that you can watch. Server only.
 */
final class Words {
    private Words() {}

    private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    // ---- Regrowth -----------------------------------------------------------------------------------------------------

    /** Seconds between one use mended, by level. */
    static final int[] REGROWTH_SECONDS = {0, 12, 8, 5};
    static final int REGROWTH_COLOUR = 0x9FE07A;

    /** Whether Regrowth works where this player stands: open sky by day, or rain falling on them. */
    static boolean grows(ServerLevel level, BlockPos at) {
        return level.isRainingAt(at) || level.isBrightOutside() && level.canSeeSky(at);
    }

    private static void regrowth(ServerPlayer p, ServerLevel level) {
        long second = p.tickCount / 20;
        boolean asked = false, grows = false;
        for (EquipmentSlot slot : WORN) {
            ItemStack s = p.getItemBySlot(slot);
            if (s.isEmpty() || !s.isDamaged()) continue;
            int lv = Math.min(3, EnchantEffects.levelOf(s, EnchantContent.REGROWTH.get()));
            if (lv <= 0 || second % REGROWTH_SECONDS[lv] != 0 || EnchantFx.has(s, Enchantments.MENDING)) continue;
            if (!asked) {
                asked = true;
                grows = grows(level, BlockPos.containing(p.getX(), p.getEyeY(), p.getZ()));
            }
            if (!grows) return;
            s.setDamageValue(s.getDamageValue() - 1);
            EnchantFx.regrew(p, slot);
        }
    }

    // ---- Wayfarer -----------------------------------------------------------------------------------------------------

    /** How much faster you run on a road or a path, by level. */
    static final double[] WAYFARER_SPEED = {0, 0.08, 0.14, 0.20};
    private static final Identifier WAYFARER = Awaken.id("enchantment.wayfarer");
    /** Where each wearer was at the last look (x, z), to give back the hunger running there cost. */
    private static final Map<UUID, double[]> LAST = new HashMap<>();

    /** A road laid by the world's roads, or a path under the feet. */
    static boolean onRoad(ServerLevel level, Player p) {
        BlockPos under = p.getBlockPosBelowThatAffectsMyMovement();
        if (level.getBlockState(under).is(Blocks.DIRT_PATH) || level.getBlockState(p.blockPosition()).is(Blocks.DIRT_PATH)) return true;
        return RoadHooks.nearestRoad(level, p.blockPosition(), 1).isPresent();
    }

    private static void wayfarer(ServerPlayer p, ServerLevel level) {
        int lv = Math.min(3, EnchantEffects.levelOf(p.getItemBySlot(EquipmentSlot.FEET), EnchantContent.WAYFARER.get()));
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean on = lv > 0 && p.onGround() && !p.isPassenger() && onRoad(level, p);
        AttributeModifier has = speed.getModifier(WAYFARER);
        if (!on) {
            // a jump leaves the ground for a few ticks: the word holds through it, and goes when you land off the road
            if (has != null && (lv <= 0 || p.onGround())) speed.removeModifier(WAYFARER);
            if (lv <= 0) LAST.remove(p.getUUID());
            return;
        }
        double want = WAYFARER_SPEED[lv];
        if (has == null || has.amount() != want) speed.addOrUpdateTransientModifier(new AttributeModifier(WAYFARER, want, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        double[] last = LAST.computeIfAbsent(p.getUUID(), k -> new double[]{p.getX(), p.getZ()});
        double dx = p.getX() - last[0], dz = p.getZ() - last[1], gone = Math.sqrt(dx * dx + dz * dz);
        last[0] = p.getX();
        last[1] = p.getZ();
        if (gone < 0.2 || gone > 6) return;
        // running there costs no hunger: what the last steps took is given back (a sprint costs a tenth a block)
        if (p.isSprinting()) p.getFoodData().addExhaustion((float) (-0.1 * gone));
        level.sendParticles(ColorParticleOption.create(EnchantContent.LETTER.get(), 0xFF000000 | Family.CARRYING.colour),
                p.getX() - dx * 0.4, p.getY() + 0.08, p.getZ() - dz * 0.4, 2, 0.12, 0.02, 0.12, 0.0);
    }

    static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !p.isAlive() || p.isSpectator()) return;
        ServerLevel level = (ServerLevel) p.level();
        if (p.tickCount % 5 == 0) wayfarer(p, level);
        if (p.tickCount % 20 == 0) regrowth(p, level);
    }

    static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        LAST.remove(e.getEntity().getUUID());
    }

    // ---- Gather -------------------------------------------------------------------------------------------------------

    /** How far a thing can be and still come to you, by level. */
    static final int[] GATHER_REACH = {0, 8, 12};

    static int gather(ItemStack tool) {
        return Math.min(2, EnchantEffects.levelOf(tool, EnchantContent.GATHER.get()));
    }

    /** What fits goes into the pack (the rest falls where it would have); a thread for the first few. */
    static int pull(ServerPlayer p, Collection<ItemEntity> drops, double x, double y, double z) {
        int taken = 0;
        for (Iterator<ItemEntity> it = drops.iterator(); it.hasNext(); ) {
            ItemEntity d = it.next();
            ItemStack s = d.getItem().copy();
            if (s.isEmpty() || !p.getInventory().add(s)) continue;
            if (s.isEmpty()) it.remove();
            else d.setItem(s);
            if (taken++ < 3) EnchantFx.gathered(p, x, y, z);
        }
        if (taken > 0) p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.25F, 1.3F);
        return taken;
    }

    static void onBlockDrops(BlockDropsEvent e) {
        if (!(e.getBreaker() instanceof ServerPlayer p) || e.getDrops().isEmpty()) return;
        int lv = gather(e.getTool());
        BlockPos pos = e.getPos();
        if (lv <= 0 || p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > GATHER_REACH[lv] * GATHER_REACH[lv]) return;
        pull(p, e.getDrops(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    static void onLivingDrops(LivingDropsEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || e.getDrops().isEmpty()) return;
        // never another player's pack, and never what a Lord or a named beast leaves: that lies there for everyone who fought
        if (dead instanceof Player || EnchantEffects.spared(dead)) return;
        int lv = gather(p.getMainHandItem());
        if (lv <= 0 || p.distanceToSqr(dead) > GATHER_REACH[lv] * GATHER_REACH[lv]) return;
        pull(p, e.getDrops(), dead.getX(), dead.getY() + dead.getBbHeight() * 0.5, dead.getZ());
    }

    // ---- Remembrance --------------------------------------------------------------------------------------------------

    /** The names a piece keeps (item component {@code awaken:remembered}), oldest first, twelve at most. */
    public record Remembered(List<String> names) {
        public static final int MAX = 12, SCHEMA = 1;
        public static final Codec<Remembered> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(r -> SCHEMA),
                Codec.STRING.listOf().optionalFieldOf("names", List.of()).forGetter(Remembered::names)).apply(i, (v, n) -> new Remembered(n)));
        public static final StreamCodec<ByteBuf, Remembered> STREAM_CODEC =
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.<ByteBuf, String>list(MAX)).map(Remembered::new, Remembered::names);

        /** With one more name: a name already kept stays where it is, and the oldest goes when there are twelve. */
        Remembered with(String name) {
            if (names.contains(name)) return this;
            List<String> out = new ArrayList<>(names);
            while (out.size() >= MAX) out.removeFirst();
            out.add(name);
            return new Remembered(List.copyOf(out));
        }
    }

    /** How the fallen is named: a gate's Lord as the game says it ("the Bell Warden"), a named beast with its title. */
    static String nameOf(LivingEntity dead) {
        MobRole role = MobRole.of(dead);
        if (role.role() == MobRole.Role.BOSS && !role.theme().isEmpty()) {
            String theme = role.theme().startsWith("awaken:") ? role.theme().substring(7) : role.theme();
            if (theme.equals("lantern_night_market")) theme = "lantern_market";
            String lord = Inscriptions.lordName(theme);
            if (!lord.equals("a Lord")) return lord;
        }
        String name = dead.getName().getString(), title = CreatureTag.of(dead).title();
        return title.isEmpty() || name.contains(title) ? name : name + ", " + title;
    }

    /** Writes the name onto a piece that carries the word. True if it was new to the piece. */
    static boolean remember(ItemStack piece, String name) {
        if (piece.isEmpty() || EnchantEffects.levelOf(piece, EnchantContent.REMEMBRANCE.get()) <= 0) return false;
        Remembered was = piece.getOrDefault(EnchantContent.REMEMBERED.get(), new Remembered(List.of()));
        Remembered now = was.with(name);
        if (now == was) return false;
        piece.set(EnchantContent.REMEMBERED.get(), now);
        return true;
    }

    /** A Lord or a named beast fell to you: the weapon in your hand and each piece you wear that carries the word keep its name. */
    static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || dead instanceof Player || !EnchantEffects.spared(dead)) return;
        String name = nameOf(dead);
        ItemStack first = ItemStack.EMPTY;
        int kept = 0;
        if (remember(p.getMainHandItem(), name)) {
            first = p.getMainHandItem();
            kept++;
        }
        for (EquipmentSlot slot : ARMOUR) {
            ItemStack s = p.getItemBySlot(slot);
            if (!remember(s, name)) continue;
            if (first.isEmpty()) first = s;
            kept++;
        }
        if (kept == 0) return;
        String what = kept == 1 ? "Your " + first.getHoverName().getString() + " remembers " : "Your gear remembers ";
        p.sendSystemMessage(Component.literal(what + name + ".").withColor(Family.KEEPING.colour), false);
        EnchantFx.remembered(p);
    }
}
