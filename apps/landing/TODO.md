# Landing page: before launch

- [ ] **Store links.** Both App Store and Google Play buttons point at `#download`. Put the real
      listings in `APP_STORE_URL` and `PLAY_STORE_URL` in `src/components/Landing.astro`; every
      language page picks them up.
- [ ] **VPN.** Left off the page on purpose. `PROMO_SCRIPT.md` says not to show or mention it until
      the tunnel ships. When it does, add a feature tile and a Premium bullet in all three
      languages in `src/i18n.ts`.
- [ ] **Free plan list.** The Free card lists "Password generator and health check".
      `ARCHITECTURE.md` §4.4 only compares devices, leak alerts and VPN between plans, so confirm
      both are really free, or move them under Premium in `src/i18n.ts` (en, ckb, ar).
