package org.meh.dnd;

import java.util.List;
import java.util.stream.Stream;

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
        return new Attack(name, NpcType.valueOf(typeStr.toUpperCase()));
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
        return switch (action) {
            case "Melee" -> new WeaponAttack(DndCombat.weaponByName(info));
            case "Spell" -> new SpellAttack(DndCombat.spellByName(info));
            case "MoveForward" -> new Move(TOWARDS_ENEMY, parseInt(info));
            case "MoveBackward" -> new Move(AWAY_FROM_ENEMY, parseInt(info));
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
                    pc.xp() >= pc.nextXp()));
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
                    pc.xp() >= pc.nextXp()));
            case RestOutput ignored -> Templates.template(new GameView(
                    pc.name(),
                    pc.level(),
                    pc.charClass().toString().toLowerCase(),
                    "You are resting.",
                    List.of(ViewEncoderDecoder.encodeAction(new Explore(game.place()), game.quest())),
                    game.background(),
                    ViewEncoderDecoder.encodeQuest(game.quest()),
                    game.place(), questDone,
                    pc.xp() >= pc.nextXp()));
            case CombatOutput co -> Templates.combat(new CombatView(
                    co.playerTurn(), co.playerWon(), co.enemyWon(),
                    co.playerWon() || co.enemyWon(),
                    co.playerTurn() ? co.playerAvailableActions() : co.opponentAvailableActions(),
                    new CharacterView(pc.name(),
                            pc.level(),
                            pc.charClass().toString().toLowerCase(),
                            pc.ac(),
                            pc.xp(),
                            pc.nextXp(),
                            pc.hp(),
                            pc.maxHp(),
                            pc.stats().strength(),
                            pc.stats().dexterity(),
                            pc.stats().constitution(),
                            pc.stats().intelligence(),
                            pc.stats().wisdom(),
                            pc.stats().charisma(),
                            pc.charClass() == CharClass.WIZARD,
                            pc.spellSlots()),
                    new CharacterView(co.opponent().name(),
                            co.opponent().level(),
                            co.opponent().charClass().toString().toLowerCase(),
                            co.opponent().ac(),
                            co.opponent().xp(),
                            co.opponent().nextXp(),
                            co.opponent().hp(),
                            co.opponent().maxHp(),
                            co.opponent().stats().strength(),
                            co.opponent().stats().dexterity(),
                            co.opponent().stats().constitution(),
                            co.opponent().stats().intelligence(),
                            co.opponent().stats().wisdom(),
                            co.opponent().stats().charisma(),
                            co.opponent().charClass() == CharClass.WIZARD,
                            co.opponent().spellSlots()),
                    String.join("\n", co.log().reversed()).trim(),
                    co.distance(),
                    co.availableActions().stream()
                            .flatMap(ViewEncoderDecoder::encodeCombatAction)
                            .toList(),
                    game.place()
            ));
        };
    }

    private static Stream<CombatActionView> encodeCombatAction(AvailableAction a) {
        return switch (a.type()) {
            case WEAPON -> Stream.of(
                    new CombatActionView("Melee", a.info(),
                            "Attack with " + a.info(), a.bonusAction()));
            case SPELL -> Stream.of(
                    new CombatActionView("Spell", a.info(),
                            "Cast " + a.info(), a.bonusAction()));
            case MOVE -> Stream.of(
                    new CombatActionView("MoveForward", a.info(),
                            "Move " + a.info() + " feet forward",
                            a.bonusAction()),
                    new CombatActionView("MoveBackward", a.info(),
                            "Move " + a.info() + " feet backward",
                            a.bonusAction()));
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
                    attack.type() + "_" + attack.target(),
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
