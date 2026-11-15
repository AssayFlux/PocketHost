cmake_minimum_required(VERSION 3.22.1)
project(pockethost_node_bridge LANGUAGES CXX)

if(NOT DEFINED NODE_SDK_DIR)
    message(FATAL_ERROR "NODE_SDK_DIR must point to the verified Node.js mobile SDK.")
endif()

add_library(libnode SHARED IMPORTED GLOBAL)
set_target_properties(libnode PROPERTIES
    IMPORTED_LOCATION "${NODE_SDK_DIR}/jniLibs/${ANDROID_ABI}/libnode.so")

add_library(nodebridge SHARED node-bridge.cpp)
target_include_directories(nodebridge PRIVATE
    "${NODE_SDK_DIR}/include/node"
    "${NODE_SDK_DIR}/include/node/deps/uv/include"
    "${NODE_SDK_DIR}/include/node/deps/openssl/config")
find_library(log-lib log)
find_library(android-lib android)
target_link_libraries(nodebridge PRIVATE libnode ${log-lib} ${android-lib} dl)
