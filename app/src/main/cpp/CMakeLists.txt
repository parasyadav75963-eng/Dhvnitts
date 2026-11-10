cmake_minimum_required(VERSION 3.22.1)
project("dhwanitts")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

# Official espeak-ng source for 100+ languages, numbers, and punctuation phonemization
FetchContent_Declare(
    espeak_ng
    GIT_REPOSITORY https://github.com/rhasspy/espeak-ng.git
    GIT_TAG master
)

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(USE_ASYNC OFF CACHE BOOL "" FORCE)
set(USE_MBROLA OFF CACHE BOOL "" FORCE)
set(USE_LIBPCAUDIO OFF CACHE BOOL "" FORCE)
set(USE_KLATT OFF CACHE BOOL "" FORCE)
set(USE_SPEECHPLAYER OFF CACHE BOOL "" FORCE)
set(BUILD_ESPEAK_NG_TESTS OFF CACHE BOOL "" FORCE)
set(BUILD_ESPEAK_NG_DOCS OFF CACHE BOOL "" FORCE)
set(COMPILE_INTONATIONS OFF CACHE BOOL "" FORCE)
set(COMPILE_DICT OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(espeak_ng)

set(ONNX_SO_PATH "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/arm64-v8a/libonnxruntime.so")

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
    IMPORTED_LOCATION ${ONNX_SO_PATH}
)

add_library(piper_wrapper SHARED piper_wrapper.cpp)

target_include_directories(piper_wrapper PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${espeak_ng_SOURCE_DIR}/src/include
)

find_library(log-lib log)

target_link_libraries(piper_wrapper
    espeak-ng
    onnxruntime
    ${log-lib}
)
