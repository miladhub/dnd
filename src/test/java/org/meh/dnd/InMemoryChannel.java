package org.meh.dnd;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class InMemoryChannel<T>
        implements Channel<T>
{
    private final List<Consumer<T>> consumers = new CopyOnWriteArrayList<>();

    @Override
    public void subscribe(Consumer<T> consumer) {
        consumers.add(consumer);
    }

    @Override
    public void post(T message) {
        consumers.forEach(c -> c.accept(message));
    }
}
