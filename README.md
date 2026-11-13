# Λαούτο — Δρόμοι (Android project)
Native Android app (Kotlin), microphone pitch detection, tonic/road selection, adjustable 100–1000 ms stable-note threshold, session-only history, no error counter.

## Build without Android Studio
Upload this project to a GitHub repository and use GitHub Actions with the included workflow. In the Actions run, download the APK artifact. Android permissions: microphone.

## Limitations
Not compiled or tested on a physical laouto. Autocorrelation may confuse harmonics, double courses, and repeated notes without a short silence. Road interval lists are equal-tempered approximations; verify against your preferred repertoire. The stability slider filters note duration, not tuning cents.
