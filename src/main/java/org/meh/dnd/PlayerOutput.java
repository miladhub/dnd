package org.meh.dnd;

public sealed interface PlayerOutput
        permits CombatOutput, DialogueOutput, ExploreOutput, RestOutput
{
}
