# Sky Home Proxy 开发文档

## 项目目标

在 **NIO Phone（SkyUI）** 上实现：

- 保持 **SkyUI Launcher** 为系统默认 Launcher。
- 保持 SkyUI 的 **Quickstep / Recent Apps** 完全正常。
- 用户执行 **Home 操作（上滑返回桌面）** 后，最终进入 **Niagara Launcher**。
- 不修改系统、不 Root、不修改 Launcher、不依赖 Tasker、MacroDroid。

本项目本质上是一个 **Home Proxy**。

---

# 背景

SkyUI 不允许第三方 Launcher 成为默认 Launcher。

虽然可以通过：

```bash
cmd package set-home-activity
```

修改默认 Launcher 为 Niagara。

但是会导致：

```
Quickstep
↓

RecentsActivity

↓

ClassCastException

↓

Recent Apps 崩溃
```

日志：

```
java.lang.ClassCastException:

com.android.quickstep.RecentsActivity

cannot be cast to

com.android.launcher3.Launcher
```

因此不能真正修改默认 Launcher。

必须保持：

```
SkyUI Launcher
```

作为默认 Launcher。

---

# 核心思路

不要修改 Launcher。

只代理 Home。

流程：

```
App

↓

Home Gesture

↓

SkyUI Launcher

↓

AccessibilityService 收到窗口变化

↓

检测到：

com.skyui.launcher/com.android.launcher3.uioverrides.QuickstepLauncher

↓

延迟约30~50ms

↓

启动：

bitpit.launcher.ui.HomeActivity

↓

最终用户看到 Niagara
```

SkyUI 仍然认为：

```
Home = SkyUI
```

因此：

Quickstep

Recent Apps

Overview

全部正常。

---

# 技术方案

采用：

AccessibilityService

监听：

```
TYPE_WINDOW_STATE_CHANGED
```

无需 Root。

无需 Shizuku。

无需 Tasker。

---

# 为什么不用监听包名

不要：

```
package == com.skyui.launcher
```

因为：

Recent Apps：

```
com.android.quickstep.RecentsActivity
```

也属于：

```
com.skyui.launcher
```

如果只判断 package：

Recent 打开瞬间

↓

又跳 Niagara

↓

Recent 无法使用

因此必须判断：

Activity。

---

# 判断条件

仅代理：

```
Package

com.skyui.launcher
```

并且：

```
Activity

com.android.launcher3.uioverrides.QuickstepLauncher
```

其它 Activity：

全部忽略。

尤其：

```
com.android.quickstep.RecentsActivity
```

必须忽略。

---

# Niagara 启动方式

启动：

```
bitpit.launcher.ui.HomeActivity
```

Component：

```
Package

bitpit.launcher

Activity

bitpit.launcher.ui.HomeActivity
```

使用：

```
FLAG_ACTIVITY_NEW_TASK
```

即可。

无需 Shell。

无需 am start。

直接：

```
startActivity(Intent)
```

---

# Niagara 特性

Niagara 已验证：

```
launchMode

singleTask
```

重复启动：

不会创建新实例。

而是：

恢复已有桌面。

因此无需自己处理去重。

---

# 防止重复启动

建议增加：

Cooldown。

例如：

```
700ms
```

避免：

```
SkyUI

↓

Accessibility

↓

连续收到多个事件

↓

连续启动 Niagara
```

可记录：

```
lastLaunchTime
```

超过：

700ms

才允许再次启动。

---

# 建议增加延迟

不要：

收到事件立即启动。

建议：

```
30~50ms
```

原因：

Quickstep Home 动画刚开始时：

SkyUI Launcher

尚未完全 Resume。

稍微等待：

动画更稳定。

建议：

```
40ms
```

作为默认值。

可配置。

---

# 是否读取屏幕内容

不需要。

Accessibility：

仅监听：

```
TYPE_WINDOW_STATE_CHANGED
```

即可。

无需：

```
canRetrieveWindowContent=true
```

建议：

```
false
```

降低权限。

---

# Manifest

需要：

```
AccessibilityService
```

权限：

```
android.permission.BIND_ACCESSIBILITY_SERVICE
```

无需：

SYSTEM_ALERT_WINDOW

无需：

Shizuku

无需：

Root

---

# 主界面

只需要一个按钮：

```
启用 Home Proxy
```

点击：

跳转：

```
Accessibility Settings
```

让用户开启：

Accessibility Service。

无需其它 UI。

---

# 兼容性

仅针对：

SkyUI。

当前已验证：

SkyUI Home：

```
com.android.launcher3.uioverrides.QuickstepLauncher
```

Niagara：

```
bitpit.launcher.ui.HomeActivity
```

如果未来：

SkyUI 更新 Launcher。

Activity 名发生变化。

只需修改：

判断 Activity。

---

# 不做的事情

不要：

修改默认 Launcher。

不要：

使用：

```
cmd package set-home-activity
```

不要：

disable SkyUI。

不要：

hook Quickstep。

不要：

监听所有窗口内容。

不要：

模拟 Home。

不要：

模拟 Gesture。

---

# 最终效果

用户：

```
应用

↓

Home

↓

SkyUI

↓

40ms

↓

Niagara
```

视觉上：

最终进入 Niagara。

同时：

Recent Apps

Quickstep

Overview

全部保持 SkyUI 原生实现。

整个 APK 只负责：

```
SkyUI Home

↓

自动切 Niagara
```

不负责其它任何自动化逻辑。