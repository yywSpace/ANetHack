/* andsoundroidsound.c */
/* NetHack may be freely redistributed.  See license for details. */

#ifdef SND_LIB_ANDSOUND

#include "hack.h"
#include <android/log.h>

#define TAG "NetHack Native Sound"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)


static void andsound_init_nhsound(void);
static void andsound_exit_nhsound(const char *);
static void andsound_achievement(schar, schar, int32_t);
static void andsound_soundeffect(char *, int32_t, int32_t);
static void andsound_hero_playnotes(int32_t instrument, const char *str, int32_t volume);
static void andsound_play_usersound(char *, int32_t, int32_t);
static void andsound_ambience(int32_t, int32_t, int32_t);
static void andsound_verbal(char *, int32_t, int32_t, int32_t, int32_t);

struct sound_procs andsound_procs = {
    SOUNDID(andsound),
    SOUND_TRIGGER_USERSOUNDS | SOUND_TRIGGER_SOUNDEFFECTS
    | SOUND_TRIGGER_HEROMUSIC | SOUND_TRIGGER_AMBIENCE
    | SOUND_TRIGGER_ACHIEVEMENTS | SOUND_TRIGGER_VERBAL,
    andsound_init_nhsound,
    andsound_exit_nhsound,
    andsound_achievement,
    andsound_soundeffect,
    andsound_hero_playnotes,
    andsound_play_usersound,
    andsound_ambience,
    andsound_verbal
};

static void
andsound_init_nhsound(void)
{
    LOGD("init_nhsound");
}

static void
andsound_exit_nhsound(const char *reason)
{
    LOGD("exit_nhsound %s", reason);
}

static void
andsound_achievement(schar ach1, schar ach2, int32_t repeat)
{
    LOGD("achievement");
}

static void
andsound_soundeffect(char *desc, int32_t seid, int32_t volume)
{
    LOGD("soundeffect %s", desc);
}

static void andsound_hero_playnotes(int32_t instrument, const char *str, int32_t volume)
{
    LOGD("hero_playnotes %s", str);
}

static void
andsound_play_usersound(char *filename, int32_t volume UNUSED, int32_t idx UNUSED)
{
    LOGD("play_usersound %s", filename);
}

static void
andsound_ambience(int32_t ambienceid, int32_t ambience_action,
              int32_t hero_proximity)
{
    LOGD("ambience %d", ambience_action);
}

static void
andsound_verbal(char *text, int32_t gender, int32_t tone,
            int32_t vol, int32_t moreinfo)
{
    LOGD("verbal %s", text);
}

#endif