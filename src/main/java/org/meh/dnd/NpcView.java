package org.meh.dnd;

public record NpcView(String name, String type) {
    public String portrait() {
        return switch (type) {
            case "magic" -> "🧙";
            case "warrior" -> "⚔️";
            case "beast" -> "🐾";
            default -> "👤";
        };
    }

    public String avatar() {
        if ("beast".equals(type))
            return "<svg class=\"combat-avatar w-12 h-12 inline-block\" viewBox=\"0 0 32 32\" aria-hidden=\"true\">"
                    + "<path d=\"M7 12 4 4l8 4q4-3 8 0l8-4-3 8v9q-9 8-18 0z\" fill=\"#a16207\"/>"
                    + "<circle cx=\"12\" cy=\"16\" r=\"1.3\" fill=\"#111827\"/><circle cx=\"20\" cy=\"16\" r=\"1.3\" fill=\"#111827\"/>"
                    + "<path d=\"M14 20q2 2 4 0\" fill=\"none\" stroke=\"#451a03\"/>"
                    + "</svg>";
        return "<svg class=\"combat-avatar w-12 h-12 inline-block\" viewBox=\"0 0 32 32\" aria-hidden=\"true\">"
                + "<circle cx=\"16\" cy=\"12\" r=\"7\" fill=\"#f2b38b\"/>"
                + "<path d=\"M8 12q1-10 8-10t8 10q-8-5-16 0\" fill=\"#7c3aed\"/>"
                + "<circle cx=\"13.5\" cy=\"13\" r=\".8\"/><circle cx=\"18.5\" cy=\"13\" r=\".8\"/>"
                + "<path d=\"M6 31c1-8 5-12 10-12s9 4 10 12\" fill=\"#7c3aed\"/>"
                + "</svg>";
    }

    public String portraitUrl() {
        if (name.toLowerCase().contains("wolf"))
            return "/assets/portraits/wolf.svg";
        return "beast".equals(type)
                ? "/assets/portraits/tadga-goblin.png"
                : "/assets/portraits/caelri-berserker.png";
    }
}
