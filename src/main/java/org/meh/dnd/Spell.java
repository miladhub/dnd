package org.meh.dnd;

public record Spell(
        String name,
        boolean ranged,
        int damage
)
{
}
