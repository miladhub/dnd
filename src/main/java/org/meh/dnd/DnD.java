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
            gameRepository.save(gameId, g -> g
                    .withLastOutput(new CombatOutput(attack.target()))
                    .withMode(COMBAT));
            playersChannel.post(gameId, gameRepository.gameById(gameId)
                    .map(Game::lastOutput)
                    .orElseThrow());
        }
        else if (action instanceof Rest) {
            gameRepository.save(gameId, g -> g
                    .withLastOutput(new RestOutput())
                    .withMode(RESTING));
            playersChannel.post(gameId, gameRepository.gameById(gameId)
                    .map(Game::lastOutput)
                    .orElseThrow());
        }
    }

    public PlayerOutput enter(
            String gameId
    ) {
        return gameRepository.gameById(gameId).map(Game::lastOutput).orElseThrow();
    }
}
