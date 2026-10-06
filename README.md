# LiquidGlass Overlay

**真·液态玻璃悬浮窗 · 浮在任何 App 之上，实时折射屏幕背后真实画面。**
*Real liquid-glass floating overlay that refracts the actual screen content behind it — over any app.*

<div align="center">

![platform](https://img.shields.io/badge/platform-Android-green)
![minSdk](https://img.shields.io/badge/minSdk-24-blue)
![targetSdk](https://img.shields.io/badge/targetSdk-35-blue)
![license](https://img.shields.io/badge/license-MIT-blue)

</div>

---

## 这是什么 / What is this

一个 Android 应用：开启后常驻两个悬浮窗。

1. **主玻璃** —— iOS 26 风格的液态玻璃，可**拖动**、**双指缩放**、**边缘拉扯**改变形状；
   圆角跟随短边比例，缩成细长条 / 正方形时依然协调。
2. **设置面板** —— 可拖动的调试抽屉，逐个滑杆实时调节玻璃材质（可关闭，长按玻璃重新打开）。

最关键的是：玻璃**折射的不是壁纸，而是此刻屏幕上的真实内容**——桌面、其他 App、视频……都能透过玻璃看到被折射、色散、放大的效果。

> **This is, to our knowledge, one of the very few complete open examples of a floating overlay that refracts the *live screen* behind it.** The hard part is not the glass — it is getting a clean background feed without the overlay capturing itself. See [工作原理](#工作原理--how-it-works).

---

## 核心难点与解法 / The hard part

### 问题：两面镜子面对面

用 `MediaProjection` 实时截屏做背景时，截到的画面**包含悬浮窗自己**。于是玻璃反射自己 → 镜像里还有镜像 → 无限套娃，看起来就是「两面镜子面对面」。

### 解法：把悬浮窗从录屏里排除

系统隐藏 API `SurfaceControl.Transaction.setSkipCapture()`（API 33+）正是为了让某个图层**不参与屏幕捕获**而设计的。把它应用到我们悬浮窗的 `SurfaceControl` 上，SurfaceFlinger 合成虚拟显示器时就会跳过这个图层——玻璃就能拿到**没有自己**的干净背景。

获取悬浮窗 `SurfaceControl` 的方式（反射，全部包在 try/catch 内，失败静默降级）：

```kotlin
val attachInfo = View::class.java.getDeclaredField("mAttachInfo")
    .apply { isAccessible = true }.get(view)
val viewRoot = attachInfo.javaClass.getDeclaredField("mViewRootImpl")
    .apply { isAccessible = true }.get(attachInfo)
val sc = viewRoot.javaClass.getMethod("getSurfaceControl").invoke(viewRoot) as SurfaceControl

val txn = SurfaceControl.Transaction()
txn.javaClass.getMethod("setSkipCapture", SurfaceControl::class.java).invoke(txn, sc) // 回退: setSkipScreenshot
txn.apply()
```

然后每帧把截屏按玻璃位置裁剪，喂给渲染库：

```kotlin
glass.setCustomBackdropCapture { bounds -> cropScreenRegion(glass, bounds) }
```

这就是整个玩具的「魔法」所在。没有这一步，任何截屏类方案都会出现镜子效应。

> API 33 以下没有 `setSkipCapture`，会自动回退尝试 `setSkipScreenshot`；两者都不可用时退化为普通静态模糊。

---

## 工作原理 / How it works

```
┌──────────────────────────────────────────────────────────────┐
│  MediaProjection  →  VirtualDisplay  →  ImageReader          │
│         (全屏实时帧，已排除本悬浮窗图层)                        │
└───────────────────────────────┬──────────────────────────────┘
                                │ 每帧
                                ▼
                  ┌───────────────────────────┐
                  │  screenBitmap (降采样 0.4x)│
                  └─────────────┬─────────────┘
                                │ 按玻璃窗口位置裁剪
                                ▼
        glass.setCustomBackdropCapture { cropScreenRegion(...) }
                                │
                                ▼
        ┌──────────────────────────────────────────────┐
        │  LiquidGlassView                              │
        │  SDF 折射 / 色散 / 边缘高光 / 姿态驱动高光      │
        │  (AGSL 单遍透镜管线, API 33+)                  │
        └──────────────────────────────────────────────┘
```

关键设计：
- **限帧 14fps**：`ImageReader` 回调里做时间节流，避免每帧全屏拷贝把主线程拖垮。
- **降采样 0.4x**：截屏分辨率按比例缩小，裁剪后再由玻璃放大，肉眼几乎无损但省一半以上带宽。
- **SurfaceControl 排除**：见上。悬浮窗与设置面板都被排除。
- **前台服务类型** `specialUse|mediaProjection`，保证长时间存活。

---

## 权限 / Permissions

| 权限 | 用途 |
|---|---|
| `SYSTEM_ALERT_WINDOW` | 显示悬浮窗 |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | 前台服务保活 |
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | 录屏前台服务类型 |
| `POST_NOTIFICATIONS` | 前台服务通知 |

应用**不申请**存储、录音、联系人等任何多余权限。录屏需你在系统弹窗里手动授权一次。

---

## 构建 / Build

要求：JDK 17、Android SDK 35（`compileSdk=35`，`minSdk=24`）。

```bash
# Debug
./gradlew :app:assembleDebug
# 产物: app/build/outputs/apk/debug/app-debug.apk
```

或直接用 Android Studio 打开根目录。

### 重建 native 库（可选）

`app/src/main/jniLibs/arm64-v8a/libnativegauss.so` 已预编译并提交。若需重建，C++ 源码在 `app/src/main/cpp/`，用 NDK 编译（见该目录 `README`）。

---

## 项目结构 / Structure

```
app/src/main/java/
├── com/liquidglass/app/
│   ├── MainActivity.kt        # 权限引导 + 录屏授权 + 拉起服务
│   ├── LiquidGlassApp.kt      # Application，全局崩溃日志
│   └── overlay/
│       ├── OverlayService.kt  # 核心：双悬浮窗 + 手势 + MediaProjection + 排除图层
│       └── SettingsPanel.kt   # 调试面板（滑杆调参）
└── com/example/liquidglass/   # 液态玻璃渲染库（见 NOTICE，上游 MIT）
app/src/main/cpp/              # native 模糊/色散 C++ 源码
app/src/main/jniLibs/          # 预编译 .so
```

---

## 已知限制 / Limitations

- `setSkipCapture` 是隐藏 API，不同 ROM 可能缺失或行为不同；本机（vivo / Android 13）实测有效。缺失时自动降级（会有镜子效应或退化为静态模糊）。
- 录屏受 DRM 保护内容（如部分 Netflix）限制，属系统行为。
- 目前仅提供 `arm64-v8a` 的 native 库；其他 ABI 会走 Kotlin 回退模糊（较慢）。
- 悬浮窗折射需要持续录屏，会增加一定耗电。

---

## 归因 / Credits & Attribution

本项目站在这些优秀工作的肩膀上：

- **[QWEA0 / Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android)** (MIT, © pandadog)
  —— `com/example/liquidglass/` 渲染库与 `app/src/main/cpp/` native 源码均来自此项目。
  真实 SDF 折射、物理色散、姿态驱动高光由它实现。
- **[Kyant0 / AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)**
  —— 早期思路参考。
- **[OpenCode AI](https://opencode.ai)**
  —— 本项目的悬浮窗架构、手势、MediaProjection 背景、
  「排除自身图层以消除镜子效应」的解法，以及绝大部分代码，均由 OpenCode AI 结对产出。

上游为 MIT 许可，本项目同样以 MIT 发布，详见 [NOTICE](NOTICE)。

---

## 致谢 / Acknowledgements

- [QWEA0/Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android)
- [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)
- [OpenCode AI](https://opencode.ai)

---

## Contact

- GitHub: [@httpspycharmhelpers](https://github.com/httpspycharmhelpers)
- Email: cgx_0925@foxmail.com · geoguesser168214@outlook.com

---

## License

[MIT](LICENSE) © 2026 httpspycharmhelpers