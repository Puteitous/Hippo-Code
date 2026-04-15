package com.example.agent.domain.ast;

import ch.usi.si.seart.treesitter.Language;
import ch.usi.si.seart.treesitter.LibraryLoader;
import ch.usi.si.seart.treesitter.Node;
import ch.usi.si.seart.treesitter.Parser;
import ch.usi.si.seart.treesitter.Point;
import ch.usi.si.seart.treesitter.Tree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TreeSitterJavaParser implements CodeParser {

    private static final Logger logger = LoggerFactory.getLogger(TreeSitterJavaParser.class);
    private static boolean libraryLoaded = false;
    private static Throwable loadError = null;

    static {
        try {
            LibraryLoader.load();
            libraryLoaded = true;
        } catch (Throwable t) {
            loadError = t;
            logger.warn("Tree-sitter library could not be loaded. Syntax validation will be disabled: {}", t.getMessage());
        }
    }

    public static boolean isAvailable() {
        return libraryLoaded;
    }

    @Override
    public String language() {
        return "java";
    }

    @Override
    public boolean supports(String filePath) {
        return filePath != null && filePath.endsWith(".java");
    }

    @Override
    public ParseResult parse(String content) throws Exception {
        if (!libraryLoaded) {
            return new ParseResult(true, List.of(), content);
        }

        if (content == null || content.trim().isEmpty()) {
            return new ParseResult(true, List.of(), content);
        }

        try (Parser parser = Parser.getFor(Language.JAVA);
             Tree tree = parser.parse(content)) {

            Node root = tree.getRootNode();

            List<SyntaxError> errors = new ArrayList<>();
            collectErrors(root, content, errors);

            boolean valid = errors.isEmpty() && !root.hasError();

            return new ParseResult(valid, errors, content);

        } catch (Exception e) {
            logger.debug("Tree-sitter 解析失败，跳过语法检查: {}", e.getMessage());
            return new ParseResult(true, List.of(), content);
        }
    }

    private void collectErrors(Node node, String content, List<SyntaxError> errors) {
        if (node == null) {
            return;
        }

        if (node.hasError()) {
            if ("ERROR".equals(node.getType()) || node.isMissing()) {
                Point start = node.getStartPoint();
                int line = start.getRow() + 1;
                int column = start.getColumn() + 1;
                String context = extractContext(content, node);
                String message = node.isMissing() ?
                        "缺少语法元素: " + node.getType() :
                        "语法错误";

                errors.add(new SyntaxError(line, column, message, context));
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            collectErrors(node.getChild(i), content, errors);
        }
    }

    private String extractContext(String content, Node node) {
        try {
            int start = Math.max(0, node.getStartByte() - 20);
            int end = Math.min(content.length(), node.getEndByte() + 20);
            String context = content.substring(start, end)
                    .replace("\n", " ")
                    .replace("\r", " ")
                    .trim();

            if (context.length() > 50) {
                context = context.substring(0, 47) + "...";
            }

            return context;
        } catch (Exception e) {
            return "";
        }
    }
}
