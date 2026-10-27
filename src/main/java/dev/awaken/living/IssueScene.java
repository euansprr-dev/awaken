package dev.awaken.living;

import dev.awaken.core.api.ArmsApi;
import dev.awaken.core.api.CityHooks;
import dev.awaken.core.api.NowHooks;
import dev.awaken.living.quest.QuestEngine;
import dev.awaken.living.wayfarer.Scenes;
import dev.awaken.living.wayfarer.Wayfarer;
import dev.awaken.progression.Progression;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hale's issue (docs/arms-progression/PLAN.md §3.3, SPEC §25 "The first hour"): the first time a new hunter comes within a
 * few blocks of Hale with no styled weapon, he stops them and offers one of three. Talking to him offers it any time it
 * is still waiting, and Ilse hands it over in his name once you have stood at her camp and she has nothing of her own to
 * say. The pick, the items and what Hale says after are Arms'; this is the people, the place and the Now line.
 */
public final class IssueScene {
    private IssueScene() {}

    /** How close Hale stops you, and how long before he stops you again if you walk off. */
    static final double NEAR = 5.0;
    static final long AGAIN = 20L * 60;
    private static final Map<UUID, Long> ASKED = new HashMap<>();

    static void init() {
        ArmsApi.speaking(QuestEngine::sayLine);
        NowHooks.register(IssueScene::now);
        NeoForge.EVENT_BUS.addListener(IssueScene::onTick);
    }

    /** Whether the issue is ready to be offered by anyone: not mid scene, registered with the Association, and still waiting. */
    private static boolean ready(ServerPlayer p) {
        if (FirstScenes.shielded(p) || !LivingModule.shown(p.level().getServer())) return false;
        if (Progression.profile(p).counter("awaken:carillon_registered") <= 0 && CityHooks.spot(p.level(), "quartermaster") != null) return false;
        return ArmsApi.issuePending(p);
    }

    private static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || (p.tickCount + p.getId()) % 10 != 0) return;
        if (!ready(p)) return;
        long now = p.level().getGameTime();
        Long at = ASKED.get(p.getUUID());
        if (at != null && now - at < AGAIN) return;
        var near = p.level().getEntitiesOfClass(Wayfarer.class, p.getBoundingBox().inflate(NEAR), w -> w.who().equals("hale") && w.isAlive());
        if (near.isEmpty()) return;
        offer(p, true);
    }

    /** Talking to Hale or Ilse: true when it was theirs to hand over (Ilse only after her own scene, never over the first thread's step). */
    public static boolean talk(ServerPlayer p, String who) {
        if (who.equals("hale") && p.isShiftKeyDown() && ArmsApi.issueTaken(p)) {
            QuestEngine.sayLine(p, "Hale: Patterns? Have a look. The more you've done, the more I'll show you.");
            ArmsApi.openRack(p);
            return true;
        }
        if (!ready(p)) return false;
        if (who.equals("hale")) {
            offer(p, true);
            return true;
        }
        if (who.equals("ilse") && Progression.profile(p).counter("awaken:camp_reached") > 0 && !FirstScenes.atIlseStep(p)) {
            offer(p, false);
            return true;
        }
        return false;
    }

    private static void offer(ServerPlayer p, boolean hale) {
        ASKED.put(p.getUUID(), p.level().getGameTime());
        if (hale) {
            QuestEngine.sayLines(p, List.of("Hale: Hold on. You're not going out there with nothing in your hands.",
                    "Hale: The Association gives every new hunter one weapon. Take whichever one you like. They're all fine."));
        } else {
            QuestEngine.sayLines(p, List.of("Ilse: You never picked up your hunter's issue from Hale, did you? He asked me to hand it over if you came by.",
                    "Ilse: The Association gives every new hunter one weapon. Take whichever one you like."));
        }
        ArmsApi.openIssue(p);
    }

    /** The Now line while the issue waits: Hale, with a compass to where he stands. */
    private static List<NowHooks.Suggestion> now(ServerPlayer p) {
        if (!ready(p)) return List.of();
        BlockPos at = Scenes.residentAt(p.level().getServer(), "hale");
        if (at == null) at = CityHooks.spot(p.level(), "quartermaster");
        String text = "Hale has your hunter's issue";
        return List.of(at == null ? NowHooks.Suggestion.of("arms:issue", 70, text) : NowHooks.Suggestion.at("arms:issue", 70, text, at));
    }
}
