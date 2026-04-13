const readline = require('readline');

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
                    tools: {}
                }
            });
            console.error("✅ 初始化完成");
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
        console.error("处理错误:", e.message);
    }
});

process.on('SIGINT', () => {
    console.error("MCP Echo Server 关闭");
    process.exit(0);
});
