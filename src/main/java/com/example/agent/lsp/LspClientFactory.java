package com.example.agent.lsp;

import com.example.agent.config.Config;
import com.example.agent.lsp.config.LspConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class LspClientFactory {

    private static final Logger logger = LoggerFactory.getLogger(LspClientFactory.class);

    private LspClientFactory() {
    }

    public static LspClient create(String languageId) {
        return create(languageId, Config.getInstance().getLsp());
    }

    public static LspClient create(String languageId, LspConfig config) {
        LspConfig.LspServerConfig serverConfig = config.getServer(languageId);
        if (serverConfig == null || !serverConfig.isEnabled()) {
            logger.warn("LSP 服务器未启用或未配置: {}", languageId);
            return null;
        }

        String resolvedCommand = resolveVariables(serverConfig.getCommand());
        List<String> resolvedArgs = serverConfig.getArgs().stream()
                .map(LspClientFactory::resolveVariables)
                .collect(Collectors.toList());

        logger.info("创建 LSP 客户端: {} - {}", languageId, resolvedCommand);
        logger.debug("命令参数: {}", resolvedArgs);

        return new LspClient(
                languageId,
                resolvedCommand,
                resolvedArgs,
                Paths.get(".").toAbsolutePath().normalize(),
                serverConfig.getEnv()
        );
    }

    private static String resolveVariables(String value) {
        if (value == null) {
            return null;
        }
        String result = value;
        result = result.replace("${user_home}", System.getProperty("user.home"));
        result = result.replace("${user.dir}", System.getProperty("user.dir"));
        return result;
    }
}
