package org.meh.dnd;

import java.util.Optional;
import java.util.function.Function;

public interface GameRepository
{
    Optional<Game> game();
    void save(Game game);
    void save(Function<Game, Game> mutator);
}
