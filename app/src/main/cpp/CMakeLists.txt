cmake_minimum_required(VERSION 3.22.1)

project(NetHack VERSION 3.7)

if(CMAKE_BUILD_TYPE)
    message("Build type: ${CMAKE_BUILD_TYPE}.")
endif()

message(WARNING "Building NetHack version: ${NetHack_VERSION}.")

add_compile_definitions(
        ANDROID
        ANDROID_GRAPHICS
        SND_LIB_ANDSOUND
        SND_SOUNDEFFECTS_AUTOMAP
        SND_SPEECH
        NOTTYGRAPHICS
        EDIT_GETLIN
        TILES_IN_GLYPHMAP
        DEFAULT_WINDOW_SYS="and")

message(WARNING "NetHack_BINARY_DIR: ${NetHack_BINARY_DIR}.")

# Lua.
set(LUA_VERSION 5.4.3)
set(LUA_REL_PATH "Lua")

set(LUA_INCLUDE ${CMAKE_CURRENT_SOURCE_DIR}/${LUA_REL_PATH}/lua)
set(TOPLUALIB ${CMAKE_BINARY_DIR}/${LUA_REL_PATH}/liblua.a)

add_subdirectory(Lua)

set(NETHACK_SRC
        "NetHack/src/allmain.c"
        "NetHack/src/alloc.c"
        "NetHack/src/apply.c"
        "NetHack/src/artifact.c"
        "NetHack/src/attrib.c"
        "NetHack/src/ball.c"
        "NetHack/src/bones.c"
        "NetHack/src/botl.c"
        "NetHack/src/cmd.c"
        "NetHack/src/date.c"
        "NetHack/src/dbridge.c"
        "NetHack/src/decl.c"
        "NetHack/src/detect.c"
        "NetHack/src/dig.c"
        "NetHack/src/display.c"
        "NetHack/src/dlb.c"
        "NetHack/src/do.c"
        "NetHack/src/do_name.c"
        "NetHack/src/do_wear.c"
        "NetHack/src/dog.c"
        "NetHack/src/dogmove.c"
        "NetHack/src/dokick.c"
        "NetHack/src/dothrow.c"
        "NetHack/src/drawing.c"
        "NetHack/src/dungeon.c"
        "NetHack/src/eat.c"
        "NetHack/src/end.c"
        "NetHack/src/engrave.c"
        "NetHack/src/exper.c"
        "NetHack/src/explode.c"
        "NetHack/src/extralev.c"
        "NetHack/src/files.c"
        "NetHack/src/fountain.c"
        "NetHack/src/hack.c"
        "NetHack/src/hacklib.c"
        "NetHack/src/insight.c"
        "NetHack/src/invent.c"
        "NetHack/src/isaac64.c"
        "NetHack/src/light.c"
        "NetHack/src/lock.c"
        "NetHack/src/mail.c"
        "NetHack/src/makemon.c"
        "NetHack/src/mcastu.c"
        "NetHack/src/mdlib.c"
        "NetHack/src/mhitm.c"
        "NetHack/src/mhitu.c"
        "NetHack/src/minion.c"
        "NetHack/src/mklev.c"
        "NetHack/src/mkmap.c"
        "NetHack/src/mkmaze.c"
        "NetHack/src/mkobj.c"
        "NetHack/src/mkroom.c"
        "NetHack/src/mon.c"
        "NetHack/src/mondata.c"
        "NetHack/src/monmove.c"
        "NetHack/src/monst.c"
        "NetHack/src/mplayer.c"
        "NetHack/src/mthrowu.c"
        "NetHack/src/muse.c"
        "NetHack/src/music.c"
        "NetHack/src/nhlobj.c"
        "NetHack/src/nhlsel.c"
        "NetHack/src/nhlua.c"
        "NetHack/src/o_init.c"
        "NetHack/src/objects.c"
        "NetHack/src/objnam.c"
        "NetHack/src/options.c"
        "NetHack/src/pager.c"
        "NetHack/src/pickup.c"
        "NetHack/src/pline.c"
        "NetHack/src/polyself.c"
        "NetHack/src/potion.c"
        "NetHack/src/pray.c"
        "NetHack/src/priest.c"
        "NetHack/src/quest.c"
        "NetHack/src/questpgr.c"
        "NetHack/src/read.c"
        "NetHack/src/rect.c"
        "NetHack/src/region.c"
        "NetHack/src/restore.c"
        "NetHack/src/rip.c"
        "NetHack/src/rnd.c"
        "NetHack/src/role.c"
        "NetHack/src/rumors.c"
        "NetHack/src/save.c"
        "NetHack/src/sfstruct.c"
        "NetHack/src/shk.c"
        "NetHack/src/shknam.c"
        "NetHack/src/sit.c"
        "NetHack/src/sounds.c"
        "NetHack/src/sp_lev.c"
        "NetHack/src/spell.c"
        "NetHack/src/steal.c"
        "NetHack/src/steed.c"
        "NetHack/src/symbols.c"
        "NetHack/src/sys.c"
        "NetHack/src/teleport.c"
        "NetHack/src/timeout.c"
        "NetHack/src/topten.c"
        "NetHack/src/track.c"
        "NetHack/src/trap.c"
        "NetHack/src/u_init.c"
        "NetHack/src/uhitm.c"
        "NetHack/src/vault.c"
        "NetHack/src/version.c"
        "NetHack/src/vision.c"
        "NetHack/src/weapon.c"
        "NetHack/src/were.c"
        "NetHack/src/wield.c"
        "NetHack/src/windows.c"
        "NetHack/src/wizard.c"
        "NetHack/src/wizcmds.c"
        "NetHack/src/worm.c"
        "NetHack/src/worn.c"
        "NetHack/src/write.c"
        "NetHack/src/zap.c"
        "NetHack/src/calendar.c"
        "NetHack/src/stairs.c"
        "NetHack/src/glyphs.c"
        "NetHack/src/getpos.c"
        "NetHack/src/coloratt.c"
        "NetHack/src/strutil.c"
        "NetHack/src/selvar.c"
        "NetHack/src/report.c"
        "NetHack/src/nhmd4.c"
        "NetHack/src/utf8map.c"
        "NetHack/sys/share/posixregex.c"
        "NetHack/sys/share/ioctl.c"
        "NetHack/sys/share/unixtty.c"
        # "NetHack/sys/unix/unixmain.c"
        # "NetHack/sys/unix/unixunix.c"
        "NetHack/sys/unix/unixres.c"
        "tile.c"
        "winandroid.c"
        "androidmain.c"
        "androidunix.c"
        "androidsound.c"
)

add_library(${CMAKE_PROJECT_NAME} SHARED ${NETHACK_SRC})
target_include_directories(
        ${CMAKE_PROJECT_NAME} PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/NetHack/include
        ${LUA_INCLUDE} Lua)

target_link_libraries(${CMAKE_PROJECT_NAME} ${TOPLUALIB} android log)
