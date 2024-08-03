package org.meh.dnd;

public sealed interface PlayerOutput
        permits CombatOutput, ExploreOutput, RestOutput
{
}
