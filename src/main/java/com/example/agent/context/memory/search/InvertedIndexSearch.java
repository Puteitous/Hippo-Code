package com.example.agent.context.memory.search;

import com.example.agent.context.memory.CodeSearchStrategy;
import com.example.agent.context.memory.SearchResult;
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

import static com.example.agent.context.memory.search.SearchEngineUtils.*;

public class InvertedIndexSearch implements CodeSearchStrategy {
    private static final Logger logger = LoggerFactory.getLogger(InvertedIndexSearch.class);

    private static final Pattern CLASS_PATTERN = Pattern.compile("class\\s+(\\w+)");
    private static final Pattern INTERFACE_PATTERN = Pattern.compile("interface\\s+(\\w+)");
    private static final Pattern METHOD_PATTERN = Pattern.compile("(?:public|private|protected|static)\\s+\\w+\\s+(\\w+)\\s*\\(");

    private final Map<String, Set<IndexedFile>> invertedIndex = new ConcurrentHashMap<>();
    private final Map<String, String> fileContentCache = new ConcurrentHashMap<>();
    private Path projectRoot;

    @Override
    public void buildIndex() {
        projectRoot = Paths.get(System.getProperty("user.dir"));

        try {
            Files.walk(projectRoot)
                    .filter(Files::isRegularFile)
                    .filter(SearchEngineUtils::isCodeFile)
                    .filter(SearchEngineUtils::isNotInIgnoredDir)
                    .forEach(this::indexFile);
        } catch (IOException e) {
            logger.warn("构建索引失败: {}", e.getMessage());
        }
    }

    @Override
    public int getIndexSize() {
        return fileContentCache.size();
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        String[] keywords = query.toLowerCase().split("[\\s_\\-]+");

        Map<String, Double> fileScores = new HashMap<>();
        for (String keyword : keywords) {
            if (keyword.length() < 2) continue;

            Set<IndexedFile> matchedFiles = invertedIndex.get(keyword);
            if (matchedFiles != null) {
                for (IndexedFile indexedFile : matchedFiles) {
                    double score = calculateMatchScore(
                            fileContentCache.get(indexedFile.path),
                            indexedFile.path,
                            keywords);
                    fileScores.merge(indexedFile.path, score, Double::sum);
                }
            }
        }

        for (Map.Entry<String, Double> entry : fileScores.entrySet()) {
            if (entry.getValue() > 0.1) {
                String preview = SearchEngineUtils.generatePreview(fileContentCache.get(entry.getKey()), keywords);
                results.add(new SearchResult(entry.getKey(), preview, entry.getValue()));
            }
        }

        results.sort((a, b) -> Double.compare(b.score, a.score));
        if (results.size() > maxResults) {
            results = results.subList(0, maxResults);
        }

        logger.debug("倒排检索完成: '{}' 找到 {} 个文件，耗时 {}ms",
                query, results.size(), System.currentTimeMillis() - startTime);

        return results;
    }

    private void indexFile(Path path) {
        try {
            String content = Files.readString(path);
            String relativePath = projectRoot.relativize(path).toString().replace("\\", "/");

            fileContentCache.put(relativePath, content);

            Set<String> words = extractFeatures(content, relativePath);

            for (String word : words) {
                invertedIndex.computeIfAbsent(word, k -> new HashSet<>())
                        .add(new IndexedFile(relativePath, calculateTermFrequency(word, content)));
            }
        } catch (IOException e) {
        }
    }

    private Set<String> extractFeatures(String content, String filePath) {
        Set<String> features = new HashSet<>();
        String contentLower = content.toLowerCase();

        Matcher classMatcher = CLASS_PATTERN.matcher(content);
        while (classMatcher.find()) {
            features.add(classMatcher.group(1).toLowerCase());
        }

        Matcher interfaceMatcher = INTERFACE_PATTERN.matcher(content);
        while (interfaceMatcher.find()) {
            features.add(interfaceMatcher.group(1).toLowerCase());
        }

        Matcher methodMatcher = METHOD_PATTERN.matcher(content);
        while (methodMatcher.find()) {
            features.add(methodMatcher.group(1).toLowerCase());
        }

        String fileName = filePath.toLowerCase().replaceAll("\\.[a-z]+", "");
        for (String part : fileName.split("[/_\\-]")) {
            if (part.length() >= 2) {
                features.add(part);
            }
        }

        return features;
    }

    private double calculateTermFrequency(String term, String content) {
        int count = 0;
        int idx = content.toLowerCase().indexOf(term);
        while (idx != -1) {
            count++;
            idx = content.toLowerCase().indexOf(term, idx + 1);
        }
        return count > 0 ? Math.min(1.0, count * 0.1) : 0;
    }

    private double calculateMatchScore(String content, String filePath, String[] keywords) {
        String contentLower = content.toLowerCase();
        String pathLower = filePath.toLowerCase();
        double score = 0.0;

        for (String keyword : keywords) {
            if (keyword.length() < 2) continue;

            Matcher classMatcher = CLASS_PATTERN.matcher(content);
            while (classMatcher.find()) {
                if (classMatcher.group(1).toLowerCase().contains(keyword)) {
                    score += 10.0;
                    break;
                }
            }

            Matcher interfaceMatcher = INTERFACE_PATTERN.matcher(content);
            while (interfaceMatcher.find()) {
                if (interfaceMatcher.group(1).toLowerCase().contains(keyword)) {
                    score += 8.0;
                    break;
                }
            }

            Matcher methodMatcher = METHOD_PATTERN.matcher(content);
            while (methodMatcher.find()) {
                if (methodMatcher.group(1).toLowerCase().contains(keyword)) {
                    score += 5.0;
                    break;
                }
            }

            if (pathLower.contains(keyword)) score += 3.0;
            if (contentLower.contains(keyword)) score += 1.0;
        }

        return Math.min(1.0, score / 20.0);
    }

    private static class IndexedFile {
        final String path;
        final double termFrequency;

        IndexedFile(String path, double termFrequency) {
            this.path = path;
            this.termFrequency = termFrequency;
        }
    }
}
