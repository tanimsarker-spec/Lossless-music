#include <jni.h>
#include "ll_core.h"
#define F(n) Java_com_tanimsarker_lossless_Engine_##n
extern "C" {
JNIEXPORT jint JNICALL F(open)(JNIEnv* e, jclass, jstring p) {
  const char* s = e->GetStringUTFChars(p, nullptr);
  int r = ll_open(s);
  e->ReleaseStringUTFChars(p, s);
  return r;
}
JNIEXPORT void JNICALL F(close)(JNIEnv*, jclass) { ll_close(); }
JNIEXPORT void JNICALL F(play)(JNIEnv*, jclass) { ll_play(); }
JNIEXPORT void JNICALL F(pause)(JNIEnv*, jclass) { ll_pause(); }
JNIEXPORT void JNICALL F(seek)(JNIEnv*, jclass, jdouble s) { ll_seek(s); }
JNIEXPORT jdouble JNICALL F(position)(JNIEnv*, jclass) { return ll_position(); }
JNIEXPORT jdouble JNICALL F(duration)(JNIEnv*, jclass) { return ll_duration(); }
JNIEXPORT jint JNICALL F(playing)(JNIEnv*, jclass) { return ll_is_playing(); }
JNIEXPORT jint JNICALL F(finished)(JNIEnv*, jclass) { return ll_finished(); }
JNIEXPORT void JNICALL F(eq)(JNIEnv*, jclass, jboolean on) { ll_eq(on ? 1 : 0); }
JNIEXPORT void JNICALL F(band)(JNIEnv*, jclass, jint i, jfloat db) { ll_band(i, db); }
JNIEXPORT void JNICALL F(preamp)(JNIEnv*, jclass, jfloat db) { ll_preamp(db); }
JNIEXPORT jint JNICALL F(sourceRate)(JNIEnv*, jclass) { return (jint)ll_source_rate(); }
JNIEXPORT jint JNICALL F(sourceBits)(JNIEnv*, jclass) { return (jint)ll_source_bits(); }
JNIEXPORT jint JNICALL F(outputRate)(JNIEnv*, jclass) { return (jint)ll_output_rate(); }
}
