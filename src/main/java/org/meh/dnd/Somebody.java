package org.meh.dnd;

public record Somebody(String who, NpcType type)
        implements DialogueTarget
{
}
