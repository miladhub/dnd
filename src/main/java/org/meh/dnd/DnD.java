package org.meh.dnd;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.meh.dnd.AvailableActionType.*;
import static org.meh.dnd.FightOutcome.*;
import static org.meh.dnd.GameMode.*;
import static org.meh.dnd.QuestGoalType.*;

public record DnD(
            GameRepository gameRepository,
            DMChannel dmChannel,
            PlayerChannel playersChannel,
            Combat combat
    ) {

    private static final int MOVE_STEP = 5;
    private static final int WEAPONS_REACH = 5;

    public void doAction(Actions action) {
        Game game = gameRepository.game().orElseThrow();
        switch (action) {
            case Start s -> {
                gameRepository.save(g -> g
                        .withPlayerChar(g.playerChar().withHp(g.playerChar().maxHp()))
                        .withDiary(List.of())
                        .withQuest(updateQuestFromExploring(g.quest(), s.place())));
                dmChannel.post(action);
            }
            case Explore e -> {
                gameRepository.save(g -> g
                        .withMode(EXPLORING)
                        .withPlace(e.place())
                        .withQuest(updateQuestFromExploring(g.quest(), e.place())));
                dmChannel.post(action);
            }
            case Attack attack -> {
                Fight fight = combat.generateFight(game.playerChar(), attack);
                CombatOutput output = new CombatOutput(
                        fight.playerTurn(),
                        fight.playerActions(),
                        fight.opponentActions(),
                        fight.opponent(),
                        List.of(),
                        false, false, fight.distance(),
                        computeActions(game.playerChar(), fight.playerActions(), fight.distance()));
                gameRepository.save(g -> g
                        .withMode(COMBAT)
                        .withFightStatus(fight)
                        .withLastOutput(output));
                playersChannel.post(output);
                if (!fight.playerTurn())
                    playEnemyCombatTurn();
            }
            case Rest ignored -> {
                gameRepository.save(g -> g
                        .withPlayerChar(g.playerChar().withHp(g.playerChar().maxHp()))
                        .withMode(RESTING)
                        .withLastOutput(new RestOutput())
                        .withDialogueTarget(new Nobody()));
                playersChannel.post(new RestOutput());
            }
            case Dialogue d -> {
                gameRepository.save(g -> g
                        .withMode(DIALOGUE)
                        .withDialogueTarget(new Somebody(d.target(), d.type()))
                );
                dmChannel.post(action);
            }
            case Say ignored -> {
                gameRepository.save(g -> g.withMode(DIALOGUE));
                dmChannel.post(action);
            }
            case EndDialogue ignored -> {
                gameRepository.save(g -> g
                        .withMode(EXPLORING)
                        .withDialogueTarget(new Nobody())
                );
                dmChannel.post(action);
            }
        }
    }

    public void playCombatAction(
            CombatActions action,
            boolean bonusAction
    ) {
        Game game = gameRepository.game().orElseThrow();
        Fight fight = (Fight) game.combatStatus();
        if (action instanceof Move move) {
            int newDistance = computeDistance(move, fight);
            GameChar gameChar = game.playerChar();
            GameChar opponent = fight.opponent();
            String description =
                    gameChar.name() + ": " + "move " +
                    dirDescription(move) + opponent.name();
            AvailableActions newPlayerActions =
                    fight.playerActions().subtractSpeed(move.amount());
            List<String> newLog = new ArrayList<>(fight.log());
            newLog.add(description);
            Fight newFight = new Fight(
                    newPlayerActions.hasActionsLeft(),
                    fight.opponent(),
                    newLog,
                    newDistance,
                    fight.outcome(),
                    newPlayerActions,
                    fight.opponentActions()
            );
            gameRepository.save(g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayersFight(game, newFight);
            if (!newFight.playerTurn())
                playEnemyCombatTurn();
        } else if (action instanceof Attacks attack) {
            AttackResult result =
                    combat.computeAttack(attack, game.playerChar(), fight.opponent());
            String description = DndCombat.combatActionDescription(
                    attack,
                    game.playerChar(),
                    result);
            AvailableActions newPlayerActions =
                    fight.playerActions().subtractAction(bonusAction);
            List<String> newLog = new ArrayList<>(fight.log());
            newLog.add(description);
            Fight newFight = new Fight(
                    newPlayerActions.hasActionsLeft(),
                    result.gameChar(),
                    newLog,
                    fight.distance(),
                    result.gameChar().isDead() ? PLAYER_WON : IN_PROGRESS,
                    newPlayerActions,
                    fight.opponentActions()
            );
            gameRepository.save(g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
                    .withQuest(updateQuestFromFight(game.quest(), newFight))
            );
            notifyPlayersFight(game, newFight);
            if (!newFight.playerTurn())
                playEnemyCombatTurn();
        } else if (action instanceof EndTurn) {
            Fight newFight = new Fight(
                    false,
                    fight.opponent(),
                    fight.log(),
                    fight.distance(),
                    fight.outcome(),
                    fight.playerActions(),
                    fight.opponent().availableActions()
            );
            gameRepository.save(g -> g.withFightStatus(newFight));
            notifyPlayersFight(game, newFight);
            playEnemyCombatTurn();
        }
    }

    private List<QuestGoal> updateQuestFromFight(
            List<QuestGoal> q,
            Fight f
    ) {
        if (f.outcome() == ENEMY_WON)
            return q;
        else
            return q.stream()
                    .map(g -> {
                        if (g.type() != KILL || !g.target().equals(f.opponent().name())) {
                            return g;
                        } else {
                            return new QuestGoal(KILL, f.opponent().name(), true);
                        }
                    })
                    .toList();
    }

    private List<QuestGoal> updateQuestFromExploring(
            List<QuestGoal> q,
            String place
    ) {
        return q.stream()
                .map(g -> {
                    if (g.type() != EXPLORE || !placeMatches(place, g)) {
                        return g;
                    } else {
                        return new QuestGoal(EXPLORE, g.target(), true);
                    }
                })
                .toList();
    }

    private static boolean placeMatches(
            String place,
            QuestGoal g
    ) {
        String target = g.target().toLowerCase().trim().replaceAll("\\Qthe \\E", "");
        String cleanedPlace = place.toLowerCase().trim().replaceAll("\\Qthe \\E", "");
        return target.equals(cleanedPlace);
    }

    private void playEnemyCombatTurn() {
        Game game = gameRepository.game().orElseThrow();
        Fight fight = (Fight) game.combatStatus();
        while (fight.outcome() == IN_PROGRESS &&
                fight.opponentActions().hasActionsLeft() &&
                !fight.playerTurn()
        ) {
            GeneratedCombatAction ga = combat.generateAttack(fight);
            enemyCombatAction(ga);
            game = gameRepository.game().orElseThrow();
            fight = (Fight) game.combatStatus();
        }
    }

    private void enemyCombatAction(GeneratedCombatAction ga) {
        Game game = gameRepository.game().orElseThrow();
        Fight fight = (Fight) game.combatStatus();
        if (ga.action() instanceof Move move) {
            int newDistance = computeDistance(move, fight);
            GameChar gameChar = fight.opponent();
            GameChar opponent = game.playerChar();
            String description = gameChar.name() + ": " + "move " +
                    dirDescription(move) + opponent.name();
            AvailableActions newOpponentActions =
                    fight.opponentActions().subtractSpeed(move.amount());
            List<String> newLog = new ArrayList<>(fight.log());
            newLog.add(description);
            Fight newFight = new Fight(
                    !newOpponentActions.hasActionsLeft(),
                    fight.opponent(),
                    newLog,
                    newDistance,
                    fight.outcome(),
                    fight.playerActions(),
                    newOpponentActions
            );
            gameRepository.save(g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayersFight(game, newFight);
        } else if (ga.action() instanceof Attacks attack) {
            AttackResult result =
                    combat.computeAttack(attack, fight.opponent(), game.playerChar());
            String description = DndCombat.combatActionDescription(
                    attack,
                    fight.opponent(),
                    result);
            AvailableActions newOpponentActions =
                    fight.opponentActions().subtractAction(ga.bonusAction());
            List<String> newLog = new ArrayList<>(fight.log());
            newLog.add(description);
            Fight newFight = new Fight(
                    !newOpponentActions.hasActionsLeft(),
                    fight.opponent(),
                    newLog,
                    fight.distance(),
                    result.gameChar().isDead()? ENEMY_WON : IN_PROGRESS,
                    fight.playerActions(),
                    newOpponentActions
            );
            gameRepository.save(g -> g
                    .withFightStatus(newFight)
                    .withPlayerChar(result.gameChar())
                    .withMode(result.gameChar().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayersFight(game, newFight);
        } else if (ga.action() instanceof EndTurn) {
            Fight newFight = new Fight(
                    true,
                    fight.opponent(),
                    fight.log(),
                    fight.distance(),
                    fight.outcome(),
                    game.playerChar().availableActions(),
                    fight.opponentActions()
            );
            gameRepository.save(g -> g
                    .withFightStatus(newFight)
            );
            notifyPlayersFight(game, newFight);
        }
    }

    private static int computeDistance(
            Move move,
            Fight fight
    ) {
        return switch (move.dir()) {
            case TOWARDS_ENEMY -> Math.max(fight.distance() - move.amount(), 0);
            case AWAY_FROM_ENEMY -> fight.distance() + move.amount();
        };
    }

    private void notifyPlayersFight(
            Game game,
            Fight fight
    ) {
        CombatOutput output = new CombatOutput(
                fight.playerTurn(),
                fight.playerActions(),
                fight.opponentActions(),
                fight.opponent(),
                fight.log(),
                fight.outcome() == PLAYER_WON,
                fight.outcome() == ENEMY_WON,
                fight.distance(),
                computeActions(game.playerChar(), fight.playerActions(), fight.distance())
        );
        gameRepository.save(g -> g.withLastOutput(output));
        playersChannel.post(output);
    }

    private static String dirDescription(Move m) {
        return m.amount() + switch (m.dir()) {
            case TOWARDS_ENEMY -> " feet towards ";
            case AWAY_FROM_ENEMY -> " feet away from ";
        };
    }

    public Optional<PlayerOutput> enter() {
        return gameRepository.game().map(g -> g.events().getLast());
    }

    private List<AvailableAction> computeActions(
            GameChar gc,
            AvailableActions av,
            int distance
    ) {
        Stream<AvailableAction> stdActions = av.actions() > 0
                ? gc.weapons().stream()
                .filter(w -> distance <= WEAPONS_REACH || w.ranged())
                .map(w -> new AvailableAction(WEAPON, w.name(), false))
                : Stream.of();
        Stream<AvailableAction> bonusWeapons = av.bonusActions() > 0
                ? gc.weapons().stream()
                .filter(Weapon::light)
                .filter(w -> distance <= WEAPONS_REACH || w.ranged())
                .map(w -> new AvailableAction(WEAPON, w.name(), true))
                : Stream.of();
        Stream<AvailableAction> spells = av.actions() > 0
                ? gc.spells().stream()
                .filter(s -> distance <= WEAPONS_REACH || s.ranged())
                .map(s -> new AvailableAction(SPELL, s.name(), false))
                : Stream.of();
        int moveAmount = Math.min(av.remainingSpeed(), MOVE_STEP);
        Stream<AvailableAction> move = moveAmount > 0?
                Stream.of(new AvailableAction(MOVE, Integer.toString(moveAmount), false))
                : Stream.of();
        return Stream.concat(Stream.concat(stdActions,
                Stream.concat(bonusWeapons,
                        Stream.concat(spells, move))),
                Stream.of(new AvailableAction(END_TURN, "", false))).toList();
    }
}
