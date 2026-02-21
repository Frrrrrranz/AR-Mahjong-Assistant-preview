# 📱 麻将助手 Android 版 (Mahjong Assistant Android)

> 基于 [LYiHub/AR-Mahjong-Assistant-preview](https://github.com/LYiHub/AR-Mahjong-Assistant-preview) 修改，将 AR 眼镜版适配为**普通安卓手机**可用的版本。没有 AR 眼镜也能体验 AI 麻将助手！

## ✨ 功能

- **📷 拍照识别** — 对准手牌拍照，YOLOv8 自动识别牌面
- **🧠 牌效计算** — 计算向听数、进张数，推荐最优切牌
- **🎤 语音交互** — 说出场况信息，AI 自动追踪可见牌
- **🔒 本地推理** — ONNX Runtime 本地 YOLO 推理，隐私安全

## 🏗 架构

```
Android 客户端 (app/)          Python 服务端 (server/)
───────────────────           ────────────────────────
· 手机摄像头拍照    ──HTTP──►  · YOLOv8 牌面识别 (ONNX)
· 触控按钮操作                 · 牌效引擎计算
· 语音录制        ◄──HTTP──   · 语音转文字 (DashScope)
· 结果展示                    · LLM 意图分析
```

## 🚀 快速开始

### 1. 启动服务端

```bash
git clone https://github.com/Frrrrrranz/Mahjong-Assistant-Android.git
cd Mahjong-Assistant-Android/server

python -m venv venv
venv\Scripts\activate        # Windows
# source venv/bin/activate   # macOS/Linux

pip install -r requirements.txt

cp .env.example .env         # 编辑 .env 配置 LLM API Key
python main.py               # 运行在 http://localhost:8000
```

或使用 Docker：
```bash
docker-compose up -d --build
```

### 2. 配置客户端

编辑 `app/.../AppConfig.kt`，将 `SERVER_BASE_URL` 改为你电脑的局域网 IP：

```kotlin
const val SERVER_BASE_URL = "http://192.168.x.x:8000/"
```

> ⚠️ 手机和服务端必须在**同一局域网**下。

### 3. 编译安装

Android Studio 打开项目 → Gradle Sync → USB 连接手机 → Run ▶

## 📖 使用流程

```
开始对局 → 📷 拍照分析 → 确认发送 → 查看建议 → 继续拍照 / 🎤 语音 / 结束对局
```

### 拍摄技巧

- 手牌平放桌面，手机从上方俯拍
- 光线充足时识别效果最佳

## ⚙️ 服务端配置 (.env)

```env
LLM_API_KEY=your-api-key                         # LLM API 密钥
LLM_BASE_URL=https://your-llm-endpoint/v1        # LLM 服务地址
LLM_MODEL=qwen3.5-plus                           # 模型名称
```

YOLO 调参工具：启动服务后访问 `http://localhost:8000/static/yolo_debug.html`

## 🙏 致谢

- [LYiHub/AR-Mahjong-Assistant-preview](https://github.com/LYiHub/AR-Mahjong-Assistant-preview) — 原始项目
- [Jon Chan](https://universe.roboflow.com/jon-chan-gnsoa/mahjong-baq4s) — Mahjong Dataset
- [FluffyStuff/riichi-mahjong-tiles](https://github.com/FluffyStuff/riichi-mahjong-tiles) — 麻将素材
