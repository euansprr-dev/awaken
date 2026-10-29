package dev.awaken.fault;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.awaken.Awaken;
import dev.awaken.core.AwakenCommands;
import dev.awaken.core.test.AwakenTests;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Faults (SPEC §43.8; JOURNEY part 5): the reusable director every big moment calls, its sounds, and
 * {@code /awaken admin fault flicker|fault|great|mend} to see one.
 */
public final class FaultModule {
    private FaultModule() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Awaken.MODID);
    /** The world's sound sucked out and a bell note pitched wrong. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TELL = sound("fault.tell");
    /** Digital tearing laced with a reversed bell. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TEAR = sound("fault.tear");
    /** The System's voice as broken glass tones, one a line. */
    public static final DeferredHolder<SoundEvent, SoundEvent> VOICE = sound("fault.voice");
    /** The sub drop on the cut. */
    public static final DeferredHolder<SoundEvent, SoundEvent> DROP = sound("fault.drop");
    /** The clean chord of the mend. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MEND = sound("fault.mend");
    /** A flicker's short crackle. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLICKER = sound("fault.flicker");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(Awaken.id(name)));
    }

    public static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
        modBus.addListener(FaultNet::register);
        AwakenCommands.add((root, ctx) -> root.then(Commands.literal("admin").requires(AwakenCommands.admin()).then(Commands.literal("fault")
                .then(Commands.literal("flicker").executes(c -> {
                    Faults.play(c.getSource().getPlayerOrException(), Fault.flicker(""));
                    return 1;
                }))
                .then(Commands.literal("fault").executes(c -> {
                    Faults.play(c.getSource().getPlayerOrException(), sample(c.getSource().getPlayerOrException(), Fault.Size.FAULT, ""));
                    return 1;
                }))
                .then(Commands.literal("great").then(Commands.argument("key", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Faults.play(p, sample(p, Fault.Size.GREAT, StringArgumentType.getString(c, "key")));
                    return 1;
                })))
                .then(Commands.literal("mend").executes(c -> {
                    Faults.mend(c.getSource().getPlayerOrException());
                    return 1;
                })))));
        AwakenTests.register("fault_great_once", 20, h -> {
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            Fault f = sample(p, Fault.Size.GREAT, "test_once");
            h.assertTrue(Faults.play(p, f) == Fault.Size.GREAT && Faults.seen(p, "test_once"), "a Great Fault plays the first time");
            h.assertTrue(Faults.play(p, f) == Fault.Size.FAULT, "and as a plain Fault after");
            h.assertTrue(f.length() >= 200 && f.length() <= 400 && f.as(Fault.Size.FAULT).length() <= 100, "10 to 20 s, and 3 to 5 s");
            h.succeed();
        });
    }

    /** The sample the admin command plays: the first raid's words, with a held look east from up over your head. */
    static Fault sample(ServerPlayer p, Fault.Size size, String key) {
        Vec3 eye = p.getEyePosition();
        return new Fault(size, key, List.of("[ERROR] … BREACH", "412 BLOCKS EAST · COUNT ▮▮▮"), size == Fault.Size.GREAT ? "[ERROR] not this one again" : "",
                "A gate broke to the east. They're coming here.", eye.add(-6, 18, 0), eye.add(200, 4, 0), 60, true);
    }
}
