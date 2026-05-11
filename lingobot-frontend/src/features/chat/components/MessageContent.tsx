import React from 'react'
import { MessageType, AudioAttachment } from '../../../types'
import AudioPlayer from '../../../components/media/AudioPlayer'
import UserAudioMessage from '../../../components/media/UserAudioMessage'
import UserImageMessage from '../../../components/media/UserImageMessage'
import VocabularyFlashcard, { isVocabularyJson } from '../../vocabulary/components/VocabularyFlashcard'

interface ParsedMessage {
  text: string
  audioAttachments: AudioAttachment[]
}

const normalizeAudioUrl = (url: string): string => {
  if (!url) return url
  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url
  }
  if (url.startsWith('/')) {
    return url
  }
  return `/${url}`
}

const parseMessageContent = (content: string): ParsedMessage => {
  const result: ParsedMessage = {
    text: content,
    audioAttachments: [],
  }

  const urlPattern = /(?:https?:\/\/[^\s"']+|\/[^\s"']*)\.(mp3|wav|ogg|m4a)(\?[^\s"']*)?/gi
  const matches = content.match(urlPattern)

  if (matches) {
    const uniqueUrls = [...new Set(matches)]
    uniqueUrls.forEach((url, index) => {
      const normalizedUrl = normalizeAudioUrl(url)
      const existingIndex = result.audioAttachments.findIndex(a => a.url === normalizedUrl)
      if (existingIndex === -1) {
        result.audioAttachments.push({
          id: `audio-${index}`,
          url: normalizedUrl,
          title: `音频 ${index + 1}`,
          type: 'audio/mp3',
        })
      }
    })
  }

  try {
    const jsonPattern = /\{(?:[^{}]|\{[^{}]*\})*"audio_url"(?:[^{}]|\{[^{}]*\})*\}/g
    const jsonMatches = content.match(jsonPattern)

    if (jsonMatches) {
      jsonMatches.forEach((jsonStr, jsonIndex) => {
        try {
          const parsed = JSON.parse(jsonStr) as {
            audio_url?: string
            bird_name?: string
            description?: string
            message?: string
            duration_seconds?: number
          }

          if (parsed.audio_url) {
            const normalizedUrl = normalizeAudioUrl(parsed.audio_url)
            const existingIndex = result.audioAttachments.findIndex(a => a.url === normalizedUrl)
            if (existingIndex === -1) {
              result.audioAttachments.push({
                id: `audio-json-${jsonIndex}`,
                url: normalizedUrl,
                title: parsed.bird_name || `音频 ${result.audioAttachments.length + 1}`,
                type: 'audio/mp3',
                description: parsed.description || parsed.message,
                durationSeconds: parsed.duration_seconds,
              })
            }
          }
        } catch (e) {
          console.warn('解析音频JSON失败:', e)
        }
      })
    }
  } catch (e) {
    console.warn('解析消息内容时出错:', e)
  }

  return result
}

export interface MessageContentProps {
  content: string
  messageType?: MessageType
  audioData?: string
  audioFormat?: string
  audioDuration?: number
  imageData?: string
  imageFormat?: string
  onSendWithIntent?: (content: string, intent: string, currentWord: string) => void
}

const MessageContent: React.FC<MessageContentProps> = ({
  content,
  messageType,
  audioData,
  audioFormat,
  audioDuration,
  imageData,
  imageFormat,
  onSendWithIntent,
}) => {
  if (messageType === 'audio' && audioData) {
    return (
      <div className="message-content-wrapper-english">
        <UserAudioMessage
          audioData={audioData}
          audioFormat={audioFormat || 'webm'}
          audioDuration={audioDuration}
        />
        {content && <div className="message-text-english whitespace-pre-wrap">{content}</div>}
      </div>
    )
  }

  if (messageType === 'image' && imageData) {
    return (
      <div className="message-content-wrapper-english">
        <UserImageMessage
          imageData={imageData}
          imageFormat={imageFormat}
        />
        {content && <div className="message-text-english whitespace-pre-wrap">{content}</div>}
      </div>
    )
  }

  const vocabData = isVocabularyJson(content)
  if (vocabData) {
    return <VocabularyFlashcard data={vocabData} onSendWithIntent={onSendWithIntent} />
  }

  const parsed = parseMessageContent(content)

  if (parsed.audioAttachments.length === 0) {
    return <div className="message-text-english whitespace-pre-wrap">{content}</div>
  }

  let textWithoutAudio = content
  parsed.audioAttachments.forEach(attachment => {
    textWithoutAudio = textWithoutAudio.replace(attachment.url, '')
  })

  const jsonPattern = /\{[\s\S]*?"audio_url"[\s\S]*?\}/g
  textWithoutAudio = textWithoutAudio.replace(jsonPattern, '').trim()

  return (
    <div className="message-content-wrapper-english">
      {textWithoutAudio && (
        <div className="message-text-english whitespace-pre-wrap">{textWithoutAudio}</div>
      )}
      <div className="audio-attachments-english">
        {parsed.audioAttachments.map(attachment => (
          <AudioPlayer key={attachment.id} attachment={attachment} />
        ))}
      </div>
    </div>
  )
}

export default MessageContent
export { normalizeAudioUrl, parseMessageContent }
