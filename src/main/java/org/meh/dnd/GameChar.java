package org.meh.dnd;

import java.util.List;

public record GameChar(
        String name,
        int hp,
        int maxHp,
        List<Weapon> weapons,
        List<Spell> spells
)
{
    public GameChar damage(int damage) {
        return new GameChar(name, Math.max(hp - damage, 0), maxHp, weapons, spells);
    }

    public boolean isDead() {
        return hp == 0;
    }
}
