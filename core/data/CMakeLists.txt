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
# SQLITE_ENABLE_LOAD_EXTENSION: needed by vec0's vtab implementation
# NDEBUG: disable sqlite assertions for release performance
target_compile_definitions(sqlite_vec_android PRIVATE
    SQLITE_CORE
    SQLITE_ENABLE_LOAD_EXTENSION
    NDEBUG
)

target_link_libraries(sqlite_vec_android
    android
    log
)
