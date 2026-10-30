package dev.awaken.above;

import dev.awaken.Awaken;
import dev.awaken.above.machine.CloneVats;
import dev.awaken.above.net.AboveNet;
import dev.awaken.core.AwakenNet;
import dev.awaken.core.PlayDays;
import dev.awaken.progression.Counters;
import dev.awaken.progression.Progression;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Public API of the Above (SPEC section 10 and 12). Other modules call {@link #consumeCloneSave} from their death
 * handling (gates: keep the Satchel; descent: keep the Deep Pack) and may read blueprints.
 */
public final class Above {
    private Above() {}

    // counters (docs/COUNTERS.md) and story flags
    public static final String MACHINES_BUILT = Counters.MACHINES_BUILT;
    public static final String MACHINES_SCANNED = Counters.MACHINES_SCANNED;
    public static final String LOGS_READ = "awaken:precursor_logs_read";
    public static final String RUINS_FOUND = "awaken:ruins_found";
    public static final String DOORS_OPENED = "awaken:precursor_doors_opened";
    public static final String GATES_STABILISED = "awaken:gates_stabilised";
    public static final String PYLON_JUMPS = "awaken:pylon_jumps";
    public static final String CLONE_SAVES = "awaken:clone_saves";
    public static final String RECALIBRATIONS = "awaken:recalibrations";
    public static final String LEVIATHAN_SIGHTINGS = "awaken:leviathan_sightings";
    public static final String HALO_SEEN = "awaken:story/halo_seen";
    public static final String HALO_REACHED = "awaken:story/halo_reached";

    /** Block tag every machine is in (Hearth reads it for the Workshop room). */
    public static final TagKey<Block> MACHINES_TAG = TagKey.create(Registries.BLOCK, Awaken.id("machines"));
    /** Every Precursor ruin structure. */
    public static final TagKey<Structure> RUINS_TAG = TagKey.create(Registries.STRUCTURE, Awaken.id("precursor_ruins"));

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Awaken.MODID);
    public static final Supplier<AttachmentType<AboveData>> DATA = ATTACHMENTS.register("above", () -> AttachmentType
            .builder(AboveData::new)
            .serialize(AboveData.MAP_CODEC)
            .copyOnDeath()
            .build());

    static void initAttachments(net.neoforged.bus.api.IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static AboveData data(Player player) {
        return player.getData(DATA.get());
    }

    /** Send the owner a fresh copy (Codex, Fabricator screen, Gate Map). Safe for fake players. */
    public static void sync(ServerPlayer player) {
        if (AwakenNet.canReceive(player, AboveNet.Sync.TYPE)) AwakenNet.send(player, new AboveNet.Sync(data(player).copy()));
    }

    // ---- blueprints -----------------------------------------------------------------------------------------------

    public static boolean knows(Player player, MachineType type) {
        return data(player).knows(type);
    }

    /** Learn a blueprint. Returns true if it was new (counts as a new thing). */
    public static boolean learn(ServerPlayer player, MachineType type) {
        AboveData d = data(player);
        if (!d.blueprints.add(type.id)) return false;
        Progression.markNewThing(player, "blueprint:" + type.id);
        sync(player);
        return true;
    }

    /** Called when a machine is built at a Fabricator. */
    public static void recordBuilt(ServerPlayer player, MachineType type) {
        AboveData d = data(player);
        boolean first = d.built.add(type.id);
        Progression.count(player, MACHINES_BUILT, 1);
        if (first) Progression.markNewThing(player, "machine:" + type.id);
        sync(player);
    }

    // ---- logs -----------------------------------------------------------------------------------------------------

    /** Records a Precursor log as read. Returns true if it was new. */
    public static boolean readLog(ServerPlayer player, String logId) {
        if (!data(player).logs.add(logId)) return false;
        Progression.count(player, LOGS_READ, 1);
        sync(player);
        return true;
    }

    // ---- ruins ----------------------------------------------------------------------------------------------------

    public static boolean addRuin(ServerPlayer player, AboveData.RuinMark mark) {
        AboveData d = data(player);
        if (d.hasRuinNear(mark.center(), 24)) return false;
        d.ruins.add(mark);
        Progression.count(player, RUINS_FOUND, 1);
        sync(player);
        return true;
    }

    // ---- Clone Vat ------------------------------------------------------------------------------------------------

    /**
     * Death hook for other modules. If this player has a bound, grown Clone Vat and hasn't used it this play-day,
     * consumes the save and returns true: the player wakes in the Vat and the caller should keep the Satchel / Deep
     * Pack. Repeated calls during the same death return true again without consuming a second save.
     */
    public static boolean consumeCloneSave(ServerPlayer player) {
        return CloneVats.consume(player, PlayDays.current(player.level().getServer()));
    }

    /** Whether a clone save would be available right now (for UI only; does not consume). */
    public static boolean cloneSaveAvailable(ServerPlayer player) {
        return CloneVats.available(player, PlayDays.current(player.level().getServer()));
    }
}
