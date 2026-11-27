Project website
===============

The GitHub Pages home page (https://danzuep.github.io/UsfmBible/) is a [Hugo](https://gohugo.io) site in `website/`. It has no content of its own: the title, short and full description, icon and screenshots are read from the Fastlane store metadata, so updating the store listing updates the website.

| Page element | Source |
|--------------|--------|
| Title, tagline, intro, feature cards (each `• Title: text` line) | `fastlane/metadata/android/en-US/{title,short_description,full_description}.txt` |
| Icon | `fastlane/metadata/android/en-US/images/icon.png` |
| Screenshots | `fastlane/metadata/android/en-US/images/phoneScreenshots/` |
| Download buttons and links | `website/hugo.toml` (`[params]`) |

Hugo mounts `fastlane/metadata` as assets (`website/hugo.toml`), and `website/layouts/index.html` is the only template.

Store buttons
-------------

Set `playStoreId` (the application id, once published on Google Play) or `appStoreUrl` (iOS) in `website/hugo.toml` and the matching button appears. F-Droid and the APK download are always shown. When iOS metadata is added under `fastlane/metadata/ios`, extend the template to read it.

Building
--------

`hugo --source website --destination /tmp/site` (extended or standard Hugo, v0.147 or later). Deployment: Pages has one deployment holding the site and the F-Droid repo (`fdroid/repo`), built by `release.yml` on each `v*` tag. The site only updates when a release is tagged, so give pull requests that change it a `patch:` title prefix (see `auto-tag.yml`). The `Website` workflow only checks that it builds on pull requests.
