/* andsoundroidsound.c */
/* NetHack may be freely redistributed.  See license for details. */

#ifdef SND_LIB_ANDSOUND

#include "hack.h"
#include <jni.h>

#include "androidentry.h"

#include <android/log.h>
#define TAG "NativeNetHackSound"
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
    JNICallV(jInitNHSound)
}

static void
andsound_exit_nhsound(const char *reason)
{
    LOGD("exit_nhsound %s", reason);
    jstring jreason = char2Jstring(jEnv, reason);
    JNICallV(jExitNHSound, jreason)
}

static void
andsound_achievement(schar ach1, schar ach2, int32_t repeat)
{
    LOGD("achievement ach1:%d, ach2:%d, repeat:%d", ach1, ach2, repeat);
    JNICallV(jSoundAchievement, ach1, ach2, repeat)
}

static void
andsound_soundeffect(char *desc, int32_t seid, int32_t volume)
{
#ifdef SND_SOUNDEFFECTS_AUTOMAP
    char buf[1024];
    const char *soundname = get_sound_effect_filename(seid, buf, sizeof buf,
                                          sff_base_only);
    LOGD("soundeffect soundname:%s, desc:%s, seid:%d, volume:%d", soundname, desc, seid, volume);
    jstring jsoundname = char2Jstring(jEnv, soundname);
    jstring jdesc = char2Jstring(jEnv, desc);
    JNICallV(jSoundEffect, jsoundname, jdesc, seid, volume)
#endif
}

static void andsound_hero_playnotes(int32_t instrument, const char *str, int32_t volume)
{
    LOGD("hero_playnotes instrument::%d, str:%s， volume:%d", instrument, str, volume);
    jstring jstr = char2Jstring(jEnv, str);
    JNICallV(jHeroPlayNotes, instrument, jstr, volume)
}

static void
andsound_play_usersound(char *filename, int32_t volume UNUSED, int32_t idx UNUSED)
{
    LOGD("play_usersound filename:%s, volume:%d, idx:%d", filename, volume, idx);
    jstring jfilename = char2Jstring(jEnv, filename);
    JNICallV(jPlayUserSound, jfilename, volume, idx)
}

static void
andsound_ambience(int32_t ambienceid, int32_t ambience_action,
              int32_t hero_proximity)
{
    LOGD("ambience ambienceid:%d, ambience_action:%d, hero_proximity:%d", ambienceid, ambience_action, hero_proximity);
    JNICallV(jSoundAmbience, ambienceid, ambience_action, hero_proximity)
}

static void
andsound_verbal(char *text, int32_t gender, int32_t tone,
            int32_t vol, int32_t moreinfo)
{
#ifdef SND_SPEECH
    LOGD("verbal text:%s, gender:%d, tone:%d, vol:%d, moreinfo:%d", text, gender, tone, vol, moreinfo);
    jstring jtext = char2Jstring(jEnv, text);
    JNICallV(jSoundVerbal, jtext, gender, tone, vol, moreinfo)
#endif
}

#endif