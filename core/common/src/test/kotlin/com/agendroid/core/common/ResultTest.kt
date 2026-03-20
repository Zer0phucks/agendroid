package com.agendroid.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
        assertNull(Result.Failure<Int>(RuntimeException()).getOrNull())
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
