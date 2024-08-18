package org.meh.dnd;

import java.util.List;

import static org.meh.dnd.Dice.roll;
import static org.meh.dnd.DndCombat.*;
import static org.meh.dnd.Stat.INT;

public class Spells
{
    public static Damage damageRollsFor(
            SpellAttack s,
            GameChar attacker,
            GameChar defender
    ) {
        if (s.spell().name().equals(MAGIC_MISSILE.name())) {
            return new Damage(List.of(
                    new DamageRoll(roll(1, s.spell().damage(), 1), s.spell().damage(), INT),
                    new DamageRoll(roll(1, s.spell().damage(), 1), s.spell().damage(), INT),
                    new DamageRoll(roll(1, s.spell().damage(), 1), s.spell().damage(), INT)),
                    List.of());
        } else if (s.spell().name().equals(MELF_ARROW.name())) {
            return new Damage(
                    List.of(
                            new DamageRoll(roll(4, s.spell().damage(), 0), s.spell().damage(), INT)),
                    List.of(
                            new DelayedEffect(1, List.of(
                                    new DamageRoll(roll(2, s.spell().damage(), 0), s.spell().damage(), INT)
                            ), attacker, defender, s)
                    )
            );
        } else {
            return new Damage(
                    List.of(new DamageRoll(
                            roll(1, s.spell().damage(), 0), s.spell().damage(), INT)),
                    List.of());
        }
    }
}
