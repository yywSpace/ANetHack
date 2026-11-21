/* NetHack 5.0 androidconf.h (Android port) */
/* Copyright (c) Kenneth Lorber, Bethesda, Maryland, 1990, 1991, 1992, 1993. */
/* NetHack may be freely redistributed.  See license for details. */

#ifdef ANDROID
#ifndef ANDROIDCONF_H
#define ANDROIDCONF_H

/* If no hard links */
#define NO_FILE_LINKS
/* Allow the game itself to recover an aborted game */
#define SELF_RECOVER

/* Route NetHack's blocking character input to the Android window port. */
#undef getchar
#define getchar nhgetch

#undef tgetch
#define tgetch nhgetch

/* Android has no Unix uid; report a non-root user. */
#define getuid() 1

/* We do not support the '!' command */
#undef SHELL

/*
 * Allow paths to be specified for HACKDIR, LEVELDIR, SAVEDIR,
 * BONESDIR, DATADIR, SCOREDIR, LOCKDIR, CONFIGDIR and TROUBLEDIR.
 */
#define NOCWD_ASSUMPTIONS

#define DUMPLOG

#undef MAIL
#undef DEF_PAGER
#undef DEF_MAILREADER

#define NO_SIGNAL

#define USER_SOUNDS

/* Write a recovery checkpoint after 10 seconds without input. */
#define IDLECHECKPOINT

/* #define CHANGE_COLOR */

#endif /* ANDROIDCONF_H */
#endif /* ANDROID */
