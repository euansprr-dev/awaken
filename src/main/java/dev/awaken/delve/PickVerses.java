package dev.awaken.delve;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.awaken.progression.Progression;
import dev.awaken.signal.Signal;
import dev.awaken.signal.SignalWindow;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * The Delver's Pick's verses (SPEC §34.4): it wakes one verse at a time through what you do underground with it on
 * you, each a moment, each changing how it mines, how it looks and what it gives. Saved on the item
 * ({@code awaken:pick_verses}, schema 1), so it goes where the pick goes. A verse wakes when its deed is done while you
 * carry a pick whose last verse is the one before; a deed done early (with the pick on you) is remembered per player,
 * so the next verse wakes as soon as the one before it does.
 */
public final class PickVerses {
    private PickVerses() {}

    public static final int SCHEMA = 1;

    public record State(int schemaVersion, int verse) {
        public static final State ASLEEP = new State(SCHEMA, 0);
        public static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("schemaVersion", SCHEMA).forGetter(State::schemaVersion),
                Codec.INT.optionalFieldOf("verse", 0).forGetter(State::verse)).apply(i, State::new));
        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, State::schemaVersion, ByteBufCodecs.VAR_INT, State::verse, State::new);
    }

    /**
     * The verses: the name, the deed that wakes it (a per-player counter set while carrying the pick, or a rite at the
     * Forge), what it mines like (the blocks it can't take, its speed, uses, damage), its gift in plain words.
     */
    public enum Verse {
        ASLEEP("Asleep", null, BlockTags.INCORRECT_FOR_STONE_TOOL, 4F, 131, 1F,
                "It hums when its head points at metal in the rock within 16 blocks"),
        KINDLE("I · Kindle", "awaken:delve/deed_heart", BlockTags.INCORRECT_FOR_IRON_TOOL, 6F, 400, 2F,
                "Sneak and strike bare rock: an ember sticks in the wall and lights the cave round it for five minutes (three at a time)"),
        ECHO("II · Echo", "awaken:delve/deed_lamp", BlockTags.INCORRECT_FOR_IRON_TOOL, 6.5F, 500, 2F,
                "Stand still, sneak and use it on stone: a ring goes out through the rock, and ores and open caves within 12 glow through it for four seconds"),
        TEMPERED("III · Tempered", null, BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 8F, 1561, 3F,
                "True strikes come easier: the seam is wider"),
        GLASS("IV · Glass", "awaken:delve/deed_deepglass", BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 8.5F, 1800, 3F,
                "Charge the heavy strike to its glint and let it go looking up: for five seconds the rock before you turns to glass to your eyes"),
        HUSH("V · Hush", "awaken:delve/deed_seep", BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 9F, 2031, 4F,
                "Your strikes make no sound to creatures, and you mine at full speed while sneaking, even in the air or in water"),
        CALL("VI · Call", null, BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 10F, 2600, 4F,
                "When you break a lode's heart, its open blocks within 8 answer: they crack and their ore flies to you");

        public final String title;
        /** The per-player counter its deed sets (null: a rite at the Forge wakes it). */
        public final String deed;
        final TagKey<Block> incorrect;
        final float speed, damage;
        final int uses;
        public final String gift;

        Verse(String title, String deed, TagKey<Block> incorrect, float speed, int uses, float damage, String gift) {
            this.title = title;
            this.deed = deed;
            this.incorrect = incorrect;
            this.speed = speed;
            this.uses = uses;
            this.damage = damage;
            this.gift = gift;
        }
    }

    public static final Verse[] VERSES = Verse.values();

    /** What wakes a verse, in plain words (the tooltip's "Next:"). */
    public static String wakesWhen(Verse v) {
        return switch (v) {
            case ASLEEP -> "pull it from the stone";
            case KINDLE -> "break a lode's heart with it on you";
            case ECHO -> "light a Delver lamp with it on you";
            case TEMPERED -> "bring it to your Forge with 3 diamonds";
            case GLASS -> "cut Deepglass with it on you";
            case HUSH -> "quiet a seep with it on you";
            case CALL -> "bring it to your Forge with a starmetal ingot";
        };
    }

    public static int verse(ItemStack s) {
        if (!s.is(DelveContent.WAKING_PICK.get())) return -1;
        State st = s.get(DelveContent.PICK_VERSES.get());
        return st == null ? 0 : Math.clamp(st.verse(), 0, VERSES.length - 1);
    }

    /** Whether the pick in hand has woken at least {@code v}. */
    public static boolean has(ItemStack s, Verse v) {
        return verse(s) >= v.ordinal();
    }

    /** Sets a pick's verse and all it changes: the tool (what it takes, how fast), its uses and blow, its look. */
    public static void set(ItemStack s, int v, HolderGetter<Block> blocks) {
        Verse verse = VERSES[Math.clamp(v, 0, VERSES.length - 1)];
        s.set(DelveContent.PICK_VERSES.get(), new State(SCHEMA, verse.ordinal()));
        s.set(DataComponents.TOOL, new Tool(List.of(
                Tool.Rule.deniesDrops(blocks.getOrThrow(verse.incorrect)),
                Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), verse.speed)), 1.0F, 1, true));
        s.set(DataComponents.MAX_DAMAGE, verse.uses);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, verse.damage, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.8, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build());
        // the look: items/waking_pick.json picks the icon by this number
        s.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of((float) verse.ordinal()), List.of(), List.of(), List.of()));
    }

    // ---- waking ---------------------------------------------------------------------------------------------------------

    /** A verse's deed was done: remembered if the pick is on you, then whatever can wake, wakes. */
    public static void deed(ServerPlayer p, Verse v) {
        if (v.deed == null || !WakingPick.carries(p)) return;
        if (Progression.profile(p).counter(v.deed) == 0) Progression.count(p, v.deed);
        check(p);
    }

    /** Wakes, one verse after another, every pick the player carries whose next verse's deed is done. */
    public static void check(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            int v = verse(s);
            if (v < 0) continue;
            while (v + 1 < VERSES.length && VERSES[v + 1].deed != null && Progression.profile(p).counter(VERSES[v + 1].deed) > 0) {
                wake(p, s, VERSES[++v]);
            }
        }
    }

    /** The furthest verse any pick of the player's has woken (kept on the player: a lost pick comes back at it). */
    public static final String WOKEN = "awaken:delve/verses_woken";

    public static int best(ServerPlayer p) {
        return Math.clamp(Progression.profile(p).counter(WOKEN), 0, VERSES.length - 1);
    }

    /** One verse wakes in this pick: its moment, its sound, its new look and tier. */
    public static void wake(ServerPlayer p, ItemStack s, Verse v) {
        if (verse(s) >= v.ordinal()) return;
        set(s, v.ordinal(), p.level().registryAccess().lookupOrThrow(Registries.BLOCK));
        // verses wake one step at a time, so the counter is the furthest verse woken
        if (v.ordinal() > best(p)) Progression.count(p, WOKEN);
        p.level().playSound(null, p.blockPosition(), DelveContent.TRUE_STRIKE.get(), SoundSource.PLAYERS, 0.9F, 0.6F + 0.1F * v.ordinal());
        DelveNet.send(p, new DelveNet.PickWoke(v.ordinal()));
        Signal.send(p, SignalWindow.builder(SignalWindow.Kind.NOTICE, Component.literal("The pick wakes: " + v.title))
                .body(Component.literal(v.gift + "."))
                .moment().ttl(20 * 14).build());
    }
}
