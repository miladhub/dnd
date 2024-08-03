package org.meh.dnd;

public record AiDM(GameRepository gameRepository, DMChannel dmChannel,
                   PlayerChannel playersChannel)
    implements DM
{
    @Override
    public Game process(
            Game game,
            PlayerInput input
    ) {
        playersChannel.post(game.id(), game.lastOutput());
        return game;
    }
}
