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
) {
    public String portrait() {
        return switch (charClass) {
            case "wizard" -> "🧙";
            case "fighter" -> "⚔️";
            default -> "👤";
        };
    }

    public String avatar() {
        boolean wizard = "wizard".equals(charClass);
        String main = wizard ? "#8b5cf6" : "#2563eb";
        String head = wizard ? "#f8c9a0" : "#f2b38b";
        String gear = wizard
                ? "<path d=\"M5 12 16 2l11 10h-5v3H10v-3z\" fill=\"#312e81\"/>"
                : "<path d=\"M8 11a8 8 0 0 1 16 0v3H8z\" fill=\"#1e3a8a\"/>";
        return "<svg class=\"combat-avatar w-12 h-12 inline-block\" viewBox=\"0 0 32 32\" aria-hidden=\"true\">"
                + gear
                + "<circle cx=\"16\" cy=\"13\" r=\"6\" fill=\"" + head + "\"/>"
                + "<circle cx=\"13.5\" cy=\"13\" r=\".8\" fill=\"#111827\"/><circle cx=\"18.5\" cy=\"13\" r=\".8\" fill=\"#111827\"/>"
                + "<path d=\"M13 16q3 2 6 0\" fill=\"none\" stroke=\"#7c2d12\" stroke-width=\".8\"/>"
                + "<path d=\"M5 31c1-8 5-12 11-12s10 4 11 12\" fill=\"" + main + "\"/>"
                + "</svg>";
    }

    public String portraitUrl() {
        return "wizard".equals(charClass)
                ? "/assets/portraits/character-69849197.webp"
                : "/assets/portraits/ilalea-goliath-knight.png";
    }
}
