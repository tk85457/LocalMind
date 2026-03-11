cmake_minimum_required(VERSION 3.22.1)

project("localmind")

# Set C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization flags for release
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG -fvisibility=hidden -ffunction-sections -fdata-sections")
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3 -DNDEBUG -fvisibility=hidden -ffunction-sections -fdata-sections")

# OWASP MASVS: Native library hardening flags
# CMAKE_POSITION_INDEPENDENT_CODE=ON is the correct CMake way to enable -fPIC
# for ALL targets including llama.cpp subdirectory targets (ggml-base, etc.)
# Using add_compile_options(-fPIC) was wrong — it only affected targets defined
# AFTER the call, not llama.cpp's internal targets built via add_subdirectory.
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fstack-protector-all -D_FORTIFY_SOURCE=2")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fstack-protector-all -D_FORTIFY_SOURCE=2")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,relro,-z,now,-z,noexecstack")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,relro,-z,now,-z,noexecstack")

# Do not force a single ARM ISA globally here.
# ggml will build multiple CPU backend variants and select the best one at runtime.

# llama.cpp configuration
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
# Disable cpp-httplib OpenSSL warning
set(HTTPLIB_REQUIRE_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_SERVER_SSL OFF CACHE BOOL "" FORCE)
# Suppress Git-not-found warning — build info not needed for Android
set(LLAMA_BUILD_INFO OFF CACHE BOOL "" FORCE)
set(GIT_EXECUTABLE "" CACHE FILEPATH "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS ON CACHE BOOL "" FORCE)
set(LLAMA_CUBLAS OFF CACHE BOOL "" FORCE)
set(LLAMA_METAL OFF CACHE BOOL "" FORCE)
set(LLAMA_VULKAN ON CACHE BOOL "" FORCE)
set(LLAMA_STATIC ON CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)

add_subdirectory(llama.cpp)

add_library(${CMAKE_PROJECT_NAME} SHARED
    jni_bridge.cpp
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log
    mtmd
    llama
)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/src
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/include
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/common
    ${CMAKE_CURRENT_SOURCE_DIR}/llama.cpp/ggml/include
)

if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_custom_command(TARGET ${CMAKE_PROJECT_NAME} POST_BUILD
        COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:${CMAKE_PROJECT_NAME}>
    )
endif()
