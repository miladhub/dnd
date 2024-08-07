package org.meh.dnd;

import java.util.Random;

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
            GameChar opponent = Combat.generateMonster(attack.target());
            boolean playersTurn = new Random().nextBoolean();
            CombatOutput output = new CombatOutput(playersTurn, opponent, "", false, false);
            gameRepository.save(gameId, g -> g
                    .withMode(COMBAT)
                    .withFightStatus(new Fight(playersTurn, opponent, "", IN_PROGRESS))
                    .withLastOutput(output));
            playersChannel.post(gameId, output);
            if (!playersTurn)
                enemyCombatTurn(gameId, Combat.generateAttack(opponent));
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
        GameChar newOpponent = fight.opponent().damage(3);
        String description = combatActionDescription(
                action,
                game.playerChar(),
                newOpponent);
        Fight newFight = new Fight(
                !fight.playerTurn(),
                newOpponent,
                description,
                newOpponent.isDead()? PLAYER_WON : IN_PROGRESS
        );
        gameRepository.save(gameId, g -> g
                .withFightStatus(newFight)
                .withMode(newOpponent.isDead()? EXPLORING : COMBAT)
        );
        notifyPlayers(gameId, newFight);
    }

    public void enemyCombatTurn(
            String gameId,
            CombatActions action
    ) {
        Game game = gameRepository.gameById(gameId).orElseThrow();
        Fight fight = (Fight) game.combatStatus();
        GameChar newPlayerChar = game.playerChar().damage(3);
        String description = combatActionDescription(
                action,
                fight.opponent(),
                newPlayerChar);
        Fight newFight = new Fight(
                !fight.playerTurn(),
                fight.opponent(),
                description,
                newPlayerChar.isDead()? ENEMY_WON : IN_PROGRESS
        );
        gameRepository.save(gameId, g -> g
                .withFightStatus(newFight)
                .withPlayerChar(newPlayerChar)
                .withMode(newPlayerChar.isDead()? EXPLORING : COMBAT)
        );
        notifyPlayers(gameId, newFight);
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
                fight.outcome() == ENEMY_WON
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
            };
    }

    public PlayerOutput enter(
            String gameId
    ) {
        return gameRepository.gameById(gameId).map(Game::lastOutput).orElseThrow();
    }
}
