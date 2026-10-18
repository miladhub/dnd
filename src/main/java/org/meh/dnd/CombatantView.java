package org.meh.dnd;

public record CombatantView(String id, CharacterView character, boolean ally,
                            int initiative, int position, boolean active,
                            int mapRow, int gridColumn, int gridRow,
                            String tokenStyle, boolean dead) {
    private static final int MAP_LENGTH = 30;

    public CombatantView(String id, CharacterView character, boolean ally,
                         int initiative, int position, boolean active) {
        this(id, character, ally, initiative, position, active, 0,
                GridPosition.fromLegacyDistance(position).column(), 0,
                ally ? "ally" : "enemy", character.hp() <= 0);
    }

    public String tokenState() { return dead ? "dead" : active ? "active" : tokenStyle; }
    public int gridColumnPlusOne() { return gridColumn + 1; }
    public int gridRowPlusOne() { return gridRow + 1; }

    public String portraitUrl() {
        String name = character.charName().toLowerCase();
        if (name.contains("goblin"))
            return "/assets/portraits/mythjourneys-goblin.jpg";
        if (name.contains("kobold"))
            return "/assets/portraits/mythjourneys-kobold.jpg";
        if (name.contains("troll"))
            return "/assets/portraits/mythjourneys-troll.jpg";
        if (name.contains("skeleton"))
            return "/assets/portraits/mythjourneys-skeleton.jpg";
        if (name.contains("wolf"))
            return "/assets/portraits/wolf.svg";
        if (name.contains("bear") || name.contains("beast"))
            return "/assets/portraits/tadga-goblin.png";
        return character.portraitUrl();
    }

    public int mapPosition() {
        return Math.min(96, Math.max(4, position * 100 / MAP_LENGTH));
    }

    public int mapTop() {
        return ally ? 10 + mapRow * 22 : 56 + mapRow * 22;
    }
}
