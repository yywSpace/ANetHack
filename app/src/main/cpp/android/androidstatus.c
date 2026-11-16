/* Android status-bar field dispatch, separated from androidwin.c. */

#include "hack.h"
#include "androidstatus.h"
#include "androidentry.h"

#include <jni.h>

extern const char *status_fieldfmt[MAXBLSTATS];
extern boolean status_activefields[MAXBLSTATS];

static unsigned long *condition_masks;
static long condition_bits = 0L;

static char status_fmt[MAXBLSTATS][BUFSZ];
static char status_real[MAXBLSTATS][BUFSZ];
static int status_attr[MAXBLSTATS];
static int status_color[MAXBLSTATS];
static int status_percent[MAXBLSTATS];
static boolean status_seen[MAXBLSTATS];


static int cond_color(long, const unsigned long *);
static int cond_attr(long, const unsigned long *);
static int hl_attr_index_to_atr(int);
static int hl_attr_mask_to_atr(int);

static void flush_status_batch(void)
{
    jint data[MAXBLSTATS * 4 + SIZE(conditions) * 4];
    jintArray jdata;
    jobjectArray jtexts;
    jclass jstring_class;
    jstring jfmt_val, jreal_val;
    int i, c, ci, count = 0, n = 0;
    long bits = condition_bits;
    int attrmask, coloridx;
    const char *condtext;

    for (i = 0; i < MAXBLSTATS; ++i)
        if (status_seen[i] && status_activefields[i])
            ++count;
    if (status_activefields[BL_CONDITION]) {
        for (c = 0; c < SIZE(conditions) && bits != 0L; ++c) {
            ci = cond_idx[c];
            if (bits & conditions[ci].mask) {
                ++count;
                bits &= ~conditions[ci].mask;
            }
        }
        bits = condition_bits;
    }

    if ((*jEnv)->PushLocalFrame(jEnv, count * 2 + 16) != 0)
        return;

    jdata = (*jEnv)->NewIntArray(jEnv, count * 4);
    jstring_class = (*jEnv)->FindClass(jEnv, "java/lang/String");
    jtexts = (*jEnv)->NewObjectArray(jEnv, count * 2, jstring_class, NULL);

    for (i = 0; i < MAXBLSTATS; ++i) {
        if (!status_seen[i] || !status_activefields[i])
            continue;
        data[n * 4] = i;
        data[n * 4 + 1] = status_attr[i];
        data[n * 4 + 2] = status_color[i];
        data[n * 4 + 3] = status_percent[i];
        jfmt_val = char2Jstring(jEnv, status_fmt[i]);
        jreal_val = char2Jstring(jEnv, status_real[i]);
        (*jEnv)->SetObjectArrayElement(jEnv, jtexts, n * 2, jfmt_val);
        (*jEnv)->SetObjectArrayElement(jEnv, jtexts, n * 2 + 1, jreal_val);
        if (jfmt_val) (*jEnv)->DeleteLocalRef(jEnv, jfmt_val);
        if (jreal_val) (*jEnv)->DeleteLocalRef(jEnv, jreal_val);
        ++n;
    }

    for (c = 0; status_activefields[BL_CONDITION] && c < SIZE(conditions) && bits != 0L; ++c) {
        ci = cond_idx[c];
        if ((bits & conditions[ci].mask) == 0)
            continue;
        condtext = conditions[ci].text[0];
        attrmask = 0;
        coloridx = NO_COLOR;
        if (iflags.hilite_delta) {
            attrmask = cond_attr(conditions[ci].mask, condition_masks);
            coloridx = cond_color(conditions[ci].mask, condition_masks);
        }
        data[n * 4] = BL_CONDITION;
        data[n * 4 + 1] = attrmask;
        data[n * 4 + 2] = coloridx;
        data[n * 4 + 3] = 0;
        jfmt_val = char2Jstring(jEnv, condtext ? condtext : "");
        (*jEnv)->SetObjectArrayElement(jEnv, jtexts, n * 2, jfmt_val);
        (*jEnv)->SetObjectArrayElement(jEnv, jtexts, n * 2 + 1, jfmt_val);
        if (jfmt_val) (*jEnv)->DeleteLocalRef(jEnv, jfmt_val);
        bits &= ~conditions[ci].mask;
        ++n;
    }

    (*jEnv)->SetIntArrayRegion(jEnv, jdata, 0, count * 4, data);
    JNICallV(jUpdateStatus, jdata, jtexts)
    (*jEnv)->PopLocalFrame(jEnv, NULL);
}

void and_status_update(int fldidx, genericptr_t ptr, int chg UNUSED, int percent, int color, unsigned long *colormasks)
{
    long *condptr = (long *) ptr;
    char goldbuf[40];
    char *text = (char *) ptr;

    if ((fldidx < BL_RESET) || (fldidx >= MAXBLSTATS))
        return;

    if (!status_activefields[fldidx])
        return;

    switch (fldidx) {
        case BL_RESET:
        case BL_FLUSH:
            flush_status_batch();
            return;
        case BL_CONDITION:
            condition_bits = *condptr;
            condition_masks = colormasks;
            return;
        case BL_GOLD:
            text = decode_mixed(goldbuf, text);
            break;
        default:
            break;
    }

    if (text == NULL)
        text = "";

    Sprintf(status_fmt[fldidx], status_fieldfmt[fldidx] ? status_fieldfmt[fldidx] : "%s", text);
    strncpy(status_real[fldidx], text, sizeof status_real[fldidx] - 1);
    status_real[fldidx][sizeof status_real[fldidx] - 1] = '\0';
    status_attr[fldidx] = hl_attr_mask_to_atr((color >> 8) & 0x00FF);
    status_color[fldidx] = color & 0x00FF;
    status_percent[fldidx] = percent;
    status_seen[fldidx] = TRUE;
}

/* HL_ATTCLR_* attribute index -> Android ATR_* attribute bit. */
static int hl_attr_index_to_atr(int idx)
{
    switch (idx) {
        case HL_ATTCLR_DIM: return (1 << ATR_DIM);
        case HL_ATTCLR_BLINK: return (1 << ATR_BLINK);
        case HL_ATTCLR_ULINE: return (1 << ATR_ULINE);
        case HL_ATTCLR_INVERSE: return (1 << ATR_INVERSE);
        case HL_ATTCLR_BOLD: return (1 << ATR_BOLD);
        default: return 0;
    }
}

/* NetHack HL_* highlight mask -> Android ATR_* attribute bits. */
static int hl_attr_mask_to_atr(int mask)
{
    int attr = 0;
    if (mask & HL_DIM) attr |= (1 << ATR_DIM);
    if (mask & HL_BLINK) attr |= (1 << ATR_BLINK);
    if (mask & HL_ULINE) attr |= (1 << ATR_ULINE);
    if (mask & HL_INVERSE) attr |= (1 << ATR_INVERSE);
    if (mask & HL_BOLD) attr |= (1 << ATR_BOLD);
    return attr;
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