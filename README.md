# 📱 麻将助手 (Mahjong Assistant)

基于手机摄像头 + 本地 AI 的实时麻将辅助系统。拍摄手牌照片，自动识别牌面并计算最优切牌建议。

## ✨ 核心特性

- **📷 拍照识别** — 手机对准手牌拍照，YOLOv8 自动识别所有牌面
- **🧠 牌效计算** — 实时计算向听数、进张数，推荐最优切牌
- **🎤 语音交互** — 说出场况信息（如"上家打了五万"），AI 自动追踪可见牌
- **🔒 完全本地化** — ONNX Runtime 本地推理，无需上传图片到第三方服务

## 🏗 项目架构

```
┌──────────────────┐     HTTP/REST     ┌──────────────────────┐
│  Android 客户端   │ ◄──────────────► │   Python 服务端       │
│                  │                   │                      │
│  · 手机摄像头拍照  │   图片/音频上传    │  · YOLOv8 牌面识别    │
│  · 触控按钮操作    │ ───────────────► │  · 牌效引擎计算       │
│  · 结果展示       │                   │  · 语音转文字 (STT)   │
│  · 语音录制       │  ◄─────────────── │  · LLM 意图分析       │
│                  │   分析结果返回      │  · 对局状态追踪       │
└──────────────────┘                   └──────────────────────┘
     app/                                    server/
```

---

## 🚀 快速开始

### 前置要求

| 工具 | 版本 | 用途 |
|------|------|------|
| Android Studio | Ladybug+ | 编译客户端 |
| JDK | 17+ | Android 编译环境 |
| Python | 3.9+ | 服务端运行 |
| Docker (可选) | 最新版 | 一键部署服务端 |
| LLM 服务 (可选) | — | 语音意图分析 ([Ollama](https://ollama.com/) / [LM Studio](https://lmstudio.ai/)) |

### 1. 启动服务端

#### 方式 A：Docker 部署（推荐）

```bash
git clone https://github.com/Frrrrrranz/AR-Mahjong-Assistant-preview.git
cd AR-Mahjong-Assistant-preview

# 配置环境变量
cd server
cp .env.example .env
# 编辑 .env 设置 LLM 地址等参数（见下方配置说明）
cd ..

# 启动
docker-compose up -d --build
```

#### 方式 B：本地运行

```bash
cd server
python -m venv venv
# Windows:
venv\Scripts\activate
# macOS/Linux:
# source venv/bin/activate

pip install -r requirements.txt
python main.py
```

服务将运行在 `http://localhost:8000`。

### 2. 配置客户端

编辑 `app/src/main/java/com/example/ai_assist/AppConfig.kt`：

```kotlin
object AppConfig {
    const val SERVER_BASE_URL = "http://你的电脑IP:8000/"  // ← 改为局域网 IP
    const val USE_COLOR_FONT = true                       // 彩色麻将字体
    const val FONT_SCALE_COLOR = 1.8f                     // 彩色字体缩放
    const val FONT_SCALE_DEFAULT = 2.5f                   // 默认字体缩放
}
```

> ⚠️ 手机和服务端必须在**同一局域网**下。

### 3. 编译安装

1. 用 Android Studio 打开项目根目录
2. 等待 Gradle Sync 完成
3. USB 连接 Android 手机（需开启 USB 调试）
4. 点击 **Run ▶** 安装到手机

---

## 📖 使用说明

### 操作流程

```
开始对局 → 📷 拍照分析 → 对准手牌拍照 → 确认发送
                ↓
    ┌─────────────────────────────┐
    │  🀄 手牌: 🀇🀈🀉🀊🀋🀌🀍...  │
    │  📋 副露: 🀐🀐🀐             │
    │  💡 建议: 切 🀊，向听数 1    │
    │         进张: 🀆🀅 共 6 张    │
    └─────────────────────────────┘
                ↓
    继续拍照 / 🎤 语音报告场况 / 结束对局
```

### 按钮说明

| 状态 | 按钮 | 操作 |
|------|------|------|
| 等待中 | `开始对局` | 创建新对局会话 |
| 对局中 | `📷 拍照分析` | 打开相机拍摄手牌 |
| 对局中 | `🎤 按住说话` | 按住录音，松开自动上传 |
| 对局中 | `结束对局` | 结束当前对局 |
| 拍照中 | **快门按钮** | 画面中央白色圆形按钮 |
| 拍照中 | `✕ 返回` | 关闭相机 |
| 确认照片 | `发送分析` | 将照片发送到服务端分析 |
| 确认照片 | `重拍` | 丢弃照片重新拍摄 |

### 拍摄技巧

- 将手牌平放在桌面上，手机从上方俯拍
- 确保手牌位于画面**下半部分**（辅助线以下）
- 光线充足时识别效果最佳
- 副露牌组放在手牌下方（画面底部）

---

## ⚙️ 服务端配置

编辑 `server/.env`：

```env
# LLM 配置 (语音意图分析)
LLM_BASE_URL=http://host.docker.internal:1234/v1   # LM Studio 默认地址
LLM_API_KEY=lm-studio                               # API 密钥
LLM_MODEL=qwen/qwen3-4b-2507                        # 模型名称

# YOLO 配置 (牌面识别)
YOLO_CONF_THRESHOLD=0.54                             # 置信度阈值
YOLO_IOU_THRESHOLD=0.85                              # 重叠阈值
```

### YOLO 调参工具

服务端运行后，访问 `http://localhost:8000/static/yolo_debug.html` 可在线调试识别参数。

---

## 📂 目录结构

```
AR-Mahjong-Assistant-preview/
├── app/                              # Android 客户端
│   └── src/main/java/.../ai_assist/
│       ├── MainActivity.kt           # 主界面 (相机、状态管理)
│       ├── AppConfig.kt              # 全局配置
│       ├── model/                    # 数据模型
│       ├── repository/               # 数据仓库层
│       ├── service/                  # API 接口定义
│       ├── utils/                    # 工具类 (录音、牌面映射)
│       └── viewmodel/               # MVVM ViewModel
│
├── server/                           # Python 服务端
│   ├── main.py                       # FastAPI 入口
│   ├── vision_service.py             # YOLO 视觉识别
│   ├── yolo_inference.py             # ONNX 推理引擎
│   ├── efficiency_engine.py          # 牌效计算引擎
│   ├── mahjong_state_tracker.py      # 对局状态追踪
│   ├── llm_service.py                # LLM 意图分析
│   ├── stt_service.py                # 语音转文字
│   ├── config.py                     # 配置管理
│   ├── models/yolo/                  # YOLO ONNX 模型文件
│   └── tests/                        # 测试用例
│
└── docker-compose.yml                # Docker 部署配置
```

---

## 🧪 测试

```bash
cd server
pytest tests/ -v
```

---

## 🙏 致谢

- [Jon Chan](https://universe.roboflow.com/jon-chan-gnsoa/mahjong-baq4s) — Mahjong Dataset (Roboflow Universe)
- [FluffyStuff/riichi-mahjong-tiles](https://github.com/FluffyStuff/riichi-mahjong-tiles) — 麻将 PNG 素材
- [SYSTRAN/faster-whisper](https://github.com/SYSTRAN/faster-whisper) — 离线语音转文字
- [googlefonts/nanoemoji](https://github.com/googlefonts/nanoemoji) — 彩色字体工具

## 📄 许可

本项目基于 [LYiHub/AR-Mahjong-Assistant-preview](https://github.com/LYiHub/AR-Mahjong-Assistant-preview) 修改，适配手机摄像头使用场景。
