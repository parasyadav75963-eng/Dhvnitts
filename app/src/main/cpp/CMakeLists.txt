cmake_minimum_required(VERSION 3.22.1)
project("dhwanitts" C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_STANDARD 11)

include(FetchContent)

FetchContent_Declare(
    espeak_ng
    GIT_REPOSITORY https://github.com/rhasspy/espeak-ng.git
    GIT_TAG master
)
FetchContent_GetProperties(espeak_ng)
if(NOT espeak_ng_POPULATED)
    FetchContent_Populate(espeak_ng)
endif()

file(WRITE "${espeak_ng_BINARY_DIR}/config.h" "
#define PACKAGE_VERSION \"1.52-dev\"
#define HAVE_STDINT_H 1
#define HAVE_MKSTEMP 1
")

file(GLOB ESPEAK_SOURCES
    "${espeak_ng_SOURCE_DIR}/src/libespeak-ng/*.c"
    "${espeak_ng_SOURCE_DIR}/src/ucd-tools/src/*.c"
)

list(FILTER ESPEAK_SOURCES EXCLUDE REGEX ".*(event|fifo|espeak_command|sPlayer|klatt|mbrowrap|speechPlayer).*\\.c$")

add_library(espeak-ng STATIC ${ESPEAK_SOURCES})

target_include_directories(espeak-ng PUBLIC
    "${espeak_ng_BINARY_DIR}"
    "${espeak_ng_SOURCE_DIR}/src/include"
    "${espeak_ng_SOURCE_DIR}/src/include/espeak-ng"
    "${espeak_ng_SOURCE_DIR}/src/include/compat"
    "${espeak_ng_SOURCE_DIR}/src/ucd-tools/src/include"
    "${espeak_ng_SOURCE_DIR}/src/libespeak-ng"
)

target_compile_definitions(espeak-ng PRIVATE
    HAVE_CONFIG_H=1
    _BSD_SOURCE=1
    _DEFAULT_SOURCE=1
    _POSIX_C_SOURCE=200112L
    _STDINT_H_COMPAT=1
)

target_compile_options(espeak-ng PRIVATE
    -Wno-incompatible-pointer-types
    -Wno-unused-parameter
    -Wno-sign-compare
    -Wno-missing-field-initializers
    -fPIC
)

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
