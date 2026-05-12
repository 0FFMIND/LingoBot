import React from 'react'

interface WelcomeMessageProps {
  username?: string
  learningConfig: {
    welcomeMessage: string
    icon: string
    label: string
  }
}

const WelcomeMessage: React.FC<WelcomeMessageProps> = ({ username = '你', learningConfig }) => {
  return (
    <div className="welcome-section-english">
      <div className="welcome-card-english">
        <div className="welcome-avatar-english">
          <div className="avatar-robot-english">
            <span className="robot-emoji">🤖</span>
          </div>
        </div>
        <div className="welcome-content-english">
          <h2 className="welcome-title-english">
            {username ? `Hi, ${username}!` : 'Hi'}
          </h2>
          <p className="welcome-text-english">
            {learningConfig.welcomeMessage}
          </p>
          <p className="welcome-hint-english">
            告诉我你的学习目标或想练习的内容吧，我会为你量身定制学习体验。
          </p>
        </div>
      </div>
    </div>
  )
}

export default WelcomeMessage
