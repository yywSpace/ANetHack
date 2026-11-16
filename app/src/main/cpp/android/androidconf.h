/*	SCCS Id: @(#)androidconf.h	3.4	2011/03/31	*/
/* Copyright (c) Kenneth Lorber, Bethesda, Maryland, 1990, 1991, 1992, 1993. */
/* NetHack may be freely redistributed.  See license for details. */

#ifdef ANDROID
#ifndef ANDROIDCONF_H
#define ANDROIDCONF_H

#define NO_FILE_LINKS /* if no hard links */
#define SELF_RECOVER /* Allow the game itself to recover from an aborted game */

#ifdef getchar
#undef getchar
#endif
#define getchar nhgetch
#undef tgetch
#define tgetch nhgetch
#define getuid() 1

#undef SHELL /* we do not support the '!' command */

#define NOCWD_ASSUMPTIONS /* Allow paths to be specified for HACKDIR,      \
                             LEVELDIR, SAVEDIR, BONESDIR, DATADIR,         \
                             SCOREDIR, LOCKDIR, CONFIGDIR, and TROUBLEDIR. \
                             */

#define DUMPLOG

#undef MAIL
#undef DEF_PAGER
#undef DEF_MAILREADER

#define NO_SIGNAL

#define USER_SOUNDS
// #define CHANGE_COLOR

#endif /* ANDROIDCONF_H */
#endif /* ANDROID */
