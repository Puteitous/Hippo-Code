package com.example.agent.domain.ast;

public interface CodeParser {

    String language();

    boolean supports(String filePath);

    Object parse(String content) throws Exception;
}
