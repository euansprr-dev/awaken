package dev.awaken.mobs.entity;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.PowerCoreHooks;
import dev.awaken.mobs.MobTypes;
import dev.awaken.mobs.Mobs;
import dev.awaken.mobs.def.Bestiary;
import dev.awaken.mobs.def.MobDef;
import dev.awaken.progression.Rank;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Lantern Broker: the friendly Gate Lord of the Lantern Night Market. Right-click to trade (essence shards for a
 * Power Core and supplies, boss parts for shards). Sneak and right-click to be offered a duel (accept with Y, or
 * {@code /awaken mobs duel}); only then does it fight, as a full three-phase Gate Lord with Lord drops. If the
 * challenger dies or leaves, the duel ends and the Broker is friendly again.
 */
public class BrokerBoss extends AwakenBoss implements Merchant {
    private @Nullable Player tradingPlayer;
    private @Nullable MerchantOffers offers;
    private @Nullable UUID duelist;

    public BrokerBoss(EntityType<? extends BrokerBoss> type, Level level) {
        super(type, level);
    }

    public boolean dueling() {
        return flag(F_DUEL);
    }

    /** Starts the duel with this player (called from the Signal answer or the command). */
    public void startDuel(ServerPlayer player) {
        if (dueling() || !isAlive()) return;
        duelist = player.getUUID();
        setFlag(F_DUEL, true);
        setTarget(player);
        ensureBar();
        barVisible(true);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.FLAME, getX(), getY() + 1.2, getZ(), 30, 0.6, 1.0, 0.6, 0.05);
            player.sendSystemMessage(Component.literal(def().name + ": ").withColor(def().glow).append(Component.literal(def().intro == null ? "" : def().intro)));
        }
        playLevelSound(SoundEvents.BELL_BLOCK, 1.5F, 0.7F);
    }

    private void endDuel(ServerLevel level) {
        setFlag(F_DUEL, false);
        duelist = null;
        setTarget(null);
        barVisible(false);
        reset(level);
    }

    @Override
    protected void ensureBar() {
        super.ensureBar();
        if (!dueling()) barVisible(false);
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        if (!dueling()) {
            // A merchant between duels: no abilities, no target, idles at its stall.
            if (getTarget() != null) setTarget(null);
            if (abilities != null) abilities.interrupt();
            if (tickCount % 20 == 0 && getHealth() < getMaxHealth()) heal(getMaxHealth() * 0.05F);
            return;
        }
        super.customServerAiStep(level);
        Player p = duelist == null ? null : level.getPlayerByUUID(duelist);
        if (p == null || !p.isAlive() || p.distanceToSqr(this) > (def().arena + 16) * (def().arena + 16)) {
            if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.translatable("awaken.mobs.broker.duel_over").withColor(def().glow));
            endDuel(level);
        } else if (getTarget() == null) {
            setTarget(p);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if (!dueling() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (source.getEntity() instanceof ServerPlayer sp && tickCount % 10 == 0) sp.sendOverlayMessage(Component.translatable("awaken.mobs.broker.ask"));
            return false;
        }
        if (dueling() && source.getEntity() instanceof Player p && duelist != null && !p.getUUID().equals(duelist)) return false;
        return super.hurtServer(level, source, damage);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        return dueling() && duelist != null && target.getUUID().equals(duelist) && super.canAttack(target);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || dueling() || !isAlive()) return super.mobInteract(player, hand);
        if (player instanceof ServerPlayer sp) {
            if (sp.isSecondaryUseActive()) {
                Mobs.offerDuel(sp, this);
            } else if (getTradingPlayer() == null) {
                setTradingPlayer(sp);
                openTradingScreen(sp, getDisplayName(), 1);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ---- Merchant -------------------------------------------------------------------------------------------------

    @Override public void setTradingPlayer(@Nullable Player player) { this.tradingPlayer = player; }
    @Override public @Nullable Player getTradingPlayer() { return tradingPlayer; }

    @Override
    public MerchantOffers getOffers() {
        if (offers == null) offers = buildOffers();
        return offers;
    }

    private MerchantOffers buildOffers() {
        MerchantOffers o = new MerchantOffers();
        Item shard = CoreApi.ESSENCE_SHARD.get();
        Rank rank = Rank.of(Math.min(Rank.SOVEREIGN.ordinal(), def().theme.home.ordinal() + 1));
        ItemStack core = PowerCoreHooks.roll(rank, random);
        if (!core.isEmpty()) o.add(new MerchantOffer(new ItemCost(shard, 12), core, 1, 0, 0.0F));
        o.add(new MerchantOffer(new ItemCost(shard, 2), new ItemStack(Items.LANTERN, 2), 8, 0, 0.05F));
        o.add(new MerchantOffer(new ItemCost(shard, 3), new ItemStack(Items.GOLDEN_CARROT, 6), 8, 0, 0.05F));
        o.add(new MerchantOffer(new ItemCost(shard, 4), new ItemStack(Items.ENDER_PEARL, 2), 6, 0, 0.05F));
        o.add(new MerchantOffer(new ItemCost(shard, 6), new ItemStack(Items.GOLDEN_APPLE), 3, 0, 0.05F));
        o.add(new MerchantOffer(new ItemCost(shard, 5), new ItemStack(Items.FIREWORK_ROCKET, 4), 6, 0, 0.05F));
        // Buys boss parts back for shards, so a part you don't need is never wasted.
        List<MobDef.Part> parts = new ArrayList<>();
        for (MobDef d : Bestiary.all()) parts.addAll(d.parts);
        for (int i = 0; i < 3 && !parts.isEmpty(); i++) {
            MobDef.Part part = parts.remove(random.nextInt(parts.size()));
            Item item = MobTypes.part(part.id());
            if (item != null) o.add(new MerchantOffer(new ItemCost(item, 1), new ItemStack(shard, 6), 4, 0, 0.0F));
        }
        return o;
    }

    @Override public void overrideOffers(MerchantOffers offers) { this.offers = offers; }
    @Override public void notifyTrade(MerchantOffer offer) { offer.increaseUses(); ambientSoundTime = -getAmbientSoundInterval(); }
    @Override public void notifyTradeUpdated(ItemStack itemStack) { }
    @Override public int getVillagerXp() { return 0; }
    @Override public void overrideXp(int xp) { }
    @Override public boolean showProgressBar() { return false; }
    @Override public SoundEvent getNotifyTradeSound() { return SoundEvents.WANDERING_TRADER_YES; }
    @Override public boolean isClientSide() { return level().isClientSide(); }
    @Override public boolean stillValid(Player player) { return getTradingPlayer() == player && isAlive() && player.distanceToSqr(this) < 64; }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (offers != null) out.store("awaken_offers", MerchantOffers.CODEC, offers);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        offers = in.read("awaken_offers", MerchantOffers.CODEC).orElse(null);
        setFlag(F_DUEL, false);
    }
}
