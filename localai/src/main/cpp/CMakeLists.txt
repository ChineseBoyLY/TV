cmake_minimum_required(VERSION 3.22.1)
project("mnn_llm_bridge")

set(MNN_INCLUDE_DIR "${CMAKE_SOURCE_DIR}/include")
set(MNN_LIB_DIR "${CMAKE_SOURCE_DIR}/../jniLibs/arm64-v8a")

include_directories("${MNN_INCLUDE_DIR}/")
include_directories("${MNN_INCLUDE_DIR}/3rd_party/")
include_directories("${MNN_INCLUDE_DIR}/express/")

add_library(mnn_llm_bridge SHARED mnn_llm_bridge.cpp)
add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION "${MNN_LIB_DIR}/libMNN.so")
target_compile_options(mnn_llm_bridge PRIVATE -std=c++17 -fvisibility=hidden)
target_link_options(mnn_llm_bridge PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(mnn_llm_bridge android log MNN)
