package org.meh.dnd;

import java.util.Random;

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
            gameRepository.save(gameId, g ->
                    g.withMode(COMBAT).withFightStatus(new Fight(playersTurn, opponent, "")));
            playersChannel.post(gameId, new CombatOutput(playersTurn, opponent, ""));
            if (!playersTurn)
                enemyCombatTurn(gameId, Combat.generateAttack(opponent));
        }
        else if (action instanceof Rest) {
            gameRepository.save(gameId, g -> g.withMode(RESTING));
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
        combatTurn(action, game.playerChar(), fight, gameId);
    }

    public void enemyCombatTurn(
            String gameId,
            CombatActions action
    ) {
        Game game = gameRepository.gameById(gameId).orElseThrow();
        Fight fight = (Fight) game.combatStatus();
        combatTurn(action, fight.opponent(), fight, gameId);
    }

    private void combatTurn(
            CombatActions action,
            GameChar gameChar,
            Fight fight,
            String gameId
    ) {
        String description = combatActionDescription(action, gameChar);
        Fight newFight = new Fight(
                !fight.playerTurn(),
                fight.opponent(),
                description
        );
        gameRepository.save(gameId, g -> g.withFightStatus(newFight));
        playersChannel.post(gameId, new CombatOutput(
                newFight.playerTurn(),
                newFight.opponent(),
                newFight.lastAction()
        ));
    }

    private static String combatActionDescription(
            CombatActions action,
            GameChar gameChar
    ) {
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
