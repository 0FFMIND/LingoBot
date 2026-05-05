# Audio 模块

## 模块概述

Audio 模块负责处理聊天机器人的音频相关功能，包括静态音频资源的访问和音频格式的转换。该模块为用户提供了播放音频（如鸟叫声）的能力，并支持多种音频格式的输入处理。

## 主要功能

- **静态音频服务**：提供音频文件的 HTTP 访问接口
- **音频格式转换**：将多种音频格式转换为模型支持的格式
- **格式检测**：自动识别音频格式类型

## 模块结构

### controller/ 控制器层

#### `AudioController` 音频控制器
- 提供静态音频文件的 REST API
- 支持范围请求（Range Requests）
- 正确设置缓存头和内容类型

### service/ 服务层

#### `AudioConversionService` 音频转换服务
- 音频格式检测与验证
- 使用 JAVE (Java Audio Video Encoder) 进行格式转换
- 支持多种输入格式：webm、opus、mp3、m4a、wav、flac、ogg、aiff
- 目标格式：wav（PCM 16-bit）

## 音频文件服务

### 访问方式
静态音频文件通过以下 URL 模式访问：
```
GET /audio/{filename}
```

### 支持的音频格式
- `.mp3`：audio/mpeg
- `.wav`：audio/wav
- `.ogg`：audio/ogg
- `.m4a`：audio/mp4

### 缓存策略
- 响应头：`Cache-Control: public, max-age=3600`
- 缓存时间：1 小时
- 支持浏览器缓存

### 范围请求支持
- 响应头：`Accept-Ranges: bytes`
- 支持断点续传
- 适用于大文件播放

## 音频格式转换

### 转换流程
1. 检测输入音频格式
2. 如果格式已支持，直接返回
3. 如果需要转换：
   - 将 Base64 数据解码为字节
   - 写入临时文件
   - 使用 JAVE 进行格式转换
   - 读取转换后的文件
   - 重新编码为 Base64
   - 清理临时文件

### 支持的输入格式
| 格式 | 扩展名/标识 | 说明 |
|------|-------------|------|
| WebM | .webm, opus | 常见浏览器录音格式 |
| MP3 | .mp3 | 通用音频格式 |
| M4A/MP4 | .m4a, .mp4 | Apple 格式 |
| WAV | .wav | 无损格式 |
| FLAC | .flac | 无损压缩格式 |
| OGG | .ogg, .oga | 开源格式 |
| AIFF | .aiff | Apple 无损格式 |

### 目标格式
- **WAV (PCM 16-bit)**：大多数 AI 模型支持的标准格式
- 编码器：`pcm_s16le`

## 使用场景

### 1. 播放静态音频
- 鸟叫声工具返回音频 URL
- 前端通过 AudioController 访问音频文件
- 用户可以直接在浏览器中播放

### 2. 语音输入转换
- 用户录制语音（通常为 webm/opus 格式）
- 前端发送 Base64 编码的音频数据
- 后端使用 AudioConversionService 转换为 wav 格式
- 发送给支持音频的 AI 模型（如 Qwen Omni）

## 配置与依赖

### 依赖库
- **JAVE (ws.schild)**：Java 音频视频编码库
- 基于 FFmpeg 的 Java 封装
- 支持多种音频格式转换

### 临时文件管理
- 转换过程中创建临时文件
- 转换完成后自动清理
- 使用独立的临时目录
