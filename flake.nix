{
  description = "chronophoto — Android dev environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config = {
            allowUnfree = true;
            android_sdk.accept_license = true;
          };
        };

        androidComposition = pkgs.androidenv.composeAndroidPackages {
          platformToolsVersion = "35.0.2";
          buildToolsVersions = [ "34.0.0" ];
          platformVersions = [ "34" ];
          abiVersions = [ "x86_64" ];
          includeEmulator = true;
          includeNDK = false;
          includeSystemImages = true;
          systemImageTypes = [ "google_apis" ];
          includeSources = false;
        };

        androidSdk = androidComposition.androidsdk;
        androidSdkPath = "${androidSdk}/libexec/android-sdk";

        avdName = "chronophoto_test";
        systemImage = "system-images;android-34;google_apis;x86_64";
        appPackage = "com.chronophoto";

        envExports = ''
          export ANDROID_HOME="${androidSdkPath}"
          export ANDROID_SDK_ROOT="${androidSdkPath}"
          export JAVA_HOME="${pkgs.jdk17.home}"
        '';

        ensureGradlew = ''
          if [ ! -x ./gradlew ]; then
            echo "error: ./gradlew not found in current directory ($PWD)" >&2
            echo "       run this from the project root" >&2
            exit 1
          fi
        '';

        ensureAvd = ''
          if ! avdmanager list avd 2>/dev/null | grep -q "Name: ${avdName}"; then
            echo "AVD '${avdName}' missing — creating it..."
            echo "no" | avdmanager create avd \
              -n "${avdName}" \
              -k "${systemImage}" \
              -d "pixel_5"
          fi
        '';

        mkApp = name: text: {
          type = "app";
          program = "${pkgs.writeShellApplication {
            inherit name;
            runtimeInputs = [ androidSdk pkgs.jdk17 ];
            text = envExports + text;
          }}/bin/${name}";
        };

        avdApp = mkApp "chronophoto-avd" ''
          ${ensureAvd}
          echo "AVD '${avdName}' is ready"
        '';

        emulatorApp = mkApp "chronophoto-emulator" ''
          ${ensureAvd}
          exec emulator -avd "${avdName}" -no-snapshot-save -gpu auto "$@"
        '';

        installApp = mkApp "chronophoto-install" ''
          ${ensureGradlew}
          ./gradlew installDebug
        '';

        launchApp = mkApp "chronophoto-launch" ''
          adb shell monkey -p "${appPackage}" -c android.intent.category.LAUNCHER 1 >/dev/null
          echo "launched ${appPackage}"
        '';

        runApp = mkApp "chronophoto-run" ''
          ${ensureGradlew}
          ./gradlew installDebug
          adb shell monkey -p "${appPackage}" -c android.intent.category.LAUNCHER 1 >/dev/null
          echo "launched ${appPackage}"
        '';

        logcatApp = mkApp "chronophoto-logcat" ''
          PID="$(adb shell pidof "${appPackage}" 2>/dev/null | tr -d '\r' || true)"
          if [ -n "$PID" ]; then
            echo "tailing logs for ${appPackage} (pid $PID); Ctrl+C to stop"
            exec adb logcat --pid="$PID" "$@"
          else
            echo "${appPackage} not running; tailing all logs filtered by tag"
            exec adb logcat "$@"
          fi
        '';
      in {
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            androidSdk
            jdk17
            kotlin
            gradle
          ];

          ANDROID_HOME = androidSdkPath;
          ANDROID_SDK_ROOT = androidSdkPath;
          JAVA_HOME = "${pkgs.jdk17.home}";
          GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdkPath}/build-tools/34.0.0/aapt2";

          shellHook = ''
            echo "chronophoto dev shell"
            echo "  android sdk: $ANDROID_HOME"
            echo "  java:        $(java -version 2>&1 | head -n1)"
            echo "  kotlin:      $(kotlin -version 2>&1)"
            echo "  gradle:      $(gradle --version | grep '^Gradle')"
            echo ""
            echo "available apps (run via 'nix run .#<name>'):"
            echo "  avd       — create the chronophoto AVD if missing"
            echo "  emulator  — start the emulator (auto-creates AVD)"
            echo "  install   — build & install debug APK to running device"
            echo "  launch    — launch the installed app"
            echo "  run       — install + launch in one shot"
            echo "  logcat    — tail logs for ${appPackage}"
          '';
        };

        apps = {
          avd = avdApp;
          emulator = emulatorApp;
          install = installApp;
          launch = launchApp;
          run = runApp;
          logcat = logcatApp;
          default = runApp;
        };
      });
}
