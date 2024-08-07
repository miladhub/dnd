package org.meh.dnd;

import java.util.Optional;
import java.util.function.Function;

public interface GameRepository
{
    Optional<Game> gameById(String gameId);
    void save(Game game);
    void save(String gameId, Function<Game, Game> mutator);
}
