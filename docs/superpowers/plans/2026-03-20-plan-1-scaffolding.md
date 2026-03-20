# Agendroid — Plan 1: Project Scaffolding

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bootstrap a buildable, lint-clean Gradle multi-module Android project with all 10 modules declared, :core:common fully implemented, all other modules stubbed with correct build files and empty package structures, and the :app manifest declaring every permission and intent filter the final app needs.

**Architecture:** Modular monorepo — one APK, 10 Gradle modules with strict layer boundaries (feature → core → common). Kotlin DSL throughout. Version catalog (libs.versions.toml) as the single source of truth for dependency versions. No feature logic in this plan — only the skeleton that Plans 2–8 fill in.

**Tech Stack:** Kotlin 2.1.0, AGP 8.7.0, Jetpack Compose BOM 2024.12.01, Hilt 2.52, Room 2.7.0, WorkManager 2.10.0, Coroutines 1.9.0, JUnit 5, MockK 1.13.13

**Spec reference:** `docs/superpowers/specs/2026-03-20-agendroid-design.md` §2, §7, §10

---

## File Map

```
agendroid/
├── settings.gradle.kts                          # Declares all 10 modules
├── build.gradle.kts                             # Root — plugin versions, no dependencies
├── gradle.properties                            # JVM heap, Kotlin opts, AndroidX flag
├── gradle/
│   └── libs.versions.toml                       # Version catalog (single source of truth)
├── .gitignore                                   # Android standard + project-specific
│
├── app/
│   ├── build.gradle.kts                         # :app — depends on all :feature:* modules
│   └── src/main/
│       ├── AndroidManifest.xml                  # ALL permissions + intent filters for final app
│       └── kotlin/com/agendroid/
│           ├── AgendroidApp.kt                  # @HiltAndroidApp Application class
│           └── MainActivity.kt                  # Stub — single-activity host for Compose nav
│
├── core/
│   ├── common/
│   │   ├── build.gradle.kts
│   │   └── src/
│   │       ├── main/kotlin/com/agendroid/core/common/
│   │       │   ├── Result.kt                    # sealed Result<T> + extensions
│   │       │   └── di/DispatcherModule.kt       # Hilt module providing named dispatchers
│   │       └── test/kotlin/com/agendroid/core/common/
│   │           └── ResultTest.kt                # Unit tests for Result extensions
│   ├── data/
│   │   └── build.gradle.kts                     # Stub — Room, SQLCipher, sqlite-vec deps declared
│   ├── embeddings/
│   │   └── build.gradle.kts                     # Stub — LiteRT deps declared
│   ├── ai/
│   │   └── build.gradle.kts                     # Stub — LiteRT-LM dep declared
│   ├── voice/
│   │   └── build.gradle.kts                     # Stub
│   └── telephony/
│       └── build.gradle.kts                     # Stub
│
└── feature/
    ├── sms/
    │   └── build.gradle.kts                     # Stub
    ├── phone/
    │   └── build.gradle.kts                     # Stub
    └── assistant/
        └── build.gradle.kts                     # Stub
```

---

## Task 1: Version Catalog and Root Build Files

**Files:**
- Create: `gradle/libs.versions.toml`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `.gitignore`
- Create: `settings.gradle.kts`

- [ ] **Step 1.1: Create the version catalog**

```toml
# gradle/libs.versions.toml
[versions]
agp                 = "8.7.0"
kotlin              = "2.1.0"
ksp                 = "2.1.0-1.0.29"
hilt                = "2.52"
compose-bom         = "2024.12.01"
room                = "2.7.0"
workmanager         = "2.10.0"
coroutines          = "1.9.0"
core-ktx            = "1.15.0"
activity-compose    = "1.9.3"
navigation-compose  = "2.8.4"
lifecycle           = "2.8.7"
sqlcipher           = "4.5.6"
sqlite-vec          = "0.1.6"
litert              = "1.0.1"
mockk               = "1.13.13"
junit5              = "5.11.3"
junit5-android      = "1.6.0"

[libraries]
# Kotlin + Coroutines
kotlin-stdlib                 = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
coroutines-core               = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-android            = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
coroutines-test               = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }

# AndroidX Core
core-ktx                      = { module = "androidx.core:core-ktx", version.ref = "core-ktx" }
activity-compose              = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }
lifecycle-runtime-ktx         = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
lifecycle-viewmodel-compose   = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# Compose
compose-bom                   = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-ui                    = { module = "androidx.compose.ui:ui" }
compose-ui-tooling-preview    = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling            = { module = "androidx.compose.ui:ui-tooling" }
compose-material3             = { module = "androidx.compose.material3:material3" }
compose-foundation            = { module = "androidx.compose.foundation:foundation" }
navigation-compose            = { module = "androidx.navigation:navigation-compose", version.ref = "navigation-compose" }

# Hilt
hilt-android                  = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler                 = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose       = { module = "androidx.hilt:hilt-navigation-compose", version = "1.2.0" }

# Room
room-runtime                  = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx                      = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler                 = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing                  = { module = "androidx.room:room-testing", version.ref = "room" }

# WorkManager
workmanager-ktx               = { module = "androidx.work:work-runtime-ktx", version.ref = "workmanager" }
hilt-work                     = { module = "androidx.hilt:hilt-work", version = "1.2.0" }
hilt-work-compiler            = { module = "androidx.hilt:hilt-compiler", version = "1.2.0" }

# SQLCipher + sqlite-vec
sqlcipher-android             = { module = "net.zetetic:sqlcipher-android", version.ref = "sqlcipher" }
sqlite-ktx                    = { module = "androidx.sqlite:sqlite-ktx", version = "2.5.0" }
# sqlite-vec ships as a prebuilt AAR downloaded from GitHub releases.
# Add the AAR to app/libs/ and declare it as a file dependency in :core:data (Plan 3).
# The version entry here is for documentation/tracking only.
sqlite-vec-version            = { module = "io.github.asg017:sqlite-vec-android-bundled", version.ref = "sqlite-vec" }

# LiteRT (on-device ML)
litert                        = { module = "com.google.ai.edge.litert:litert", version.ref = "litert" }
litert-gpu                    = { module = "com.google.ai.edge.litert:litert-gpu", version.ref = "litert" }
litert-support                = { module = "com.google.ai.edge.litert:litert-support-api", version.ref = "litert" }

# Testing
junit5-api                    = { module = "org.junit.jupiter:junit-jupiter-api", version.ref = "junit5" }
junit5-engine                 = { module = "org.junit.jupiter:junit-jupiter-engine", version.ref = "junit5" }
junit5-params                 = { module = "org.junit.jupiter:junit-jupiter-params", version.ref = "junit5" }
mockk                         = { module = "io.mockk:mockk", version.ref = "mockk" }
androidx-test-core            = { module = "androidx.test:core-ktx", version = "1.6.1" }
androidx-test-runner          = { module = "androidx.test:runner", version = "1.6.2" }
hilt-android-testing          = { module = "com.google.dagger:hilt-android-testing", version.ref = "hilt" }

[plugins]
android-application   = { id = "com.android.application", version.ref = "agp" }
android-library       = { id = "com.android.library", version.ref = "agp" }
kotlin-android        = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm            = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
ksp                   = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt                  = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
compose-compiler      = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }

[bundles]
compose               = ["compose-ui", "compose-ui-tooling-preview", "compose-material3", "compose-foundation"]
coroutines            = ["coroutines-core", "coroutines-android"]
room                  = ["room-runtime", "room-ktx"]
litert                = ["litert", "litert-gpu", "litert-support"]
junit5                = ["junit5-api", "junit5-params"]
```

- [ ] **Step 1.2: Create the root build.gradle.kts**

```kotlin
// build.gradle.kts (root)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library)     apply false
    alias(libs.plugins.kotlin.android)      apply false
    alias(libs.plugins.kotlin.jvm)          apply false
    alias(libs.plugins.ksp)                 apply false
    alias(libs.plugins.hilt)                apply false
    alias(libs.plugins.compose.compiler)    apply false
}
```

- [ ] **Step 1.3: Create gradle.properties**

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 1.4: Create .gitignore**

```gitignore
# Android
*.iml
.gradle/
/local.properties
/.idea/
.DS_Store
/build/
/captures/
.externalNativeBuild/
.cxx/
*.apk
*.aab

# Models (large binary assets — tracked separately via Git LFS or downloaded at build time)
**/*.tflite
**/*.bin
**/*.gguf
**/*.ort

# Superpowers brainstorm session files
.superpowers/
```

- [ ] **Step 1.5: Create settings.gradle.kts**

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://storage.googleapis.com/download.tensorflow.org/maven") // LiteRT
    }
}

rootProject.name = "agendroid"

include(":app")
include(":core:common")
include(":core:data")
include(":core:embeddings")
include(":core:ai")
include(":core:voice")
include(":core:telephony")
include(":feature:sms")
include(":feature:phone")
include(":feature:assistant")
```

*(Full build verification happens in Task 5 once the Gradle wrapper exists.)*

---

## Task 2: Gradle Wrapper

**Files:**
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Run: wrapper generation command

- [ ] **Step 2.1: Bootstrap the Gradle wrapper**

```bash
cd /home/noob/agendroid
gradle wrapper --gradle-version 8.11.1 --distribution-type bin
```

Expected output: `BUILD SUCCESSFUL` and creation of `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.

If `gradle` is not installed globally, use the system package manager or download manually: https://gradle.org/releases/ (v8.11.1).

- [ ] **Step 2.2: Make the wrapper executable**

```bash
chmod +x /home/noob/agendroid/gradlew
```

- [ ] **Step 2.3: Commit the wrapper**

```bash
cd /home/noob/agendroid
git add gradle/ gradlew gradlew.bat settings.gradle.kts build.gradle.kts \
        gradle.properties .gitignore
git commit -m "chore: bootstrap Gradle multi-module project structure"
```

---

## Task 3: :core:common Module

**Files:**
- Create: `core/common/build.gradle.kts`
- Create: `core/common/src/main/kotlin/com/agendroid/core/common/Result.kt`
- Create: `core/common/src/main/kotlin/com/agendroid/core/common/di/DispatcherModule.kt`
- Create: `core/common/src/test/kotlin/com/agendroid/core/common/ResultTest.kt`

- [ ] **Step 3.1: Create core/common/build.gradle.kts**

```kotlin
// core/common/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace  = "com.agendroid.core.common"
    compileSdk = 35
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 3.2: Write the failing test for Result**

```kotlin
// core/common/src/test/kotlin/com/agendroid/core/common/ResultTest.kt
package com.agendroid.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResultTest {

    @Test
    fun `Success holds value and isSuccess is true`() {
        val result: Result<Int> = Result.Success(42)
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals(42, (result as Result.Success).data)
    }

    @Test
    fun `Failure holds exception and isFailure is true`() {
        val ex = RuntimeException("boom")
        val result: Result<Int> = Result.Failure(ex)
        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertEquals(ex, (result as Result.Failure).exception)
    }

    @Test
    fun `getOrNull returns data on Success, null on Failure`() {
        assertEquals(7, Result.Success(7).getOrNull())
        assertEquals(null, Result.Failure<Int>(RuntimeException()).getOrNull())
    }

    @Test
    fun `getOrDefault returns data on Success, default on Failure`() {
        assertEquals(7, Result.Success(7).getOrDefault(0))
        assertEquals(0, Result.Failure<Int>(RuntimeException()).getOrDefault(0))
    }

    @Test
    fun `map transforms Success value, passes through Failure`() {
        val mapped = Result.Success(3).map { it * 2 }
        assertEquals(6, (mapped as Result.Success).data)

        val ex = RuntimeException()
        val passthrough = Result.Failure<Int>(ex).map { it * 2 }
        assertEquals(ex, (passthrough as Result.Failure).exception)
    }
}
```

- [ ] **Step 3.3: Run the test to confirm it fails**

```bash
cd /home/noob/agendroid && ./gradlew :core:common:test --tests "*.ResultTest" 2>&1 | tail -10
```

Expected: compilation error — `Result` class does not exist yet.

- [ ] **Step 3.4: Implement Result.kt**

```kotlin
// core/common/src/main/kotlin/com/agendroid/core/common/Result.kt
package com.agendroid.core.common

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Failure<T>(val exception: Throwable) : Result<T>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrDefault(default: @UnsafeVariance T): T = (this as? Success)?.data ?: default

    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> Failure(exception)
    }
}

inline fun <T> runCatching(block: () -> T): Result<T> = try {
    Result.Success(block())
} catch (e: Throwable) {
    Result.Failure(e)
}
```

- [ ] **Step 3.5: Run the test to confirm it passes**

```bash
cd /home/noob/agendroid && ./gradlew :core:common:test --tests "*.ResultTest" 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`, `5 tests completed, 0 failures`.

- [ ] **Step 3.6: Implement DispatcherModule.kt**

```kotlin
// core/common/src/main/kotlin/com/agendroid/core/common/di/DispatcherModule.kt
package com.agendroid.core.common.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class IoDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DefaultDispatcher
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class MainDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides @IoDispatcher      fun io():      CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun default(): CoroutineDispatcher = Dispatchers.Default
    @Provides @MainDispatcher    fun main():    CoroutineDispatcher = Dispatchers.Main
}
```

- [ ] **Step 3.7: Commit :core:common**

```bash
cd /home/noob/agendroid
git add core/common/
git commit -m "feat(core:common): Result<T>, dispatcher qualifiers, unit tests"
```

---

## Task 4: Stub Modules

Create a build.gradle.kts for each remaining module. Each is an Android library stub — no source files yet, just the build file that declares its dependencies so future plans can add code without touching the build system.

**Files:**
- Create: `core/data/build.gradle.kts`
- Create: `core/embeddings/build.gradle.kts`
- Create: `core/ai/build.gradle.kts`
- Create: `core/voice/build.gradle.kts`
- Create: `core/telephony/build.gradle.kts`
- Create: `feature/sms/build.gradle.kts`
- Create: `feature/phone/build.gradle.kts`
- Create: `feature/assistant/build.gradle.kts`

- [ ] **Step 4.1: Create core/data/build.gradle.kts**

```kotlin
// core/data/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace  = "com.agendroid.core.data"
    compileSdk = 35
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room + encryption
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.sqlite.ktx)

    // WorkManager
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.room.testing)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 4.2: Create core/embeddings/build.gradle.kts**

```kotlin
// core/embeddings/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace  = "com.agendroid.core.embeddings"
    compileSdk = 35
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.bundles.litert)  // runs all-MiniLM-L6-v2

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 4.3: Create core/ai/build.gradle.kts**

```kotlin
// core/ai/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace  = "com.agendroid.core.ai"
    compileSdk = 35
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:embeddings"))
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.bundles.litert)  // LiteRT-LM for Gemma 3

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 4.4: Create core/voice/build.gradle.kts**

```kotlin
// core/voice/build.gradle.kts
// Note: Whisper (whisper.cpp JNI) and Kokoro TTS ship as AAR files
// added to libs/ directory — not yet available on Maven Central.
// Their AARs will be added in Plan 5.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace  = "com.agendroid.core.voice"
    compileSdk = 35
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 4.5: Create core/telephony/build.gradle.kts**

```kotlin
// core/telephony/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace  = "com.agendroid.core.telephony"
    compileSdk = 35
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ai"))
    implementation(project(":core:voice"))
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 4.6: Create feature/sms/build.gradle.kts**

```kotlin
// feature/sms/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace  = "com.agendroid.feature.sms"
    compileSdk = 35
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:ai"))
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 4.7: Create feature/phone/build.gradle.kts**

```kotlin
// feature/phone/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace  = "com.agendroid.feature.phone"
    compileSdk = 35
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:telephony"))
    implementation(project(":core:ai"))
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 4.8: Create feature/assistant/build.gradle.kts**

```kotlin
// feature/assistant/build.gradle.kts
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace  = "com.agendroid.feature.assistant"
    compileSdk = 35
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:ai"))
    implementation(project(":core:voice"))
    implementation(project(":core:data"))
    implementation(libs.bundles.coroutines)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)

    testImplementation(libs.bundles.junit5)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
}

tasks.withType<Test> { useJUnitPlatform() }
```

- [ ] **Step 4.9: Create empty source directories for each stub module (so Gradle can find them)**

```bash
for module in core/data core/embeddings core/ai core/voice core/telephony \
              feature/sms feature/phone feature/assistant; do
  mkdir -p /home/noob/agendroid/$module/src/main/kotlin
  mkdir -p /home/noob/agendroid/$module/src/test/kotlin
  # AndroidManifest placeholder required for Android library modules
  cat > /home/noob/agendroid/$module/src/main/AndroidManifest.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest />
EOF
done
```

- [ ] **Step 4.10: Commit stub modules**

```bash
cd /home/noob/agendroid
git add core/ feature/
git commit -m "chore: add stub build files for all 9 remaining modules"
```

---

## Task 5: :app Module

**Files:**
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/agendroid/AgendroidApp.kt`
- Create: `app/src/main/kotlin/com/agendroid/MainActivity.kt`

- [ ] **Step 5.1: Create app/build.gradle.kts**

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace   = "com.agendroid"
    compileSdk  = 35
    defaultConfig {
        applicationId = "com.agendroid"
        minSdk        = 31
        targetSdk     = 35
        versionCode   = 1
        versionName   = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:data"))
    implementation(project(":core:ai"))
    implementation(project(":core:voice"))
    implementation(project(":core:telephony"))
    implementation(project(":feature:sms"))
    implementation(project(":feature:phone"))
    implementation(project(":feature:assistant"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose)
    implementation(libs.navigation.compose)
    implementation(libs.bundles.coroutines)
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
}
```

- [ ] **Step 5.2: Create app/src/main/AndroidManifest.xml**

This manifest declares every permission and intent filter the final app needs. Future plans add service/receiver/activity entries inside the existing `<application>` tag — they do not rewrite this file.

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- ================================================================
         PERMISSIONS
         ================================================================ -->

    <!-- Telephony -->
    <uses-permission android:name="android.permission.READ_PHONE_STATE" />
    <uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />
    <uses-permission android:name="android.permission.READ_CALL_LOG" />
    <uses-permission android:name="android.permission.WRITE_CALL_LOG" />
    <uses-permission android:name="android.permission.ANSWER_PHONE_CALLS" />

    <!-- SMS -->
    <uses-permission android:name="android.permission.SEND_SMS" />
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.WRITE_SMS" />

    <!-- Contacts & Calendar -->
    <uses-permission android:name="android.permission.READ_CONTACTS" />
    <uses-permission android:name="android.permission.READ_CALENDAR" />

    <!-- Audio -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

    <!-- System / Background -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_PHONE_CALL" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <!-- ================================================================
         APPLICATION
         Plans 3–8 add <service>, <receiver>, and <activity> entries here.
         ================================================================ -->
    <application
        android:name=".AgendroidApp"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Agendroid">

        <!-- MainActivity — single-activity Compose host -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
            <!-- Default dialer role entry point -->
            <intent-filter>
                <action android:name="android.intent.action.DIAL" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="tel" />
            </intent-filter>
            <!-- Default SMS app entry point -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.APP_MESSAGING" />
            </intent-filter>
        </activity>

        <!-- ================================================================
             Plans 3–8 add <service> and <receiver> entries here.
             IMPORTANT for plans 3–8: Any service using FOREGROUND_SERVICE_MICROPHONE
             or FOREGROUND_SERVICE_PHONE_CALL must also declare the matching
             android:foregroundServiceType attribute on its <service> element,
             or Android will throw a SecurityException at runtime.
             ================================================================ -->

        <!-- WorkManager initializer — disable default, use Hilt initializer -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>

    </application>

</manifest>
```

- [ ] **Step 5.3: Create AgendroidApp.kt**

```kotlin
// app/src/main/kotlin/com/agendroid/AgendroidApp.kt
package com.agendroid

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AgendroidApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
```

- [ ] **Step 5.4: Create MainActivity.kt**

```kotlin
// app/src/main/kotlin/com/agendroid/MainActivity.kt
package com.agendroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Navigation graph wired in Plan 8.
            // Placeholder: empty composable so the app launches.
        }
    }
}
```

- [ ] **Step 5.5: Create minimal resources so the app compiles**

```bash
mkdir -p /home/noob/agendroid/app/src/main/res/values

cat > /home/noob/agendroid/app/src/main/res/values/strings.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Agendroid</string>
</resources>
EOF

cat > /home/noob/agendroid/app/src/main/res/values/themes.xml << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.Agendroid" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
EOF
```

The manifest references `@mipmap/ic_launcher`. A missing launcher icon causes `assembleDebug` to fail with a resource-not-found error — it does not merely warn. Create a placeholder PNG using Python (produces a valid 48×48 white PNG):

```bash
mkdir -p /home/noob/agendroid/app/src/main/res/mipmap-mdpi
python3 -c "
import struct, zlib

def png_chunk(name, data):
    c = zlib.crc32(name + data) & 0xffffffff
    return struct.pack('>I', len(data)) + name + data + struct.pack('>I', c)

w, h = 48, 48
raw = b''.join(b'\x00' + b'\xff\xff\xff' * w for _ in range(h))
compressed = zlib.compress(raw, 9)

png = (b'\x89PNG\r\n\x1a\n'
    + png_chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
    + png_chunk(b'IDAT', compressed)
    + png_chunk(b'IEND', b''))

open('app/src/main/res/mipmap-mdpi/ic_launcher.png', 'wb').write(png)
print('Placeholder launcher icon written.')
"
```

A proper icon replaces this in Plan 8.

- [ ] **Step 5.6: Verify the full project builds cleanly**

```bash
cd /home/noob/agendroid && ./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`. If there are dependency resolution failures, check that `storage.googleapis.com/download.tensorflow.org/maven` is reachable and that LiteRT artifact versions in `libs.versions.toml` are correct — update version strings if needed.

- [ ] **Step 5.7: Commit :app**

```bash
cd /home/noob/agendroid
git add app/
git commit -m "feat(app): MainActivity, AgendroidApp, full AndroidManifest with all permissions"
```

---

## Task 6: Verify All Module Tests Pass

- [ ] **Step 6.1: Run all unit tests**

```bash
cd /home/noob/agendroid && ./gradlew test 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`. Only `:core:common:test` has tests at this point (ResultTest — 5 tests). All other modules have no tests yet; they should show 0 tests executed, not failures.

- [ ] **Step 6.2: Run lint check**

```bash
cd /home/noob/agendroid && ./gradlew lint 2>&1 | grep -E "(error|warning)" | head -20
```

Review any errors. Warnings from stub modules with no source are expected and acceptable. Errors are not — fix before proceeding.

- [ ] **Step 6.3: Final commit**

```bash
cd /home/noob/agendroid
git add -A
git commit -m "chore: verify build and lint clean across all modules

All 10 modules declared and buildable. :core:common tests pass (5/5).
Stub modules compile with correct dependency declarations.
AndroidManifest declares all required permissions and intent filters."
```

---

## Acceptance Criteria

Plan 1 is complete when:

- [ ] `./gradlew assembleDebug` succeeds with `BUILD SUCCESSFUL`
- [ ] `./gradlew test` passes all tests in `:core:common` (5 tests) with 0 failures
- [ ] `./gradlew lint` produces 0 errors (warnings from empty stub modules are acceptable)
- [ ] All 10 modules are declared in `settings.gradle.kts` and resolvable
- [ ] `AndroidManifest.xml` contains all permissions from spec §7 (including `MANAGE_OWN_CALLS`, not `CALL_PHONE`)
- [ ] `gradle/libs.versions.toml` is the only place version numbers appear

**Next:** Plan 2 — Call Pipeline Feasibility Spike
