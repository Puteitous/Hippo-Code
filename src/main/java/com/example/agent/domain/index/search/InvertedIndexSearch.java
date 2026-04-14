package com.example.agent.domain.index.search;

import com.example.agent.domain.index.CodeSearchStrategy;
import com.example.agent.domain.index.SearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.example.agent.domain.index.search.SearchEngineUtils.*;

public class InvertedIndexSearch implements CodeSearchStrategy {
    private static final Logger logger = LoggerFactory.getLogger(InvertedIndexSearch.class);

    private static final Pattern CLASS_PATTERN = Pattern.compile("class\\s+(\\w+)");
    private static final Pattern INTERFACE_PATTERN = Pattern.compile("interface\\s+(\\w+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("(?:public|private|protected|static)\\s+\\w+\\s+(\\w+)\\s*\\(");

    private final Map<String, Set<String>> invertedIndex = new ConcurrentHashMap<>();
    private final Map<String, String> fileContents = new ConcurrentHashMap<>();

    @Override
    public void buildIndex() {
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        final int maxDepth = 15;
        try {
            Files.walk(projectRoot, maxDepth)
                    .filter(Files::isRegularFile)
                    .filter(SearchEngineUtils::isCodeFile)
                    .filter(SearchEngineUtils::isNotInIgnoredDir)
                    .forEach(path -> {
                        try {
                            String relativePath = projectRoot.relativize(path).toString().replace("\\", "/");
                            String content = Files.readString(path);
                            fileContents.put(relativePath, content);
                            indexFile(relativePath, content);
                        } catch (IOException | SecurityException | OutOfMemoryError e) {
                            logger.debug("跳过文件 {}: {}", path, e.getMessage());
                        } catch (Exception e) {
                            logger.debug("处理文件 {} 时发生异常: {}", path, e.getMessage());
                        }
                    });
            logger.debug("构建倒排索引完成，共 {} 个文件, {} 个词条", fileContents.size(), invertedIndex.size());
        } catch (IOException e) {
            logger.warn("扫描文件失败: {}", e.getMessage());
        }
    }

    private void indexFile(String filePath, String content) {
        Set<String> symbols = extractSymbols(content);
        for (String symbol : symbols) {
            invertedIndex
                    .computeIfAbsent(symbol.toLowerCase(), k -> new HashSet<>())
                    .add(filePath);
        }
    }

    private Set<String> extractSymbols(String content) {
        Set<String> symbols = new HashSet<>();

        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        while (classMatcher.find()) {
            symbols.add(classMatcher.group(1));
        }

        Matcher interfaceMatcher = INTERFACE_PATTERN.matcher(content);
        while (interfaceMatcher.find()) {
            symbols.add(interfaceMatcher.group(1));
        }

        Matcher methodMatcher = METHOD_PATTERN.matcher(content);
        while (methodMatcher.find()) {
            symbols.add(methodMatcher.group(1));
        }

        return symbols;
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        List<SearchResult> emptyResults = new ArrayList<>();
        if (query == null || query.isEmpty() || maxResults <= 0) {
            return emptyResults;
        }

        Map<String, Double> scores = new HashMap<>();
        String[] keywords = query.toLowerCase().split("[\\s_\\-]+");

        for (String keyword : keywords) {
            if (keyword.length() < 2) continue;

            Set<String> matchingFiles = invertedIndex.getOrDefault(keyword, new HashSet<>());
            for (String file : matchingFiles) {
                scores.put(file, scores.getOrDefault(file, 0.0) + 1.0);
            }
        }

        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            double tfIdfScore = calculateSimpleScore(entry.getValue(), entry.getKey(), keywords);
            scores.put(entry.getKey(), scores.getOrDefault(entry.getKey(), 0.0) + tfIdfScore);
        }

        List<SearchResult> results = new ArrayList<>();
        scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(maxResults)
                .forEach(entry -> {
                    if (entry.getValue() > 0.1) {
                        results.add(new SearchResult(
                                entry.getKey(),
                                generatePreview(fileContents.get(entry.getKey()), keywords),
                                entry.getValue()
                        ));
                    }
                });

        return results;
    }

    @Override
    public int getIndexSize() {
        return fileContents.size();
    }
}
