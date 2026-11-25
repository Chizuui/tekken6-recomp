/**
 * @file        android/tekken6_helper_jni.cpp
 *
 * @brief       JNI bridge for com.chizui.tekken6.Tekken6Helper (libtekken6helper.so).
 * Used by the launcher before the game starts: env vars and ISO extraction.
 */
#include <cstdlib>
#include <string>

#include <jni.h>

#include "iso_extract.h"

extern "C" {

JNIEXPORT void JNICALL
Java_com_chizui_tekken6_Tekken6Helper_setEnv(JNIEnv* env, jclass /*clazz*/, jstring key,
                                            jstring value) {
  const char* k = env->GetStringUTFChars(key, nullptr);
  const char* v = env->GetStringUTFChars(value, nullptr);
  if (k && v) {
    setenv(k, v, 1);
  }
  if (k) {
    env->ReleaseStringUTFChars(key, k);
  }
  if (v) {
    env->ReleaseStringUTFChars(value, v);
  }
}

JNIEXPORT jint JNICALL
Java_com_chizui_tekken6_Tekken6Helper_extractIso(JNIEnv* env, jclass /*clazz*/, jstring iso_path,
                                                jstring dest_dir, jobject listener) {
  const char* iso = env->GetStringUTFChars(iso_path, nullptr);
  const char* dest = env->GetStringUTFChars(dest_dir, nullptr);
  if (!iso || !dest) {
    if (iso) env->ReleaseStringUTFChars(iso_path, iso);
    if (dest) env->ReleaseStringUTFChars(dest_dir, dest);
    return -1;
  }

  // Resolve the progress callback once (same thread does the extraction).
  jmethodID on_progress = nullptr;
  if (listener) {
    jclass listener_class = env->GetObjectClass(listener);
    on_progress = env->GetMethodID(listener_class, "onProgress", "(FLjava/lang/String;)V");
  }

  int result = tekken6::android::ExtractIsoImage(
      iso, dest,
      [env, listener, on_progress](float fraction, const std::string& current_file) {
        if (!listener || !on_progress) {
          return;
        }
        jstring name = env->NewStringUTF(current_file.c_str());
        env->CallVoidMethod(listener, on_progress, fraction, name);
        env->DeleteLocalRef(name);
      });

  env->ReleaseStringUTFChars(iso_path, iso);
  env->ReleaseStringUTFChars(dest_dir, dest);
  return result;
}

}  // extern "C"
