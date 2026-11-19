# 参与贡献

感谢你愿意花时间。这个项目的目标很具体：**把官方 App 的对话体验原样搬到 API 上**，所以判断一个改动要不要收，主要看它是否让体验更接近官方、或让上手更简单。

## 环境

- JDK 17
- Android SDK：compileSdk 36 / build-tools 36.0.0
- 不要提交 `local.properties`、`keystore.properties`、任何 `.jks`

```bash
./gradlew :app:testDebugUnitTest   # 单测
./gradlew :app:assembleDebug       # Debug 包
```

国内网络已默认使用阿里云 Maven 与腾讯 Gradle 镜像，一般不需要额外配置代理。

## 提 PR 之前

1. 先开 issue 说清楚要解决什么，避免大改动方向不一致白做
2. 单测通过；涉及解析逻辑（SSE 事件、导出包、角标重排、公式）的改动请补测试
3. 一个 PR 只做一件事，别把重构和功能混在一起
4. 界面改动请附截图或录屏，说明改动前后的差别

## 代码约定

- Kotlin + Jetpack Compose，单 Activity + 二级页导航，不引入新的 UI 框架
- 状态走 ViewModel + StateFlow，单向数据流；不要在 Composable 里直接发起网络请求
- 新增用户可见文案：**先对齐官方 App 的说法**，官方怎么叫就怎么叫，不自创名词
- 文案不写营销词、不堆感叹号
- 提交信息用中文，一句话说清做了什么；正文可以补原因

## 明确不接受的改动

- 调用官方 App 私有 `/api/v0/*` 接口、反编译或二次打包
- 内置 API Key、共享 Key 池、把 Key 上传到任何服务器
- 使用 DeepSeek 的商标、图标、名称作为产品标识
- 引入需要联网上报数据的统计 SDK
- 改变 GPL-3.0 许可的改动

## 许可

提交代码即表示同意以 [GPL-3.0](LICENSE) 授权。参考的官方开源前端 [chat-demo](https://github.com/deepseek-ai/chat-demo) 为 MIT，其声明保留在 `third_party/`。
