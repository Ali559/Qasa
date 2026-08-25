# KASO Admin Dashboard

Vue 3 + TypeScript admin dashboard with **shadcn-vue** (Nova style), dark emerald theme, and motion transitions.

## Stack

- Vue 3.5 + TypeScript + Vite
- shadcn-vue (Reka UI) + Tailwind CSS v4
- Lucide icons, Sonner toasts, Unovis charts, Motion One

## Screens

| Route | Screen |
|---|---|
| `/login` | Admin Sign-in |
| `/` | Executive Overview |
| `/users` | User Directory |
| `/payments` | Webhook Audit Logs |
| `/vpn` | VPN Nodes |
| `/telemetry` | System Telemetry |

## Develop

```bash
cd dashboard
npm install
npm run dev
```

Open the URL Vite prints (usually `http://localhost:5173`).

## Build

```bash
npm run build
npm run preview
```
