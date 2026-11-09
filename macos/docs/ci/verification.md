## Universal binary verification

```
Architectures in the fat file: build/Release/Akaa Flix.app/Contents/MacOS/Akaa Flix are: x86_64 arm64 
```
✅ Main executable contains: x86_64 arm64

| Mach-O file | Architectures | Minimum macOS (per slice) |
|---|---|---|
| `Contents/MacOS/Akaa Flix` | x86_64 arm64 | x86_64:11.0 arm64:11.0  |
| `Contents/Frameworks/VLCKit.framework/Versions/A/VLCKit` | x86_64 arm64 | x86_64:10.9 arm64:11.0  |
| `Contents/Frameworks/libswift_Concurrency.dylib` | x86_64 arm64 | x86_64:10.9 arm64:11.0  |

- Bundle name: **Akaa Flix** · version **4.0.7** · identifier `com.akaaflix.mac` · LSMinimumSystemVersion **11.0**
- Signature: Signature=adhoc · flags=0x10002(adhoc,runtime)
- ✅ codesign --verify --deep --strict passed
- ✅ Hardened Runtime enabled
