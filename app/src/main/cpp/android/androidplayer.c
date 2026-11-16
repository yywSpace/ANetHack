/* Android player selection menus, separated from androidwin.c. */

#include "hack.h"
#include "androidplayer.h"
#include "androidwin.h"
#include "androidentry.h"

static int choose_player_field(int field)
{
    winid win;
    anything any;
    menu_item *selected = 0;
    char thisch, lastch = 0;
    int i, result, choice = -1;

    win = create_nhwindow(NHW_MENU);
    start_menu(win, MENU_BEHAVE_STANDARD);
    any.a_void = 0;

    switch (field) {
        case RS_ROLE:
            any = cg.zeroany;
            any.a_int = randrole(TRUE) + 1;
            add_menu(win, &nul_glyphinfo, &any, '*', 0, ATR_NONE, 0,
                     "Random", MENU_ITEMFLAGS_SELECTED);
            for (i = 0; roles[i].name.m; i++) {
                if (!ok_role(i, flags.initrace, flags.initgend, flags.initalign))
                    continue;
                any.a_int = i + 1;
                thisch = lowc(roles[i].name.m[0]);
                if (thisch == lastch)
                    thisch = highc(thisch);
                add_menu(win, &nul_glyphinfo, &any, thisch, 0, ATR_NONE, 0,
                         roles[i].name.m, MENU_ITEMFLAGS_NONE);
                lastch = thisch;
            }
            end_menu(win, "Pick a role or profession");
            break;

        case RS_RACE:
            any.a_int = randrace(flags.initrole) + 1;
            add_menu(win, &nul_glyphinfo, &any, '*', 0, ATR_NONE, 0,
                     "Random", MENU_ITEMFLAGS_SELECTED);
            for (i = 0; races[i].noun; i++) {
                if (!ok_race(flags.initrole, i, flags.initgend, flags.initalign))
                    continue;
                any.a_int = i + 1;
                add_menu(win, &nul_glyphinfo, &any, races[i].noun[0], 0, ATR_NONE,
                         0, races[i].noun, MENU_ITEMFLAGS_NONE);
            }
            end_menu(win, "Pick a race or species");
            break;

        case RS_GENDER:
            any.a_int = randgend(flags.initrole, flags.initrace) + 1;
            add_menu(win, &nul_glyphinfo, &any, '*', 0, ATR_NONE, 0,
                     "Random", MENU_ITEMFLAGS_SELECTED);
            for (i = 0; i < ROLE_GENDERS; i++) {
                if (!ok_gend(flags.initrole, flags.initrace, i, flags.initalign))
                    continue;
                any.a_int = i + 1;
                add_menu(win, &nul_glyphinfo, &any, genders[i].adj[0], 0, ATR_NONE,
                         0, genders[i].adj, MENU_ITEMFLAGS_NONE);
            }
            end_menu(win, "Pick a gender or sex");
            break;

        case RS_ALGNMNT:
            any.a_int = randalign(flags.initrole, flags.initrace) + 1;
            add_menu(win, &nul_glyphinfo, &any, '*', 0, ATR_NONE, 0,
                     "Random", MENU_ITEMFLAGS_SELECTED);
            for (i = 0; i < ROLE_ALIGNS; i++) {
                if (!ok_align(flags.initrole, flags.initrace, flags.initgend, i))
                    continue;
                any.a_int = i + 1;
                add_menu(win, &nul_glyphinfo, &any, aligns[i].adj[0], 0, ATR_NONE,
                         0, aligns[i].adj, MENU_ITEMFLAGS_NONE);
            }
            end_menu(win, "Pick an alignment or creed");
            break;

        default:
            destroy_nhwindow(win);
            return -1;
    }

    result = select_menu(win, PICK_ONE, &selected);

    destroy_nhwindow(win);
    if (result > 0 && selected != 0 && selected[0].item.a_int > 0)
        choice = selected[0].item.a_int - 1;
    if (selected)
        free((genericptr_t) selected);
    return choice;
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
    int picked[RS_ALGNMNT + 1] = { 0 };
    int current = RS_ROLE;
    int choice, previous, i;

    while (current <= RS_ALGNMNT) {
        /* Honor nethackrc/default values and rigid auto-selection first. */
        if (current == RS_RACE && flags.initrace == ROLE_NONE)
            flags.initrace = pick_race(flags.initrole, flags.initgend, flags.initalign, PICK_RIGID);
        if (current == RS_GENDER && flags.initgend == ROLE_NONE)
            flags.initgend = pick_gend(flags.initrole, flags.initrace, flags.initalign, PICK_RIGID);
        if (current == RS_ALGNMNT && flags.initalign == ROLE_NONE)
            flags.initalign = pick_align(flags.initrole, flags.initrace, flags.initgend, PICK_RIGID);
        if ((current == RS_ROLE && flags.initrole != ROLE_NONE)
            || (current == RS_RACE && flags.initrace != ROLE_NONE)
            || (current == RS_GENDER && flags.initgend != ROLE_NONE)
            || (current == RS_ALGNMNT && flags.initalign != ROLE_NONE)) {
            current++;
            continue;
        }

        choice = choose_player_field(current);
        if (choice < 0) {
            if (current == RS_ROLE) {
                and_bail("bye");
                return;
            }

            previous = ROLE_NONE;
            for (i = current - 1; i >= RS_ROLE; i--) {
                if (picked[i]) {
                    previous = i;
                    break;
                }
            }
            if (previous == ROLE_NONE)
                previous = RS_ROLE;
            for (i = previous; i <= RS_ALGNMNT; i++) {
                switch (i) {
                    case RS_ROLE: flags.initrole = ROLE_NONE; break;
                    case RS_RACE: flags.initrace = ROLE_NONE; break;
                    case RS_GENDER: flags.initgend = ROLE_NONE; break;
                    case RS_ALGNMNT: flags.initalign = ROLE_NONE; break;
                    default: break;
                }
                picked[i] = 0;
            }
            current = previous;
            continue;
        }

        switch (current) {
            case RS_ROLE: flags.initrole = choice; break;
            case RS_RACE: flags.initrace = choice; break;
            case RS_GENDER: flags.initgend = choice; break;
            case RS_ALGNMNT: flags.initalign = choice; break;
            default: break;
        }
        picked[current] = 1;
        current++;
    }
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
    free(player);
    free(playmode);
    (*jEnv)->DeleteLocalRef(jEnv, jPlayer);
    (*jEnv)->DeleteLocalRef(jEnv, jPlaymode);
    (*jEnv)->DeleteLocalRef(jEnv, jSavedList);
    (*jEnv)->DeleteLocalRef(jEnv, jPlayerInfo);
}
