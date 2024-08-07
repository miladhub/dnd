package org.meh.dnd;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public class InMemoryGameRepository
    implements GameRepository
{
    private final Map<String, Game> games = new HashMap<>();

    @Override
    public Optional<Game> gameById(String gameId) {
        return Optional.ofNullable(games.get(gameId));
    }

    @Override
    public void save(Game game) {
        games.put(game.id(), game);
    }

    @Override
    public void save(
            String gameId,
            Function<Game, Game> mutator
    ) {
        gameById(gameId).ifPresent(g -> save(mutator.apply(g)));
    }
}
