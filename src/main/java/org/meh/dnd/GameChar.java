package org.meh.dnd;

import java.util.List;

public record GameChar(
        String name,
        List<Weapon> weapons,
        List<Spell> spells
)
{
}
