package org.meh.dnd;

import java.util.Random;
import java.util.stream.IntStream;

import static org.meh.dnd.Die.D20;

public class Dice
{
    public static int roll(int n, Die die, int bonus) {
        return Math.max(0, IntStream.of(n)
                .map(i -> new Random().nextInt(bound(die)) + 1)
                .sum() + bonus);
    }

    private static int bound(Die die) {
        return switch (die) {
            case D4 -> 4;
            case D6 -> 6;
            case D8 -> 8;
            case D12 -> 12;
            case D20 -> 20;
        };
    }

    public static int initiative(GameChar gameChar) {
        return Dice.roll(1, D20, dexBonus(gameChar));
    }

    public static int bonus(
            GameChar gameChar,
            Stat stat
    ) {
        return (gameChar.stats().stat(stat) - 10) / 2;
    }

    public static int rollMelee(GameChar attacker, Die die) {
        return Dice.roll(1, die, strBonus(attacker));
    }

    public static int rollRanged(GameChar attacker, Die die
    ) {
        return Dice.roll(1, die, dexBonus(attacker));
    }

    public static int rollInt(GameChar attacker, Die die) {
        return Dice.roll(1, die, intBonus(attacker));
    }

    public static int strBonus(GameChar gameChar) {
        return bonus(gameChar, Stat.STR);
    }

    public static int dexBonus(GameChar gameChar) {
        return bonus(gameChar, Stat.DEX);
    }

    public static int intBonus(GameChar gameChar) {
        return bonus(gameChar, Stat.INT);
    }
}
