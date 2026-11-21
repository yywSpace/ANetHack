#include <string.h>
#include <errno.h>
#include <jni.h>
#include <ctype.h>

#include "hack.h"
#include "func_tab.h"   /* for extended commands */
#include "dlb.h"
#include "androidentry.h"
#include "androidstatus.h"
#include "androidplayer.h"
#include "androidwin.h"
#include "androidmenu.h"

#include <android/log.h>
#define TAG "NativeNetHackWin"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#ifndef NDEBUG
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#else
#define LOGD(...) ((void) 0)
#endif


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

void more(void){

}

/*
 * and_init_nhwindows() - initialize the Android window port.
 *
 * Called once before the standard windows are created. Marks the window
 * system as initialized so pline() goes through the window port.
 *
 * window.txt: init_nhwindows()
 */

void and_init_nhwindows(int* argcp, char** argv)
{
    LOGD("init_nhwindows");
    iflags.window_inited = TRUE;
}


/*
 * and_get_nh_event() - process pending window-system events.
 *
 * Android input is event driven, so there is nothing to poll here.
 *
 * window.txt: get_nh_event()
 */

void and_get_nh_event()
{
}

/*
 * and_exit_nhwindows() - shut down the window system.
 *
 * Dismisses the NetHack windows and marks the port as no longer initialized.
 * str is a final message from the core and is not displayed here.
 *
 * window.txt: exit_nhwindows(str)
 */

void and_exit_nhwindows(const char *str)
{
    LOGD("exit_nhwindows(%s)", str);
    iflags.window_inited = FALSE;
}

/*
 * and_bail() - release locks and terminate after a startup cancellation.
 *
 * Android counterpart of the tty bail() path; never returns.
 */

void and_bail(const char *mesg)
{
    clearlocks();
    exit_nhwindows(mesg);
    nh_terminate(EXIT_SUCCESS);
    /*NOTREACHED*/
}

/*
 * and_suspend_nhwindows() - prepare the port for suspension.
 *
 * The Android activity owns suspension, so the port has no state to save.
 *
 * window.txt: suspend_nhwindows(str)
 */

void and_suspend_nhwindows(const char *str)
{
}

/*
 * and_resume_nhwindows() - restore the port after suspension.
 *
 * The activity redraws its views on resume, so no port-level work is needed.
 *
 * window.txt: resume_nhwindows()
 */

void and_resume_nhwindows()
{
}

/*
 * and_create_nhwindow() - create a NetHack window of the requested type.
 *
 * The type is forwarded to the Kotlin window factory, which owns the actual
 * NHW_MESSAGE, NHW_MAP, NHW_MENU and NHW_TEXT implementations.
 *
 * window.txt: create_nhwindow(type)
 */

winid and_create_nhwindow(int type)
{
    LOGD("create_nhwindow(type:%d)", type);
    return JNICallI(jCreateWindow, type)
}

/*
 * and_clear_nhwindow() - clear a window before it is reused.
 *
 * The rogue-level flag lets the map view reset its tile cache correctly.
 *
 * window.txt: clear_nhwindow(window)
 */

void and_clear_nhwindow(winid wid)
{
    JNICallV(jClearWindow, wid, Is_rogue_level(&u.uz))
}

/*
 * and_display_nhwindow() - flush and show a window.
 *
 * Message, status and map windows are non-blocking. All other windows are
 * forced to blocking here and wait for a key after the Kotlin dialog closes.
 *
 * window.txt: display_nhwindow(window, blocking)
 */

void and_display_nhwindow(winid wid, boolean blocking)
{
    if(wid != WIN_MESSAGE && wid != WIN_STATUS && wid != WIN_MAP)
        blocking = TRUE;
    JNICallV(jDisplayWindow, wid, blocking)
    if(blocking)
        and_nhgetch();
}

/*
 * and_destroy_nhwindow() - destroy a window and release its Kotlin state.
 *
 * window.txt: destroy_nhwindow(window)
 */

void and_destroy_nhwindow(winid wid)
{
    JNICallV(jDestroyWindow, wid)
}

/*
 * and_curs() - position the next output cursor at (x, y).
 *
 * Coordinates use the NetHack map convention: 1 <= x < COLNO, 0 <= y < ROWNO.
 * Menu and text windows may ignore the position.
 *
 * window.txt: curs(window, x, y)
 */

void and_curs(winid wid, int x, int y)
{
    JNICallV(jCurs, wid, x, y)
}

/*
 * and_putstr() - append one attributed line to a text or menu window.
 *
 * Consecutive calls form consecutive lines. Empty strings are forwarded so
 * the Kotlin side can preserve paragraph breaks; null is ignored.
 *
 * window.txt: putstr(window, attr, str)
 */

void and_putstr(winid wid, int attr, const char *str)
{

    if (!str) return;
    if(attr) attr = 1<<attr;
    jstring jstr = char2Jstring(jEnv, str);
    JNICallV(jPutString, wid, attr, jstr, NO_COLOR)
    if (jstr) (*jEnv)->DeleteLocalRef(jEnv, jstr);
}

/*
 * and_putmixed() - append a line that may contain encoded glyphs.
 *
 * Delegates to genl_putmixed(), which decodes \GXXXXNNNN sequences into
 * characters before the line reaches the Kotlin window.
 *
 * window.txt: putmixed(window, attr, str)
 */

void and_putmixed(winid wid, int attr, const char *str)
{
    genl_putmixed(wid, attr, str);
}

/*
 * and_display_file() - read a DLB file into a blocking NHW_TEXT window.
 *
 * Tabs are expanded to spaces, then the file is shown with putstr() and
 * display_nhwindow(TRUE). Missing files are ignored unless complain is TRUE.
 *
 * window.txt: display_file(str, complain)
 */

void and_display_file(const char *name, boolean complain)
{
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

/*
 * and_update_inventory() - notify the port that inventory changed.
 *
 * The permanent inventory window is maintained by the Kotlin side, so there
 * is nothing to repaint here.
 *
 * window.txt: update_inventory()
 */

void and_update_inventory(int unused)
{
    (void) unused;
}

/*
 * and_mark_synch() - mark a synchronization point for output.
 *
 * The Android port has no separate output streams, so this is a no-op.
 *
 * window.txt: mark_synch()
 */

void and_mark_synch()
{
}

/*
 * and_wait_synch() - wait for pending output to become visible.
 *
 * Rendering is posted to the Kotlin map thread; no extra flush is required.
 *
 * window.txt: wait_synch()
 */

void and_wait_synch()
{
}

#ifdef CLIPPING
/*
 * and_cliparound() - keep the hero near the centre of a clipped map.
 *
 * Only built when CLIPPING is defined.
 *
 * window.txt: cliparound(x, y)
 */

void and_cliparound(int x, int y)
{
	JNICallV(jClipAround, x, y, u.ux, u.uy)
}

#endif

#ifdef POSITIONBAR
/*
 * and_update_positionbar() - update the optional position bar.
 *
 * Only built when POSITIONBAR is defined; Android has no position bar.
 *
 * window.txt: update_positionbar(features)
 */

void and_update_positionbar(char *features)
{
}

#endif

/*
 * and_print_glyph() - draw one map cell.
 *
 * Converts the glyph into a tile or tty character plus color and special
 * flags, dropping flags the current display options do not support.
 *
 * window.txt: print_glyph(window, x, y, glyphinfo, bkglyphinfo)
 */

void and_print_glyph(winid wid, coordxy x, coordxy y, const glyph_info * glyphinfo, const glyph_info * bkglyphinfo)
{
    int ch = glyphinfo->ttychar;
    int color = glyphinfo->gm.sym.color;
    int tile = glyphinfo->gm.tileidx;

    unsigned int special = glyphinfo->gm.glyphflags;

    special &= ~(MG_CORPSE|MG_RIDDEN|MG_STATUE|MG_MALE|MG_FEMALE);
    if(!iflags.hilite_pet)
        special &= ~MG_PET;
    if(!iflags.hilite_pile)
        special &= ~MG_OBJPILE;
    if(!iflags.use_inverse)
        special &= ~MG_DETECT;
    JNICallV(jPrintTile, wid, x, y, tile, ch, color, special)
}

/*
 * and_raw_print() - print a message directly to the user.
 *
 * Used for startup and fatal messages, before or outside the window system.
 *
 * window.txt: raw_print(str)
 */

void and_raw_print(const char* str)
{
    jstring jstr = char2Jstring(jEnv,str);
    JNICallV(jRawPrint, 1<<ATR_BOLD, jstr)
    if (jstr) (*jEnv)->DeleteLocalRef(jEnv, jstr);
}

/*
 * and_raw_print_bold() - print a highlighted raw message.
 *
 * Android currently uses the same bold presentation as raw_print().
 *
 * window.txt: raw_print_bold(str)
 */

void and_raw_print_bold(const char* str)
{
    jstring jstr = char2Jstring(jEnv,str);
    JNICallV(jRawPrint, 1<<ATR_BOLD, jstr)
    if (jstr) (*jEnv)->DeleteLocalRef(jEnv, jstr);
}

/*
 * and_nhgetch() - wait for and return one command key.
 *
 * The returned character is non-zero and must not be meta-zero.
 *
 * window.txt: nhgetch()
 */

int and_nhgetch()
{
#ifdef IDLECHECKPOINT
    if (iflags.idlecheckpoint) {
        int c = JNICallI(jRequireKeyCommand,
                         IDLECHECKPOINT_WAIT_TIME * 1000)
        if (c != -1)
            return c;
        LOGD("idle checkpoint: no input for %d ms, saving",
             IDLECHECKPOINT_WAIT_TIME * 1000);
        save_currentstate();
        LOGD("idle checkpoint: save done");
    }
#endif
    return JNICallI(jRequireKeyCommand, -1)
}

/*
 * and_nh_poskey() - read a command key or a map position event.
 *
 * A non-zero return is a typed character. A zero return fills x, y and mod
 * from the Kotlin position event (CLICK_1/CLICK_2).
 *
 * window.txt: nh_poskey(x, y, mod)
 */

int and_nh_poskey(coordxy *x, coordxy *y, int *mod)
{
    jintArray jEvent = (*jEnv)->NewIntArray(jEnv, 3);
    int ch;
#ifdef IDLECHECKPOINT
    if (iflags.idlecheckpoint) {
        ch = JNICallI(jRequirePosKeyCommand, jEvent,
                      IDLECHECKPOINT_WAIT_TIME * 1000)
        if (ch == -1) {
            LOGD("idle checkpoint(poskey): no input for %d ms, saving",
                 IDLECHECKPOINT_WAIT_TIME * 1000);
            save_currentstate();
            LOGD("idle checkpoint(poskey): save done");
            ch = JNICallI(jRequirePosKeyCommand, jEvent, -1)
        }
    } else
#endif
        ch = JNICallI(jRequirePosKeyCommand, jEvent, -1)
    if(!ch) {
        int *event = (*jEnv)->GetIntArrayElements(jEnv, jEvent, NULL);
        *x = (coordxy)event[0];
        *y = (coordxy)event[1];
        *mod = event[2];
        (*jEnv)->ReleaseIntArrayElements(jEnv, jEvent, event, 0);
    }
    (*jEnv)->DeleteLocalRef(jEnv, jEvent);
    return ch;
}

/*
 * and_nhbell() - ring the terminal bell.
 *
 * Sound output is handled by the Kotlin sound system, so this is a no-op.
 *
 * window.txt: nhbell()
 */

void and_nhbell()
{
}

/*
 * and_doprev_message() - display previous messages for the ^P command.
 *
 * Android shows the message history in a dedicated window.
 *
 * window.txt: doprev_message()
 */

int and_doprev_message()
{
    display_nhwindow(WIN_MESSAGE, TRUE);
    return ECMD_OK;
}

/*
 * and_yn_function() - ask a yes/no or single-choice question.
 *
 * choices may contain ESC to hide the remaining prompt text, and '#' to ask
 * for a numeric count (returned in yn_number). A null choices string accepts
 * any key, and bracket prompts are treated as messages.
 *
 * window.txt: win_yn_function(ques, choices, default)
 */

char and_yn_function(const char *question, const char *choices, char def)
{
    char ch = 0;
    int allow_num = choices != NULL && (strchr(choices, '#') != 0);
    int has_esc = choices != NULL && (strchr(choices, '\033') != 0);
    // if choice is not null or question contain [], treat it as question
    if(choices || ((strchr(question, '[') != 0) && (strchr(question, ']') != 0)))
    {
        jstring jQuestion = char2Jstring(jEnv, question);
        jstring jChoices = char2Jstring(jEnv, choices ? choices : "");
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
            jChoices = char2Jstring(jEnv, choices);
            ch = JNICallC(jYNFunction, jQuestion, jChoices, jYnNumber, def)
        } else {
            putstr(WIN_MESSAGE, 1<<ATR_BOLD, question);
            ch = JNICallC(jYNFunction, jQuestion, jChoices, jYnNumber, def)
        }
        (*jEnv)->DeleteLocalRef(jEnv, jYnNumber);
        if (jQuestion) (*jEnv)->DeleteLocalRef(jEnv, jQuestion);
        if (jChoices) (*jEnv)->DeleteLocalRef(jEnv, jChoices);
    } else {
        // otherwise treat it as message
        putstr(WIN_MESSAGE, 1<<ATR_BOLD, question);
        ch = nhgetch();
    }
    return ch;
}

/*
 * and_getlin() - prompt for one line of text.
 *
 * The Kotlin input dialog returns at most BUFSZ bytes; ESC returns an empty
 * string. The buffer is assumed to be at least BUFSZ bytes.
 *
 * window.txt: getlin(ques, input)
 */

void and_getlin(const char *question, char *input)
{
    JNIEnv *e = jEnv;
    jstring jquestion = char2Jstring(e, question);
    jstring jinput = char2Jstring(e, input);
    jstring result = (jstring)JNICallO(jGetLine, jquestion, jinput, BUFSZ)
    if (result) {
        char *chars = jstring2Char(e, result);
        if (chars) {
            strcpy(input, chars);
            free(chars);
        }
    }
    if (jquestion) (*e)->DeleteLocalRef(e, jquestion);
    if (jinput) (*e)->DeleteLocalRef(e, jinput);
    if (result) (*e)->DeleteLocalRef(e, result);
}


/*
 * and_get_ext_cmd() - choose an extended command from the command list.
 *
 * Sends name/description pairs to Kotlin and returns the selected index,
 * or -1 when the menu is cancelled.
 *
 * window.txt: get_ext_cmd()
 */

int and_get_ext_cmd()
{
    int size, idx = 0;
    unsigned int flgs;
    for(size = 0; extcmdlist[size].ef_txt != (char *)0; size++) ;
    jclass stringClass = (*jEnv)->FindClass(jEnv, "java/lang/String");
    jobjectArray jExtCmdList = (*jEnv)->NewObjectArray(jEnv, size * 2, stringClass, 0);
    (*jEnv)->DeleteLocalRef(jEnv, stringClass);
    for(int i = 0; i < size; i++) {
        flgs = extcmdlist[i].flags;
        jstring jCmdName, jCmdDesc;
        jCmdName = char2Jstring(jEnv, extcmdlist[i].ef_txt);
        if(extcmdlist[i].ef_desc)
            jCmdDesc = char2Jstring(jEnv, extcmdlist[i].ef_desc);
        else
            jCmdDesc = char2Jstring(jEnv, "");

        if((flgs & WIZMODECMD) && !wizard) {
            jCmdName = char2Jstring(jEnv, "");
            jCmdDesc = char2Jstring(jEnv, "");
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

/*
 * and_number_pad() - initialize the number-pad mode.
 *
 * Kotlin currently has no number-pad option hook, so this is a no-op.
 *
 * window.txt: number_pad(state)
 */

void and_number_pad(int state)
{
    /* Kotlin has no setNumPadOption() counterpart; the previous code called
       an uninitialized (NULL) jSetNumPadOption method id, which crashed.
       Kept as a no-op until a Kotlin-side handler is added. */
}

/*
 * and_delay_output() - pause briefly so animations remain visible.
 *
 * Kotlin sleeps on the game thread; see window.txt for the intended
 * "wait_synch followed by a short nap" behavior.
 *
 * window.txt: delay_output()
 */

void and_delay_output()
{
    JNICallV(jDelayOutput)
}

#ifdef CHANGE_COLOR
/*
 * and_change_color() - change a terminal color.
 *
 * Built only with CHANGE_COLOR; Android colors are managed by Kotlin.
 *
 * window.txt: change_color(color, rgb, reverse)
 */

void and_change_color(int color_number, long rgb, int reverse)
{
}
/*
 * and_get_color_string() - return a terminal color as a string.
 *
 * Built only with CHANGE_COLOR; Android has no terminal color map.
 *
 * window.txt: get_color_string()
 */

char* and_get_color_string()
{
    LOGE("and_get_color_string");
	return "";
}
#endif

/*
 * and_getmsghistory() - return saved messages oldest first.
 *
 * Called repeatedly by the save code; init restarts the cursor and null means
 * there are no more messages.
 *
 * window.txt: getmsghistory(init)
 */

char* and_getmsghistory(boolean init)
{
    static int nxtidx;
    if (init) {
        nxtidx = 0;
    }
    JNIEnv *e = jEnv;
    jstring result = (jstring)JNICallO(jGetMessageHistory, nxtidx++)
    if (!result)
        return NULL;
    char *msg = jstring2Char(e, result);
    (*e)->DeleteLocalRef(e, result);
    if (!msg)
        return NULL;
    /* the core does not free the returned buffer; hand back a static copy */
    static char msg_buf[BUFSZ];
    strncpy(msg_buf, msg, sizeof(msg_buf) - 1);
    msg_buf[sizeof(msg_buf) - 1] = '\0';
    free(msg);
    return msg_buf;
}

/*
 * and_putmsghistory() - restore one saved message.
 *
 * Called oldest first during restore; Kotlin rebuilds the recall buffer in
 * the same order.
 *
 * window.txt: putmsghistory(msg, restoring)
 */

void and_putmsghistory(const char *msg, boolean restoring)
{
    if(!msg) return;
    if(restoring) {
        jstring jmsg = char2Jstring(jEnv, msg);
        JNICallV(jPutMessageHistory, jmsg, restoring)
        if (jmsg) (*jEnv)->DeleteLocalRef(jEnv, jmsg);
    }
}

/*
 * and_ctrl_nhwindow() - handle window-control requests.
 *
 * The Kotlin menu already owns these settings, so the request is accepted
 * and the request descriptor is returned unchanged.
 *
 * window.txt: ctrl_nhwindow(...)
 */

win_request_info *
and_ctrl_nhwindow(
        winid window,
        int request,
        win_request_info *wri)
{
    (void) window;
    (void) request;
    return wri;
}
