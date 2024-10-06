/* androidmain.c
 * based on unixmain.c
 */

#include "hack.h"
#include "dlb.h"
#include <setjmp.h>

#include <sys/stat.h>
#include <ctype.h>

#ifndef O_RDONLY
#ifndef O_BINARY
#define O_BINARY 0
#endif
#include <fcntl.h>
#endif
#ifdef CHDIR
static void chdirx(const char *);
#endif /* CHDIR */

#include <android/log.h>
#include <dirent.h>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "Tag", __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, "Tag", __VA_ARGS__)


static void process_options(int, char **);
static void wd_message(void);

static boolean wiz_error_flag = FALSE;

int NetHackMain(int argc, char** argv)
{
    char *dir = argv[1];
    NHFILE *nhfp;
    boolean resuming = FALSE; /* assume new game */
    boolean plsel_once = FALSE;
    early_init(argc, argv);
    gh.hname = argv[0];
    svh.hackpid = getpid();
    (void) umask(0777 & ~FCMASK);
    choose_windows(DEFAULT_WINDOW_SYS);

#ifdef SND_LIB_INTEGRATED
    /* One of the soundlib interfaces was integrated on build.
     * We can leave a hint here for activate_chosen_soundlib later.
     * assign_soundlib() just sets an indicator, it doesn't initialize
     * any soundlib, and the indicator could be overturned before
     * activate_chosen_soundlib() gets called. Qt will place its own
     * hint if qt_init_nhwindow() is invoked.
     */
#if defined(SND_LIB_MACSOUND)
    soundlibchoice = soundlib_macsound;
    assign_soundlib(soundlibchoice);
#endif
#if defined(SND_LIB_ANDSOUND)
    assign_soundlib(soundlib_andsound);
#endif

#endif

#ifdef ENHANCED_SYMBOLS
    if (argcheck(argc, argv, ARG_DUMPGLYPHIDS) == 2)
        exit(EXIT_SUCCESS);
#endif

#ifdef CHDIR
    /*
     * Change directories before we initialize the window system so
     * we can find the tile file.
     */
    chdirx(dir);
#endif

    initoptions();
#ifdef PANICTRACE
    ARGV0 = gh.hname; /* save for possible stack trace */
#ifndef NO_SIGNAL
    panictrace_setsignals(TRUE);
#endif
#endif

    /*
     * It seems you really want to play.
     */
    u.uhp = 1; /* prevent RIP on early quits */
#if defined(HANGUPHANDLING)
    program_state.preserve_locks = 1;
#ifndef NO_SIGNAL
    sethanguphandler((SIG_RET_TYPE) hangup);
#endif
#endif

    process_options(argc, argv); /* command line options */

    init_nhwindows(&argc, argv); /* now we can set up window system */

#ifdef DEF_PAGER
    if (!(gc.catmore = nh_getenv("NETHACKPAGER"))
        && !(gc.catmore = nh_getenv("HACKPAGER"))
        && !(gc.catmore = nh_getenv("PAGER")))
        gc.catmore = DEF_PAGER;
#endif
#ifdef MAIL
    getmailstatus();
#endif
    /* strip role,race,&c suffix; in android set plname/plnamelen in askname()
       or holds a generic user name like "player" or "games" */
    plnamesuffix();
    /* wizard mode access is deferred until here */
    set_playmode(); /* sets plname to "wizard" for wizard mode */
    if (wizard) {
        /* use character name rather than lock letter for file names */
        gl.locknum = 0;
    } else {
        /* suppress interrupts while processing lock file */
        (void) signal(SIGQUIT, SIG_IGN);
        (void) signal(SIGINT, SIG_IGN);
    }
     dlb_init(); /* must be before newgame() */

    /*
     * Initialize the vision system.  This must be before mklev() on a
     * new game or before a level restore on a saved game.
     */
    vision_init();

    init_sound_disp_gamewindows();

    /*
     * First, try to find and restore a save file for specified character.
     * We'll return here if new game player_selection() renames the hero.
     */
    attempt_restore:

    /*
     * getlock() find weather current user have a lock that can recover,
     * if have recover it or start a new game
     */
    if (*svp.plname) {
        getlock();
#if defined(HANGUPHANDLING)
        program_state.preserve_locks = 0; /* after getlock() */
#endif
    }

    if (*svp.plname && (nhfp = restore_saved_game()) != 0) {
        const char *fq_save = fqname(gs.SAVEF, SAVEPREFIX, 1);

        (void) chmod(fq_save, 0); /* disallow parallel restores */
#ifndef NO_SIGNAL
        (void) signal(SIGINT, (SIG_RET_TYPE) done1);
#endif
#ifdef NEWS
        if (iflags.news) {
            display_file(NEWS, FALSE);
            iflags.news = FALSE; /* in case dorecover() fails */
        }
#endif
        /* if there are early trouble-messages issued, let's
         * not go overtop of them with a pline just yet */
        if (ge.early_raw_messages)
            raw_print("Restoring save file...");
        else
            pline("Restoring save file...");
        mark_synch(); /* flush output */
        if (dorecover(nhfp)) {
            resuming = TRUE; /* not starting new game */
            wd_message();
            if (discover || wizard) {
                /* this seems like a candidate for paranoid_confirmation... */
                if (y_n("Do you want to keep the save file?") == 'n') {
                    (void) delete_savefile();
                } else {
                    (void) chmod(fq_save, FCMASK); /* back to readable */
                    nh_compress(fq_save);
                }
            }
        }
    }

    if (!resuming) {
        boolean neednewlock = (!*svp.plname);
        /* new game:  start by choosing role, race, etc;
           player might change the hero's name while doing that,
           in which case we try to restore under the new name
           and skip selection this time if that didn't succeed */
        if (!iflags.renameinprogress || iflags.defer_plname || neednewlock) {
            if (!plsel_once)
                player_selection();
            plsel_once = TRUE;
            if (neednewlock && *svp.plname)
                goto attempt_restore;
            if (iflags.renameinprogress) {
                /* player has renamed the hero while selecting role;
                   if locking alphabetically, the existing lock file
                   can still be used; otherwise, discard current one
                   and create another for the new character name */
                if (!gl.locknum) {
                    delete_levelfile(0); /* remove empty lock file */
                    getlock();
                }
                goto attempt_restore;
            }
        }
        newgame();
        wd_message();
    }

    /* moveloop() never returns but isn't flagged NORETURN */
    moveloop(resuming);

    exit(EXIT_SUCCESS);
    /*NOTREACHED*/
}

/* caveat: argv elements might be arbitrarily long */
static void
process_options(int argc, char *argv[])
{
    LOGE("process_options");
}


boolean
check_user_string(const char *optstr)
{
    // android is not a multi-user system, allow any user
    // in sysconf must set WIZARDS=* EXPLORERS=*
    if (optstr[0] == '*')
        return TRUE;
    return FALSE;
}

boolean
authorize_wizard_mode(void)
{
    if (sysopt.wizards && sysopt.wizards[0]) {
        if (check_user_string(sysopt.wizards))
            return TRUE;
    }
    wiz_error_flag = TRUE; /* not being allowed into wizard mode */
    return FALSE;
}


static void
wd_message(void)
{
    if (wiz_error_flag) {
        if (sysopt.wizards && sysopt.wizards[0]) {
            char *tmp = build_english_list(sysopt.wizards);
            pline("Only user%s %s may access debug (wizard) mode.",
                  strchr(sysopt.wizards, ' ') ? "s" : "", tmp);
            free(tmp);
        } else
            pline("Entering explore/discovery mode instead.");
        wizard = 0, discover = 1; /* (paranoia) */
    } else if (discover)
        You("are in non-scoring explore/discovery mode.");
}

boolean authorize_explore_mode(void)
{
    return TRUE; /* no restrictions on explore mode */
}

#ifdef CHDIR
static void
chdirx(const char *dir)
{

#ifdef HACKDIR
    if (!dir)
        dir = HACKDIR;
#endif

    if (dir && chdir(dir) < 0) {
        perror(dir);
        error("Cannot chdir to %s.", dir);
        /*NOTREACHED*/
    }

    check_recordfile(dir);
}
#endif /* CHDIR */


#ifdef PORT_HELP
void
port_help()
{
	/*
	 * Display unix-specific help.   Just show contents of the helpfile
	 * named by PORT_HELP.
	 */
	display_file(PORT_HELP, TRUE);
}
#endif

unsigned long
sys_random_seed(void)
{
    unsigned long seed = 0L;
    unsigned long pid = (unsigned long) getpid();
    boolean no_seed = TRUE;
#ifdef DEV_RANDOM
    FILE *fptr;

    fptr = fopen(DEV_RANDOM, "r");
    if (fptr) {
        fread(&seed, sizeof (long), 1, fptr);
        has_strong_rngseed = TRUE;  /* decl.c */
        no_seed = FALSE;
        (void) fclose(fptr);
    } else {
        /* leaves clue, doesn't exit */
        paniclog("sys_random_seed", "falling back to weak seed");
    }
#endif
    if (no_seed) {
        seed = (unsigned long) getnow(); /* time((TIME_type) 0) */
        /* Quick dirty band-aid to prevent PRNG prediction */
        if (pid) {
            if (!(pid & 3L))
                pid -= 1L;
            seed *= pid;
        }
    }
    return seed;
}

// reference recover.c/restore_savefile
char * plname_from_running(const char *filename)
{
    int fd;
    char *result = 0;
    char savename[SAVESIZE];
    int savelev, hpid, pltmpsiz, filecmc;
    struct version_info version_data;
    struct savefile_info sfi;
    char plbuf[PL_NSIZ], indicator;

    /* level 0 file contains:
     *  pid of creating process (ignored here)
     *  level number for current level of save file
     *  name of save file nethack would have created
     *  savefile info
     *  player name
     *  and game state
     */
    if((fd = open(filename, O_RDONLY, 0)) >= 0) {
        if (read(fd, (genericptr_t) &hpid, sizeof hpid) == sizeof hpid
            && read(fd, (genericptr_t) &savelev, sizeof(savelev))
                == sizeof(savelev)
            && (read(fd, (genericptr_t) savename, sizeof savename)
                == sizeof savename)
            && (read(fd, (genericptr_t) &indicator, sizeof indicator)
                == sizeof indicator)
            && (read(fd, (genericptr_t) &filecmc, sizeof filecmc)
                == sizeof filecmc)
            && (read(fd, (genericptr_t) &version_data, sizeof version_data)
                == sizeof version_data)
            && (read(fd, (genericptr_t) &sfi, sizeof sfi) == sizeof sfi)
            && (read(fd, (genericptr_t) &pltmpsiz, sizeof pltmpsiz)
                == sizeof pltmpsiz) && (pltmpsiz > 0 && pltmpsiz <= PL_NSIZ)
            && (read(fd, (genericptr_t) plbuf, pltmpsiz) == pltmpsiz)) {
            result = dupstr(plbuf);
        }
        close(fd);
    }

    return result;
}

int filter_running_game(const struct dirent* entry)
{
    return *entry->d_name && entry->d_name[strlen(entry->d_name)-1] == '0';
}

char ** get_aborted_games() {
    char **result = NULL;
    char name[64]; /* more than PL_NSIZ */
    struct dirent **namelist;
    int i, j, uid, myuid=getuid();
    int n = scandir(".", &namelist, filter_running_game, 0);
    if(n > 0) {
        result = (char **) alloc((n + 1) * sizeof(char *)); /* at most */
        (void) memset((genericptr_t) result, 0, (n + 1) * sizeof(char *));
        for (i = 0, j = 0; i<n; i++) {
            if (sscanf(namelist[i]->d_name, "%d%63[^.].0", &uid, name ) == 2 ) {
                if (uid==myuid) {
                    char *r = plname_from_running(namelist[i]->d_name);
                    if (r)
                        result[j++] = r;
                }
            }
        }
    }
    return result;
}

/*
 * Add a slash to any name not ending in /. There must
 * be room for the /
 */
void
append_slash(char *name)
{
    char *ptr;

    if (!*name)
        return;
    ptr = name + (strlen(name) - 1);
    if (*ptr != '/') {
        *++ptr = '/';
        *++ptr = '\0';
    }
}