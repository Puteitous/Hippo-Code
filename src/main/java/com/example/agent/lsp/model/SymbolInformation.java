package com.example.agent.lsp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SymbolInformation {

    private String name;
    private int kind;
    private Location location;
    private String containerName;

    public SymbolInformation() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getKind() {
        return kind;
    }

    public void setKind(int kind) {
        this.kind = kind;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public String getKindName() {
        return SymbolKind.fromValue(kind).name();
    }

    public Path toFilePath() {
        if (location != null) {
            return location.toFilePath();
        }
        return null;
    }

    @Override
    public String toString() {
        return "SymbolInformation{name='" + name + "', kind=" + getKindName() + ", location=" + location + "}";
    }

    public enum SymbolKind {
        FILE(1),
        MODULE(2),
        NAMESPACE(3),
        PACKAGE(4),
        CLASS(5),
        METHOD(6),
        PROPERTY(7),
        FIELD(8),
        CONSTRUCTOR(9),
        ENUM(10),
        INTERFACE(11),
        FUNCTION(12),
        VARIABLE(13),
        CONSTANT(14),
        STRING(15),
        NUMBER(16),
        BOOLEAN(17),
        ARRAY(18),
        OBJECT(19),
        KEY(20),
        NULL(21),
        ENUM_MEMBER(22),
        STRUCT(23),
        EVENT(24),
        OPERATOR(25),
        TYPE_PARAMETER(26);

        private final int value;

        SymbolKind(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static SymbolKind fromValue(int value) {
            for (SymbolKind kind : values()) {
                if (kind.value == value) {
                    return kind;
                }
            }
            return OBJECT;
        }
    }
}
