#ifndef ANETHACK_ANDROID_STATUS_H
#define ANETHACK_ANDROID_STATUS_H

#include "hack.h"

void and_status_update(int fldidx, genericptr_t ptr, int chg,
                       int percent, int color, unsigned long *colormasks);

#endif
