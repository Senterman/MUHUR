cmake_minimum_required(VERSION 3.22.1)
project(muhur LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(native_app_glue STATIC
    ${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c
)
target_include_directories(native_app_glue PUBLIC
    ${ANDROID_NDK}/sources/android/native_app_glue
)

add_library(muhur SHARED
    ${CMAKE_CURRENT_SOURCE_DIR}/main.cpp
)

target_include_directories(muhur PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${ANDROID_NDK}/sources/android/native_app_glue
)

find_library(lib_log     log)
find_library(lib_android android)
find_library(lib_egl     EGL)
find_library(lib_gles2   GLESv2)

target_link_libraries(muhur
    native_app_glue
    ${lib_log}
    ${lib_android}
    ${lib_egl}
    ${lib_gles2}
)
