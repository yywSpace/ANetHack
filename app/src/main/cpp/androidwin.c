#include <string.h>
#include <errno.h>
#include <jni.h>
#include <ctype.h>

#include "hack.h"
#include "func_tab.h"   /* for extended commands */
#include "dlb.h"
#include "androidentry.h"

#include <android/log.h>
#define TAG "NativeNetHackWin"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#ifndef NDEBUG
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#else
#define LOGD(...) ((void) 0)
#endif


static void and_init_nhwindows(int *, char **);
static void and_player_selection();
static void and_askname();
static void and_get_nh_event() ;
static void and_exit_nhwindows (const char *);
static void and_bail(const char *);
static void and_suspend_nhwindows(const char *);
static void and_resume_nhwindows();
static winid and_create_nhwindow(int);
static void and_clear_nhwindow(winid);
static void and_display_nhwindow(winid, boolean);
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
win_request_info *and_ctrl_nhwindow(winid, int, win_request_info *);
/* 菜单选择字母：窗口端口负责给"可选中但没给 accelerator"的条目分配 a..z / A..Z，
   tty/curses 端口也是这么做的；不分配的话界面上就没有 "a - " 这样的前缀 */
static char next_menu_selector = 'a';

static char* and_getmsghistory(boolean);
static void and_putmsghistory(const char *, boolean);
static void and_status_update(int, genericptr_t, int, int, int, unsigned long *);

static int cond_color(long, const unsigned long *);
static int cond_attr(long, const unsigned long *);

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

char **get_aborted_games(void);

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
 * and_player_selection() - let the player choose role, race, gender and
 * alignment through native menus.
 *
 * Cancelling the first role menu exits the game; cancelling a later menu
 * returns to the previous choice. pl_character[0] is filled in by the core.
 *
 * window.txt: player_selection()
 */

void and_player_selection()
{
    int i, result, picked_state[RS_ALGNMNT+1] = {0};
    char thisch, lastch = 0;
    winid win;
    anything any;
    menu_item *selected = 0;

    flags.initrole = flags.initrace = flags.initgend = flags.initalign
            = ROLE_NONE;
    previous:
    // 返回时，取消直至最后一个手动选择属性
    for (i = RS_ALGNMNT; i >= RS_ROLE; --i) {
        if(i == RS_ROLE) flags.initrole = ROLE_NONE;
        if(i == RS_RACE) flags.initrace = ROLE_NONE;
        if(i == RS_GENDER) flags.initgend = ROLE_NONE;
        if(picked_state[i] == 1) {
            picked_state[i] = 0;
            break;
        }
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
            and_bail("bye");
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

static void and_bail(const char *mesg)
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

void and_status_field_render(int idx, const char *fmt_val, const char *real_val, int attr, int color, int percent) {
    jstring fmtVal = char2Jstring(jEnv, fmt_val);
    jstring realVal = char2Jstring(jEnv, real_val);
    JNICallV(jRenderStatus, idx, fmtVal, realVal, attr, color, percent)
    if (fmtVal) (*jEnv)->DeleteLocalRef(jEnv, fmtVal);
    if (realVal) (*jEnv)->DeleteLocalRef(jEnv, realVal);
}


/* HL_ATTCLR_* 属性索引 -> Android ATR_* 属性位 */
static int hl_attr_index_to_atr(int idx)
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

/* NetHack HL_* 高亮掩码 -> Android ATR_* 属性位 */
static int hl_attr_mask_to_atr(int mask)
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
                        idx, "", "", attrmask, coloridx, percent);
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
                    and_status_field_render(
                            idx, condtext, condtext, attrmask, coloridx, percent);
                    bits &= ~mask;
                }
            }
        } else {
            // for other
            attrmask = status_attrmasks[idx];
            coloridx = status_colors[idx];

            char *fmt_val = status_vals[idx];
            char *real_val = status_real_values[idx];
            and_status_field_render(
                    idx, fmt_val, real_val,
                    hl_attr_mask_to_atr(attrmask), coloridx, percent);
        }
    }
    display_nhwindow(WIN_STATUS, FALSE);
}

/*
 * and_status_update() - receive one status field update.
 *
 * fldindex selects the field, BL_FLUSH/BL_RESET trigger a redraw, and
 * BL_CONDITION carries condition bits and highlight masks. Other fields are
 * formatted into status_vals for the Kotlin status view.
 *
 * window.txt: status_update(...)
 */

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
        case BL_FLUSH:
            render_status();
            return;
        case BL_CONDITION:
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
                attr |= hl_attr_index_to_atr(i);
        }
    }
    return attr;
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
 * and_start_menu() - switch a menu window into menu-building mode.
 *
 * Resets the automatic selector to 'a'; the behavior flags are forwarded to
 * the Kotlin menu implementation.
 *
 * window.txt: start_menu(window, mbehavior)
 */

void and_start_menu(winid wid,unsigned long mbehavior)
{
    /* 每个菜单从 a 开始分配选择字母 */
    next_menu_selector = 'a';
    JNICallV(jStartMenu, wid, mbehavior)
}

/*
 * and_add_menu() - append one entry to a menu window.
 *
 * Selectable entries without an accelerator get the next automatic letter
 * (a-z, A-Z). identifier, glyph, attributes, color and preselection are
 * forwarded to the Kotlin menu.
 *
 * window.txt: add_menu(window, ...)
 */

void and_add_menu(winid window, const glyph_info * glyph, const union any * identifier, char accelerator,
        char groupacc, int attr, int clr, const char *str, unsigned int itemflags)
{
    boolean preselected = (boolean) ((itemflags & MENU_ITEMFLAGS_SELECTED) != 0);
    int tile = glyph->gm.tileidx;
    int menu_color = clr, menu_attr = attr;
    /* 可选中但调用方没给 accelerator 的条目，由窗口端口分配一个选择字母，
       界面上显示成 "a - " 这样的前缀（tty/curses 端口也是这么做的） */
    if (identifier->a_lptr && !accelerator) {
        accelerator = next_menu_selector;
        if (next_menu_selector == 'z')
            next_menu_selector = 'A';
        else if (next_menu_selector == 'Z')
            next_menu_selector = 'a';
        else
            next_menu_selector++;
    }
    if(menu_attr)
        menu_attr = 1<<menu_attr;
    jstring jstr = char2Jstring(jEnv, str);
    JNICallV(jAddMenu, window, tile, (long long )identifier->a_lptr, accelerator, groupacc, menu_attr, menu_color, jstr, preselected)
    if (jstr) (*jEnv)->DeleteLocalRef(jEnv, jstr);
}

/*
 * and_end_menu() - finish a menu and set its prompt.
 *
 * A null prompt means no prompt text.
 *
 * window.txt: end_menu(window, prompt)
 */

void and_end_menu(winid wid, const char *prompt)
{
    jstring jstr = char2Jstring(jEnv, prompt);
    JNICallV(jEndMenu, wid, jstr)
    if (jstr) (*jEnv)->DeleteLocalRef(jEnv, jstr);
}

/*
 * and_select_menu() - display a menu and collect the player's selection.
 *
 * Returns the number of selected entries, 0 for none, or -1 for cancel.
 * The Kotlin side returns (identifier, count) pairs; this function converts
 * them into the MENU_ITEM_P array owned by the caller.
 *
 * window.txt: select_menu(window, how, selected)
 */

int and_select_menu(winid wid, int how, MENU_ITEM_P **selected)
{
    jlong *p, *q;
    jlongArray selectedItems = (jlongArray)JNICallO(jSelectMenu, wid, how)
    *selected = 0;
    int itemCnt = (*jEnv)->GetArrayLength(jEnv, selectedItems);
    if(itemCnt > 1)  { // n should always be 2k (id, count) pairs
        itemCnt >>= 1;
        q = p = (*jEnv)->GetLongArrayElements(jEnv, selectedItems, NULL);
        *selected = (MENU_ITEM_P*)malloc(sizeof(MENU_ITEM_P) * itemCnt);
        for(int i = 0; i < itemCnt; i++)
        {
            // convert identifier address to anything
            (*selected)[i].item.a_lptr = (long *)(*p++);
            (*selected)[i].count = (int)*p++;
        }
        (*jEnv)->ReleaseLongArrayElements(jEnv, selectedItems, q, 0);
    } else if(itemCnt == 1) {
        //  0 if none were chosen, -1 when explicitly cancelled
        jlong *result =  (*jEnv)->GetLongArrayElements(jEnv, selectedItems, NULL);
        int ret = (int) *result;
        (*jEnv)->ReleaseLongArrayElements(jEnv, selectedItems, result, 0);
        (*jEnv)->DeleteLocalRef(jEnv, selectedItems);
        return ret;
    }
    (*jEnv)->DeleteLocalRef(jEnv, selectedItems);
    return itemCnt;
}


/*
 * and_update_inventory() - notify the port that inventory changed.
 *
 * The permanent inventory window is maintained by the Kotlin side, so there
 * is nothing to repaint here.
 *
 * window.txt: update_inventory()
 */

void and_update_inventory()
{
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

    special &= ~(MG_CORPSE|MG_RIDDEN|MG_STATUE|MG_UNEXPL|MG_MALE|MG_FEMALE); // TODO support
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
    int c = JNICallI(jRequireKeyCommand)
    return c;
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
    char ch = JNICallC(jRequirePosKeyCommand, jEvent)
    if(!ch) {
        int *event = (*jEnv)->GetIntArrayElements(jEnv, jEvent, NULL);
        *x = (coordxy)event[0];
        *y = (coordxy)event[1];
        *mod = event[2];
        (*jEnv)->ReleaseIntArrayElements(jEnv, jEvent, event, 0);
    }
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


/* A saved game name carries its playmode in the final byte (see
   store_plname_in_file()), which char2Jstring() stops short of; pass it along
   so that the chooser doesn't need to remember the mode. */
static jstring
savedName2Jstring(const char *name)
{
    char buf[BUFSZ];
    char playmode = name[PL_NSIZ_PLUS - 1];

    Snprintf(buf, sizeof buf, "%s\t%c", name,
             (playmode == 'D' || playmode == 'X') ? playmode : '-');
    return char2Jstring(jEnv, buf);
}

/*
 * and_askname() - choose a player name and play mode.
 *
 * Presents saved and aborted games plus a new-game option. The returned
 * playmode selects normal, wizard or discover mode and fills plname.
 *
 * window.txt: askname()
 */

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
    for (i = 0; i < saved_size; i++)
        (*jEnv)->SetObjectArrayElement(jEnv, jSavedList, i, savedName2Jstring(saved[i]));
    for (i = 0; i < aborted_size; i++)
        (*jEnv)->SetObjectArrayElement(jEnv, jSavedList, saved_size + i, savedName2Jstring(aborted[i]));
    (*jEnv)->DeleteLocalRef(jEnv, stringClass);
    free_saved_games(saved);
    free_saved_games(aborted);
    jobjectArray jPlayerInfo = (jobjectArray)JNICallO(jAskName, PL_NSIZ, jSavedList)
    int infoLen = jPlayerInfo ? (*jEnv)->GetArrayLength(jEnv, jPlayerInfo) : 0;
    if (infoLen < 2) {
        /* 玩家在选人界面取消：和 and_player_selection 一样，在游戏线程退出 */
        and_bail("bye");
    }
    jstring jPlayer = (*jEnv)->GetObjectArrayElement(jEnv, jPlayerInfo, 0);
    jstring jPlaymode = (*jEnv)->GetObjectArrayElement(jEnv, jPlayerInfo, 1);
    char *player = jstring2Char(jEnv, jPlayer);
    char *playmode = jstring2Char(jEnv, jPlaymode);
    if(strncmp(playmode, "wizard", strlen(playmode)) == 0)
        wizard = TRUE;
    else if(strncmp(playmode, "discover", strlen(playmode)) == 0)
        discover = TRUE;
    gp.plnamelen = (int) strlen(strncpy(svp.plname, player, sizeof svp.plname - 1));
    free(player);   /* jstring2Char() malloc'd these; do NOT use
                       ReleaseStringUTFChars() on them */
    free(playmode);
    (*jEnv)->DeleteLocalRef(jEnv, jPlayer);
    (*jEnv)->DeleteLocalRef(jEnv, jPlaymode);
    (*jEnv)->DeleteLocalRef(jEnv, jSavedList);
    (*jEnv)->DeleteLocalRef(jEnv, jPlayerInfo);
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
