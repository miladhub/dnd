package org.meh.dnd;

import java.util.List;

import static org.meh.dnd.DndCombat.*;
import static org.meh.dnd.Stat.INT;

public class Spells
{
    public static List<DamageRoll> damageRollsFor(Spell s) {
        if (s.name().equals(MAGIC_MISSILE.name())) {
            return List.of(
                    new DamageRoll(Dice.roll(1, s.damage(), 1), s.damage(), INT),
                    new DamageRoll(Dice.roll(1, s.damage(), 1), s.damage(), INT),
                    new DamageRoll(Dice.roll(1, s.damage(), 1), s.damage(), INT)
            );
        } else if (s.name().equals(MELF_ARROW.name())) {
            return List.of(
                    new DamageRoll(Dice.roll(1, s.damage(), 0), s.damage(), INT),
                    new DamageRoll(Dice.roll(1, s.damage(), 0), s.damage(), INT),
                    new DamageRoll(Dice.roll(1, s.damage(), 0), s.damage(), INT),
                    new DamageRoll(Dice.roll(1, s.damage(), 0), s.damage(), INT)
            );
        } else {
            return List.of(new DamageRoll(
                    Dice.roll(1, s.damage(), 0), s.damage(), INT));
        }
    }
}
