# Qasa

Local-first password manager for iOS and Android, with Premium breach monitoring, VPN and
multi-device, paid through Iraqi wallets. The vault is encrypted and stored on the device;
the server only meters devices and sells features.

| Folder | What |
| --- | --- |
| [`server/`](server/) | Rust API, background worker, VPN node agent ([README](server/README.md)) |
| [`apps/ios/`](apps/ios/) | iOS app and its AutoFill / notification extensions |
| [`apps/android/`](apps/android/) | Android app |
| [`apps/dashboard/`](apps/dashboard/) | Admin console ([README](apps/dashboard/README.md)) |
| [`l10n/`](l10n/) | Shared UI strings (en / ar / ckb) for both apps ([README](l10n/README.md)) |
| [`docs/`](docs/) | Plans, design system, user flows |

Start with **[ARCHITECTURE.md](ARCHITECTURE.md)** for how it all fits together. Open work
is tracked in [`docs/server-followups-plan.md`](docs/server-followups-plan.md).

```bash
make help   # local dev commands: database, API, worker, dashboard, Android
```
