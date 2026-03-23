// AiServiceClient tests have been moved to the JVM unit-test source set:
// core/ai/src/test/kotlin/com/agendroid/core/ai/AiServiceClientTest.kt
//
// The original androidTest called real bindService against AiCoreService which is not
// started during instrumented test runs, causing the coroutine to hang indefinitely.
// The replacement unit test uses a fake Context that delivers the binder synchronously.
package com.agendroid.core.ai
