package org.meh.dnd;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.meh.dnd.AvailableActionType.*;
import static org.meh.dnd.FightStatus.*;
import static org.meh.dnd.GameMode.*;
import static org.meh.dnd.GameMode.EXPLORING;

public record DnD(
            GameRepository gameRepository,
            DMChannel dmChannel,
            PlayerChannel playersChannel,
            Combat combat
    ) {

    private static final int MOVE_STEP = 5;

    public void doAction(
            String gameId,
            Actions action
    ) {
        PlayerInput input = new PlayerInput(action);
        Game game = gameRepository.gameById(gameId).orElseThrow();
        if (action instanceof Start) {
            gameRepository.save(gameId, g -> g.withPlayerChar(
                    g.playerChar().withHp(g.playerChar().maxHp())));
            dmChannel.post(gameId, input);
        }
        else if (action instanceof Explore e) {
            gameRepository.save(gameId, g -> g
                    .withMode(EXPLORING)
                    .withPlace(e.place()));
            dmChannel.post(gameId, input);
        }
        else if (action instanceof Attack attack) {
            Fight fight = combat.generateFight(game.playerChar(), attack.target());
            CombatOutput output = new CombatOutput(
                    fight.playerTurn(),
                    fight.playerActions(),
                    fight.opponentActions(),
                    fight.opponent(),
                    List.of(),
                    false, false, fight.distance(),
                    computeActions(game.playerChar(), fight.playerActions(), fight.distance()));
            gameRepository.save(gameId, g -> g
                    .withMode(COMBAT)
                    .withFightStatus(fight)
                    .withLastOutput(output));
            playersChannel.post(gameId, output);
            if (!fight.playerTurn())
                playEnemyCombatTurn(gameId);
        }
        else if (action instanceof Rest) {
            gameRepository.save(gameId, g -> g
                    .withPlayerChar(g.playerChar().withHp(g.playerChar().maxHp()))
                    .withMode(RESTING)
                    .withLastOutput(new RestOutput())
                    .withDialogueTarget(new Nobody()));
            playersChannel.post(gameId, new RestOutput());
        }
        else if (action instanceof Dialogue d) {
            gameRepository.save(gameId, g -> g
                    .withMode(DIALOGUE)
                    .withDialogueTarget(new Somebody(d.target()))
            );
            dmChannel.post(gameId, input);
        } else if (action instanceof Say) {
            gameRepository.save(gameId, g -> g.withMode(DIALOGUE));
            dmChannel.post(gameId, input);
        } else {
            if (action instanceof EndDialogue) {
                gameRepository.save(gameId, g -> g
                        .withMode(EXPLORING)
                        .withDialogueTarget(new Nobody())
                );
                dmChannel.post(gameId, input);
            }
        }
    }

    public void playCombatAction(
            String gameId,
            CombatActions action,
            boolean bonusAction
    ) {
        Game game = gameRepository.gameById(gameId).orElseThrow();
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
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayers(game, newFight);
            if (!newFight.playerTurn())
                playEnemyCombatTurn(gameId);
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
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayers(game, newFight);
            if (!newFight.playerTurn())
                playEnemyCombatTurn(gameId);
        } else if (action instanceof StopTurn) {
            Fight newFight = new Fight(
                    false,
                    fight.opponent(),
                    fight.log(),
                    fight.distance(),
                    fight.outcome(),
                    fight.playerActions(),
                    fight.opponent().availableActions()
            );
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
            );
            notifyPlayers(game, newFight);
            playEnemyCombatTurn(gameId);
        }
    }

    private void enemyCombatAction(
            String gameId,
            GeneratedCombatAction ga
    ) {
        Game game = gameRepository.gameById(gameId).orElseThrow();
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
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayers(game, newFight);
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
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
                    .withPlayerChar(result.gameChar())
                    .withMode(result.gameChar().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayers(game, newFight);
        } else if (ga.action() instanceof StopTurn) {
            Fight newFight = new Fight(
                    true,
                    fight.opponent(),
                    fight.log(),
                    fight.distance(),
                    fight.outcome(),
                    game.playerChar().availableActions(),
                    fight.opponentActions()
            );
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
            );
            notifyPlayers(game, newFight);
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

    private void notifyPlayers(
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
        gameRepository.save(game.id(), g -> g.withLastOutput(output));
        playersChannel.post(game.id(), output);
    }

    private static String dirDescription(Move m) {
        return m.amount() + switch (m.dir()) {
            case TOWARDS_ENEMY -> " feet towards ";
            case AWAY_FROM_ENEMY -> " feet away from ";
        };
    }

    public Optional<PlayerOutput> enter(
            String gameId
    ) {
        return gameRepository.gameById(gameId).map(g -> g.events().getLast());
    }

    private void playEnemyCombatTurn(String gameId) {
        Game game = gameRepository.gameById(gameId).orElseThrow();
        Fight fight = (Fight) game.combatStatus();
        while (fight.outcome() == FightStatus.IN_PROGRESS &&
                fight.opponentActions().hasActionsLeft() &&
                !fight.playerTurn()
        ) {
            GeneratedCombatAction ga = combat.generateAttack(fight);
            enemyCombatAction(gameId, ga);
            game = gameRepository.gameById(gameId).orElseThrow();
            fight = (Fight) game.combatStatus();
        }
    }

    private List<AvailableAction> computeActions(
            GameChar gc,
            AvailableActions av,
            int distance
    ) {
        Stream<AvailableAction> stdActions = av.actions() > 0
                ? gc.weapons().stream()
                .filter(w -> distance <= 5 || w.ranged())
                .map(w -> new AvailableAction(WEAPON, w.name(), false))
                : Stream.of();
        Stream<AvailableAction> bonusWeapons = av.bonusActions() > 0
                ? gc.weapons().stream()
                .filter(Weapon::light)
                .filter(w -> distance <= 5 || w.ranged())
                .map(w -> new AvailableAction(WEAPON, w.name(), true))
                : Stream.of();
        Stream<AvailableAction> spells = av.actions() > 0
                ? gc.spells().stream()
                .filter(s -> distance <= 5 || s.ranged())
                .map(s -> new AvailableAction(SPELL, s.name(), false))
                : Stream.of();
        int moveAmount = Math.min(av.remainingSpeed(), MOVE_STEP);
        Stream<AvailableAction> move = moveAmount > 0?
                Stream.of(new AvailableAction(MOVE, Integer.toString(moveAmount), false))
                : Stream.of();
        return Stream.concat(Stream.concat(stdActions,
                Stream.concat(bonusWeapons,
                        Stream.concat(spells, move))),
                Stream.of(new AvailableAction(STOP, "", false))).toList();
    }
}
