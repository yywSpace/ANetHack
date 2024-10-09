#include <jni.h>
#include <string.h>
#include <errno.h>
#include <ctype.h>

#include "hack.h"
#include "func_tab.h"   /* for extended commands */
#include "dlb.h"

#include "androidentry.h"

JNIEnv* jEnv;
jclass jApp;
jobject jAppInstance;
// window
jmethodID jRequireKeyCommand;
jmethodID jRequirePosKeyCommand;
jmethodID jCreateWindow;
jmethodID jDisplayWindow;
jmethodID jClearWindow;
jmethodID jDestroyWindow;
jmethodID jPutString;
jmethodID jRawPrint;
jmethodID jCurs;
jmethodID jPrintTile;
jmethodID jYNFunction;
jmethodID jGetLine;
jmethodID jStartMenu;
jmethodID jAddMenu;
jmethodID jEndMenu;
jmethodID jSelectMenu;
jmethodID jClipAround;
jmethodID jDelayOutput;
jmethodID jSetNumPadOption;
jmethodID jAskName;
jmethodID jRenderStatus;
jmethodID jShowExtCmdMenu;
jmethodID jGetMessageHistory;
jmethodID jPutMessageHistory;
// sound
jmethodID jInitNHSound;
jmethodID jExitNHSound;
jmethodID jSoundAchievement;
jmethodID jSoundEffect;
jmethodID jHeroPlayNotes;
jmethodID jPlayUserSound;
jmethodID jSoundAmbience;
jmethodID jSoundVerbal;

int NetHackMain(int argc, char** argv);
void initNetHackSound();
void initNetHackWin();

JNIEXPORT void JNICALL
Java_com_yywspace_anethack_NetHack_runNetHack(JNIEnv *env, jobject thiz, jstring path) {
    char* params[10];
    const char *path_c = jstring2Char(env, path);
    params[0] = "NetHack";
    params[1] = strdup(path_c);
    jEnv = env;
    jAppInstance = thiz;
    jApp = (*jEnv)->GetObjectClass(jEnv, jAppInstance);
    initNetHackWin();
    initNetHackSound();
    NetHackMain(2, params);
}

JNIEXPORT void JNICALL
Java_com_yywspace_anethack_NetHack_stopNetHack(JNIEnv *env, jobject thiz) {
    nh_terminate(EXIT_SUCCESS);
}

void initNetHackWin() {
    jCreateWindow = (*jEnv)->GetMethodID(jEnv,jApp, "createWindow", "(I)I");
    jStartMenu = (*jEnv)->GetMethodID(jEnv, jApp, "startMenu", "(IJ)V");
    jAddMenu = (*jEnv)->GetMethodID(jEnv, jApp, "addMenu", "(IIJCCIILjava/lang/String;Z)V");
    jEndMenu = (*jEnv)->GetMethodID(jEnv, jApp, "endMenu", "(ILjava/lang/String;)V");
    jSelectMenu = (*jEnv)->GetMethodID(jEnv, jApp, "selectMenu", "(II)[J");
    jDestroyWindow = (*jEnv)->GetMethodID(jEnv, jApp, "destroyWindow", "(I)V");
    jClearWindow = (*jEnv)->GetMethodID(jEnv, jApp, "clearWindow", "(II)V");
    jRawPrint = (*jEnv)->GetMethodID(jEnv, jApp, "rawPrint", "(ILjava/lang/String;)V");
    jDisplayWindow = (*jEnv)->GetMethodID(jEnv, jApp, "displayWindow", "(IZ)V");
    jCurs = (*jEnv)->GetMethodID(jEnv, jApp, "curs", "(III)V");
    jPutString = (*jEnv)->GetMethodID(jEnv, jApp, "putString", "(IILjava/lang/String;I)V");
    jPrintTile = (*jEnv)->GetMethodID(jEnv, jApp, "printTile", "(IIIIIII)V");
    jRequireKeyCommand = (*jEnv)->GetMethodID(jEnv, jApp, "requireKeyCommand", "()I");
    jRequirePosKeyCommand = (*jEnv)->GetMethodID(jEnv, jApp, "requirePosKeyCommand", "([I)C");
    jRenderStatus = (*jEnv)->GetMethodID(jEnv, jApp, "renderStatus",
                                         "(ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;III)V");
    jDelayOutput = (*jEnv)->GetMethodID(jEnv, jApp, "delayOutput", "()V");
    jClipAround = (*jEnv)->GetMethodID(jEnv, jApp, "clipAround", "(IIII)V");
    jYNFunction = (*jEnv)->GetMethodID(jEnv, jApp, "ynFunction",
                                       "(Ljava/lang/String;Ljava/lang/String;[JC)C");
    jGetLine = (*jEnv)->GetMethodID(jEnv, jApp, "getLine", "(Ljava/lang/String;Ljava/lang/String;I)Ljava/lang/String;");
    jAskName = (*jEnv)->GetMethodID(jEnv, jApp, "askName","(I[Ljava/lang/String;)[Ljava/lang/String;");
    jShowExtCmdMenu = (*jEnv)->GetMethodID(jEnv, jApp, "showExtCmdMenu", "([Ljava/lang/String;)I");
    jGetMessageHistory = (*jEnv)->GetMethodID(jEnv, jApp, "getMessageHistory","(I)Ljava/lang/String;");
    jPutMessageHistory = (*jEnv)->GetMethodID(jEnv, jApp, "putMessageHistory", "(Ljava/lang/String;Z)V");
}

void initNetHackSound() {
    jInitNHSound = (*jEnv)->GetMethodID(jEnv, jApp, "initNHSound", "()V");
    jExitNHSound = (*jEnv)->GetMethodID(jEnv, jApp, "exitNHSound", "(Ljava/lang/String;)V");
    jSoundAchievement = (*jEnv)->GetMethodID(jEnv, jApp, "soundAchievement", "(III)V");
    jSoundEffect = (*jEnv)->GetMethodID(jEnv, jApp, "soundEffect",
                                        "(Ljava/lang/String;Ljava/lang/String;II)V");
    jHeroPlayNotes = (*jEnv)->GetMethodID(jEnv, jApp, "heroPlayNotes", "(ILjava/lang/String;I)V");
    jPlayUserSound = (*jEnv)->GetMethodID(jEnv, jApp, "playUserSound", "(Ljava/lang/String;II)V");
    jSoundAmbience = (*jEnv)->GetMethodID(jEnv, jApp, "soundAmbience", "(III)V");
    jSoundVerbal = (*jEnv)->GetMethodID(jEnv, jApp, "soundVerbal", "(Ljava/lang/String;IIII)V");
}

jstring char2Jstring(JNIEnv *env, const char *c_str) {
    if (c_str == NULL)
        return NULL;
    jclass str_cls = (*env)->FindClass(env, "java/lang/String");
    jmethodID constructor_mid = (*env)->GetMethodID(env, str_cls, "<init>", "([BLjava/lang/String;)V");
    int len = (int)strlen(c_str);
    jbyteArray bytes = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, bytes, 0, len, (signed char *)c_str);
    jstring charsetName = (*env)->NewStringUTF(env, "utf-8");
    return (*env)->NewObject(env,str_cls,constructor_mid,bytes,charsetName);
}

char* jstring2Char(JNIEnv *env, jstring jstr)
{
    char* rtn = NULL;
    jclass jclass = (*env)->FindClass(env, "java/lang/String");
    jmethodID mid = (*env)->GetMethodID(env, jclass, "getBytes", "(Ljava/lang/String;)[B");
    jstring jencode = (*env)->NewStringUTF(env, "utf-8");
    jbyteArray barr= (jbyteArray)(*env)->CallObjectMethod(env, jstr, mid, jencode);
    jsize alen = (*env)->GetArrayLength(env, barr);
    jbyte* ba = (*env)->GetByteArrayElements(env, barr, JNI_FALSE);
    if (alen > 0)
    {
        rtn = (char*)malloc(alen + 1);
        memcpy(rtn, ba, alen);
        rtn[alen] = 0;
    }
    (*env)->ReleaseByteArrayElements(env, barr, ba, 0);
    return rtn;
}