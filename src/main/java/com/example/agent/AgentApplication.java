package com.example.agent;

import com.example.agent.console.AgentUi;
import com.example.agent.console.CommandDispatcher;
import com.example.agent.console.InputHandler;
import com.example.agent.console.ConsoleStyle;
import com.example.agent.core.AgentContext;
import com.example.agent.execute.AgentTurnExecutor;
import com.example.agent.execute.ConversationLoop;
import com.example.agent.execute.ToolCallProcessor;
import com.example.agent.intent.HybridIntentRecognizer;
import com.example.agent.intent.IntentRecognizer;
import com.example.agent.plan.CompositeTaskPlanner;
import com.example.agent.plan.LlmTaskPlanner;
import com.example.agent.plan.PlanExecutor;
import com.example.agent.plan.SequentialPlanExecutor;
import com.example.agent.plan.SimpleTaskPlanner;
import com.example.agent.plan.TaskPlanner;
import com.example.agent.service.TokenEstimator;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;

import java.io.IOException;

public class AgentApplication {

    private boolean intentRecognitionEnabled = true;
    private boolean planningEnabled = true;

    public static void main(String[] args) {
        AgentApplication app = new AgentApplication();
        app.run();
    }

    public void run() {
        AgentContext context = null;
        try {
            context = new AgentContext();
            context.initialize();

            AgentUi ui = new AgentUi(context.getTerminal(), context.getConfig());
            TokenEstimator tokenEstimator = context.getTokenEstimator();
            InputHandler inputHandler = new InputHandler(context.getReader(), tokenEstimator);

            CommandDispatcher dispatcher = new CommandDispatcher(context, ui, inputHandler);

            if (!dispatcher.validateConfig()) {
                return;
            }

            ToolCallProcessor toolCallProcessor = new ToolCallProcessor(
                    context.getConcurrentToolExecutor(),
                    context.getConversationManager(),
                    ui
            );

            AgentTurnExecutor turnExecutor = new AgentTurnExecutor(context, toolCallProcessor, ui);

            IntentRecognizer intentRecognizer = createIntentRecognizer(context);

            TaskPlanner taskPlanner = createTaskPlanner(context);

            PlanExecutor planExecutor = createPlanExecutor();

            ConversationLoop conversationLoop = new ConversationLoop(
                    context, turnExecutor, inputHandler, ui, intentRecognizer, taskPlanner, planExecutor
            );

            context.getTerminal().handle(org.jline.terminal.Terminal.Signal.INT, signal -> {
                conversationLoop.interrupt();
            });

            ui.printWelcome();

            LineReader reader = context.getReader();
            while (true) {
                try {
                    String line = reader.readLine(ConsoleStyle.prompt());

                    CommandDispatcher.CommandResult result = dispatcher.dispatch(line);

                    if (result.getType() == CommandDispatcher.CommandResult.Type.EXIT) {
                        break;
                    }

                    if (result.getType() == CommandDispatcher.CommandResult.Type.CONTINUE) {
                        continue;
                    }

                    if (result.getType() == CommandDispatcher.CommandResult.Type.PROCESS_INPUT) {
                        String actualInput = result.getInput();
                        conversationLoop.processUserInput(actualInput);
                        dispatcher.setCurrentConversationId(conversationLoop.getCurrentConversationId());
                    }

                } catch (UserInterruptException e) {
                    if (turnExecutor.isInterrupted()) {
                        ui.printInterrupted();
                        turnExecutor.setInterrupted(false);
                    } else {
                        ui.printCtrlC();
                        ui.printGoodbye();
                        break;
                    }
                } catch (EndOfFileException e) {
                    ui.printGoodbye();
                    break;
                }
            }

        } catch (IOException e) {
            System.err.println(ConsoleStyle.error("终端错误: " + e.getMessage()));
        } finally {
            if (context != null) {
                context.close();
            }
        }
    }

    private IntentRecognizer createIntentRecognizer(AgentContext context) {
        if (!intentRecognitionEnabled) {
            return null;
        }

        HybridIntentRecognizer recognizer = new HybridIntentRecognizer(context.getLlmClient());
        recognizer.setPreferLlm(false);
        return recognizer;
    }

    private TaskPlanner createTaskPlanner(AgentContext context) {
        if (!planningEnabled) {
            return null;
        }

        SimpleTaskPlanner simplePlanner = new SimpleTaskPlanner();
        LlmTaskPlanner llmPlanner = new LlmTaskPlanner(context.getLlmClient());

        CompositeTaskPlanner compositePlanner = new CompositeTaskPlanner(simplePlanner, llmPlanner);
        compositePlanner.setPreferLlm(false);

        return compositePlanner;
    }

    private PlanExecutor createPlanExecutor() {
        if (!planningEnabled) {
            return null;
        }

        return new SequentialPlanExecutor();
    }

    public void setIntentRecognitionEnabled(boolean enabled) {
        this.intentRecognitionEnabled = enabled;
    }

    public void setPlanningEnabled(boolean enabled) {
        this.planningEnabled = enabled;
    }
}
