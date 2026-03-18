package com.hyang.ich.omnitrix.service;

import com.hyang.ich.omnitrix.entity.AiKnowledgeBase;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import com.hyang.ich.omnitrix.mapper.AiKnowledgeBaseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * KnowledgeService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class KnowledgeServiceTest {

    @Mock
    private AiKnowledgeBaseMapper knowledgeBaseMapper;
    
    @Mock
    private LlmClient llmClient;
    
    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        knowledgeService = new KnowledgeService(knowledgeBaseMapper, llmClient);
    }

    @Test
    void testSearch_withKeyword_shouldReturnResults() {
        // Given
        String keyword = "非遗";
        List<AiKnowledgeBase> expectedResults = Arrays.asList(
            createKnowledgeBase(1L, "什么是非物质文化遗产？", "非物质文化遗产是指...")
        );
        when(knowledgeBaseMapper.searchByKeyword(keyword)).thenReturn(expectedResults);

        // When
        List<AiKnowledgeBase> results = knowledgeService.search(keyword);

        // Then
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("什么是非物质文化遗产？", results.get(0).getQuestion());
        verify(knowledgeBaseMapper).searchByKeyword(keyword);
    }

    @Test
    void testSearch_withEmptyKeyword_shouldReturnEmptyList() {
        // Given
        String keyword = "";
        
        // When
        List<AiKnowledgeBase> results = knowledgeService.search(keyword);
        
        // Then
        assertNotNull(results);
        assertTrue(results.isEmpty());
        verify(knowledgeBaseMapper, never()).searchByKeyword(anyString());
    }

    @Test
    void testHasMatch_withMatchingKeyword_shouldReturnTrue() {
        // Given
        String keyword = "非遗";
        List<AiKnowledgeBase> results = Arrays.asList(
            createKnowledgeBase(1L, "问题", "答案")
        );
        when(knowledgeBaseMapper.searchByKeyword(keyword)).thenReturn(results);

        // When
        boolean hasMatch = knowledgeService.hasMatch(keyword);

        // Then
        assertTrue(hasMatch);
    }

    @Test
    void testHasMatch_withNoMatchingKeyword_shouldReturnFalse() {
        // Given
        String keyword = "不存在的关键词";
        when(knowledgeBaseMapper.searchByKeyword(keyword)).thenReturn(Arrays.asList());

        // When
        boolean hasMatch = knowledgeService.hasMatch(keyword);

        // Then
        assertFalse(hasMatch);
    }

    @Test
    void testSearchAndRerank_withMultipleCandidates_shouldReturnReranked() throws Exception {
        // Given
        String userQuery = "如何传承非遗？";
        List<AiKnowledgeBase> candidates = Arrays.asList(
            createKnowledgeBase(1L, "问题1", "答案1"),
            createKnowledgeBase(2L, "问题2", "答案2")
        );
        
        when(knowledgeBaseMapper.searchByKeyword(userQuery)).thenReturn(candidates);
        when(knowledgeBaseMapper.searchByTokens(any(), anyInt())).thenReturn(candidates);
        
        // Mock LLM rerank response
        LlmResponse llmResponse = new LlmResponse();
        llmResponse.setContent("1");
        when(llmClient.chatAuxiliary(anyString(), any(), anyString())).thenReturn(llmResponse);

        // When
        List<AiKnowledgeBase> results = knowledgeService.searchAndRerank(userQuery);

        // Then
        assertNotNull(results);
        assertFalse(results.isEmpty());
    }

    @Test
    void testIncrementHitCount_shouldCallMapper() {
        // Given
        Long id = 1L;
        
        // When
        knowledgeService.incrementHitCount(id);
        
        // Then
        verify(knowledgeBaseMapper).incrementHitCount(id);
    }

    private AiKnowledgeBase createKnowledgeBase(Long id, String question, String answer) {
        AiKnowledgeBase kb = new AiKnowledgeBase();
        kb.setId(id);
        kb.setQuestion(question);
        kb.setAnswer(answer);
        return kb;
    }
}
