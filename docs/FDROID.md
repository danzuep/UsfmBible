Self-hosted F-Droid repo
========================

The `fdroid` and `deploy-fdroid` jobs in `.github/workflows/release.yml` run on each `v*` tag, after the APKs are built. They take only that release's plain APK (not the `-with-WEB` one), build an F-Droid index with `fdroidserver` and deploy it to GitHub Pages, so only the latest APK is ever served. The index is signed with the same release keystore as the APK, from the `release` environment secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`, see [RELEASING.md](RELEASING.md)), so there is nothing extra to configure for signing. The previous web reader Pages workflow was removed, since a site has one Pages deployment.

Users add `https://danzuep.github.io/UsfmBible/fdroid/repo` in F-Droid (Settings > Repositories); the site root is a download page built by Hugo (see [WEBSITE.md](WEBSITE.md))

One-time setup
--------------

1. Settings > Pages > Source: GitHub Actions.
2. Settings > Environments > `github-pages` > Deployment branches and tags: allow `v*` tags (the release deploy runs on the tag ref) and `main` (the website-only deploy in `pages.yml`).
3. Publish the current release: `gh workflow run release.yml --ref vX.Y.Z` (the latest tag).

App text for the listing is in `fdroid/metadata/com.github.danzuep.usfmbible.yml`; the landing page is built by Hugo from `website/` and the Fastlane metadata, see [WEBSITE.md](WEBSITE.md).
