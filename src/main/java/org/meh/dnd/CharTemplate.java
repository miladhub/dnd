package org.meh.dnd;

import java.util.List;

public record CharTemplate(
        int maxHp,
        int ac,
        int level,
        CharClass charClass,
        Stats stats,
        List<Weapon> weapons,
        List<Spell> spells
)
{
}
