// JNI bridge over Genie's C API.
//
// genie-t2t-run is strictly one-shot: config -> GenieDialog_create -> one query -> exit.
// Creating the dialog costs 4.3-32.0 s because it mmaps ~4 GB of context binaries and LUTs,
// against ~4.5 s for the generation itself. Shelling out per capture would pay that every
// time. The C API has the resident model we want instead: create the dialog once, query it
// repeatedly on the same handle.
//
// Symbols are resolved with dlopen/dlsym rather than linked. Two reasons, both practical.
// ADSP_LIBRARY_PATH must be set before libGenie.so initialises its backend, and doing the
// setenv immediately before the dlopen in the same function makes that ordering impossible
// to get wrong from Kotlin. And the devcloud flavour ships none of these .so files, so a
// link-time dependency would break a build that never intends to run Genie at all.

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <cstdlib>
#include <mutex>
#include <string>

#include "include/Genie/GenieCommon.h"
#include "include/Genie/GenieDialog.h"

#define TAG "SmritiGenie"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

using ConfigFromJsonFn = Genie_Status_t (*)(const char*, GenieDialogConfig_Handle_t*);
using ConfigFreeFn     = Genie_Status_t (*)(GenieDialogConfig_Handle_t);
using DialogCreateFn   = Genie_Status_t (*)(GenieDialogConfig_Handle_t, GenieDialog_Handle_t*);
using DialogQueryFn    = Genie_Status_t (*)(GenieDialog_Handle_t, const char*,
                                            GenieDialog_SentenceCode_t,
                                            GenieDialog_QueryCallback_t, const void*);
using DialogResetFn    = Genie_Status_t (*)(GenieDialog_Handle_t);
using DialogFreeFn     = Genie_Status_t (*)(GenieDialog_Handle_t);

struct Genie {
    void*             handle = nullptr;
    ConfigFromJsonFn  configFromJson = nullptr;
    ConfigFreeFn      configFree = nullptr;
    DialogCreateFn    dialogCreate = nullptr;
    DialogQueryFn     dialogQuery = nullptr;
    DialogResetFn     dialogReset = nullptr;
    DialogFreeFn      dialogFree = nullptr;
};

Genie       g_genie;
std::mutex  g_lock;        // Genie dialogs are not documented as thread-safe; serialise queries.
std::string g_lastError;

void setError(const std::string& what) {
    g_lastError = what;
    LOGE("%s", what.c_str());
}

template <typename Fn>
bool bind(void* lib, const char* name, Fn& out) {
    out = reinterpret_cast<Fn>(dlsym(lib, name));
    if (out == nullptr) {
        setError(std::string("libGenie.so is missing symbol ") + name);
        return false;
    }
    return true;
}

// Loads libGenie.so once, after the DSP search path is in place.
bool ensureLoaded(const char* adspPath) {
    if (g_genie.handle != nullptr) return true;

    // fastRPC reads ADSP_LIBRARY_PATH when the backend spins up the DSP session. Setting it
    // after libGenie.so is loaded is too late, so it happens here, one line before the dlopen.
    if (setenv("ADSP_LIBRARY_PATH", adspPath, 1) != 0) {
        setError("could not set ADSP_LIBRARY_PATH");
        return false;
    }
    LOGI("ADSP_LIBRARY_PATH=%s", adspPath);

    void* lib = dlopen("libGenie.so", RTLD_NOW | RTLD_GLOBAL);
    if (lib == nullptr) {
        setError(std::string("dlopen(libGenie.so) failed: ") + dlerror());
        return false;
    }

    if (!bind(lib, "GenieDialogConfig_createFromJson", g_genie.configFromJson) ||
        !bind(lib, "GenieDialogConfig_free",           g_genie.configFree)     ||
        !bind(lib, "GenieDialog_create",               g_genie.dialogCreate)   ||
        !bind(lib, "GenieDialog_query",                g_genie.dialogQuery)    ||
        !bind(lib, "GenieDialog_reset",                g_genie.dialogReset)    ||
        !bind(lib, "GenieDialog_free",                 g_genie.dialogFree)) {
        dlclose(lib);
        return false;
    }

    g_genie.handle = lib;
    LOGI("libGenie.so loaded");
    return true;
}

// Genie streams the answer in fragments; userData accumulates them.
void onToken(const char* response, const GenieDialog_SentenceCode_t code, const void* userData) {
    if (response == nullptr || userData == nullptr) return;
    if (code == GENIE_DIALOG_SENTENCE_ABORT) return;
    const_cast<std::string*>(static_cast<const std::string*>(userData))->append(response);
}

std::string toStd(JNIEnv* env, jstring s) {
    if (s == nullptr) return {};
    const char* raw = env->GetStringUTFChars(s, nullptr);
    std::string out(raw ? raw : "");
    if (raw) env->ReleaseStringUTFChars(s, raw);
    return out;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_smriti_app_ai_GenieBridge_nativeCreate(JNIEnv* env, jobject,
                                                jstring configJson, jstring adspPath) {
    std::lock_guard<std::mutex> guard(g_lock);
    g_lastError.clear();

    const std::string json = toStd(env, configJson);
    const std::string adsp = toStd(env, adspPath);
    if (!ensureLoaded(adsp.c_str())) return 0;

    GenieDialogConfig_Handle_t config = nullptr;
    Genie_Status_t status = g_genie.configFromJson(json.c_str(), &config);
    if (status != GENIE_STATUS_SUCCESS || config == nullptr) {
        setError("GenieDialogConfig_createFromJson failed with status " + std::to_string(status));
        return 0;
    }

    GenieDialog_Handle_t dialog = nullptr;
    status = g_genie.dialogCreate(config, &dialog);
    g_genie.configFree(config);   // the dialog owns what it needs; the config is done either way

    if (status != GENIE_STATUS_SUCCESS || dialog == nullptr) {
        setError("GenieDialog_create failed with status " + std::to_string(status));
        return 0;
    }

    LOGI("dialog created");
    return reinterpret_cast<jlong>(dialog);
}

JNIEXPORT jstring JNICALL
Java_com_smriti_app_ai_GenieBridge_nativeQuery(JNIEnv* env, jobject, jlong handle, jstring prompt) {
    std::lock_guard<std::mutex> guard(g_lock);
    if (handle == 0) { setError("query on a null dialog handle"); return nullptr; }

    const std::string text = toStd(env, prompt);
    std::string answer;

    const Genie_Status_t status = g_genie.dialogQuery(
        reinterpret_cast<GenieDialog_Handle_t>(handle),
        text.c_str(),
        GENIE_DIALOG_SENTENCE_COMPLETE,
        onToken,
        &answer);

    // A context overrun still leaves a usable partial answer, so it is a warning, not a failure.
    if (status != GENIE_STATUS_SUCCESS && status != GENIE_STATUS_WARNING_CONTEXT_EXCEEDED) {
        setError("GenieDialog_query failed with status " + std::to_string(status));
        return nullptr;
    }
    if (status == GENIE_STATUS_WARNING_CONTEXT_EXCEEDED) {
        LOGW("context limit exceeded; returning the partial response");
    }
    return env->NewStringUTF(answer.c_str());
}

JNIEXPORT void JNICALL
Java_com_smriti_app_ai_GenieBridge_nativeReset(JNIEnv*, jobject, jlong handle) {
    std::lock_guard<std::mutex> guard(g_lock);
    if (handle != 0 && g_genie.dialogReset != nullptr) {
        g_genie.dialogReset(reinterpret_cast<GenieDialog_Handle_t>(handle));
    }
}

JNIEXPORT void JNICALL
Java_com_smriti_app_ai_GenieBridge_nativeDestroy(JNIEnv*, jobject, jlong handle) {
    std::lock_guard<std::mutex> guard(g_lock);
    if (handle != 0 && g_genie.dialogFree != nullptr) {
        g_genie.dialogFree(reinterpret_cast<GenieDialog_Handle_t>(handle));
        LOGI("dialog freed");
    }
}

JNIEXPORT jstring JNICALL
Java_com_smriti_app_ai_GenieBridge_nativeLastError(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> guard(g_lock);
    return env->NewStringUTF(g_lastError.c_str());
}

}  // extern "C"
