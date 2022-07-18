/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class cc_ioctl_intern_NativeBridge */

#ifndef _Included_cc_ioctl_intern_NativeBridge
#define _Included_cc_ioctl_intern_NativeBridge
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     cc_ioctl_intern_NativeBridge
 * Method:    nativeInit
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_cc_ioctl_intern_NativeBridge_nativeInit
  (JNIEnv *, jclass, jstring);

/*
 * Class:     cc_ioctl_intern_NativeBridge
 * Method:    nativeTDLibPollEventUnlocked
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_cc_ioctl_intern_NativeBridge_nativeTDLibPollEventUnlocked
  (JNIEnv *, jclass, jint);

/*
 * Class:     cc_ioctl_intern_NativeBridge
 * Method:    nativeTDLibExecuteSynchronized
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_cc_ioctl_intern_NativeBridge_nativeTDLibExecuteSynchronized
  (JNIEnv *, jclass, jstring);

/*
 * Class:     cc_ioctl_intern_NativeBridge
 * Method:    nativeTDLibExecuteAsync
 * Signature: (ILjava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_cc_ioctl_intern_NativeBridge_nativeTDLibExecuteAsync
  (JNIEnv *, jclass, jint, jstring);

/*
 * Class:     cc_ioctl_intern_NativeBridge
 * Method:    nativeTDLibCreateClient
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_cc_ioctl_intern_NativeBridge_nativeTDLibCreateClient
  (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif