package org.meh.dnd;

public sealed interface Attacks
        extends CombatActions
        permits MeleeAttack, SpellAttack
{
}
