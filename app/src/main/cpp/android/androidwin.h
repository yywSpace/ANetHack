#ifndef ANETHACK_ANDROIDWIN_H
#define ANETHACK_ANDROIDWIN_H

#include "hack.h"

void and_bail(const char *mesg);

void and_suspend_nhwindows(const char *str);
void and_resume_nhwindows(void);
winid and_create_nhwindow(int type);
void and_clear_nhwindow(winid wid);
void and_display_nhwindow(winid wid, boolean blocking);
void and_destroy_nhwindow(winid wid);
void and_init_nhwindows(int *argcp, char **argv);
void and_get_nh_event(void);
void and_exit_nhwindows(const char *str);
void and_curs(winid wid, int x, int y);
void and_putstr(winid wid, int attr, const char *str);
void and_putmixed(winid wid, int attr, const char *str);
void and_display_file(const char *name, boolean complain);
void and_update_inventory(int unused);
void and_mark_synch(void);
void and_wait_synch(void);
#ifdef CLIPPING
void and_cliparound(int x, int y);
#endif
#ifdef POSITIONBAR
void and_update_positionbar(char *features);
#endif
void and_print_glyph(winid wid, coordxy x, coordxy y,
                     const glyph_info *glyphinfo,
                     const glyph_info *bkglyphinfo);
void and_raw_print(const char *str);
void and_raw_print_bold(const char *str);
int and_nhgetch(void);
int and_nh_poskey(coordxy *x, coordxy *y, int *mod);
void and_nhbell(void);
int and_doprev_message(void);
char and_yn_function(const char *question, const char *choices, char def);
void and_getlin(const char *question, char *input);
int and_get_ext_cmd(void);
void and_number_pad(int state);
void and_delay_output(void);
#ifdef CHANGE_COLOR
void and_change_color(int color_number, long rgb, int reverse);
char *and_get_color_string(void);
#endif
win_request_info *and_ctrl_nhwindow(winid wid, int request,
                                    win_request_info *wri);
char *and_getmsghistory(boolean init);
void and_putmsghistory(const char *msg, boolean restoring);


#endif
