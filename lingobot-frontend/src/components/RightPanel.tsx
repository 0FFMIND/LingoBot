import React from 'react';

interface RightPanelProps {
  learningStats?: {
    streakDays?: number;
    completedLessons?: number;
    totalHours?: number;
    vocabularyCount?: number;
  };
  todayProgress?: {
    completionPercent?: number;
    studyMinutes?: number;
    targetMinutes?: number;
    exercisesCompleted?: number;
    targetExercises?: number;
  };
  recommendedTasks?: Array<{
    id: number;
    title: string;
    subtitle: string;
    icon: string;
    completed?: boolean;
  }>;
}

const ProgressRing: React.FC<{ percent: number; size?: number; strokeWidth?: number }> = ({ 
  percent, 
  size = 120, 
  strokeWidth = 8 
}) => {
  const radius = (size - strokeWidth) / 2;
  const circumference = radius * 2 * Math.PI;
  const offset = circumference - (percent / 100) * circumference;

  return (
    <svg width={size} height={size} className="progress-ring">
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        strokeWidth={strokeWidth}
        fill="none"
        stroke="#e5e7eb"
        className="progress-ring-bg"
      />
      <circle
        cx={size / 2}
        cy={size / 2}
        r={radius}
        strokeWidth={strokeWidth}
        fill="none"
        stroke="url(#progressGradient)"
        strokeLinecap="round"
        strokeDasharray={circumference}
        strokeDashoffset={offset}
        className="progress-ring-progress"
      />
      <defs>
        <linearGradient id="progressGradient" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#6366f1" />
          <stop offset="100%" stopColor="#8b5cf6" />
        </linearGradient>
      </defs>
    </svg>
  );
};

const RightPanel: React.FC<RightPanelProps> = ({
  learningStats = {
    streakDays: 12,
    completedLessons: 24,
    totalHours: 28.5,
    vocabularyCount: 532,
  },
  todayProgress = {
    completionPercent: 75,
    studyMinutes: 30,
    targetMinutes: 40,
    exercisesCompleted: 18,
    targetExercises: 20,
  },
  recommendedTasks = [
    {
      id: 1,
      title: '口语每日练习',
      subtitle: '完成1次英语对话',
      icon: '💬',
      completed: false,
    },
    {
      id: 2,
      title: '学习10个新词',
      subtitle: '词汇记忆与复习',
      icon: '📚',
      completed: false,
    },
    {
      id: 3,
      title: '听力训练',
      subtitle: '完成1篇听力练习',
      icon: '🎧',
      completed: false,
    },
  ],
}) => {
  return (
    <div className="right-panel">
      <div className="panel-section progress-section">
        <h3 className="panel-title">今日学习概览</h3>
        <div className="progress-container">
          <div className="progress-ring-container">
            <ProgressRing percent={todayProgress.completionPercent || 0} />
            <div className="progress-percent">
              {todayProgress.completionPercent || 0}%
            </div>
            <div className="progress-label">每日目标完成度</div>
          </div>
          <div className="progress-stats">
            <div className="progress-stat-item">
              <div className="stat-value">
                {todayProgress.studyMinutes || 0}
                <span className="stat-unit">/{todayProgress.targetMinutes || 40} 分钟</span>
              </div>
              <div className="stat-label">学习时长</div>
            </div>
            <div className="progress-stat-item">
              <div className="stat-value">
                {todayProgress.exercisesCompleted || 0}
                <span className="stat-unit">/{todayProgress.targetExercises || 20} 题</span>
              </div>
              <div className="stat-label">练习题完成</div>
            </div>
          </div>
        </div>
      </div>

      <div className="panel-section stats-section">
        <h3 className="panel-title">学习数据</h3>
        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-card-icon">🔥</div>
            <div className="stat-card-value">{learningStats.streakDays || 0}</div>
            <div className="stat-card-label">连续学习</div>
          </div>
          <div className="stat-card">
            <div className="stat-card-icon">📖</div>
            <div className="stat-card-value">{learningStats.completedLessons || 0}</div>
            <div className="stat-card-label">完成课程</div>
          </div>
          <div className="stat-card">
            <div className="stat-card-icon">⏱️</div>
            <div className="stat-card-value">{learningStats.totalHours || 0}</div>
            <div className="stat-card-label">累计时长</div>
          </div>
          <div className="stat-card">
            <div className="stat-card-icon">⭐</div>
            <div className="stat-card-value">{learningStats.vocabularyCount || 0}</div>
            <div className="stat-card-label">词汇掌握</div>
          </div>
        </div>
      </div>

      <div className="panel-section tasks-section">
        <h3 className="panel-title">今日推荐任务</h3>
        <div className="tasks-list">
          {recommendedTasks.map((task) => (
            <div 
              key={task.id} 
              className={`task-item ${task.completed ? 'completed' : ''}`}
            >
              <div className="task-icon">{task.icon}</div>
              <div className="task-content">
                <div className="task-title">{task.title}</div>
                <div className="task-subtitle">{task.subtitle}</div>
              </div>
              <div className="task-arrow">›</div>
            </div>
          ))}
        </div>
      </div>

      <div className="motivation-card">
        <div className="motivation-text">
          每天进步一点点，<br />
          流利英语就在不远处！
        </div>
        <div className="motivation-icon">🚀</div>
      </div>
    </div>
  );
};

export default RightPanel;
