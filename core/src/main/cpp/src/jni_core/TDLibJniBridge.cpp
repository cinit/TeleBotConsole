//
// Created by kinit on 7/5/22.
//

#include <unistd.h>
#include <string>
#include <cerrno>
#include <cstring>

#include "natives_utils.h"

#include "../jni/cc_ioctl_telebot_intern_NativeBridge.h"
#include "../jni/cc_ioctl_telebot_cli_Console.h"
#include "../jni/cc_ioctl_telebot_util_OsUtils.h"

#include <MMKV.h>

#include "Console.h"
#include "utils/log/Log.h"
#include "utils/text/EncodingHelper.h"
#include "LogImpl.h"

#include <td/telegram/td_json_client.h>

using cli::Console;


static bool throwIfNull(JNIEnv *env, jobject obj, const char *msg) {
    if (obj == nullptr) {
        jclass clazz = env->FindClass("java/lang/NullPointerException");
        env->ThrowNew(clazz, msg);
        return true;
    }
    return false;
}

static void throwIllegalArgumentException(JNIEnv *env, const char *msg) {
    jclass clazz = env->FindClass("java/lang/IllegalArgumentException");
    env->ThrowNew(clazz, msg);
}

static void throwIllegalStateException(JNIEnv *env, const char *msg) {
    jclass clazz = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(clazz, msg);
}

#define requiresNonNullP(__obj, __msg) if (throwIfNull(env, __obj, __msg)) return nullptr; ((void)0)
#define requiresNonNullV(__obj, __msg) if (throwIfNull(env, __obj, __msg)) return; ((void)0)
#define requiresNonNullZ(__obj, __msg) if (throwIfNull(env, __obj, __msg)) return 0; ((void)0)

static std::string getJstringToUtf8(JNIEnv *env, jstring jstr) {
    if (jstr == nullptr) {
        return "";
    }
    int len = env->GetStringLength(jstr);
    std::u16string str16;
    str16.resize(len);
    env->GetStringRegion(jstr, 0, len, (jchar *) str16.data());
    return swgui::EncodingHelper::toString8(str16);
}

EXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = nullptr;
    jint retCode = MMKV_JNI_OnLoad(vm, reserved);
    if (retCode < 0) {
        return retCode;
    }
    if (vm->GetEnv((void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    return JNI_VERSION_1_6;
}

std::string gWorkingDir;

/*
 * Class:     cc_ioctl_telebot_intern_NativeBridge
 * Method:    nativeInit
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_cc_ioctl_telebot_intern_NativeBridge_nativeInit
        (JNIEnv *env, jclass, jstring jworkingDir) {
    requiresNonNullV(jworkingDir, "workingDir is null");
    std::string workingDir = getJstringToUtf8(env, jworkingDir);
    if (workingDir.empty() || workingDir[0] != '/') {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "workingDir is not absolute path");
        return;
    }
    if (chdir(workingDir.c_str()) != 0) {
        env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"),
                      std::string("chdir failed: " + std::string(strerror(errno))).c_str());
        return;
    }
    gWorkingDir = workingDir;
    Console &console = Console::getInstance();
    Log::setLogHandler(LogImpl::getLogHandler());
    MMKV::registerLogHandler(+[](MMKVLogLevel level, const char *file, int line, const char *function, MMKVLog_t message) {
        Log::Level lv;
        std::string msg = message + " at " + function + "(" + file + ":" + std::to_string(line) + ")";
        switch (level) {
            case MMKVLogDebug: {
                lv = Log::Level::DEBUG;
                break;
            }
            case MMKVLogInfo: {
                lv = Log::Level::INFO;
                break;
            }
            case MMKVLogWarning: {
                lv = Log::Level::WARN;
                break;
            }
            case MMKVLogError: {
                lv = Log::Level::ERROR;
                break;
            }
            default: {
                lv = Log::Level::INFO;
                break;
            }
        }
        Log::logBuffer(lv, "MMKV", msg.c_str());
    });
}

/*
 * Class:     cc_ioctl_telebot_util_OsUtils
 * Method:    getPid
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_cc_ioctl_telebot_util_OsUtils_getPid
        (JNIEnv *env, jclass) {
    return (jint) TEMP_FAILURE_RETRY(getpid());
}

/*
 * Class:     cc_ioctl_telebot_intern_NativeBridge
 * Method:    nativeTDLibPollEventUnlocked
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_cc_ioctl_telebot_intern_NativeBridge_nativeTDLibPollEventUnlocked
        (JNIEnv *env, jclass, jint timeout_ms) {
    if (gWorkingDir.empty()) {
        throwIllegalStateException(env, "nativeInit not called");
        return nullptr;
    }
    double timeout_sec = double(timeout_ms) / 1000.0;
    const char *event = td_receive(timeout_sec);
    if (event == nullptr) {
        return nullptr;
    } else {
        std::string event_str = event;
        return env->NewStringUTF(event_str.c_str());
    }
}

/*
 * Class:     cc_ioctl_telebot_intern_NativeBridge
 * Method:    nativeTDLibExecuteSynchronized
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_cc_ioctl_telebot_intern_NativeBridge_nativeTDLibExecuteSynchronized
        (JNIEnv *env, jclass, jstring jstrRequest) {
    requiresNonNullP(jstrRequest, "request is null");
    if (gWorkingDir.empty()) {
        throwIllegalStateException(env, "nativeInit not called");
        return nullptr;
    }
    std::string request = getJstringToUtf8(env, jstrRequest);
    std::string response = td_execute(request.c_str());
    return env->NewStringUTF(response.c_str());
}

/*
 * Class:     cc_ioctl_telebot_intern_NativeBridge
 * Method:    nativeTDLibExecuteAsync
 * Signature: (ILjava/lang/String;)V
 */
JNIEXPORT void JNICALL
Java_cc_ioctl_telebot_intern_NativeBridge_nativeTDLibExecuteAsync
        (JNIEnv *env, jclass, jint clientId, jstring jstrRequest) {
    requiresNonNullV(jstrRequest, "request is null");
    if (gWorkingDir.empty()) {
        throwIllegalStateException(env, "nativeInit not called");
        return;
    }
    std::string request = getJstringToUtf8(env, jstrRequest);
    int id = clientId;
    if (id < -1) {
        throwIllegalArgumentException(env, "clientId is invalid");
        return;
    }
    td_send(id, request.c_str());
}

/*
 * Class:     cc_ioctl_telebot_intern_NativeBridge
 * Method:    nativeTDLibCreateClient
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_cc_ioctl_telebot_intern_NativeBridge_nativeTDLibCreateClient
        (JNIEnv *env, jclass) {
    if (gWorkingDir.empty()) {
        throwIllegalStateException(env, "nativeInit not called");
        return -1;
    }
    return td_create_client_id();
}


/*
 * Class:     cc_ioctl_telebot_cli_Console
 * Method:    nGetConsoleInfo
 * Signature: ()Lcc/ioctl/telebot/cli/Console$ConsoleInfo;
 */
JNIEXPORT jobject JNICALL
Java_cc_ioctl_telebot_cli_Console_nGetConsoleInfo
        (JNIEnv *env, jobject) {
    Console &console = Console::getInstance();
    // TODO: 2022-07-16 implement console info
    return env->NewObject(env->FindClass("cc/ioctl/telebot/cli/Console$ConsoleInfo"),
                          env->GetMethodID(env->FindClass("cc/ioctl/telebot/cli/Console$ConsoleInfo"), "<init>", "(ZZII)V"),
                          true, false, 120, 80);
}

/*
 * Class:     cc_ioctl_telebot_cli_Console
 * Method:    nUpdateStatusText
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_cc_ioctl_telebot_cli_Console_nUpdateStatusText
        (JNIEnv *env, jobject, jstring jstrStatusText) {
    auto &console = Console::getInstance();
    std::string statusText = getJstringToUtf8(env, jstrStatusText);
    console.updateStatusText(statusText);
}

/*
 * Class:     cc_ioctl_telebot_cli_Console
 * Method:    nUpdateTitleText
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_cc_ioctl_telebot_cli_Console_nUpdateTitleText
        (JNIEnv *env, jobject, jstring jstrTitleText) {
    auto &console = Console::getInstance();
    std::string titleText = getJstringToUtf8(env, jstrTitleText);
    // TODO: 2022-07-16 Implement updateTitleText
}

/*
 * Class:     cc_ioctl_telebot_cli_Console
 * Method:    nPrintLine
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_cc_ioctl_telebot_cli_Console_nPrintLine
        (JNIEnv *env, jobject, jstring jstrLine) {
    if (jstrLine == nullptr) {
        return;
    }
    std::string line = getJstringToUtf8(env, jstrLine);
    Console::getInstance().printLine(line);
}

/*
 * Class:     cc_ioctl_telebot_cli_Console
 * Method:    nLogMessage
 * Signature: (ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_cc_ioctl_telebot_cli_Console_nLogMessage
        (JNIEnv *env, jobject, jint level, jstring jstrTag, jstring jstrMessage, jstring jstrDetails) {
    std::string tag = getJstringToUtf8(env, jstrTag);
    std::string message = getJstringToUtf8(env, jstrMessage);
    std::string details = getJstringToUtf8(env, jstrDetails);
    if (tag.empty()) {
        tag = "NO_TAG";
    }
    if (!details.empty()) {
        message += "\n" + details;
    }
    Log::logBuffer(static_cast<Log::Level>(level), tag.c_str(), message.c_str());
}

/*
 * Class:     cc_ioctl_telebot_cli_Console
 * Method:    nPromptInputText
 * Signature: (Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Z)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_cc_ioctl_telebot_cli_Console_nPromptInputText
        (JNIEnv *env, jobject, jstring, jstring, jstring, jboolean) {
    env->ThrowNew(env->FindClass("java/lang/UnsupportedOperationException"), "Not implemented");
    return nullptr;
}
