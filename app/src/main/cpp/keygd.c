/*
 * keygd: takes over one input device and reports its key events on stdout.
 *
 * It is started as the shell user through Shizuku, because /dev/input is not
 * readable by a normal app. Grabbing (EVIOCGRAB) means the system no longer
 * sees the keys, so every key that is not remapped has to be sent back by the
 * app with `input keyevent`.
 *
 * The grab is released when the file descriptor closes, so if this process dies
 * or is killed the keys go back to the system by themselves.
 */
#include <linux/input.h>
#include <poll.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/ioctl.h>
#include <unistd.h>

static int device_fd = -1;

static void release_and_exit(int code) {
    if (device_fd >= 0) {
        ioctl(device_fd, EVIOCGRAB, 0);
        close(device_fd);
        device_fd = -1;
    }
    _exit(code);
}

static void on_signal(int sig) {
    (void) sig;
    release_and_exit(0);
}

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: keygd <device>\n");
        return 2;
    }

    device_fd = open(argv[1], O_RDONLY | O_NONBLOCK);
    if (device_fd < 0) {
        perror("open");
        return 3;
    }

    if (ioctl(device_fd, EVIOCGRAB, 1) < 0) {
        perror("grab");
        release_and_exit(4);
    }

    signal(SIGTERM, on_signal);
    signal(SIGINT, on_signal);
    signal(SIGHUP, on_signal);

    printf("READY\n");
    fflush(stdout);

    struct pollfd descriptor;
    descriptor.fd = device_fd;
    descriptor.events = POLLIN;

    for (;;) {
        // If the app that started us is gone, let go of the keys.
        if (getppid() == 1) {
            release_and_exit(0);
        }

        int ready = poll(&descriptor, 1, 500);
        if (ready < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (ready == 0) continue;

        struct input_event event;
        ssize_t count = read(device_fd, &event, sizeof(event));
        if (count != (ssize_t) sizeof(event)) continue;

        if (event.type == EV_KEY) {
            long long stamp = (long long) event.time.tv_sec * 1000LL +
                              (long long) event.time.tv_usec / 1000LL;
            printf("K %u %d %lld\n", event.code, event.value, stamp);
            fflush(stdout);
        }
    }

    release_and_exit(0);
    return 0;
}
