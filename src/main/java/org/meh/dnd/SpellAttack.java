package org.meh.dnd;

public record SpellAttack(Spell spell, String target)
        implements Attacks
{
    public SpellAttack(Spell spell) {
        this(spell, "");
    }
}
