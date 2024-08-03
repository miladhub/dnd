package org.meh.dnd;

public record DnD(
            GameRepository gameRepository,
            DMChannel dmChannel,
            PlayerChannel playersChannel
    ) {

    public void playTurn(
            String gameId,
            PC pc,
            Actions action
    ) {
        PlayerInput playerInput = new PlayerInput(pc, action);
        if (action instanceof Explore) {
            gameRepository.save(gameId, g -> g
                    .withMode(GameMode.EXPLORING));
            dmChannel.post(gameId, playerInput);
        }
        else if (action instanceof Attack attack) {
            gameRepository.save(gameId, g -> g
                    .withLastOutput(new CombatOutput(attack.target()))
                    .withMode(GameMode.COMBAT));
            playersChannel.post(gameId, gameRepository.gameById(gameId)
                    .map(Game::lastOutput)
                    .orElseThrow());
        }
        else if (action instanceof Rest) {
            gameRepository.save(gameId, g -> g
                    .withLastOutput(new RestOutput())
                    .withMode(GameMode.RESTING));
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
