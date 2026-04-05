package com.example.agent;

public class SimpleJavaAgent {

    public static void main(String[] args) {
        AgentApplication.main(args);
    }

    public void run() {
        AgentApplication app = new AgentApplication();
        app.run();
    }
}
