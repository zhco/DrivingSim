# 驾考模拟器 (Driving Simulator)

基于 Android 的驾考模拟应用，利用手机陀螺仪、加速度计和触屏模拟驾驶，覆盖科目二和科目三。

## 技术栈

| 组件 | 选型 |
|------|------|
| 语言 | Kotlin |
| 图形 | OpenGL ES 3.0 |
| 物理 | JBullet (Java port) |
| 架构 | MVVM + GameLoop (60FPS) |
| 最低 API | 26 (Android 8.0) |

## 功能

- **科目二**：倒车入库、侧方停车、坡道起步、曲线行驶、直角转弯
- **科目三**：直线行驶、变道、超车、通过路口/学校/公交站、靠边停车
- **操控**：陀螺仪方向盘 + 触屏油门/刹车/离合器滑条 + 档位按钮
- **评分**：实时扣分系统（车身出线 -100、中途停车 -5 等）

## 项目结构

```
DrivingSim/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/drivingsim/
│       │   ├── MainActivity.kt              # 入口 Activity
│       │   ├── game/
│       │   │   ├── GameEngine.kt            # 60FPS 主循环
│       │   │   ├── GameWorld.kt             # 物理世界管理
│       │   │   ├── GameRenderer.kt          # OpenGL 渲染器
│       │   │   └── scene/
│       │   │       ├── Scene.kt             # 场景基类
│       │   │       ├── Subject2Scene.kt     # 科目二场地
│       │   │       └── Subject3Scene.kt     # 科目三城市道路
│       │   ├── vehicle/
│       │   │   └── Vehicle.kt               # 车辆物理模型
│       │   ├── control/
│       │   │   └── InputAggregator.kt       # 陀螺仪+触屏融合
│       │   ├── exam/
│       │   │   ├── ExamManager.kt           # 考试状态机
│       │   │   ├── Subject2Rules.kt         # 科目二评分
│       │   │   └── Subject3Rules.kt         # 科目三评分
│       │   └── ui/
│       │       ├── GameSurfaceView.kt       # GLSurfaceView
│       │       └── HudOverlay.kt            # HUD 覆盖层
│       └── res/values/
├── resources/
│   └── generate_resources.py   # 占位资源生成脚本
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/wrapper/
```

## 编译

### 1. 生成占位资源

```bash
cd resources
python3 generate_resources.py
cp -r placeholder_assets ../app/src/main/assets/
```

### 2. 添加 JBullet

下载 JBullet JAR 放入 `app/libs/` 目录：
- https://github.com/MovingBlocks/TeraBullet/releases

或使用 Maven 方式引入（修改 app/build.gradle.kts）。

### 3. 编译

```bash
# Debug APK
./gradlew assembleDebug

# Release APK (需配置签名)
./gradlew assembleRelease
```

输出 APK 位于 `app/build/outputs/apk/`。

## 预估 APK 体积

| 类别 | 体积 |
|------|------|
| Kotlin 代码 (R8) | 0.5 MB |
| JBullet 引擎 | 0.8 MB |
| AndroidX 依赖 | 1.2 MB |
| 3D 模型 (glTF) | 13.5 MB |
| 纹理 (ASTC) | 11.5 MB |
| 音频 (OGG) | 2 MB |
| 打包开销 | 3 MB |
| **合计** | **~32 MB** |

使用 AAB 分发包可压缩至 18-22 MB。

## 操控说明

- **方向盘**：手机左右倾斜（陀螺仪 Yaw 轴）
- **油门**：屏幕右下区域上滑
- **刹车**：屏幕右下中间区域上滑
- **离合器**：屏幕右下左侧区域上滑
- **档位**：左侧按钮 R/N/1-5
- **考试模式**：左上角切换科目二/三

## License

MIT
