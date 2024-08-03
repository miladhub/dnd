package org.meh.dnd;

import java.util.function.Consumer;

public interface Channel<T>
{
    void subscribe(String topic, Consumer<T> consumer);
    void post(String topic, T message);
}
