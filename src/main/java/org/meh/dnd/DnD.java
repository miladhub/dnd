package org.meh.dnd;

import static org.meh.dnd.GameMode.*;
import static org.meh.dnd.GameMode.EXPLORING;

public record DnD(
            GameRepository gameRepository,
            DMChannel dmChannel,
            PlayerChannel playersChannel
    ) {

    public void playTurn(
            String gameId,
            Actions action
    ) {
        PlayerInput input = new PlayerInput(action);
        if (action instanceof Explore) {
            gameRepository.save(gameId, g -> g.withMode(EXPLORING));
            dmChannel.post(gameId, input);
        }
        else if (action instanceof Attack attack) {
            GameChar opponent = Monsters.generate(attack.target());
            gameRepository.save(gameId, g ->
                    g.withMode(COMBAT).withFightStatus(new Fight(true, opponent, "")));
            playersChannel.post(gameId, new CombatOutput(true, opponent, ""));
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

    public void combatTurn(
            String gameId,
            CombatActions action
    ) {
        Game game = gameRepository.gameById(gameId).orElseThrow();
        Fight fight = (Fight) game.fightStatus();
        String description = combatActionDescription(action, game);
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
            Game game
    ) {
        return game.playerChar().name() + ": " + switch (action) {
            case MeleeAttack m -> "melee attack with " + m.weapon();
            case SpellAttack s -> "cast spell " + s.spell();
        };
    }

    public PlayerOutput enter(
            String gameId
    ) {
        return gameRepository.gameById(gameId).map(Game::lastOutput).orElseThrow();
    }
}
