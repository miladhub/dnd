package org.meh.dnd;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.meh.dnd.Die.*;
import static org.meh.dnd.DndCombat.*;

class DiceTest
{
    @Test
    void rollD4() {
        for (int i = 0; i < 100; i++) {
            int roll = Dice.roll(1, D4, 0);
            assertTrue(roll <= 4, Integer.toString(roll));
            assertTrue(roll >= 1, Integer.toString(roll));
        }
    }

    @Test
    void rollD20() {
        for (int i = 0; i < 100; i++) {
            int roll = Dice.roll(1, D20, 0);
            assertTrue(roll <= 20, Integer.toString(roll));
            assertTrue(roll >= 1, Integer.toString(roll));
        }
    }

    @Test
    void rollD12_bonus() {
        for (int i = 0; i < 100; i++) {
            int roll = Dice.roll(1, D12, 3);
            assertTrue(roll <= 12 + 3, Integer.toString(roll));
            assertTrue(roll >= 1 + 3, Integer.toString(roll));
        }
    }

    @Test
    void rollD12_malus() {
        for (int i = 0; i < 100; i++) {
            int roll = Dice.roll(1, D8, -3);
            assertTrue(roll <= 8 - 3, Integer.toString(roll));
            assertTrue(roll >= 0, Integer.toString(roll));
        }
    }

    @Test
    void dex_bonus_1() {
        GameChar foo = pc(new Stats(15, 12, 14, 8, 12, 10));
        assertEquals(1, Dice.dexBonus(foo));
    }

    @Test
    void dex_bonus_1_with_13() {
        GameChar foo = pc(new Stats(15, 13, 14, 8, 12, 10));
        assertEquals(1, Dice.dexBonus(foo));
    }

    @Test
    void dex_bonus_5() {
        GameChar foo = pc(new Stats(15, 20, 14, 8, 12, 10));
        assertEquals(5, Dice.dexBonus(foo));
    }

    private static final SpellSlots SPELL_SLOTS = new SpellSlots(4, 3, 0, 0, 0, 0, 0, 0, 0);
    private static GameChar pc(Stats stats) {
        return new GameChar("Foo", 3,
                CharClass.FIGHTER,
                10, 10, 15, 1000, 1500,
                stats,
                List.of(SWORD, BOW),
                List.of(MAGIC_MISSILE, SHOCKING_GRASP),
                STANDARD_ACTIONS,
                SPELL_SLOTS);
    }
}