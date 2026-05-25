package com.example.agent.tools.grep;

import java.util.ArrayList;
import java.util.List;

public class BackendSelector {

    private final List<GrepBackend> backends;

    public BackendSelector() {
        this.backends = new ArrayList<>();
    }

    public BackendSelector(List<GrepBackend> backends) {
        this.backends = new ArrayList<>(backends);
    }

    public BackendSelector register(GrepBackend backend) {
        backends.add(backend);
        return this;
    }

    public GrepBackend selectBackend() {
        for (GrepBackend backend : backends) {
            if (backend.isAvailable()) {
                return backend;
            }
        }
        throw new IllegalStateException("没有可用的 GrepBackend");
    }

    public List<GrepBackend> getRegisteredBackends() {
        return new ArrayList<>(backends);
    }

    public static BackendSelector createDefault() {
        BackendSelector selector = new BackendSelector();
        selector.register(new NativeGrepBackend());
        selector.register(new JavaGrepBackend());
        return selector;
    }
}
