/* Android menu window callbacks, separated from androidwin.c. */

#include "hack.h"
#include "androidentry.h"
#include "androidmenu.h"

/* 菜单选择字母：窗口端口负责给"可选中但没给 accelerator"的条目分配 a..z / A..Z，
   tty/curses 端口也是这么做的；不分配的话界面上就没有 "a - " 这样的前缀 */
static char next_menu_selector = 'a';

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
    int glyph_flags = (int) glyph->gm.glyphflags;
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
    JNICallV(jAddMenu, window, tile, glyph_flags, (long long )identifier->a_lptr, accelerator, groupacc, menu_attr, menu_color, jstr, preselected)
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



