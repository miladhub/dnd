package org.meh.dnd;

public record Move(
        Dir dir,
        int amount
)
        implements CombatActions
{
}
