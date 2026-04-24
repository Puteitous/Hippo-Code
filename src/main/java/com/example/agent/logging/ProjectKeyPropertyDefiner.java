package com.example.agent.logging;

import ch.qos.logback.core.PropertyDefinerBase;

public class ProjectKeyPropertyDefiner extends PropertyDefinerBase {

    public static String sanitize(String path) {
        String safe = path.replaceAll("[^a-zA-Z0-9]", "-");
        if (safe.length() > 64) {
            int hash = Math.abs(path.hashCode());
            safe = safe.substring(0, 48) + "-" + hash;
        }
        return safe;
    }

    @Override
    public String getPropertyValue() {
        return sanitize(System.getProperty("user.dir"));
    }
}
