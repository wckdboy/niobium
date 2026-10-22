#define _GNU_SOURCE
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

/* This process owns and reaps every guest descendant. Signals never target a
 * port, process name or a PID that has already been reaped. */
static volatile sig_atomic_t stopping;
static void request_stop(int signal_number) { (void)signal_number; stopping = 1; }
static long long millis(void) {
    struct timespec now; clock_gettime(CLOCK_MONOTONIC, &now);
    return (long long)now.tv_sec * 1000 + now.tv_nsec / 1000000;
}
static void signal_children(int number) {
    char path[128];
    snprintf(path, sizeof(path), "/proc/self/task/%d/children", getpid());
    FILE *children = fopen(path, "re");
    if (!children) {
        /* Android kernels may omit CONFIG_CHECKPOINT_RESTORE's children file.
         * A live direct child's PPid cannot change away from this subreaper;
         * no other thread or handler reaps it before kill(). */
        DIR *processes = opendir("/proc");
        if (!processes) return;
        struct dirent *entry;
        while ((entry = readdir(processes))) {
            char *end;
            long candidate = strtol(entry->d_name, &end, 10);
            if (*end || candidate <= 0) continue;
            snprintf(path, sizeof(path), "/proc/%ld/stat", candidate);
            FILE *status = fopen(path, "re");
            if (!status) continue;
            char row[2048];
            if (fgets(row, sizeof(row), status)) {
                char *name_end = strrchr(row, ')');
                char state; int parent;
                if (name_end && sscanf(name_end + 1, " %c %d", &state, &parent) == 2 && parent == getpid())
                    kill((pid_t)candidate, number);
            }
            fclose(status);
        }
        closedir(processes);
        return;
    }
    int pid;
    /* No handler or other thread reaps children. A child that exits during
     * this loop remains a zombie and its PID cannot be reused before waitpid. */
    while (fscanf(children, "%d", &pid) == 1) if (pid > 0) kill(pid, number);
    fclose(children);
}
int main(int argc, char **argv) {
    if (argc < 2) { fputs("niobium: missing guest launcher\n", stderr); return 125; }
    if (prctl(PR_SET_CHILD_SUBREAPER, 1) == -1) {
        fputs("niobium: cannot supervise guest descendants\n", stderr); return 125;
    }
    struct sigaction action = {0};
    action.sa_handler = request_stop;
    sigemptyset(&action.sa_mask);
    sigaction(SIGTERM, &action, NULL); sigaction(SIGINT, &action, NULL);
    prctl(PR_SET_PDEATHSIG, SIGTERM);
    if (getppid() == 1) return 125;
    pid_t child = fork();
    if (child == -1) return 125;
    if (child == 0) {
        signal(SIGTERM, SIG_DFL); signal(SIGINT, SIG_DFL);
        setsid();
        int input = open("/dev/null", O_RDONLY);
        if (input >= 0) { dup2(input, STDIN_FILENO); if (input > 2) close(input); }
        execv(argv[1], &argv[1]);
        fputs("niobium: cannot execute guest launcher\n", stderr); _exit(126);
    }
    int root_reaped = 0, exit_code = 125;
    long long stop_at = 0;
    for (;;) {
        if (stopping && stop_at == 0) stop_at = millis();
        if (stop_at) {
            int number = millis() - stop_at < 1500 ? SIGTERM : SIGKILL;
            if (!root_reaped) kill(child, number);
            signal_children(number);
        }
        int status, reaped;
        while ((reaped = waitpid(-1, &status, WNOHANG)) > 0) {
            if (reaped == child) {
                root_reaped = 1;
                exit_code = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
                stopping = 1;
            }
        }
        if (root_reaped && reaped == -1 && errno == ECHILD) return exit_code;
        struct pollfd input = { .fd = STDIN_FILENO, .events = POLLIN | POLLHUP };
        if (poll(&input, 1, 50) > 0 && input.revents) {
            char message[64];
            if ((input.revents & (POLLHUP | POLLERR)) || read(STDIN_FILENO, message, sizeof(message)) <= 0)
                stopping = 1;
        }
    }
}
