#include <jni.h>
#include <node.h>
#include <atomic>
#include <cstdlib>
#include <string>
#include <vector>
#include <sys/stat.h>
#include <unistd.h>

namespace {
std::atomic<bool> g_node_started{false};

std::string from_java(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void make_directory(const std::string& path) {
    mkdir(path.c_str(), 0700);
}
}

extern "C" JNIEXPORT jint JNICALL
Java_com_assayflux_pockethost_runtime_NodeBridge_start(
    JNIEnv* env, jclass, jstring project_root, jstring entry_file, jint port) {
    bool expected = false;
    if (!g_node_started.compare_exchange_strong(expected, true)) return -2;

    const std::string root = from_java(env, project_root);
    const std::string entry = from_java(env, entry_file);
    if (root.empty() || entry.empty() || port < 1024 || port > 65535) {
        g_node_started.store(false);
        return -3;
    }
    if (chdir(root.c_str()) != 0) {
        g_node_started.store(false);
        return -4;
    }

    const std::string home = root + "/.pockethost/home";
    const std::string temp = root + "/.pockethost/tmp";
    const std::string cache = root + "/.pockethost/compile-cache";
    make_directory((root + "/.pockethost").c_str());
    make_directory(home);
    make_directory(temp);
    make_directory(cache);
    setenv("HOME", home.c_str(), 1);
    setenv("TMPDIR", temp.c_str(), 1);
    setenv("NODE_COMPILE_CACHE", cache.c_str(), 1);
    setenv("NODE_ENV", "production", 1);
    setenv("PORT", std::to_string(port).c_str(), 1);
    setenv("POCKETHOST_METRICS_FILE", (root + "/.pockethost/metrics.json").c_str(), 1);

    std::vector<std::string> arguments{
        "node",
        "--max-old-space-size=384",
        entry
    };
    std::vector<char*> argv;
    argv.reserve(arguments.size() + 1);
    for (auto& argument : arguments) argv.push_back(argument.data());
    argv.push_back(nullptr);

    const int result = node::Start(static_cast<int>(arguments.size()), argv.data());
    g_node_started.store(false);
    return result;
}
