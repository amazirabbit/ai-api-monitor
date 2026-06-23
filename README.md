# AI API Monitor

<div align="center">
  <img src="ic_preview_rounded.png" alt="AI API Monitor" width="120">
  <h3>AI API 余额监控 Android 应用</h3>
  <p>实时监控多个 AI 平台的 API 余额，支持桌面小组件和消费统计</p>
</div>

---

## ✨ 功能特性

### 📊 多平台支持
- **OpenAI** - 余额查询
- **DeepSeek** - 余额查询
- **OpenRouter** - 余额查询
- **Kimi (月之暗面)** - 余额查询

### 📱 桌面小组件
- **2×1 小组件** - 总余额 + 健康指示灯
- **4×2 中小组件** - 总余额 + 各账号余额列表
- **4×3 大小组件** - 总余额 + 健康环形图 + 账号网格 + 趋势图

### 📈 消费统计
- 累计消费追踪
- 本月/今日消费统计
- 日均消费计算
- 可用天数预测
- 7天/30天趋势图表

### 🔔 智能通知
- 余额不足提醒
- 三级告警阈值（警告/严重/紧急）
- 6小时防重复通知

### 🎨 UI 设计
- Material Design 3 风格
- 深色主题
- 流畅动画效果
- 交互式图表（触摸查看金额）

---

## 🛠️ 技术栈

| 技术 | 用途 |
|------|------|
| **Kotlin** | 主要开发语言 |
| **Jetpack Compose** | 声明式 UI |
| **Material 3** | 设计系统 |
| **Room** | 本地数据库 |
| **Retrofit + OkHttp** | 网络请求 |
| **Hilt** | 依赖注入 |
| **WorkManager** | 后台任务 |
| **Glance** | 桌面小组件 |
| **Coroutines + Flow** | 异步编程 |

---

## 📦 项目结构

```
app/src/main/java/com/yzarc/aiapimonitor/
├── data/
│   ├── api/           # API 接口定义
│   ├── db/            # Room 数据库
│   └── repository/    # 数据仓库层
├── di/                # Hilt 依赖注入
├── model/             # 数据模型
├── service/           # 后台服务和 Widget
└── ui/
    ├── account/       # 账号管理页面
    ├── charts/        # 图表页面
    ├── home/          # 首页
    ├── settings/      # 设置页面
    └── theme/         # 主题配置
```

---

## 🚀 快速开始

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高版本
- JDK 17
- Android SDK 34+

### 安装步骤

1. **克隆项目**
   ```bash
   git clone https://github.com/amazirabbit/ai-api-monitor.git
   cd ai-api-monitor
   ```

2. **打开项目**
   用 Android Studio 打开项目

3. **同步 Gradle**
   等待 Gradle 同步完成

4. **运行项目**
   连接 Android 设备或启动模拟器，点击运行

---

## 📝 更新日志

### v1.5.0
- ✨ 新增 3 种尺寸的桌面小组件
- ✨ 新增交互式图表（触摸查看金额）
- 🎨 优化 UI 设计（Material 3 风格）
- 🔧 引入 Hilt 依赖注入
- 🔧 Repository 拆分优化
- 🔧 开启 R8 混淆
- 🐛 修复多项编译错误

### v1.0.0
- 🎉 初始版本
- ✨ 支持 OpenAI/DeepSeek/OpenRouter/Kimi
- ✨ 余额查询和监控
- ✨ 消费统计和图表

---

## 📄 许可证

[MIT License](LICENSE)

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

<div align="center">
  <p>⭐ 如果这个项目对你有帮助，请给个 Star 支持一下！⭐</p>
</div>