# Media Module

## 模块概述

媒体处理模块负责音频相关的功能，包括文本转语音（TTS）和音频格式转换。

主要职责：

- 文本转语音（TTS）：将英语单词转换为发音音频，支持美式和英式发音
- 音频格式转换：将不支持的音频格式（如 webm、opus 等）转换为标准 wav 格式

## API / 调用流转

TTS 接口统一挂载在 `/api/tts` 路径下，直接返回音频二进制数据。

```text
请求
  └─ GET /api/tts/word → 获取单词发音音频
       └─ TtsController.getWordPronunciation()
            ├─ 解析参数（word/voiceType）
            ├─ voiceType 默认 us，支持 us/uk
            ├─ 调用 TextToSpeechService.getWordPronunciation()
            │    ├─ 检查内存缓存（7天有效期）
            │    ├─ 缓存命中 → 直接返回
            │    └─ 缓存未命中 → 请求有道词典 TTS API
            │         ├─ 最多重试 3 次
            │         ├─ 成功 → 写入缓存
            │         └─ 失败 → 抛出 MediaException
            ├─ 设置 HTTP 响应头（audio/mpeg，7天缓存）
            └─ 返回 MP3 格式音频数据
```

## TTS Workflow

文本转语音使用有道词典 TTS API，内置 7 天内存缓存避免重复请求。

```text
GET /api/tts/word?word=hello&voiceType=us
  └─ TtsController.getWordPronunciation()
       ├─ 解析 word 参数（不能为空）
       ├─ 解析 voiceType 参数（默认 US）
       ├─ 验证 voiceType 合法性（US/UK）
       │    └─ 非法 → 使用默认 US
       ├─ 调用 TextToSpeechService.getWordPronunciation(word, type)
       │    ├─ 构建缓存 key（word + "-" + type）
       │    ├─ 检查缓存
       │    │    └─ 命中且未过期 → 返回缓存数据
       │    ├─ URL 编码单词
       │    ├─ 构建有道 API URL：https://dict.youdao.com/dictvoice?type={type}&audio={word}
       │    ├─ 发起 HTTP GET 请求（最多重试 3 次）
       │    │    ├─ 超时设置：连接 8s，读取 8s
       │    │    ├─ 重试间隔：500ms / 1000ms / 2000ms
       │    │    ├─ 设置 User-Agent、Referer 等请求头
       │    │    ├─ 成功 → 读取音频流
       │    │    └─ 失败 → 等待后重试或抛出异常
       │    ├─ 写入缓存（音频大小 > 0 时）
       │    └─ 返回音频 byte[]
       ├─ 设置响应头：
       │    ├─ Content-Type: audio/mpeg
       │    ├─ Content-Length: {音频大小}
       │    └─ Cache-Control: max-age=7 天
       └─ 返回 ResponseEntity<byte[]>
```

### 语音类型

| 类型 | 说明 | 有道 API type 参数 |
|------|------|-------------------|
| US | 美式发音 | 0 |
| UK | 英式发音 | 1 |

## 音频转换 Workflow

音频格式转换使用 ws.schild.jave 库，将不支持的格式转换为标准 wav。

```text
AudioConversionService.convertIfNeeded(base64Audio, sourceFormat)
  ├─ 检查是否需要转换（SUPPORTED_FORMATS: mp3, flac, m4a, wav, ogg）
  │    └─ 无需转换 → 直接返回原始数据
  ├─ Base64 解码音频数据
  ├─ 创建临时源文件（audio_source_xxx）
  ├─ 创建临时目标文件（audio_target_xxx.wav）
  ├─ 调用 convertAudio() 执行转换
  │    ├─ 创建 MultimediaObject（源文件）
  │    ├─ 设置 AudioAttributes（编码器：pcm_s16le）
  │    ├─ 设置 EncodingAttributes（输出格式：wav）
  │    └─ Encoder.encode() 执行编码
  ├─ 读取转换后的文件并 Base64 编码
  ├─ 清理临时文件（包括父目录）
  └─ 返回 ConversionResult（base64Audio + format）
```

### 支持的格式

| 格式 | 编码器 | 说明 |
|------|--------|------|
| wav | pcm_s16le | 目标格式，默认输出 |
| mp3 | libmp3lame | 直接支持，无需转换 |
| flac | flac | 直接支持，无需转换 |
| m4a | aac | 直接支持，无需转换 |
| ogg | libvorbis | 直接支持，无需转换 |
| webm/opus | - | 需要转换为 wav |

## 重要类

| 类 | 作用 |
|----|------|
| `TtsController` | TTS REST 入口：获取单词发音 |
| `TextToSpeechService` | 文本转语音服务，含缓存管理和重试机制 |
| `AudioConversionService` | 音频格式转换服务，使用 JAVE 库 |

## 关键约定

- **缓存策略**：TTS 发音使用内存缓存，有效期 7 天，HTTP 响应头也设置 7 天缓存
- **重试机制**：TTS API 请求失败自动重试最多 3 次，重试间隔递增
- **临时文件**：音频转换使用临时文件，完成后自动清理（包括空目录）
- **异常处理**：所有媒体相关异常抛出 `MediaException`，包含具体错误信息
- **格式规范化**：音频格式字符串统一规范化处理，兼容 MIME 类型和描述字符串
