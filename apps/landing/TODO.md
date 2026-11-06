# Landing page: before launch

- [ ] **Switch from the coming-soon page.** Until the apps are in the stores, every language
      page renders `ComingSoon.astro`. At launch, point `src/pages/index.astro`, `ar/` and
      `ckb/` back at `Landing.astro` (`<Landing lang="..." />`).

- [ ] **Store links.** Both App Store and Google Play buttons point at `#download`. Put the real
      listings in `APP_STORE_URL` and `PLAY_STORE_URL` in `src/components/Landing.astro`; every
      language page picks them up.
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
