package org.meh.dnd;

public record CharacterView(
        String charName,
        int hp,
        int maxHp,
        int strength,
        int dexterity,
        int constitution,
        int intelligence,
        int wisdom,
        int charisma
) {}
