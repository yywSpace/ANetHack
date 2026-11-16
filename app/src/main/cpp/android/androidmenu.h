#ifndef ANETHACK_ANDROIDMENU_H
#define ANETHACK_ANDROIDMENU_H

#include "hack.h"

void and_start_menu(winid wid, unsigned long mbehavior);
void and_add_menu(winid window, const glyph_info *glyph,
                  const union any *identifier, char accelerator,
                  char groupacc, int attr, int clr, const char *str,
                  unsigned int itemflags);
void and_end_menu(winid wid, const char *prompt);
int and_select_menu(winid wid, int how, MENU_ITEM_P **selected);

#endif
