import React, { useState, useRef, useEffect, useCallback } from 'react';

interface VoiceRecorderProps {
  onRecordingComplete: (audioData: string, audioFormat: string, duration: number) => void;
  onCancel: () => void;
  autoStart?: boolean;
  disabled?: boolean;
}

const VoiceRecorder: React.FC<VoiceRecorderProps> = ({
  onRecordingComplete,
  onCancel,
  autoStart = true,
  disabled = false,
}) => {
  const [isRecording, setIsRecording] = useState(false);
  const [duration, setDuration] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [waveformLevels, setWaveformLevels] = useState<number[]>(Array(20).fill(0.2));

  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const audioChunksRef = useRef<Blob[]>([]);
  const timerRef = useRef<number | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const audioContextRef = useRef<AudioContext | null>(null);
  const analyserRef = useRef<AnalyserNode | null>(null);
  const animationFrameRef = useRef<number | null>(null);
  const startTimeRef = useRef<number>(0);
  const mimeTypeRef = useRef<string>('');
  const isInitialized = useRef(false);

  const updateWaveform = useCallback(() => {
    if (!analyserRef.current) return;

    const analyser = analyserRef.current;
    const bufferLength = analyser.frequencyBinCount;
    const dataArray = new Uint8Array(bufferLength);

    const animate = () => {
      analyser.getByteFrequencyData(dataArray);
      const sampleCount = 20;
      const newLevels: number[] = [];
      const step = Math.floor(bufferLength / sampleCount);

      for (let i = 0; i < sampleCount; i++) {
        const start = i * step;
        const end = start + step;
        let sum = 0;
        for (let j = start; j < end; j++) {
          sum += dataArray[j];
        }
        const avg = sum / step;
        newLevels.push(Math.max(0.2, avg / 255));
      }

      setWaveformLevels(newLevels);
      animationFrameRef.current = requestAnimationFrame(animate);
    };

    animate();
  }, []);

  const startRecording = useCallback(async () => {
    if (disabled || isRecording || isInitialized.current) return;
    isInitialized.current = true;

    setError(null);

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: {
          sampleRate: 44100,
          channelCount: 1,
          echoCancellation: true,
          noiseSuppression: true,
        },
      });
      streamRef.current = stream;

      const audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
      audioContextRef.current = audioContext;

      const source = audioContext.createMediaStreamSource(stream);
      const analyser = audioContext.createAnalyser();
      analyser.fftSize = 256;
      source.connect(analyser);
      analyserRef.current = analyser;

      let mimeType = 'audio/webm;codecs=opus';
      if (!MediaRecorder.isTypeSupported(mimeType)) {
        mimeType = 'audio/webm';
      }
      if (!MediaRecorder.isTypeSupported(mimeType)) {
        mimeType = 'audio/mp4';
      }
      mimeTypeRef.current = mimeType;

      const mediaRecorder = new MediaRecorder(stream, {
        mimeType,
        audioBitsPerSecond: 128000,
      });

      mediaRecorderRef.current = mediaRecorder;
      audioChunksRef.current = [];

      mediaRecorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          audioChunksRef.current.push(event.data);
        }
      };

      mediaRecorder.onstop = () => {
        if (animationFrameRef.current) {
          cancelAnimationFrame(animationFrameRef.current);
          animationFrameRef.current = null;
        }
        if (audioContextRef.current) {
          audioContextRef.current.close();
          audioContextRef.current = null;
        }
        if (streamRef.current) {
          streamRef.current.getTracks().forEach(track => track.stop());
          streamRef.current = null;
        }
      };

      mediaRecorder.start(100);
      setIsRecording(true);
      setDuration(0);
      startTimeRef.current = Date.now();
      setWaveformLevels(Array(20).fill(0.2));

      updateWaveform();

      timerRef.current = window.setInterval(() => {
        const elapsed = Math.floor((Date.now() - startTimeRef.current) / 1000);
        setDuration(elapsed);

        if (elapsed >= 60) {
          handleStopAndSend();
        }
      }, 1000);

    } catch (err) {
      console.error('录音失败:', err);
      setError('无法访问麦克风，请检查权限设置');
      isInitialized.current = false;
    }
  }, [disabled, isRecording, updateWaveform]);

  const stopRecordingInternal = useCallback(() => {
    if (mediaRecorderRef.current && isRecording) {
      mediaRecorderRef.current.stop();
      setIsRecording(false);

      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
        animationFrameRef.current = null;
      }
    }
  }, [isRecording]);

  const handleStopAndSend = useCallback(() => {
    if (!mediaRecorderRef.current || !isRecording) return;

    const finalDuration = duration;

    const originalOnStop = mediaRecorderRef.current.onstop;
    mediaRecorderRef.current.onstop = () => {
      if (originalOnStop) {
        (originalOnStop as () => void)();
      }

      const blob = new Blob(audioChunksRef.current, { type: mimeTypeRef.current });

      if (blob.size === 0) {
        setError('音频数据为空，请重新录制');
        return;
      }

      const reader = new FileReader();
      reader.onload = () => {
        const result = reader.result as string;
        const commaIndex = result.indexOf(',');
        
        if (commaIndex === -1 || commaIndex === result.length - 1) {
          setError('音频数据格式错误，请重新录制');
          return;
        }
        
        const base64 = result.substring(commaIndex + 1);
        
        if (!base64 || base64.trim().length === 0) {
          setError('音频数据为空，请重新录制');
          return;
        }
        
        const format = mimeTypeRef.current.split('/')[1] || 'webm';
        onRecordingComplete(base64, format, finalDuration);
      };
      reader.onerror = () => {
        setError('音频处理失败');
      };
      reader.readAsDataURL(blob);
    };

    stopRecordingInternal();
  }, [duration, isRecording, onRecordingComplete, stopRecordingInternal]);

  const handleCancel = useCallback(() => {
    if (isRecording) {
      stopRecordingInternal();
    }

    if (animationFrameRef.current) {
      cancelAnimationFrame(animationFrameRef.current);
      animationFrameRef.current = null;
    }
    if (audioContextRef.current) {
      audioContextRef.current.close();
      audioContextRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach(track => track.stop());
      streamRef.current = null;
    }

    setWaveformLevels(Array(20).fill(0.2));
    setDuration(0);
    setError(null);
    isInitialized.current = false;
    onCancel();
  }, [isRecording, onCancel, stopRecordingInternal]);

  const formatDuration = (seconds: number): string => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  useEffect(() => {
    if (autoStart && !isInitialized.current && !isRecording && !disabled) {
      startRecording();
    }
  }, [autoStart, startRecording, isRecording, disabled]);

  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
      if (animationFrameRef.current) {
        cancelAnimationFrame(animationFrameRef.current);
      }
      if (audioContextRef.current) {
        audioContextRef.current.close();
      }
      if (streamRef.current) {
        streamRef.current.getTracks().forEach(track => track.stop());
      }
    };
  }, []);

  if (!isRecording) {
    return null;
  }

  return (
    <div className="voice-recording-bar">
      <button
        className="voice-cancel-btn"
        onClick={handleCancel}
        title="取消录音"
      >
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <line x1="18" y1="6" x2="6" y2="18"></line>
          <line x1="6" y1="6" x2="18" y2="18"></line>
        </svg>
      </button>

      <div className="waveform-container">
        {waveformLevels.map((level, index) => (
          <div
            key={index}
            className="waveform-bar"
            style={{
              height: `${Math.max(4, level * 32)}px`,
              opacity: 0.5 + level * 0.5,
            }}
          />
        ))}
      </div>

      <span className="recording-duration">{formatDuration(duration)}</span>

      <button
        className="voice-send-btn"
        onClick={handleStopAndSend}
        title="发送语音"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          <polyline points="20 6 9 17 4 12"></polyline>
        </svg>
      </button>

      {error && (
        <div className="recording-error-inline">
          {error}
        </div>
      )}
    </div>
  );
};

export default VoiceRecorder;
