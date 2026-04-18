package com.example.agent.lsp.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Hover {

    private JsonNode contents;
    private Range range;

    public Hover() {
    }

    public JsonNode getContents() {
        return contents;
    }

    public void setContents(JsonNode contents) {
        this.contents = contents;
    }

    public Range getRange() {
        return range;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public List<String> getContentStrings() {
        List<String> result = new ArrayList<>();
        if (contents == null) {
            return result;
        }

        if (contents.isTextual()) {
            result.add(contents.asText());
        } else if (contents.isArray()) {
            for (JsonNode item : contents) {
                if (item.isTextual()) {
                    result.add(item.asText());
                } else if (item.has("value")) {
                    result.add(item.get("value").asText());
                }
            }
        } else if (contents.has("value")) {
            result.add(contents.get("value").asText());
        }

        return result;
    }

    @Override
    public String toString() {
        return "Hover{contents=" + getContentStrings() + ", range=" + range + "}";
    }
}
