package org.meh.dnd;

public record Spell(
        String name,
        boolean ranged,
        Die damage,
        boolean rollsToHit,
        int level
)
{
}
