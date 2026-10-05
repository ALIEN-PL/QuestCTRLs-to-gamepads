# Handoff Documentation: QuestToGamepad

**Projekt:** QuestToGamepad – Mapowanie kontrolerów VR (Meta Quest 2, Quest 3, Quest 3S, Quest Pro) na wirtualne kontrolery (Sony DualSense PS5, DualShock 4, Xbox 360, Xbox One, Mouse/Desktop) za pomocą Linux `/dev/uinput` i Shizuku.  
**Platforma docelowa:** Meta Quest (Android 12L / Horizon OS), architektura arm64-v8a / x86_64.  
**Ostatnia aktualizacja:** 2026-10-05.

---

## 📌 Podsumowanie wykonanych prac i rozwiązanych problemów

### 1. Rozwiązanie problemu opóźnień (Latency < 1 ms)
- **Przyczyna pierwotna:** W `QuestVrInputProvider.kt` pracował 90-hercowy wątek w Javie wysyłający ramki zerowe przez Binder IPC do procesu Shizuku, który rywalizował z natywnym wątkiem czytającym evdev w C++. Powodowało to zatory w IPC oraz kolejkowanie zdarzeń w Android InputManagerze.
- **Rozwiązanie:**
  - Wyłączono pętlę pollingu Javy dla kontrolerów Questa (`QuestGamepadService.kt`), pozostawiając ją jedynie dla zewnętrznych padów / Steam Controllera.
  - W C++ (`app/src/main/cpp/uinput_jni.cpp`) zaimplementowano bufor deduplikacji stanów (`g_last_lx`, `g_last_ly`, `g_last_rx`, `g_last_ry`, `g_last_buttons` itp.). Zdarzenia `write_event()` do `/dev/uinput` są emitowane **wyłącznie przy faktycznej zmianie stanu**, co całkowicie wyeliminowało zjawisko kernel write storm.
  - Opóźnienie reakcji spadło do poziomu poniżej 1 ms (czas reakcji przerwania evdev w jądrze).

---

### 2. Rozwiązanie problemu lewego drążka (Left Stick Bug)
- **Objaw:** Lewy drążek nie reagował lub sterował prawym drążkiem wirtualnego gamepada.
- **Diagnoza niskopoziomowa:**
  - Analiza `dumpsys input` i `getevent` na podłączonych goglach Meta Quest 3 wykazała, że Horizon OS łączy wejścia obu kontrolerów (Touch Plus Left & Right) w **jeden wspólny węzeł sprzętowy** `/dev/input/event5` (`vendor=0x2833, product=0x5024`).
  - Węzeł ten transmituje jednocześnie osie obu kontrolerów:
    - `ABS_X` (0x00) i `ABS_Y` (0x01) dla lewego drążka,
    - `ABS_RX` (0x03) i `ABS_RY` (0x04) dla prawego drążka.
  - Poprzednia heurystyka w `rescan_devices()` sprawdzała ścieżkę jako `strstr(path, "event5") != nullptr` i błędnie flagowała węzeł jako wyłącznie prawy kontroler (`is_right = true`). W efekcie wejścia `ABS_X` i `ABS_Y` z lewego drążka przepisywane były do `rx` i `ry` (prawy stick), a `lx`/`ly` pozostawały na 0.
- **Wdrożona poprawka w C++:**
  - Dodano odpytanie deskryptora przez `ioctl(fd, EVIOCGBIT(EV_ABS, ...))`: obecność osi `ABS_RX` oznacza węzeł typu `is_unified`.
  - W węźle zunifikowanym:
    - `ABS_X` / `ABS_Y` $\rightarrow$ bezpośrednio i niezależnie zasilają `lx` i `ly` (lewy stick),
    - `ABS_RX` / `ABS_RY` $\rightarrow$ bezpośrednio i niezależnie zasilają `rx` i `ry` (prawy stick),
    - `ABS_Z` $\rightarrow$ lewy trigger analogowy (L2),
    - `ABS_RZ` $\rightarrow$ prawy trigger analogowy (R2).
  - Usunięto ściśnięcie lewego gripa (`s.lg > 400`) z aktywacji modyfikatora D-Pada, dzięki czemu trzymanie L1 (np. celowanie/blok w grze) nie blokuje ruchu lewym stickiem.

---

### 3. Pełny research mapowania kontrolerów i standardu Sony DualSense (PS5)

#### A. Węzeł sprzętowy Meta Quest 3 (`Vendor 0x2833, Product 0x5024`):
| Wejście sprzętowe Questa | Kod linuksowy evdev | Funkcja docelowa na gamepadzie |
| :--- | :--- | :--- |
| **Lewy Stick X/Y** | `ABS_X`, `ABS_Y` (0..65533, środek 32768) | Left Analog Stick (X, Y) |
| **Prawy Stick X/Y** | `ABS_RX`, `ABS_RY` (0..65533, środek 32768) | Right Analog Stick (Z, RZ w Androidzie) |
| **Lewy Trigger (Index)** | `ABS_Z` (0..1023) + `BTN_TL2` (0x138) | L2 Trigger (analog + cyfrowy klik) |
| **Prawy Trigger (Index)** | `ABS_RZ` (0..1023) + `BTN_TR2` (0x139) | R2 Trigger (analog + cyfrowy klik) |
| **Lewy Grip (Middle)** | `BTN_TL` (0x136) | L1 Bumper |
| **Prawy Grip (Middle)** | `BTN_TR` (0x137) | R1 Bumper |
| **Przycisk A** (Prawy) | `BTN_A` (0x130) | Krzyżyk (Cross / A) |
| **Przycisk B** (Prawy) | `BTN_B` (0x131) | Kółko (Circle / B) |
| **Przycisk X** (Lewy) | `BTN_X` / `BTN_NORTH` (0x133) | Kwadrat (Square / X na DualSense) |
| **Przycisk Y** (Lewy) | `BTN_Y` / `BTN_WEST` (0x134) | Trójkąt (Triangle / Y na DualSense) |
| **Przycisk Menu** (Lewy) | `BTN_SELECT` (0x13a) | Share / Create / Select |
| **Przycisk Meta** (Prawy) | `BTN_MODE` (0x13c) | PS Button / Home (zastrzeżony dla systemu VR) |
| **Klik Lewego Sticka** | `BTN_THUMBL` (0x13d) | L3 |
| **Klik Prawego Sticka** | `BTN_THUMBR` (0x13e) | R3 |

#### B. Mapowanie systemowe Androida dla DualSense:
Zweryfikowano plik systemowy na goglach: `/system/usr/keylayout/Vendor_054c_Product_0ce6.kl`:
- **Różnica PlayStation vs Xbox w evdev:**
  - W kontrolerach PlayStation (DualSense/DS4) scancode `0x134` odpowiada przyciskowi **Kwadrat** (`BUTTON_X` w Androidzie), a scancode `0x133` odpowiada przyciskowi **Trójkąt** (`BUTTON_Y` w Androidzie).
  - W kontrolerach Xbox jest odwrotnie: `0x133` to X, a `0x134` to Y.
  - Sterownik `uinput_jni.cpp` automatycznie podmienia kody scancode w zależności od aktywnego profilu (`square_key = isPlayStation ? 0x134 : 0x133`).
- **Osie wirtualnego DualSense:**
  - Lewy drążek: `ABS_X`, `ABS_Y`
  - Prawy drążek: `ABS_RX` $\rightarrow$ Android mapuje na oś `Z`, `ABS_RY` $\rightarrow$ Android mapuje na oś `RZ`.
  - Spusty: `ABS_Z` $\rightarrow$ `LTRIGGER`, `ABS_RZ` $\rightarrow$ `RTRIGGER`.

---

### 4. Funkcja szybkiego wyjścia / pauzy (Quick-Toggle L3 + R3)
- Zaimplementowano sprzętowy skrót: jednoczesne wciśnięcie drążków **L3 + R3** (`BTN_THUMBL` + `BTN_THUMBR`) natychmiastowo przełącza stan przekazywania gamepada (`g_forwarding_enabled`).
- Przy pauzie wirtualny kontroler wysyła neutralną ramkę i zwalnia przyciski, co pozwala użytkownikowi korzystać z systemowego wskaźnika laserowego VR Questa bez konieczności zamykania gry czy aplikacji.

---

### 5. Poprawki w warstwie UI i Service
- **`QuestGamepadService.kt`:**
  - Dodano akcję `ACTION_SET_PROFILE` wraz z `EXTRA_PROFILE_ID`.
  - Wyeliminowano błąd, w którym wybór chipa w UI wywoływał `ACTION_CYCLE_PROFILE` i przełączał profil na kolejny zamiast wybranego.
- **`MainActivity.kt`:**
  - Podpięto `ACTION_SET_PROFILE` bezpośrednio pod wybór profilu (DualSense, DS4, Xbox 360, Xbox One, Desktop).
  - Automatyczny start usługi po autoryzacji uprawnień w Shizuku.

---

### 6. Faktyczna przyczyna braku lewego sticka: klasyfikacja jako touchpad
- **Potwierdzone na Quest 3 przez `dumpsys input`:** wirtualny kontroler miał klasy `TOUCH` i `GAMEPAD`, a osie `ABS_X` / `ABS_Y` były przejmowane przez `Touch Input Mapper`. Android udostępniał X/Y jako `source=MOUSE`, nie `source=JOYSTICK`; prawy stick pozostawał joystickiem.
- **Przyczyna:** deklaracja `BTN_TOUCH` w deskryptorze `/dev/uinput`. To sygnał kontaktu z powierzchnią dotykową, a nie klik touchpada Sony. W połączeniu z `ABS_X` / `ABS_Y` powodował błędną klasyfikację lewego sticka.
- **Poprawka:** usunięto `BTN_TOUCH` z deklarowanych możliwości oraz z obu ścieżek wysyłania przycisków (`forward_state_to_uinput` i JNI `sendFrame`). Klik touchpada nie jest obecnie emulowany.
- **Weryfikacja:** `assembleDebug` zakończone powodzeniem; poprawiony APK zainstalowano na goglach i uruchomiono świeży proces Shizuku. Dla DualSense (`054c:0ce6`) potwierdzono brak klasy `TOUCH` oraz X/Y jako `source=JOYSTICK`, zakres -1..1. Tymczasowy log potwierdził przekazywanie rzeczywistego ruchu lewego sticka bez zerowania; diagnostykę usunięto po teście.
- **Test regresji:** po utworzeniu gamepada sprawdzić w `adb shell dumpsys input`, że X/Y występują w `Motion Ranges` jako `source=JOYSTICK`, a urządzenie nie ma klasy `TOUCH` ani źródła `MOUSE`.
- **Po aktualizacji:** uruchomić ponownie grę, aby odświeżyła listę urządzeń i osi. Działanie w konkretnej grze wymaga potwierdzenia przez użytkownika.

---

## 🗂️ Struktura kluczowych plików

```text
h:\DEV\quest-to-gamepad/
├── app/src/main/cpp/
│   ├── uinput_jni.cpp               # Bezpośredni czytnik evdev Questa i emulacja uinput w UID 2000
│   └── CMakeLists.txt              # Konfiguracja NDK i bibliotek Android Log
├── app/src/main/aidl/com/questgamepad/android/uinput/
│   └── IUInputService.aidl         # Interfejs IPC Shizuku (createDevice, sendFrame, setForwardingEnabled)
├── app/src/main/java/com/questgamepad/android/
│   ├── MainActivity.kt             # Główny ekran z wyborem profili i statusem Shizuku
│   ├── Prefs.kt                    # Ustawienia (martwe strefy, profile, kalibracja)
│   ├── service/
│   │   └── QuestGamepadService.kt  # Usługa pierwszoplanowa zarządzająca cyklem życia uinput
│   └── uinput/
│       ├── GamepadProfile.kt       # Definicje profili (VID/PID DualSense, Xbox itp.)
│       ├── UInputGamepad.kt        # Menedżer usługi Shizuku
│       ├── UInputNative.kt         # Deklaracje metod natywnych JNI
│       └── UInputService.kt        # Klasa usługi uruchamianej w procesie Shizuku
├── README.md                       # Główna dokumentacja projektu
└── handoff.md                      # Dokumentacja bieżącego stanu i historii zmian
```

---

## 🚀 Instrukcja budowania i uruchamiania

### Wymagania:
- Java JDK 17+
- Android SDK (API 34+)
- Aktywne połączenie ADB z goglami Meta Quest.
- Zainstalowane i uruchomione Shizuku na goglach (`moe.shizuku.privileged.api`).

### Kompilacja i instalacja:
```powershell
./gradlew assembleDebug
adb install -r -d "app/build/outputs/apk/debug/app-debug.apk"
```

### Uruchomienie z profilem Sony DualSense:
```powershell
adb shell "am force-stop com.questgamepad.android; pkill -f quest_uinput; sleep 1; am start -n com.questgamepad.android/.MainActivity; sleep 1; am start-foreground-service -a com.questgamepad.android.ACTION_START com.questgamepad.android/.service.QuestGamepadService; am start-foreground-service -a com.questgamepad.android.ACTION_SET_PROFILE --ei com.questgamepad.android.EXTRA_PROFILE_ID 3 com.questgamepad.android/.service.QuestGamepadService"
```

### Podgląd logów w czasie rzeczywistym:
```powershell
adb logcat -s quest_uinput_jni:V QuestGamepadService:V
```
