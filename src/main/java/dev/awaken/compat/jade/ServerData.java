package dev.awaken.compat.jade;

import dev.awaken.Awaken;
import dev.awaken.above.machine.MachineBlockEntity;
import dev.awaken.compat.LookAt;
import dev.awaken.hearth.town.TownNpc;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.StreamServerDataProvider;

/**
 * Jade's server half: each provider sends one {@link LookAt} record for what the player is looking at. The facts and
 * the spoiler rule live in {@link LookAt} (plain Awaken code, GameTested); these only carry them.
 */
public final class ServerData {
    private ServerData() {}

    public static final Identifier GATE_ID = Awaken.id("gate");
    public static final Identifier MACHINE_ID = Awaken.id("machine");
    public static final Identifier HEARTH_ID = Awaken.id("hearth");
    public static final Identifier CREATURE_ID = Awaken.id("creature");
    public static final Identifier TOWNSPERSON_ID = Awaken.id("townsperson");

    public static final StreamServerDataProvider<BlockAccessor, LookAt.Gate> GATE = new StreamServerDataProvider<>() {
        @Override
        public LookAt.Gate streamData(BlockAccessor a) {
            if (!(a.getLevel() instanceof ServerLevel level) || !(a.getPlayer() instanceof ServerPlayer p)) return null;
            return LookAt.gate(p, level, a.getPosition()).orElse(null);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, LookAt.Gate> streamCodec() {
            return LookAt.Gate.STREAM_CODEC.cast();
        }

        @Override
        public Identifier getUid() {
            return GATE_ID;
        }
    };

    public static final StreamServerDataProvider<BlockAccessor, LookAt.Machine> MACHINE = new StreamServerDataProvider<>() {
        @Override
        public LookAt.Machine streamData(BlockAccessor a) {
            if (!(a.getLevel() instanceof ServerLevel level) || !(a.getBlockEntity() instanceof MachineBlockEntity be)) return null;
            return LookAt.machine(level, be);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, LookAt.Machine> streamCodec() {
            return LookAt.Machine.STREAM_CODEC.cast();
        }

        @Override
        public Identifier getUid() {
            return MACHINE_ID;
        }
    };

    public static final StreamServerDataProvider<BlockAccessor, LookAt.Hearth> HEARTH = new StreamServerDataProvider<>() {
        @Override
        public LookAt.Hearth streamData(BlockAccessor a) {
            if (!(a.getLevel() instanceof ServerLevel level)) return null;
            return LookAt.hearth(level, a.getPosition()).orElse(null);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, LookAt.Hearth> streamCodec() {
            return LookAt.Hearth.STREAM_CODEC.cast();
        }

        @Override
        public Identifier getUid() {
            return HEARTH_ID;
        }
    };

    public static final StreamServerDataProvider<EntityAccessor, LookAt.Creature> CREATURE = new StreamServerDataProvider<>() {
        @Override
        public LookAt.Creature streamData(EntityAccessor a) {
            return LookAt.creature(a.getEntity()).orElse(null);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, LookAt.Creature> streamCodec() {
            return LookAt.Creature.STREAM_CODEC.cast();
        }

        @Override
        public Identifier getUid() {
            return CREATURE_ID;
        }
    };

    public static final StreamServerDataProvider<EntityAccessor, LookAt.Townsperson> TOWNSPERSON = new StreamServerDataProvider<>() {
        @Override
        public LookAt.Townsperson streamData(EntityAccessor a) {
            if (!(a.getLevel() instanceof ServerLevel level) || !(a.getEntity() instanceof TownNpc npc)) return null;
            return LookAt.townsperson(level, npc);
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, LookAt.Townsperson> streamCodec() {
            return LookAt.Townsperson.STREAM_CODEC.cast();
        }

        @Override
        public Identifier getUid() {
            return TOWNSPERSON_ID;
        }
    };
}
