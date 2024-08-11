package org.meh.dnd;

import java.util.function.Consumer;

public interface Channel<T>
{
    void subscribe(Consumer<T> consumer);
    void post(T message);
}
