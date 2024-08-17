package org.meh.dnd;

public record LevelUpView(
        int level,
        int xp,
        int nextXp,
        int hp,
        boolean hasSpellSlots,
        SpellSlots spellSlots,
        int profBonus
)
{
}
