/*
 * JNI bridge for byedpi (https://github.com/hufrea/byedpi).
 *
 * byedpi is a command line program that keeps its configuration in the
 * global `params` struct. To run it more than once per process we snapshot
 * the pristine defaults before the first run and restore them before every
 * following run, and reset getopt's state.
 */
#include <jni.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <android/log.h>

#include "params.h"

#define TAG "RknByeDpi"

extern int optind;
extern int server_fd;

/* Restart getopt from scratch: bionic (BSD getopt) uses optreset, glibc optind = 0. */
static void reset_getopt(void)
{
#ifdef __ANDROID__
    extern int optreset;
    optreset = 1;
    optind = 1;
#else
    optind = 0;
#endif
}

int byedpi_main(int argc, char **argv);

static pthread_mutex_t run_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t state_lock = PTHREAD_MUTEX_INITIALIZER;
static struct params pristine;
static int pristine_saved;
static int running;

JNIEXPORT jint JNICALL
Java_com_richizu_fixrkn_bypass_ByeDpiNative_run(JNIEnv *env, jclass cls, jobjectArray jargs)
{
    if (pthread_mutex_trylock(&run_lock) != 0) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "already running");
        return -100;
    }

    int argc = (*env)->GetArrayLength(env, jargs);
    char **argv = calloc((size_t) argc + 1, sizeof(char *));
    for (int i = 0; i < argc; i++) {
        jstring item = (jstring) (*env)->GetObjectArrayElement(env, jargs, i);
        const char *utf = (*env)->GetStringUTFChars(env, item, NULL);
        argv[i] = strdup(utf);
        (*env)->ReleaseStringUTFChars(env, item, utf);
        (*env)->DeleteLocalRef(env, item);
    }

    pthread_mutex_lock(&state_lock);
    if (!pristine_saved) {
        pristine = params;
        pristine_saved = 1;
    } else {
        params = pristine;
    }
    reset_getopt();
    server_fd = -1;
    running = 1;
    pthread_mutex_unlock(&state_lock);

    __android_log_print(ANDROID_LOG_INFO, TAG, "start, %d args", argc);
    int code = byedpi_main(argc, argv);
    __android_log_print(ANDROID_LOG_INFO, TAG, "exit code %d", code);

    pthread_mutex_lock(&state_lock);
    running = 0;
    server_fd = -1;
    pthread_mutex_unlock(&state_lock);

    for (int i = 0; i < argc; i++) {
        free(argv[i]);
    }
    free(argv);
    pthread_mutex_unlock(&run_lock);
    return code;
}

JNIEXPORT void JNICALL
Java_com_richizu_fixrkn_bypass_ByeDpiNative_stop(JNIEnv *env, jclass cls)
{
    pthread_mutex_lock(&state_lock);
    if (running && server_fd >= 0) {
        shutdown(server_fd, SHUT_RDWR);
    }
    pthread_mutex_unlock(&state_lock);
}

JNIEXPORT void JNICALL
Java_com_richizu_fixrkn_bypass_ByeDpiNative_forceStop(JNIEnv *env, jclass cls)
{
    pthread_mutex_lock(&state_lock);
    if (running && server_fd >= 0) {
        shutdown(server_fd, SHUT_RDWR);
        close(server_fd);
        server_fd = -1;
    }
    pthread_mutex_unlock(&state_lock);
}
