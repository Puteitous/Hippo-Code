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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.example.agent.context.memory.search.SearchEngineUtils.*;

public class SimpleKeywordSearch implements CodeSearchStrategy {
    private static final Logger logger = LoggerFactory.getLogger(SimpleKeywordSearch.class);

    private final Map<String, String> fileContents = new ConcurrentHashMap<>();

    @Override
    public void buildIndex() {
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        try {
            Files.walk(projectRoot)
                    .filter(Files::isRegularFile)
                    .filter(SearchEngineUtils::isCodeFile)
                    .filter(SearchEngineUtils::isNotInIgnoredDir)
                    .forEach(path -> {
                        try {
                            String relativePath = projectRoot.relativize(path).toString().replace("\\", "/");
                            fileContents.put(relativePath, Files.readString(path));
                        } catch (IOException ignored) {}
                    });
        } catch (IOException e) {
            logger.warn("扫描文件失败: {}", e.getMessage());
        }
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        String[] keywords = query.toLowerCase().split("[\\s_\\-]+");

        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            double score = calculateSimpleScore(entry.getValue(), entry.getKey(), keywords);
            if (score > 0.1) {
                String preview = SearchEngineUtils.generatePreview(entry.getValue(), keywords);
                results.add(new SearchResult(entry.getKey(), preview, score));
            }
        }

        results.sort((a, b) -> Double.compare(b.score, a.score));
        return results.size() > maxResults ? results.subList(0, maxResults) : results;
    }

    @Override
    public int getIndexSize() {
        return fileContents.size();
    }

    private double calculateSimpleScore(String content, String path, String[] keywords) {
        String contentLower = content.toLowerCase();
        String pathLower = path.toLowerCase();
        double score = 0.0;

        for (String keyword : keywords) {
            if (keyword.length() < 2) continue;
            if (pathLower.contains(keyword)) score += 3.0;
            if (contentLower.contains(keyword)) score += 1.0;
        }
        return Math.min(1.0, score / 10.0);
    }
}
