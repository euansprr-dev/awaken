package dev.awaken.bells;

import dev.awaken.core.api.CoreApi;
import dev.awaken.core.api.Sources;
import dev.awaken.hearth.HearthRecord;
import dev.awaken.hearth.HearthService;
import dev.awaken.village.Holdfast;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Optional;

/**
 * The hundred bells (SPEC §43.7; JOURNEY part 4): the Bell Book, the sleeping places, the tower and its song, the
 * handbell's peals and each home's voices. Stage 4 builds the first thirty.
 */
public final class BellModule {
    private BellModule() {}

    /** Growth: per Growth bell, a crop that didn't grow on its random tick gets this chance to grow anyway (at most 5). */
    static final float GROWTH_PER = 0.05F;
    /** Ward: per Ward bell, the chance a night creature's natural spawn inside the Hearth fails (at most 80%). */
    static final float WARD_PER = 0.2F;

    public static void init(IEventBus modBus) {
        BellContent.register(modBus);
        modBus.addListener(BellNet::register);
        // a tracked lead's card goes the moment its bell is held, however it came home (SPEC §27.12)
        dev.awaken.core.api.Destinations.doneWhen("bell", (p, no) -> {
            try {
                return Bells.book(p).held.containsKey(Integer.parseInt(no));
            } catch (NumberFormatException e) {
                return true;
            }
        });
        BellGoals.register(); // the Bell Book's leads and the tower's next step on the Tasks page's Bells tab (SPEC §27.13)
        dev.awaken.core.api.Shown.register("bells", p -> {
            java.util.List<String> out = new java.util.ArrayList<>();
            for (java.util.List<String> l : BellPage.build(p).leads()) out.add(l.get(1) + ": " + l.get(2).replace('\n', ' '));
            return out;
        });
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((ServerTickEvent.Post e) -> Bells.tick(e.getServer()));
        bus.addListener((LivingDeathEvent e) -> {
            LivingEntity dead = e.getEntity();
            if (!(dead.level() instanceof ServerLevel level) || !dead.hasData(CoreApi.MOB_ROLE.get())) return;
            Bells.sovereignFell(level, dead);
        });
        bus.addListener((PlayerInteractEvent.RightClickBlock e) -> {
            if (!(e.getEntity() instanceof ServerPlayer p) || !e.getLevel().getBlockState(e.getPos()).is(Blocks.BELL)) return;
            String key = Holdfast.bellAt(p, e.getPos());
            if (key != null) Bells.holdfastStruck(p, key);
        });
        bus.addListener((CropGrowEvent.Pre e) -> {
            if (e.getResult() != CropGrowEvent.Pre.Result.DEFAULT || !(e.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
            Optional<HearthRecord> h = HearthService.data(level.getServer()).containing(e.getPos(), HearthService.OVERWORLD);
            if (h.isEmpty()) return;
            int n = Math.min(5, Bells.voice(level, h.get().pos, BellCatalog.Voice.GROWTH));
            if (n > 0 && level.getRandom().nextFloat() < n * GROWTH_PER) e.setResult(CropGrowEvent.Pre.Result.GROW);
        });
        bus.addListener((MobSpawnEvent.PositionCheck e) -> {
            if (!(e.getEntity() instanceof Enemy) || e.getSpawnType() != EntitySpawnReason.NATURAL || !(e.getLevel() instanceof ServerLevel level)
                    || level.dimension() != Level.OVERWORLD) return;
            Optional<HearthRecord> h = HearthService.data(level.getServer()).containing(net.minecraft.core.BlockPos.containing(e.getX(), e.getY(), e.getZ()), HearthService.OVERWORLD);
            if (h.isEmpty()) return;
            int n = Bells.voice(level, h.get().pos, BellCatalog.Voice.WARD);
            if (n > 0 && level.getRandom().nextFloat() < Math.min(0.8F, n * WARD_PER)) e.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        });
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingFallEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p && Bells.voiceHere(p, BellCatalog.Voice.FEATHER) > 0) e.setDamageMultiplier(0F);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                Peals.forget(p.getUUID());
                DreamBells.forget(p.getUUID());
            }
        });
        Sources.declare("given with your first bell, and made again at a table (bells.Bells)", "handbell", "bell_frame");
        Sources.neverHeld("an old world's sleeping bell (retired, SPEC §43.16) and a dream's bell; it can't be broken or moved and drops nothing", "sleeping_bell");
        Sources.declare("the Lord of any S-rank gate gives it to a hunter not carrying one (bells.Bells.lordFell)", "heart_clapper");
        Sources.declare("every Sovereign felled gives one to a hunter not carrying one (bells.Bells.sovereignFell)", "sovereign_relic");
        dev.awaken.core.api.BellHooks.install(new HundredPeal.Access());
        bus.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> HundredPeal.serverStopped());
        BellCommands.register();
        BellTests.register();
    }
}
