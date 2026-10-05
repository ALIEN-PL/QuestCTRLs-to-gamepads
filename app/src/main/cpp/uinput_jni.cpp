// Virtual Gamepad via Linux uinput for Meta Quest & Steam Controller
// Supports Sony DualSense (PS5), DualShock 4 (PS4), Xbox 360, Xbox One, Desktop Mouse/Kbd.
// Automatically reads hardware Quest 2, 3, Pro controllers directly via evdev (/dev/input/event*).

#include <jni.h>
#include <android/log.h>
#include <linux/uinput.h>
#include <linux/input.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/ioctl.h>
#include <string.h>
#include <errno.h>
#include <time.h>
#include <poll.h>
#include <dirent.h>
#include <pthread.h>
#include <atomic>
#include <algorithm>
#include <cmath>

#define LOG_TAG "quest_uinput_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct gamepad_profile {
    int id;
    uint16_t vid;
    uint16_t pid;
    const char* name;
    bool mouse_mode;
};

static const gamepad_profile PROFILES[] = {
    { 0, 0x045E, 0x028E, "Microsoft X-Box 360 pad",                                           false }, // XBOX_360
    { 1, 0x045E, 0x02EA, "Microsoft Xbox One Controller",                                     false }, // XBOX_ONE
    { 2, 0x054C, 0x05C4, "Sony Interactive Entertainment Wireless Controller",                false }, // DUALSHOCK_4
    { 3, 0x054C, 0x0CE6, "Sony Interactive Entertainment DualSense Wireless Controller",      false }, // DUALSENSE
    { 4, 0x046D, 0xC077, "Desktop Mode (Mouse + Keyboard)",                                   true  }, // MOUSE
};

static const int MOUSE_KEYS[] = {
    KEY_UP, KEY_DOWN, KEY_LEFT, KEY_RIGHT,        // 0..3
    KEY_ENTER, KEY_BACK, KEY_TAB, KEY_SPACE,      // 4..7
    KEY_HOME, KEY_ESC,                            // 8..9
    KEY_VOLUMEUP, KEY_VOLUMEDOWN,                 // 10..11
    KEY_PLAYPAUSE, KEY_MENU,                      // 12..13
    KEY_BACKSPACE,                                // 14
    KEY_SELECT,                                   // 15 -> AKEYCODE_DPAD_CENTER
};
static constexpr int MOUSE_KEY_COUNT = sizeof(MOUSE_KEYS) / sizeof(MOUSE_KEYS[0]);

static const gamepad_profile* find_profile(int id) {
    for (const auto& p : PROFILES) if (p.id == id) return &p;
    return &PROFILES[3];  // default DualSense
}

#define STICK_MIN   -32768
#define STICK_MAX    32767
#define TRIG_MIN     0
#define TRIG_MAX     255
#define HAT_MIN     -1
#define HAT_MAX      1

static int g_fd_gamepad = -1;
static int g_fd_mouse   = -1;
static int g_fd_kbd     = -1;
static int g_fd_sensors = -1;

static int g_last_mouse_buttons = 0;
static int g_last_kbd_keys      = 0;

static int set_bit_or_log(int fd, unsigned long req, int bit, const char* what) {
    if (ioctl(fd, req, bit) < 0) {
        LOGE("ioctl %s bit=%d failed: %s", what, bit, strerror(errno));
        return -1;
    }
    return 0;
}

static int setup_abs(int fd, int code, int min, int max, int fuzz, int flat) {
    if (ioctl(fd, UI_SET_ABSBIT, code) < 0) return -1;
    struct uinput_abs_setup s;
    memset(&s, 0, sizeof(s));
    s.code = code;
    s.absinfo.minimum = min;
    s.absinfo.maximum = max;
    s.absinfo.fuzz    = fuzz;
    s.absinfo.flat    = flat;
    if (ioctl(fd, UI_ABS_SETUP, &s) < 0) {
        LOGE("UI_ABS_SETUP code=%d failed: %s", code, strerror(errno));
        return -1;
    }
    return 0;
}

static int write_event(int fd, uint16_t type, uint16_t code, int32_t value) {
    struct input_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.type  = type;
    ev.code  = code;
    ev.value = value;
    if (write(fd, &ev, sizeof(ev)) != (ssize_t)sizeof(ev)) {
        LOGE("write event t=%d c=%d v=%d failed: %s", type, code, value, strerror(errno));
        return -1;
    }
    return 0;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_questgamepad_android_uinput_UInputNative_canOpen(JNIEnv*, jclass) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) {
        LOGI("canOpen: /dev/uinput open denied: %s", strerror(errno));
        return JNI_FALSE;
    }
    close(fd);
    return JNI_TRUE;
}

struct ff_slot {
    int id;
    uint16_t strong;
    uint16_t weak;
};
static constexpr int MAX_FF_EFFECTS = 4;
static ff_slot g_ff_effects[MAX_FF_EFFECTS] = {};
static int32_t g_pending_strong = -1;
static int32_t g_pending_weak   = -1;

static int finalize_device(int fd, uint16_t vid, uint16_t pid, const char* name, uint32_t ff_effects_max) {
    if (vid == 0x054C) ioctl(fd, UI_SET_PHYS, "questgamepad/sony");
    struct uinput_setup us;
    memset(&us, 0, sizeof(us));
    us.id.bustype = BUS_USB;
    us.id.vendor  = vid;
    us.id.product = pid;
    us.id.version = 0x0114;
    us.ff_effects_max = ff_effects_max;
    strncpy(us.name, name, UINPUT_MAX_NAME_SIZE - 1);
    if (ioctl(fd, UI_DEV_SETUP, &us) < 0) {
        LOGE("UI_DEV_SETUP failed: %s", strerror(errno));
        return -1;
    }
    if (ioctl(fd, UI_DEV_CREATE) < 0) {
        LOGE("UI_DEV_CREATE failed: %s", strerror(errno));
        return -1;
    }
    return 0;
}

static int create_mouse_fd(uint16_t vid, uint16_t pid) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { LOGE("open /dev/uinput (mouse) failed: %s", strerror(errno)); return -1; }
    if (set_bit_or_log(fd, UI_SET_EVBIT,  EV_KEY,    "mouse EV_KEY")    < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_EVBIT,  EV_REL,    "mouse EV_REL")    < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_EVBIT,  EV_SYN,    "mouse EV_SYN")    < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_RELBIT, REL_X,     "REL_X")           < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_RELBIT, REL_Y,     "REL_Y")           < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_RELBIT, REL_WHEEL, "REL_WHEEL")       < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_KEYBIT, BTN_LEFT,   "BTN_LEFT")       < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_KEYBIT, BTN_RIGHT,  "BTN_RIGHT")      < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_KEYBIT, BTN_MIDDLE, "BTN_MIDDLE")     < 0) goto fail;
    if (finalize_device(fd, vid, pid, "Quest/Steam Virtual Mouse", 0) < 0) goto fail;
    return fd;
fail:
    close(fd);
    return -1;
}

static int create_keyboard_fd(uint16_t vid, uint16_t pid, bool full_alpha) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) { LOGE("open /dev/uinput (kbd) failed: %s", strerror(errno)); return -1; }
    if (set_bit_or_log(fd, UI_SET_EVBIT, EV_KEY, "kbd EV_KEY") < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_EVBIT, EV_SYN, "kbd EV_SYN") < 0) goto fail;
    for (int i = 0; i < MOUSE_KEY_COUNT; i++) {
        if (set_bit_or_log(fd, UI_SET_KEYBIT, MOUSE_KEYS[i], "MOUSE_KEY") < 0) goto fail;
    }
    if (full_alpha) {
        const int alpha_keys[] = {
            KEY_A, KEY_B, KEY_C, KEY_D, KEY_E, KEY_F, KEY_G, KEY_H, KEY_I, KEY_J,
            KEY_K, KEY_L, KEY_M, KEY_N, KEY_O, KEY_P, KEY_Q, KEY_R, KEY_S, KEY_T,
            KEY_U, KEY_V, KEY_W, KEY_X, KEY_Y, KEY_Z,
            KEY_0, KEY_1, KEY_2, KEY_3, KEY_4, KEY_5, KEY_6, KEY_7, KEY_8, KEY_9,
            KEY_LEFTSHIFT, KEY_RIGHTSHIFT, KEY_LEFTCTRL, KEY_LEFTALT, KEY_CAPSLOCK,
            KEY_COMMA, KEY_DOT, KEY_SLASH, KEY_SEMICOLON, KEY_APOSTROPHE,
            KEY_MINUS, KEY_EQUAL, KEY_SELECT
        };
        for (int k : alpha_keys) {
            if (set_bit_or_log(fd, UI_SET_KEYBIT, k, "alpha KEY") < 0) goto fail;
        }
    }
    if (finalize_device(fd, vid, (uint16_t)(pid + 1), "Quest/Steam Virtual Keyboard", 0) < 0) goto fail;
    return fd;
fail:
    close(fd);
    return -1;
}

static int create_sensors_fd(const gamepad_profile& profile) {
    int fd = open("/dev/uinput", O_WRONLY | O_NONBLOCK);
    if (fd < 0) return -1;
    if (set_bit_or_log(fd, UI_SET_EVBIT, EV_ABS, "sensor EV_ABS") < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_EVBIT, EV_SYN, "sensor EV_SYN") < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_EVBIT, EV_MSC, "sensor EV_MSC") < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_MSCBIT, MSC_TIMESTAMP, "sensor timestamp") < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_PROPBIT, INPUT_PROP_ACCELEROMETER, "sensor property") < 0) goto fail;
    for (int axis : {ABS_X, ABS_Y, ABS_Z, ABS_RX, ABS_RY, ABS_RZ}) {
        int resolution = axis <= ABS_Z ? 8192 : 1024;
        int range = axis <= ABS_Z ? 4 * resolution : 2048 * resolution;
        if (setup_abs(fd, axis, -range, range, 0, 0) < 0) goto fail;
        struct uinput_abs_setup setup = {};
        setup.code = axis;
        setup.absinfo.minimum = -range;
        setup.absinfo.maximum = range;
        setup.absinfo.resolution = resolution;
        if (ioctl(fd, UI_ABS_SETUP, &setup) < 0) goto fail;
    }
    {
        char name[UINPUT_MAX_NAME_SIZE];
        snprintf(name, sizeof(name), "%s Motion Sensors", profile.name);
        if (finalize_device(fd, profile.vid, profile.pid, name, 0) < 0) goto fail;
    }
    return fd;
fail:
    close(fd);
    return -1;
}

static void write_motion_frame(int gyroX, int gyroY, int gyroZ, int accelX, int accelY, int accelZ) {
    if (g_fd_sensors < 0) return;
    const int gyro[] = {gyroX, gyroY, gyroZ};
    const int accel[] = {accelX, accelY, accelZ};
    for (int axis = 0; axis < 3; axis++) {
        int angular = std::clamp(static_cast<int>(std::lround(gyro[axis] * (1024.0 * 180.0 / (1000.0 * 3.141592653589793)))), -2097152, 2097152);
        int acceleration = std::clamp(static_cast<int>(std::lround(accel[axis] * (8192.0 / 9806.65))), -32768, 32768);
        write_event(g_fd_sensors, EV_ABS, ABS_RX + axis, angular);
        write_event(g_fd_sensors, EV_ABS, ABS_X + axis, acceleration);
    }
    struct timespec now;
    clock_gettime(CLOCK_MONOTONIC, &now);
    uint32_t timestamp = static_cast<uint32_t>(static_cast<uint64_t>(now.tv_sec) * 1000000 + now.tv_nsec / 1000);
    write_event(g_fd_sensors, EV_MSC, MSC_TIMESTAMP, static_cast<int32_t>(timestamp));
    write_event(g_fd_sensors, EV_SYN, SYN_REPORT, 0);
}

// ─────────────────────────────────────────────────────────────────────────────
// DIRECT HARDWARE QUEST EVDEV READER PIPELINE
// Runs directly inside the Shizuku shell process.
// Captures /dev/input/event* devices for Meta Quest (vendor 0x2833) and
// immediately writes real DualSense / Xbox events to /dev/uinput!
// ─────────────────────────────────────────────────────────────────────────────

struct QuestHwState {
    int32_t lx = 0;
    int32_t ly = 0;
    int32_t rx = 0;
    int32_t ry = 0;
    int32_t lt = 0;
    int32_t rt = 0;
    int32_t lg = 0; // Left Grip
    int32_t rg = 0; // Right Grip
    int32_t dpadX = 0;
    int32_t dpadY = 0;
    uint32_t buttons = 0;
    bool left_thumbrest = false;
};

static QuestHwState g_hw_state;
static int g_current_profile_id = 3; // Default DualSense
static std::atomic<bool> g_forwarding_enabled{true};
static std::atomic<bool> g_system_paused{true};
static bool g_quest_input_enabled = false;
static pthread_mutex_t g_state_mutex = PTHREAD_MUTEX_INITIALIZER;
static std::atomic<bool> g_reader_running{false};
static bool g_both_clicked_last = false;
static pthread_t g_reader_thread;

static uint32_t g_last_buttons = 0xFFFFFFFF;
static int32_t g_last_lx = 999999;
static int32_t g_last_ly = 999999;
static int32_t g_last_rx = 999999;
static int32_t g_last_ry = 999999;
static int32_t g_last_lt = 999999;
static int32_t g_last_rt = 999999;
static int32_t g_last_dpadX = 999999;
static int32_t g_last_dpadY = 999999;

static void forward_state_to_uinput(const QuestHwState& s, bool force = false) {
    if (g_fd_gamepad < 0) return;
    if (!force && (!g_forwarding_enabled.load() || g_system_paused.load())) return;

    bool isPlayStation = g_current_profile_id == 3;
    // On PlayStation (DualSense / DS4 in Android keylayout):
    // 0x134 is BUTTON_X (Square), 0x133 is BUTTON_Y (Triangle)
    // On Xbox (XInput in Android):
    // 0x133 is BUTTON_X (X), 0x134 is BUTTON_Y (Y)
    const int square_key   = isPlayStation ? 0x134 : 0x133;
    const int triangle_key = isPlayStation ? 0x133 : 0x134;

    const int modern_keys[] = {
        0x130, 0x131, square_key, triangle_key,
        BTN_TL, BTN_TR,
        BTN_SELECT, BTN_START, BTN_MODE,
        BTN_THUMBL, BTN_THUMBR,
        BTN_TL2, BTN_TR2
    };
    const int ds4_keys[] = {
        0x131, 0x132, 0x130, 0x133, 0x134, 0x135,
        0x138, 0x139, 0x13c, 0x13a, 0x13b, 0x136, 0x137, 0x13d
    };
    const int* bit_to_key = g_current_profile_id == 2 ? ds4_keys : modern_keys;
    const int n = g_current_profile_id == 2 ? 14 : 13;
    const int right_x_axis = g_current_profile_id == 2 ? ABS_Z : ABS_RX;
    const int right_y_axis = g_current_profile_id == 2 ? ABS_RZ : ABS_RY;
    const int left_trigger_axis = g_current_profile_id == 2 ? ABS_RX : ABS_Z;
    const int right_trigger_axis = g_current_profile_id == 2 ? ABS_RY : ABS_RZ;

    bool any_written = false;
    uint32_t changed_buttons = s.buttons ^ g_last_buttons;
    if (changed_buttons != 0) {
        for (int i = 0; i < n; i++) {
            if ((changed_buttons >> i) & 1) {
                int pressed = (s.buttons >> i) & 1;
                write_event(g_fd_gamepad, EV_KEY, bit_to_key[i], pressed);
                any_written = true;
            }
        }
        g_last_buttons = s.buttons;
    }

    int finalLx = s.lx;
    int finalLy = s.ly;
    int finalDpadX = s.dpadX;
    int finalDpadY = s.dpadY;

    // D-Pad Modifier mode: if Left Thumbrest touched
    bool isModifierActive = s.left_thumbrest;
    if (isModifierActive) {
        if (s.ly < -12000) finalDpadY = -1;
        else if (s.ly > 12000) finalDpadY = 1;
        if (s.lx < -12000) finalDpadX = -1;
        else if (s.lx > 12000) finalDpadX = 1;

        // Zero out stick so character does not move
        finalLx = 0;
        finalLy = 0;
    }

    if (finalLx != g_last_lx) { write_event(g_fd_gamepad, EV_ABS, ABS_X, finalLx); g_last_lx = finalLx; any_written = true; }
    if (finalLy != g_last_ly) { write_event(g_fd_gamepad, EV_ABS, ABS_Y, finalLy); g_last_ly = finalLy; any_written = true; }
    if (s.rx != g_last_rx) { write_event(g_fd_gamepad, EV_ABS, right_x_axis, s.rx); g_last_rx = s.rx; any_written = true; }
    if (s.ry != g_last_ry) { write_event(g_fd_gamepad, EV_ABS, right_y_axis, s.ry); g_last_ry = s.ry; any_written = true; }
    if (s.lt != g_last_lt) { write_event(g_fd_gamepad, EV_ABS, left_trigger_axis, s.lt); g_last_lt = s.lt; any_written = true; }
    if (s.rt != g_last_rt) { write_event(g_fd_gamepad, EV_ABS, right_trigger_axis, s.rt); g_last_rt = s.rt; any_written = true; }
    if (finalDpadX != g_last_dpadX) { write_event(g_fd_gamepad, EV_ABS, ABS_HAT0X, finalDpadX); g_last_dpadX = finalDpadX; any_written = true; }
    if (finalDpadY != g_last_dpadY) { write_event(g_fd_gamepad, EV_ABS, ABS_HAT0Y, finalDpadY); g_last_dpadY = finalDpadY; any_written = true; }

    if (any_written) {
        write_event(g_fd_gamepad, EV_SYN, SYN_REPORT, 0);
    }
}

static inline int normalize_quest_stick(int raw) {
    int centered = raw - 32768;
    if (centered < -32768) return -32768;
    if (centered > 32767) return 32767;
    return centered;
}

struct DeviceSlot {
    int fd = -1;
    bool is_unified = false; // Unified node with both sticks (ABS_RX present)
    bool is_left = false;
    bool is_right = false;
};

static void* quest_evdev_reader_loop(void*) {
    LOGI("Hardware Quest evdev background reader started");

    DeviceSlot slots[16];
    int slot_count = 0;

    auto rescan_devices = [&]() {
        // Close existing
        for (int i = 0; i < slot_count; i++) {
            if (slots[i].fd >= 0) close(slots[i].fd);
            slots[i].fd = -1;
        }
        slot_count = 0;

        for (int i = 0; i < 32 && slot_count < 16; i++) {
            char path[64];
            snprintf(path, sizeof(path), "/dev/input/event%d", i);
            int fd = open(path, O_RDONLY | O_NONBLOCK);
            if (fd < 0) continue;

            struct input_id id;
            if (ioctl(fd, EVIOCGID, &id) < 0 || id.vendor != 0x2833) {
                close(fd);
                continue;
            }

            char name[128] = {0};
            ioctl(fd, EVIOCGNAME(sizeof(name) - 1), name);
            LOGI("Discovered Quest controller node: %s (name: %s, product: 0x%04X)", path, name, id.product);

            slots[slot_count].fd = fd;
            slots[slot_count].is_unified = false;
            slots[slot_count].is_left = false;
            slots[slot_count].is_right = false;

            // Check if device is unified (has ABS_RX for right stick)
            uint8_t abs_bits[(ABS_MAX + 7) / 8] = {0};
            if (ioctl(fd, EVIOCGBIT(EV_ABS, sizeof(abs_bits)), abs_bits) >= 0) {
                bool has_rx = (abs_bits[ABS_RX / 8] & (1 << (ABS_RX % 8))) != 0;
                if (has_rx) {
                    slots[slot_count].is_unified = true;
                    slots[slot_count].is_left = true;
                    slots[slot_count].is_right = true;
                    LOGI("-> Configured as UNIFIED Quest Controller node (both sticks & triggers): %s", path);
                }
            }

            if (!slots[slot_count].is_unified) {
                uint8_t key_bits[(KEY_MAX + 7) / 8] = {0};
                ioctl(fd, EVIOCGBIT(EV_KEY, sizeof(key_bits)), key_bits);
                bool has_x_or_y = ((key_bits[BTN_X / 8] & (1 << (BTN_X % 8))) != 0) ||
                                  ((key_bits[BTN_Y / 8] & (1 << (BTN_Y % 8))) != 0) ||
                                  ((key_bits[BTN_THUMBL / 8] & (1 << (BTN_THUMBL % 8))) != 0);
                bool has_a_or_b = ((key_bits[BTN_A / 8] & (1 << (BTN_A % 8))) != 0) ||
                                  ((key_bits[BTN_B / 8] & (1 << (BTN_B % 8))) != 0) ||
                                  ((key_bits[BTN_THUMBR / 8] & (1 << (BTN_THUMBR % 8))) != 0);

                if (has_x_or_y && !has_a_or_b) {
                    slots[slot_count].is_left = true;
                } else if (has_a_or_b && !has_x_or_y) {
                    slots[slot_count].is_right = true;
                } else if (strstr(name, "Left") != nullptr) {
                    slots[slot_count].is_left = true;
                } else if (strstr(name, "Right") != nullptr) {
                    slots[slot_count].is_right = true;
                } else {
                    slots[slot_count].is_left = (slot_count == 0);
                    slots[slot_count].is_right = (slot_count > 0);
                }
                LOGI("-> Configured as separate node: %s (is_left=%d, is_right=%d)",
                     path, slots[slot_count].is_left, slots[slot_count].is_right);
            }

            slot_count++;
        }
    };

    rescan_devices();

    while (g_reader_running.load()) {
        if (slot_count == 0) {
            sleep(1);
            rescan_devices();
            continue;
        }

        struct pollfd pfd[16];
        for (int i = 0; i < slot_count; i++) {
            pfd[i].fd = slots[i].fd;
            pfd[i].events = POLLIN;
            pfd[i].revents = 0;
        }

        int ret = poll(pfd, slot_count, 100);
        if (ret < 0) {
            if (errno == EINTR) continue;
            break;
        }
        if (ret == 0) continue;

        for (int i = 0; i < slot_count; i++) {
            if (!(pfd[i].revents & POLLIN)) continue;

            struct input_event evs[32];
            int n = read(slots[i].fd, evs, sizeof(evs));
            if (n < 0) {
                if (errno == EAGAIN || errno == EWOULDBLOCK) continue;
                LOGW("Controller disconnected: event node %d", i);
                rescan_devices();
                break;
            }

            int count = n / sizeof(struct input_event);
            bool state_changed = false;

            for (int k = 0; k < count; k++) {
                const auto& ev = evs[k];

                if (ev.type == EV_KEY) {
                    pthread_mutex_lock(&g_state_mutex);
                    if (!slots[i].is_unified) {
                        // Dynamically associate Left vs Right on distinctive buttons for separate nodes
                        if (ev.code == BTN_X || ev.code == BTN_Y || ev.code == BTN_SELECT || ev.code == BTN_THUMBL) {
                            slots[i].is_left = true;
                            slots[i].is_right = false;
                        } else if (ev.code == BTN_A || ev.code == BTN_B || ev.code == BTN_START || ev.code == BTN_THUMBR) {
                            slots[i].is_right = true;
                            slots[i].is_left = false;
                        }
                    }

                    int mask = 0;
                    if (ev.code == BTN_A)       mask = (1 << 0); // Cross
                    if (ev.code == BTN_B)       mask = (1 << 1); // Circle
                    if (ev.code == BTN_X)       mask = (1 << 2); // Square
                    if (ev.code == BTN_Y)       mask = (1 << 3); // Triangle
                    if (ev.code == BTN_TL)      mask = (1 << 4); // L1 Bumper
                    if (ev.code == BTN_TR)      mask = (1 << 5); // R1 Bumper
                    if (ev.code == BTN_SELECT)  mask = (1 << 6); // Create / Share
                    if (ev.code == BTN_START)   mask = (1 << 7); // Options
                    if (ev.code == BTN_MODE)    mask = (1 << 8); // PS Button
                    if (ev.code == BTN_THUMBL)  mask = (1 << 9); // L3
                    if (ev.code == BTN_THUMBR)  mask = (1 << 10); // R3
                    if (ev.code == BTN_TL2)     mask = (1 << 11); // L2
                    if (ev.code == BTN_TR2)     mask = (1 << 12); // R2

                    if (mask != 0) {
                        if (ev.value != 0) g_hw_state.buttons |= mask;
                        else g_hw_state.buttons &= ~mask;
                        state_changed = true;
                        LOGI("Quest HW Key: code=0x%04X val=%d -> buttons=0x%04X", ev.code, ev.value, g_hw_state.buttons);

                        // Quick-toggle: L3 (bit 9) + R3 (bit 10) pressed together toggles VR pointer vs Gamepad mode
                        bool both_clicked = ((g_hw_state.buttons & (1 << 9)) != 0) && ((g_hw_state.buttons & (1 << 10)) != 0);
                        if (both_clicked && !g_both_clicked_last) {
                            bool newState = !g_forwarding_enabled.load();
                            g_forwarding_enabled.store(newState);
                            LOGI(">>> L3 + R3 QUICK TOGGLE: Gamepad Forwarding is now %s <<<", newState ? "ACTIVE" : "PAUSED (VR laser pointer mode)");
                            if (!newState && g_fd_gamepad >= 0) {
                                QuestHwState neutral;
                                forward_state_to_uinput(neutral, true);
                                write_motion_frame(0, 0, 0, 0, 0, 0);
                            }
                        }
                        g_both_clicked_last = both_clicked;
                    } else if (ev.code == BTN_DPAD_UP) {
                        if (ev.value != 0) g_hw_state.dpadY = -1;
                        else if (g_hw_state.dpadY == -1) g_hw_state.dpadY = 0;
                        state_changed = true;
                    } else if (ev.code == BTN_DPAD_DOWN) {
                        if (ev.value != 0) g_hw_state.dpadY = 1;
                        else if (g_hw_state.dpadY == 1) g_hw_state.dpadY = 0;
                        state_changed = true;
                    } else if (ev.code == BTN_DPAD_LEFT) {
                        if (ev.value != 0) g_hw_state.dpadX = -1;
                        else if (g_hw_state.dpadX == -1) g_hw_state.dpadX = 0;
                        state_changed = true;
                    } else if (ev.code == BTN_DPAD_RIGHT) {
                        if (ev.value != 0) g_hw_state.dpadX = 1;
                        else if (g_hw_state.dpadX == 1) g_hw_state.dpadX = 0;
                        state_changed = true;
                    }
                    pthread_mutex_unlock(&g_state_mutex);
                } else if (ev.type == EV_ABS) {
                    pthread_mutex_lock(&g_state_mutex);
                    if (slots[i].is_unified) {
                        // UNIFIED DEVICE: ABS_X/Y is LEFT stick, ABS_RX/RY is RIGHT stick!
                        if (ev.code == ABS_X) {
                            g_hw_state.lx = normalize_quest_stick(ev.value);
                            state_changed = true;
                        } else if (ev.code == ABS_Y) {
                            g_hw_state.ly = normalize_quest_stick(ev.value);
                            state_changed = true;
                        } else if (ev.code == ABS_RX) {
                            g_hw_state.rx = normalize_quest_stick(ev.value);
                            state_changed = true;
                        } else if (ev.code == ABS_RY) {
                            g_hw_state.ry = normalize_quest_stick(ev.value);
                            state_changed = true;
                        } else if (ev.code == ABS_Z || ev.code == ABS_BRAKE) {
                            int val = (ev.value * 255) / 1023;
                            if (val < 0) val = 0; if (val > 255) val = 255;
                            g_hw_state.lt = val;
                            if (val >= 217) g_hw_state.buttons |= (1 << 11);
                            else g_hw_state.buttons &= ~(1 << 11);
                            state_changed = true;
                        } else if (ev.code == ABS_RZ || ev.code == ABS_GAS) {
                            int val = (ev.value * 255) / 1023;
                            if (val < 0) val = 0; if (val > 255) val = 255;
                            g_hw_state.rt = val;
                            if (val >= 217) g_hw_state.buttons |= (1 << 12);
                            else g_hw_state.buttons &= ~(1 << 12);
                            state_changed = true;
                        } else if (ev.code == ABS_HAT0X) {
                            g_hw_state.dpadX = ev.value;
                            state_changed = true;
                        } else if (ev.code == ABS_HAT0Y) {
                            g_hw_state.dpadY = ev.value;
                            state_changed = true;
                        }
                    } else {
                        // SEPARATE CONTROLLER NODES
                        if (slots[i].is_right) {
                            if (ev.code == ABS_X || ev.code == ABS_RX) {
                                g_hw_state.rx = normalize_quest_stick(ev.value);
                                state_changed = true;
                            } else if (ev.code == ABS_Y || ev.code == ABS_RY) {
                                g_hw_state.ry = normalize_quest_stick(ev.value);
                                state_changed = true;
                            } else if (ev.code == ABS_Z || ev.code == ABS_GAS) {
                                int val = (ev.value * 255) / 1023;
                                if (val < 0) val = 0; if (val > 255) val = 255;
                                g_hw_state.rt = val;
                                if (val >= 217) g_hw_state.buttons |= (1 << 12);
                                else g_hw_state.buttons &= ~(1 << 12);
                                state_changed = true;
                            } else if (ev.code == ABS_RZ) {
                                g_hw_state.rg = ev.value;
                                if (ev.value > 400) g_hw_state.buttons |= (1 << 5); // R1 Bumper
                                else g_hw_state.buttons &= ~(1 << 5);
                                state_changed = true;
                            }
                        } else {
                            // Left controller
                            if (ev.code == ABS_X) {
                                g_hw_state.lx = normalize_quest_stick(ev.value);
                                state_changed = true;
                            } else if (ev.code == ABS_Y) {
                                g_hw_state.ly = normalize_quest_stick(ev.value);
                                state_changed = true;
                            } else if (ev.code == ABS_Z || ev.code == ABS_BRAKE) {
                                int val = (ev.value * 255) / 1023;
                                if (val < 0) val = 0; if (val > 255) val = 255;
                                g_hw_state.lt = val;
                                if (val >= 217) g_hw_state.buttons |= (1 << 11);
                                else g_hw_state.buttons &= ~(1 << 11);
                                state_changed = true;
                            } else if (ev.code == ABS_RZ) {
                                g_hw_state.lg = ev.value;
                                if (ev.value > 400) g_hw_state.buttons |= (1 << 4); // L1 Bumper
                                else g_hw_state.buttons &= ~(1 << 4);
                                state_changed = true;
                            }
                        }
                    }
                    pthread_mutex_unlock(&g_state_mutex);
                }
            }
        }
    }

    for (int i = 0; i < slot_count; i++) {
        if (slots[i].fd >= 0) close(slots[i].fd);
    }
    LOGI("Hardware Quest evdev background reader stopped");
    return nullptr;
}

static void start_quest_reader() {
    if (!g_quest_input_enabled) return;
    if (g_reader_running.load()) return;
    g_reader_running.store(true);
    pthread_create(&g_reader_thread, nullptr, quest_evdev_reader_loop, nullptr);
}

static void stop_quest_reader() {
    if (!g_reader_running.load()) return;
    g_reader_running.store(false);
    pthread_join(g_reader_thread, nullptr);
}

static void destroy_devices() {
    stop_quest_reader();

    bool destroyed = false;
    if (g_fd_sensors >= 0) {
        ioctl(g_fd_sensors, UI_DEV_DESTROY);
        close(g_fd_sensors);
        g_fd_sensors = -1;
        destroyed = true;
    }
    if (g_fd_gamepad >= 0) {
        ioctl(g_fd_gamepad, UI_DEV_DESTROY);
        close(g_fd_gamepad);
        g_fd_gamepad = -1;
        destroyed = true;
    }
    if (g_fd_mouse >= 0) {
        ioctl(g_fd_mouse, UI_DEV_DESTROY);
        close(g_fd_mouse);
        g_fd_mouse = -1;
        destroyed = true;
    }
    if (g_fd_kbd >= 0) {
        ioctl(g_fd_kbd, UI_DEV_DESTROY);
        close(g_fd_kbd);
        g_fd_kbd = -1;
        destroyed = true;
    }
    g_last_mouse_buttons = 0;
    g_last_kbd_keys = 0;
    if (destroyed) {
        struct timespec ts = { 0, 80 * 1000 * 1000 };
        nanosleep(&ts, nullptr);
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_questgamepad_android_uinput_UInputNative_createDevice(JNIEnv*, jclass, jint profileId) {
    const gamepad_profile* prof = find_profile(profileId);
    LOGI("createDevice: profile=%d (VID=0x%04X PID=0x%04X name=\"%s\")",
         prof->id, prof->vid, prof->pid, prof->name);

    destroy_devices();
    g_current_profile_id = profileId;
    g_hw_state = QuestHwState{};
    g_both_clicked_last = false;
    g_last_buttons = 0xFFFFFFFF;
    g_last_lx = 999999; g_last_ly = 999999;
    g_last_rx = 999999; g_last_ry = 999999;
    g_last_lt = 999999; g_last_rt = 999999;
    g_last_dpadX = 999999; g_last_dpadY = 999999;

    if (prof->mouse_mode) {
        int mouse_fd = create_mouse_fd(prof->vid, prof->pid);
        if (mouse_fd < 0) return JNI_FALSE;
        int kbd_fd = create_keyboard_fd(prof->vid, prof->pid, true);
        if (kbd_fd < 0) {
            ioctl(mouse_fd, UI_DEV_DESTROY);
            close(mouse_fd);
            return JNI_FALSE;
        }
        g_fd_mouse = mouse_fd;
        g_fd_kbd   = kbd_fd;
        LOGI("Desktop devices created — mouse fd=%d, kbd fd=%d", mouse_fd, kbd_fd);
        start_quest_reader();
        return JNI_TRUE;
    }

    int fd = open("/dev/uinput", O_RDWR | O_NONBLOCK);
    if (fd < 0) {
        LOGE("open /dev/uinput failed: %s", strerror(errno));
        return JNI_FALSE;
    }

    if (set_bit_or_log(fd, UI_SET_EVBIT, EV_KEY, "EV_KEY") < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_EVBIT, EV_ABS, "EV_ABS") < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_EVBIT, EV_SYN, "EV_SYN") < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_EVBIT, EV_FF,  "EV_FF")  < 0) goto fail;

    if (set_bit_or_log(fd, UI_SET_FFBIT, FF_RUMBLE,   "FF_RUMBLE")   < 0) goto fail;
    if (set_bit_or_log(fd, UI_SET_FFBIT, FF_PERIODIC, "FF_PERIODIC") < 0) goto fail;

    {
        const int btns[] = {
            0x130, 0x131, 0x132, 0x133, 0x134, 0x135, 0x136,
            0x137, 0x138, 0x139, 0x13a, 0x13b, 0x13c, 0x13d, 0x13e
        };
        for (int b : btns) {
            if (set_bit_or_log(fd, UI_SET_KEYBIT, b, "KEY") < 0) goto fail;
        }
    }

    if (setup_abs(fd, ABS_X,        STICK_MIN, STICK_MAX, 16, 128) < 0) goto fail;
    if (setup_abs(fd, ABS_Y,        STICK_MIN, STICK_MAX, 16, 128) < 0) goto fail;
    if (setup_abs(fd, profileId == 2 ? ABS_Z : ABS_RX, STICK_MIN, STICK_MAX, 16, 128) < 0) goto fail;
    if (setup_abs(fd, profileId == 2 ? ABS_RZ : ABS_RY, STICK_MIN, STICK_MAX, 16, 128) < 0) goto fail;
    if (setup_abs(fd, profileId == 2 ? ABS_RX : ABS_Z, TRIG_MIN, TRIG_MAX, 0, 0) < 0) goto fail;
    if (setup_abs(fd, profileId == 2 ? ABS_RY : ABS_RZ, TRIG_MIN, TRIG_MAX, 0, 0) < 0) goto fail;
    if (setup_abs(fd, ABS_HAT0X,    HAT_MIN,   HAT_MAX,    0,   0) < 0) goto fail;
    if (setup_abs(fd, ABS_HAT0Y,    HAT_MIN,   HAT_MAX,    0,   0) < 0) goto fail;

    if (finalize_device(fd, prof->vid, prof->pid, prof->name, MAX_FF_EFFECTS) < 0) goto fail;

    memset(g_ff_effects, 0, sizeof(g_ff_effects));
    g_pending_strong = -1;
    g_pending_weak   = -1;

    LOGI("Virtual gamepad created (profile=%d), fd=%d", prof->id, fd);
    g_fd_gamepad = fd;

    if (profileId == 2 || profileId == 3) {
        g_fd_sensors = create_sensors_fd(*prof);
        if (g_fd_sensors < 0) LOGW("Sony motion sensor device unavailable");
    }
    g_fd_mouse = create_mouse_fd(prof->vid, (uint16_t)(prof->pid + 0x100));
    g_fd_kbd = create_keyboard_fd(prof->vid, (uint16_t)(prof->pid + 0x200), false);
    LOGI("Gamepad devices ready — gamepad=%d, mouse=%d, kbd=%d", g_fd_gamepad, g_fd_mouse, g_fd_kbd);

    // Launch direct hardware reader
    start_quest_reader();

    return JNI_TRUE;

fail:
    close(fd);
    return JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_questgamepad_android_uinput_UInputNative_sendFrame(
        JNIEnv*, jclass,
        jint buttons,
        jint lx, jint ly, jint rx, jint ry,
        jint lt, jint rt,
        jint dpadX, jint dpadY) {
    pthread_mutex_lock(&g_state_mutex);
    QuestHwState output;
    output.buttons = buttons;
    output.lx = lx; output.ly = ly; output.rx = rx; output.ry = ry;
    output.lt = lt; output.rt = rt;
    output.dpadX = dpadX; output.dpadY = dpadY;
    forward_state_to_uinput(output);
    pthread_mutex_unlock(&g_state_mutex);
}

extern "C" JNIEXPORT void JNICALL
Java_com_questgamepad_android_uinput_UInputNative_sendMotionFrame(
        JNIEnv*, jclass,
        jint gyroX, jint gyroY, jint gyroZ,
        jint accelX, jint accelY, jint accelZ) {
    pthread_mutex_lock(&g_state_mutex);
    if (g_forwarding_enabled.load() && !g_system_paused.load()) {
        write_motion_frame(gyroX, gyroY, gyroZ, accelX, accelY, accelZ);
    }
    pthread_mutex_unlock(&g_state_mutex);
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_questgamepad_android_uinput_UInputNative_pollFFEvent(JNIEnv* env, jclass) {
    if (g_fd_gamepad < 0) return nullptr;

    struct input_event ev;
    while (read(g_fd_gamepad, &ev, sizeof(ev)) == (ssize_t)sizeof(ev)) {
        if (ev.type == EV_UINPUT && ev.code == UI_FF_UPLOAD) {
            struct uinput_ff_upload upload;
            memset(&upload, 0, sizeof(upload));
            upload.request_id = ev.value;
            if (ioctl(g_fd_gamepad, UI_BEGIN_FF_UPLOAD, &upload) >= 0) {
                if (upload.effect.type == FF_RUMBLE) {
                    int slot = -1;
                    for (int i = 0; i < MAX_FF_EFFECTS; i++) {
                        if (g_ff_effects[i].id == upload.effect.id) { slot = i; break; }
                    }
                    if (slot < 0) {
                        for (int i = 0; i < MAX_FF_EFFECTS; i++) {
                            if (g_ff_effects[i].id == 0) { slot = i; break; }
                        }
                    }
                    if (slot >= 0) {
                        g_ff_effects[slot].id     = upload.effect.id;
                        g_ff_effects[slot].strong = upload.effect.u.rumble.strong_magnitude;
                        g_ff_effects[slot].weak   = upload.effect.u.rumble.weak_magnitude;
                    }
                }
                upload.retval = 0;
                ioctl(g_fd_gamepad, UI_END_FF_UPLOAD, &upload);
            }
        } else if (ev.type == EV_UINPUT && ev.code == UI_FF_ERASE) {
            struct uinput_ff_erase erase;
            memset(&erase, 0, sizeof(erase));
            erase.request_id = ev.value;
            if (ioctl(g_fd_gamepad, UI_BEGIN_FF_ERASE, &erase) >= 0) {
                for (int i = 0; i < MAX_FF_EFFECTS; i++) {
                    if (g_ff_effects[i].id == (int)erase.effect_id) {
                        g_ff_effects[i] = {};
                        break;
                    }
                }
                erase.retval = 0;
                ioctl(g_fd_gamepad, UI_END_FF_ERASE, &erase);
            }
        } else if (ev.type == EV_FF) {
            int effect_id = ev.code;
            if (ev.value == 0) {
                g_pending_strong = 0;
                g_pending_weak   = 0;
            } else {
                for (int i = 0; i < MAX_FF_EFFECTS; i++) {
                    if (g_ff_effects[i].id == effect_id) {
                        g_pending_strong = g_ff_effects[i].strong;
                        g_pending_weak   = g_ff_effects[i].weak;
                        break;
                    }
                }
            }
        }
    }

    if (g_pending_strong < 0) return nullptr;

    jintArray result = env->NewIntArray(2);
    jint vals[2] = { g_pending_strong, g_pending_weak };
    env->SetIntArrayRegion(result, 0, 2, vals);
    g_pending_strong = -1;
    g_pending_weak   = -1;
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_questgamepad_android_uinput_UInputNative_sendMouseFrame(
        JNIEnv*, jclass, jint relX, jint relY, jint scrollY, jint keys) {
    if (!g_forwarding_enabled.load() || g_system_paused.load()) return;
    const int mouse_bits = keys & ((1 << 16) | (1 << 17) | (1 << 18));
    const int kbd_bits   = keys & ((1 << MOUSE_KEY_COUNT) - 1);

    if (g_fd_mouse >= 0) {
        bool any_event = false;
        const int btn_changed = mouse_bits ^ g_last_mouse_buttons;
        if (btn_changed & (1 << 16)) { write_event(g_fd_mouse, EV_KEY, BTN_LEFT,   (mouse_bits >> 16) & 1); any_event = true; }
        if (btn_changed & (1 << 17)) { write_event(g_fd_mouse, EV_KEY, BTN_RIGHT,  (mouse_bits >> 17) & 1); any_event = true; }
        if (btn_changed & (1 << 18)) { write_event(g_fd_mouse, EV_KEY, BTN_MIDDLE, (mouse_bits >> 18) & 1); any_event = true; }
        if (relX != 0)    { write_event(g_fd_mouse, EV_REL, REL_X,     relX);    any_event = true; }
        if (relY != 0)    { write_event(g_fd_mouse, EV_REL, REL_Y,     relY);    any_event = true; }
        if (scrollY != 0) { write_event(g_fd_mouse, EV_REL, REL_WHEEL, scrollY); any_event = true; }
        if (any_event) {
            write_event(g_fd_mouse, EV_SYN, SYN_REPORT, 0);
            g_last_mouse_buttons = mouse_bits;
        }
    }

    if (g_fd_kbd >= 0) {
        const int kbd_changed = kbd_bits ^ g_last_kbd_keys;
        if (kbd_changed != 0) {
            for (int i = 0; i < MOUSE_KEY_COUNT; i++) {
                if (kbd_changed & (1 << i)) {
                    write_event(g_fd_kbd, EV_KEY, MOUSE_KEYS[i], (kbd_bits >> i) & 1);
                }
            }
            write_event(g_fd_kbd, EV_SYN, SYN_REPORT, 0);
            g_last_kbd_keys = kbd_bits;
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_questgamepad_android_uinput_UInputNative_setForwardingEnabled(JNIEnv*, jclass, jboolean enabled) {
    pthread_mutex_lock(&g_state_mutex);
    g_system_paused.store(!enabled);
    if (!enabled) {
        QuestHwState neutral;
        forward_state_to_uinput(neutral, true);
        write_motion_frame(0, 0, 0, 0, 0, 0);
    }
    pthread_mutex_unlock(&g_state_mutex);
    LOGI("Native forwarding enabled set to: %d", enabled ? 1 : 0);
}

extern "C" JNIEXPORT void JNICALL
Java_com_questgamepad_android_uinput_UInputNative_setQuestInputEnabled(JNIEnv*, jclass, jboolean enabled) {
    stop_quest_reader();
    g_quest_input_enabled = enabled;
    g_forwarding_enabled.store(true);
    g_system_paused.store(enabled);
    g_hw_state = QuestHwState{};
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_questgamepad_android_uinput_UInputNative_readQuestState(JNIEnv* env, jclass) {
    pthread_mutex_lock(&g_state_mutex);
    const auto& state = g_hw_state;
    jint values[] = {
        state.lx, state.ly, state.rx, state.ry, state.lt, state.rt,
        static_cast<jint>(state.buttons), state.dpadX, state.dpadY,
        state.left_thumbrest ? 1 : 0,
        g_forwarding_enabled.load() && !g_system_paused.load() ? 1 : 0,
        g_fd_sensors >= 0 ? 1 : 0
    };
    pthread_mutex_unlock(&g_state_mutex);
    jintArray result = env->NewIntArray(12);
    if (result) env->SetIntArrayRegion(result, 0, 12, values);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_questgamepad_android_uinput_UInputNative_destroy(JNIEnv*, jclass) {
    destroy_devices();
    LOGI("Virtual devices destroyed");
}
