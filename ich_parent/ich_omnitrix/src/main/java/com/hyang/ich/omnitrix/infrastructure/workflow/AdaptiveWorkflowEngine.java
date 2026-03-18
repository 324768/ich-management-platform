package com.hyang.ich.omnitrix.infrastructure.workflow;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 自适应工作流引擎 - AI 自主决策的循环执行引擎
 * 参考 Kortex AdaptiveWorkflowEngine 设计
 */
@Slf4j
@Component
public class AdaptiveWorkflowEngine {
    
    private final DecisionNode decisionNode;
    private final ToolCallNode toolCallNode;
    private final ResultFilterNode resultFilterNode;
    private final AnswerGenerationNode answerGenerationNode;
    private final ReflectionNode reflectionNode;
    
    private static final int DEFAULT_MAX_ITERATIONS = 10;
    
    public AdaptiveWorkflowEngine(DecisionNode decisionNode,
                                  ToolCallNode toolCallNode,
                                  ResultFilterNode resultFilterNode,
                                  AnswerGenerationNode answerGenerationNode,
                                  ReflectionNode reflectionNode) {
        this.decisionNode = decisionNode;
        this.toolCallNode = toolCallNode;
        this.resultFilterNode = resultFilterNode;
        this.answerGenerationNode = answerGenerationNode;
        this.reflectionNode = reflectionNode;
    }
    
    /**
     * 执行工作流（同步版本）
     */
    public String execute(String userQuery, List<Object> tools) {
        return execute(userQuery, tools, DEFAULT_MAX_ITERATIONS);
    }
    
    /**
     * 执行工作流（指定迭代次数）
     */
    public String execute(String userQuery, List<Object> tools, int maxIterations) {
        AiWorkflowState state = AiWorkflowState.builder()
                .userQuery(userQuery)
                .maxIterations(maxIterations)
                .build();
        
        log.info("AdaptiveWorkflowEngine 开始执行: query={}, maxIterations={}", 
                userQuery.substring(0, Math.min(50, userQuery.length())), maxIterations);
        
        while (!state.isTerminated() && !state.reachedMaxIterations()) {
            Decision decision = decisionNode.decide(state);
            state.setLastAction(decision.getAction());
            
            log.info("决策: action={}, reasoning={}, toolName={}", 
                    decision.getAction(), decision.getReasoning(), decision.getToolName());
            
            switch (decision.getAction()) {
                case CALL_TOOL:
                    handleToolCall(state, decision, tools);
                    break;
                case DIRECT_ANSWER:
                    handleDirectAnswer(state);
                    break;
                case REPLAN:
                    handleReplan(state);
                    break;
                case FINISH:
                    state.setTerminated(true);
                    log.info("工作流完成：决策完成");
                    break;
                default:
                    handleDirectAnswer(state);
            }
            
            state.incrementIteration();
        }
        
        if (!state.isTerminated() && state.reachedMaxIterations()) {
            log.warn("工作流达到最大迭代次数: {}", maxIterations);
            state.setCurrentAnswer("任务执行达到最大迭代次数(" + maxIterations + ")，请重试或简化查询。");
        }
        
        return state.getCurrentAnswer();
    }
    
    private void handleToolCall(AiWorkflowState state, Decision decision, List<Object> tools) {
        ToolCallResult result = toolCallNode.execute(state, decision, tools);
        state.getToolCalls().add(result.toRecord());
        
        log.info("工具调用结果: tool={}, success={}, duration={}ms", 
                result.getToolName(), result.isSuccess(), result.getDurationMs());
        
        if (resultFilterNode.shouldGenerateAnswer(result, state)) {
            String answer = answerGenerationNode.generate(state);
            state.setCurrentAnswer(answer);
            state.setTerminated(true);
            log.info("工作流完成：结果质量满足，生成答案");
        } else if (!result.isSuccess()) {
            state.resetConsecutiveSuccess();
        }
    }
    
    private void handleDirectAnswer(AiWorkflowState state) {
        String answer = answerGenerationNode.generate(state);
        state.setCurrentAnswer(answer);
        state.setTerminated(true);
        log.info("工作流完成：直接生成答案");
    }
    
    private void handleReplan(AiWorkflowState state) {
        ReflectionResult reflection = reflectionNode.reflect(state);
        state.getReflections().add(reflection.getProblemAnalysis());
        
        log.info("反思结果: problem={}, looping={}", 
                reflection.getProblemAnalysis(), reflection.isLooping());
        
        if (reflection.isLooping() || state.isReplanLooping()) {
            state.setTerminated(true);
            state.setCurrentAnswer("任务遇到循环，无法继续执行。建议您重新描述问题。");
            log.warn("工作流终止：检测到反思循环");
        }
    }
    
    /**
     * 流式版本 - 带回调
     */
    public void executeWithStream(String userQuery, List<Object> tools, Consumer<String> chunkConsumer) {
        String result = execute(userQuery, tools);
        
        if (chunkConsumer != null && result != null) {
            String[] sentences = result.split("(?<=[。！？.!?])");
            for (String sentence : sentences) {
                if (!sentence.trim().isEmpty()) {
                    chunkConsumer.accept(sentence);
                }
            }
        }
    }
    
    /**
     * 流式版本 - 带状态回调
     */
    public void executeWithStateStream(String userQuery, List<Object> tools, 
                                       Consumer<WorkflowEvent> eventConsumer) {
        AiWorkflowState state = AiWorkflowState.builder()
                .userQuery(userQuery)
                .maxIterations(DEFAULT_MAX_ITERATIONS)
                .build();
        
        if (eventConsumer != null) {
            eventConsumer.accept(new WorkflowEvent("START", userQuery, null, 0));
        }
        
        while (!state.isTerminated() && !state.reachedMaxIterations()) {
            long startTime = System.currentTimeMillis();
            
            Decision decision = decisionNode.decide(state);
            state.setLastAction(decision.getAction());
            
            if (eventConsumer != null) {
                eventConsumer.accept(new WorkflowEvent("DECISION", 
                        decision.getAction().name(), decision.getReasoning(), 
                        (int) (System.currentTimeMillis() - startTime)));
            }
            
            switch (decision.getAction()) {
                case CALL_TOOL:
                    handleToolCall(state, decision, tools);
                    if (eventConsumer != null && !state.getToolCalls().isEmpty()) {
                        ToolCallRecord lastCall = state.getToolCalls()
                                .get(state.getToolCalls().size() - 1);
                        eventConsumer.accept(new WorkflowEvent("TOOL_RESULT",
                                lastCall.getToolName(), lastCall.getResult(),
                                lastCall.getDurationMs()));
                    }
                    break;
                case DIRECT_ANSWER:
                    handleDirectAnswer(state);
                    if (eventConsumer != null) {
                        eventConsumer.accept(new WorkflowEvent("ANSWER", 
                                state.getCurrentAnswer(), null, 0));
                    }
                    break;
                case REPLAN:
                    handleReplan(state);
                    break;
                case FINISH:
                    state.setTerminated(true);
                    break;
            }
            
            state.incrementIteration();
        }
        
        if (eventConsumer != null) {
            eventConsumer.accept(new WorkflowEvent("COMPLETE", 
                    state.getCurrentAnswer(), null, 0));
        }
    }
    
    @Data
    @AllArgsConstructor
    public static class WorkflowEvent {
        private String eventType;
        private String content;
        private String metadata;
        private int durationMs;
    }
}
