cmake_minimum_required(VERSION 3.16)
project(lossless_core CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# miniaudio (public domain / MIT-0): single header, fetched once at configure time
set(MA_DIR ${CMAKE_BINARY_DIR}/ma)
if(NOT EXISTS ${MA_DIR}/miniaudio.h)
  file(DOWNLOAD https://raw.githubusercontent.com/mackron/miniaudio/0.11.21/miniaudio.h
       ${MA_DIR}/miniaudio.h STATUS dl_status TLS_VERIFY ON)
  list(GET dl_status 0 dl_code)
  if(NOT dl_code EQUAL 0)
    file(REMOVE ${MA_DIR}/miniaudio.h)
    message(FATAL_ERROR "Could not download miniaudio.h: ${dl_status}")
  endif()
endif()

add_library(lossless_jni SHARED ll_core.cpp ll_jni.cpp)
target_include_directories(lossless_jni PRIVATE ${MA_DIR} ${CMAKE_CURRENT_SOURCE_DIR})

if(ANDROID)
  # pthread is part of bionic libc on Android, so it is not linked separately
  target_link_libraries(lossless_jni PRIVATE dl m log)
else()
  find_package(JNI REQUIRED)
  target_include_directories(lossless_jni PRIVATE ${JNI_INCLUDE_DIRS})
  if(UNIX AND NOT APPLE)
    target_link_libraries(lossless_jni PRIVATE pthread dl m)
  endif()
endif()
if(APPLE)
  set_source_files_properties(ll_core.cpp PROPERTIES COMPILE_FLAGS "-x objective-c++ -DMA_NO_RUNTIME_LINKING")
  target_link_libraries(lossless_jni PRIVATE "-framework CoreFoundation" "-framework CoreAudio" "-framework AudioToolbox")
endif()
