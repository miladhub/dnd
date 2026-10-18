package org.meh.dnd;

import java.util.List;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;

final class ApiMapper {
    private ApiMapper() {}

    static ApiModels.State state(Game game) {
        PlayerOutput output = game.events().isEmpty()
                ? null : game.events().getLast();
        return new ApiModels.State(
                game.mode().name(), game.place(), game.background(), game.diary(),
                game.quest().stream().map(ApiMapper::quest).toList(),
                character(game.playerChar()), narrative(output, game), combat(output, game));
    }

    private static ApiModels.Quest quest(QuestGoal goal) {
        String type = switch (goal) {
            case ExploreGoal ignored -> "explore";
            case KillGoal ignored -> "kill";
            case TalkGoal ignored -> "talk";
        };
        String description = ViewEncoderDecoder.encodeQuest(List.of(goal)).getFirst().description();
        return new ApiModels.Quest(type, goal.target(), description, goal.reached());
    }

    static ApiModels.Character character(GameChar c) {
        return new ApiModels.Character(c.name(), c.level(), c.charClass().name().toLowerCase(),
                c.ac(), c.hp(), c.maxHp(), c.xp(), c.nextXp(),
                new ApiModels.Stats(c.stats().strength(), c.stats().dexterity(), c.stats().constitution(),
                        c.stats().intelligence(), c.stats().wisdom(), c.stats().charisma()),
                c.weapons().stream().map(w -> new ApiModels.Weapon(w.name(), w.ranged(), w.damage().name(), w.twoHanded(), w.light())).toList(),
                c.spells().stream().map(s -> new ApiModels.Spell(s.name(), s.ranged(), s.damage().name(), s.rollsToHit(), s.level())).toList(),
                actions(c.availableActions()), slots(c.spellSlots()));
    }

    private static ApiModels.AvailableActions actions(AvailableActions a) {
        return new ApiModels.AvailableActions(a.actions(), a.bonusActions(), a.remainingSpeed());
    }

    private static ApiModels.SpellSlots slots(SpellSlots s) {
        return new ApiModels.SpellSlots(s.level1(), s.level2(), s.level3(), s.level4(), s.level5(),
                s.level6(), s.level7(), s.level8(), s.level9());
    }

    private static ApiModels.Narrative narrative(PlayerOutput output, Game game) {
        if (output instanceof ExploreOutput e)
            return new ApiModels.Narrative("explore", e.description(), e.choices().stream().map(a -> action(a, game)).toList(), e.storyLine(), interaction(game));
        if (output instanceof DialogueOutput d)
            return new ApiModels.Narrative("dialogue", d.phrase(), d.answers().stream().map(a -> action(a, game)).toList(), "", interaction(game));
        if (output instanceof RestOutput)
            return new ApiModels.Narrative("rest", "You are resting.", List.of(new ApiModels.Action("explore", game.place(), "", "", 0, false)), "", null);
        return null;
    }

    private static String interaction(Game game) {
        return switch (game.dialogueTarget()) {
            case Somebody s -> s.who();
            case Nobody ignored -> null;
        };
    }

    private static ApiModels.Action action(Actions a, Game game) {
        return switch (a) {
            case Start s -> new ApiModels.Action("start", s.place(), "", "", 0, false);
            case Explore e -> new ApiModels.Action("explore", e.place(), "", "", 0, false);
            case Attack a1 -> new ApiModels.Action("attack", a1.type().name() + "_" + a1.target(), a1.target(), "", 0, false);
            case Dialogue d -> new ApiModels.Action("dialogue", d.type().name() + "_" + d.target(), d.target(), "", 0, false);
            case Say s -> new ApiModels.Action("say", s.what(), "", "", 0, false);
            case EndDialogue ignored -> new ApiModels.Action("end_dialogue", "", "", "", 0, false);
            case Rest ignored -> new ApiModels.Action("rest", "", "", "", 0, false);
        };
    }

    private static ApiModels.Combat combat(PlayerOutput output, Game game) {
        if (!(output instanceof CombatOutput c)) return null;
        List<ApiModels.Combatant> combatants = c.combatants().stream().map(x ->
                new ApiModels.Combatant(x.id(), character(x.character()), x.ally(), x.initiative(), x.position(),
                        new ApiModels.GridPosition(x.gridPosition().column(), x.gridPosition().row()), actions(x.actions()),
                        x.id().equals(c.activeCombatant()), x.character().isDead())).toList();
        List<ApiModels.Target> targets = combatants.stream().filter(x -> !x.ally() && !x.dead())
                .map(x -> new ApiModels.Target(x.id(), x.character().name(), x.character().hp(), x.character().maxHp(),
                        distance(c, x.gridPosition()), c.availableActions().stream().filter(a -> a.type() == AvailableActionType.WEAPON || a.type() == AvailableActionType.SPELL)
                                .map(a -> combatAction(a, x.id())).toList())).toList();
        List<ApiModels.Action> available = c.availableActions().stream().flatMap(ApiMapper::combatActions).toList();
        return new ApiModels.Combat(c.playerTurn(), c.playerWon(), c.enemyWon(), c.activeCombatant(), c.distance(),
                actions(c.playerTurn() ? c.playerAvailableActions() : c.opponentAvailableActions()), c.log(), combatants, targets, available);
    }

    private static int distance(CombatOutput c, ApiModels.GridPosition target) {
        Combatant player = c.combatants().stream().filter(x -> x.id().equals("player")).findFirst().orElse(null);
        if (player == null) return c.distance();
        return Math.max(Math.abs(player.gridPosition().column() - target.column()), Math.abs(player.gridPosition().row() - target.row())) * GridPosition.CELL_FEET;
    }

    private static ApiModels.Action combatAction(AvailableAction a) { return combatAction(a, ""); }
    private static ApiModels.Action combatAction(AvailableAction a, String target) {
        return new ApiModels.Action(a.type().name().toLowerCase(), a.info(), target, "", 0, a.bonusAction());
    }

    private static java.util.stream.Stream<ApiModels.Action> combatActions(AvailableAction a) {
        if (a.type() != AvailableActionType.MOVE) return java.util.stream.Stream.of(combatAction(a));
        return java.util.stream.Stream.of(Dir.UP, Dir.DOWN, Dir.LEFT, Dir.RIGHT)
                .map(d -> new ApiModels.Action("move", a.info(), "", d.name(), parseAmount(a.info()), a.bonusAction()));
    }

    private static int parseAmount(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ignored) { return 5; }
    }
}
