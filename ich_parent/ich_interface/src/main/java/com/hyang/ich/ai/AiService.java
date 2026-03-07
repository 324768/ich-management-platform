package com.hyang.ich.ai;

import com.hyang.ich.ai.dto.AiChatRequestDTO;
import com.hyang.ich.ai.dto.AiConversationDTO;
import com.hyang.ich.ai.dto.AiKnowledgeBaseDTO;
import com.hyang.ich.ai.dto.AiMessageDTO;
import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.dto.IchItemDTO;

import java.util.List;

public interface AiService {

    // ========== 智能问答 ==========

    /** 发送消息并获取AI回复 */
    AiMessageDTO chat(AiChatRequestDTO requestDTO);

    /** 创建新对话 */
    AiConversationDTO createConversation(Long userId, String title);

    /** 获取用户对话列表 */
    List<AiConversationDTO> listConversations(Long userId);

    /** 获取对话详情（含消息） */
    AiConversationDTO getConversation(Long conversationId);

    /** 删除对话 */
    void deleteConversation(Long conversationId, Long userId);

    // ========== 知识库管理（管理端） ==========

    /** 分页查询知识库 */
    PageResult<AiKnowledgeBaseDTO> listKnowledge(int pageNum, int pageSize, String keyword);

    /** 新增知识 */
    AiKnowledgeBaseDTO addKnowledge(AiKnowledgeBaseDTO knowledgeDTO);

    /** 修改知识 */
    void updateKnowledge(AiKnowledgeBaseDTO knowledgeDTO);

    /** 删除知识 */
    void deleteKnowledge(Long id);

    // ========== 智能推荐 ==========

    /** 获取推荐的非遗项目 */
    List<IchItemDTO> getRecommendations(Long userId, int limit);

    /** 获取热门非遗项目 */
    List<IchItemDTO> getHotItems(int limit);
}
