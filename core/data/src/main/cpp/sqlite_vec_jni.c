#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

/* SQLITE_CORE is defined via CMakeLists.txt compile definitions — do not redefine here. */
#include "sqlite3.h"
#include "sqlite-vec.h"

#define LOG_TAG "VectorStore"

static void log_error(const char *msg, int rc) {
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "%s: rc=%d", msg, rc);
}

/* ── Database lifecycle ──────────────────────────────────────────────────── */

JNIEXPORT jlong JNICALL
Java_com_agendroid_core_data_vector_VectorStoreJni_nativeOpen(
        JNIEnv *env, jclass cls, jstring jPath) {
    const char *path = (*env)->GetStringUTFChars(env, jPath, NULL);

    sqlite3 *db = NULL;
    int rc = sqlite3_open_v2(path, &db,
            SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX, NULL);
    (*env)->ReleaseStringUTFChars(env, jPath, path);

    if (rc != SQLITE_OK) {
        log_error("sqlite3_open_v2 failed", rc);
        if (db) sqlite3_close(db);
        return 0L;
    }

    /* Register sqlite-vec extension. Built with SQLITE_CORE so it uses direct calls. */
    rc = sqlite3_vec_init(db, NULL, NULL);
    if (rc != SQLITE_OK) {
        log_error("sqlite3_vec_init failed", rc);
        sqlite3_close(db);
        return 0L;
    }

    return (jlong)(intptr_t)db;
}

JNIEXPORT void JNICALL
Java_com_agendroid_core_data_vector_VectorStoreJni_nativeClose(
        JNIEnv *env, jclass cls, jlong handle) {
    sqlite3 *db = (sqlite3 *)(intptr_t)handle;
    if (db) sqlite3_close(db);
}

JNIEXPORT jint JNICALL
Java_com_agendroid_core_data_vector_VectorStoreJni_nativeExec(
        JNIEnv *env, jclass cls, jlong handle, jstring jSql) {
    sqlite3 *db = (sqlite3 *)(intptr_t)handle;
    const char *sql = (*env)->GetStringUTFChars(env, jSql, NULL);
    char *errmsg = NULL;
    int rc = sqlite3_exec(db, sql, NULL, NULL, &errmsg);
    (*env)->ReleaseStringUTFChars(env, jSql, sql);
    if (errmsg) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "exec error: %s", errmsg);
        sqlite3_free(errmsg);
    }
    return rc;
}

/* ── Vector operations ───────────────────────────────────────────────────── */

JNIEXPORT jint JNICALL
Java_com_agendroid_core_data_vector_VectorStoreJni_nativeInsert(
        JNIEnv *env, jclass cls, jlong handle, jlong chunkId, jfloatArray jEmbedding) {
    sqlite3 *db = (sqlite3 *)(intptr_t)handle;
    jsize len = (*env)->GetArrayLength(env, jEmbedding);
    jfloat *floats = (*env)->GetFloatArrayElements(env, jEmbedding, NULL);

    const char *sql = "INSERT OR REPLACE INTO chunks_vec (chunk_id, embedding) VALUES (?, ?)";
    sqlite3_stmt *stmt = NULL;
    int rc = sqlite3_prepare_v2(db, sql, -1, &stmt, NULL);
    if (rc != SQLITE_OK) { log_error("prepare insert", rc); return rc; }

    sqlite3_bind_int64(stmt, 1, (sqlite3_int64)chunkId);
    sqlite3_bind_blob(stmt, 2, floats, (int)(len * sizeof(float)), SQLITE_TRANSIENT);

    (*env)->ReleaseFloatArrayElements(env, jEmbedding, floats, JNI_ABORT);

    rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return (rc == SQLITE_DONE) ? SQLITE_OK : rc;
}

JNIEXPORT jint JNICALL
Java_com_agendroid_core_data_vector_VectorStoreJni_nativeDelete(
        JNIEnv *env, jclass cls, jlong handle, jlong chunkId) {
    sqlite3 *db = (sqlite3 *)(intptr_t)handle;
    const char *sql = "DELETE FROM chunks_vec WHERE chunk_id = ?";
    sqlite3_stmt *stmt = NULL;
    int rc = sqlite3_prepare_v2(db, sql, -1, &stmt, NULL);
    if (rc != SQLITE_OK) { log_error("prepare delete", rc); return rc; }
    sqlite3_bind_int64(stmt, 1, (sqlite3_int64)chunkId);
    rc = sqlite3_step(stmt);
    sqlite3_finalize(stmt);
    return (rc == SQLITE_DONE) ? SQLITE_OK : rc;
}

/**
 * Returns flat jlong array: [chunkId0, distanceBits0, chunkId1, distanceBits1, ...].
 * distanceBits is the IEEE-754 float distance reinterpreted as int32, then sign-extended to int64.
 * Kotlin side: Float.fromBits(result[i*2+1].toInt()).
 */
JNIEXPORT jlongArray JNICALL
Java_com_agendroid_core_data_vector_VectorStoreJni_nativeQuery(
        JNIEnv *env, jclass cls, jlong handle, jfloatArray jEmbedding, jint limit) {
    sqlite3 *db = (sqlite3 *)(intptr_t)handle;
    jsize embLen = (*env)->GetArrayLength(env, jEmbedding);
    jfloat *floats = (*env)->GetFloatArrayElements(env, jEmbedding, NULL);

    const char *sql =
        "SELECT chunk_id, distance FROM chunks_vec "
        "WHERE embedding MATCH ? AND k = ?";
    sqlite3_stmt *stmt = NULL;
    int rc = sqlite3_prepare_v2(db, sql, -1, &stmt, NULL);
    if (rc != SQLITE_OK) {
        (*env)->ReleaseFloatArrayElements(env, jEmbedding, floats, JNI_ABORT);
        log_error("prepare query", rc);
        return (*env)->NewLongArray(env, 0);
    }

    sqlite3_bind_blob(stmt, 1, floats, (int)(embLen * sizeof(float)), SQLITE_TRANSIENT);
    sqlite3_bind_int(stmt, 2, (int)limit);
    (*env)->ReleaseFloatArrayElements(env, jEmbedding, floats, JNI_ABORT);

    /* Collect results — use malloc to avoid VLA stack issues */
    jlong *buf = (jlong *)malloc((size_t)limit * 2 * sizeof(jlong));
    if (!buf) {
        sqlite3_finalize(stmt);
        return (*env)->NewLongArray(env, 0);
    }
    int count = 0;
    while (sqlite3_step(stmt) == SQLITE_ROW && count < limit) {
        jlong chunkId = (jlong)sqlite3_column_int64(stmt, 0);
        float dist = (float)sqlite3_column_double(stmt, 1);
        int32_t distBits;
        memcpy(&distBits, &dist, sizeof(float));
        buf[count * 2]     = chunkId;
        buf[count * 2 + 1] = (jlong)distBits;
        count++;
    }
    sqlite3_finalize(stmt);

    jlongArray result = (*env)->NewLongArray(env, count * 2);
    if (count > 0) {
        (*env)->SetLongArrayRegion(env, result, 0, count * 2, buf);
    }
    free(buf);
    return result;
}

JNIEXPORT jlongArray JNICALL
Java_com_agendroid_core_data_vector_VectorStoreJni_nativeListIds(
        JNIEnv *env, jclass cls, jlong handle) {
    sqlite3 *db = (sqlite3 *)(intptr_t)handle;
    const char *sql = "SELECT chunk_id FROM chunks_vec";
    sqlite3_stmt *stmt = NULL;
    int rc = sqlite3_prepare_v2(db, sql, -1, &stmt, NULL);
    if (rc != SQLITE_OK) {
        log_error("prepare listIds", rc);
        return (*env)->NewLongArray(env, 0);
    }

    /* Two-pass: count then collect (or use dynamic array) */
    jlong ids[65536]; /* max 64k chunks — adjust if needed */
    int count = 0;
    while (sqlite3_step(stmt) == SQLITE_ROW && count < 65536) {
        ids[count++] = (jlong)sqlite3_column_int64(stmt, 0);
    }
    sqlite3_finalize(stmt);

    jlongArray result = (*env)->NewLongArray(env, count);
    if (count > 0) {
        (*env)->SetLongArrayRegion(env, result, 0, count, ids);
    }
    return result;
}
