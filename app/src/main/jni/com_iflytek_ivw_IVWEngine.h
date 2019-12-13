/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class com_iflytek_ivw_IVWEngine */

#ifndef _Included_com_iflytek_ivw_IVWEngine
#define _Included_com_iflytek_ivw_IVWEngine
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     com_iflytek_ivw_IVWEngine
 * Method:    create_ivw
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_iflytek_ivw_IVWEngine_create_1ivw
  (JNIEnv *, jobject, jstring);

/*
 * Class:     com_iflytek_ivw_IVWEngine
 * Method:    ivw_auth
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_com_iflytek_ivw_IVWEngine_ivw_1auth
  (JNIEnv *, jobject, jstring);

/*
 * Class:     com_iflytek_ivw_IVWEngine
 * Method:    write_audio
 * Signature: ([BI)I
 */
JNIEXPORT jint JNICALL Java_com_iflytek_ivw_IVWEngine_write_1audio
  (JNIEnv *, jobject, jbyteArray, jint);

/*
 * Class:     com_iflytek_ivw_IVWEngine
 * Method:    get_version
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_com_iflytek_ivw_IVWEngine_get_1version
  (JNIEnv *, jobject);

/*
 * Class:     com_iflytek_ivw_IVWEngine
 * Method:    set_log_level
 * Signature: (I)V
 */
JNIEXPORT void JNICALL Java_com_iflytek_ivw_IVWEngine_set_1log_1level
  (JNIEnv *, jobject, jint);

/*
 * Class:     com_iflytek_ivw_IVWEngine
 * Method:    destroy_ivw
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_com_iflytek_ivw_IVWEngine_destroy_1ivw
  (JNIEnv *, jobject);

#ifdef __cplusplus
}
#endif
#endif