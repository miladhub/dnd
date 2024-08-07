package org.meh.dnd;

import java.util.List;

public record Combat()
{
    public static GameChar generateMonster(String name) {
        return new GameChar(
                name, 10, 10,
                List.of(new Weapon("sword")),
                List.of());
    }

    public static CombatActions generateAttack(GameChar monster) {
        return new MeleeAttack(monster.weapons().getFirst().name());
    }
}
