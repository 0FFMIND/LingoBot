import React from 'react';

interface CircularProgressProps {
  percentage: number;
  size?: number;
  strokeWidth?: number;
  showPercentage?: boolean;
  onDoubleClick?: () => void;
}

const CircularProgress: React.FC<CircularProgressProps> = ({
  percentage,
  size = 24,
  strokeWidth = 3,
  showPercentage = true,
  onDoubleClick,
}) => {
  const validPercentage = typeof percentage === 'number' && isFinite(percentage) ? percentage : 0;
  const clampedPercentage = Math.max(0, Math.min(100, validPercentage));

  const radius = (size - strokeWidth) / 2;
  const circumference = radius * 2 * Math.PI;
  const offset = circumference - (clampedPercentage / 100) * circumference;

  const getColor = () => {
    if (clampedPercentage >= 90) return '#e74c3c';
    if (clampedPercentage >= 70) return '#f39c12';
    return '#3498db';
  };

  return (
    <div
      className="circular-progress"
      style={{
        position: 'relative',
        width: size,
        height: size,
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        cursor: onDoubleClick ? 'pointer' : 'default',
      }}
      onDoubleClick={onDoubleClick}
      title={onDoubleClick ? '双击手动压缩上下文' : undefined}
    >
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="#e0e0e0"
          strokeWidth={strokeWidth}
        />
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={getColor()}
          strokeWidth={strokeWidth}
          strokeLinecap="round"
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
          style={{
            transition: 'stroke-dashoffset 0.35s ease-in-out',
          }}
        />
      </svg>
      {showPercentage && (
        <span
          style={{
            position: 'absolute',
            fontSize: Math.max(size * 0.25, 10),
            fontWeight: 'bold',
            color: '#333',
          }}
        >
          {Math.round(clampedPercentage)}%
        </span>
      )}
    </div>
  );
};

export default CircularProgress;
