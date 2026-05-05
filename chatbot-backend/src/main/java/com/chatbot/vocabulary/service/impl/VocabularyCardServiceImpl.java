package com.lingobot.vocabulary.service.impl;

import com.lingobot.chat.service.ToolLoopService;
import com.lingobot.common.exception.ChatException;
import com.lingobot.conversation.entity.Conversation;
import com.lingobot.conversation.repository.ConversationRepository;
import com.lingobot.learning.service.SystemPromptService;
import com.lingobot.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.llm.dto.openai.OpenAiTool;
import com.lingobot.mcp.service.McpService;
import com.lingobot.vocabulary.dto.CreateVocabularyCardRequest;
import com.lingobot.vocabulary.dto.VocabularyCardDTO;
import com.lingobot.vocabulary.dto.WordCardData;
import com.lingobot.vocabulary.entity.VocabularyCard;
import com.lingobot.vocabulary.repository.VocabularyCardRepository;
import com.lingobot.vocabulary.service.MeaningCheckService;
import com.lingobot.vocabulary.service.VocabularyCardService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 词汇卡服务实现类
 * 实现词汇卡的核心业务逻辑，包括AI生成单词、导航、状态管理等
 * 使用 Redis 缓存减少数据库访�? */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyCardServiceImpl implements VocabularyCardService {

    private final VocabularyCardRepository vocabularyCardRepository;
    private final ConversationRepository conversationRepository;
    private final ToolLoopService toolLoopService;
    private final McpService mcpService;
    private final SystemPromptService systemPromptService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final MeaningCheckService meaningCheckService;

    /** 默认使用的AI模型 */
    private static final String DEFAULT_MODEL = "qwen";
    /** 默认词汇标准（CEFR�?*/
    private static final String DEFAULT_VOCABULARY_CATEGORY = "cefr";
    
    /** Redis 缓存键前缀 - 单个单词�?*/
    private static final String CACHE_KEY_CARD = "vocabulary:card:";
    /** Redis 缓存键前缀 - 对话的所有有效卡片列�?*/
    private static final String CACHE_KEY_CARDS_LIST = "vocabulary:cards:";
    /** Redis 缓存键前缀 - 对话的有效卡片数�?*/
    private static final String CACHE_KEY_CARDS_COUNT = "vocabulary:count:";
    /** 缓存过期时间（小时） */
    private static final long CACHE_EXPIRE_HOURS = 1;
    
    /**
     * 获取单个单词卡的缓存�?     */
    private String getCardCacheKey(Long cardId) {
        return CACHE_KEY_CARD + cardId;
    }
    
    /**
     * 获取对话卡片列表的缓存键
     */
    private String getCardsListCacheKey(Long conversationId) {
        return CACHE_KEY_CARDS_LIST + conversationId;
    }
    
    /**
     * 获取对话卡片数量的缓存键
     */
    private String getCardsCountCacheKey(Long conversationId) {
        return CACHE_KEY_CARDS_COUNT + conversationId;
    }
    
    /**
     * 从缓存获取单个单词卡
     */
    private Optional<VocabularyCardDTO> getCardFromCache(Long cardId) {
        try {
            String key = getCardCacheKey(cardId);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isEmpty()) {
                VocabularyCardDTO dto = objectMapper.readValue(json, VocabularyCardDTO.class);
                log.debug("Cache hit: cardId={}", cardId);
                return Optional.of(dto);
            }
        } catch (Exception e) {
            log.warn("Failed to get card from cache: cardId={}", cardId, e);
        }
        log.debug("Cache miss: cardId={}", cardId);
        return Optional.empty();
    }
    
    /**
     * 从缓存获取对话的所有有效卡片列�?     */
    private Optional<List<VocabularyCardDTO>> getCardsListFromCache(Long conversationId) {
        try {
            String key = getCardsListCacheKey(conversationId);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isEmpty()) {
                List<VocabularyCardDTO> list = objectMapper.readValue(
                    json, new TypeReference<List<VocabularyCardDTO>>() {});
                log.debug("Cache hit: cards list for conversationId={}", conversationId);
                return Optional.of(list);
            }
        } catch (Exception e) {
            log.warn("Failed to get cards list from cache: conversationId={}", conversationId, e);
        }
        log.debug("Cache miss: cards list for conversationId={}", conversationId);
        return Optional.empty();
    }
    
    /**
     * 从缓存获取对话的有效卡片数量
     */
    private Optional<Long> getCardsCountFromCache(Long conversationId) {
        try {
            String key = getCardsCountCacheKey(conversationId);
            String countStr = stringRedisTemplate.opsForValue().get(key);
            if (countStr != null && !countStr.isEmpty()) {
                Long count = Long.parseLong(countStr);
                log.debug("Cache hit: cards count for conversationId={}, count={}", conversationId, count);
                return Optional.of(count);
            }
        } catch (Exception e) {
            log.warn("Failed to get cards count from cache: conversationId={}", conversationId, e);
        }
        log.debug("Cache miss: cards count for conversationId={}", conversationId);
        return Optional.empty();
    }
    
    /**
     * 缓存单个单词�?     */
    private void cacheCard(Long cardId, VocabularyCardDTO dto) {
        try {
            String key = getCardCacheKey(cardId);
            String json = objectMapper.writeValueAsString(dto);
            stringRedisTemplate.opsForValue().set(key, json, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("Cached card: cardId={}", cardId);
        } catch (Exception e) {
            log.warn("Failed to cache card: cardId={}", cardId, e);
        }
    }
    
    /**
     * 缓存对话的所有有效卡片列�?     */
    private void cacheCardsList(Long conversationId, List<VocabularyCardDTO> cards) {
        try {
            String key = getCardsListCacheKey(conversationId);
            String json = objectMapper.writeValueAsString(cards);
            stringRedisTemplate.opsForValue().set(key, json, CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("Cached cards list: conversationId={}, count={}", conversationId, cards.size());
        } catch (Exception e) {
            log.warn("Failed to cache cards list: conversationId={}", conversationId, e);
        }
    }
    
    /**
     * 缓存对话的有效卡片数�?     */
    private void cacheCardsCount(Long conversationId, long count) {
        try {
            String key = getCardsCountCacheKey(conversationId);
            stringRedisTemplate.opsForValue().set(key, String.valueOf(count), CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("Cached cards count: conversationId={}, count={}", conversationId, count);
        } catch (Exception e) {
            log.warn("Failed to cache cards count: conversationId={}", conversationId, e);
        }
    }
    
    /**
     * 清除单个单词卡的缓存
     */
    private void evictCardCache(Long cardId) {
        try {
            String key = getCardCacheKey(cardId);
            stringRedisTemplate.delete(key);
            log.debug("Evicted card cache: cardId={}", cardId);
        } catch (Exception e) {
            log.warn("Failed to evict card cache: cardId={}", cardId, e);
        }
    }
    
    /**
     * 清除对话相关的所有缓存（卡片列表和数量）
     */
    private void evictConversationCache(Long conversationId) {
        try {
            String listKey = getCardsListCacheKey(conversationId);
            String countKey = getCardsCountCacheKey(conversationId);
            stringRedisTemplate.delete(listKey);
            stringRedisTemplate.delete(countKey);
            log.debug("Evicted conversation cache: conversationId={}", conversationId);
        } catch (Exception e) {
            log.warn("Failed to evict conversation cache: conversationId={}", conversationId, e);
        }
    }
    
    /**
     * 清除单词卡及其所属对话的所有缓�?     * 用于更新/删除操作�?     */
    private void evictCardAndConversationCache(Long cardId, Long conversationId) {
        evictCardCache(cardId);
        evictConversationCache(conversationId);
    }

    @Override
    @Transactional
    public VocabularyCardDTO createCard(Long conversationId, CreateVocabularyCardRequest request) {
        Conversation conversation = getConversation(conversationId);

        // 计算下一个位置（基于有效卡片的最大位置）
        Integer nextPosition = vocabularyCardRepository.findMaxActivePositionByConversationId(conversationId)
                .map(pos -> pos + 1)
                .orElse(0);

        VocabularyCard card = VocabularyCard.builder()
                .conversation(conversation)
                .word(request.getWord())
                .phonetic(request.getPhonetic())
                .meaning(request.getMeaning())
                .example(request.getExample())
                .exampleTranslation(request.getExampleTranslation())
                .level(request.getLevel())
                .position(nextPosition)
                .isCompleted(false)
                .isRegenerated(false)
                .regenerationIndex(0)
                .build();

        if (request.getSynonyms() != null && !request.getSynonyms().isEmpty()) {
            card.setSynonyms(request.getSynonyms());
        }
        if (request.getAntonyms() != null && !request.getAntonyms().isEmpty()) {
            card.setAntonyms(request.getAntonyms());
        }

        VocabularyCard saved = vocabularyCardRepository.save(card);
        // 创建新卡片后清除该对话的缓存
        evictConversationCache(conversationId);
        log.info("Created new card and evicted cache: conversationId={}, cardId={}", conversationId, saved.getId());
        return toDTO(saved);
    }

    @Override
    public VocabularyCardDTO getCardById(Long cardId) {
        // 先尝试从缓存获取
        Optional<VocabularyCardDTO> cached = getCardFromCache(cardId);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // 缓存未命中，从数据库查询
        VocabularyCard card = vocabularyCardRepository.findById(cardId)
                .orElseThrow(() -> new ChatException("词汇卡不存在: " + cardId));
        VocabularyCardDTO dto = toDTOWithNavigation(card);
        
        // 写入缓存
        cacheCard(cardId, dto);
        return dto;
    }

    @Override
    public List<VocabularyCardDTO> getAllCards(Long conversationId) {
        // 先尝试从缓存获取
        Optional<List<VocabularyCardDTO>> cached = getCardsListFromCache(conversationId);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // 缓存未命中，从数据库查询
        // 只返回有效卡片（isRegenerated=false�?        List<VocabularyCard> cards = vocabularyCardRepository.findActiveCardsByConversationId(conversationId);
        List<VocabularyCardDTO> dtoList = cards.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        
        // 写入缓存
        cacheCardsList(conversationId, dtoList);
        return dtoList;
    }

    @Override
    @Transactional
    public VocabularyCardDTO getNextCard(Long conversationId, Integer currentPosition) {
        List<VocabularyCardDTO> cardDTOs = getAllCards(conversationId);
        
        if (cardDTOs.isEmpty()) {
            log.info("该对话没有词汇卡，自动生成第一�?);
            return generateNextCard(conversationId, null);
        }

        int currentIndex = -1;
        if (currentPosition != null) {
            for (int i = 0; i < cardDTOs.size(); i++) {
                if (cardDTOs.get(i).getPosition().equals(currentPosition)) {
                    currentIndex = i;
                    break;
                }
            }
        }

        int nextIndex = 0;
        if (currentIndex >= 0) {
            nextIndex = currentIndex + 1;
        }

        if (nextIndex >= cardDTOs.size()) {
            log.info("已经是最后一个单词了，自动生成下一�?);
            return generateNextCard(conversationId, null);
        }

        VocabularyCardDTO nextCard = cardDTOs.get(nextIndex);
        nextCard.setCurrentIndex(nextIndex);
        nextCard.setTotalCount(cardDTOs.size());
        nextCard.setHasPrev(nextIndex > 0);
        nextCard.setHasNext(nextIndex < cardDTOs.size() - 1);

        return nextCard;
    }

    @Override
    public VocabularyCardDTO getPrevCard(Long conversationId, Integer currentPosition) {
        List<VocabularyCardDTO> cardDTOs = getAllCards(conversationId);
        
        if (cardDTOs.isEmpty()) {
            throw new ChatException("该对话没有词汇卡");
        }

        int currentIndex = -1;
        if (currentPosition != null) {
            for (int i = 0; i < cardDTOs.size(); i++) {
                if (cardDTOs.get(i).getPosition().equals(currentPosition)) {
                    currentIndex = i;
                    break;
                }
            }
        }

        if (currentIndex < 0) {
            currentIndex = cardDTOs.size() - 1;
        }

        int prevIndex = currentIndex - 1;
        if (prevIndex < 0) {
            throw new ChatException("已经是第一个单词了");
        }

        VocabularyCardDTO prevCard = cardDTOs.get(prevIndex);
        prevCard.setCurrentIndex(prevIndex);
        prevCard.setTotalCount(cardDTOs.size());
        prevCard.setHasPrev(prevIndex > 0);
        prevCard.setHasNext(prevIndex < cardDTOs.size() - 1);

        return prevCard;
    }

    @Override
    public VocabularyCardDTO getCurrentCard(Long conversationId) {
        List<VocabularyCardDTO> cardDTOs = getAllCards(conversationId);
        
        if (cardDTOs.isEmpty()) {
            return null;
        }

        int targetIndex = -1;
        for (int i = 0; i < cardDTOs.size(); i++) {
            if (!cardDTOs.get(i).getIsCompleted()) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex < 0) {
            targetIndex = cardDTOs.size() - 1;
        }

        VocabularyCardDTO currentCard = cardDTOs.get(targetIndex);
        currentCard.setCurrentIndex(targetIndex);
        currentCard.setTotalCount(cardDTOs.size());
        currentCard.setHasPrev(targetIndex > 0);
        currentCard.setHasNext(targetIndex < cardDTOs.size() - 1);

        return currentCard;
    }

    @Override
    @Transactional
    public VocabularyCardDTO updateUserMeaning(Long cardId, String userMeaning) {
        VocabularyCard card = vocabularyCardRepository.findById(cardId)
                .orElseThrow(() -> new ChatException("词汇卡不存在: " + cardId));

        card.setUserMeaningGuess(userMeaning);
        card.setMeaningCheckCompleted(false);
        card.setMeaningIsCorrect(null);
        card.setMeaningCheckResult(null);
        VocabularyCard saved = vocabularyCardRepository.save(card);

        Long conversationId = card.getConversation() != null ? card.getConversation().getId() : null;
        if (conversationId != null && userMeaning != null && !userMeaning.trim().isEmpty()) {
            log.info("Triggering async meaning check for cardId={}, conversationId={}", cardId, conversationId);
            meaningCheckService.checkUserMeaningAsync(conversationId, cardId, userMeaning);
        }

        evictCardAndConversationCache(cardId, conversationId);
        log.info("Updated user meaning and evicted cache: cardId={}", cardId);
        return toDTOWithNavigation(saved);
    }

    @Override
    @Transactional
    public VocabularyCardDTO updateUserSentence(Long cardId, String userSentence) {
        VocabularyCard card = vocabularyCardRepository.findById(cardId)
                .orElseThrow(() -> new ChatException("词汇卡不存在: " + cardId));
        
        card.setUserSentence(userSentence);
        VocabularyCard saved = vocabularyCardRepository.save(card);
        // 更新后清除缓�?        evictCardAndConversationCache(cardId, card.getConversation().getId());
        log.info("Updated user sentence and evicted cache: cardId={}", cardId);
        return toDTOWithNavigation(saved);
    }

    @Override
    @Transactional
    public VocabularyCardDTO updateAIFeedback(Long cardId, String feedback) {
        VocabularyCard card = vocabularyCardRepository.findById(cardId)
                .orElseThrow(() -> new ChatException("词汇卡不存在: " + cardId));
        
        card.setAiFeedback(feedback);
        VocabularyCard saved = vocabularyCardRepository.save(card);
        // 更新后清除缓�?        evictCardAndConversationCache(cardId, card.getConversation().getId());
        log.info("Updated AI feedback and evicted cache: cardId={}", cardId);
        return toDTOWithNavigation(saved);
    }

    @Override
    @Transactional
    public VocabularyCardDTO markAsCompleted(Long cardId) {
        VocabularyCard card = vocabularyCardRepository.findById(cardId)
                .orElseThrow(() -> new ChatException("词汇卡不存在: " + cardId));
        
        card.setIsCompleted(true);
        VocabularyCard saved = vocabularyCardRepository.save(card);
        // 更新后清除缓�?        evictCardAndConversationCache(cardId, card.getConversation().getId());
        log.info("Marked card as completed and evicted cache: cardId={}", cardId);
        return toDTOWithNavigation(saved);
    }

    @Override
    @Transactional
    public VocabularyCardDTO generateNextCard(Long conversationId, String level) {
        Conversation conversation = getConversation(conversationId);

        // 计算新词汇卡的位置（基于有效卡片的最大位置）
        Integer nextPosition = vocabularyCardRepository.findMaxActivePositionByConversationId(conversationId)
                .map(pos -> pos + 1)
                .orElse(0);

        // 调用AI生成新单词（可能需要较长时间）
        WordCardData generated = generateRandomWord(conversationId, level, "next_word");

        // 双重检查：AI调用完成后，再次验证对话是否仍然存在
        // 防止在AI调用期间对话被删除导致外键约束错�?        conversation = getConversation(conversationId);

        VocabularyCard card = VocabularyCard.builder()
                .conversation(conversation)
                .word(generated.getWord())
                .phonetic(generated.getPhonetic())
                .meaning(generated.getMeaning())
                .example(generated.getExample())
                .exampleTranslation(generated.getExampleTranslation())
                .level(generated.getLevel())
                .position(nextPosition)
                .isCompleted(false)
                .isRegenerated(false)
                .regenerationIndex(0)
                .build();

        if (generated.getSynonyms() != null && !generated.getSynonyms().isEmpty()) {
            card.setSynonyms(generated.getSynonyms());
        }
        if (generated.getAntonyms() != null && !generated.getAntonyms().isEmpty()) {
            card.setAntonyms(generated.getAntonyms());
        }

        VocabularyCard saved = vocabularyCardRepository.save(card);
        // 生成新卡片后清除该对话的缓存
        evictConversationCache(conversationId);
        log.info("Generated next card and evicted cache: conversationId={}, cardId={}", conversationId, saved.getId());
        return toDTOWithNavigation(saved);
    }

    @Override
    @Transactional
    public VocabularyCardDTO regenerateCard(Long conversationId, String level) {
        Conversation conversation = getConversation(conversationId);

        // 获取所有有效卡�?        List<VocabularyCard> activeCards = vocabularyCardRepository.findActiveCardsByConversationId(conversationId);
        
        if (activeCards.isEmpty()) {
            // 没有卡片，直接生成第一�?            log.info("该对话没有词汇卡，直接生成第一�?);
            return generateNextCard(conversationId, level);
        }

        // 检查当前是否是最后一张卡�?        // 找到当前应该重新生成的卡片：第一个未完成的，或者最后一�?        VocabularyCard currentCard = null;
        for (VocabularyCard card : activeCards) {
            if (!card.getIsCompleted()) {
                currentCard = card;
                break;
            }
        }
        if (currentCard == null) {
            currentCard = activeCards.get(activeCards.size() - 1);
        }

        // 检查是否是最后一张卡�?        Integer maxPosition = vocabularyCardRepository.findMaxActivePositionByConversationId(conversationId)
                .orElse(0);
        
        if (!currentCard.getPosition().equals(maxPosition)) {
            throw new ChatException("只能在最后一张卡片时重新生成");
        }

        Integer replacePosition = currentCard.getPosition();
        Integer currentRegenerationIndex = currentCard.getRegenerationIndex();
        
        // 标记旧卡片为已重新生成（不删除，数据库保留）
        log.info("标记词汇卡为已重新生�? id={}, word={}, position={}, regenerationIndex={}", 
                currentCard.getId(), currentCard.getWord(), replacePosition, currentRegenerationIndex);
        currentCard.setIsRegenerated(true);
        vocabularyCardRepository.save(currentCard);

        // 生成新词汇卡（使用相同的position，regenerationIndex递增�?        WordCardData generated = generateRandomWord(conversationId, level, "regenerate");

        // 双重检查：AI调用完成后，再次验证对话是否仍然存在
        // 防止在AI调用期间对话被删除导致外键约束错�?        conversation = getConversation(conversationId);

        VocabularyCard newCard = VocabularyCard.builder()
                .conversation(conversation)
                .word(generated.getWord())
                .phonetic(generated.getPhonetic())
                .meaning(generated.getMeaning())
                .example(generated.getExample())
                .exampleTranslation(generated.getExampleTranslation())
                .level(generated.getLevel())
                .position(replacePosition)
                .isCompleted(false)
                .isRegenerated(false)
                .regenerationIndex(currentRegenerationIndex + 1)
                .build();

        if (generated.getSynonyms() != null && !generated.getSynonyms().isEmpty()) {
            newCard.setSynonyms(generated.getSynonyms());
        }
        if (generated.getAntonyms() != null && !generated.getAntonyms().isEmpty()) {
            newCard.setAntonyms(generated.getAntonyms());
        }

        VocabularyCard saved = vocabularyCardRepository.save(newCard);
        log.info("重新生成词汇卡成�? id={}, word={}, position={}, regenerationIndex={}", 
                saved.getId(), saved.getWord(), replacePosition, saved.getRegenerationIndex());
        // 重新生成卡片后清除缓存（旧卡片和新卡片的缓存都清除）
        evictCardCache(currentCard.getId());
        evictConversationCache(conversationId);
        log.info("Regenerated card and evicted cache: conversationId={}, oldCardId={}, newCardId={}", 
                conversationId, currentCard.getId(), saved.getId());
        return toDTOWithNavigation(saved);
    }

    @Override
    @Transactional
    public void deleteAllCards(Long conversationId) {
        vocabularyCardRepository.deleteByConversationId(conversationId);
        // 删除所有卡片后清除该对话的缓存
        evictConversationCache(conversationId);
        log.info("Deleted all cards and evicted cache: conversationId={}", conversationId);
    }

    @Override
    public long getCardCount(Long conversationId) {
        // 先尝试从缓存获取
        Optional<Long> cached = getCardsCountFromCache(conversationId);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // 缓存未命中，从数据库查询
        // 返回有效卡片的数�?        long count = vocabularyCardRepository.countActiveCardsByConversationId(conversationId);
        
        // 写入缓存
        cacheCardsCount(conversationId, count);
        return count;
    }

    /**
     * 获取对话实体
     * @param conversationId 对话ID
     * @return 对话实体
     */
    private Conversation getConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ChatException("对话不存�? " + conversationId));
    }

    /**
     * 通过AI Agent生成随机单词
     * 核心流程：构建System Prompt -> 调用AI -> 解析工具返回结果
     * @param conversationId 对话ID
     * @param level 难度级别
     * @param intent 意图（next_word/regenerate�?     * @return 生成的单词卡片数�?     */
    private WordCardData generateRandomWord(Long conversationId, String level, String intent) {
        try {
            log.info("=== 开始生成词汇卡（AI Agent 模式�?===");
            log.info("conversationId: {}, level: {}, intent: {}", conversationId, level, intent);

            String vocabularyCategory = DEFAULT_VOCABULARY_CATEGORY;
            String vocabularyDifficulty = level != null ? level.toLowerCase() : "b2";

            // 获取System Prompt并追加历史单词记录（避免重复�?            String systemPrompt = systemPromptService.getSystemPrompt(
                    "vocabulary", vocabularyCategory, vocabularyDifficulty);
            log.info("System prompt 已生成，长度: {}", systemPrompt != null ? systemPrompt.length() : 0);

            String vocabularyHistory = buildVocabularyHistoryForPrompt(conversationId);
            if (vocabularyHistory != null && !vocabularyHistory.isEmpty()) {
                systemPrompt = systemPrompt + vocabularyHistory;
                log.info("已追加词汇历史到 System Prompt，conversationId: {}", conversationId);
            }

            // 构建消息列表
            String userMessage = String.format("[intent:%s]", intent != null ? intent : "next_word");
            log.info("发送给 AI 的用户消�? {}", userMessage);

            List<OpenAiChatMessage> messages = new ArrayList<>();
            messages.add(OpenAiChatMessage.createTextMessage("system", systemPrompt));
            messages.add(OpenAiChatMessage.createTextMessage("user", userMessage));

            // 获取vocabulary模式可用的工�?            List<OpenAiTool> tools = mcpService.getOpenAiToolsForMode("vocabulary");
            log.info("获取�?{} �?vocabulary 模式的工�?, tools != null ? tools.size() : 0);

            if (tools == null || tools.isEmpty()) {
                log.warn("没有可用�?vocabulary 工具，使用默认单�?);
                return getDefaultWord();
            }

            // 调用AI Agent执行工具调用
            log.info("调用 AI Agent 执行工具调用...");
            ToolLoopService.ToolLoopResult result = toolLoopService.executeOneTimeToolCall(
                    conversationId, messages, tools, DEFAULT_MODEL);

            log.info("AI Agent 执行结果: hasToolCalls={}, hasTextResponse={}",
                    result.hasToolCalls(), result.hasTextResponse());

            // 解析工具返回结果
            if (result.hasToolCalls() && result.getToolResultText() != null) {
                String toolResultText = result.getToolResultText();
                log.info("工具返回结果: {}", toolResultText != null && toolResultText.length() > 100 
                        ? toolResultText.substring(0, 100) + "..." : toolResultText);

                return parseWordCardDataFromToolResult(toolResultText, vocabularyDifficulty);
            } else if (result.hasTextResponse()) {
                log.warn("AI 返回了文本响应而非工具调用，文�? {}", result.getTextResponse());
                return getDefaultWord();
            } else {
                log.warn("AI 没有返回有效结果，使用默认单�?);
                return getDefaultWord();
            }

        } catch (Exception e) {
            log.error("生成词汇卡时发生错误", e);
            return getDefaultWord();
        }
    }

    /**
     * 解析AI工具返回的JSON结果为WordCardData
     * @param toolResultText 工具返回的JSON字符�?     * @param difficulty 难度级别
     * @return 解析后的单词卡片数据
     */
    private WordCardData parseWordCardDataFromToolResult(String toolResultText, String difficulty) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    toolResultText, new TypeReference<Map<String, Object>>() {});

            String word = (String) parsed.get("word");
            if (word == null || word.trim().isEmpty()) {
                log.warn("工具返回结果�?word 为空，使用默认单�?);
                return getDefaultWord();
            }

            List<String> synonyms = parseList(parsed.get("synonyms"));
            List<String> antonyms = parseList(parsed.get("antonyms"));

            String level = (String) parsed.get("vocabularyDifficulty");
            if (level == null || level.isEmpty()) {
                level = difficulty != null ? difficulty.toUpperCase() : "B2";
            } else {
                level = level.toUpperCase();
            }

            log.info("成功解析词汇卡数�? word={}, phonetic={}, meaning={}, level={}",
                    word, parsed.get("phonetic"), parsed.get("meaning"), level);

            return WordCardData.builder()
                    .word(word)
                    .phonetic((String) parsed.get("phonetic"))
                    .meaning((String) parsed.get("meaning"))
                    .example((String) parsed.get("example"))
                    .exampleTranslation((String) parsed.get("exampleTranslation"))
                    .synonyms(synonyms)
                    .antonyms(antonyms)
                    .level(level)
                    .build();

        } catch (Exception e) {
            log.error("解析工具返回结果时发生错�? {}", toolResultText, e);
            return getDefaultWord();
        }
    }

    /**
     * 安全解析列表对象
     */
    @SuppressWarnings("unchecked")
    private List<String> parseList(Object obj) {
        if (obj == null) {
            return new ArrayList<>();
        }
        if (obj instanceof List<?>) {
            return (List<String>) obj;
        }
        return new ArrayList<>();
    }

    /**
     * 构建历史单词记录，用于注入到System Prompt中避免AI生成重复单词
     * 包含重新生成的历史，让AI知道用户对哪些单词不满意
     * @param conversationId 对话ID
     * @return 格式化的历史记录字符�?     */
    private String buildVocabularyHistoryForPrompt(Long conversationId) {
        // 获取所有卡片（包括已重新生成的）用于构建历�?        List<VocabularyCard> allCards = vocabularyCardRepository.findByConversationIdOrderByPositionAsc(conversationId);
        if (allCards == null || allCards.isEmpty()) {
            return "";
        }
        
        // 按position分组，构建每个位置的历史
        Map<Integer, List<VocabularyCard>> cardsByPosition = allCards.stream()
                .collect(Collectors.groupingBy(VocabularyCard::getPosition));
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 历史单词卡学习记录\n");
        sb.append("用户之前已经学习了以下单词，请在生成新单词时确保不重复：\n\n");
        
        // 按position顺序遍历
        List<Integer> positions = cardsByPosition.keySet().stream()
                .sorted()
                .collect(Collectors.toList());
        
        for (Integer position : positions) {
            List<VocabularyCard> cardsAtPosition = cardsByPosition.get(position).stream()
                    .sorted((a, b) -> a.getRegenerationIndex().compareTo(b.getRegenerationIndex()))
                    .collect(Collectors.toList());
            
            sb.append("### 位置 ").append(position + 1).append("\n");
            
            // 显示每个重新生成的版�?            for (VocabularyCard card : cardsAtPosition) {
                if (card.getIsRegenerated()) {
                    // 已重新生成的单词，标记为用户不满�?                    sb.append("- [重新生成过的单词（用户不满意）] ").append(card.getWord() != null ? card.getWord() : "");
                    if (card.getRegenerationIndex() > 0) {
                        sb.append(" (�?).append(card.getRegenerationIndex()).append("�?");
                    }
                    sb.append("\n");
                } else {
                    // 当前有效的单�?                    sb.append("- [当前单词] ").append(card.getWord() != null ? card.getWord() : "");
                    if (card.getRegenerationIndex() > 0) {
                        sb.append(" (�?).append(card.getRegenerationIndex()).append("�?");
                    }
                    sb.append("\n");
                }
                if (card.getPhonetic() != null && !card.getPhonetic().isEmpty()) {
                    sb.append("  音标: ").append(card.getPhonetic()).append("\n");
                }
                if (card.getMeaning() != null && !card.getMeaning().isEmpty()) {
                    sb.append("  释义: ").append(card.getMeaning()).append("\n");
                }
            }
            sb.append("\n");
        }
        
        log.info("已为 conversationId {} 构建历史信息，包�?{} 个位置的单词记录", conversationId, positions.size());
        return sb.toString();
    }

    /**
     * 获取默认单词（当AI生成失败时的兜底方案�?     */
    private WordCardData getDefaultWord() {
        return WordCardData.builder()
                .word("hello")
                .phonetic("həˈloʊ")
                .meaning("你好")
                .example("Hello, how are you?")
                .exampleTranslation("你好，你好吗�?)
                .synonyms(List.of("hi", "hey"))
                .antonyms(List.of("goodbye"))
                .level("A1")
                .build();
    }

    /**
     * 获取某个位置的重新生成历史单词列�?     */
    private List<String> getRegeneratedWords(Long conversationId, Integer position) {
        List<VocabularyCard> cardsAtPosition = vocabularyCardRepository
                .findByConversationIdAndPositionOrderByRegenerationIndexAsc(conversationId, position);
        
        return cardsAtPosition.stream()
                .filter(VocabularyCard::getIsRegenerated)
                .map(VocabularyCard::getWord)
                .collect(Collectors.toList());
    }

    /**
     * 将实体转换为DTO（基础转换�?     */
    private VocabularyCardDTO toDTO(VocabularyCard card) {
        // 获取该位置的重新生成历史
        List<String> regeneratedWords = getRegeneratedWords(
                card.getConversation().getId(), card.getPosition());
        
        return VocabularyCardDTO.builder()
                .id(card.getId())
                .conversationId(card.getConversation().getId())
                .word(card.getWord())
                .phonetic(card.getPhonetic())
                .meaning(card.getMeaning())
                .example(card.getExample())
                .exampleTranslation(card.getExampleTranslation())
                .synonyms(card.getSynonyms())
                .antonyms(card.getAntonyms())
                .level(card.getLevel())
                .position(card.getPosition())
                .userMeaningGuess(card.getUserMeaningGuess())
                .meaningCheckResult(card.getMeaningCheckResult())
                .meaningIsCorrect(card.getMeaningIsCorrect())
                .meaningCheckCompleted(card.getMeaningCheckCompleted())
                .userSentence(card.getUserSentence())
                .aiFeedback(card.getAiFeedback())
                .isCompleted(card.getIsCompleted())
                .isRegenerated(card.getIsRegenerated())
                .regenerationIndex(card.getRegenerationIndex())
                .regeneratedWords(regeneratedWords)
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .build();
    }

    /**
     * 将实体转换为DTO（包含导航信息：上一�?下一个是否存在）
     * 使用 getAllCards 来获取卡片列表，以便利用缓存
     */
    private VocabularyCardDTO toDTOWithNavigation(VocabularyCard card) {
        VocabularyCardDTO dto = toDTO(card);
        
        Long conversationId = card.getConversation().getId();
        // 使用 getAllCards 获取卡片列表（会利用缓存�?        List<VocabularyCardDTO> cardDTOs = getAllCards(conversationId);
        
        // 找到当前卡片在列表中的索�?        int currentIndex = -1;
        for (int i = 0; i < cardDTOs.size(); i++) {
            if (cardDTOs.get(i).getId().equals(card.getId())) {
                currentIndex = i;
                break;
            }
        }

        // 设置导航信息
        dto.setCurrentIndex(currentIndex);
        dto.setTotalCount(cardDTOs.size());
        dto.setHasPrev(currentIndex > 0);
        dto.setHasNext(currentIndex < cardDTOs.size() - 1);

        return dto;
    }
}
