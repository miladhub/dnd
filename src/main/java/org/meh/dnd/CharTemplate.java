package org.meh.dnd;

import java.util.List;

public record CharTemplate(
        int maxHp,
        Stats stats,
        List<Weapon> weapons,
        List<Spell> spells
)
{
}
