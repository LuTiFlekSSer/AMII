import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  id("java") // Java support
  alias(libs.plugins.kotlin) // Kotlin support
  alias(libs.plugins.intelliJPlatform) // IntelliJ Platform Gradle Plugin
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

val localIdePath = providers.environmentVariable("AMII_IDE_PATH")
  .orElse(providers.gradleProperty("platformLocalPath"))
  .orElse(
    providers.environmentVariable("LOCALAPPDATA")
      .map { "$it/Programs/PyCharm Professional" }
  )

val localIdeBuildMajor = localIdePath.map { idePath ->
  val productInfo = file("$idePath/product-info.json")
  val buildNumber = Regex("\"buildNumber\"\\s*:\\s*\"(\\d+)")
    .find(productInfo.readText())
    ?.groupValues
    ?.get(1)
    ?: error("Unable to read buildNumber from $productInfo")
  buildNumber.toInt()
}

// IntelliJ Platform 2024.2+ uses Java 21 bytecode. Do not request an exact
// toolchain here: current JetBrains IDEs may run Gradle on a newer JBR.
java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
  toolchain {
    // The IDE runtime can be newer (the local PyCharm ships JBR 25), while
    // --release/JVM target below keeps the produced plugin compatible with 21.
    languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
  }
}

// Configure project's dependencies
repositories {
  mavenCentral()

  // IntelliJ Platform Gradle Plugin Repositories Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
  intellijPlatform {
    defaultRepositories()
  }
}

// Dependencies are managed with Gradle version catalog - read more: https://docs.gradle.org/current/userguide/platforms.html#sub:version-catalog
dependencies {
  implementation("commons-io:commons-io:2.15.1")
  implementation("org.javassist:javassist:3.29.2-GA")
  implementation("com.googlecode.soundlibs:mp3spi:1.9.5.4") {
    // mp3spi exposes its legacy test dependency at runtime; the plugin does
    // not use JUnit 3 and should not bundle it into the distribution.
    exclude(group = "junit", module = "junit")
  }
  implementation("io.sentry:sentry:6.28.0")
  testImplementation("org.assertj:assertj-core:3.25.3")
  testImplementation("io.mockk:mockk:1.14.11")
  compileOnly(files("lib/instrumented-doki-theme-jetbrains-88.5-1.11.0.jar"))
  testImplementation(libs.junit)
  testImplementation(libs.opentest4j)

  // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
  intellijPlatform {
    // Compile against the user's existing PyCharm installation. This keeps local
    // builds offline with respect to IntelliJ Platform distributions.
    local(localIdePath)

    // Plugin Dependencies. Uses `platformBundledPlugins` property from the gradle.properties file for bundled IntelliJ Platform plugins.
    bundledPlugins(
      providers.gradleProperty("platformBundledPlugins").map {
        it.split(',').map(String::trim).filter(String::isNotEmpty)
      }
    )

    // ProjectTaskListener moved out of platform core in 2025.2.
    bundledModule("intellij.platform.tasks")

    // SM runner is a standalone library in 2026.1 and a bundled plugin module
    // in 2026.2.
    bundledModule("intellij.platform.smRunner")

    if (localIdeBuildMajor.get() >= 262) {
      // Base test APIs and HwFacadeJPanel moved out of core in 2026.2.
      bundledModule("intellij.platform.testRunner")
      bundledModule("intellij.platform.ui.jcef")
    }

    // Plugin Dependencies. Uses `platformPlugins` property from the gradle.properties file for plugin from JetBrains Marketplace.
    plugins(
      providers.gradleProperty("platformPlugins").map {
        it.split(',').map(String::trim).filter(String::isNotEmpty)
      }
    )

    testFramework(TestFrameworkType.Platform)
  }
}

configurations {
  implementation.configure {
    // sentry brings in a slf4j that breaks when
    // with the platform slf4j
    exclude("org.slf4j")

  }
}

// Configure IntelliJ Platform Gradle Plugin - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
intellijPlatform {
  pluginConfiguration {
    name = providers.gradleProperty("pluginName")
    version = providers.gradleProperty("pluginVersion")

    ideaVersion {
      sinceBuild = providers.gradleProperty("pluginSinceBuild")
      untilBuild = providers.gradleProperty("pluginUntilBuild")
    }
  }

  signing {
    certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
    privateKey = providers.environmentVariable("PRIVATE_KEY")
    password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
  }

  publishing {
    token = providers.environmentVariable("PUBLISH_TOKEN")
    // The pluginVersion is based on the SemVer (https://semver.org) and supports pre-release labels, like 2.1.7-alpha.3
    // Specify pre-release label to publish the plugin in a custom Release Channel automatically. Read more:
    // https://plugins.jetbrains.com/docs/intellij/deployment.html#specifying-a-release-channel
    channels = providers.gradleProperty("pluginVersion").map { listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" }) }
  }

  pluginVerification {
    ides {
      current()
    }
  }
}

tasks {
  processResources {
    filesMatching("amii-version.txt") {
      expand("pluginVersion" to providers.gradleProperty("pluginVersion").get())
    }
  }

  withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
    options.release.set(21)
  }

  withType<KotlinJvmCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
  }

  wrapper {
    gradleVersion = providers.gradleProperty("gradleVersion").get()
  }

  buildSearchableOptions {
    enabled = false
  }
}

intellijPlatformTesting {
  runIde {
    register("runIdeForUiTests") {
      task {
        jvmArgumentProviders += CommandLineArgumentProvider {
          listOf(
            "-Drobot-server.port=8082",
            "-Dide.mac.message.dialogs.as.sheets=false",
            "-Djb.privacy.policy.text=<!--999.999-->",
            "-Djb.consents.confirmation.enabled=false",
          )
        }
      }

      plugins {
        robotServerPlugin()
      }
    }
  }
}
