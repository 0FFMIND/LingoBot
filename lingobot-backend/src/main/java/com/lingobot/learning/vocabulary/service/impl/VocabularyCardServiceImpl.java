package com.lingobot.learning.vocabulary.service.impl;

import com.lingobot.learning.chat.service.ToolLoopService;
import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.repository.ConversationRepository;
import com.lingobot.core.user.preference.dto.UserPreferenceDTO;
import com.lingobot.core.user.preference.service.UserPreferenceService;
import com.lingobot.learning.mode.service.SystemPromptService;
import com.lingobot.learning.llm.dto.openai.OpenAiChatMessage;
import com.lingobot.learning.llm.dto.openai.OpenAiTool;
import com.lingobot.learning.llm.tool.service.McpService;
import com.lingobot.learning.vocabulary.dto.CreateVocabularyCardRequest;
import com.lingobot.learning.vocabulary.dto.VocabularyCardDTO;
import com.lingobot.learning.vocabulary.dto.WordCardData;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.entity.VocabularyWord;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import com.lingobot.learning.vocabulary.service.MeaningCheckService;
import com.lingobot.learning.vocabulary.service.SentenceAnalysisService;
import com.lingobot.learning.vocabulary.service.UserVocabularyService;
import com.lingobot.learning.vocabulary.service.VocabularyCardService;
import com.lingobot.learning.vocabulary.service.VocabularyWordService;
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
 * 词汇卡服务实现类。
 *
 * 实现词汇卡的核心业务逻辑，包括 AI 生成单词、导航、状态管理等，
 * 使用 Redis 缓存减少数据库访问。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VocabularyCardServiceImpl implements VocabularyCardService {

    private final VocabularyCardRepository vocabularyCardRepository;
    private final ConversationRepository conversationRepository;
    private final ToolLoopService toolLoopService;
    private final McpService mcpService;
    private final SystemPromptService systemPromptService;
    private final UserPreferenceService userPreferenceService;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final MeaningCheckService meaningCheckService;
    private final SentenceAnalysisService sentenceAnalysisService;
    private final VocabularyWordService vocabularyWordService;
    private final UserVocabularyService userVocabularyService;

    /** 默认使用的AI模型 */
    private static final String DEFAULT_MODEL = "qwen";
    /** 默认词汇标准（CEFR）*/
    private static final String DEFAULT_VOCABULARY_CATEGORY = "cefr";
    
    /** Redis 缓存键前缀 - 单个单词卡*/
    private static final String CACHE_KEY_CARD = "vocabulary:card:";
    /** Redis 缓存键前缀 - 对话的所有有效卡片列表*/
    private static final String CACHE_KEY_CARDS_LIST = "vocabulary:cards:";
    /** Redis 缓存键前缀 - 对话的有效卡片数量*/
    private static final String CACHE_KEY_CARDS_COUNT = "vocabulary:count:";
    /** 缓存过期时间（小时） */
    private static final long CACHE_EXPIRE_HOURS = 1;
    
    /**
     * 获取单个单词卡的缓存键     */
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
                return Optional.of(dto);
            }
        } catch (Exception e) {
            log.warn("Failed to get card from cache: cardId={}", cardId, e);
        }
        log.debug("Cache miss: cardId={}", cardId);
        return Optional.empty();
    }
    
    /**
     * 从缓存获取对话的所有有效卡片列表     */
    private Optional<List<VocabularyCardDTO>> getCardsListFromCache(Long conversationId) {
        try {
            String key = getCardsListCacheKey(conversationId);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null && !json.isEmpty()) {
                List<VocabularyCardDTO> list = objectMapper.readValue(
                    json, new TypeReference<List<VocabularyCardDTO>>() {});
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
                return Optional.of(count);
            }
        } catch (Exception e) {
            log.warn("Failed to get cards count from cache: conversationId={}", conversationId, e);
        }
        log.debug("Cache miss: cards count for conversationId={}", conversationId);
        return Optional.empty();
    }
    
    /**
     * 缓存单个单词卡     */
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
     * 缓存对话的所有有效卡片列表     */
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
     * 缓存对话的有效卡片数量     */
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
     * 清除单词卡及其所属对话的所有缓存
     * 用于更新/删除操作
     */
    private void evictCardAndConversationCache(Long cardId, Long conversationId) {
        evictCardCache(cardId);
        evictConversationCache(conversationId);
    }

    @Override
    @Transactional
    public VocabularyCardDTO createCard(Long conversationId, CreateVocabularyCardRequest request) {
        Conversation conversation = getConversation(conversationId);

        Integer nextPosition = vocabularyCardRepository.findMaxActivePositionByConversationId(conversationId)
                .map(pos -> pos + 1)
                .orElse(0);

        ensureConversationStillExists(conversationId);
        conversation = getConversation(conversationId);

        VocabularyWord vocabularyWord = vocabularyWordService.findOrCreateWord(request.getWord());
        log.info("Using VocabularyWord: id={}, normalizedWord={}", vocabularyWord.getId(), vocabularyWord.getNormalizedWord());

        VocabularyCard card = VocabularyCard.builder()
                .conversation(conversation)
                .vocabularyWordId(vocabularyWord.getId())
                .word(request.getWord())
                .phonetic(request.getPhonetic())
                .partOfSpeech(request.getPartOfSpeech())
                .meaning(request.getMeaning())
                .example(request.getExample())
                .exampleTranslation(request.getExampleTranslation())
                .chineseSentenceForTranslation(request.getExampleTranslation())
                .category(request.getCategory())
                .difficulty(request.getDifficulty())
                .position(nextPosition)
                .isCompleted(false)
                .isRegenerated(false)
                .regenerationIndex(0)
                .build();

        if (request.getSynonyms() != null && !request.getSynonyms().isEmpty()) {
            card.setSynonyms(request.getSynonyms());
        }

        VocabularyCard saved = vocabularyCardRepository.save(card);
        if (conversation.getUser() != null && conversation.getUser().getId() != null) {
            userVocabularyService.upsertProgress(conversation.getUser().getId(), saved);
            log.info("Updated UserVocabulary for userId={}, vocabularyWordId={}",
                    conversation.getUser().getId(), vocabularyWord.getId());
        }
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
                .orElseThrow(() -> ChatException.badRequest("词汇卡不存在: " + cardId));
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
        // 只返回有效卡片（isRegenerated=false）
                List<VocabularyCard> cards = vocabularyCardRepository.findActiveCardsByConversationId(conversationId);
        List<VocabularyCardDTO> dtoList = cards.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        
        // 写入缓存
        cacheCardsList(conversationId, dtoList);
        return dtoList;
    }

    @Override
    @Transactional
    public VocabularyCardDTO getNextCard(Long conversationId, Integer currentPosition, String category, String difficulty) {
        List<VocabularyCardDTO> cardDTOs = getAllCards(conversationId);
        
        if (cardDTOs.isEmpty()) {
            log.info("该对话没有词汇卡，自动生成第一张");
            return generateNextCard(conversationId, category, difficulty);
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
            log.info("已经是最后一个单词了，自动生成下一张");
            return generateNextCard(conversationId, category, difficulty);
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
            throw ChatException.badRequest("该对话没有词汇卡");
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
            throw ChatException.badRequest("已经是第一个单词了");
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
                .orElseThrow(() -> ChatException.badRequest("词汇卡不存在: " + cardId));

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
    public VocabularyCardDTO updateUserEnglishSentence(Long cardId, String userEnglishSentence) {
        VocabularyCard card = vocabularyCardRepository.findById(cardId)
                .orElseThrow(() -> ChatException.badRequest("词汇卡不存在: " + cardId));

        card.setUserEnglishSentence(userEnglishSentence);
        card.setSentenceAnalysisCompleted(false);
        card.setSentenceHasNewWord(null);
        card.setSentenceMeaningMatches(null);
        card.setSentenceAnalysis(null);
        VocabularyCard saved = vocabularyCardRepository.save(card);
        evictCardAndConversationCache(cardId, card.getConversation().getId());
        log.info("Updated user english sentence and evicted cache: cardId={}", cardId);
        return toDTOWithNavigation(saved);
    }

    @Override
    public void analyzeUserSentenceAsync(Long cardId) {
        VocabularyCard card = vocabularyCardRepository.findById(cardId)
                .orElseThrow(() -> ChatException.badRequest("词汇卡不存在: " + cardId));

        if (card.getUserEnglishSentence() == null || card.getUserEnglishSentence().isBlank()) {
            log.warn("No userEnglishSentence to analyze for cardId={}", cardId);
            return;
        }

        log.info("Triggering async sentence analysis for cardId={}", cardId);
        sentenceAnalysisService.analyzeUserSentenceAsync(cardId);
    }

    @Override
    @Transactional
    public VocabularyCardDTO updateSentenceAnalysis(Long cardId, String analysis, Boolean hasNewWord, Boolean meaningMatches) {
        VocabularyCard card = vocabularyCardRepository.findById(cardId)
                .orElseThrow(() -> ChatException.badRequest("词汇卡不存在: " + cardId));

        card.setSentenceAnalysis(analysis);
        card.setSentenceHasNewWord(hasNewWord);
        card.setSentenceMeaningMatches(meaningMatches);
        card.setSentenceAnalysisCompleted(true);
        VocabularyCard saved = vocabularyCardRepository.save(card);
        evictCardAndConversationCache(cardId, card.getConversation().getId());
        log.info("Updated sentence analysis and evicted cache: cardId={}", cardId);
        return toDTOWithNavigation(saved);
    }

    @Override
    @Transactional
    public VocabularyCardDTO markAsCompleted(Long cardId) {
        VocabularyCard card = vocabularyCardRepository.findById(cardId)
                .orElseThrow(() -> ChatException.badRequest("词汇卡不存在: " + cardId));
        
        card.setIsCompleted(true);
        VocabularyCard saved = vocabularyCardRepository.save(card);
        if (card.getVocabularyWordId() != null
                && card.getConversation() != null
                && card.getConversation().getUser() != null
                && card.getConversation().getUser().getId() != null) {
            userVocabularyService.upsertProgress(
                    card.getConversation().getUser().getId(),
                    saved);
        }
        // 更新后清除缓存
        evictCardAndConversationCache(cardId, card.getConversation().getId());
        evictCardAndConversationCache(cardId, card.getConversation().getId());
        log.info("Marked card as completed and evicted cache: cardId={}", cardId);
        return toDTOWithNavigation(saved);
    }

    @Override
    @Transactional
    public VocabularyCardDTO generateNextCard(Long conversationId, String category, String difficulty) {
        Conversation conversation = getConversation(conversationId);

        Integer nextPosition = vocabularyCardRepository.findMaxActivePositionByConversationId(conversationId)
                .map(pos -> pos + 1)
                .orElse(0);

        WordCardData generated = generateRandomWord(conversationId, category, difficulty, "next_word");

        conversation = getConversation(conversationId);
        ensureConversationStillExists(conversationId);
        conversation = getConversation(conversationId);

        VocabularyWord vocabularyWord = vocabularyWordService.findOrCreateWord(generated.getWord());
        log.info("Using VocabularyWord: id={}, normalizedWord={}", vocabularyWord.getId(), vocabularyWord.getNormalizedWord());

        VocabularyCard card = VocabularyCard.builder()
                .conversation(conversation)
                .vocabularyWordId(vocabularyWord.getId())
                .word(generated.getWord())
                .phonetic(generated.getPhonetic())
                .partOfSpeech(generated.getPartOfSpeech())
                .meaning(generated.getMeaning())
                .example(generated.getExample())
                .exampleTranslation(generated.getExampleTranslation())
                .chineseSentenceForTranslation(generated.getExampleTranslation())
                .category(generated.getCategory())
                .difficulty(generated.getDifficulty())
                .position(nextPosition)
                .isCompleted(false)
                .isRegenerated(false)
                .regenerationIndex(0)
                .build();

        if (generated.getSynonyms() != null && !generated.getSynonyms().isEmpty()) {
            card.setSynonyms(generated.getSynonyms());
        }

        VocabularyCard saved = vocabularyCardRepository.save(card);
        if (conversation.getUser() != null && conversation.getUser().getId() != null) {
            userVocabularyService.upsertProgress(conversation.getUser().getId(), saved);
            log.info("Updated UserVocabulary for userId={}, vocabularyWordId={}",
                    conversation.getUser().getId(), vocabularyWord.getId());
        }
        evictConversationCache(conversationId);
        log.info("Generated next card and evicted cache: conversationId={}, cardId={}", conversationId, saved.getId());
        return toDTOWithNavigation(saved);
    }

    @Override
    @Transactional
    public VocabularyCardDTO regenerateCard(Long conversationId, String category, String difficulty) {
        Conversation conversation = getConversation(conversationId);

        List<VocabularyCard> activeCards = vocabularyCardRepository.findActiveCardsByConversationId(conversationId);
        
        if (activeCards.isEmpty()) {
            log.info("该对话没有词汇卡，直接生成第一张");
            return generateNextCard(conversationId, category, difficulty);
        }

        VocabularyCard lastCard = activeCards.get(activeCards.size() - 1);
        
        Integer maxPosition = vocabularyCardRepository.findMaxActivePositionByConversationId(conversationId)
                .orElse(0);
        
        if (!lastCard.getPosition().equals(maxPosition)) {
            log.warn("最后一张卡片的位置不一致: cardPosition={}, maxPosition={}", 
                    lastCard.getPosition(), maxPosition);
        }

        Integer replacePosition = lastCard.getPosition();
        Integer currentRegenerationIndex = lastCard.getRegenerationIndex();
        
        log.info("标记词汇卡为已重新生成: id={}, word={}, position={}, regenerationIndex={}",
                lastCard.getId(), lastCard.getWord(), replacePosition, currentRegenerationIndex);
        lastCard.setIsRegenerated(true);
        vocabularyCardRepository.save(lastCard);

        WordCardData generated = generateRandomWord(conversationId, category, difficulty, "regenerate");

        conversation = getConversation(conversationId);
        ensureConversationStillExists(conversationId);
        conversation = getConversation(conversationId);

        VocabularyWord vocabularyWord = vocabularyWordService.findOrCreateWord(generated.getWord());
        log.info("Using VocabularyWord: id={}, normalizedWord={}", vocabularyWord.getId(), vocabularyWord.getNormalizedWord());

        VocabularyCard newCard = VocabularyCard.builder()
                .conversation(conversation)
                .vocabularyWordId(vocabularyWord.getId())
                .word(generated.getWord())
                .phonetic(generated.getPhonetic())
                .partOfSpeech(generated.getPartOfSpeech())
                .meaning(generated.getMeaning())
                .example(generated.getExample())
                .exampleTranslation(generated.getExampleTranslation())
                .chineseSentenceForTranslation(generated.getExampleTranslation())
                .category(generated.getCategory())
                .difficulty(generated.getDifficulty())
                .position(replacePosition)
                .isCompleted(false)
                .isRegenerated(false)
                .regenerationIndex(currentRegenerationIndex + 1)
                .build();

        if (generated.getSynonyms() != null && !generated.getSynonyms().isEmpty()) {
            newCard.setSynonyms(generated.getSynonyms());
        }

        VocabularyCard saved = vocabularyCardRepository.save(newCard);
        if (conversation.getUser() != null && conversation.getUser().getId() != null) {
            userVocabularyService.upsertProgress(conversation.getUser().getId(), saved);
            log.info("Updated UserVocabulary for userId={}, vocabularyWordId={}",
                    conversation.getUser().getId(), vocabularyWord.getId());
        }
        log.info("重新生成词汇卡成功: id={}, word={}, position={}, regenerationIndex={}",
                saved.getId(), saved.getWord(), replacePosition, saved.getRegenerationIndex());
        evictCardCache(lastCard.getId());
        evictConversationCache(conversationId);
        log.info("Regenerated card and evicted cache: conversationId={}, oldCardId={}, newCardId={}", 
                conversationId, lastCard.getId(), saved.getId());
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
    public VocabularyCardDTO getCardByIdFromDb(Long cardId) {
        VocabularyCard card = vocabularyCardRepository.findById(cardId)
                .orElseThrow(() -> ChatException.badRequest("词汇卡不存在: " + cardId));
        return toDTO(card);
    }

    @Override
    public long getCardCount(Long conversationId) {
        // 先尝试从缓存获取
        Optional<Long> cached = getCardsCountFromCache(conversationId);
        if (cached.isPresent()) {
            return cached.get();
        }
        
        // 缓存未命中，从数据库查询
        // 返回有效卡片的数量
                long count = vocabularyCardRepository.countActiveCardsByConversationId(conversationId);
        
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
                .orElseThrow(() -> ChatException.badRequest("对话不存在: " + conversationId));
    }

    /**
     * 通过AI Agent生成随机单词
     * 核心流程：构建System Prompt -> 调用AI -> 解析工具返回结果
     * @param conversationId 对话ID
     * @param level 难度级别
     * @param intent 意图（next_word/regenerate）
     * @return 生成的单词卡片数据
     */
    private void ensureConversationStillExists(Long conversationId) {
        if (!conversationRepository.existsById(conversationId)) {
            throw ChatException.badRequest("Conversation was deleted while generating vocabulary card: " + conversationId);
        }
    }

    private WordCardData generateRandomWord(Long conversationId, String category, String difficulty, String intent) {
        try {
            log.info("=== 开始生成词汇卡（AI Agent 模式）===");
            log.info("conversationId: {}, category: {}, difficulty: {}, intent: {}", conversationId, category, difficulty, intent);

            String vocabularyCategory = DEFAULT_VOCABULARY_CATEGORY;
            String vocabularyDifficulty = "b2";
            String model = DEFAULT_MODEL;

            if (isNotBlank(category)) {
                vocabularyCategory = category.toLowerCase();
            }
            if (isNotBlank(difficulty)) {
                vocabularyDifficulty = difficulty.toLowerCase();
            }

            Conversation conversation = getConversation(conversationId);
            if (conversation.getUser() != null && conversation.getUser().getId() != null) {
                UserPreferenceDTO preference = userPreferenceService.getOrCreatePreference(conversation.getUser().getId());
                if (!isNotBlank(category) && isNotBlank(preference.getVocabularyCategory())) {
                    vocabularyCategory = preference.getVocabularyCategory().toLowerCase();
                }
                if (!isNotBlank(difficulty) && isNotBlank(preference.getVocabularyDifficulty())) {
                    vocabularyDifficulty = preference.getVocabularyDifficulty().toLowerCase();
                }
                if (isNotBlank(preference.getVocabularyModel())) {
                    model = preference.getVocabularyModel().toLowerCase();
                }
            }
            log.info("Using vocabulary preferences: category={}, difficulty={}, model={}",
                    vocabularyCategory, vocabularyDifficulty, model);

            // 获取System Prompt并追加历史单词记录（避免重复）
            String systemPrompt = systemPromptService.getSystemPrompt(
                    "vocabulary", vocabularyCategory, vocabularyDifficulty);
            log.info("System prompt 已生成，长度: {}", systemPrompt != null ? systemPrompt.length() : 0);

            String vocabularyHistory = buildVocabularyHistoryForPrompt(conversationId);
            if (vocabularyHistory != null && !vocabularyHistory.isEmpty()) {
                systemPrompt = systemPrompt + vocabularyHistory;
                log.info("已追加词汇历史到 System Prompt，conversationId: {}", conversationId);
            }

            // 构建消息列表
            String userMessage = String.format("[intent:%s]", intent != null ? intent : "next_word");
            log.info("发送给 AI 的用户消息: {}", userMessage);

            List<OpenAiChatMessage> messages = new ArrayList<>();
            messages.add(OpenAiChatMessage.createTextMessage("system", systemPrompt));
            messages.add(OpenAiChatMessage.createTextMessage("user", userMessage));

            // 获取vocabulary模式可用的工具
            List<OpenAiTool> tools = mcpService.getOpenAiToolsForMode("vocabulary");
            log.info("获取到 {} 个vocabulary 模式的工具", tools != null ? tools.size() : 0);

            if (tools == null || tools.isEmpty()) {
                log.warn("没有可用的vocabulary 工具，使用默认单词");
                return getDefaultWord();
            }

            // 调用AI Agent执行工具调用
            log.info("调用 AI Agent 执行工具调用...");
            ToolLoopService.ToolLoopResult result = toolLoopService.executeOneTimeToolCall(
                    conversationId, messages, tools, model);
            recordVocabularyTokenUsage(conversationId, result);

            log.info("AI Agent 执行结果: hasToolCalls={}, hasTextResponse={}",
                    result.hasToolCalls(), result.hasTextResponse());

            // 解析工具返回结果
            if (result.hasToolCalls() && result.getToolResultText() != null) {
                String toolResultText = result.getToolResultText();
                log.info("工具返回结果: {}", toolResultText != null && toolResultText.length() > 100 
                        ? toolResultText.substring(0, 100) + "..." : toolResultText);

                return parseWordCardDataFromToolResult(toolResultText, vocabularyCategory, vocabularyDifficulty);
            } else if (result.hasTextResponse()) {
                log.warn("AI 返回了文本响应而非工具调用，文本: {}", result.getTextResponse());
                return getDefaultWord();
            } else {
                log.warn("AI 没有返回有效结果，使用默认单词");
                return getDefaultWord();
            }

        } catch (Exception e) {
            log.error("生成词汇卡时发生错误", e);
            return getDefaultWord();
        }
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private void recordVocabularyTokenUsage(Long conversationId, ToolLoopService.ToolLoopResult result) {
        if (result == null || !result.hasTokenUsage()) {
            return;
        }
        Integer total = result.getTokenUsage().getTotal();
        if (total == null || total <= 0) {
            return;
        }
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            long current = conversation.getTotalTokensEstimate() != null ? conversation.getTotalTokensEstimate() : 0L;
            conversation.setTotalTokensEstimate(current + total);
            conversationRepository.save(conversation);
            log.info("Recorded vocabulary token usage: conversationId={}, added={}, totalEstimate={}",
                    conversationId, total, conversation.getTotalTokensEstimate());
        });
    }

    /**
     * 解析AI工具返回的JSON结果为WordCardData
     * @param toolResultText 工具返回的JSON字符串
     * @param defaultCategory 默认词汇类别
     * @param defaultDifficulty 默认难度级别
     * @return 解析后的单词卡片数据
     */
    private WordCardData parseWordCardDataFromToolResult(String toolResultText, String defaultCategory, String defaultDifficulty) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(
                    toolResultText, new TypeReference<Map<String, Object>>() {});

            String word = (String) parsed.get("word");
            if (word == null || word.trim().isEmpty()) {
                log.warn("工具返回结果中word 为空，使用默认单词");
                return getDefaultWord();
            }

            List<String> synonyms = parseList(parsed.get("synonyms"));

            String category = (String) parsed.get("vocabularyCategory");
            if (category == null || category.isEmpty()) {
                category = defaultCategory != null ? defaultCategory : DEFAULT_VOCABULARY_CATEGORY;
            }

            String difficulty = (String) parsed.get("vocabularyDifficulty");
            if (difficulty == null || difficulty.isEmpty()) {
                difficulty = defaultDifficulty != null ? defaultDifficulty : "b2";
            }

            log.info("成功解析词汇卡数据: word={}, phonetic={}, meaning={}, category={}, difficulty={}",
                    word, parsed.get("phonetic"), parsed.get("meaning"), category, difficulty);

            return WordCardData.builder()
                    .word(word)
                    .phonetic((String) parsed.get("phonetic"))
                    .meaning((String) parsed.get("meaning"))
                    .example((String) parsed.get("example"))
                    .exampleTranslation((String) parsed.get("exampleTranslation"))
                    .synonyms(synonyms)
                    .category(category)
                    .difficulty(difficulty)
                    .build();

        } catch (Exception e) {
            log.error("解析工具返回结果时发生错误: {}", toolResultText, e);
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
     * @return 格式化的历史记录字符串
     */
    private String buildVocabularyHistoryForPrompt(Long conversationId) {
        // 获取所有卡片（包括已重新生成的）用于构建历史
                List<VocabularyCard> allCards = vocabularyCardRepository.findByConversationIdOrderByPositionAsc(conversationId);
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

            // 显示每个重新生成的版本
            for (VocabularyCard card : cardsAtPosition) {
                if (card.getIsRegenerated()) {
                    // 已重新生成的单词，标记为用户不满意
                    sb.append("- [重新生成过的单词（用户不满意）] ").append(card.getWord() != null ? card.getWord() : "");
                    if (card.getRegenerationIndex() > 0) {
                        sb.append(" (第").append(card.getRegenerationIndex()).append("版)");
                    }
                    sb.append("\n");
                } else {
                    // 当前有效的单词
                    sb.append("- [当前单词] ").append(card.getWord() != null ? card.getWord() : "");
                    if (card.getRegenerationIndex() > 0) {
                        sb.append(" (第").append(card.getRegenerationIndex()).append("版)");
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
        
        log.info("已为 conversationId {} 构建历史信息，包含 {} 个位置的单词记录", conversationId, positions.size());
        return sb.toString();
    }

    /**
     * 获取默认单词（当AI生成失败时的兜底方案）
     */
    private WordCardData getDefaultWord() {
        return WordCardData.builder()
                .word("hello")
                .phonetic("həˈloʊ")
                .partOfSpeech("int.")
                .meaning("你好")
                .example("Hello, how are you?")
                .exampleTranslation("你好，你好吗？")
                .synonyms(List.of("hi", "hey"))
                .category("cefr")
                .difficulty("a1")
                .build();
    }

    /**
     * 获取某个位置的重新生成历史单词列表
     */
    private List<String> getRegeneratedWords(Long conversationId, Integer position) {
        List<VocabularyCard> cardsAtPosition = vocabularyCardRepository
                .findByConversationIdAndPositionOrderByRegenerationIndexAsc(conversationId, position);
        
        return cardsAtPosition.stream()
                .filter(VocabularyCard::getIsRegenerated)
                .map(VocabularyCard::getWord)
                .collect(Collectors.toList());
    }

    /**
     * 将实体转换为DTO（基础转换）
     */
    private VocabularyCardDTO toDTO(VocabularyCard card) {
        // 获取该位置的重新生成历史
        List<String> regeneratedWords = getRegeneratedWords(
                card.getConversation().getId(), card.getPosition());
        
        return VocabularyCardDTO.builder()
                .id(card.getId())
                .conversationId(card.getConversation().getId())
                .word(card.getWord())
                .phonetic(card.getPhonetic())
                .partOfSpeech(card.getPartOfSpeech())
                .meaning(card.getMeaning())
                .example(card.getExample())
                .exampleTranslation(card.getExampleTranslation())
                .synonyms(card.getSynonyms())
                .category(card.getCategory())
                .difficulty(card.getDifficulty())
                .position(card.getPosition())
                .userMeaningGuess(card.getUserMeaningGuess())
                .meaningCheckResult(card.getMeaningCheckResult())
                .meaningIsCorrect(card.getMeaningIsCorrect())
                .meaningCheckCompleted(card.getMeaningCheckCompleted())
                .chineseSentenceForTranslation(card.getChineseSentenceForTranslation())
                .userEnglishSentence(card.getUserEnglishSentence())
                .sentenceAnalysis(card.getSentenceAnalysis())
                .sentenceAnalysisCompleted(card.getSentenceAnalysisCompleted())
                .sentenceHasNewWord(card.getSentenceHasNewWord())
                .sentenceMeaningMatches(card.getSentenceMeaningMatches())
                .isCompleted(card.getIsCompleted())
                .isRegenerated(card.getIsRegenerated())
                .regenerationIndex(card.getRegenerationIndex())
                .regeneratedWords(regeneratedWords)
                .createdAt(card.getCreatedAt())
                .updatedAt(card.getUpdatedAt())
                .build();
    }

    /**
     * 将实体转换为DTO（包含导航信息：上一个、下一个是否存在）
     * 使用 getAllCards 来获取卡片列表，以便利用缓存
     */
    private VocabularyCardDTO toDTOWithNavigation(VocabularyCard card) {
        VocabularyCardDTO dto = toDTO(card);
        
        Long conversationId = card.getConversation().getId();
        // 使用 getAllCards 获取卡片列表（会利用缓存）
                List<VocabularyCardDTO> cardDTOs = getAllCards(conversationId);
        
        // 找到当前卡片在列表中的索引
                int currentIndex = -1;
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
