#include <jni.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <android/log.h>

#define LOG_TAG "SqliteVecAndroid"

/*
 * sqlite-vec is compiled as a loadable extension (without SQLITE_CORE), so its
 * sqlite3_vec_init uses the sqlite3_api_routines pointer for all sqlite3_* calls.
 *
 * To call sqlite3_auto_extension we need Android's own libsqlite.so symbols.
 * The NDK does not provide a link-time stub for libsqlite, so we resolve it at
 * runtime via dlopen.
 */

/* Forward-declare the sqlite3_api_routines struct opaquely (we only pass a pointer). */
struct sqlite3_api_routines_t;
typedef struct sqlite3 sqlite3;

/* sqlite3_vec_init as a loadable-extension entry point. */
extern int sqlite3_vec_init(
    sqlite3 *db,
    char **pzErrMsg,
    const struct sqlite3_api_routines_t *pApi
);

/* Function pointer type for sqlite3_auto_extension. */
typedef int (*sqlite3_auto_extension_fn)(void (*xEntryPoint)(void));

JNIEXPORT void JNICALL
Java_com_agendroid_core_data_vector_SqliteVecAndroid_nativeInit(JNIEnv *env, jclass clazz) {
    /*
     * Open Android's system SQLite. RTLD_GLOBAL makes its symbols available to
     * subsequently loaded libraries (including our own .so at dlopen time), and
     * crucially gives us access to the *same* sqlite3_auto_extension registry that
     * android.database.sqlite.SQLiteDatabase uses.
     */
    void *lib = dlopen("libsqlite.so", RTLD_NOW | RTLD_GLOBAL);
    if (lib == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
            "dlopen(libsqlite.so) failed: %s", dlerror());
        return;
    }

    sqlite3_auto_extension_fn auto_ext =
        (sqlite3_auto_extension_fn) dlsym(lib, "sqlite3_auto_extension");
    if (auto_ext == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
            "dlsym(sqlite3_auto_extension) failed: %s", dlerror());
        return;
    }

    int rc = auto_ext((void (*)(void)) sqlite3_vec_init);
    if (rc != 0) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG,
            "sqlite3_auto_extension registration returned %d", rc);
    } else {
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG,
            "sqlite-vec registered via sqlite3_auto_extension");
    }
}
