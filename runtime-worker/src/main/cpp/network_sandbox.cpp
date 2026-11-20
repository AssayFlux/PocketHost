#include <jni.h>
#include <errno.h>
#include <stddef.h>
#include <stdint.h>
#include <linux/sched.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <dirent.h>
#include <fcntl.h>
#include <stdlib.h>
#include <sys/prctl.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <linux/audit.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <linux/unistd.h>

#if defined(__aarch64__)
#define POCKETHOST_AUDIT_ARCH AUDIT_ARCH_AARCH64
#elif defined(__arm__)
#define POCKETHOST_AUDIT_ARCH AUDIT_ARCH_ARM
#elif defined(__x86_64__)
#define POCKETHOST_AUDIT_ARCH AUDIT_ARCH_X86_64
#else
#error Unsupported ABI for PocketHost network sandbox
#endif

#define DENY_SYSCALL(nr) \
    BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (nr), 0, 1), \
    BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | (EPERM & SECCOMP_RET_DATA))

#define DENY_SYSCALL_AS(nr, error_code) \
    BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (nr), 0, 1), \
    BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | ((error_code) & SECCOMP_RET_DATA))

#define ALLOW_ONLY_FD(nr) \
    BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (nr), 0, 4), \
    BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[0])), \
    BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (uint32_t) listener_fd, 0, 1), \
    BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW), \
    BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | (EPERM & SECCOMP_RET_DATA))

#define ALLOW_CLONE_THREADS_ONLY(nr) \
    BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, (nr), 0, 4), \
    BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, args[0])), \
    BPF_JUMP(BPF_JMP | BPF_JSET | BPF_K, CLONE_THREAD, 0, 1), \
    BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW), \
    BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ERRNO | (EPERM & SECCOMP_RET_DATA))


/**
 * Close every inherited socket except the broker-created visitor listener.
 * This must run before project code. Seccomp cannot distinguish read/write on
 * an arbitrary connected socket from ordinary file I/O, so descriptor hygiene
 * is a required part of the egress boundary.
 */
static int close_inherited_sockets_except(int listener_fd) {
    if (listener_fd < 0) return EINVAL;
    struct stat listener_stat {};
    if (fstat(listener_fd, &listener_stat) != 0) return errno;
    if (!S_ISSOCK(listener_stat.st_mode)) return ENOTSOCK;

    // The sole socket allowed to survive startup must be a broker-created,
    // already-listening AF_UNIX stream. Never accept a TCP/UDP socket here:
    // that would turn the visitor capability into a network egress primitive.
    int domain = -1;
    socklen_t option_length = sizeof(domain);
    if (getsockopt(listener_fd, SOL_SOCKET, SO_DOMAIN, &domain, &option_length) != 0) return errno;
    if (domain != AF_UNIX) return EAFNOSUPPORT;
    int socket_type = -1;
    option_length = sizeof(socket_type);
    if (getsockopt(listener_fd, SOL_SOCKET, SO_TYPE, &socket_type, &option_length) != 0) return errno;
    if (socket_type != SOCK_STREAM) return EPROTOTYPE;
    int accepting = 0;
    option_length = sizeof(accepting);
    if (getsockopt(listener_fd, SOL_SOCKET, SO_ACCEPTCONN, &accepting, &option_length) != 0) return errno;
    if (accepting != 1) return EINVAL;

    DIR* directory = opendir("/proc/self/fd");
    if (directory == nullptr) return errno == 0 ? EIO : errno;
    const int directory_fd = dirfd(directory);
    int failure = 0;
    while (true) {
        errno = 0;
        struct dirent* entry = readdir(directory);
        if (entry == nullptr) {
            if (errno != 0 && failure == 0) failure = errno;
            break;
        }
        char* end = nullptr;
        long parsed = strtol(entry->d_name, &end, 10);
        if (end == entry->d_name || *end != '\0' || parsed < 0 ||
            parsed == listener_fd || parsed == directory_fd) continue;
        const int fd = static_cast<int>(parsed);
        struct stat metadata {};
        if (fstat(fd, &metadata) != 0) {
            if (errno != EBADF && failure == 0) failure = errno;
            continue;
        }
        if (S_ISSOCK(metadata.st_mode) && close(fd) != 0 && failure == 0) {
            failure = errno;
        }
    }
    closedir(directory);
    return failure;
}

static int lower_resource_limit(int resource, rlim_t target) {
    struct rlimit current {};
    if (getrlimit(resource, &current) != 0) return errno;
    const rlim_t hard = current.rlim_max < target ? current.rlim_max : target;
    const rlim_t soft = current.rlim_cur < hard ? current.rlim_cur : hard;
    struct rlimit restricted { soft, hard };
    if (setrlimit(resource, &restricted) != 0) return errno;
    return 0;
}

static int apply_resource_limits() {
    int result = lower_resource_limit(RLIMIT_CORE, 0);
    if (result != 0) return result;
    result = lower_resource_limit(RLIMIT_NOFILE, 128);
    if (result != 0) return result;
#ifdef RLIMIT_NPROC
    result = lower_resource_limit(RLIMIT_NPROC, 32);
    if (result != 0) return result;
#endif
#ifdef RLIMIT_FSIZE
    result = lower_resource_limit(RLIMIT_FSIZE, 64 * 1024 * 1024);
    if (result != 0) return result;
#endif
    return 0;
}

static int install_policy(int listener_fd) {
    if (listener_fd < 0) return EINVAL;
    const int limits_result = apply_resource_limits();
    if (limits_result != 0) return limits_result;
    if (prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) != 0) return errno;

    struct sock_filter filter[] = {
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, arch)),
        BPF_JUMP(BPF_JMP | BPF_JEQ | BPF_K, POCKETHOST_AUDIT_ARCH, 1, 0),
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_KILL),
        BPF_STMT(BPF_LD | BPF_W | BPF_ABS, offsetof(struct seccomp_data, nr)),
#ifdef __NR_socket
        DENY_SYSCALL(__NR_socket),
#endif
#ifdef __NR_socketpair
        DENY_SYSCALL(__NR_socketpair),
#endif
#ifdef __NR_connect
        DENY_SYSCALL(__NR_connect),
#endif
#ifdef __NR_bind
        DENY_SYSCALL(__NR_bind),
#endif
// A pre-opened or inherited socket must not become an egress channel.
// Visitor traffic is consumed only from the brokered accepted stream using
// read/write; socket-specific send/receive and descriptor-passing APIs are denied.
#ifdef __NR_sendto
        DENY_SYSCALL(__NR_sendto),
#endif
#ifdef __NR_sendmsg
        DENY_SYSCALL(__NR_sendmsg),
#endif
#ifdef __NR_sendmmsg
        DENY_SYSCALL(__NR_sendmmsg),
#endif
#ifdef __NR_recvfrom
        DENY_SYSCALL(__NR_recvfrom),
#endif
#ifdef __NR_recvmsg
        DENY_SYSCALL(__NR_recvmsg),
#endif
#ifdef __NR_recvmmsg
        DENY_SYSCALL(__NR_recvmmsg),
#endif
#ifdef __NR_shutdown
        DENY_SYSCALL(__NR_shutdown),
#endif
#ifdef __NR_socketcall
        DENY_SYSCALL(__NR_socketcall),
#endif
#ifdef __NR_io_uring_setup
        DENY_SYSCALL(__NR_io_uring_setup),
#endif
#ifdef __NR_io_uring_enter
        DENY_SYSCALL(__NR_io_uring_enter),
#endif
#ifdef __NR_io_uring_register
        DENY_SYSCALL(__NR_io_uring_register),
#endif
#ifdef __NR_unshare
        DENY_SYSCALL(__NR_unshare),
#endif
#ifdef __NR_setns
        DENY_SYSCALL(__NR_setns),
#endif
#ifdef __NR_mount
        DENY_SYSCALL(__NR_mount),
#endif
#ifdef __NR_umount2
        DENY_SYSCALL(__NR_umount2),
#endif
#ifdef __NR_pivot_root
        DENY_SYSCALL(__NR_pivot_root),
#endif
#ifdef __NR_chroot
        DENY_SYSCALL(__NR_chroot),
#endif
#ifdef __NR_ptrace
        DENY_SYSCALL(__NR_ptrace),
#endif
#ifdef __NR_process_vm_readv
        DENY_SYSCALL(__NR_process_vm_readv),
#endif
#ifdef __NR_process_vm_writev
        DENY_SYSCALL(__NR_process_vm_writev),
#endif
#ifdef __NR_execve
        DENY_SYSCALL(__NR_execve),
#endif
#ifdef __NR_execveat
        DENY_SYSCALL(__NR_execveat),
#endif
#ifdef __NR_fork
        DENY_SYSCALL(__NR_fork),
#endif
#ifdef __NR_vfork
        DENY_SYSCALL(__NR_vfork),
#endif
#ifdef __NR_clone3
        DENY_SYSCALL_AS(__NR_clone3, ENOSYS),
#endif
#ifdef __NR_clone
        ALLOW_CLONE_THREADS_ONLY(__NR_clone),
#endif
#ifdef __NR_listen
        ALLOW_ONLY_FD(__NR_listen),
#endif
#ifdef __NR_accept
        ALLOW_ONLY_FD(__NR_accept),
#endif
#ifdef __NR_accept4
        ALLOW_ONLY_FD(__NR_accept4),
#endif
        BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW),
    };

    struct sock_fprog program = {
        .len = (unsigned short)(sizeof(filter) / sizeof(filter[0])),
        .filter = filter,
    };

#if defined(__NR_seccomp)
    if (syscall(__NR_seccomp, SECCOMP_SET_MODE_FILTER, SECCOMP_FILTER_FLAG_TSYNC, &program) == 0) {
        return 0;
    }
    return errno == 0 ? EIO : errno;
#else
    return ENOSYS;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_assayflux_pockethost_worker_NetworkSandbox_installNetworkPolicy(
    JNIEnv*, jclass, jint listener_fd) {
    return install_policy(listener_fd);
}


extern "C" JNIEXPORT jint JNICALL
Java_com_assayflux_pockethost_worker_NetworkSandbox_duplicateToStandardInput(
    JNIEnv*, jclass, jint socket_fd) {
    return dup2(socket_fd, STDIN_FILENO) < 0 ? (errno == 0 ? EIO : errno) : 0;
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_assayflux_pockethost_worker_NetworkSandbox_getResourceLimits(
    JNIEnv* env, jclass) {
    const int resources[] = { RLIMIT_NOFILE, RLIMIT_NPROC, RLIMIT_FSIZE, RLIMIT_CORE };
    jlong values[4] = {};
    for (int i = 0; i < 4; ++i) {
        struct rlimit limit {};
        if (getrlimit(resources[i], &limit) != 0) {
            values[i] = -static_cast<jlong>(errno == 0 ? EIO : errno);
        } else {
            values[i] = static_cast<jlong>(limit.rlim_cur);
        }
    }
    jlongArray result = env->NewLongArray(4);
    if (result != nullptr) env->SetLongArrayRegion(result, 0, 4, values);
    return result;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_assayflux_pockethost_worker_NetworkSandbox_getPeerUid(
    JNIEnv*, jclass, jint socket_fd) {
    struct ucred credentials {};
    socklen_t length = sizeof(credentials);
    if (getsockopt(socket_fd, SOL_SOCKET, SO_PEERCRED, &credentials, &length) != 0) {
        return -errno;
    }
    return static_cast<jint>(credentials.uid);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_assayflux_pockethost_worker_NetworkSandbox_closeInheritedSocketsExcept(
    JNIEnv*, jclass, jint listener_fd) {
    return close_inherited_sockets_except(listener_fd);
}

/**
 * Exercise the socket I/O syscalls which must remain denied even when a caller
 * already owns the broker listener FD. The filter must return EPERM before the
 * kernel validates the operation or touches the supplied message buffers.
 *
 * Bit assignments: sendto=0, sendmsg=1, sendmmsg=2, recvfrom=3,
 * recvmsg=4, recvmmsg=5, shutdown=6.
 */
static bool probe_seccomp_errno(long result) {
    return result < 0 && errno == EPERM;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_assayflux_pockethost_worker_NetworkSandbox_probeDeniedNetworkIo(
    JNIEnv*, jclass, jint listener_fd) {
    int denied_mask = 0;
    unsigned char byte = 0;
    alignas(8) unsigned char message[256] = {};
#ifdef __NR_sendto
    errno = 0;
    if (probe_seccomp_errno(syscall(__NR_sendto, listener_fd, &byte, sizeof(byte), 0, nullptr, 0))) denied_mask |= 1 << 0;
#endif
#ifdef __NR_sendmsg
    errno = 0;
    if (probe_seccomp_errno(syscall(__NR_sendmsg, listener_fd, message, 0))) denied_mask |= 1 << 1;
#endif
#ifdef __NR_sendmmsg
    errno = 0;
    if (probe_seccomp_errno(syscall(__NR_sendmmsg, listener_fd, message, 1, 0))) denied_mask |= 1 << 2;
#endif
#ifdef __NR_recvfrom
    errno = 0;
    if (probe_seccomp_errno(syscall(__NR_recvfrom, listener_fd, &byte, sizeof(byte), 0, nullptr, nullptr))) denied_mask |= 1 << 3;
#endif
#ifdef __NR_recvmsg
    errno = 0;
    if (probe_seccomp_errno(syscall(__NR_recvmsg, listener_fd, message, 0))) denied_mask |= 1 << 4;
#endif
#ifdef __NR_recvmmsg
    errno = 0;
    if (probe_seccomp_errno(syscall(__NR_recvmmsg, listener_fd, message, 1, 0, nullptr))) denied_mask |= 1 << 5;
#endif
#ifdef __NR_shutdown
    errno = 0;
    if (probe_seccomp_errno(syscall(__NR_shutdown, listener_fd, SHUT_RDWR))) denied_mask |= 1 << 6;
#endif
    return denied_mask;
}


extern "C" JNIEXPORT jint JNICALL
Java_com_assayflux_pockethost_worker_NetworkSandbox_isDescriptorClosed(
    JNIEnv*, jclass, jint fd) {
    errno = 0;
    if (fcntl(fd, F_GETFD) >= 0) return 0;
    if (errno == EBADF) return 1;
    return -(errno == 0 ? EIO : errno);
}


/**
 * Attempt connect() with a valid AF_UNIX sockaddr while seccomp is active.
 * Using the broker listener FD avoids creating a new socket (also denied).
 * The kernel must report EPERM from seccomp before socket-state validation.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_assayflux_pockethost_worker_NetworkSandbox_probeConnectErrno(
    JNIEnv*, jclass, jint listener_fd) {
    struct sockaddr_un address {};
    address.sun_family = AF_UNIX;
    const char name[] = "pockethost-seccomp-denied";
    address.sun_path[0] = '\0';
    memcpy(address.sun_path + 1, name, sizeof(name) - 1);
    const socklen_t address_length = static_cast<socklen_t>(
        offsetof(struct sockaddr_un, sun_path) + 1 + sizeof(name) - 1);
    errno = 0;
    if (connect(listener_fd, reinterpret_cast<struct sockaddr*>(&address), address_length) == 0) {
        return 0;
    }
    return errno == 0 ? EIO : errno;
}


/**
 * Attempt bind() with a valid abstract AF_UNIX address while seccomp is active.
 * Use the syscall directly so API-level differences in android.system.Os do not
 * weaken or disable the denial check on Android 8.x (API 26–28).
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_assayflux_pockethost_worker_NetworkSandbox_probeBindErrno(
    JNIEnv*, jclass, jint listener_fd) {
#ifdef __NR_bind
    struct sockaddr_un address {};
    address.sun_family = AF_UNIX;
    const char name[] = "pockethost-seccomp-bind-denied";
    address.sun_path[0] = '\0';
    memcpy(address.sun_path + 1, name, sizeof(name) - 1);
    const socklen_t address_length = static_cast<socklen_t>(
        offsetof(struct sockaddr_un, sun_path) + 1 + sizeof(name) - 1);
    errno = 0;
    if (syscall(__NR_bind, listener_fd, &address, address_length) == 0) return 0;
    return errno == 0 ? EIO : errno;
#else
    return ENOSYS;
#endif
}
