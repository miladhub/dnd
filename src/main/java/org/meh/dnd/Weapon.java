package org.meh.dnd;

public record Weapon(
        String name,
        boolean ranged,
        Die damage,
        boolean twoHanded,
        boolean light
)
{
}
