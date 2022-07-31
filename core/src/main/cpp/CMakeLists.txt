cmake_minimum_required(VERSION 3.10)

project(tdjni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_C_STANDARD 11)

if ("${CMAKE_CXX_COMPILER_ID}" MATCHES "Clang")
    SET(CLANG_CXX_EXTRA_OPT "-Werror=unknown-warning-option -Werror=format-invalid-specifier -Werror=call-to-pure-virtual-from-ctor-dtor")
    SET(CLANG_C_EXTRA_OPT "-Werror=format-invalid-specifier")
else ()
    SET(CLANG_CXX_EXTRA_OPT "")
    SET(CLANG_C_EXTRA_OPT "")
endif ()
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} ${CLANG_CXX_EXTRA_OPT} -fPIC -Werror=delete-non-virtual-dtor -Werror=return-type -Werror=non-virtual-dtor -Wno-invalid-offsetof")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${CLANG_C_EXTRA_OPT} -fPIC -Werror=return-type")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--no-allow-shlib-undefined,--no-undefined")

# JNI headers
# check if JAVA_HOME is set
if (NOT DEFINED JAVA_HOME)
    message(FATAL_ERROR "JAVA_HOME is not set")
endif ()
set(JAVA_INCLUDE_PATH1 "${JAVA_HOME}/include")
set(JAVA_INCLUDE_PATH2 "${JAVA_HOME}/include/linux")
include_directories(${JAVA_INCLUDE_PATH1})
include_directories(${JAVA_INCLUDE_PATH2})

add_subdirectory(../../../../libs/mmkv/src/main/cpp mmkv_static)
add_subdirectory(../../../../libs/td td_build)

add_library(tdjni SHARED

        src/jni_core/TDLibJniBridge.cpp src/jni_core/Console.cpp src/jni_core/LogImpl.cpp

        src/utils/ProcessUtils.cpp src/utils/TextUtils.cpp src/utils/SharedBuffer.cpp src/utils/FileMemMap.cpp
        src/utils/auto_close_fd.cpp src/utils/io_utils.cpp src/utils/Uuid.cpp src/utils/shared_memory.cpp
        src/utils/file_utils.cpp src/utils/CachedThreadPool.cpp src/utils/SyncUtils.cpp src/utils/text/EncodingHelper.cpp

        src/utils/log/Log.cpp
        src/utils/config/ConfigManager.cpp)

#include_directories(libs/rapidjson/include)
include_directories(libs/MMKV/Core)

include_directories(src)
# TDLib generated headers
include_directories(${CMAKE_CURRENT_BINARY_DIR}/external/td)

set_target_properties(tdjni PROPERTIES
        CXX_EXTENSIONS OFF
        POSITION_INDEPENDENT_CODE ON
        )

target_link_libraries(tdjni c mmkv memprof tdjson_static tdcore tdnet tdutils tdclient)
