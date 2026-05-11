import { useState } from 'react'
import { LearningMode, LEARNING_MODES } from '../../../types'

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
    mode: 'grammar' as LearningMode,
    label: '语法纠错',
    labelEn: 'Grammar Fix',
    icon: '📝',
    color: '#8b5cf6',
    bgColor: '#f5f3ff',
  },
  {
    mode: 'vocabulary' as LearningMode,
    label: '词汇拓展',
    labelEn: 'Vocabulary',
    icon: '📚',
    color: '#10b981',
    bgColor: '#ecfdf5',
  },
  {
    mode: 'writing' as LearningMode,
    label: '发音训练',
    labelEn: 'Pronunciation',
    icon: '🔊',
    color: '#f59e0b',
    bgColor: '#fffbeb',
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
  onCreateConversationWithMode?: (mode: LearningMode) => void
  disabled?: boolean
  isEmptyState?: boolean
}

export function ModeSelectorGrid({
  learningMode,
  onLearningModeSelect,
  onCreateConversationWithMode,
  disabled,
  isEmptyState = false,
}: ModeSelectorGridProps) {
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
            className={`mode-selector-card ${learningMode === feature.mode ? 'selected' : ''}`}
            style={
              learningMode === feature.mode
                ? { borderColor: feature.color, backgroundColor: feature.bgColor }
                : {}
            }
            onClick={() => {
              if (isEmptyState && onCreateConversationWithMode && !disabled) {
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
