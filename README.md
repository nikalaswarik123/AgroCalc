# AgroCalc

Android-приложение для учёта хозяйства на Kotlin и Jetpack Compose.

## Возможности

- Поголовье и потребность в кормах на 30 дней.
- Склад кормов и воды, приход и списание, журнал кормления.
- Доходы и расходы в тенге.
- События с животными, вес и ветеринарные записи.
- Резервные копии JSON, экспорт CSV и PDF.

Данные хранятся локально на устройстве.

## Сборка

Откройте папку проекта в Android Studio и выполните синхронизацию Gradle.
Укажите путь к Android SDK в Android Studio или local.properties (не включается в Git).
Исходная конфигурация: SDK 37, minSdk 24, AGP 9.5.1, Gradle 9.6.0; Gradle Daemon использует JDK 25.

Windows: ` .\gradlew.bat assembleDebug `

Linux / macOS: ` ./gradlew assembleDebug `

Тесты: ` ./gradlew testDebugUnitTest ` (Windows: gradlew.bat).

APK: app/build/outputs/apk/debug/app-debug.apk.

Проект восстановлен из предоставленных файлов с сохранением кода и зависимостей.
Полная сборка пока не подтверждена.
