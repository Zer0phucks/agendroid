cmake_minimum_required(VERSION 3.22)
project(sqlite_vec_android VERSION 0.1.6)

add_library(sqlite_vec_android SHARED
    src/main/cpp/sqlite3.c
    src/main/cpp/sqlite-vec.c
    src/main/cpp/sqlite_vec_jni.c
)

target_include_directories(sqlite_vec_android PRIVATE
    src/main/cpp
)

# SQLITE_CORE: compile sqlite-vec as a builtin extension (uses direct sqlite3_* calls)
# NDEBUG: disable sqlite assertions for release performance
# Note: SQLITE_ENABLE_LOAD_EXTENSION is intentionally omitted — sqlite-vec is registered
# at compile time via SQLITE_CORE, so the runtime loader API is not needed and is a
# security risk on Android. The loader is also explicitly disabled via
# sqlite3_db_config(SQLITE_DBCONFIG_ENABLE_LOAD_EXTENSION, 0) at runtime.
target_compile_definitions(sqlite_vec_android PRIVATE
    SQLITE_CORE
    NDEBUG
)

target_link_libraries(sqlite_vec_android
    android
    log
)
