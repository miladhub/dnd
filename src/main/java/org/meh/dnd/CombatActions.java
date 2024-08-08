package org.meh.dnd;

public sealed interface CombatActions
        permits MeleeAttack, SpellAttack, Move
{
}
