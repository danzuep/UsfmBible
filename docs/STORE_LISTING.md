Store listing and tester readiness
==================================

Home page, website and support link for every listing: https://danzuep.github.io/UsfmBible/ (privacy policy: https://danzuep.github.io/UsfmBible/privacy/). The same URL is opened by Settings > About in the app (`Navigator.HOME_PAGE_URL`).

Play Console fields not covered by Fastlane: privacy policy URL (above), Data safety form (no data collected or shared, no ads), content rating questionnaire, target audience (not designed for children), app category (Books & Reference), contact email.

Screenshot sequence
-------------------

Play orders screenshots by file name. Use Pixel-class phone screenshots in `fastlane/metadata/android/en-US/images/phoneScreenshots/` and add a caption in the top third (large, high contrast, at most 6 words); keep the Bible text as the hero and the caption the only overlay. Existing files cover steps 1 to 5 and 7; steps 6 and 8 still need new screenshots.

| # | Screen | Caption |
|---|--------|---------|
| 1 | Reading, day theme, WEB John 3 | Read the Bible offline |
| 2 | Translations list, language filter | 1,500+ translations, one tap |
| 3 | Verse in parallel with two translations | Compare translations side by side |
| 4 | Search results for a phrase | Search every verse |
| 5 | Verse with note, bookmark and highlight | Notes, bookmarks, highlights |
| 6 | Reading with TTS highlight and media notification | Listen with verse highlighting |
| 7 | Reading progress or timeline | Pick up where you left off |
| 8 | Import USFM dialog, Settings > About | Your own Bibles. No ads, no tracking |

Copy and ASO notes
------------------

- Title `USFM Bible` (10/30). Short description (73/80) leads with the strongest search terms: free, offline, 1,500+ translations, compare, search, listen.
- Full description (about 2,300/4,000) repeats the main keywords naturally in bullet lines, states the privacy stance and ends with the home page link. Update `title.txt`, `short_description.txt` and `full_description.txt` only in `fastlane/metadata/android/en-US/`; the website reads them too.
- The deuterocanon is supported but only a one-line technical note at the end of the description, not a selling point.
- Add other locales as `fastlane/metadata/android/<locale>/` (same files). Check the limits with `.github/scripts/check-store-assets.py`.

Once the Play account is active
-------------------------------

1. Complete identity verification, create the app (`com.github.danzuep.usfmbible`) and enrol in Play App Signing. A new personal account must run a closed test with at least 20 testers opted in for 14 continuous days before applying for production access.
2. Build a signed bundle: `./gradlew bundleRelease -PappVersion=X.Y.Z`. Signing needs the upload keystore described in [RELEASING.md](RELEASING.md); without `keystore.properties` the bundle is signed with a per-runner debug key and Play rejects later uploads. CI builds APKs only, so build the AAB locally or add a workflow.
3. Fill in the Play Console forms above, then `bundle exec fastlane validate`, `metadata` and `closed aab:...`.
4. Create a tester group (Google Group or email list) and add it to the closed track. Recruit from friends, church and Bible-study groups, r/androidapps and r/Bible, and the GitHub issues page; aim for 25 to cover drop-outs. Send the opt-in link with a short checklist: install, download a translation, read offline, search, add a note, listen, import a USFM zip.
5. Collect feedback through GitHub issues and Play's feedback channel, fix, and upload a new build to the same track about every week (versionCode must grow; the changelog is `changelogs/<versionCode>.txt`). Keep testers opted in until day 14 is complete.
6. Apply for production access, answer the review questions with the changes made from feedback, then use `bundle exec fastlane supply --track alpha --track_promote_to production` or Play Console to promote. Set `playStoreId` in `website/hugo.toml` so the website shows the Play button.
