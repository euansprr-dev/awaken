package dev.awaken.arms;

import dev.awaken.core.api.NoticeEvents;
import dev.awaken.core.test.AwakenTests;
import dev.awaken.powers.PowerType;
import dev.awaken.signal.NoticeHistory;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;

final class ArmsNoticeTests {
    private ArmsNoticeTests() {}
    static void register() {
        AwakenTests.register("arms_notice_severance_mechanism", 40, h -> {
            var p = ArmsTests.fighter(h, 1.5, 0.5, 0);
            p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(50);
            var target = ArmsTests.put(h, EntityTypes.ZOMBIE, 1.5, 2, 200);
            long now = ArmsServer.now(p);
            Posture posture = target.getData(ArmsContent.POSTURE);
            posture.brokenUntil = now + 30;
            var state = ArmsServer.state(p);
            ArmsWeaves.open(p, state, ArmsWeaves.BREAK, now, target.getId());
            h.assertTrue(Math.abs(ArmsWeaves.INSTANCE.costScale(p) - 0.7) < 0.00001, "the opening discounts the triggering cast by 30%");
            ArmsWeaves.INSTANCE.cast(p, "SPIRIT", "SPIRIT", 2);
            h.assertTrue(Math.abs(target.getHealth() - 194) < 0.01 && posture.brokenUntil == now + 50,
                    "Severance actually applies its 3% large-enemy cap and one-second break extension");
            var history = p.getData(NoticeHistory.HISTORY);
            var entry = history.entries().stream().filter(e -> e.event().id().equals("weave/severance")).findFirst().orElseThrow().event();
            String detail = String.join(" ", entry.details());
            h.assertTrue(entry.kind() == NoticeEvents.Kind.COMBAT && entry.name().equals("Severance")
                    && entry.summary().contains("1 s") && detail.contains("0.6 seconds") && detail.contains("3%") && detail.contains("does not add"),
                    "the actual proc leaves an accurate, retrievable temporary-combat explanation");
            target.damageCooldownTime = 0;
            ArmsWeaves.open(p, state, ArmsWeaves.BREAK, now, target.getId());
            ArmsWeaves.INSTANCE.cast(p, "SPIRIT", "SPIRIT", 2);
            h.assertTrue(p.getData(NoticeHistory.HISTORY).sequence() == history.sequence(), "repeated combos do not spam notices or history");
            float hp = target.getHealth();
            ArmsWeaves.open(p, state, ArmsWeaves.BREAK, now - ArmsWeaves.WINDOW - 1, target.getId());
            ArmsWeaves.INSTANCE.cast(p, "SPIRIT", "SPIRIT", 2);
            h.assertTrue(target.getHealth() == hp, "a late cast does not trigger the described combo");
            for (int opening = 1; opening <= 4; opening++) for (PowerType.Family family : PowerType.Family.values()) {
                var e = ArmsWeaves.explanation(opening, family);
                h.assertTrue(e.kind() == NoticeEvents.Kind.COMBAT && !e.details().isEmpty() && !e.summary().isBlank(), "all 36 named weaves have semantic explanations");
            }
            target.discard();
            ArmsTests.done(h, p);
        });
    }
}
