package org.meh.dnd;

public sealed interface CombatStatus
    permits Peace, Fight
{
}
