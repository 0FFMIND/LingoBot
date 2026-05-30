import React, { useState, useEffect, useRef } from 'react';

export interface UserAudioMessageProps {
  audioData: string;
  audioFormat: string;
  audioDuration?: number;
}

const UserAudioMessage: React.FC<UserAudioMessageProps> = ({ 
  audioData, 
  audioFormat, 
  audioDuration 
}) => {
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(audioDuration || 0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const audioRef = useRef<HTMLAudioElement>(null);

  const audioUrl = `data:audio/${audioFormat};base64,${audioData}`;

  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;

    setIsPlaying(false);
    setProgress(0);
    setIsLoading(true);
    setError(null);

    const handleTimeUpdate = () => {
      if (audio.duration) {
        setProgress((audio.currentTime / audio.duration) * 100);
      }
    };

    const handleLoadedMetadata = () => {
      setDuration(audio.duration);
      setIsLoading(false);
      setError(null);
    };

    const handleEnded = () => {
      setIsPlaying(false);
      setProgress(0);
    };

    const handleError = () => {
      setError('音频加载失败');
      setIsLoading(false);
    };

    audio.addEventListener('timeupdate', handleTimeUpdate);
    audio.addEventListener('loadedmetadata', handleLoadedMetadata);
    audio.addEventListener('ended', handleEnded);
    audio.addEventListener('error', handleError);

    return () => {
      audio.removeEventListener('timeupdate', handleTimeUpdate);
      audio.removeEventListener('loadedmetadata', handleLoadedMetadata);
      audio.removeEventListener('ended', handleEnded);
      audio.removeEventListener('error', handleError);
    };
  }, [audioUrl]);

  const togglePlay = () => {
    const audio = audioRef.current;
    if (!audio || isLoading || !!error) return;

    if (isPlaying) {
      audio.pause();
      setIsPlaying(false);
    } else {
      audio.play().catch(e => {
        setError(`播放失败: ${e.message}`);
        setIsPlaying(false);
      });
      setIsPlaying(true);
    }
  };

  const handleProgressClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const audio = audioRef.current;
    if (!audio || !audio.duration) return;

    const rect = e.currentTarget.getBoundingClientRect();
    const percent = (e.clientX - rect.left) / rect.width;
    audio.currentTime = percent * audio.duration;
    setProgress(percent * 100);
  };

  const formatTime = (seconds: number): string => {
    if (!seconds || !isFinite(seconds)) return '0:00';
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <div className="user-audio-message-english">
      <audio ref={audioRef} src={audioUrl} preload="metadata" />
      <div className="audio-bubble-english">
        <button
          className="audio-play-btn-english"
          onClick={togglePlay}
          title={isPlaying ? '暂停' : '播放'}
          disabled={isLoading || !!error}
        >
          {isLoading ? '⏳' : error ? '❌' : (isPlaying ? '⏸️' : '▶️')}
        </button>
        <div
          className="audio-progress-container-english"
          onClick={handleProgressClick}
        >
          <div className="audio-progress-bar-english">
            <div
              className="audio-progress-fill-english"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
        <span className="audio-duration-english">{formatTime(duration)}</span>
      </div>
      {error && (
        <div className="audio-error-message-english" style={{ color: '#ef4444', fontSize: '12px', marginTop: '4px' }}>
          {error}
        </div>
      )}
    </div>
  );
};

export default UserAudioMessage;
