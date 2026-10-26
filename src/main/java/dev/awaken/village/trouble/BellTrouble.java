package dev.awaken.village.trouble;

import dev.awaken.Awaken;
import dev.awaken.core.api.GateHooks;
import dev.awaken.core.api.Notices;
import dev.awaken.delve.DelveContent;
import dev.awaken.living.camp.Camps;
import dev.awaken.signal.Signal;
import dev.awaken.village.VillageService;
import dev.awaken.village.api.Villages;
import dev.awaken.village.plan.VillagePlan;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Bell (SPEC §28.7, PLAN 6.3): the village's bell goes silent, stolen by the riftborn camp grown near it or cracked
 * when a gate broke close by. The call is the silence: at dusk the bell-ringer stands at the rope with nothing to ring.
 * Take it back from the camp (it lies there once the camp's heart falls), or cast a new one: five ingots of bell bronze
 * (copper and amethyst, W2) to the smith, who casts it at the forge. Then haul it up the tower yourself (heavy: you go
 * slow with it in hand) and hang it. It rings dawn and dusk again, a ring of light runs out over the land, gate breaks
 * near the village are warned two play-days ahead, and the blight keeps off its fields.
 */
final class BellTrouble implements Plot {
    static final int SILENT = 0, CASTING = 1;
    /** Bell bronze ingots the smith needs for a new bell. */
    static final int BRONZE = 5;

    static @Nullable String cause(Ctx c, VillageService.Live l) {
        VillagePlan p = l.plan();
        if (p.bell == null) return null;
        TroubleState.Memory mem = c.st().memory(l.key());
        if (mem.bell != 0) return "silent";
        BlockPos o = new BlockPos(p.ox, p.baseY, p.oz);
        // a gate that broke near it cracks the bell (once per gate)
        for (GateHooks.GateView g : GateHooks.near(c.level(), o, 600)) {
            if (!g.state().equals("BROKEN") || mem.get("cracked_" + g.id().hashCode(), 0) != 0) continue;
            mem.data.put("cracked_" + g.id().hashCode(), 1L);
            return "cracked:" + g.id();
        }
        // a riftborn camp grown near it comes for the bronze, one night in eight
        if (c.tod() < 14000 || c.tod() > 20000 || Math.floorMod(Taken.mix(p.seed, c.day(), 23), 8L) != 0) return null;
        for (Camps.Camp camp : Camps.get(c.level().getServer()).all()) {
            if (!camp.resolved() || camp.cleared(c.day()) || !camp.kind.equals("riftborn") || camp.tier < 2) continue;
            if (TroubleDirector.horizontal(o, camp.pos()) < 800) return "stolen:" + camp.id;
        }
        return null;
    }

    static @Nullable BlockPos bellPos(VillageService.Live l) {
        VillagePlan p = l.plan();
        if (p.bell == null) return null;
        return new BlockPos(p.ox + p.bell.bx(), p.baseY + p.bell.lift() + p.bell.bry(), p.oz + p.bell.bz());
    }

    @Override
    public boolean begin(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return false;
        BlockPos bell = bellPos(l);
        if (bell == null || !c.level().isLoaded(bell)) return false;
        BlockState s = c.level().getBlockState(bell);
        if (s.is(Blocks.BELL)) {
            t.put("bell_state", Block.getId(s));
            c.level().setBlock(bell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else if (t.get("bell_state", 0) == 0) {
            t.put("bell_state", Block.getId(Blocks.BELL.defaultBlockState()));
        }
        TroubleState.Memory mem = c.st().memory(l.key());
        boolean stolen = t.cause.startsWith("stolen:");
        mem.bell = stolen ? 1 : 2;
        t.kin = Burning.elderOf(l);
        t.due = c.day() + 6;
        if (stolen) {
            Camps.Camp camp = Camps.get(c.level().getServer()).byId(Integer.parseInt(t.cause.substring(7)));
            if (camp != null) {
                t.place = camp.pos().asLong();
                t.placeName = "the " + camp.name();
                t.put("camp", camp.id);
            }
            Villages.history(c.level().getServer(), l.key(), "Raiders from " + t.placeName + " took the bell in the night.");
        } else {
            t.place = bell.asLong();
            t.placeName = l.plan().name + "'s bell tower";
            // the cracked bell lies at the tower's foot
            Villages.history(c.level().getServer(), l.key(), "The gate's breaking cracked the bell. It came down in pieces.");
        }
        Villages.addMood(c.level().getServer(), l.key(), -8);
        c.st().setDirty();
        return true;
    }

    @Override
    public void tick(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return;
        // dusk: the ringer at the rope with nothing to ring, the green looking up at the tower
        BlockPos bell = bellPos(l);
        VillagePlan p = l.plan();
        if (bell != null && p.bell != null && c.tod() >= 11800 && c.tod() < 13200) {
            int ringer = -1;
            for (int i = 0; i < l.settlement().people(); i++) if (l.settlement().job(i).equals("bellringer")) ringer = i;
            if (ringer >= 0) {
                Vec3 at = TroubleDirector.stand(c.level(), l, p.ox + p.bell.ringerX() + 0.5, p.oz + p.bell.ringerZ() + 0.5);
                TroubleDirector.stage(l, ringer, at, TroubleDirector.yaw(at, Vec3.atCenterOf(bell)), "bell_pull", "");
                if (c.now() % 200 == 13) TroubleDirector.say(c.level(), l, ringer, TroubleText.line(c.level(), "village.trouble.bell.dusk", c.now() / 200), 90);
            }
        }
        if (t.kin >= 0) {
            Vec3 at = bell == null ? TroubleDirector.well(l) : TroubleDirector.stand(c.level(), l, bell.getX() + 2.5, bell.getZ() + 2.5);
            boolean someone = false;
            for (ServerPlayer pl : c.level().players()) someone |= pl.distanceToSqr(at) < 12 * 12 && !t.helped(pl.getUUID());
            TroubleDirector.stage(l, t.kin, at, someone || bell == null ? Float.NaN : TroubleDirector.yaw(at, Vec3.atCenterOf(bell)), someone ? "talk" : "arms_crossed", "cane");
        }
        // stolen: once the camp's heart falls, the bell lies there for whoever cleared it
        if (t.cause.startsWith("stolen:") && t.get("found", 0) == 0) {
            Camps.Camp camp = Camps.get(c.level().getServer()).byId((int) t.get("camp", -1));
            if (camp != null && camp.cleared(c.day()) && c.level().isLoaded(camp.pos())) {
                ItemEntity e = new ItemEntity(c.level(), camp.pos().getX() + 0.5, camp.pos().getY() + 0.6, camp.pos().getZ() + 0.5, bellItem(l));
                e.setUnlimitedLifetime();
                c.level().addFreshEntity(e);
                t.put("found", 1);
                for (ServerPlayer pl : c.level().players()) {
                    if (pl.distanceToSqr(Vec3.atCenterOf(camp.pos())) < 80 * 80)
                        Signal.toast(pl, Component.literal(l.plan().name + "'s bell lies in the ashes of the camp"), Notices.Tier.IMPORTANT, "village_trouble");
                }
            }
        }
        // the smith casting
        if (t.stage == CASTING) cast(c, t, l);
    }

    static ItemStack bellItem(VillageService.Live l) {
        ItemStack s = new ItemStack(TroubleContent.VILLAGE_BELL.get());
        s.set(DataComponents.CUSTOM_NAME, Component.literal(l.plan().name + "'s Bell").withStyle(ChatFormatting.GOLD));
        s.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag(l.key())));
        return s;
    }

    private static net.minecraft.nbt.CompoundTag tag(String key) {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putString("awaken_village", key);
        return t;
    }

    @Override
    public boolean ask(Ctx c, Trouble t, ServerPlayer p, int rec) {
        VillageService.Live l = c.live();
        if (l == null) return false;
        String job = l.settlement().job(rec);
        // the smith casts a new one from bell bronze, whoever asks
        if (job.equals("smith") && t.stage == SILENT) {
            int have = count(p);
            if (have >= BRONZE) {
                take(p, BRONZE);
                t.stage = CASTING;
                t.since = c.now();
                t.put("smith", rec);
                t.put("for_hi", p.getUUID().getMostSignificantBits());
                t.put("for_lo", p.getUUID().getLeastSignificantBits());
                if (!t.helped(p.getUUID())) t.helpers.add(p.getUUID());
                l.settlement().say(p, rec, TroubleText.line(c.level(), "village.trouble.bell.casting", c.now()), 140);
                c.st().setDirty();
                return true;
            }
            if (t.helped(p.getUUID())) {
                l.settlement().say(p, rec, "Five ingots of bell bronze and I'll cast her new. Copper and amethyst, smelted together. You've " + have + ".", 140);
                return true;
            }
            return false;
        }
        if (rec != t.kin && rec != Runner.of(t)) return false;
        if (t.helped(p.getUUID())) {
            l.settlement().say(p, rec, TroubleDirector.fill(c, t, TroubleText.line(c.level(), "village.trouble.bell.again", c.now() / 100)), 100);
            return true;
        }
        l.settlement().say(p, rec, TroubleDirector.fill(c, t, TroubleText.line(c.level(), t.cause.startsWith("stolen:") ? "village.trouble.bell.ask_stolen"
                : "village.trouble.bell.ask_cracked", c.now() / 100 + p.getId())), 140);
        TroubleDirector.card(p, t, "The Bell · " + l.plan().name, t.cause.startsWith("stolen:")
                ? "Raiders from " + t.placeName + " took " + l.plan().name + "'s bell. Take it back, or have the smith cast a new one from five ingots of bell bronze. Then hang it in the tower."
                : l.plan().name + "'s bell cracked when the gate broke. Bring the smith five ingots of bell bronze to cast a new one, and hang it in the tower.");
        return true;
    }

    static int count(ServerPlayer p) {
        int n = 0;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(DelveContent.BELL_BRONZE_INGOT.get())) n += inv.getItem(i).getCount();
        return n;
    }

    static void take(ServerPlayer p, int n) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize() && n > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(DelveContent.BELL_BRONZE_INGOT.get())) continue;
            int k = Math.min(n, s.getCount());
            s.shrink(k);
            n -= k;
        }
    }

    /** The smith at the forge for a while (hammering, the sparks), then the bell in your hands. */
    private void cast(Ctx c, Trouble t, VillageService.Live l) {
        int smith = (int) t.get("smith", -1);
        VillagePlan p = l.plan();
        Vec3 forge = p.forge != null ? new Vec3(p.ox + p.forge.smithX() + 0.5, p.baseY + p.forge.lift() + 1, p.oz + p.forge.smithZ() + 0.5) : TroubleDirector.well(l);
        Vec3 anvil = p.forge != null ? new Vec3(p.ox + p.forge.anvilX() + 0.5, forge.y, p.oz + p.forge.anvilZ() + 0.5) : forge;
        if (smith >= 0) TroubleDirector.stage(l, smith, forge, TroubleDirector.yaw(forge, anvil), "hammer", "hammer");
        if (c.now() % 10 == 3) {
            c.level().playSound(null, BlockPos.containing(anvil), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6f, 0.9f + c.level().getRandom().nextFloat() * 0.2f);
            c.level().sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA, anvil.x, anvil.y + 0.6, anvil.z, 2, 0.2, 0.1, 0.2, 0.0);
        }
        if (c.now() - t.since < 20 * 12) return;
        ServerPlayer to = c.level().getServer().getPlayerList().getPlayer(new java.util.UUID(t.get("for_hi", 0), t.get("for_lo", 0)));
        ItemStack bell = bellItem(l);
        if (to != null && to.distanceToSqr(anvil) < 24 * 24) {
            if (!to.getInventory().add(bell)) to.drop(bell, false, net.minecraft.util.Prediction.SERVER_ONLY);
            if (smith >= 0) l.settlement().say(to, smith, TroubleText.line(c.level(), "village.trouble.bell.cast", c.now()), 140);
        } else {
            ItemEntity e = new ItemEntity(c.level(), anvil.x, anvil.y + 0.6, anvil.z, bell);
            e.setUnlimitedLifetime();
            c.level().addFreshEntity(e);
        }
        TroubleDirector.release(l, smith);
        t.stage = SILENT;
        t.put("cast", 1);
        c.st().setDirty();
    }

    @Override
    public void help(Ctx c, Trouble t, ServerPlayer p) {
        VillageService.Live l = c.live();
        if (l != null && t.kin >= 0) l.settlement().say(p, t.kin, TroubleText.line(c.level(), "village.trouble.bell.thanks", c.now()), 120);
    }

    @Override
    public String journal(Ctx c, Trouble t, ServerPlayer p) {
        String v = c.live() != null ? c.live().plan().name : "the village";
        if (t.stage == CASTING) return "The smith is casting " + v + "'s new bell.";
        if (p.getInventory().contains(s -> s.is(TroubleContent.VILLAGE_BELL.get()))) return "Haul " + v + "'s bell up its tower and hang it.";
        if (t.cause.startsWith("stolen:") && t.get("found", 0) == 0)
            return "Take " + v + "'s bell back from " + t.placeName + ", or bring the smith five ingots of bell bronze.";
        if (t.get("found", 0) != 0) return "The bell lies at " + t.placeName + ". Bring it back and hang it.";
        return "Bring " + v + "'s smith five ingots of bell bronze (copper and amethyst).";
    }

    @Override
    public @Nullable BlockPos goal(Ctx c, Trouble t, ServerPlayer p) {
        VillageService.Live l = c.live();
        if (l == null) return null;
        if (p.getInventory().contains(s -> s.is(TroubleContent.VILLAGE_BELL.get()))) return bellPos(l);
        if (t.cause.startsWith("stolen:") && t.get("found", 0) == 0) return BlockPos.of(t.place);
        VillagePlan pl = l.plan();
        return pl.forge != null ? new BlockPos(pl.ox + pl.forge.anvilX(), pl.baseY + pl.forge.lift(), pl.oz + pl.forge.anvilZ()) : bellPos(l);
    }

    // ---- hanging it ------------------------------------------------------------------------------------------------------------

    /** The bell used near its empty place in the belfry: it hangs and rings, and a ring of light runs out over the land. */
    static boolean hang(ServerLevel level, ServerPlayer p, BlockPos clicked, Direction face, ItemStack stack) {
        VillageService.Live l = VillageService.at(level, clicked);
        if (l == null) {
            p.sendOverlayMessage(Component.literal("Its place is in its own village's tower."));
            return false;
        }
        BlockPos bell = bellPos(l);
        TroubleState st = TroubleState.get(level.getServer());
        TroubleState.Memory mem = st.memory(l.key());
        if (bell == null || mem.bell == 0 || clicked.distManhattan(bell) > 3) {
            p.sendOverlayMessage(Component.literal(bell == null ? "This village has no belfry." : mem.bell == 0 ? l.plan().name + "'s bell already hangs."
                    : "Up in the belfry: where the old bell hung."));
            return false;
        }
        if (!level.getBlockState(bell).isAir()) return false;
        Trouble t = st.of(l.key());
        int id = t != null && t.kind.equals(TroubleKind.BELL.id) ? (int) t.get("bell_state", 0) : 0;
        BlockState s = id != 0 ? Block.stateById(id) : Blocks.BELL.defaultBlockState();
        if (!s.is(Blocks.BELL)) s = Blocks.BELL.defaultBlockState();
        level.setBlock(bell, s, Block.UPDATE_ALL);
        if (!p.hasInfiniteMaterials()) stack.shrink(1);
        if (level.getBlockEntity(bell) instanceof BellBlockEntity be) be.onHit(p.getDirection());
        level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 8f, 0.92f);
        level.playSound(null, bell, SoundEvents.BELL_RESONATE, SoundSource.BLOCKS, 4f, 1f);
        mem.bell = 0;
        mem.bellBy = p.getName().getString();
        TroubleNet.ring(level, Vec3.atCenterOf(bell), 0xFFD27A, 160);
        Villages.history(level.getServer(), l.key(), p.getName().getString() + " hung " + l.plan().name + "'s bell again. It rings dawn and dusk.");
        if (t != null && t.kind.equals(TroubleKind.BELL.id)) {
            if (!t.helped(p.getUUID())) t.helpers.add(p.getUUID());
            Plot.Ctx c = TroubleDirector.ctx(level, l.key());
            TroubleDirector.pay(c, t, p.position(), 64, 16, 20, 0.45, l.plan().name + "'s bell rings again",
                    pl -> Keepsake.make(l.plan().name + "'s Bell-Pin", "A sliver of the old bell's bronze, worn on a cord.",
                            "Given for the day the bell was rehung."));
            TroubleDirector.end(c, t);
        }
        st.setDirty();
        Awaken.LOG.info("[trouble] {} hung {}'s bell", p.getName().getString(), l.plan().name);
        return true;
    }

    /** Hauling it: heavy in the hand (slow, no running). */
    static void heavy(ServerPlayer p) {
        if (!p.getMainHandItem().is(TroubleContent.VILLAGE_BELL.get()) && !p.getOffhandItem().is(TroubleContent.VILLAGE_BELL.get())) return;
        p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 15, 1, false, false, true));
        p.setSprinting(false);
    }

    // ---- days, and without you ------------------------------------------------------------------------------------------------

    /** Each day: a bell a player hung warns of any gate near its village due to break within two play-days. */
    static void newDay(ServerLevel level, TroubleState st, long day) {
        for (VillageService.Live l : VillageService.all()) {
            TroubleState.Memory mem = st.peek(l.key());
            if (mem == null || mem.bellBy.isEmpty() || mem.bell != 0) continue;
            BlockPos o = new BlockPos(l.plan().ox, l.plan().baseY, l.plan().oz);
            for (GateHooks.GateView g : GateHooks.near(level, o, 600)) {
                long in = g.breaksOnPlayDay() - day;
                if (in < 1 || in > 2 || mem.get("bellwarn_" + g.id().hashCode(), 0) != 0) continue;
                mem.data.put("bellwarn_" + g.id().hashCode(), 1L);
                String dir = TroubleText.compass(g.pos().getX() - o.getX(), g.pos().getZ() - o.getZ());
                for (ServerPlayer p : level.players()) {
                    if (p.distanceToSqr(Vec3.atCenterOf(o)) > 500 * 500) continue;
                    Signal.toast(p, Component.literal(l.plan().name + "'s bell rings a warning: a gate " + dir + " breaks in " + in + (in == 1 ? " day" : " days")),
                            Notices.Tier.IMPORTANT, "village_bell");
                }
                st.setDirty();
            }
        }
    }

    @Override
    public boolean holds(Ctx c, Trouble t) {
        return t.stage == CASTING;
    }

    @Override
    public void miss(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        TroubleState.Memory mem = c.st().memory(t.village);
        if (l != null) {
            BlockPos bell = bellPos(l);
            if (bell != null && c.level().isLoaded(bell) && c.level().getBlockState(bell).isAir()) {
                int id = (int) t.get("bell_state", 0);
                BlockState s = id != 0 ? Block.stateById(id) : Blocks.BELL.defaultBlockState();
                c.level().setBlock(bell, s.is(Blocks.BELL) ? s : Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
            } else if (bell != null && !c.level().isLoaded(bell)) {
                mem.data.put("bell_hang_pending", t.get("bell_state", 0));
            }
        }
        mem.bell = 0;
        mem.bellBy = "";
        Villages.history(c.level().getServer(), t.village, "The smith cast a thin new bell from pots and hinges. It rings flat.");
        Villages.addProsperity(c.level().getServer(), t.village, -8);
    }

    @Override
    public void cleanup(Ctx c, Trouble t) {
        VillageService.Live l = c.live();
        if (l == null) return;
        TroubleDirector.release(l, t.kin);
        TroubleDirector.release(l, (int) t.get("smith", -1));
        Runner.release(c, t, l);
        // ended any other way: the bell is back
        TroubleState.Memory mem = c.st().memory(t.village);
        if (mem.bell != 0) {
            BlockPos bell = bellPos(l);
            if (bell != null && c.level().isLoaded(bell) && c.level().getBlockState(bell).isAir()) c.level().setBlock(bell, Blocks.BELL.defaultBlockState(), Block.UPDATE_ALL);
            mem.bell = 0;
        }
    }
}
