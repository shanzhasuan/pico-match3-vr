# 空间消消乐

PICO Spatial 动物三消游戏，包含分数、冰块、金豆荚、宝石和蜗牛五类目标，共25关。

## 构建

使用 JDK 21、Android SDK Platform 35 和项目内 Gradle Wrapper：

```bash
./gradlew :app:assembleRelease
```

Spatial SDK/BOM 为 6.1.9。外部依赖由 Google、Maven Central、Gradle Plugin Portal 和 PICO 官方公共 Maven 仓库提供。

release 校验包使用 Gradle 开发签名。签名密钥、机器配置、构建缓存和本地发布素材均不提交到仓库。

模型包和场景清单已位于 `app/src/main/assets/`，构建无需启动 Spatial Editor；`.bundle` 以未压缩形式写入 APK。

## VibeLab

`vibelab.yaml` 为发布配置。根目录 `assets/` 是本地上传素材目录，`.vibelab/` 为本地发布状态，两者均由 `.gitignore` 排除。
