package org.meh.dnd;

import java.util.List;
import java.util.Objects;

public record GameChar(
        String name,
        int level,
        CharClass charClass,
        int hp,
        int maxHp,
        int ac,
        int xp,
        int nextXp,
        Stats stats,
        List<Weapon> weapons,
        List<Spell> spells,
        AvailableActions availableActions
)
{
    public GameChar {
        if (name == null) throw new IllegalArgumentException("name");
    }

    public GameChar damage(int damage) {
        return new GameChar(name, level, charClass, Math.max(hp - damage, 0),
                maxHp, ac, xp, nextXp,
                stats,
                weapons, spells, availableActions);
    }

    public boolean isDead() {
        return hp == 0;
    }

    public GameChar withHp(int hp) {
        return new GameChar(name, level, charClass, hp, maxHp,  ac, xp, nextXp, stats, weapons, spells, availableActions);
    }
}
