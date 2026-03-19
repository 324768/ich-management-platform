package com.hyang.ich.omnitrix.brain.tools;

import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.infrastructure.vector.VectorRagService;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG 知识库工具
 * 用于 AI 对话中检索知识库内容
 */
@Slf4j
@Component
public class KnowledgeBaseTools {

    private final VectorRagService ragService;

    public KnowledgeBaseTools(VectorRagService ragService) {
        this.ragService = ragService;
    }

    /**
     * 检索知识库
     *
     * @param query 查询文本
     * @return 检索结果
     */
    @Tool("检索知识库内容。当用户询问非遗项目、传统文化知识时，使用此工具查询知识库。" +
            "参数：查询文本")
    public String searchKnowledgeBase(String query) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }

        if (query == null || query.trim().isEmpty()) {
            return ToolResultWrapper.error("请提供有效的查询内容").withErrorHint().toXml();
        }

        try {
            List<VectorRagService.RagResult> results = ragService.retrieve(query).stream()
                    .map(r -> VectorRagService.RagResult.builder()
                            .answer(r.getText())
                            .sources(List.of(VectorRagService.Source.builder()
                                    .id(r.getId())
                                    .text(r.getText())
                                    .score(r.getScore())
                                    .build()))
                            .build())
                    .collect(Collectors.toList());

            if (results.isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("知识库中没有找到相关内容")
                        .withSearchEmptyHint("知识库").toXml();
            }

            // 构建结果文本
            StringBuilder sb = new StringBuilder();
            sb.append("找到以下知识库相关内容：\n\n");

            for (int i = 0; i < results.size(); i++) {
                VectorRagService.RagResult result = results.get(i);
                sb.append("【相关内容 ").append(i + 1).append("】\n");
                sb.append(result.getAnswer()).append("\n\n");
            }

            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("找到" + results.size() + "条相关内容", sb.toString())
                    .withSearchSuccessHint("知识库").toXml();

        } catch (Exception e) {
            log.error("知识库检索失败: {}", e.getMessage(), e);
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("知识库检索失败: " + e.getMessage())
                    .withErrorHint().toXml();
        }
    }

    /**
     * RAG问答（带生成）
     *
     * @param query 用户问题
     * @return RAG回答结果
     */
    @Tool("知识库问答。当用户询问需要准确回答的知识性问题时（如非遗项目详情、传统文化知识等），" +
            "使用此工具可以直接获取AI生成的回答。" +
            "参数：用户问题")
    public String answerFromKnowledgeBase(String query) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }

        if (query == null || query.trim().isEmpty()) {
            return ToolResultWrapper.error("请提供有效的问题").withErrorHint().toXml();
        }

        try {
            VectorRagService.RagResult result = ragService.answer(query);

            if (result.getSources().isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("知识库中没有找到相关内容")
                        .withSearchEmptyHint("知识库").toXml();
            }

            // 构建结果
            StringBuilder sb = new StringBuilder();
            sb.append("回答：\n").append(result.getAnswer()).append("\n\n");
            sb.append("参考来源：\n");

            for (int i = 0; i < result.getSources().size(); i++) {
                VectorRagService.Source source = result.getSources().get(i);
                sb.append(String.format("%d. (相似度: %.2f) %s\n",
                        i + 1, source.getScore(),
                        source.getText().substring(0, Math.min(100, source.getText().length())) + "..."));
            }

            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("知识库问答完成", sb.toString())
                    .withSearchSuccessHint("知识库").toXml();

        } catch (Exception e) {
            log.error("知识库问答失败: {}", e.getMessage(), e);
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("知识库问答失败: " + e.getMessage())
                    .withErrorHint().toXml();
        }
    }

    /**
     * 获取知识库状态
     */
    @Tool("获取知识库状态信息，包括文档数量、向量数量等")
    public String getKnowledgeBaseStatus() {
        try {
            var status = ragService.getKnowledgeBaseStatus();

            if (status == null) {
                return ToolResultWrapper.empty("知识库未初始化").toXml();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("- 集合名称: ").append(status.getName()).append("\n");
            sb.append("- 文档数量: ").append(status.getPointsCount()).append("\n");
            sb.append("- 向量数量: ").append(status.getVectorsCount()).append("\n");
            sb.append("- 状态: ").append(status.getStatus()).append("\n");

            return ToolResultWrapper.success("知识库状态", sb.toString()).toXml();

        } catch (Exception e) {
            log.error("获取知识库状态失败: {}", e.getMessage(), e);
            return ToolResultWrapper.error("获取知识库状态失败: " + e.getMessage()).toXml();
        }
    }
}
