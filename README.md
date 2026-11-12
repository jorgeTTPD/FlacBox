# FlacBox

Reproductor de música para Android: motor de audio en Rust (decodificación + salida AAudio) y UI en Jetpack Compose (Material 3).

## Arquitectura

```
app/    UI Compose, escáner MediaStore, carátulas, MediaSession (media3)
audio/  Fachada JNI + jniLibs (libflacplayer.so, libc++_shared.so)
rust/   Motor: symphonia + oboe/AAudio + lofty
```

- **Rust** (`libflacplayer.so`): decodifica (FLAC, ALAC, MP3, AAC/M4A, Vorbis, WAV), mantiene la cola, gapless, seek, shuffle/repeat y escribe en el stream de AAudio. Los tags y carátulas también salen de `lofty`.
- **Kotlin**: biblioteca desde MediaStore (fuente de verdad, sin Room), `SimpleBasePlayer` como puente hacia `MediaSession`, notificación de reproducción y UI.

## Requisitos

- JDK 21, Android SDK 36, NDK 27.2.12479018
- Rust con targets `aarch64-linux-android` y `x86_64-linux-android`
- `cargo-ndk` (`cargo install cargo-ndk`)

## Compilar

```bash
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.2.12479018
export PATH=$HOME/.cargo/bin:$PATH

./gradlew :app:assembleDebug
```

`audio/build.gradle.kts` compila el `.so` con `cargo ndk` y copia `libc++_shared.so` desde el NDK antes de empaquetar. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Instalar

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Al abrir, conceder el permiso de audio ("Dar acceso a mi música").

## Tests del motor

```bash
cd rust && cargo test
cargo run --features cli --bin decodecheck -- archivo.flac
```
