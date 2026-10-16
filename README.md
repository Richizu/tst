<p align="center"><img src="docs/logo.png" width="112" alt="RichizuFixRKN"></p>

<h1 align="center">RichizuFixRKN</h1>

<p align="center">Обход блокировок YouTube, Discord и других сайтов и встроенный прокси Telegram для Android-телефонов и Android TV — одной кнопкой.</p>

## Возможности

**Обход блокировок (VPN без сервера)**
- Локальный VPN: трафик выбранных приложений проходит через встроенный [byedpi](https://github.com/hufrea/byedpi), который режет, переставляет и подменяет первые пакеты соединения так, чтобы фильтр провайдера не узнал сайт.
- Автоподбор: быстрая проверка ~90 стратегий по адресам YouTube и Discord, затем подробная проверка лучших. Прогресс, оценка времени, «завершить сейчас».
- Отдельная стратегия для Wi‑Fi и мобильной сети, автоматическое переключение при смене сети.
- Проверка «сам»: приложение открывает YouTube и спрашивает, работает ли; если нет — пробует следующий способ.
- Свои стратегии (вставка из ByeByeDPI и т.п.), избранное, подробная проверка одной стратегии (YouTube/Discord, задержка).
- Только выбранные приложения или весь трафик; выбор приложений с поиском.
- DNS внутри туннеля, помощник по «Частному DNS» системы, IPv6.

**Прокси Telegram**
- Собственный локальный MTProto-прокси (secret `dd…`), передаёт трафик через WebSocket: напрямую на серверы Telegram, через сеть Cloudflare или обычным TCP — что доступно.
- Пул заранее открытых соединений, автоматический обход Cloudflare при включённом стороннем VPN, статистика.
- Подключение в Telegram одной кнопкой, ссылка, QR-код, смена ключа и порта.

**Остальное**
- Режим телевизора: интерфейс под пульт, блокировка QUIC в туннеле для стабильного YouTube, установка SmartTube одной кнопкой.
- Плитка быстрых настроек, автозапуск при включении, перезапуск при остановке системой (watchdog), экономный режим.
- Пауза/стоп из уведомления, напоминания (выключить прокси в Telegram, обход отключён другим VPN).
- Проверка и установка обновлений из GitHub Releases, экспорт/импорт настроек, журнал работы.

Требования: Android 8.0+, `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.

## Сборка

```bash
git clone --recurse-submodules https://github.com/richizu/tst.git
cd tst
./gradlew assembleRelease
```

Нужны JDK 17+, Android SDK (platform 36), NDK `30.0.14904198` и CMake `3.22.1`.
Нативные библиотеки собираются из исходников: `byedpi` — CMake вместе с JNI-мостом `app/src/main/cpp/bridge`,
`hev-socks5-tunnel` — ndk-build с небольшим патчем `app/src/main/cpp/tun/hev-drop-quic.patch` (блокировка QUIC для ТВ).

APK также собирает GitHub Actions (`.github/workflows/build.yml`) — см. вкладку Actions.

### Тесты

```bash
./gradlew testDebugUnitTest                         # протокол, нарезка пакетов, стратегии
RKN_NET_TEST=1 ./gradlew testDebugUnitTest          # + живая проверка прокси через серверы Telegram
```

## Структура

| Путь | Что внутри |
|---|---|
| `bypass/` | VPN-служба, JNI byedpi и туннеля, стратегии, подбор и проверка |
| `tg/` | прокси Telegram: obfuscated2, WebSocket-клиент, маршруты, Cloudflare |
| `service/` | управление запуском, уведомления, плитка, автозапуск, watchdog |
| `update/` | обновления и установка APK |
| `ui/` | интерфейс на Jetpack Compose (телефон и ТВ) |

## Лицензия

MIT — см. [LICENSE](LICENSE). Сторонние компоненты: [byedpi](https://github.com/hufrea/byedpi) (MIT),
[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel) (MIT), список Cloudflare-узлов из
[tg-ws-proxy](https://github.com/Flowseal/tg-ws-proxy) (MIT), [ZXing](https://github.com/zxing/zxing) (Apache 2.0).

Программа предоставляется «как есть», используйте на свой страх и риск.
