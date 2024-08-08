package org.meh.dnd;

public record CharacterView(
        String charName,
        int level,
        String charClass,
        int hp,
        int maxHp,
        int strength,
        int dexterity,
        int constitution,
        int intelligence,
        int wisdom,
        int charisma
) {}
