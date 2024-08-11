package org.meh.dnd;

import java.util.Optional;
import java.util.function.Function;

public class InMemoryGameRepository
    implements GameRepository
{
    private Game game = null;

    @Override
    public Optional<Game> game() {
        return Optional.ofNullable(game);
    }

    @Override
    public void save(Game game) {
        this.game = game;
    }

    @Override
    public void save(
            Function<Game, Game> mutator
    ) {
        game().ifPresent(g -> save(mutator.apply(g)));
    }
}
