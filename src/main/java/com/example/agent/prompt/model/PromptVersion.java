package com.example.agent.prompt.model;

public class PromptVersion implements Comparable<PromptVersion> {

    public static final PromptVersion DEFAULT = new PromptVersion(1, 0, 0);

    private final int major;
    private final int minor;
    private final int patch;

    public PromptVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static PromptVersion fromString(String versionStr) {
        if (versionStr == null || versionStr.trim().isEmpty()) {
            return DEFAULT;
        }
        try {
            String[] parts = versionStr.split("\\.");
            int major = parts.length > 0 ? Integer.parseInt(parts[0]) : 1;
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            return new PromptVersion(major, minor, patch);
        } catch (Exception e) {
            return DEFAULT;
        }
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    @Override
    public int compareTo(PromptVersion other) {
        if (this.major != other.major) {
            return Integer.compare(this.major, other.major);
        }
        if (this.minor != other.minor) {
            return Integer.compare(this.minor, other.minor);
        }
        return Integer.compare(this.patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
