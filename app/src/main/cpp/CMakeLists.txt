cmake_minimum_required(VERSION 3.22.1)

project(NetHack VERSION 3.7)

if(CMAKE_BUILD_TYPE)
    message("Build type: ${CMAKE_BUILD_TYPE}.")
endif()

message(WARNING "Building NetHack version: ${NetHack_VERSION}.")

add_compile_definitions(
        ANDROID
        ANDROID_GRAPHICS
        NOCRASHREPORT
        SND_LIB_ANDSOUND
        SND_SOUNDEFFECTS_AUTOMAP
        SND_SPEECH
        DLB
        NOTTYGRAPHICS
        EDIT_GETLIN
        TILES_IN_GLYPHMAP
        ZLIB_COMP
        DEFAULT_WINDOW_SYS="and")

message(WARNING "NetHack_BINARY_DIR: ${NetHack_BINARY_DIR}.")

# Lua.
set(LUA_VERSION 5.4.6)
set(LUA_REL_PATH "Lua")

set(LUA_INCLUDE ${CMAKE_CURRENT_SOURCE_DIR}/${LUA_REL_PATH}/lua)
set(TOPLUALIB ${CMAKE_BINARY_DIR}/${LUA_REL_PATH}/liblua.a)

add_subdirectory(Lua)

file(GLOB NETHACK_CORE CONFIGURE_DEPENDS "NetHack/src/*.c")

set(NETHACK_SRC
        ${NETHACK_CORE}
        "NetHack/sys/share/posixregex.c"
        "NetHack/sys/share/ioctl.c"
        "NetHack/sys/share/unixtty.c"
        "NetHack/sys/unix/unixres.c"
        "tile.c"
        "androidmain.c"
        "androidrecover.c"
        "androidunix.c"
        "androidwin.c"
        "androidsound.c"
        "androidentry.c"
)

add_library(${CMAKE_PROJECT_NAME} SHARED ${NETHACK_SRC})
target_include_directories(
        ${CMAKE_PROJECT_NAME} PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/NetHack/include
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${LUA_INCLUDE} Lua)

target_link_libraries(${CMAKE_PROJECT_NAME} ${TOPLUALIB} android log z)
