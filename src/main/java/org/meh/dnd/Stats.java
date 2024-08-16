package org.meh.dnd;

public record Stats(
        int strength,
        int dexterity,
        int constitution,
        int intelligence,
        int wisdom,
        int charisma
)
{
    public int stat(Stat stat) {
        return switch (stat) {
            case STR -> strength;
            case CON -> constitution;
            case DEX -> dexterity;
            case INT -> intelligence;
            case WIS -> wisdom;
            case CHA -> charisma;
        };
    }
}
