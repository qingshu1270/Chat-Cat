// CI 跑在海外 runner 上：阿里云镜像对境外 IP 常返回 403，Gradle 会把它记成「依赖不存在」，
// 导致插件（如 KSP）解析失败。因此 CI 里直接用官方仓库，本地仍优先走镜像。
pluginManagement {
    val useChinaMirror = System.getenv("CI").isNullOrEmpty()
    repositories {
        if (useChinaMirror) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val useChinaMirror = System.getenv("CI").isNullOrEmpty()
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useChinaMirror) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "chatcat"
include(":app")
