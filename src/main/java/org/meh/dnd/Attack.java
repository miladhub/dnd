package org.meh.dnd;

public record Attack(String target, NpcType type, java.util.List<AiEntities.NPC> encounter)
        implements Actions
{
    public Attack(String target, NpcType type) {
        this(target, type, java.util.List.of());
    }
}
