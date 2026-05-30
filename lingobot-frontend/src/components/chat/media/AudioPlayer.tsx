import React, { useState, useEffect, useRef } from 'react';
import { AudioAttachment } from '../../../types';

export interface AudioPlayerProps {
  attachment: AudioAttachment;
}

const AudioPlayer: React.FC<AudioPlayerProps> = ({ attachment }) => {
  const [isPlaying, setIsPlaying] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const audioRef = useRef<HTMLAudioElement>(null);

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
  }, [attachment.url]);

  const togglePlay = () => {
    const audio = audioRef.current;
    if (!audio) return;

    if (isPlaying) {
      audio.pause();
    } else {
      audio.play().catch(e => {
        setError(`播放失败: ${e.message}`);
      });
    }
    setIsPlaying(!isPlaying);
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
    <div className="audio-player-english">
      <audio ref={audioRef} src={attachment.url} preload="metadata" />
      <div className="audio-player-header-english">
        <div className="audio-icon-english">🔊</div>
        <div className="audio-info-english">
          <div className="audio-title-english">{attachment.title}</div>
          {attachment.description && (
            <div className="audio-desc-english">{attachment.description}</div>
          )}
        </div>
      </div>
      <div className="audio-controls-english">
        <button
          className="play-button-english"
          onClick={togglePlay}
          disabled={isLoading || !!error}
        >
          {isLoading ? '⏳' : (isPlaying ? '⏸️' : '▶️')}
        </button>
        <div className="progress-container-english" onClick={handleProgressClick}>
          <div className="progress-bar-english">
            <div
              className="progress-fill-english"
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
        <div className="time-display-english">
          {formatTime((progress / 100) * (duration || 0))} / {formatTime(duration)}
        </div>
      </div>
    </div>
  );
};

export default AudioPlayer;
