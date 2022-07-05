//
// Created by kinit on 7/5/22.
//

#include "natives_utils.h"

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
