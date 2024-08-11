package org.meh.dnd;

public sealed interface CombatActions
        permits Attacks, Move, EndTurn
{
}
