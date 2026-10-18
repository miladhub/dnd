package org.meh.dnd;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Locale;

final class ApiCommand {
    private ApiCommand() {}

    static void execute(JsonNode command, RestServer server) {
        String type = text(command, "type").toLowerCase(Locale.ROOT);
        if ("action".equals(type)) {
            Game game = server.currentGame().orElseThrow(() -> new IllegalStateException("No game has been created"));
            String action = text(command, "action").toLowerCase(Locale.ROOT);
            String info = text(command, "info");
            if (command.has("place")) info = text(command, "place");
            String encoded = switch (action) {
                case "start" -> "Start";
                case "explore" -> "Explore";
                case "attack" -> "Attack";
                case "dialogue" -> "Dialogue";
                case "say" -> "Say";
                case "end_dialogue" -> "EndDialogue";
                case "rest" -> "Rest";
                default -> throw new IllegalArgumentException("Unknown action: " + action);
            };
            if ("attack".equals(action) || "dialogue".equals(action))
                info = text(command, "info");
            server.executeApiAction(ViewEncoderDecoder.decodeAction(encoded, info, game));
            return;
        }
        if ("combat".equals(type)) {
            String action = text(command, "action").toLowerCase(Locale.ROOT);
            String encoded = switch (action) {
                case "melee" -> "Melee";
                case "spell" -> "Spell";
                case "end_turn" -> "EndTurn";
                case "move" -> "Move" + directionName(text(command, "direction"));
                default -> throw new IllegalArgumentException("Unknown combat action: " + action);
            };
            String info = "move".equals(action) ? Integer.toString(command.path("amount").asInt(5)) : text(command, "info");
            server.executeApiCombat(ViewEncoderDecoder.decodeCombatAction(
                            encoded, info, text(command, "target")),
                    command.path("bonusAction").asBoolean(false));
            return;
        }
        throw new IllegalArgumentException("Unknown command type: " + type);
    }

    private static String directionName(String direction) {
        return switch (direction.toUpperCase(Locale.ROOT)) {
            case "UP" -> "Up";
            case "DOWN" -> "Down";
            case "LEFT" -> "Left";
            case "RIGHT" -> "Right";
            default -> throw new IllegalArgumentException("Unknown direction: " + direction);
        };
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) return "";
        return value.asText();
    }
}
