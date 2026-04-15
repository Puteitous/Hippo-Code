package com.example.agent.prompt.loader;

import com.example.agent.prompt.model.Prompt;

import java.util.List;

public interface PromptLoader {

    List<Prompt> loadAll();
}
