package org.meh.dnd;

import static org.meh.dnd.FightStatus.*;
import static org.meh.dnd.GameMode.*;
import static org.meh.dnd.GameMode.EXPLORING;

public record DnD(
            GameRepository gameRepository,
            DMChannel dmChannel,
            PlayerChannel playersChannel
    ) {

    public void doAction(
            String gameId,
            Actions action
    ) {
        PlayerInput input = new PlayerInput(action);
        if (action instanceof Explore) {
            gameRepository.save(gameId, g -> g.withMode(EXPLORING));
            dmChannel.post(gameId, input);
        }
        else if (action instanceof Attack attack) {
            Fight fight = Combat.generateFight(attack.target());
            CombatOutput output = new CombatOutput(
                    fight.playerTurn(),
                    fight.opponent(),
                    "",
                    false, false, fight.distance());
            gameRepository.save(gameId, g -> g
                    .withMode(COMBAT)
                    .withFightStatus(fight)
                    .withLastOutput(output));
            playersChannel.post(gameId, output);
            if (!fight.playerTurn())
                enemyCombatTurn(gameId, Combat.generateAttack(fight));
        }
        else if (action instanceof Rest) {
            gameRepository.save(gameId, g -> g
                    .withPlayerChar(g.playerChar().withHp(g.playerChar().maxHp()))
                    .withMode(RESTING));
            playersChannel.post(gameId, new RestOutput());
        }
        else if (action instanceof Dialogue || action instanceof Say) {
            gameRepository.save(gameId, g -> g.withMode(DIALOGUE));
            dmChannel.post(gameId, input);
        }
        else if (action instanceof EndDialogue) {
            gameRepository.save(gameId, g -> g.withMode(EXPLORING));
            dmChannel.post(gameId, input);
        }
    }

    public void playCombatTurn(
            String gameId,
            CombatActions action
    ) {
        Game game = gameRepository.gameById(gameId).orElseThrow();
        Fight fight = (Fight) game.combatStatus();
        if (action instanceof Move move) {
            int newDistance = computeDistance(move, fight);
            String description = combatActionDescription(
                    action,
                    game.playerChar(),
                    fight.opponent());
            Fight newFight = new Fight(
                    !fight.playerTurn(),
                    fight.opponent(),
                    description,
                    newDistance,
                    fight.outcome()
            );
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayers(gameId, newFight);
        } else if (action instanceof Attacks a) {
            GameChar newOpponent =
                    Combat.computeAttack(a, game.playerChar(), fight.opponent());
            String description = combatActionDescription(
                    action,
                    game.playerChar(),
                    newOpponent);
            Fight newFight = new Fight(
                    !fight.playerTurn(),
                    newOpponent,
                    description,
                    fight.distance(),
                    newOpponent.isDead() ? PLAYER_WON : IN_PROGRESS
            );
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayers(gameId, newFight);
        }
    }

    public void enemyCombatTurn(
            String gameId,
            CombatActions action
    ) {
        Game game = gameRepository.gameById(gameId).orElseThrow();
        Fight fight = (Fight) game.combatStatus();
        if (action instanceof Move move) {
            int newDistance = computeDistance(move, fight);
            String description = combatActionDescription(
                    action,
                    fight.opponent(),
                    game.playerChar());
            Fight newFight = new Fight(
                    !fight.playerTurn(),
                    fight.opponent(),
                    description,
                    newDistance,
                    fight.outcome()
            );
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
                    .withMode(newFight.opponent().isDead()? EXPLORING : COMBAT)
            );
            notifyPlayers(gameId, newFight);
        } else if (action instanceof Attacks a) {
            GameChar newPlayerChar =
                    Combat.computeAttack(a, fight.opponent(), game.playerChar());
            String description = combatActionDescription(
                    action,
                    fight.opponent(),
                    newPlayerChar);
            Fight newFight = new Fight(
                    !fight.playerTurn(),
                    fight.opponent(),
                    description,
                    fight.distance(),
                    newPlayerChar.isDead()? ENEMY_WON : IN_PROGRESS
            );
            gameRepository.save(gameId, g -> g
                    .withFightStatus(newFight)
                    .withPlayerChar(newPlayerChar)
                    .withMode(newPlayerChar.isDead()? EXPLORING : COMBAT)
            );
            notifyPlayers(gameId, newFight);
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
            String gameId,
            Fight fight
    ) {
        CombatOutput output = new CombatOutput(
                fight.playerTurn(),
                fight.opponent(),
                fight.lastAction(),
                fight.outcome() == PLAYER_WON,
                fight.outcome() == ENEMY_WON,
                fight.distance()
        );
        gameRepository.save(gameId, g -> g.withLastOutput(output));
        playersChannel.post(gameId, output);
    }

    private static String combatActionDescription(
            CombatActions action,
            GameChar gameChar,
            GameChar opponent
    ) {
        if (opponent.isDead())
            return gameChar.name() + ": killed " + opponent.name();
        else
            return gameChar.name() + ": " + switch (action) {
                case MeleeAttack m -> "melee attack with " + m.weapon();
                case SpellAttack s -> "cast " + s.spell();
                case Move move -> "move " + dirDescription(move) + opponent.name();
            };
    }

    private static String dirDescription(Move m) {
        return m.amount() + switch (m.dir()) {
            case TOWARDS_ENEMY -> " feet towards ";
            case AWAY_FROM_ENEMY -> " feet away from ";
        };
    }

    public PlayerOutput enter(
            String gameId
    ) {
        return gameRepository.gameById(gameId).map(Game::lastOutput).orElseThrow();
    }
}
