Self-hosted F-Droid repo
========================

The `F-Droid repo` workflow (`.github/workflows/fdroid.yml`) runs after each tagged `Release APK` run. It downloads only the latest release APK (not the `-with-WEB` one), builds a signed F-Droid index with `fdroidserver` and deploys it to GitHub Pages. Older APKs are not kept. The previous web reader Pages workflow was removed, since a site has one Pages deployment.

Users add `https://danzuep.github.io/UsfmBible/fdroid/repo` in F-Droid (Settings > Repositories); the site root shows the same link.

One-time setup
--------------

1. Settings > Pages > Source: GitHub Actions.
2. Create the repo index signing key (separate from the APK key) and store it as secrets:
   ```shell
   keytool -genkeypair -v -keystore fdroid.p12 -storetype PKCS12 -alias fdroid -keyalg RSA -keysize 4096 -validity 10000
   gh secret set FDROID_KEYSTORE_BASE64 < <(base64 -w0 fdroid.p12)
   gh secret set FDROID_KEYSTORE_PASSWORD
   gh secret set FDROID_KEY_ALIAS --body fdroid
   gh secret set FDROID_KEY_PASSWORD
   ```
   Keep `fdroid.p12` safe: changing it changes the repo fingerprint and users must re-add the repo.
3. Make sure releases are signed with the real release keystore (see [RELEASING.md](RELEASING.md)). F-Droid clients reject an update signed with a different key than the installed app.
4. Run it for the current release: `gh workflow run fdroid.yml`.

App text for the listing is in `fdroid/metadata/com.github.danzuep.usfmbible.yml`.
