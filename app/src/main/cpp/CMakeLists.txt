
#




#




cmake_minimum_required(VERSION 3.18.1)

project("nativegauss")


set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)


if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfpu=neon")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mfloat-abi=softfp")
    add_definitions(-D__ARM_NEON__)
elseif(ANDROID_ABI STREQUAL "arm64-v8a")

    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv8-a")
    add_definitions(-D__ARM_NEON__)
endif()


if(CMAKE_BUILD_TYPE STREQUAL "Release")

    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3")
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -ffast-math")
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -funroll-loops")
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -fomit-frame-pointer")
    set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -fno-exceptions")


    # set(CMAKE_INTERPROCEDURAL_OPTIMIZATION TRUE)

    message(STATUS "Release build with optimizations: ${CMAKE_CXX_FLAGS_RELEASE}")
endif()





if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -O2 -g")
    set(CMAKE_CXX_FLAGS_DEBUG "${CMAKE_CXX_FLAGS_DEBUG} -DDEBUG")
    message(STATUS "Debug build: ${CMAKE_CXX_FLAGS_DEBUG}")
endif()


add_library(
    nativegauss
    SHARED
    native-lib.cpp
    gauss_iir.cpp
    gauss_iir_neon.cpp
    boxblur.cpp
    chromatic_aberration.cpp
)


target_include_directories(
    nativegauss
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)


target_link_libraries(
    nativegauss
    jnigraphics  # AndroidBitmap API
    log          # __android_log_print
    m
)


target_compile_definitions(
    nativegauss
    PRIVATE
    ANDROID
)


target_compile_options(
    nativegauss
    PRIVATE
    -Wall
    -Wextra
    -Wno-unused-parameter
)


message(STATUS "Building nativegauss for ${ANDROID_ABI}")
message(STATUS "CMake build type: ${CMAKE_BUILD_TYPE}")
message(STATUS "C++ standard: ${CMAKE_CXX_STANDARD}")

