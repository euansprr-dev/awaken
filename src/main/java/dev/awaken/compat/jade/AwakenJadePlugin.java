package dev.awaken.compat.jade;

import dev.awaken.above.machine.MachineBlockEntity;
import dev.awaken.gates.block.GateBlocks;
import dev.awaken.hearth.block.HearthAnchorBlock;
import dev.awaken.hearth.town.TownNpc;
import dev.awaken.mobs.entity.AwakenMob;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Awaken in Jade (SPEC §14): what you look at, in the System's own voice and glass, under the spoiler guard. Jade finds
 * this class by its annotation and only when Jade is installed; nothing else in Awaken refers to it. Server data
 * ({@link ServerData}) answers what the client can't know (a gate's break clock, a machine's owner and power, a
 * creature's level); the client half lives in {@code compat.client.jade}.
 */
@WailaPlugin
public final class AwakenJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(ServerData.GATE, GateBlocks.Core.class);
        registration.registerBlockDataProvider(ServerData.MACHINE, MachineBlockEntity.class);
        registration.registerBlockDataProvider(ServerData.HEARTH, HearthAnchorBlock.class);
        registration.registerEntityDataProvider(ServerData.CREATURE, AwakenMob.class);
        registration.registerEntityDataProvider(ServerData.TOWNSPERSON, TownNpc.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        dev.awaken.compat.client.jade.JadeClient.register(registration);
    }
}
