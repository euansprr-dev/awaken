package dev.awaken.finds;

import dev.awaken.Awaken;
import dev.awaken.finds.verb.Ctx;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Finds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Paying a find out (SPEC §58.6). Everyone near who hasn't been paid at this site is paid: loot tossed out of the
 * site's cache (or at their feet), the kind counted in their Journal, and the first of a kind a discovery, told with
 * the game's own find card. Once per player per site, whoever did the work.
 */
public final class Reward {
    private Reward() {}

    private static final String[] RANKS = {"e", "d", "c", "b", "a"};

    /** The loot table of a kind: its own when it names one, else the tier for its rank and size. */
    public static ResourceKey<LootTable> table(Kind k) {
        String name = k.loot().isEmpty() ? RANKS[Math.min(4, k.rank())] + (k.size() == Kind.Size.CURIO ? "_small" : "_big") : k.loot();
        return ResourceKey.create(Registries.LOOT_TABLE, Awaken.id("finds/" + name));
    }

    public static void pay(Ctx c, @Nullable ServerPlayer by) {
        List<ServerPlayer> who = new ArrayList<>(c.players(Math.max(20, c.kind().reach() + 8)));
        if (by != null) {
            who.remove(by);
            who.add(0, by);
        }
        BlockPos from = c.has("cache") ? c.pos("cache", 0) : by != null ? by.blockPosition() : c.site.ground().above();
        c.kind().after().accept(c);
        open(c);
        boolean any = false;
        for (ServerPlayer p : who) {
            if (c.heart.claimed(p.getUUID())) continue;
            c.heart.claim(p.getUUID());
            one(c, p, from);
            any = true;
        }
        if (any) {
            c.sound(from, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 0.8F);
            c.puff(from.above(), ParticleTypes.WAX_OFF, 10, 0.35, 0.02);
        }
    }

    /** A cache that has a way of standing open (a drawer, a lid, a bundle) opens. */
    private static void open(Ctx c) {
        if (!c.has("cache")) return;
        var s = c.at("cache", 0);
        if (s.getBlock() instanceof dev.awaken.finds.kit.PropBlock && dev.awaken.finds.kit.Props.opens(s.getBlock()) && s.getValue(dev.awaken.finds.kit.PropBlock.STATE) == 0)
            c.set("cache", 0, s.setValue(dev.awaken.finds.kit.PropBlock.STATE, 1));
    }

    /** A player who comes to a find someone else already finished takes their own share from its cache. */
    public static boolean late(Ctx c, ServerPlayer p) {
        if (!c.done() || c.heart.claimed(p.getUUID())) return false;
        c.heart.claim(p.getUUID());
        BlockPos from = c.has("cache") ? c.pos("cache", 0) : p.blockPosition();
        one(c, p, from);
        c.sound(from, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 0.8F);
        return true;
    }

    private static void one(Ctx c, ServerPlayer p, BlockPos from) {
        Kind k = c.kind();
        FindsData d = p.getData(FindsContent.FINDS);
        p.setData(FindsContent.FINDS, d.withDone(k.id(), c.site.key()).withoutLeadTo(c.site.key()));
        LootTable table = c.level.getServer().reloadableRegistries().getLootTable(table(k));
        table.getRandomItems(new LootParams.Builder(c.level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(from))
                .withParameter(LootContextParams.THIS_ENTITY, p).create(LootContextParamSets.CHEST), stack -> c.pop(from, stack));
        dev.awaken.core.api.Promises.kept(p, "find/" + c.site.key());
        Progression.count(p, "awaken:finds_done");
        if (!Finds.counts(p)) return;
        if (k.size() == Kind.Size.PLACE) table.getRandomItems(new LootParams.Builder(c.level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(from))
                .withParameter(LootContextParams.THIS_ENTITY, p).create(LootContextParamSets.CHEST), stack -> c.pop(from, stack)); // a place pays twice over
        boolean fresh = Progression.discover(p, k.key(), (k.size() == Kind.Size.CURIO ? 0.06 : k.size() == Kind.Size.FIND ? 0.12 : 0.3) + 0.02 * k.rank());
        if (fresh) {
            Progression.markNewThing(p, k.name());
            Finds.tell(p, Finds.Find.of("find", "New find · " + k.name(), k.name(), k.learned(), 0xE8C36A, true));
        }
        if (k.lead()) Leads.give(c, p);
    }
}
