#include <jni.h>
#include <errno.h>
#include <stddef.h>
#include <stdint.h>
#include <linux/sched.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/prctl.h>
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

static int install_policy(int listener_fd) {
    if (listener_fd < 0) return EINVAL;
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
Java_com_assayflux_pockethost_worker_NetworkSandbox_getPeerUid(
    JNIEnv*, jclass, jint socket_fd) {
    struct ucred credentials {};
    socklen_t length = sizeof(credentials);
    if (getsockopt(socket_fd, SOL_SOCKET, SO_PEERCRED, &credentials, &length) != 0) {
        return -errno;
    }
    return static_cast<jint>(credentials.uid);
}
