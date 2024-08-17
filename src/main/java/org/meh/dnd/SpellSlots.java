package org.meh.dnd;

public record SpellSlots(
        int level1,
        int level2,
        int level3,
        int level4,
        int level5,
        int level6,
        int level7,
        int level8,
        int level9
)
{
    public boolean hasSlotsAtLevel(int level) {
        return switch (level) {
            case 0 -> true;
            case 1 -> level1 > 0;
            case 2 -> level2 > 0;
            case 3 -> level3 > 0;
            case 4 -> level4 > 0;
            case 5 -> level5 > 0;
            case 6 -> level6 > 0;
            case 7 -> level7 > 0;
            case 8 -> level8 > 0;
            case 9 -> level9 > 0;
            default -> throw new IllegalArgumentException("level " + level);
        };
    }

    public SpellSlots subtractAtLevel(int level) {
        return switch (level) {
            case 0 -> this;
            case 1 -> new SpellSlots(level1 - 1, level2, level3, level4, level5, level6, level7, level8, level9);
            case 2 -> new SpellSlots(level1, level2 - 1, level3, level4, level5, level6, level7, level8, level9);
            case 3 -> new SpellSlots(level1, level2, level3 - 1, level4, level5, level6, level7, level8, level9);
            case 4 -> new SpellSlots(level1, level2, level3, level4 - 1, level5, level6, level7, level8, level9);
            case 5 -> new SpellSlots(level1, level2, level3, level4, level5 - 1, level6, level7, level8, level9);
            case 6 -> new SpellSlots(level1, level2, level3, level4, level5, level6 - 1, level7, level8, level9);
            case 7 -> new SpellSlots(level1, level2, level3, level4, level5, level6, level7 - 1, level8, level9);
            case 8 -> new SpellSlots(level1, level2, level3, level4, level5, level6, level7, level8 - 1, level9);
            case 9 -> new SpellSlots(level1, level2, level3, level4, level5, level6, level7, level8, level9 - 1);
            default -> throw new IllegalArgumentException("level " + level);
        };
    }
}
