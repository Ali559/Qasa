# Landing page: before launch

- [ ] **Switch from the coming-soon page.** Until the apps are in the stores, every language
      page renders `ComingSoon.astro`. At launch, point `src/pages/index.astro`, `ar/` and
      `ckb/` back at `Landing.astro` (`<Landing lang="..." />`).

- [ ] **Store links.** Both App Store and Google Play buttons point at `#download`. Put the real
      listings in `APP_STORE_URL` and `PLAY_STORE_URL` in `src/components/Landing.astro`; every
      language page picks them up.
- [ ] **Extension store links.** The Chrome, Edge and Firefox buttons in the "Qasa on your
      computer" section point at `#desktop`. Once the extension is published, put the listings
      in `CHROME_STORE_URL`, `EDGE_STORE_URL` and `FIREFOX_STORE_URL` in
      `src/components/DesktopDownloads.astro`; the full page and `/download` (all three
      languages) pick them up. The desktop buttons already use the stable
      `/desktop/download/{macos,windows,linux-appimage,linux-deb}` links, which the server
      redirects to the latest GitHub release.
- [ ] **Privacy policy (`/privacy`, `src/privacy.ts`).** Before linking it from the stores:
      - Done 2026-11-09: `privacy@qasa-app.com` forwards via Cloudflare Email Routing; the
        operator is Noovio (Sulaymaniyah, Iraq); the server is in Germany. Name the VPN
        node hosts once nodes exist.
      - The ckb and ar text is a draft: have native speakers review it.
      - Keep it true: re-read it whenever the server stores something new (migrations),
        when retention changes (backups, logs), or when email leak alerts ship.
- [ ] **VPN.** Left off the page on purpose. `PROMO_SCRIPT.md` says not to show or mention it until
      the tunnel ships. When it does, add a feature tile and a Premium bullet in all three
      languages in `src/i18n.ts`.
- [ ] **Much later: email leak alerts.** Parked (todo `email-leak-alerts` in
      `docs/server-followups-plan.md`). Keep them off the page until they ship; the page only
      talks about the breached-password check.
