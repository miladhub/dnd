package org.meh.dnd;

public record Weapon(
        String name,
        boolean ranged,
        int damage,
        boolean twoHanded,
        boolean light
)
{
}
