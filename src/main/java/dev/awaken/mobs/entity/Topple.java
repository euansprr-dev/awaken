package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import dev.awaken.core.api.CreatureTag;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Big beasts topple (SPEC §46.5, PLAN §4.6): a named beast or a hunt quarry whose stance breaks goes down on its side
 * for the break, the opening, and gets up as it ends. The server holds it still (its stagger); everyone who sees it is
 * sent when it went down and when it gets up, and draws the fall from that.
 */
public final class Topple {
    private Topple() {}

    /** A stagger this long or longer is a broken stance (Arms: 50 ticks, a Lord's 40); armour's staggers are shorter. */
    public static final int BREAK = 40;
    /** Ticks to go over onto its side (accelerating, one bounce after), and to get back up at the end. */
    public static final int FALL = 9, BOUNCE = 4, RISE = 12;

    /** Down from {@code from} until {@code until} (game ticks); not saved: a beast that's unloaded mid-topple stands. */
    public record Down(long from, long until) {
        public static final Down NONE = new Down(0, 0);
        static final StreamCodec<ByteBuf, Down> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_LONG, Down::from,
                ByteBufCodecs.VAR_LONG, Down::until, Down::new);
    }

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    public static final Supplier<AttachmentType<Down>> DOWN = ATTACHMENTS.register("mobs_down",
            () -> AttachmentType.builder(() -> Down.NONE).sync(Down.STREAM_CODEC).build());

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    /** A body that topples when its stance breaks: a named beast, or a hunt quarry (a herd's or a pack's alpha). */
    public static boolean big(LivingEntity e) {
        CreatureTag t = CreatureTag.of(e);
        return t.kind() == CreatureTag.Kind.BEAST || t.elite() && (t.kind() == CreatureTag.Kind.PASSIVE
                || t.kind() == CreatureTag.Kind.NEUTRAL || t.kind() == CreatureTag.Kind.HUNTER);
    }

    /**
     * Puts a big body down for a stagger of {@code ticks} if that's a broken stance; a break held longer (a Weave
     * through it) keeps it down without falling again. True when it's down.
     */
    public static boolean knock(LivingEntity e, int ticks) {
        if (ticks < BREAK || !e.isAlive() || !e.onGround() || !big(e)) return false; // in the air or the water it doesn't roll over
        long now = e.level().getGameTime();
        Down d = e.getData(DOWN);
        boolean already = d.until() > now;
        e.setData(DOWN, new Down(already ? d.from() : now, Math.max(d.until(), now + ticks)));
        return true;
    }

    public static boolean down(LivingEntity e) {
        return e.hasData(DOWN) && e.getData(DOWN).until() > e.level().getGameTime();
    }

    /** How far over it lies at game time {@code now} (with the partial tick): 0 standing, 1 on its side. */
    public static float over(Down d, float now) {
        if (d.until() <= 0 || now >= d.until() || now < d.from()) return 0.0F;
        float t = now - d.from(), left = d.until() - now, a;
        if (t < FALL) {
            float k = t / FALL;
            a = k * k; // its weight goes over: slow, then all at once
        } else if (t < FALL + BOUNCE) a = 1.0F - 0.1F * Mth.sin(Mth.PI * (t - FALL) / BOUNCE); // it lands and bounces once
        else a = 1.0F;
        if (left < RISE) {
            float k = left / RISE;
            a = Math.min(a, k * k * (3.0F - 2.0F * k)); // it heaves itself up
        }
        return a;
    }

    /** How far over it lies now, for a client drawing it (0 when it stands). */
    public static float over(LivingEntity e, float partialTick) {
        return e.hasData(DOWN) ? over(e.getData(DOWN), e.level().getGameTime() + partialTick) : 0.0F;
    }

    /** Which side it goes over on (the same side every time for one body). */
    public static float side(Entity e) {
        return (e.getId() & 1) == 0 ? 1.0F : -1.0F;
    }
}
