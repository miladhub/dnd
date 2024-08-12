package org.meh.dnd;

public record Attack(String target, NpcType type)
        implements Actions
{
}
