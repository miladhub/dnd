package org.meh.dnd;

public record Dialogue(String target, NpcType type)
        implements Actions
{
}
