#define _GNU_SOURCE
#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#ifndef MFD_CLOEXEC
#define MFD_CLOEXEC 0x0001U
#endif
#ifndef MFD_ALLOW_SEALING
#define MFD_ALLOW_SEALING 0x0002U
#endif
#ifndef AT_EMPTY_PATH
#define AT_EMPTY_PATH 0x1000
#endif
#ifndef F_ADD_SEALS
#define F_ADD_SEALS 1033
#endif
#ifndef F_SEAL_SEAL
#define F_SEAL_SEAL 0x0001
#endif
#ifndef F_SEAL_SHRINK
#define F_SEAL_SHRINK 0x0002
#endif
#ifndef F_SEAL_GROW
#define F_SEAL_GROW 0x0004
#endif
#ifndef F_SEAL_WRITE
#define F_SEAL_WRITE 0x0008
#endif

static char **read_string_array(JNIEnv *env, jobjectArray array, jsize *count) {
    *count = array ? (*env)->GetArrayLength(env, array) : 0;
    char **values = (char **) calloc((size_t) *count + 1, sizeof(char *));
    if (!values) return NULL;
    for (jsize i = 0; i < *count; ++i) {
        jstring item = (jstring) (*env)->GetObjectArrayElement(env, array, i);
        if (!item) { values[i] = NULL; continue; }
        const char *utf = (*env)->GetStringUTFChars(env, item, NULL);
        if (!utf) {
            (*env)->DeleteLocalRef(env, item);
            for (jsize j = 0; j < i; ++j) free(values[j]);
            free(values);
            return NULL;
        }
        values[i] = strdup(utf);
        (*env)->ReleaseStringUTFChars(env, item, utf);
        (*env)->DeleteLocalRef(env, item);
        if (!values[i]) {
            for (jsize j = 0; j < i; ++j) free(values[j]);
            free(values);
            return NULL;
        }
    }
    return values;
}

static void free_string_array(char **values) {
    if (!values) return;
    for (size_t i = 0; values[i] != NULL; ++i) free(values[i]);
    free(values);
}

static int stage_elf_in_memfd(const char *path) {
#if defined(__aarch64__) && defined(__NR_memfd_create)
    int source = open(path, O_RDONLY | O_CLOEXEC);
    if (source < 0) return -errno;
    int target = (int) syscall(__NR_memfd_create, "nova-guest-exec", MFD_CLOEXEC | MFD_ALLOW_SEALING);
    if (target < 0) {
        int saved = errno;
        close(source);
        return -saved;
    }
    char buffer[65536];
    ssize_t count;
    while ((count = read(source, buffer, sizeof(buffer))) > 0) {
        ssize_t offset = 0;
        while (offset < count) {
            ssize_t written = write(target, buffer + offset, (size_t)(count - offset));
            if (written < 0) {
                int saved = errno;
                close(source); close(target);
                return -saved;
            }
            offset += written;
        }
    }
    if (count < 0) {
        int saved = errno;
        close(source); close(target);
        return -saved;
    }
    close(source);
    if (lseek(target, 0, SEEK_SET) < 0) {
        int saved = errno;
        close(target);
        return -saved;
    }
    (void) fcntl(target, F_ADD_SEALS, F_SEAL_SEAL | F_SEAL_SHRINK | F_SEAL_GROW | F_SEAL_WRITE);
    return target;
#else
    (void) path;
    return -ENOTSUP;
#endif
}

JNIEXPORT jint JNICALL
Java_app_nova_desktop_runtime_NativeProcessLauncher_nativeStart(
        JNIEnv *env, jclass clazz, jstring jExecutable, jobjectArray jArguments,
        jobjectArray jEnvironment, jstring jWorkingDirectory, jint sdkInt) {
    (void) clazz;
    if (!jExecutable || !jWorkingDirectory) return -EINVAL;

    const char *executable = (*env)->GetStringUTFChars(env, jExecutable, NULL);
    const char *working = (*env)->GetStringUTFChars(env, jWorkingDirectory, NULL);
    if (!executable || !working) {
        if (executable) (*env)->ReleaseStringUTFChars(env, jExecutable, executable);
        if (working) (*env)->ReleaseStringUTFChars(env, jWorkingDirectory, working);
        return -ENOMEM;
    }

    jsize argc = 0, envc = 0;
    char **argv = read_string_array(env, jArguments, &argc);
    char **envp = read_string_array(env, jEnvironment, &envc);
    if (!argv || !envp) {
        free_string_array(argv); free_string_array(envp);
        (*env)->ReleaseStringUTFChars(env, jExecutable, executable);
        (*env)->ReleaseStringUTFChars(env, jWorkingDirectory, working);
        return -ENOMEM;
    }
    if (argc == 0 || argv[0] == NULL) {
        free_string_array(argv); free_string_array(envp);
        (*env)->ReleaseStringUTFChars(env, jExecutable, executable);
        (*env)->ReleaseStringUTFChars(env, jWorkingDirectory, working);
        return -EINVAL;
    }

    int executableFd = -1;
    if (sdkInt >= 29) {
        executableFd = stage_elf_in_memfd(executable);
        if (executableFd < 0) {
            int error = executableFd;
            free_string_array(argv); free_string_array(envp);
            (*env)->ReleaseStringUTFChars(env, jExecutable, executable);
            (*env)->ReleaseStringUTFChars(env, jWorkingDirectory, working);
            return error;
        }
    }

    pid_t child = fork();
    if (child < 0) {
        int error = errno;
        if (executableFd >= 0) close(executableFd);
        free_string_array(argv); free_string_array(envp);
        (*env)->ReleaseStringUTFChars(env, jExecutable, executable);
        (*env)->ReleaseStringUTFChars(env, jWorkingDirectory, working);
        return -error;
    }

    if (child == 0) {
        if (chdir(working) != 0) _exit(126);
        if (sdkInt >= 29) {
#if defined(__aarch64__) && defined(__NR_execveat)
            syscall(__NR_execveat, executableFd, "", argv, envp, AT_EMPTY_PATH);
#else
            errno = ENOTSUP;
#endif
        } else {
            execve(executable, argv, envp);
        }
        _exit(errno == ENOENT ? 127 : 126);
    }

    if (executableFd >= 0) close(executableFd);
    free_string_array(argv); free_string_array(envp);
    (*env)->ReleaseStringUTFChars(env, jExecutable, executable);
    (*env)->ReleaseStringUTFChars(env, jWorkingDirectory, working);
    return (jint) child;
}

JNIEXPORT jint JNICALL
Java_app_nova_desktop_runtime_NativeProcessLauncher_nativePoll(JNIEnv *env, jclass clazz, jint pid) {
    (void) env; (void) clazz;
    int status = 0;
    pid_t result = waitpid((pid_t) pid, &status, WNOHANG);
    if (result == 0) return -2147483647;
    if (result < 0) return -errno;
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 128 + WTERMSIG(status);
    return -1;
}

JNIEXPORT jint JNICALL
Java_app_nova_desktop_runtime_NativeProcessLauncher_nativeStop(JNIEnv *env, jclass clazz, jint pid) {
    (void) env; (void) clazz;
    if (kill((pid_t) pid, SIGTERM) == 0) return 0;
    return -errno;
}
