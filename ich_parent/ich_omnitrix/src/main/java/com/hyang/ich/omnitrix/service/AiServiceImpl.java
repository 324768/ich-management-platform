package com.hyang.ich.omnitrix.service;

import com.hyang.ich.ai.AiService;
import com.hyang.ich.ai.dto.AiChatRequestDTO;
import com.hyang.ich.ai.dto.AiConversationDTO;
import com.hyang.ich.ai.dto.AiKnowledgeBaseDTO;
import com.hyang.ich.ai.dto.AiMessageDTO;
import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.dto.IchItemDTO;
import com.hyang.ich.omnitrix.dto.ChatResponse;
import com.hyang.ich.omnitrix.entity.AiConversation;
import com.hyang.ich.omnitrix.entity.AiKnowledgeBase;
import com.hyang.ich.omnitrix.entity.AiMessage;
import com.hyang.ich.omnitrix.orchestrator.OrchestratorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@DubboService
public class AiServiceImpl implements AiService {

    private final OrchestratorService orchestratorService;
    private final ConversationService conversationService;
    private final KnowledgeService knowledgeService;

    public AiServiceImpl(OrchestratorService orchestratorService,
                         ConversationService conversationService,
                         KnowledgeService knowledgeService) {
        this.orchestratorService = orchestratorService;
        this.conversationService = conversationService;
        this.knowledgeService = knowledgeService;
    }

    // ========== 智能问答 ==========

    @Override
    public AiMessageDTO chat(AiChatRequestDTO requestDTO) {
        // AiChatRequestDTO 没有 sessionId，用 conversationId 查找或生成
        String sessionId;
        if (requestDTO.getConversationId() != null) {
            AiConversation conv = conversationService.getById(requestDTO.getConversationId());
            sessionId = (conv != null) ? conv.getSessionId() : UUID.randomUUID().toString().replace("-", "");
        } else {
            sessionId = UUID.randomUUID().toString().replace("-", "");
        }

        ChatResponse response = orchestratorService.chat(
                requestDTO.getUserId(), sessionId, requestDTO.getMessage());

        AiMessageDTO dto = new AiMessageDTO();
        dto.setId(response.getMessageId());
        dto.setRole("assistant");
        dto.setContent(response.getContent());
        return dto;
    }

    @Override
    public AiConversationDTO createConversation(Long userId, String title) {
        AiConversation conv = conversationService.create(userId, title);
        return toConversationDTO(conv);
    }

    @Override
    public List<AiConversationDTO> listConversations(Long userId) {
        List<AiConversation> conversations = conversationService.listByUserId(userId);
        return conversations.stream().map(this::toConversationDTO).collect(Collectors.toList());
    }

    @Override
    public AiConversationDTO getConversation(Long conversationId) {
        AiConversation conv = conversationService.getById(conversationId);
        if (conv == null) return null;

        AiConversationDTO dto = toConversationDTO(conv);
        List<AiMessage> messages = conversationService.listMessages(conversationId);
        dto.setMessages(messages.stream().map(this::toMessageDTO).collect(Collectors.toList()));
        return dto;
    }

    @Override
    public void deleteConversation(Long conversationId, Long userId) {
        conversationService.deleteConversation(conversationId, userId);
    }

    // ========== 知识库管理 ==========

    @Override
    public PageResult<AiKnowledgeBaseDTO> listKnowledge(int pageNum, int pageSize, String keyword) {
        List<AiKnowledgeBase> list = knowledgeService.listPage(pageNum, pageSize, keyword);
        int total = knowledgeService.countAll(keyword);

        List<AiKnowledgeBaseDTO> dtoList = list.stream().map(this::toKnowledgeDTO).collect(Collectors.toList());

        PageResult<AiKnowledgeBaseDTO> result = new PageResult<>();
        result.setList(dtoList);
        result.setTotal(total);
        return result;
    }

    @Override
    public AiKnowledgeBaseDTO addKnowledge(AiKnowledgeBaseDTO knowledgeDTO) {
        AiKnowledgeBase kb = toKnowledgeEntity(knowledgeDTO);
        if (kb.getStatus() == null) kb.setStatus(1);
        if (kb.getHitCount() == null) kb.setHitCount(0);
        knowledgeService.save(kb);
        return toKnowledgeDTO(kb);
    }

    @Override
    public void updateKnowledge(AiKnowledgeBaseDTO knowledgeDTO) {
        AiKnowledgeBase kb = toKnowledgeEntity(knowledgeDTO);
        knowledgeService.save(kb);
    }

    @Override
    public void deleteKnowledge(Long id) {
        knowledgeService.deleteById(id);
    }

    // ========== 智能推荐（Phase 4 实现） ==========

    @Override
    public List<IchItemDTO> getRecommendations(Long userId, int limit) {
        // Phase 4 实现
        return new ArrayList<>();
    }

    @Override
    public List<IchItemDTO> getHotItems(int limit) {
        // Phase 4 实现
        return new ArrayList<>();
    }

    // ========== 转换方法 ==========

    private AiConversationDTO toConversationDTO(AiConversation conv) {
        AiConversationDTO dto = new AiConversationDTO();
        dto.setId(conv.getId());
        dto.setUserId(conv.getUserId());
        dto.setSessionId(conv.getSessionId());
        dto.setTitle(conv.getTitle());
        dto.setCreateTime(conv.getCreateTime());
        dto.setUpdateTime(conv.getUpdateTime());
        return dto;
    }

    private AiMessageDTO toMessageDTO(AiMessage msg) {
        AiMessageDTO dto = new AiMessageDTO();
        dto.setId(msg.getId());
        dto.setRole(msg.getRole());
        dto.setContent(msg.getContent());
        dto.setCreateTime(msg.getCreateTime());
        return dto;
    }

    private AiKnowledgeBaseDTO toKnowledgeDTO(AiKnowledgeBase kb) {
        AiKnowledgeBaseDTO dto = new AiKnowledgeBaseDTO();
        dto.setId(kb.getId());
        dto.setQuestion(kb.getQuestion());
        dto.setAnswer(kb.getAnswer());
        dto.setHitCount(kb.getHitCount());
        return dto;
    }

    private AiKnowledgeBase toKnowledgeEntity(AiKnowledgeBaseDTO dto) {
        AiKnowledgeBase kb = new AiKnowledgeBase();
        kb.setId(dto.getId());
        kb.setQuestion(dto.getQuestion());
        kb.setAnswer(dto.getAnswer());
        kb.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        return kb;
    }
}
