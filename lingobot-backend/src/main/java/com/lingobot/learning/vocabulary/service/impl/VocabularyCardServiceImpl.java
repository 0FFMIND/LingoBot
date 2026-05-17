package com.lingobot.learning.vocabulary.service.impl;

import com.lingobot.infrastructure.common.exception.ChatException;
import com.lingobot.core.conversation.entity.Conversation;
import com.lingobot.core.conversation.repository.ConversationRepository;
import com.lingobot.learning.conversation.entity.ConversationLearningData;
import com.lingobot.learning.conversation.repository.ConversationLearningDataRepository;
import com.lingobot.learning.langgraph.vocabulary.VocabularyGraph;
import com.lingobot.learning.memory.vocabulary.VocabularyGenerationIntent;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryEventType;
import com.lingobot.learning.vocabulary.dto.CreateVocabularyCardRequest;
import com.lingobot.learning.vocabulary.dto.VocabularyCardDTO;
import com.lingobot.learning.vocabulary.dto.WordCardData;
import com.lingobot.learning.vocabulary.entity.VocabularyCard;
import com.lingobot.learning.vocabulary.entity.VocabularyWord;
import com.lingobot.learning.vocabulary.repository.UserVocabularyRepository;
import com.lingobot.learning.vocabulary.repository.VocabularyCardRepository;
import com.lingobot.learning.vocabulary.service.MeaningCheckService;
import com.lingobot.learning.vocabulary.service.SentenceAnalysisService;
import com.lingobot.learning.vocabulary.service.UserVocabularyEventService;
import com.lingobot.learning.vocabulary.service.UserVocabularyService;
import com.lingobot.learning.vocabulary.service.VocabularyCardService;
import com.lingobot.learning.util.UserInputSanitizer;
import com.lingobot.learning.vocabulary.service.VocabularyCardSnapshotService;
import com.lingobot.learning.vocabulary.service.VocabularyWordService;
import com.lingobot.learning.langgraph.vocabulary.VocabularyBatchGraph;
import com.lingobot.learning.memory.vocabulary.VocabularyMemoryService;
import com.lingobot.learning.vocabulary.dto.VocabularyBatchGenerationResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
    private final ConversationLearningDataRepository learningDataRepository;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final MeaningCheckService meaningCheckService;
    private final SentenceAnalysisService sentenceAnalysisService;
    private final VocabularyWordService vocabularyWordService;
    private final UserVocabularyService userVocabularyService;
    private final UserVocabularyRepository userVocabularyRepository;
    private final UserVocabularyEventService eventService;
    private final VocabularyCardSnapshotService snapshotService;
    private final VocabularyMemoryService vocabularyMemoryService;
    private final VocabularyGraph vocabularyGraph;
    private final VocabularyBatchGraph vocabularyBatchGraph;
    
    /** Redis 缓存键前缀 - 单个单词卡*/
    private static final String CACHE_KEY_CARD = "vocabulary:card:";
    /** Redis 缓存键前缀 - 对话的所有有效卡片列表*/
    private static final String CACHE_KEY_CARDS_LIST = "vocabulary:cards:";
    /** Redis 缓存键前缀 - 对话的有效卡片数量*/
    private static final String CACHE_KEY_CARDS_COUNT = "vocabulary:count:";
    /** 缓存过期时间（小时） */
    private static final long CACHE_EXPIRE_HOURS = 1;
    
    // 获取单个词汇卡的 Redis 缓存键
    private String getCardCacheKey(Long cardId) {
        return CACHE_KEY_CARD + cardId;
    }
    
    // 获取对话词汇卡列表的 Redis 缓存键
    private String getCardsListCacheKey(Long conversationId) {
        return CACHE_KEY_CARDS_LIST + conversationId;
    }
    
    // 获取对话词汇卡数量的 Redis 缓存键
    private String getCardsCountCacheKey(Long conversationId) {
        return CACHE_KEY_CARDS_COUNT + conversationId;
    }
    
    // 从 Redis 缓存读取单个词汇卡，未命中返回 empty
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
    
    // 从 Redis 缓存读取对话的词汇卡列表，未命中返回 empty
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
    
    // 从 Redis 缓存读取对话的词汇卡数量，未命中返回 empty
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
    
    // 将单个词汇卡写入 Redis 缓存
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
    
    // 将对话的词汇卡列表写入 Redis 缓存
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
    
    // 将对话的词汇卡数量写入 Redis 缓存
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
            Long userId = conversation.getUser().getId();
            userVocabularyService.upsertRecordOnly(userId, saved);
            vocabularyMemoryService.recordInteraction(userId, saved, VocabularyMemoryEventType.SEEN);
            log.info("Created UserVocabulary record for userId={}, vocabularyWordId={}",
                    userId, vocabularyWord.getId());
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
            log.info("该对话没有词汇卡，自动批量生成10张");
            VocabularyBatchGenerationResult batchResult = generateBatchCards(conversationId, category, difficulty);
            if (!batchResult.getRevealedCards().isEmpty()) {
                return batchResult.getRevealedCards().get(0);
            }
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
            log.info("已经是最后一个单词了，自动批量生成10张新单词卡");
            VocabularyBatchGenerationResult batchResult = generateBatchCards(conversationId, category, difficulty);
            if (!batchResult.getRevealedCards().isEmpty()) {
                return batchResult.getRevealedCards().get(0);
            }
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
        String sanitizedMeaning = UserInputSanitizer.sanitizeUserMeaning(userMeaning);

        VocabularyCard card = vocabularyCardRepository.findById(cardId)
                .orElseThrow(() -> ChatException.badRequest("词汇卡不存在: " + cardId));

        card.setUserMeaningGuess(sanitizedMeaning);
        card.setMeaningCheckCompleted(false);
        card.setMeaningIsCorrect(null);
        card.setMeaningCheckResult(null);
        VocabularyCard saved = vocabularyCardRepository.save(card);

        Long conversationId = card.getConversation() != null ? card.getConversation().getId() : null;
        if (conversationId != null) {
            log.info("Triggering async meaning check for cardId={}, conversationId={}", cardId, conversationId);
            meaningCheckService.checkUserMeaningAsync(conversationId, cardId, sanitizedMeaning);
        }

        evictCardAndConversationCache(cardId, conversationId);
        log.info("Updated user meaning and evicted cache: cardId={}", cardId);
        return toDTOWithNavigation(saved);
    }

    @Override
    @Transactional
    public VocabularyCardDTO updateUserEnglishSentence(Long cardId, String userEnglishSentence) {
        String sanitizedSentence = UserInputSanitizer.sanitizeUserSentence(userEnglishSentence);

        VocabularyCard card = vocabularyCardRepository.findById(cardId)
                .orElseThrow(() -> ChatException.badRequest("词汇卡不存在: " + cardId));

        card.setUserEnglishSentence(sanitizedSentence);
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
        // 更新后清除缓存
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

        String intent = learningDataRepository.findByConversationId(conversationId)
                .map(ConversationLearningData::getVocabularyIntent)
                .orElse(null);
        if (intent == null || intent.isEmpty()) {
            intent = "new_word";
        }

        WordCardData generated = generateRandomWord(conversationId, category, difficulty, intent);

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
            Long userId = conversation.getUser().getId();
            userVocabularyService.upsertRecordOnly(userId, saved);
            vocabularyMemoryService.recordInteraction(userId, saved, VocabularyMemoryEventType.SEEN);
            log.info("Created UserVocabulary record for userId={}, vocabularyWordId={}",
                    userId, vocabularyWord.getId());
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
        Long userId = conversation.getUser() != null ? conversation.getUser().getId() : null;
        vocabularyMemoryService.recordInteraction(userId, lastCard, VocabularyMemoryEventType.REGENERATED);

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
        if (userId != null) {
            userVocabularyService.upsertRecordOnly(userId, saved);
            vocabularyMemoryService.recordInteraction(userId, saved, VocabularyMemoryEventType.SEEN);
            log.info("Created UserVocabulary record for userId={}, vocabularyWordId={}",
                    userId, vocabularyWord.getId());
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

    // 根据 ID 获取对话实体，不存在则抛出业务异常
    private Conversation getConversation(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> ChatException.badRequest("对话不存在: " + conversationId));
    }

    // 确认对话仍然存在（AI 生成期间对话可能被并发删除）
    private void ensureConversationStillExists(Long conversationId) {
        if (!conversationRepository.existsById(conversationId)) {
            throw ChatException.badRequest("Conversation was deleted while generating vocabulary card: " + conversationId);
        }
    }

    /**
     * 通过 AI Agent 生成随机单词卡片数据。
     * 核心流程：读取用户偏好 → 构建 System Prompt（含历史词汇）→ 调用 AI 工具 → 解析返回结果。
     * @param conversationId 对话ID
     * @param category 词汇类别（cefr/ielts/toefl），null 时从用户偏好读取
     * @param difficulty 难度级别，null 时从用户偏好读取
     * @param intent 生成意图（next_word / regenerate / new_word / review / hybrid）
     * @return 解析后的单词卡片数据，AI 失败时返回默认单词
     */
    private WordCardData generateRandomWord(Long conversationId, String category, String difficulty, String intent) {
        log.info("=== 开始生成词汇卡（LangGraph4j 模式）===");
        log.info("conversationId: {}, category: {}, difficulty: {}, intent: {}", conversationId, category, difficulty, intent);

        Conversation conversation = getConversation(conversationId);
        Long userId = conversation.getUser() != null ? conversation.getUser().getId() : null;

        VocabularyGenerationIntent generationIntent = switch (intent) {
            case "regenerate" -> VocabularyGenerationIntent.REGENERATE;
            case "new_word" -> VocabularyGenerationIntent.NEW_WORD;
            case "review" -> VocabularyGenerationIntent.REVIEW;
            case "hybrid" -> VocabularyGenerationIntent.HYBRID;
            default -> VocabularyGenerationIntent.NEXT_WORD;
        };

        Optional<WordCardData> result = vocabularyGraph.execute(
                conversationId, userId, category, difficulty, generationIntent);

        return result.orElseGet(() -> {
            log.warn("LangGraph execution did not produce a result, using fallback");
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
        });
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
                .isRevealed(card.getIsRevealed())
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

    @Override
    @Transactional
    public VocabularyBatchGenerationResult generateBatchCards(Long conversationId, String category, String difficulty) {
        return generateBatchCards(conversationId, category, difficulty, 10);
    }

    @Override
    @Transactional
    public VocabularyBatchGenerationResult generateBatchCards(Long conversationId, String category, String difficulty, int batchSize) {
        log.info("=== 开始批量生成词汇卡（Batch 模式）===");
        log.info("conversationId: {}, category: {}, difficulty: {}, batchSize: {}", conversationId, category, difficulty, batchSize);

        Conversation conversation = getConversation(conversationId);
        Long userId = conversation.getUser() != null ? conversation.getUser().getId() : null;

        String vocabularyIntentStr = learningDataRepository.findByConversationId(conversationId)
                .map(ConversationLearningData::getVocabularyIntent)
                .orElse(null);
        com.lingobot.learning.memory.vocabulary.VocabularyGenerationIntent intent =
                com.lingobot.learning.memory.vocabulary.VocabularyGenerationIntent.NEW_WORD;
        
        if (vocabularyIntentStr != null && !vocabularyIntentStr.isBlank()) {
            try {
                intent = com.lingobot.learning.memory.vocabulary.VocabularyGenerationIntent.valueOf(
                        vocabularyIntentStr.toUpperCase());
                log.info("Using vocabulary intent from conversation: {}", intent);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid vocabulary intent: {}, using default NEW_WORD", vocabularyIntentStr);
            }
        }

        var result = vocabularyBatchGraph.execute(
                conversationId, userId, category, difficulty, intent, batchSize);

        evictConversationCache(conversationId);

        return result.orElseGet(() -> {
            log.warn("Batch generation failed, returning empty result");
            return VocabularyBatchGenerationResult.builder()
                    .revealedCards(List.of())
                    .hiddenCards(List.of())
                    .totalRevealed(0)
                    .totalHidden(0)
                    .totalCount(0)
                    .build();
        });
    }

    @Override
    @Transactional
    public VocabularyCardDTO revealNextCard(Long conversationId) {
        log.info("揭露下一张词汇卡: conversationId={}", conversationId);

        List<VocabularyCard> hiddenCards = vocabularyCardRepository.findNextHiddenCard(
                conversationId, PageRequest.of(0, 1));

        if (hiddenCards.isEmpty()) {
            throw com.lingobot.infrastructure.common.exception.ChatException.badRequest("没有未揭露的词汇卡了");
        }

        VocabularyCard cardToReveal = hiddenCards.get(0);
        return revealCard(cardToReveal.getId());
    }

    @Override
    @Transactional
    public VocabularyCardDTO revealCard(Long cardId) {
        log.info("揭露词汇卡: cardId={}", cardId);

        VocabularyCard card = vocabularyCardRepository.findById(cardId)
                .orElseThrow(() -> com.lingobot.infrastructure.common.exception.ChatException.badRequest("词汇卡不存在: " + cardId));

        if (Boolean.TRUE.equals(card.getIsRevealed())) {
            log.warn("词汇卡已经是揭露状态: cardId={}", cardId);
            return toDTOWithNavigation(card);
        }

        vocabularyCardRepository.markAsRevealed(cardId);
        card.setIsRevealed(true);

        evictCardAndConversationCache(cardId, card.getConversation().getId());
        log.info("词汇卡已揭露: cardId={}, word={}", cardId, card.getWord());

        return toDTOWithNavigation(card);
    }

    @Override
    @Transactional
    public VocabularyCardDTO regenerateCardAtPosition(Long conversationId, Integer position, String category, String difficulty) {
        log.info("在指定位置重新生成词汇卡: conversationId={}, position={}", conversationId, position);

        Conversation conversation = getConversation(conversationId);

        Optional<VocabularyCard> existingCardOpt = vocabularyCardRepository.findActiveCardByConversationIdAndPosition(
                conversationId, position);

        if (existingCardOpt.isEmpty()) {
            log.warn("该位置没有有效词汇卡，使用默认重新生成逻辑: position={}", position);
            return regenerateCard(conversationId, category, difficulty);
        }

        VocabularyCard existingCard = existingCardOpt.get();

        log.info("标记词汇卡为已重新生成: id={}, word={}, position={}, regenerationIndex={}",
                existingCard.getId(), existingCard.getWord(), position, existingCard.getRegenerationIndex());

        existingCard.setIsRegenerated(true);
        vocabularyCardRepository.save(existingCard);

        Long userId = conversation.getUser() != null ? conversation.getUser().getId() : null;
        vocabularyMemoryService.recordInteraction(userId, existingCard,
                com.lingobot.learning.memory.vocabulary.VocabularyMemoryEventType.REGENERATED);

        WordCardData generated = generateRandomWord(conversationId, category, difficulty, "regenerate");

        conversation = getConversation(conversationId);
        ensureConversationStillExists(conversationId);
        conversation = getConversation(conversationId);

        com.lingobot.learning.vocabulary.entity.VocabularyWord vocabularyWord = vocabularyWordService.findOrCreateWord(generated.getWord());
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
                .position(position)
                .isCompleted(false)
                .isRegenerated(false)
                .isRevealed(true)
                .regenerationIndex(existingCard.getRegenerationIndex() + 1)
                .build();

        if (generated.getSynonyms() != null && !generated.getSynonyms().isEmpty()) {
            newCard.setSynonyms(generated.getSynonyms());
        }

        VocabularyCard saved = vocabularyCardRepository.save(newCard);
        if (userId != null) {
            userVocabularyService.upsertRecordOnly(userId, saved);
            vocabularyMemoryService.recordInteraction(userId, saved,
                    com.lingobot.learning.memory.vocabulary.VocabularyMemoryEventType.SEEN);
            log.info("Created UserVocabulary record for userId={}, vocabularyWordId={}",
                    userId, vocabularyWord.getId());
        }
        log.info("在位置 {} 重新生成词汇卡成功: id={}, word={}, regenerationIndex={}",
                position, saved.getId(), saved.getWord(), saved.getRegenerationIndex());
        evictCardCache(existingCard.getId());
        evictConversationCache(conversationId);
        log.info("Regenerated card at position and evicted cache: conversationId={}, oldCardId={}, newCardId={}",
                conversationId, existingCard.getId(), saved.getId());
        return toDTOWithNavigation(saved);
    }

    @Override
    public VocabularyBatchGenerationResult getBatchStatus(Long conversationId) {
        List<VocabularyCard> allCards = vocabularyCardRepository.findActiveCardsByConversationId(conversationId);

        List<VocabularyCardDTO> revealedCards = allCards.stream()
                .filter(card -> Boolean.TRUE.equals(card.getIsRevealed()))
                .map(this::toDTO)
                .collect(Collectors.toList());

        List<VocabularyCardDTO> hiddenCards = allCards.stream()
                .filter(card -> !Boolean.TRUE.equals(card.getIsRevealed()))
                .map(card -> {
                    VocabularyCardDTO dto = toDTO(card);
                    dto.setWord("???");
                    dto.setMeaning("???");
                    dto.setPhonetic("???");
                    dto.setExample("???");
                    dto.setExampleTranslation("???");
                    dto.setPartOfSpeech("???");
                    dto.setSynonyms(List.of());
                    return dto;
                })
                .collect(Collectors.toList());

        return VocabularyBatchGenerationResult.builder()
                .revealedCards(revealedCards)
                .hiddenCards(hiddenCards)
                .totalRevealed(revealedCards.size())
                .totalHidden(hiddenCards.size())
                .totalCount(allCards.size())
                .build();
    }
}
