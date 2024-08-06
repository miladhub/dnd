package org.meh.dnd;

public sealed interface FightStatus
    permits Peace, Fight
{
}
