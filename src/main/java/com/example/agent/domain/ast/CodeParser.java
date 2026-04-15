package com.example.agent.domain.ast;

public interface CodeParser {

    String language();

    boolean supports(String filePath);

    ParseResult parse(String content) throws Exception;
}
