cmake_minimum_required(VERSION 3.22.1)

project(lua VERSION 5.4.6)

if(CMAKE_BUILD_TYPE)
  message("Build type: ${CMAKE_BUILD_TYPE}.")
endif()

message(WARNING "Building Lua version: ${Lua_VERSION}.")
message(WARNING "PROJECT_NAME: ${PROJECT_NAME}.")

message(WARNING "Lua_SOURCE_DIR: ${Lua_SOURCE_DIR}.")

set(LUA_SRC
  "lua/lapi.c"
  "lua/lauxlib.c"
  "lua/lbaselib.c"
  "lua/lcode.c"
  "lua/lcorolib.c"
  "lua/lctype.c"
  "lua/ldblib.c"
  "lua/ldebug.c"
  "lua/ldo.c"
  "lua/ldump.c"
  "lua/lfunc.c"
  "lua/lgc.c"
  "lua/linit.c"
  "lua/liolib.c"
  "lua/llex.c"
  "lua/lmathlib.c"
  "lua/lmem.c"
  "lua/loadlib.c"
  "lua/lobject.c"
  "lua/lopcodes.c"
  "lua/loslib.c"
  "lua/lparser.c"
  "lua/lstate.c"
  "lua/lstring.c"
  "lua/lstrlib.c"
  "lua/ltable.c"
  "lua/ltablib.c"
  "lua/ltests.c"
  "lua/ltm.c"
  "lua/lua.c"
  "lua/lundump.c"
  "lua/lutf8lib.c"
  "lua/lvm.c"
  "lua/lzio.c"
  "lua/onelua.c"
)

add_library(${PROJECT_NAME} STATIC ${LUA_SRC})