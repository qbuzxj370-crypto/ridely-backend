# 릴리즈 빌드 (앱스토어 제출용)

## 서명이 왜 필요한가

안드로이드는 스토어(구글 플레이·원스토어 등)에 상관없이, **APK/AAB가 반드시 서명돼 있어야 설치·배포할 수 있다.** 개발 중 `cordova run android`로 만드는 건 디버그 키로 자동 서명되는데, 이건 아무나 같은 키를 쓰는 개발용이라 실제 배포엔 못 쓴다.

**릴리즈 키(keystore)는 한 번 만들면 절대 잃어버리면 안 된다.** 앱을 업데이트하려면 항상 같은 키로 서명해야 하고, 키를 잃어버리면 지금 앱을 업데이트할 방법이 영영 없어져서 완전히 새 앱으로 다시 올려야 한다.

## 이미 만들어져 있는 것

- `android-shell/release-key.jks` — 릴리즈 서명 키 (2026-09-18 생성, 유효기간 30년). **`.gitignore` 대상이라 저장소에 없다.**
- `android-shell/build.json` — 서명에 쓸 키스토어 경로·별칭(alias: `ridely`)·비밀번호를 담은 파일. **이것도 `.gitignore` 대상이라 저장소에 없고, 비밀번호가 평문으로 들어있어 이 문서에도 적지 않는다.**

**⚠️ 이 두 파일과 비밀번호는 이 문서를 만든 사람에게 직접 요청해서 받을 것.** 다른 안전한 방법(팀 비밀번호 관리자 등)으로 전달받고, 각자 받은 파일을 `android-shell/` 밑에 그대로 두면 된다.

## 빌드 방법

`build.json`이 `android-shell/`에 있으면 `cordova build android --release` 실행 시 자동으로 그 서명 정보를 읽어서 서명까지 끝낸다.

```cmd
cd android-shell
cordova build android --release
```

### 어떤 스토어냐에 따라 포맷이 다르다

`build.json`의 `"packageType"` 값으로 결과물 포맷이 바뀐다:

| packageType | 결과물 | 용도 |
|---|---|---|
| `"bundle"` | `platforms/android/app/build/outputs/bundle/release/app-release.aab` | **구글 플레이** (요즘 AAB만 받음) |
| `"apk"` | `platforms/android/app/build/outputs/apk/release/app-release.apk` | 원스토어 등 (스토어별로 확인 필요) |

`build.json`을 열어서 `"packageType"` 값을 바꾸고 다시 빌드하면 된다. 같은 키로 서명하니 필요할 때마다 포맷만 바꿔서 뽑으면 된다.

⚠️ **각 스토어가 정확히 어떤 포맷·용량 제한·메타데이터를 요구하는지는 해당 스토어의 개발자센터 문서에서 최종 확인할 것.** 여기 적힌 건 일반적인 경향일 뿐, 스토어별 최신 정책은 다를 수 있다.

## 백업

`android-shell/release-key.jks`와 `build.json` 안의 비밀번호를 **이 컴퓨터 말고 다른 곳에도 최소 한 군데 백업**해둘 것 (팀 비밀번호 관리자, 암호화된 클라우드 저장소 등). 로컬 파일 하나에만 의존하면 안 된다.
