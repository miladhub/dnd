package org.meh.dnd;

public record Hit(
        GameChar gameChar,
        int damage
)
        implements AttackResult
{
}
