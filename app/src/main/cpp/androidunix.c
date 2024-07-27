/* NetHack 3.7	unixunix.c	$NHDT-Date: 1687124609 2023/06/18 21:43:29 $  $NHDT-Branch: NetHack-3.7 $:$NHDT-Revision: 1.39 $ */
/* Copyright (c) Stichting Mathematisch Centrum, Amsterdam, 1985. */
/*-Copyright (c) Kenneth Lorber, Kensington, Maryland, 2015. */
/* NetHack may be freely redistributed.  See license for details. */

/* This file collects some Unix dependencies */

#include "hack.h" /* mainly for strchr() which depends on BSD */

#include <errno.h>
#include <sys/stat.h>
#if defined(NO_FILE_LINKS) || defined(SUNOS4) || defined(POSIX_TYPES)
#include <fcntl.h>
#endif
#include <signal.h>

static int eraseoldlocks(void);

static struct stat buf;

static int
eraseoldlocks(void)
{
    register int i;

#if defined(HANGUPHANDLING)
    program_state.preserve_locks = 0; /* not required but shows intent */
    /* cannot use maxledgerno() here, because we need to find a lock name
     * before starting everything (including the dungeon initialization
     * that sets astral_level, needed for maxledgerno()) up
     */
#endif
    for (i = 1; i <= MAXDUNGEON * MAXLEVEL + 1; i++) {
        /* try to remove all */
        set_levelfile_name(gl.lock, i);
        (void) unlink(fqname(gl.lock, LEVELPREFIX, 0));
    }
    set_levelfile_name(gl.lock, 0);
    if (unlink(fqname(gl.lock, LEVELPREFIX, 0)))
        return 0; /* cannot remove it */
    return 1;     /* success! */
}

void
getlock(void)
{
    int fd;
    const char *fq_lock;

    /* we ignore QUIT and INT at this point */
    if (!lock_file(HLOCK, LOCKPREFIX, 10)) {
        wait_synch();
        error("%s", "");
    }

    if (!gl.locknum)
        Sprintf(gl.lock, "%u%s", (unsigned) getuid(), svp.plname);

    regularize(gl.lock);
    set_levelfile_name(gl.lock, 0);

    fq_lock = fqname(gl.lock, LEVELPREFIX, 0);
    if ((fd = open(fq_lock, 0)) == -1) {
        if (errno == ENOENT)
            goto gotlock; /* no such file */
        perror(fq_lock);
        unlock_file(HLOCK);
        error("Cannot open %s", fq_lock);
    }

    (void) close(fd);

    if(!recover_savefile()) {
        eraseoldlocks();
        unlock_file(HLOCK);
        // error("Couldn't recover old game.");
        char c = y_n("Couldn't recover old game, start new one?");
        if(c == 'y' || c == 'Y')
         goto gotlock;
        else
         error("Couldn't recover old game.");
    } else {
        pline("Recover game successfully.");
        eraseoldlocks();
    }

    gotlock:
    fd = creat(fq_lock, FCMASK);
    unlock_file(HLOCK);
    if (fd == -1) {
        error("cannot creat lock file (%s).", fq_lock);
    } else {
        if (write(fd, (genericptr_t) &svh.hackpid, sizeof svh.hackpid)
            != sizeof svh.hackpid) {
            error("cannot write lock (%s)", fq_lock);
        }
        if (close(fd) == -1) {
            error("cannot close lock (%s)", fq_lock);
        }
    }
}

/* normalize file name - we don't like .'s, /'s, spaces */
void
regularize(char *s)
{
    register char *lp;

    while ((lp = strchr(s, '.')) != 0 || (lp = strchr(s, '/')) != 0
           || (lp = strchr(s, ' ')) != 0)
        *lp = '_';
}


/* XXX should be ifdef PANICTRACE_GDB, but there's no such symbol yet */
#ifdef PANICTRACE
boolean
file_exists(const char *path)
{
    struct stat sb;

    /* Just see if it's there - trying to figure out if we can actually
     * execute it in all cases is too hard - we really just want to
     * catch typos in SYSCF.
     */
    if (stat(path, &sb)) {
        return FALSE;
    }
    return TRUE;
}
#endif

/*unixunix.c*/
