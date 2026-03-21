cmake_minimum_required(VERSION 3.22)
project(sqlite_vec_android VERSION 0.1.6)

# Build sqlite-vec as a JNI shared library.
#
# sqlite-vec.c is compiled WITHOUT SQLITE_CORE (loadable extension mode): it uses
# the sqlite3_api_routines pointer that Android's SQLite passes when auto-loading
# the extension.
#
# sqlite_vec_jni.c calls sqlite3_auto_extension at runtime via dlopen("libsqlite.so")
# because the NDK does not provide a link-time stub for Android's system libsqlite.
add_library(sqlite_vec_android SHARED
    src/main/cpp/sqlite-vec.c
    src/main/cpp/sqlite_vec_jni.c
)

# Include our local sqlite3.h / sqlite3ext.h for type definitions used by sqlite-vec.c.
# At runtime the actual SQLite code comes from Android's system libsqlite.so (loaded
# via dlopen in sqlite_vec_jni.c).
target_include_directories(sqlite_vec_android PRIVATE
    src/main/cpp
)

# dl  — for dlopen / dlsym in sqlite_vec_jni.c
# android, log — standard NDK system libs
# No link against sqlite3: resolved at runtime via dlopen.
target_link_libraries(sqlite_vec_android
    android
    log
    dl
)
