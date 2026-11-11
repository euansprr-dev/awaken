package dev.awaken.frontier;

import dev.awaken.core.api.FarPost;
import dev.awaken.core.api.Goals;
import dev.awaken.core.api.Goals.Goal;
import dev.awaken.core.api.Goals.Need;
import dev.awaken.core.api.TheLull;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * The Far Landing on the Tasks page (SPEC §67): once Ilse has given you her coal, the skiff is the next thing on the
 * main path, before your fire (your home is out there); and an invitation from the city, while one stands, is an
 * optional row under Out there.
 */
final class FrontierGoals {
    private FrontierGoals() {}

    static final String SKIFF = "frontier/skiff", INVITED = "frontier/invited";
    /** Between your first power and your fire. */
    static final int PATH_SKIFF = 5;

    static void register() {
        Goals.register(FrontierGoals::steps);
    }

    static List<Goals.Step> steps(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        if (!TheLull.farLanding(server)) return List.of();
        FrontierData d = FrontierData.get(server);
        List<Goals.Step> out = new ArrayList<>(2);
        if (!d.marked(p.getUUID(), "landed") && FarPost.ready(p)) {
            FrontierData.Mooring ridge = d.ridge();
            BlockPos at = ridge != null && ridge.built() ? Skiff.at(ridge.origin(), ridge.facing(), Skiff.HELM) : Moorings.ridgeSpot(server);
            out.add(Goals.Step.of(SKIFF, Goal.OUT, "Take the skiff to the far country")
                    .why("Ilse says that's where hunters start now. The old skiff on the ridge, just past Aldo, flies itself out there and back whenever you like.")
                    .need(Need.of("Use the helm at the skiff's stern", false))
                    .at(at)
                    .path(PATH_SKIFF)
                    .build());
        }
        String bell = Invitations.standing(p);
        if (bell != null) {
            out.add(Goals.Step.of(INVITED, Goal.OUT, "Go and hear the " + bell + " rung for you")
                    .why("Carillon's ringing it for you and asked you to come. Nothing's lost by waiting. The skiff at the landing takes you there and back.")
                    .need(Need.of("Stand in Carillon while it rings", false))
                    .optional()
                    .build());
        }
        return out;
    }
}
