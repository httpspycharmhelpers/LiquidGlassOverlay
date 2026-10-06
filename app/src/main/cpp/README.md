# Native module (`libnativegauss.so`)

SIMD-accelerated Gaussian / box blur and chromatic aberration used by
`com.example.blur.NativeGauss` and `com.example.liquidglass.NativeChromatic*`.

The prebuilt `arm64-v8a/libnativegauss.so` is committed under
`app/src/main/jniLibs/` so the app builds without an NDK.

Source files come from [QWEA0/Liquid-Glass-Android](https://github.com/QWEA0/Liquid-Glass-Android) (MIT).

## Files

| File | Purpose |
|---|---|
| `native-lib.cpp` | JNI entry points (`Java_com_example_blur_NativeGauss_*`) |
| `gauss_iir.cpp` / `gauss_iir_neon.cpp` | IIR Gaussian blur (scalar + NEON) |
| `boxblur.cpp` | Fast box blur fallback |
| `chromatic_aberration.cpp` | Chromatic aberration / dispersion |
| `CMakeLists.txt` | CMake target `nativegauss` |

## Rebuild

With NDK r26+ / clang (produces a self-contained `.so`, no libc++ dependency):

```bash
clang++ -shared -fPIC -O3 -march=armv8-a -D__ARM_NEON__ -std=c++17 \
  -fno-exceptions -nostdlib++ -Wl,-soname,libnativegauss.so \
  native-lib.cpp gauss_iir.cpp gauss_iir_neon.cpp boxblur.cpp chromatic_aberration.cpp \
  -llog -o ../jniLibs/arm64-v8a/libnativegauss.so
```

Or use CMake via the Android Gradle Plugin by adding an `externalNativeBuild`
block pointing at this `CMakeLists.txt`.