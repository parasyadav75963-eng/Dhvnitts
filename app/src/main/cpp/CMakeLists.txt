cmake_minimum_required(VERSION 3.22.1)
project("dhwanitts_piper")

set(CMAKE_CXX_STANDARD 17)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# ONNX Runtime Imported Shared Library
add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
    IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/libs/${ANDROID_ABI}/libonnxruntime.so
)

add_library(piper_wrapper SHARED piper_wrapper.cpp)

find_library(log-lib log)

target_link_libraries(piper_wrapper
    onnxruntime
    ${log-lib}
)
