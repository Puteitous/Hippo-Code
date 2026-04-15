package com.example.agent.core.di;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ServiceLocator {
    private static final Map<Class<?>, Object> SINGLETONS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Supplier<?>> PROVIDERS = new ConcurrentHashMap<>();
    private static final ThreadLocal<ArrayDeque<Class<?>>> INIT_STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private ServiceLocator() {}

    public static <T> void registerSingleton(Class<T> type, T instance) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(instance, "instance cannot be null");
        SINGLETONS.put(type, instance);
    }

    public static <T> void registerSingleton(Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        registerSingleton(type, createInstance(type));
    }

    public static <T> void registerProvider(Class<T> type, Supplier<T> provider) {
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(provider, "provider cannot be null");
        PROVIDERS.put(type, provider);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<T> type) {
        Objects.requireNonNull(type, "type cannot be null");
        
        Object existing = SINGLETONS.get(type);
        if (existing != null) {
            return (T) existing;
        }
        
        if (INIT_STACK.get().contains(type)) {
            String chain = String.join(" → ", 
                INIT_STACK.get().stream()
                    .map(Class::getSimpleName)
                    .toList());
            throw new DIException("♻️ 检测到循环依赖!\n依赖链: " + chain + " → " + type.getSimpleName());
        }
        
        return (T) SINGLETONS.computeIfAbsent(type, k -> {
            INIT_STACK.get().push(type);
            try {
                Supplier<?> provider = PROVIDERS.get(k);
                if (provider != null) {
                    return provider.get();
                }
                return createInstance(type);
            } finally {
                INIT_STACK.get().pop();
                if (INIT_STACK.get().isEmpty()) {
                    INIT_STACK.remove();
                }
            }
        });
    }

    public static <T> T getOrDefault(Class<T> type, T defaultValue) {
        T instance = getOrNull(type);
        return instance != null ? instance : defaultValue;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getOrNull(Class<T> type) {
        Object instance = SINGLETONS.get(type);
        if (instance != null) {
            return (T) instance;
        }
        Supplier<?> provider = PROVIDERS.get(type);
        return provider != null ? (T) provider.get() : null;
    }

    public static boolean isRegistered(Class<?> type) {
        return SINGLETONS.containsKey(type) || PROVIDERS.containsKey(type);
    }

    public static void clear() {
        SINGLETONS.clear();
        PROVIDERS.clear();
    }

    public static int countSingletons() {
        return SINGLETONS.size();
    }

    @SuppressWarnings("unchecked")
    private static <T> T createInstance(Class<T> type) {
        try {
            Constructor<?>[] constructors = type.getConstructors();
            for (Constructor<?> ctor : constructors) {
                Class<?>[] paramTypes = ctor.getParameterTypes();
                Object[] params = new Object[paramTypes.length];
                boolean canCreate = true;
                for (int i = 0; i < paramTypes.length; i++) {
                    params[i] = getOrNull(paramTypes[i]);
                    if (params[i] == null) {
                        canCreate = false;
                        break;
                    }
                }
                if (canCreate) {
                    return type.cast(ctor.newInstance(params));
                }
            }
            throw new DIException("No suitable constructor with resolvable dependencies for: " + type.getName());
        } catch (DIException e) {
            throw e;
        } catch (Exception e) {
            throw new DIException("Failed to create instance for: " + type.getName(), e);
        }
    }

    public static class DIException extends RuntimeException {
        public DIException(String message) {
            super(message);
        }

        public DIException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}