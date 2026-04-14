package com.example.agent.core.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EventBus {
    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);

    private static final Map<Class<? extends Event>, List<Consumer<? extends Event>>> SUBSCRIBERS =
            new ConcurrentHashMap<>();

    private static final List<Consumer<Event>> GLOBAL_SUBSCRIBERS =
            new CopyOnWriteArrayList<>();

    private EventBus() {}

    @SuppressWarnings("unchecked")
    public static <T extends Event> void subscribe(Class<T> eventType, Consumer<T> subscriber) {
        SUBSCRIBERS.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add(subscriber);
    }

    public static void subscribeToAll(Consumer<Event> subscriber) {
        GLOBAL_SUBSCRIBERS.add(subscriber);
    }

    public static <T extends Event> void publish(T event) {
        if (event == null) {
            return;
        }

        try {
            for (Consumer<Event> subscriber : GLOBAL_SUBSCRIBERS) {
                subscriber.accept(event);
            }

            List<Consumer<? extends Event>> handlers = SUBSCRIBERS.get(event.getClass());
            if (handlers != null) {
                for (Consumer<? extends Event> handler : handlers) {
                    ((Consumer<T>) handler).accept(event);
                }
            }
        } catch (Exception e) {
            logger.warn("事件处理异常: {}", e.getMessage(), e);
        }
    }

    public static void clear() {
        SUBSCRIBERS.clear();
        GLOBAL_SUBSCRIBERS.clear();
    }
}