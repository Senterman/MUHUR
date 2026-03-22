# MÜHÜR — CMakeLists.txt
# Android NDK Native Activity build konfigürasyonu
#
# Minimum CMake: 3.22.1  (Android Studio / NDK 25+ ile uyumlu)

cmake_minimum_required(VERSION 3.22.1)
project(muhur VERSION 0.1.0 LANGUAGES CXX)

# ─────────────────────────────────────────────
# Dil standardı
# ─────────────────────────────────────────────
set(CMAKE_CXX_STANDARD          17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS        OFF)

# ─────────────────────────────────────────────
# NDK'nın native_app_glue statik kütüphanesi
# android_native_app_glue: NativeActivity için temel olay döngüsü
# ─────────────────────────────────────────────
add_library(native_app_glue STATIC
    ${ANDROID_NDK}/sources/android/native_app_glue/android_native_app_glue.c
)
target_include_directories(native_app_glue PUBLIC
    ${ANDROID_NDK}/sources/android/native_app_glue
)

# ─────────────────────────────────────────────
# Ana paylaşımlı kütüphane (APK içindeki .so)
# ─────────────────────────────────────────────
add_library(muhur SHARED
    src/main/cpp/main.cpp
    # İleride eklenecek modüller:
    # src/main/cpp/game/card_engine.cpp
    # src/main/cpp/game/stat_system.cpp
    # src/main/cpp/ui/renderer.cpp
)

target_include_directories(muhur PRIVATE
    src/main/cpp
    ${ANDROID_NDK}/sources/android/native_app_glue
)

# ─────────────────────────────────────────────
# Derleme bayrakları
# ─────────────────────────────────────────────
target_compile_options(muhur PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
    -fvisibility=hidden          # .so boyutunu azaltır
    -ffunction-sections
    -fdata-sections
)

# ─────────────────────────────────────────────
# Android sistem kütüphaneleri
# ─────────────────────────────────────────────
find_library(lib_log        log)        # __android_log_print
find_library(lib_android    android)    # AAssetManager, ANativeWindow
find_library(lib_egl        EGL)        # EGL context/surface
find_library(lib_gles2      GLESv2)     # OpenGL ES 2.0
find_library(lib_z          z)          # zlib (ileride PNG decode için)

target_link_libraries(muhur
    native_app_glue
    ${lib_log}
    ${lib_android}
    ${lib_egl}
    ${lib_gles2}
    ${lib_z}
    -Wl,--gc-sections              # Kullanılmayan section'ları at
    -Wl,-z,nocopyreloc
)

# ─────────────────────────────────────────────
# Release optimizasyonları
# ─────────────────────────────────────────────
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_compile_options(muhur PRIVATE -O2 -DNDEBUG)
    set_target_properties(muhur PROPERTIES
        LINK_FLAGS_RELEASE "-Wl,--strip-all"
    )
endif()
