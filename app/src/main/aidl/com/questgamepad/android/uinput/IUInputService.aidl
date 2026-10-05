package com.questgamepad.android.uinput;

interface IUInputService {
    boolean canCreateDevice();

    // Create a virtual gamepad with the given profile id (see GamepadProfile.kt).
    // Returns true on success.
    boolean createGamepad(int profileId);

    void sendFrame(int buttons,
                   int leftStickX, int leftStickY,
                   int rightStickX, int rightStickY,
                   int leftTrigger, int rightTrigger,
                   int dpadX, int dpadY);

    // 6-axis motion state (gyroscope and accelerometer) for DualSense / DS4 profiles
    void sendMotionFrame(int gyroX, int gyroY, int gyroZ,
                         int accelX, int accelY, int accelZ);

    // Desktop / mouse-mode frame. `keys` is a MouseTarget bitmask.
    void sendMouseFrame(int relX, int relY, int scrollY, int keys);

    // Returns [strongMagnitude, weakMagnitude] in 0..65535 if a game triggered rumble,
    // or null if nothing happened since the last poll.
    int[] pollForceFeedback();

    // Run an arbitrary shell command as the Shizuku shell user. Returns the exit code.
    int runShellCommand(in String[] cmd);

    // Run shell command and return captured stdout
    String runShellCommandForOutput(in String[] cmd);

    void setForwardingEnabled(boolean enabled);

    void destroy();
}
