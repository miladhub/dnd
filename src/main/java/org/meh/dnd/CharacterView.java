package org.meh.dnd;

public record CharacterView(
        String charName,
        int level,
        String charClass,
        int ac,
        int xp,
        int nextXp,
        int hp,
        int maxHp,
        int strength,
        int dexterity,
        int constitution,
        int intelligence,
        int wisdom,
        int charisma,
        boolean hasSpells,
        SpellSlots spellSlots
) {}
