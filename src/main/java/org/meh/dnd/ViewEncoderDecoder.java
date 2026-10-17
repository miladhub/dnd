package org.meh.dnd;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.Comparator;

import static java.lang.Integer.parseInt;
import static org.meh.dnd.Dir.*;

public class ViewEncoderDecoder
{
    public static Actions decodeAction(
            String action,
            String info,
            Game game
    ) {
        return switch (cleanAction(action)) {
            case "Attack" -> parseAttack(info);
            case "Dialogue" -> parseDialogue(info);
            case "Rest" -> new Rest();
            case "Explore" -> new Explore(parsePlace(info, game));
            case "Say" -> new Say(cleanString(info));
            case "EndDialogue" -> infoToEndDialogue(info);
            case "Start" -> new Start(parsePlace(info, game));
            default ->
                    throw new IllegalStateException("Unexpected value: " + action);
        };
    }

    private static String parsePlace(
            String info,
            Game game
    ) {
        return info != null && !info.isBlank()
                ? info
                : game.place();
    }

    private static Dialogue parseDialogue(String info) {
        String typeStr = info.split("_")[0];
        String name = info.split("_")[1];
        return new Dialogue(name, NpcType.valueOf(typeStr.toUpperCase()));
    }

    private static Attack parseAttack(String info) {
        String typeStr = info.split("_")[0];
        String name = info.split("_")[1];
        return new Attack(name, NpcType.valueOf(typeStr.toUpperCase()),
                EncounterRegistry.get(name));
    }

    public static String cleanString(String what) {
        String clean = what.trim();
        if (clean.startsWith("\"") && clean.endsWith("\""))
            return clean.substring(1, clean.length() - 1).trim();
        else
            return clean;
    }

    public static CombatActions decodeCombatAction(
            String action,
            String info
    ) {
        return decodeCombatAction(action, info, "");
    }

    public static CombatActions decodeCombatAction(
            String action,
            String info,
            String target
    ) {
        return switch (action) {
            case "Melee" -> new WeaponAttack(DndCombat.weaponByName(info), target);
            case "Spell" -> new SpellAttack(DndCombat.spellByName(info), target);
            case "MoveForward" -> new Move(TOWARDS_ENEMY, parseInt(info));
            case "MoveBackward" -> new Move(AWAY_FROM_ENEMY, parseInt(info));
            case "MoveUp" -> new Move(UP, parseInt(info));
            case "MoveDown" -> new Move(DOWN, parseInt(info));
            case "MoveLeft" -> new Move(LEFT, parseInt(info));
            case "MoveRight" -> new Move(RIGHT, parseInt(info));
            case "EndTurn" -> new EndTurn();
            default ->
                    throw new IllegalStateException("Unexpected value: " + action);
        };
    }

    private static String cleanAction(String action) {
        return action.replaceAll(":", "");
    }

    public static String encodeOutput(
            PlayerOutput output,
            Game game
    ) {
        GameChar pc = game.playerChar();
        boolean questDone = !game.quest().isEmpty() &&
                game.quest().stream().allMatch(QuestGoal::reached);
        return switch (output) {
            case DialogueOutput d -> Templates.template(new GameView(
                    pc.name(),
                    pc.level(),
                    pc.charClass().toString().toLowerCase(),
                    d.phrase(),
                    d.answers().stream()
                            .map(a -> encodeAction(a, game.quest()))
                            .toList(),
                    game.background(),
                    ViewEncoderDecoder.encodeQuest(game.quest()),
                    game.place(), questDone,
                    pc.xp() >= pc.nextXp(), getCharacterView(pc), interactionCharacter(game)));
            case ExploreOutput e -> Templates.template(new GameView(
                    pc.name(),
                    pc.level(),
                    pc.charClass().toString().toLowerCase(),
                    e.description(),
                    e.choices().stream()
                            .map(a -> encodeAction(a, game.quest()))
                            .toList(),
                    game.background(),
                    ViewEncoderDecoder.encodeQuest(game.quest()),
                    game.place(), questDone,
                    pc.xp() >= pc.nextXp(), getCharacterView(pc), interactionCharacter(game)));
            case RestOutput ignored -> Templates.template(new GameView(
                    pc.name(),
                    pc.level(),
                    pc.charClass().toString().toLowerCase(),
                    "You are resting.",
                    List.of(ViewEncoderDecoder.encodeAction(new Explore(
                            game.place()),
                            game.quest())),
                    game.background(),
                    ViewEncoderDecoder.encodeQuest(game.quest()),
                    game.place(), questDone,
                    pc.xp() >= pc.nextXp(), getCharacterView(pc), interactionCharacter(game)));
            case CombatOutput co -> Templates.combat(new CombatView(
                    co.playerTurn(),
                    co.playerWon(),
                    co.enemyWon(),
                    co.playerWon() || co.enemyWon(),
                    co.playerTurn()
                            ? co.playerAvailableActions()
                            : co.opponentAvailableActions(),
                    getCharacterView(pc),
                    getCharacterView(co.opponent()),
                    String.join("\n", co.log().reversed()).trim(),
                    co.distance(),
                    co.availableActions().stream()
                            .flatMap(a -> encodeCombatAction(a, co))
                            .toList(),
                    game.place(),
                    combatantViews(co)
            ));
        };
    }

    private static List<CombatantView> combatantViews(CombatOutput output) {
        Map<String, Integer> rows = new HashMap<>();
        java.util.Set<GridPosition> occupied = new java.util.HashSet<>();
        return output.combatants().stream()
                .sorted(Comparator.comparingInt(Combatant::initiative).reversed())
                .map(c -> {
                    String rowKey = c.ally() + ":" + c.position();
                    int row = rows.getOrDefault(rowKey, 0);
                    rows.put(rowKey, row + 1);
                    GridPosition cell = c.gridPosition();
                    while (occupied.contains(cell))
                        cell = new GridPosition(cell.column(), (cell.row() + 1) % GridPosition.ROWS);
                    occupied.add(cell);
                    return new CombatantView(c.id(), getCharacterView(c.character()),
                            c.ally(), c.initiative(), c.position(),
                            c.id().equals(output.activeCombatant()), row,
                            cell.column(), cell.row(),
                            c.ally() ? "ally" : "enemy", c.character().isDead());
                })
                .toList();
    }

    private static CharacterView getCharacterView(GameChar gc) {
        return new CharacterView(
                gc.name(),
                gc.level(),
                gc.charClass().toString().toLowerCase(),
                gc.ac(),
                gc.xp(),
                gc.nextXp(),
                gc.hp(),
                gc.maxHp(),
                gc.stats().strength(),
                gc.stats().dexterity(),
                gc.stats().constitution(),
                gc.stats().intelligence(),
                gc.stats().wisdom(),
                gc.stats().charisma(),
                gc.charClass() == CharClass.WIZARD,
                gc.spellSlots());
    }

    private static NpcView interactionCharacter(Game game) {
        return switch (game.dialogueTarget()) {
            case Somebody somebody -> new NpcView(somebody.who(),
                    somebody.type().toString().toLowerCase());
            case Nobody ignored -> null;
        };
    }

    private static Stream<CombatActionView> encodeCombatAction(AvailableAction a, CombatOutput output) {
        List<CombatTargetView> targets = output.combatants().isEmpty()
                ? List.of()
                : output.combatants().stream()
                        .filter(c -> !c.ally() && !c.character().isDead())
                        .map(c -> new CombatTargetView(c.id(), c.character().name()))
                        .toList();
        return switch (a.type()) {
            case WEAPON -> Stream.of(new CombatActionView("Melee", a.info(),
                    "Attack with " + a.info(), a.bonusAction(),
                    targets.isEmpty() ? "" : targets.getFirst().id(), targets));
            case SPELL -> Stream.of(new CombatActionView("Spell", a.info(),
                    "Cast " + a.info(), a.bonusAction(),
                    targets.isEmpty() ? "" : targets.getFirst().id(), targets));
            case MOVE -> Stream.of(
                    new CombatActionView("MoveUp", a.info(), "Move up", a.bonusAction()),
                    new CombatActionView("MoveDown", a.info(), "Move down", a.bonusAction()),
                    new CombatActionView("MoveLeft", a.info(), "Move left", a.bonusAction()),
                    new CombatActionView("MoveRight", a.info(), "Move right", a.bonusAction()));
            case END_TURN -> Stream.of(
                    new CombatActionView("EndTurn", "", "End Turn", false)
            );
        };
    }

    private static ActionView encodeAction(
            Actions a,
            List<QuestGoal> quest
    ) {
        boolean questRelated = Quests.matchesQuestGoal(a, quest);
        return switch (a) {
            case Attack attack -> new ActionView("Attack",
                    registerEncounter(attack),
                    "Attack " + attack.target(),
                    questRelated);
            case Rest ignored -> new ActionView("Rest", "", "Rest", questRelated);
            case Dialogue d -> new ActionView("Dialogue",
                    d.type() + "_" + d.target(),
                    "Talk to " + d.target(),
                    questRelated);
            case Explore e -> new ActionView("Explore", e.place(),
                    "Explore " + e.place(),
                    questRelated);
            case EndDialogue ed -> new ActionView("EndDialogue",
                    infoFromEndDialogue(ed),
                    ed.phrase(),
                    true);
            case Say say -> new ActionView("Say", say.what(), say.what(), questRelated);
            case Start start -> new ActionView("Start", start.place(), "Play", questRelated);
        };
    }

    private static String registerEncounter(Attack attack) {
        EncounterRegistry.register(attack.target(), attack.encounter());
        return attack.type() + "_" + attack.target();
    }

    public static List<QuestGoalView> encodeQuest(List<QuestGoal> goals) {
        return goals.stream()
                .map(ViewEncoderDecoder::encodeQuestGoal)
                .toList();
    }

    private static QuestGoalView encodeQuestGoal(QuestGoal g) {
        return new QuestGoalView(
                switch (g) {
                    case KillGoal ignored -> "Kill";
                    case ExploreGoal ignored -> "Explore";
                    case TalkGoal ignored -> "Talk to";
                } + " " + g.target(), g.reached());
    }

    private static String infoFromEndDialogue(EndDialogue ed) {
        String phrase = ed.phrase();
        String goalInfo = switch (ed.goal()) {
            case ExploreGoal e -> "explore_" + e.target();
            case KillGoal k -> "kill_" + k.type() + "_" + k.target();
            case TalkGoal t -> "talk_" + t.type() + "_" + t.target();
        };
        return phrase + "=>" + goalInfo;
    }

    private static EndDialogue infoToEndDialogue(String info) {
        String[] phraseTail = info.split("=>");
        String phrase = phraseTail[0].trim();
        String[] goalElements = phraseTail[1].split("_");
        String goalType = goalElements[0];
        QuestGoal goal = switch (goalType) {
            case "explore" -> new ExploreGoal(goalElements[1].trim(), false);
            case "kill" -> new KillGoal(
                    NpcType.valueOf(goalElements[1].trim().toUpperCase()),
                    goalElements[2].trim(),
                    false
            );
            case "talk" -> new TalkGoal(
                    NpcType.valueOf(goalElements[1].trim().toUpperCase()),
                    goalElements[2].trim(),
                    false
            );
            default ->
                    throw new IllegalStateException("Unexpected value: " + goalType);
        };
        return new EndDialogue(phrase, goal);
    }
}
