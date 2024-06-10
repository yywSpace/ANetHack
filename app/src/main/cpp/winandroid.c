#include <string.h>
#include <errno.h>
#include <jni.h>
#include <ctype.h>

#include "hack.h"
#include "func_tab.h"   /* for extended commands */
#include "dlb.h"

#include <android/log.h>
#define TAG "NetHack Native"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)


static void and_init_nhwindows(int *, char **);
static void and_player_selection();
static void and_askname();
static void and_get_nh_event() ;
static void and_exit_nhwindows (const char *);
static void and_suspend_nhwindows(const char *);
static void and_resume_nhwindows();
static winid and_create_nhwindow(int);
static void and_clear_nhwindow(winid);
static void and_display_nhwindow(winid, boolean);
static void and_dismiss_nhwindow(winid);
static void and_destroy_nhwindow(winid);
static void and_curs(winid,int,int);
static void and_putstr(winid, int, const char *);
static void and_putmixed(winid, int, const char *);
static void and_display_file(const char *, boolean);
static void and_start_menu(winid,unsigned long);
static void and_add_menu(winid, const glyph_info *, const union any *, char, char, int, int, const char *,
                         unsigned int);
static void and_end_menu(winid, const char *);
static int and_select_menu(winid, int, MENU_ITEM_P **);
static void and_update_inventory();
static void and_mark_synch();
static void and_wait_synch();
#ifdef CLIPPING
static void and_cliparound(int, int);
#endif
#ifdef POSITIONBAR
static void and_update_positionbar(char *);
#endif
static void and_print_glyph(winid, coordxy, coordxy, const glyph_info *, const glyph_info *);
static void and_raw_print(const char *);
static void and_raw_print_bold(const char *);
static int and_nhgetch();
static int and_nh_poskey(coordxy *, coordxy *, int *);
static void and_nhbell();
static int and_doprev_message();
static char and_yn_function(const char *, const char *, char);
static void and_getlin(const char *,char *);
static int and_get_ext_cmd();
static void and_number_pad(int);
static void and_delay_output();
#ifdef CHANGE_COLOR
static void and_change_color(int color,long rgb,int reverse);
static char * and_get_color_string();
#endif
static void and_start_screen();
static void and_end_screen();
win_request_info *and_ctrl_nhwindow(winid, int, win_request_info *);
color_attr and_menu_promptstyle = { NO_COLOR, ATR_NONE };

static char* and_getmsghistory(boolean);
static void and_putmsghistory(const char *, boolean);
static void and_status_update(int, genericptr_t, int, int, int, unsigned long *);

static int cond_color(long, const unsigned long *);
static int cond_attr(long, const unsigned long *);
int NetHackMain(int argc, char** argv);

struct window_procs and_procs = {
        "and",
        wp_and,
        WC_COLOR | WC_HILITE_PET | WC_INVERSE,	/* window port capability options supported */
        WC2_HILITE_STATUS | WC2_FLUSH_STATUS | WC2_HITPOINTBAR,	/* additional window port capability options supported */
        {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1},   /* color availability */
        and_init_nhwindows,
        and_player_selection,
        and_askname,
        and_get_nh_event,
        and_exit_nhwindows,
        and_suspend_nhwindows,
        and_resume_nhwindows,
        and_create_nhwindow,
        and_clear_nhwindow,
        and_display_nhwindow,
        and_destroy_nhwindow,
        and_curs,
        and_putstr,
        and_putmixed,
        and_display_file,
        and_start_menu,
       and_add_menu,
        and_end_menu,
        and_select_menu,
        genl_message_menu,
        and_mark_synch,
        and_wait_synch,
#ifdef CLIPPING
        and_cliparound,
#endif
#ifdef POSITIONBAR
        and_update_positionbar,
#endif
        and_print_glyph,
        and_raw_print,
        and_raw_print_bold,
        and_nhgetch,
        and_nh_poskey,
        and_nhbell,
        and_doprev_message,
        and_yn_function,
        and_getlin,
        and_get_ext_cmd,
        and_number_pad,
        and_delay_output,
#ifdef CHANGE_COLOR
        and_change_color,
	    and_get_color_string,
#endif
        and_start_screen,
        and_end_screen,
        genl_outrip,
        genl_preference_update,
        and_getmsghistory,
        and_putmsghistory,
        genl_status_init,
        genl_status_finish,
        genl_status_enablefield,
        and_status_update,
        genl_can_suspend_no,
        and_update_inventory,
        and_ctrl_nhwindow
};

//____________________________________________________________________________________
// Java objects. Make sure they are not garbage collected!
static JNIEnv* jEnv;
static jclass jApp;
static jobject jAppInstance;
static jmethodID jRequireKeyCommand;
static jmethodID jRequirePosKeyCommand;
static jmethodID jCreateWindow;
static jmethodID jDisplayWindow;
static jmethodID jClearWindow;
static jmethodID jDestroyWindow;
static jmethodID jPutString;
static jmethodID jRawPrint;
static jmethodID jCurs;
static jmethodID jPrintTile;
static jmethodID jYNFunction;
static jmethodID jGetLine;
static jmethodID jStartMenu;
static jmethodID jAddMenu;
static jmethodID jEndMenu;
static jmethodID jSelectMenu;
static jmethodID jClipAround;
static jmethodID jDelayOutput;
static jmethodID jSetNumPadOption;
static jmethodID jAskName;
static jmethodID jRenderStatus;
static jmethodID jShowExtCmdMenu;
static jmethodID jGetMessageHistory;
static jmethodID jPutMessageHistory;

// status
extern const char *status_fieldfmt[MAXBLSTATS];
extern const char *status_fieldnm[MAXBLSTATS];
extern boolean status_activefields[MAXBLSTATS];
extern char *status_vals[MAXBLSTATS];

static int status_colors[MAXBLSTATS];
static int status_percent[MAXBLSTATS];
static char *status_real_values[MAXBLSTATS];
static int status_attrmasks[MAXBLSTATS];
static unsigned long* and_colormasks;
static long and_condition_bits = 0L;


#define JNICallV(func, ...) (*jEnv)->CallVoidMethod(jEnv, jAppInstance, func, ## __VA_ARGS__);
#define JNICallI(func, ...) (*jEnv)->CallIntMethod(jEnv, jAppInstance, func, ## __VA_ARGS__);
#define JNICallO(func, ...) (*jEnv)->CallObjectMethod(jEnv, jAppInstance, func, ## __VA_ARGS__);
#define JNICallC(func, ...) (*jEnv)->CallCharMethod(jEnv, jAppInstance, func, ## __VA_ARGS__);


JNIEXPORT void JNICALL
Java_com_yywspace_anethack_NetHack_runNetHack(JNIEnv *env, jobject thiz, jstring path) {
    char* params[10];
    const char *path_c = (*env)->GetStringUTFChars(env, path, 0);
    params[0] = "NetHack";
    params[1] = strdup(path_c);

    jEnv = env;
    jAppInstance = thiz;
    jApp = (*jEnv)->GetObjectClass(jEnv, jAppInstance);
    jCreateWindow = (*jEnv)->GetMethodID(jEnv,jApp, "createWindow", "(I)I");
    jStartMenu = (*jEnv)->GetMethodID(jEnv, jApp, "startMenu", "(IJ)V");
    jAddMenu = (*jEnv)->GetMethodID(jEnv, jApp, "addMenu", "(IIJCCIILjava/lang/String;Z)V");
    jEndMenu = (*jEnv)->GetMethodID(jEnv, jApp, "endMenu", "(ILjava/lang/String;)V");
    jSelectMenu = (*jEnv)->GetMethodID(jEnv, jApp, "selectMenu", "(II)[J");
    jDestroyWindow = (*jEnv)->GetMethodID(jEnv, jApp, "destroyWindow", "(I)V");
    jClearWindow = (*jEnv)->GetMethodID(jEnv, jApp, "clearWindow", "(II)V");
    jRawPrint = (*jEnv)->GetMethodID(jEnv, jApp, "rawPrint", "(ILjava/lang/String;)V");
    jDisplayWindow = (*jEnv)->GetMethodID(jEnv, jApp, "displayWindow", "(IZ)V");
    jCurs = (*jEnv)->GetMethodID(jEnv, jApp, "curs", "(III)V");
    jPutString = (*jEnv)->GetMethodID(jEnv, jApp, "putString", "(IILjava/lang/String;I)V");
    jPrintTile = (*jEnv)->GetMethodID(jEnv, jApp, "printTile", "(IIIIIII)V");
    jRequireKeyCommand = (*jEnv)->GetMethodID(jEnv, jApp, "requireKeyCommand", "()I");
    jRequirePosKeyCommand = (*jEnv)->GetMethodID(jEnv, jApp, "requirePosKeyCommand", "([I)C");
    jRenderStatus = (*jEnv)->GetMethodID(jEnv, jApp, "renderStatus", "(ILjava/lang/String;Ljava/lang/String;III)V");
    jDelayOutput = (*jEnv)->GetMethodID(jEnv, jApp, "delayOutput", "()V");
    jClipAround = (*jEnv)->GetMethodID(jEnv, jApp, "clipAround", "(IIII)V");
    jYNFunction = (*jEnv)->GetMethodID(jEnv, jApp, "ynFunction",
                                       "(Ljava/lang/String;Ljava/lang/String;[JC)C");
    jGetLine = (*jEnv)->GetMethodID(jEnv, jApp, "getLine", "(Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;");
    jAskName = (*jEnv)->GetMethodID(jEnv, jApp, "askName","(I[Ljava/lang/String;)[Ljava/lang/String;");
    jShowExtCmdMenu = (*jEnv)->GetMethodID(jEnv, jApp, "showExtCmdMenu", "([Ljava/lang/String;)I");
    jGetMessageHistory = (*jEnv)->GetMethodID(jEnv, jApp, "getMessageHistory","(I)Ljava/lang/String;");
    jPutMessageHistory = (*jEnv)->GetMethodID(jEnv, jApp, "putMessageHistory", "(Ljava/lang/String;Z)V");

    NetHackMain(2, params);
}

JNIEXPORT void JNICALL
Java_com_yywspace_anethack_NetHack_stopNetHack(JNIEnv *env, jobject thiz) {
    nh_terminate(EXIT_SUCCESS);
}

void more(void){

}

//____________________________________________________________________________________
//init_nhwindows(int* argcp, char** argv)
//		-- Initialize the windows used by NetHack.  This can also
//		   create the standard windows listed at the top, but does
//		   not display them.
//		-- Any commandline arguments relevant to the windowport
//		   should be interpreted, and *argcp and *argv should
//		   be changed to remove those arguments.
//		-- When the message window is created, the variable
//		   iflags.window_inited needs to be set to TRUE.  Otherwise
//		   all plines() will be done via raw_print().
//		** Why not have init_nhwindows() create all of the "standard"
//		** windows?  Or at least all but WIN_INFO?	-dean
void and_init_nhwindows(int* argcp, char** argv)
{
    LOGD("and_init_nhwindows");
    iflags.window_inited = TRUE;
}

//player_selection()
//		-- Do a window-port specific player type selection.  If
//		   player_selection() offers a Quit option, it is its
//		   responsibility to clean up and terminate the process.
//		   You need to fill in pl_character[0].
void and_player_selection()
{
    int i, result, picked_state[RS_ALGNMNT+1];
    char thisch, lastch = 0;
    winid win;
    anything any;
    menu_item *selected = 0;

    flags.initrole = flags.initrace = flags.initgend = flags.initalign
            = ROLE_NONE;
    // 返回时，取消最后一个选择过的属性
    previous:
    for (i = RS_ALGNMNT; i >= RS_ROLE; --i)
        if(picked_state[i] == 1) {
            picked_state[i] = 0;
            if(i == RS_ROLE) flags.initrole = ROLE_NONE;
            if(i == RS_RACE) flags.initrace = ROLE_NONE;
            if(i == RS_GENDER) flags.initgend = ROLE_NONE;
            break;
        }
    /* Select a role */
    if(flags.initrole == ROLE_NONE) {
        win = create_nhwindow(NHW_MENU);
        start_menu(win, MENU_BEHAVE_STANDARD);
        any = cg.zeroany; /* zero out all bits */
        any.a_int = randrole(TRUE)+1;
        add_menu(win, &nul_glyphinfo, &any, '*', 0, ATR_NONE, 0, "Random", MENU_ITEMFLAGS_SELECTED);
        for(i = 0; roles[i].name.m; i++)
        {
            if(ok_role(i, flags.initrace, flags.initgend, flags.initalign)) {
                any.a_int = i + 1; /* must be non-zero */
                thisch = lowc(roles[i].name.m[0]);
                if(thisch == lastch)
                    thisch = highc(thisch);
                add_menu(win, &nul_glyphinfo, &any, thisch, 0, ATR_NONE, 0, roles[i].name.m, MENU_ITEMFLAGS_NONE);
                lastch = thisch;
            }
        }
        end_menu(win, "Pick a role or profession");
        do
            result = select_menu(win, PICK_ONE, &selected);
        while (result == 0); // if select nothing, prompt again
        destroy_nhwindow(win), win = WIN_ERR;
        if(result > 0) {
            flags.initrole = selected[0].item.a_int - 1;
            picked_state[RS_ROLE] = 1;
        } else {
            // if cancel first menu select, exit game
            clearlocks();
            exit_nhwindows("bye");
            nh_terminate(EXIT_SUCCESS);
        }
        if (selected)
            free((genericptr_t) selected), selected = 0;
    }

    /* Select a race, if necessary */
    if(flags.initrace == ROLE_NONE)
        flags.initrace = pick_race(flags.initrole, flags.initgend, flags.initalign, PICK_RIGID);
    if(flags.initrace == ROLE_NONE) {
        win = create_nhwindow(NHW_MENU);
        start_menu(win,MENU_BEHAVE_STANDARD);
        any.a_void = 0; /* zero out all bits */
        any.a_int = randrace(flags.initrole)+1;
        add_menu(win, &nul_glyphinfo, &any, '*', 0, ATR_NONE,0, "random", MENU_ITEMFLAGS_SELECTED);
        for(i = 0; races[i].noun; i++)
            if(ok_race(flags.initrole, i, flags.initgend, flags.initalign)) {
                any.a_int = i + 1; /* must be non-zero */
                add_menu(win, &nul_glyphinfo, &any, races[i].noun[0], 0, ATR_NONE, 0, races[i].noun, MENU_ITEMFLAGS_NONE);
            }
        end_menu(win, "Pick a race or species");
        do
            result = select_menu(win, PICK_ONE, &selected);
        while (result == 0); // if select nothing, prompt again
        destroy_nhwindow(win), win = WIN_ERR;
        if(result > 0) {
            flags.initrace = selected[0].item.a_int - 1;
            picked_state[RS_RACE] = 1;
        } else // if cancel, go to previous
            goto previous;
        if (selected)
            free((genericptr_t) selected), selected = 0;
    }

    /* Select a gender, if necessary */
    if(flags.initgend == ROLE_NONE)
        flags.initgend = pick_gend(flags.initrole, flags.initrace, flags.initalign, PICK_RIGID);
    if(flags.initgend == ROLE_NONE) {
        win = create_nhwindow(NHW_MENU);
        start_menu(win,MENU_BEHAVE_STANDARD);
        any.a_void = 0; /* zero out all bits */
        any.a_int = randgend(flags.initrole, flags.initrace)+1;
        add_menu(win, &nul_glyphinfo, &any, '*', 0, ATR_NONE,0, "random", MENU_ITEMFLAGS_SELECTED);
        for(i = 0; i < ROLE_GENDERS; i++)
            if(ok_gend(flags.initrole, flags.initrace, i, flags.initalign)) {
                any.a_int = i + 1;
                add_menu(win, &nul_glyphinfo, &any, genders[i].adj[0], 0, ATR_NONE, 0,genders[i].adj, MENU_ITEMFLAGS_NONE);
            }
        end_menu(win, "Pick a gender or sex");
        do
            result = select_menu(win, PICK_ONE, &selected);
        while (result == 0); // if select nothing, prompt again
        destroy_nhwindow(win), win = WIN_ERR;
        if(result > 0) {
            flags.initgend = selected[0].item.a_int - 1;
            picked_state[RS_GENDER] = 1;
        } else // if cancel, go to previous
            goto previous;
        if (selected)
            free((genericptr_t) selected), selected = 0;
    }

    /* Select an alignment, if necessary */
    if(flags.initalign == ROLE_NONE)
        flags.initalign = pick_align(flags.initrole, flags.initrace, flags.initgend, PICK_RIGID);
    if(flags.initalign == ROLE_NONE)
    {
        win = create_nhwindow(NHW_MENU);
        start_menu(win, MENU_BEHAVE_STANDARD);
        any.a_void = 0; /* zero out all bits */
        any.a_int = randalign(flags.initrole, flags.initrace)+1;
        add_menu(win, &nul_glyphinfo, &any, '*', 0, ATR_NONE, 0,"random", MENU_ITEMFLAGS_SELECTED);
        for(i = 0; i < ROLE_ALIGNS; i++)
            if(ok_align(flags.initrole, flags.initrace, flags.initgend, i)) {
                any.a_int = i + 1;
                add_menu(win, &nul_glyphinfo, &any, aligns[i].adj[0], 0, ATR_NONE, 0, aligns[i].adj, MENU_ITEMFLAGS_NONE);
            }
        end_menu(win, "Pick an alignment or creed");
        do
            result = select_menu(win, PICK_ONE, &selected);
        while (result == 0); // if select nothing, prompt again
        destroy_nhwindow(win), win = WIN_ERR;
        if(result > 0)
            flags.initalign = selected[0].item.a_int - 1;
        else // if cancel, go to previous
            goto previous;
    }
}

//____________________________________________________________________________________
//get_nh_event()	-- Does window event processing (e.g. exposure events).
//		   A noop for the tty and X window-ports.
void and_get_nh_event()
{
    LOGD("and_get_nh_event()");
}

//____________________________________________________________________________________
//exit_nhwindows(str)
//		-- Exits the window system.  This should dismiss all windows,
//		   except the "window" used for raw_print().  str is printed
//		   if possible.
void and_exit_nhwindows(const char *str)
{
    LOGD("and_exit_nhwindows(str:%s)", str);
    iflags.window_inited = FALSE;
}

//____________________________________________________________________________________
//suspend_nhwindows(str)
//		-- Prepare the window to be suspended.
void and_suspend_nhwindows(const char *str)
{
    LOGD("and_suspend_nhwindows(str:%s)", str);
}

//____________________________________________________________________________________
//resume_nhwindows()
//		-- Restore the windows after being suspended.
void and_resume_nhwindows()
{
    LOGD("and_resume_nhwindows()");
}

//____________________________________________________________________________________
//window = create_nhwindow(type)
//		-- Create a window of type "type."
winid and_create_nhwindow(int type)
{
    LOGD("and_create_nhwindow: %d", type);
    return JNICallI(jCreateWindow, type)
}

//____________________________________________________________________________________
//clear_nhwindow(window)
//		-- Clear the given window, when appropriate.
void and_clear_nhwindow(winid wid)
{
    LOGD("and_clear_nhwindow(wid: %d)", wid);
    JNICallV(jClearWindow, wid, Is_rogue_level(&u.uz))
}

//____________________________________________________________________________________
//display_nhwindow(window, boolean blocking)
//		-- Display the window on the screen.  If there is data
//		   pending for output in that window, it should be sent.
//		   If blocking is TRUE, display_nhwindow() will not
//		   return until the data has been displayed on the screen,
//		   and acknowledged by the user where appropriate.
//		-- All calls are blocking in the tty window-port.
//		-- Calling display_nhwindow(WIN_MESSAGE,???) will do a
//		   --more--, if necessary, in the tty window-port.
void and_display_nhwindow(winid wid, boolean blocking)
{
    LOGD("display_nhwindow(wid:%d, blocking:%d)", wid, blocking);
    if(wid != WIN_MESSAGE && wid != WIN_STATUS && wid != WIN_MAP)
        blocking = TRUE;
    JNICallV(jDisplayWindow, wid, blocking)
    if(blocking)
        and_nhgetch();
}

//____________________________________________________________________________________
//destroy_nhwindow(window)
//		-- Destroy will dismiss the window if the window has not
//		   already been dismissed.
void and_destroy_nhwindow(winid wid)
{
    LOGD("and_destroy_nhwindow(%d)", wid);
    JNICallV(jDestroyWindow, wid)
}

//____________________________________________________________________________________
//curs(window, x, y)
//		-- Next output to window will start at (x,y), also moves
//		   displayable cursor to (x,y).  For backward compatibility,
//		   1 <= x < cols, 0 <= y < rows, where cols and rows are
//		   the size of window.
//		-- For variable sized windows, like the status window, the
//		   behavior when curs() is called outside the window's limits
//		   is unspecified. The mac port wraps to 0, with the status
//		   window being 2 lines high and 80 columns wide.
//		-- Still used by curs_on_u(), status updates, screen locating
//		   (identify, teleport).
//		-- NHW_MESSAGE, NHW_MENU and NHW_TEXT windows do not
//		   currently support curs in the tty window-port.
void and_curs(winid wid, int x, int y)
{
    LOGD("and_curs(wid:%d, x:%d, y:%d)", wid, x, y);
    JNICallV(jCurs, wid, x, y)
}

static int text_color = CLR_WHITE;

const char* colname(int color)
{
    switch(color)
    {
        case CLR_BLACK: return "black";
        case CLR_RED: return "red";
        case CLR_GREEN: return "green";
        case CLR_BROWN: return "brown";
        case CLR_BLUE: return "blue";
        case CLR_MAGENTA: return "magenta";
        case CLR_CYAN: return "cyan";
        case CLR_GRAY: return "gray";
        case NO_COLOR: return "no color";
        case CLR_ORANGE: return "orange";
        case CLR_BRIGHT_GREEN: return "bright green";
        case CLR_YELLOW: return "yellow";
        case CLR_BRIGHT_BLUE: return "bright blue";
        case CLR_BRIGHT_MAGENTA: return "bright magenta";
        case CLR_BRIGHT_CYAN: return "bright cyan";
        case CLR_WHITE: return "white";
        default: return "black";
    }
}

/*
putstr(window, attr, str)
    -- Print str on the window with the given attribute.  Only
       printable ASCII characters (040-0126) must be supported.
       Multiple putstr()s are output on separate lines.  Attributes
       can be one of
        ATR_NONE (or 0)
        ATR_ULINE
        ATR_BOLD
        ATR_BLINK
        ATR_INVERSE
       If a window-port does not support all of these, it may map
       unsupported attributes to a supported one (e.g. map them
       all to ATR_INVERSE).  putstr() may compress spaces out of
       str, break str, or truncate str, if necessary for the
       display.  Where putstr() breaks a line, it has to clear
       to end-of-line.
    -- putstr should be implemented such that if two putstr()s
       are done consecutively the user will see the first and
       then the second.  In the tty port, pline() achieves this
       by calling more() or displaying both on the same line.
 */
void and_putstr(winid wid, int attr, const char *str)
{

    if(!str || !*str) return;
    if(attr) attr = 1<<attr;
    jstring jstr = (*jEnv)->NewStringUTF(jEnv, str);
    JNICallV(jPutString, wid, attr, jstr, NO_COLOR)
}

void and_status_field_render(int idx, const char *filed_name, const char *value, int attr, int color, int percent) {
    jstring jFiledName = (*jEnv)->NewStringUTF(jEnv, filed_name);
    jstring jValue = (*jEnv)->NewStringUTF(jEnv, value);
    JNICallV(jRenderStatus, idx, jFiledName, jValue, attr, color, percent)
}


/*
 *  *_status_update()
 *      -- update the value of a status field.
 *      -- the fldindex identifies which field is changing and
 *         is an integer index value from botl.h
 *      -- fldindex could be any one of the following from botl.h:
 *         BL_TITLE, BL_STR, BL_DX, BL_CO, BL_IN, BL_WI, BL_CH,
 *         BL_ALIGN, BL_SCORE, BL_CAP, BL_GOLD, BL_ENE, BL_ENEMAX,
 *         BL_XP, BL_AC, BL_HD, BL_TIME, BL_HUNGER, BL_HP, BL_HPMAX,
 *         BL_LEVELDESC, BL_EXP, BL_CONDITION
 *      -- fldindex could also be BL_FLUSH (-1), which is not really
 *         a field index, but is a special trigger to tell the
 *         windowport that it should output all changes received
 *         to this point. It marks the end of a bot() cycle.
 *      -- fldindex could also be BL_RESET (-3), which is not really
 *         a field index, but is a special advisory to to tell the
 *         windowport that it should redisplay all its status fields,
 *         even if no changes have been presented to it.
 *      -- ptr is usually a "char *", unless fldindex is BL_CONDITION.
 *         If fldindex is BL_CONDITION, then ptr is a long value with
 *         any or none of the following bits set (from botl.h):
 *               BL_MASK_BAREH        0x00000001L
 *               BL_MASK_BLIND        0x00000002L
 *               BL_MASK_BUSY         0x00000004L
 *               BL_MASK_CONF         0x00000008L
 *               BL_MASK_DEAF         0x00000010L
 *               BL_MASK_ELF_IRON     0x00000020L
 *               BL_MASK_FLY          0x00000040L
 *               BL_MASK_FOODPOIS     0x00000080L
 *               BL_MASK_GLOWHANDS    0x00000100L
 *               BL_MASK_GRAB         0x00000200L
 *               BL_MASK_HALLU        0x00000400L
 *               BL_MASK_HELD         0x00000800L
 *               BL_MASK_ICY          0x00001000L
 *               BL_MASK_INLAVA       0x00002000L
 *               BL_MASK_LEV          0x00004000L
 *               BL_MASK_PARLYZ       0x00008000L
 *               BL_MASK_RIDE         0x00010000L
 *               BL_MASK_SLEEPING     0x00020000L
 *               BL_MASK_SLIME        0x00040000L
 *               BL_MASK_SLIPPERY     0x00080000L
 *               BL_MASK_STONE        0x00100000L
 *               BL_MASK_STRNGL       0x00200000L
 *               BL_MASK_STUN         0x00400000L
 *               BL_MASK_SUBMERGED    0x00800000L
 *               BL_MASK_TERMILL      0x01000000L
 *               BL_MASK_TETHERED     0x02000000L
 *               BL_MASK_TRAPPED      0x04000000L
 *               BL_MASK_UNCONSC      0x08000000L
 *               BL_MASK_WOUNDEDL     0x10000000L
 *               BL_MASK_HOLDING      0x20000000L
 *
 *      -- The value passed for BL_GOLD usually includes an encoded leading
 *         symbol for GOLD "\GXXXXNNNN:nnn". If the window port needs to use
 *         the textual gold amount without the leading "$:" the port will
 *         have to skip past ':' in the passed "ptr" for the BL_GOLD case.
 *      -- color is an unsigned int.
 *               color_index = color & 0x00FF;         CLR_* value
 *               attribute   = (color >> 8) & 0x00FF;  HL_ATTCLR_* mask
 *         This holds the color and attribute that the field should
 *         be displayed in.
 *         This is relevant for everything except BL_CONDITION fldindex.
 *         If fldindex is BL_CONDITION, this parameter should be ignored,
 *         as condition highlighting is done via the next colormasks
 *         parameter instead.
 *
 *      -- colormasks - pointer to cond_hilites[] array of colormasks.
 *         Only relevant for BL_CONDITION fldindex. The window port
 *         should ignore this parameter for other fldindex values.
 *         Each condition bit must only ever appear in one of the
 *         CLR_ array members, but can appear in multiple HL_ATTCLR_
 *         offsets (because more than one attribute can co-exist).
 *         See doc/window.txt for more details.
 */
int hl_attridx2atr(int idx)
{
    switch(idx)
    {
        case HL_ATTCLR_DIM: 	return (1<<ATR_DIM);
        case HL_ATTCLR_BLINK:	return (1<<ATR_BLINK);
        case HL_ATTCLR_ULINE:   return (1<<ATR_ULINE);
        case HL_ATTCLR_INVERSE:	return (1<<ATR_INVERSE);
        case HL_ATTCLR_BOLD:	return (1<<ATR_BOLD);
        default:
            return 0;
    }
}

int hl_attrmask2atr(int mask)
{
    int attr = 0;
    if(mask & HL_DIM) attr |= (1<<ATR_DIM);
    if(mask & HL_BLINK) attr |= (1<<ATR_BLINK);
    if(mask & HL_ULINE) attr |= (1<<ATR_ULINE);
    if(mask & HL_INVERSE) attr |= (1<<ATR_INVERSE);
    if(mask & HL_BOLD) attr |= (1<<ATR_BOLD);
    return attr;
}


static const enum statusfields status_elements[] = {
        BL_TITLE, BL_STR, BL_DX, BL_CO, BL_IN, BL_WI, BL_CH,BL_SCORE,
        BL_ALIGN, BL_GOLD, BL_HP, BL_HPMAX, BL_ENE, BL_ENEMAX,BL_AC, BL_XP, BL_EXP, BL_HD, BL_HUNGER,BL_CAP,
        BL_LEVELDESC, BL_TIME, BL_CONDITION, BL_FLUSH
    };


static void render_status(void) {
    long mask, bits;

    int i, c, ci, idx, attrmask, coloridx, percent;
    for (i = 0; (idx = status_elements[i]) != BL_FLUSH; ++i) {
        if (!status_activefields[idx])
            continue;
        percent = status_percent[idx];
        if (idx == BL_CONDITION) {
            // for condition
            bits = and_condition_bits;
            if(bits == 0L) {
                and_status_field_render(
                        idx, "conditions", "",
                        attrmask, coloridx, percent);
                continue;
            }
            for (c = 0; c < SIZE(conditions) && bits != 0L; ++c) {
                ci = cond_idx[c];
                mask = conditions[ci].mask;
                if (bits & mask) {
                    const char *condtext = conditions[ci].text[0];
                    if (iflags.hilite_delta) {
                        attrmask = cond_attr(mask, and_colormasks);
                        coloridx = cond_color(mask, and_colormasks);
                    }
                    LOGD("conditions:%s", condtext);
                    and_status_field_render(
                            idx, "conditions", condtext,
                            attrmask, coloridx, percent);
                    bits &= ~mask;
                }
            }
        } else {
            // for other
            attrmask = status_attrmasks[idx];
            coloridx = status_colors[idx];

            const char *field_nm = status_fieldnm[idx];
            char *fmt_val = status_vals[idx];
            char *real_val = status_real_values[idx];
            and_status_field_render(
                    idx, field_nm, fmt_val,
                    hl_attrmask2atr(attrmask), coloridx, percent);
        }
    }
    display_nhwindow(WIN_STATUS, FALSE);
}

void and_status_update(int fldidx, genericptr_t ptr, int chg UNUSED, int percent, int color, unsigned long *colormasks)
{
    long *condptr = (long *) ptr;
    char goldbuf[40], *text = (char *) ptr;

    if ((fldidx < BL_RESET) || (fldidx >= MAXBLSTATS))
        return;

    if ((fldidx >= 0 && fldidx < MAXBLSTATS) && !status_activefields[fldidx])
        return;
    switch(fldidx) {
        case BL_RESET:
            LOGD("BL_RESET");
        case BL_FLUSH:
            LOGD("BL_FLUSH");
            render_status();
            return;
        case BL_CONDITION:
            LOGD("BL_CONDITION");
            and_condition_bits = *condptr;
            and_colormasks = colormasks;
            break;
        case BL_GOLD:
             text = decode_mixed(goldbuf, text);
        default:
            status_attrmasks[fldidx] = (color >> 8) & 0x00FF;
            status_colors[fldidx] = (color & 0x00FF);
            status_percent[fldidx] = percent;
            status_real_values[fldidx] = text;
            Sprintf(status_vals[fldidx], status_fieldfmt[fldidx] ? status_fieldfmt[fldidx] : "%s", text ? text : "");
            LOGD("STATUS[name:%s, attr:%d, color:%s, format:%s, text:%s, %s]",
                 status_fieldnm[fldidx], status_attrmasks[fldidx], colname(status_colors[fldidx]), status_fieldfmt[fldidx], text, status_vals[fldidx]);
    }
}

static int cond_color(long bm, const unsigned long *bmarray)
{
    int i;
    if (bm && bmarray)
        for (i = 0; i < CLR_MAX; ++i) {
            if ((bm & bmarray[i]) != 0)
                return i;
        }
    return NO_COLOR;
}

static int cond_attr(long bm, const unsigned long *bmarray)
{
    int attr = 0;
    int i;

    if (bm && bmarray) {
        for (i = HL_ATTCLR_DIM; i < BL_ATTCLR_MAX; ++i) {
            if ((bm & bmarray[i]) != 0)
                attr |= hl_attridx2atr(i);
        }
    }
    return attr;
}

//____________________________________________________________________________________
void and_putmixed(winid wid, int attr, const char *str)
{
    LOGD("and_putmixed(wid:%d, attr:%d, str:%s)", wid, attr, str);
    genl_putmixed(wid, attr, str);
}

//____________________________________________________________________________________
//display_file(str, boolean complain)
//		-- Display the file named str.  Complain about missing files
//		   iff complain is TRUE.
void and_display_file(const char *name, boolean complain)
{
    LOGD("and_display_file(name:%s, complain:%d)", name, complain);
    dlb* fd;
    char buf[BUFSZ], *cr;
    fd = dlb_fopen(name, "r");
    if(fd) {
        winid data_win = create_nhwindow(NHW_TEXT);
        boolean empty = TRUE;
        while(dlb_fgets(buf, BUFSZ, fd))
        {
            if((cr = strchr(buf, '\n')) != 0)
                *cr = 0;
            if(strchr(buf, '\t') != 0)
                (void)tabexpand(buf);
            empty = FALSE;
            putstr(data_win, 0, buf);
        }
        (void)dlb_fclose(fd);
        if(!empty)
            display_nhwindow(data_win, TRUE);
        destroy_nhwindow(data_win);
    }
}

//____________________________________________________________________________________
//start_menu(window)
//		-- Start using window as a menu.  You must call start_menu()
//		   before add_menu().  After calling start_menu() you may not
//		   putstr() to the window.  Only windows of type NHW_MENU may
//		   be used for menus.
void and_start_menu(winid wid,unsigned long mbehavior)
{
    JNICallV(jStartMenu, wid, mbehavior)
}

//____________________________________________________________________________________
//add_menu(windid window, int glyph, const anything identifier,
//				char accelerator, char groupacc,
//				int attr, char *str, boolean preselected)
//		-- Add a text line str to the given menu window.  If identifier
//		   is 0, then the line cannot be selected (e.g. a title).
//		   Otherwise, identifier is the value returned if the line is
//		   selected.  Accelerator is a keyboard key that can be used
//		   to select the line.  If the accelerator of a selectable
//		   item is 0, the window system is free to select its own
//		   accelerator.  It is up to the window-port to make the
//		   accelerator visible to the user (e.g. put "a - " in front
//		   of str).  The value attr is the same as in putstr().
//		   Glyph is an optional glyph to accompany the line.  If
//		   window port cannot or does not want to display it, this
//		   is OK.  If there is no glyph applicable, then this
//		   value will be NO_GLYPH.
//		-- All accelerators should be in the range [A-Za-z],
//		   but there are a few exceptions such as the tty player
//		   selection code which uses '*'.
//	        -- It is expected that callers do not mix accelerator
//		   choices.  Either all selectable items have an accelerator
//		   or let the window system pick them.  Don't do both.
//		-- Groupacc is a group accelerator.  It may be any character
//		   outside of the standard accelerator (see above) or a
//		   number.  If 0, the item is unaffected by any group
//		   accelerator.  If this accelerator conflicts with
//		   the menu command (or their user defined alises), it loses.
//		   The menu commands and aliases take care not to interfere
//		   with the default object class symbols.
//		-- If you want this choice to be preselected when the
//		   menu is displayed, set preselected to TRUE.
void and_add_menu(winid window, const glyph_info * glyph, const union any * identifier, char accelerator,
        char groupacc, int attr, int clr, const char *str, unsigned int itemflags)
{
    boolean preselected = (itemflags & MENU_ITEMFLAGS_SELECTED) != 0;
    int tile = glyph->gm.tileidx;
    int menu_color = clr, menu_attr = attr;
    LOGD("and_add_menu attr=%d, tile=%d, color=%d,accelerator:%c address:%ld",menu_attr, tile, menu_color, accelerator, (long )identifier->a_lptr);
    if(menu_attr)
        menu_attr = 1<<menu_attr;
    jstring jstr = (*jEnv)->NewStringUTF(jEnv,str);
    JNICallV(jAddMenu, window, tile, (long )identifier->a_lptr, accelerator, groupacc, menu_attr, menu_color, jstr, preselected)
}

//____________________________________________________________________________________
//end_menu(window, prompt)
//		-- Stop adding entries to the menu and flushes the window
//		   to the screen (brings to front?).  Prompt is a prompt
//		   to give the user.  If prompt is NULL, no prompt will
//		   be printed.
//		** This probably shouldn't flush the window any more (if
//		** it ever did).  That should be select_menu's job.  -dean
void and_end_menu(winid wid, const char *prompt)
{

    jstring jstr = (*jEnv)->NewStringUTF(jEnv, prompt);
    JNICallV(jEndMenu, wid, jstr)
}

//____________________________________________________________________________________
//int select_menu(windid window, int how, menu_item **selected)
//		-- Return the number of items selected; 0 if none were chosen,
//		   -1 when explicitly cancelled.  If items were selected, then
//		   selected is filled in with an allocated array of menu_item
//		   structures, one for each selected line.  The caller must
//		   free this array when done with it.  The "count" field
//		   of selected is a user supplied count.  If the user did
//		   not supply a count, then the count field is filled with
//		   -1 (meaning all).  A count of zero is equivalent to not
//		   being selected and should not be in the list.  If no items
//		   were selected, then selected is NULL'ed out.  How is the
//		   mode of the menu.  Three valid values are PICK_NONE,
//		   PICK_ONE, and PICK_ANY, meaning: nothing is selectable,
//		   only one thing is selectable, and any number valid items
//		   may selected.  If how is PICK_NONE, this function should
//		   never return anything but 0 or -1.
//		-- You may call select_menu() on a window multiple times --
//		   the menu is saved until start_menu() or destroy_nhwindow()
//		   is called on the window.
//		-- Note that NHW_MENU windows need not have select_menu()
//		   called for them. There is no way of knowing whether
//		   select_menu() will be called for the window at
//		   create_nhwindow() time.
int and_select_menu(winid wid, int how, MENU_ITEM_P **selected)
{
    jlong *p, *q;
    jlongArray selectedItems = (jlongArray)JNICallO(jSelectMenu, wid, how)
    *selected = 0;
    int itemCnt = (*jEnv)->GetArrayLength(jEnv, selectedItems);
    LOGD("and_select_menu itemCnt:%d", itemCnt);
    if(itemCnt > 1)  { // n should always be 2k (id, count) pairs
        itemCnt >>= 1;
        q = p = (*jEnv)->GetLongArrayElements(jEnv, selectedItems, NULL);
        *selected = (MENU_ITEM_P*)malloc(sizeof(MENU_ITEM_P) * itemCnt);
        for(int i = 0; i < itemCnt; i++)
        {
            LOGD("and_select_menu address:%ld count:%d", (long)(*p), (int)*(p+1));
            // convert identifier address to anything
            (*selected)[i].item.a_lptr = (long *)(*p++);
            (*selected)[i].count = (int)*p++;
        }
        (*jEnv)->ReleaseLongArrayElements(jEnv, selectedItems, q, 0);
    } else if(itemCnt == 1) {
        //  0 if none were chosen, -1 when explicitly cancelled
        jlong *result =  (*jEnv)->GetLongArrayElements(jEnv, selectedItems, NULL);
        return (int)*result;
    }
    (*jEnv)->DeleteLocalRef(jEnv, selectedItems);
    return itemCnt;
}


//____________________________________________________________________________________
//update_inventory()
//		-- Indicate to the window port that the inventory has been
//		   changed.
//		-- Merely calls display_inventory() for window-ports that
//		   leave the window up, otherwise empty.
void and_update_inventory()
{
    LOGD("and_update_inventory()");
}

//____________________________________________________________________________________
//mark_synch()	-- Don't go beyond this point in I/O on any channel until
//		   all channels are caught up to here.  Can be an empty call
//		   for the moment
void and_mark_synch()
{
    LOGD("and_mark_synch");
}

//____________________________________________________________________________________
//wait_synch()	-- Wait until all pending output is complete (*flush*() for
//		   streams goes here).
//		-- May also deal with exposure events etc. so that the
//		   display is OK when return from wait_synch().
void and_wait_synch()
{
    LOGD("and_wait_synch");
}

//____________________________________________________________________________________
#ifdef CLIPPING
//cliparound(x, y)-- Make sure that the user is more-or-less centered on the
//		   screen if the playing area is larger than the screen.
//		-- This function is only defined if CLIPPING is defined.
void and_cliparound(int x, int y)
{
	LOGD("and_cliparound %dx%d (%dx%d)", x, y, u.ux, u.uy);
	JNICallV(jClipAround, x, y, u.ux, u.uy)
}

#endif

//____________________________________________________________________________________
#ifdef POSITIONBAR
//update_positionbar(char *features)
//		-- Optional, POSITIONBAR must be defined. Provide some
//		   additional information for use in a horizontal
//		   position bar (most useful on clipped displays).
//		   Features is a series of char pairs.  The first char
//		   in the pair is a symbol and the second char is the
//		   column where it is currently located.
//		   A '<' is used to mark an upstairs, a '>'
//		   for a downstairs, and an '@' for the current player
//		   location. A zero char marks the end of the list.
void and_update_positionbar(char *features)
{
	LOGD("and_update_positionbar(features:%s)", features);
}

#endif

/*
 *print_glyph(window, x, y, glyphinfo, bkglyphinfo)
 *		-- Print a glyph found within the glyphinfo at (x,y) on the
 *                 given window. The glyphs within the glyph_info struct are
 *                 integers and can be mapped to whatever the window-
 *		   port wants (symbol, font, color, attributes, ...there's
 *		   a 1-1 map between glyphs and distinct things on the map).
 *		-- bkglyphinfo contains a background glyph for potential use
 *		   by some graphical or tiled environments to allow the
 *		   depiction to fall against a background consistent with the
 *		   grid around x,y.  If bkglyphinfo->glyph is NO_GLYPH, then
 *		   the parameter should be ignored (do nothing with it).
 */
void and_print_glyph(winid wid, coordxy x, coordxy y, const glyph_info * glyphinfo, const glyph_info * bkglyphinfo)
{
    int ch = glyphinfo->ttychar;
    int color = glyphinfo->gm.sym.color;
    int tile = glyphinfo->gm.tileidx;
    int glyph = glyphinfo->glyph;
    LOGD("and_print_glyph wid=%d x=%d y=%d gryph=%d,color=%d, chd=%d, ch=%c", wid, x, y, glyph, color, ch, ch);

    unsigned int special = glyphinfo->gm.glyphflags;

    special &= ~(MG_CORPSE|MG_INVIS|MG_RIDDEN|MG_STATUE); // TODO support
    if(!iflags.hilite_pet)
        special &= ~MG_PET;
    if(!iflags.hilite_pile)
        special &= ~MG_OBJPILE;
    if(!iflags.use_inverse)
        special &= ~MG_DETECT;

    JNICallV(jPrintTile, wid, x, y, tile, ch, color, special)
}

//____________________________________________________________________________________
// raw_print(str)	-- Print directly to a screen, or otherwise guarantee that
// 		   the user sees str.  raw_print() appends a newline to str.
// 		   It need not recognize ASCII control characters.  This is
// 		   used during startup (before windowing system initialization
// 		   -- maybe this means only error startup messages are raw),
// 		   for error messages, and maybe other "msg" uses.  E.g.
// 		   updating status for micros (i.e, "saving").
void and_raw_print(const char* str)
{
    LOGD("and_raw_print %s", str);
    jstring jstr = (*jEnv)->NewStringUTF(jEnv,str);
    JNICallV(jRawPrint, 1<<ATR_BOLD, jstr)
}

//____________________________________________________________________________________
// raw_print_bold(str)
// 		-- Like raw_print(), but prints in bold/standout (if possible).
void and_raw_print_bold(const char* str)
{
    LOGD("and_raw_print_bold %s", str);
    jstring jstr = (*jEnv)->NewStringUTF(jEnv,str);
    JNICallV(jRawPrint, 1<<ATR_BOLD, jstr)
}

//____________________________________________________________________________________
//int nhgetch()	-- Returns a single character input from the user.
//		-- In the tty window-port, nhgetch() assumes that tgetch()
//		   will be the routine the OS provides to read a character.
//		   Returned character _must_ be non-zero and it must be
//                   non meta-zero too (zero with the meta-bit set).
int and_nhgetch()
{
    int c = JNICallI(jRequireKeyCommand)
    LOGD("and_nhgetch: %c", c);
    return c;
}

//____________________________________________________________________________________
//int nh_poskey(int *x, int *y, int *mod)
//		-- Returns a single character input from the user or a
//		   a positioning event (perhaps from a mouse).  If the
//		   return value is non-zero, a character was typed, else,
//		   a position in the MAP window is returned in x, y and mod.
//		   mod may be one of
//
//			CLICK_1		/* mouse click type 1 */
//			CLICK_2		/* mouse click type 2 */
//
//		   The different click types can map to whatever the
//		   hardware supports.  If no mouse is supported, this
//		   routine always returns a non-zero character.
int and_nh_poskey(coordxy *x, coordxy *y, int *mod)
{
    jintArray jEvent = (*jEnv)->NewIntArray(jEnv, 3);
    char ch = JNICallC(jRequirePosKeyCommand, jEvent)
    if(!ch) {
        int *event = (*jEnv)->GetIntArrayElements(jEnv, jEvent, NULL);
        *x = (coordxy)event[0];
        *y = (coordxy)event[1];
        *mod = event[2];
        (*jEnv)->ReleaseIntArrayElements(jEnv, jEvent, event, 0);
    }
    LOGD("and_nh_poskey x:%d y:%d mod:%d", *x, *y, *mod);
    return ch;
}

//____________________________________________________________________________________
//nhbell()	-- Beep at user.  [This will exist at least until sounds are
//		   redone, since sounds aren't attributable to windows anyway.]
void and_nhbell()
{
    LOGD("and_nhbell");
}

//____________________________________________________________________________________
//doprev_message()
//		-- Display previous messages.  Used by the ^P command.
//		-- On the tty-port this scrolls WIN_MESSAGE back one line.
int and_doprev_message()
{
    LOGD("and_doprev_message");
    display_nhwindow(WIN_MESSAGE, TRUE);
    return ECMD_OK;
}

//____________________________________________________________________________________
// char yn_function(const char *ques, const char *choices, char default)
//		-- Print a prompt made up of ques, choices and default.
//		   Read a single character response that is contained in
//		   choices or default.  If choices is NULL, all possible
//		   inputs are accepted and returned.  This overrides
//		   everything else.  The choices are expected to be in
//		   lower case.  Entering ESC always maps to 'q', or 'n',
//		   in that order, if present in choices, otherwise it maps
//		   to default.  Entering any other quit character (SPACE,
//		   RETURN, NEWLINE) maps to default.
//		-- If the choices string contains ESC, then anything after
//		   it is an acceptable response, but the ESC and whatever
//		   follows is not included in the prompt.
//		-- If the choices string contains a '#' then accept a count.
//		   Place this value in the global "yn_number" and return '#'.
//		-- This uses the top line in the tty window-port, other
//		   ports might use a popup.
//		-- If choices is NULL, all possible inputs are accepted and
//		   returned, preserving case (upper or lower.) This means that
//		   if the calling function needs an exact match, it must handle
//		   user input correctness itself.
char and_yn_function(const char *question, const char *choices, char def)
{
    LOGD("and_yn_function(question:%s,choices:%s, def:%d)", question, choices, def);
    char ch = 0;
    int allow_num = choices != NULL && (strchr(choices, '#') != 0);
    int has_esc = choices != NULL && (strchr(choices, '\033') != 0);
    // if choice is not null or question contain [], treat it as question
    if(choices || ((strchr(question, '[') != 0) && (strchr(question, ']') != 0)))
    {
        jstring jQuestion = (*jEnv)->NewStringUTF(jEnv, question);
        jstring jChoices = (*jEnv)->NewStringUTF(jEnv, choices ? choices : "");
        jlongArray jYnNumber = (*jEnv)->NewLongArray(jEnv, 1);
        if(allow_num) {
            ch = JNICallC(jYNFunction, jQuestion, jChoices, jYnNumber, def)
            jlong *ynNumbers = (*jEnv)->GetLongArrayElements(jEnv, jYnNumber, NULL);
            yn_number = (long)ynNumbers[0];
            (*jEnv)->ReleaseLongArrayElements(jEnv, jYnNumber, ynNumbers, 0);
        } else if(has_esc) {
            char *rb;
            if ((rb = strchr(choices, '\033')) != 0)
                *rb = '\0';
            jChoices = (*jEnv)->NewStringUTF(jEnv, choices);
            ch = JNICallC(jYNFunction, jQuestion, jChoices, jYnNumber, def)
        } else {
            putstr(WIN_MESSAGE, 1<<ATR_BOLD, question);
            ch = JNICallC(jYNFunction, jQuestion, jChoices, jYnNumber, def)
        }
        (*jEnv)->DeleteLocalRef(jEnv, jYnNumber);
    } else {
        // otherwise treat it as message
        putstr(WIN_MESSAGE, 1<<ATR_BOLD, question);
        ch = nhgetch();
    }
    return ch;
}



//____________________________________________________________________________________
// getlin(const char *ques, char *input)
//		-- Prints ques as a prompt and reads a single line of text,
//		   up to a newline.  The string entered is returned without the
//		   newline.  ESC is used to cancel, in which case the string
//		   "\033\000" is returned.
//		-- getlin() must call flush_screen(1) before doing anything.
//		-- This uses the top line in the tty window-port, other
//		   ports might use a popup.
//		-- getlin() can assume the input buffer is at least BUFSZ
//		   bytes in size and must truncate inputs to fit, including
//		   the nul character.
void and_getlin(const char *question, char *input)
{
	LOGD("and_getlin %s, %s", question, input);
    jstring jquestion = (*jEnv)->NewStringUTF(jEnv, question);
    jstring jinput = (*jEnv)->NewStringUTF(jEnv, input);
    jstring result = (jstring)JNICallO(jGetLine, jquestion, jinput, BUFSZ)
    const jchar* jchars = (*jEnv)->GetStringChars(jEnv, result, 0);
    int i, len = (*jEnv)->GetStringLength(jEnv, result);
    for(i = 0; i < len; i++)
        input[i] = (char)jchars[i];
    input[i] = 0;
    (*jEnv)->ReleaseStringChars(jEnv, result, jchars);
}


//____________________________________________________________________________________
//askname()	-- Ask the user for a player name.
void and_askname()
{
    int saved_size = 0, aborted_size = 0, i;
    char ** saved = get_saved_games();
    char ** aborted = get_aborted_games();
    for (i = 0; saved && saved[i]; i++)
        saved_size++;
    for (i = 0; aborted && aborted[i]; i++)
        aborted_size++;
    jclass stringClass = (*jEnv)->FindClass(jEnv, "java/lang/String");
    jobjectArray jSavedList = (*jEnv)->NewObjectArray(jEnv, saved_size + aborted_size, stringClass, 0);
    for(i = 0; i < saved_size; i++)
        (*jEnv)->SetObjectArrayElement(jEnv, jSavedList, i, (*jEnv)->NewStringUTF(jEnv, saved[i]));
    for(i = 0; i < aborted_size; i++)
        (*jEnv)->SetObjectArrayElement(jEnv, jSavedList, i + saved_size, (*jEnv)->NewStringUTF(jEnv, aborted[i]));
    jobjectArray jPlayerInfo = (jobjectArray)JNICallO(jAskName, PL_NSIZ, jSavedList)
    int itemCnt = (*jEnv)->GetArrayLength(jEnv, jPlayerInfo);
    jstring jPlayer = (*jEnv)->GetObjectArrayElement(jEnv, jPlayerInfo, 0);
    jstring jPlaymode = (*jEnv)->GetObjectArrayElement(jEnv, jPlayerInfo, 1);
    const char *player = (*jEnv)->GetStringUTFChars(jEnv, jPlayer, 0);
    const char *playmode = (*jEnv)->GetStringUTFChars(jEnv, jPlaymode, 0);
    if(strncmp(playmode, "wizard", strlen(playmode)) == 0)
        wizard = TRUE;
    else if(strncmp(playmode, "discover", strlen(playmode)) == 0)
        discover = TRUE;
    gp.plnamelen = (int) strlen(strncpy(gp.plname, player, sizeof gp.plname - 1));
    (*jEnv)->ReleaseStringUTFChars(jEnv, jPlayer, player);
    (*jEnv)->ReleaseStringUTFChars(jEnv, jPlaymode, playmode);
    (*jEnv)->DeleteLocalRef(jEnv, jSavedList);
    (*jEnv)->DeleteLocalRef(jEnv, jPlayerInfo);
    LOGD("and_askname");
}

//____________________________________________________________________________________
//int get_ext_cmd(void)
//		-- Get an extended command in a window-port specific way.
//		   An index into extcmdlist[] is returned on a successful
//		   selection, -1 otherwise.

int and_get_ext_cmd()
{
    int size, idx = 0;
    unsigned int flgs;
    for(size = 0; extcmdlist[size].ef_txt != (char *)0; size++) ;
    jclass stringClass = (*jEnv)->FindClass(jEnv, "java/lang/String");
    jobjectArray jExtCmdList = (*jEnv)->NewObjectArray(jEnv, size * 2, stringClass, 0);
    for(int i = 0; i < size; i++) {
        flgs = extcmdlist[i].flags;
        jstring jCmdName, jCmdDesc;
        jCmdName = (*jEnv)->NewStringUTF(jEnv, extcmdlist[i].ef_txt);
        if(extcmdlist[i].ef_desc)
            jCmdDesc = (*jEnv)->NewStringUTF(jEnv, extcmdlist[i].ef_desc);
        else
            jCmdDesc = (*jEnv)->NewStringUTF(jEnv, "");

        if((flgs & WIZMODECMD) && !wizard) {
            jCmdName = (*jEnv)->NewStringUTF(jEnv, "");
            jCmdDesc = (*jEnv)->NewStringUTF(jEnv, "");
        }
        (*jEnv)->SetObjectArrayElement(jEnv, jExtCmdList, idx++, jCmdName);
        (*jEnv)->SetObjectArrayElement(jEnv, jExtCmdList, idx++, jCmdDesc);
        (*jEnv)->DeleteLocalRef(jEnv, jCmdName);
        (*jEnv)->DeleteLocalRef(jEnv, jCmdDesc);
    }
    idx = JNICallI(jShowExtCmdMenu, jExtCmdList)

    (*jEnv)->DeleteLocalRef(jEnv, jExtCmdList);
    return idx;
}

//____________________________________________________________________________________
//number_pad(state)
//		-- Initialize the number pad to the given state.
void and_number_pad(int state)
{
    LOGD("and_number_pad(%d)", state);
    JNICallV(jSetNumPadOption, state)
}

//____________________________________________________________________________________
//delay_output()	-- Causes a visible delay of 50ms in the output.
//		   Conceptually, this is similar to wait_synch() followed
//		   by a nap(50ms), but allows asynchronous operation.
void and_delay_output()
{
    LOGD("and_delay_output()");
    JNICallV(jDelayOutput)
}

#ifdef CHANGE_COLOR
void and_change_color(int color_number, long rgb, int reverse)
{
	LOGD("and_change_color %d == 0x%lX %s", color_number, rgb, reverse?" reverse":"");
}
char* and_get_color_string()
{
    LOGE("and_get_color_string");
	return "";
}
#endif

//____________________________________________________________________________________
//start_screen()	-- Only used on Unix tty ports, but must be declared for
//		   completeness.  Sets up the tty to work in full-screen
//		   graphics mode.  Look at win/tty/termcap.c for an
//		   example.  If your window-port does not need this function
//		   just declare an empty function.
void and_start_screen()
{
    LOGD("and_start_screen");
}

//____________________________________________________________________________________
//end_screen()	-- Only used on Unix tty ports, but must be declared for
//		   completeness.  The complement of start_screen().
void and_end_screen()
{
    LOGD("and_end_screen");
}

//____________________________________________________________________________________
// and_getmsghistory(init)
// 		window ports can provide their own getmsghistory() routine to
// 		preserve message history between games. The routine is called
// 		repeatedly from the core save routine, and the window port is
// 		expected to successively return each message that it wants
// 		saved, starting with the oldest message first, finishing with
// 		the most recent. Return null pointer when finished.

char* and_getmsghistory(boolean init)
{
    static int nxtidx;
    if (init) {
        nxtidx = 0;
    }
    LOGD("and_getmsghistory(nxtidx:%d)", nxtidx);
    jstring result = (jstring)JNICallO(jGetMessageHistory, nxtidx++)
    char *msg = (char *) (*jEnv)->GetStringUTFChars(jEnv, result, 0);
    if(strncmp(msg, "message_end", strlen(msg)) == 0)
        return NULL;
    return msg;
}

// and_putmsghistory(msg, restoring)
//		window ports can provide their own putmsghistory() routine
//		to load message history from a saved game. The routine is
//		called repeatedly from the core restore routine, starting
//		with the oldest saved message first, and finishing with
//		the latest. The window port routine is expected to load
//		the message recall buffers in such a way that the ordering
//		is preserved. The window port routine should make no
// 		assumptions about how many messages are forthcoming, nor
//		should it assume that another message will follow this
//		one, so it should keep all pointers/indexes intact at the
//		end of each call.
void and_putmsghistory(const char *msg, boolean restoring)
{
    if(!msg) return;
    if(restoring) {
        jstring jmsg = (*jEnv)->NewStringUTF(jEnv, msg);
        JNICallV(jPutMessageHistory, jmsg, restoring)
    }
    LOGD("and_putmsghistory(msg: %s, restoring:%d)", msg, restoring);
}

win_request_info *
and_ctrl_nhwindow(
        winid window,
        int request,
        win_request_info *wri)
{
    if (!wri)
        return (win_request_info *) 0;

    switch(request) {
        case set_mode:
        case request_settings:
            break;
        case set_menu_promptstyle:
            and_menu_promptstyle = wri->fromcore.menu_promptstyle;
            break;
        default:
            break;
    }
    return wri;
}
