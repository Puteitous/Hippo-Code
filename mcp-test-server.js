const readline = require('readline');
const fs = require('fs');
const path = require('path');

const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: false
});

let requestIdCounter = 1;

function sendResponse(id, result) {
    const response = {
        jsonrpc: "2.0",
        id: id,
        result: result
    };
    process.stdout.write(JSON.stringify(response) + '\n');
}

function sendError(id, code, message) {
    const response = {
        jsonrpc: "2.0",
        id: id,
        error: {
            code: code,
            message: message
        }
    };
    process.stdout.write(JSON.stringify(response) + '\n');
}

const testResources = {
    "file:///README.md": {
        name: "README.md",
        description: "项目说明文档",
        mimeType: "text/markdown",
        content: "# MCP Echo Test Server\n\n这是一个用于测试 MCP 协议的 Echo 服务器。\n\n## 功能\n\n- echo: 回显消息\n- add: 计算两个数字的和\n- resources: 提供示例文件内容\n"
    },
    "file:///config.example.yaml": {
        name: "配置示例",
        description: "MCP 服务器配置示例",
        mimeType: "text/yaml",
        content: "mcp:\n  enabled: true\n  auto_connect: true\n  servers:\n    - id: echo\n      name: Echo Test Server\n      type: stdio\n      command: node\n      args: [\"mcp-test-server.js\"]\n"
    },
    "test:///server-status": {
        name: "服务器状态",
        description: "当前服务器运行状态",
        mimeType: "text/plain",
        content: `服务器状态: 运行中\n启动时间: ${new Date().toISOString()}\n版本: 1.0.0\n协议: 2024-11-05\n`
    }
};

console.error("MCP Echo Server 启动...");

rl.on('line', (line) => {
    try {
        const msg = JSON.parse(line);
        console.error("收到请求:", JSON.stringify(msg, null, 2));

        if (msg.method === 'initialize') {
            sendResponse(msg.id, {
                protocolVersion: "2024-11-05",
                serverInfo: {
                    name: "echo-server",
                    version: "1.0.0"
                },
                capabilities: {
                    tools: {},
                    resources: {}
                }
            });
            console.error("✅ 初始化完成 (支持: tools, resources)");
        }
        else if (msg.method === 'initialized') {
            console.error("✅ 客户端已确认初始化");
        }
        else if (msg.method === 'tools/list') {
            sendResponse(msg.id, {
                tools: [
                    {
                        name: "echo",
                        description: "回显输入的消息内容 - 测试用工具",
                        inputSchema: {
                            type: "object",
                            properties: {
                                message: {
                                    type: "string",
                                    description: "要回显的消息内容"
                                }
                            },
                            required: ["message"]
                        }
                    },
                    {
                        name: "add",
                        description: "计算两个数字的和 - 测试用工具",
                        inputSchema: {
                            type: "object",
                            properties: {
                                a: {
                                    type: "number",
                                    description: "第一个数字"
                                },
                                b: {
                                    type: "number",
                                    description: "第二个数字"
                                }
                            },
                            required: ["a", "b"]
                        }
                    },
                    {
                        name: "get_server_info",
                        description: "获取MCP服务器信息 - 测试用工具",
                        inputSchema: {
                            type: "object",
                            properties: {}
                        }
                    }
                ]
            });
            console.error("✅ 返回工具列表: 3个工具");
        }
        else if (msg.method === 'resources/list') {
            const resources = Object.entries(testResources).map(([uri, info]) => ({
                uri: uri,
                name: info.name,
                description: info.description,
                mimeType: info.mimeType
            }));
            sendResponse(msg.id, { resources });
            console.error(`✅ 返回资源列表: ${resources.length} 个资源`);
        }
        else if (msg.method === 'resources/read') {
            const uri = msg.params.uri;
            const resource = testResources[uri];
            
            if (resource) {
                sendResponse(msg.id, {
                    contents: [
                        {
                            uri: uri,
                            mimeType: resource.mimeType,
                            text: resource.content
                        }
                    ]
                });
                console.error(`✅ 读取资源: ${uri}`);
            } else {
                sendError(msg.id, -32602, `资源不存在: ${uri}`);
            }
        }
        else if (msg.method === 'tools/call') {
            const toolName = msg.params.name;
            const args = msg.params.arguments || {};

            console.error(`调用工具: ${toolName}, 参数:`, args);

            if (toolName === 'echo') {
                sendResponse(msg.id, {
                    content: [
                        {
                            type: "text",
                            text: `Echo: ${args.message}`
                        }
                    ]
                });
            }
            else if (toolName === 'add') {
                const sum = args.a + args.b;
                sendResponse(msg.id, {
                    content: [
                        {
                            type: "text",
                            text: `${args.a} + ${args.b} = ${sum}`
                        }
                    ]
                });
            }
            else if (toolName === 'get_server_info') {
                sendResponse(msg.id, {
                    content: [
                        {
                            type: "text",
                            text: JSON.stringify({
                                server: "echo-server",
                                version: "1.0.0",
                                tools: ["echo", "add", "get_server_info"],
                                resources: Object.keys(testResources),
                                timestamp: new Date().toISOString()
                            }, null, 2)
                        }
                    ]
                });
            }
            else {
                sendError(msg.id, -32601, `未知工具: ${toolName}`);
            }
        }
        else {
            console.error("未知方法:", msg.method);
            sendError(msg.id, -32601, `方法未实现: ${msg.method}`);
        }
    } catch (e) {
        console.error("解析错误:", e.message, line);
    }
});

process.on('SIGINT', () => {
    console.error("收到关闭信号，正在退出...");
    process.exit(0);
});
