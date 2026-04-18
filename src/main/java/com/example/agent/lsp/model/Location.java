package com.example.agent.lsp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Location {

    private String uri;
    private Range range;

    public Location() {
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Range getRange() {
        return range;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public Path toFilePath() {
        try {
            return Paths.get(URI.create(uri));
        } catch (Exception e) {
            return Paths.get(uri);
        }
    }

    @Override
    public String toString() {
        return "Location{uri='" + uri + "', range=" + range + "}";
    }
}
