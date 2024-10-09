
#ifndef ANETHACK_ANDROIDENTRY_H
#define ANETHACK_ANDROIDENTRY_H

#include <jni.h>

// jni env
extern JNIEnv* jEnv;
extern jclass jApp;
extern jobject jAppInstance;

// window
extern jmethodID jRequireKeyCommand;
extern jmethodID jRequirePosKeyCommand;
extern jmethodID jCreateWindow;
extern jmethodID jDisplayWindow;
extern jmethodID jClearWindow;
extern jmethodID jDestroyWindow;
extern jmethodID jPutString;
extern jmethodID jRawPrint;
extern jmethodID jCurs;
extern jmethodID jPrintTile;
extern jmethodID jYNFunction;
extern jmethodID jGetLine;
extern jmethodID jStartMenu;
extern jmethodID jAddMenu;
extern jmethodID jEndMenu;
extern jmethodID jSelectMenu;
extern jmethodID jClipAround;
extern jmethodID jDelayOutput;
extern jmethodID jSetNumPadOption;
extern jmethodID jAskName;
extern jmethodID jRenderStatus;
extern jmethodID jShowExtCmdMenu;
extern jmethodID jGetMessageHistory;
extern jmethodID jPutMessageHistory;
// sound
extern jmethodID jInitNHSound;
extern jmethodID jExitNHSound;
extern jmethodID jSoundAchievement;
extern jmethodID jSoundEffect;
extern jmethodID jHeroPlayNotes;
extern jmethodID jPlayUserSound;
extern jmethodID jSoundAmbience;
extern jmethodID jSoundVerbal;
// string
char * jstring2Char(JNIEnv *env, jstring jstr);
jstring char2Jstring(JNIEnv *env, const char *c_str);

#define JNICallV(func, ...) (*jEnv)->CallVoidMethod(jEnv, jAppInstance, func, ## __VA_ARGS__);
#define JNICallI(func, ...) (*jEnv)->CallIntMethod(jEnv, jAppInstance, func, ## __VA_ARGS__);
#define JNICallO(func, ...) (*jEnv)->CallObjectMethod(jEnv, jAppInstance, func, ## __VA_ARGS__);
#define JNICallC(func, ...) (*jEnv)->CallCharMethod(jEnv, jAppInstance, func, ## __VA_ARGS__);

#endif //ANETHACK_ANDROIDENTRY_H
