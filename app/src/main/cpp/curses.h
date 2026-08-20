/* Minimal curses.h placeholder for the Android (bionic) build.
 *
 * bionic does not ship a curses.h, but NetHack's sys/share/unixtty.c does
 * '#include <curses.h>' whenever LINUX is defined (and Android defines
 * LINUX via unixconf.h because __linux__ is set by the NDK).
 *
 * Nothing is actually needed from curses here: every use of curses
 * symbols in unixtty.c (has_colors() etc.) is guarded by
 * '#ifdef TTY_GRAPHICS', which is off in this build because NOTTYGRAPHICS
 * is defined.  This placeholder merely lets that include resolve.
 *
 * This directory (app/src/main/cpp) is on the CMake include path, so
 * '#include <curses.h>' from unixtty.c resolves here before the NDK
 * sysroot (which has no curses.h anyway).
 *
 * Do not remove: unixtty.c must be able to resolve the include.
 */
