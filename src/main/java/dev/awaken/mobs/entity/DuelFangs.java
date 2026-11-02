package dev.awaken.mobs.entity;

import dev.awaken.Awaken;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * A duel boss's fangs bite for its own ability's damage (SPEC §46.5), not vanilla's flat 6: a Lord tuned to hit softly
 * isn't out-hit by the roots it raises. Told and dodged as before; a deflect still takes all of it.
 */
public final class DuelFangs {
    private DuelFangs() {}

    static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Awaken.MODID);
    /** What this fang bites for; 0 for a fang that isn't a duel's (not saved: a fang lives a second). */
    static final Supplier<AttachmentType<Float>> BITE = ATTACHMENTS.register("mobs_fang_bite",
            () -> AttachmentType.builder(() -> 0.0F).build());

    public static void init(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        // first, so armour, blocks and every reduction after work from the boss's own number
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, LivingIncomingDamageEvent.class, e -> {
            if (e.getSource().getDirectEntity() instanceof EvokerFangs f && f.hasData(BITE)) e.setAmount(f.getData(BITE));
        });
    }

    static void mark(EvokerFangs fang, float bite) {
        fang.setData(BITE, bite);
    }
}
