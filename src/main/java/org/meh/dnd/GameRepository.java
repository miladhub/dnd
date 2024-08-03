package org.meh.dnd;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public interface GameRepository
{
    List<String> games();
    Optional<Game> gameById(String gameId);
    void save(Game game);
    void save(String gameId, Function<Game, Game> mutator);
}
