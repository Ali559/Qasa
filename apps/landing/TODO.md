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
      - Confirm the contact address. `privacy@qasa-app.com` is a placeholder; set up the
        mailbox (or forwarding) or change `EMAIL` in `src/privacy.ts` and the in-app summary
        (`legal.privacyBody` in `l10n/strings.json`).
      - Say who the operator is legally: the page names Ali Barznji. If there is a company,
        a postal address or a governing jurisdiction to state, add it under "Who we are".
      - Name the country the Contabo server is in (the page only says data may be processed
        outside Iraq), and the VPN node hosts once nodes exist.
      - The ckb and ar text is a draft: have native speakers review it.
      - The page says account deletion is in the desktop app now and "coming to the phone
        apps". Update it when the phones get Delete account.
      - Keep it true: re-read it whenever the server stores something new (migrations),
        when retention changes (backups, logs), or when email leak alerts ship.
- [ ] **VPN.** Left off the page on purpose. `PROMO_SCRIPT.md` says not to show or mention it until
      the tunnel ships. When it does, add a feature tile and a Premium bullet in all three
      languages in `src/i18n.ts`.
- [ ] **2FA codes (free) and the breach check (Premium).** The apps now keep 2FA codes for
      saved logins on every plan, and the breached-password check is Premium only. The full
      page (`Landing.astro` and `src/i18n.ts`, all three languages) still says the breach
      check is free: the leaks feature row, the FAQ answer ("It's free for everyone") and the
      Free plan's feature list. Move it to the Premium list, and add 2FA codes as a feature
      and to the Free list. The coming-soon page names neither, so nothing live is wrong.
- [ ] **Much later: email leak alerts.** Parked (todo `email-leak-alerts` in
      `docs/server-followups-plan.md`). Keep them off the page until they ship; the page only
      talks about the breached-password check.
