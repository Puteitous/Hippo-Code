package com.example.agent.orchestrator.model;

import com.example.agent.llm.model.ToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ToolExecutionPlan {

    private static final Logger logger = LoggerFactory.getLogger(ToolExecutionPlan.class);

    private final Map<String, ToolNode> nodes = new LinkedHashMap<>();
    private final Map<String, Set<String>> dependencies = new HashMap<>();
    private final List<ToolDependency> detectedDependencies = new ArrayList<>();

    public void addNode(ToolCall call) {
        if (!nodes.containsKey(call.getId())) {
            nodes.put(call.getId(), new ToolNode(call));
        }
    }

    public void addDependency(ToolCall from, ToolCall to, ToolDependencyType type) {
        addDependency(from.getId(), to.getId(), type);
    }

    public void addDependency(String fromId, String toId, ToolDependencyType type) {
        ToolNode fromNode = nodes.get(fromId);
        ToolNode toNode = nodes.get(toId);

        if (fromNode != null && toNode != null) {
            toNode.addDependency(fromNode);
            detectedDependencies.add(new ToolDependency(fromId, toId, type));

            logger.debug("检测到依赖 [{}]: {} -> {}",
                    type.getDescription(),
                    fromNode.getToolName(),
                    toNode.getToolName());
        }
    }

    public List<ToolNode> getRunnableNodes() {
        return nodes.values().stream()
                .filter(ToolNode::isRunnable)
                .sorted(Comparator.comparingInt(n -> n.getDependencies().size()))
                .collect(Collectors.toList());
    }

    public boolean hasPendingNodes() {
        return nodes.values().stream()
                .anyMatch(n -> n.getStatus() == ExecutionStatus.PENDING);
    }

    public List<ToolNode> getAllNodes() {
        return new ArrayList<>(nodes.values());
    }

    public ToolNode getNode(String toolCallId) {
        return nodes.get(toolCallId);
    }

    public List<ToolDependency> getDetectedDependencies() {
        return new ArrayList<>(detectedDependencies);
    }

    public int getNodeCount() {
        return nodes.size();
    }

    public int getDependencyCount() {
        return detectedDependencies.size();
    }

    public boolean hasCycle() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String nodeId : nodes.keySet()) {
            if (hasCycleDfs(nodeId, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCycleDfs(String nodeId, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(nodeId)) {
            return true;
        }
        if (visited.contains(nodeId)) {
            return false;
        }

        visited.add(nodeId);
        recursionStack.add(nodeId);

        ToolNode node = nodes.get(nodeId);
        if (node != null) {
            for (ToolNode dep : node.getDependencies()) {
                if (hasCycleDfs(dep.getToolCallId(), visited, recursionStack)) {
                    return true;
                }
            }
        }

        recursionStack.remove(nodeId);
        return false;
    }

    public static class ToolDependency {
        private final String fromId;
        private final String toId;
        private final ToolDependencyType type;

        public ToolDependency(String fromId, String toId, ToolDependencyType type) {
            this.fromId = fromId;
            this.toId = toId;
            this.type = type;
        }

        public String getFromId() {
            return fromId;
        }

        public String getToId() {
            return toId;
        }

        public ToolDependencyType getType() {
            return type;
        }
    }
}
