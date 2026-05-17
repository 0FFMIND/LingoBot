import { useState } from 'react'
import { LearningMode, LEARNING_MODES, VocabularyIntent } from '../../types'

export const learningFeatures = [
  {
    mode: 'chat' as LearningMode,
    label: '日常对话',
    labelEn: 'Daily Chat',
    icon: '💬',
    color: '#6366f1',
    bgColor: '#eef2ff',
  },
  {
    mode: 'vocabulary' as LearningMode,
    label: '词汇拓展',
    labelEn: 'Vocabulary',
    icon: '📚',
    color: '#10b981',
    bgColor: '#ecfdf5',
  },
]

export const vocabularySubModes = [
  {
    intent: 'new_word' as VocabularyIntent,
    label: '学习新单词',
    labelEn: 'Learn New Words',
    icon: '🌱',
    color: '#10b981',
    bgColor: '#dcfce7',
    description: '学习全新的词汇，扩展您的词汇量',
    features: ['发现新词汇', '记忆强化'],
  },
  {
    intent: 'review' as VocabularyIntent,
    label: '复习单词',
    labelEn: 'Review Words',
    icon: '🔄',
    color: '#3b82f6',
    bgColor: '#dbeafe',
    description: '复习已学词汇，巩固记忆效果',
    features: ['记忆巩固训练', '薄弱词汇强化'],
    recommended: false,
  },
  {
    intent: 'hybrid' as VocabularyIntent,
    label: '混合模式',
    labelEn: 'Hybrid Mode',
    icon: '🔮',
    color: '#8b5cf6',
    bgColor: '#ede9fe',
    description: '新词学习 + 复习巩固，全面提升',
    features: ['智能平衡', '最高效率'],
    recommended: true,
  },
]

interface LearningModeSelectorProps {
  learningMode: LearningMode
  onLearningModeChange: (mode: LearningMode) => void
  disabled?: boolean
}

export function LearningModeSelector({
  learningMode,
  onLearningModeChange,
  disabled,
}: LearningModeSelectorProps) {
  const [showMenu, setShowMenu] = useState(false)

  const currentConfig = LEARNING_MODES[learningMode]

  return (
    <div className="learning-mode-selector">
      <button
        className="learning-mode-toggle-btn"
        onClick={() => !disabled && setShowMenu(!showMenu)}
        disabled={disabled}
        title="选择学习模式"
      >
        <span className="learning-mode-icon">{currentConfig.icon}</span>
        <span className="learning-mode-label">{currentConfig.label}</span>
        <span className={`dropdown-arrow ${showMenu ? 'open' : ''}`}>▼</span>
      </button>

      {showMenu && (
        <div className="learning-mode-menu">
          <div className="learning-mode-menu-title">选择学习模式</div>
          <div className="learning-mode-options">
            {(Object.keys(LEARNING_MODES) as LearningMode[]).map((mode) => {
              const config = LEARNING_MODES[mode]
              const isActive = learningMode === mode

              return (
                <button
                  key={mode}
                  className={`learning-mode-option ${isActive ? 'active' : ''}`}
                  onClick={() => {
                    onLearningModeChange(mode)
                    setShowMenu(false)
                  }}
                >
                  <span className="learning-mode-option-icon">{config.icon}</span>
                  <div className="learning-mode-option-info">
                    <span className="learning-mode-option-name">{config.label}</span>
                    <span className="learning-mode-option-desc">{config.description}</span>
                  </div>
                  {isActive && <span className="check-mark">✓</span>}
                </button>
              )
            })}
          </div>
        </div>
      )}
    </div>
  )
}

interface ModeSelectorGridProps {
  learningMode: LearningMode
  onLearningModeSelect?: (mode: LearningMode) => void
  onCreateConversationWithMode?: (mode: LearningMode, vocabularyIntent?: VocabularyIntent) => void
  disabled?: boolean
  isEmptyState?: boolean
  showVocabularySubMode?: boolean
  selectedVocabularyIntent?: VocabularyIntent | null
  onVocabularyIntentSelect?: (intent: VocabularyIntent) => void
  onBackToModeSelector?: () => void
}

export function VocabularySubModeSelector({
  selectedVocabularyIntent,
  onVocabularyIntentSelect,
  onStartLearning,
  onBack,
  disabled,
}: {
  selectedVocabularyIntent: VocabularyIntent | null
  onVocabularyIntentSelect: (intent: VocabularyIntent) => void
  onStartLearning: (intent: VocabularyIntent) => void
  onBack: () => void
  disabled?: boolean
}) {
  return (
    <div className="vocabulary-submode-section">
      <div className="vocabulary-submode-header">
        <h2 className="vocabulary-submode-title">
          <span>📚</span>
          选择学习模式 - 词汇拓展
        </h2>
        <p className="vocabulary-submode-subtitle">选择适合您的学习方式，开始词汇学习之旅</p>
      </div>

      <div className="vocabulary-submode-recommend">
        <span className="vocabulary-submode-recommend-icon">💡</span>
        <p className="vocabulary-submode-recommend-text">
          <strong>推荐</strong> - 根据您的学习数据，我们推荐混合模式，能够帮助您在学习新词的同时巩固已有词汇，达到最佳学习效果。
        </p>
      </div>

      <div className="vocabulary-submode-grid">
        {vocabularySubModes.map((subMode) => (
          <button
            key={subMode.intent}
            className={`vocabulary-submode-card ${selectedVocabularyIntent === subMode.intent ? 'selected' : ''}`}
            style={
              selectedVocabularyIntent === subMode.intent
                ? { borderColor: subMode.color }
                : {}
            }
            onClick={() => !disabled && onVocabularyIntentSelect(subMode.intent)}
            disabled={disabled}
          >
            {subMode.recommended && (
              <span className="vocabulary-submode-badge">推荐</span>
            )}
            <span
              className="vocabulary-submode-icon"
              style={{ backgroundColor: subMode.bgColor, color: subMode.color }}
            >
              {subMode.icon}
            </span>
            <span className="vocabulary-submode-label">{subMode.label}</span>
            <span className="vocabulary-submode-label-en">{subMode.labelEn}</span>
            <p className="vocabulary-submode-desc">{subMode.description}</p>
            <div className="vocabulary-submode-features">
              {subMode.features.map((feature, idx) => (
                <div key={idx} className="vocabulary-submode-feature">
                  {feature}
                </div>
              ))}
            </div>
            <div className="vocabulary-submode-radio" />
          </button>
        ))}
      </div>

      <div className="vocabulary-submode-actions">
        <button
          className="vocabulary-submode-start-btn"
          onClick={() => selectedVocabularyIntent && onStartLearning(selectedVocabularyIntent)}
          disabled={disabled || !selectedVocabularyIntent}
        >
          开始词汇学习
        </button>
        <button
          className="vocabulary-submode-back-link"
          onClick={onBack}
          disabled={disabled}
        >
          返回选择其他模式
        </button>
      </div>
    </div>
  )
}

export function ModeSelectorGrid({
  learningMode,
  onLearningModeSelect,
  onCreateConversationWithMode,
  disabled,
  isEmptyState = false,
  showVocabularySubMode = false,
  selectedVocabularyIntent = null,
  onVocabularyIntentSelect,
  onBackToModeSelector,
}: ModeSelectorGridProps) {
  const [localSelectedIntent, setLocalSelectedIntent] = useState<VocabularyIntent | null>(selectedVocabularyIntent)

  const handleStartLearning = (intent: VocabularyIntent) => {
    if (isEmptyState && onCreateConversationWithMode) {
      onCreateConversationWithMode('vocabulary', intent)
    } else if (onVocabularyIntentSelect) {
      onVocabularyIntentSelect(intent)
    } else if (onCreateConversationWithMode) {
      onCreateConversationWithMode('vocabulary', intent)
    }
  }

  const handleBack = () => {
    setLocalSelectedIntent(null)
    if (onBackToModeSelector) {
      onBackToModeSelector()
    }
  }

  if (showVocabularySubMode) {
    return (
      <VocabularySubModeSelector
        selectedVocabularyIntent={localSelectedIntent}
        onVocabularyIntentSelect={setLocalSelectedIntent}
        onStartLearning={handleStartLearning}
        onBack={handleBack}
        disabled={disabled}
      />
    )
  }

  return (
    <div className="mode-selector-section">
      <div className="mode-selector-header">
        <h2 className="mode-selector-title">选择学习模式</h2>
        <p className="mode-selector-subtitle">请选择您想要的学习模式，开始您的英语学习之旅</p>
      </div>
      <div className="mode-selector-grid">
        {learningFeatures.map((feature) => (
          <button
            key={feature.mode}
            className={`mode-selector-card mode-selector-card-${feature.mode} ${learningMode === feature.mode ? 'selected' : ''}`}
            onClick={() => {
              if (feature.mode === 'vocabulary') {
                if (onLearningModeSelect && !disabled) {
                  onLearningModeSelect(feature.mode)
                }
              } else if (isEmptyState && onCreateConversationWithMode && !disabled) {
                onCreateConversationWithMode(feature.mode)
              } else if (onLearningModeSelect && !disabled) {
                onLearningModeSelect(feature.mode)
              }
            }}
            disabled={disabled}
          >
            <span
              className="mode-selector-icon"
              style={{ backgroundColor: feature.bgColor, color: feature.color }}
            >
              {feature.icon}
            </span>
            <span className="mode-selector-label">{feature.label}</span>
            <span className="mode-selector-label-en">{feature.labelEn}</span>
            {learningMode === feature.mode && !isEmptyState && (
              <span className="mode-selector-check">✓</span>
            )}
          </button>
        ))}
      </div>
    </div>
  )
}
