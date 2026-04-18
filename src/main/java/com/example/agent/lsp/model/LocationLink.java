package com.example.agent.lsp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationLink {

    private String targetUri;
    private Range targetRange;
    private Range targetSelectionRange;
    private Range originSelectionRange;

    public LocationLink() {
    }

    public String getTargetUri() {
        return targetUri;
    }

    public void setTargetUri(String targetUri) {
        this.targetUri = targetUri;
    }

    public Range getTargetRange() {
        return targetRange;
    }

    public void setTargetRange(Range targetRange) {
        this.targetRange = targetRange;
    }

    public Range getTargetSelectionRange() {
        return targetSelectionRange;
    }

    public void setTargetSelectionRange(Range targetSelectionRange) {
        this.targetSelectionRange = targetSelectionRange;
    }

    public Range getOriginSelectionRange() {
        return originSelectionRange;
    }

    public void setOriginSelectionRange(Range originSelectionRange) {
        this.originSelectionRange = originSelectionRange;
    }

    public Path toFilePath() {
        try {
            return Paths.get(URI.create(targetUri));
        } catch (Exception e) {
            return Paths.get(targetUri);
        }
    }

    @Override
    public String toString() {
        return "LocationLink{targetUri='" + targetUri + "', targetRange=" + targetRange + "}";
    }
}
