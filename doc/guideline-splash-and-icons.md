# ProIPTV: Logo, splash screen and icons

- **Version:** 0.2 (draft)
- **Date:** November 13, 2026
- **Audience:** Maintainers who change the logo and regenerate the graphics
- **Reference implementation:** `design/`, `app/src/main/java/com/kutlusoy/proiptv/ui/splash/`, `app/src/main/res/`
- **See also:** TVSig `design/` scripts and `SplashAnimationView`, which are the model for this setup

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. The logo

The logo is a red satin ribbon that forms a "P" with a play triangle inside, and the lettering "ProIPTV" in silver and crimson on black. It is a faithful vector copy of the logo picture of the maintainer.

| File | Content |
|---|---|
| `design/ProIPTV_logo.svg` | the full logo on black, 1254 x 1254 |
| `design/ProIPTV_icon.svg` | the mark only, transparent |
| `design/ProIPTV_wordmark.svg` | the lettering only, transparent |

How the vector was made: the shapes are traced from the picture, the shading is a stack of smooth gradient layers inside the outline (about 50 levels with a small blur), plus thin highlight layers. The file is therefore larger (300 KB) than a hand drawn logo. Compared with the original picture the mean difference is below 1 of 255 per pixel.

The SVG files MUST be viewed in a browser or an editor that supports SVG filters (blur). Very simple viewers may show the shading in steps.

## 2. Where the graphics are used

| Graphic | Files | Made from |
|---|---|---|
| Launcher icon (adaptive, themed) | `res/drawable-nodpi/ic_launcher_foreground.png`, `ic_launcher_monochrome.png`, `res/mipmap-anydpi-v26/ic_launcher.xml`, background color `icon_background` (black) | mark |
| Launcher icon for Android 7.x | `res/mipmap-<density>/ic_launcher.png`, `ic_launcher_round.png` | mark |
| Store icon | `app/src/main/ic_launcher-playstore.png` (512 x 512) | mark |
| Android TV banner | `res/drawable-<density>/tv_banner.png` (320 x 180 dp) | mark and lettering |
| Splash layers | `res/drawable-nodpi/splash_mark.png`, `splash_title.png`, `splash_glow_wide.png`, `splash_glow_near.png`, `splash_spark.png` | mark, lettering |
| Repository graphics | `doc/logo.png`, `doc/social-preview.png` | full logo |
| Debug build icons | `app/src/debug/res/` | amber background, DEBUG label |

The debug app has its own launcher icon (amber with a DEBUG label) and banner, so it is not mixed up with the release app.

## 3. Regenerate the graphics

After a change of an SVG in `design/`:

```bash
npm install playwright
node design/generate-assets.js
```

The script renders all pictures of the table above with Chromium (Playwright) and writes them into the project. Commit the changed files.

The social preview image can be uploaded in the repository settings under "Social preview" (`doc/social-preview.png`).

## 4. The splash screen

The start screen is a short intro of 4.5 seconds that is drawn by the app itself (`ui/splash/SplashScreen.kt`). The timeline is in `ui/splash/SplashTimeline.kt`:

- the mark fades in and settles (a small rise and zoom)
- a wide and a near red glow rise behind it and breathe slowly
- a light sweep runs over the mark (it is clipped to the shape of the mark)
- a spark flashes at the upper right highlight
- the lettering and the tagline slide in one after the other
- dust motes drift upwards in three depths (`SplashParticles.kt`)
- the whole scene pushes in slowly

The glow layers are prepared as pictures, so the app only moves and fades finished layers, which keeps it smooth on weak TV boxes. Any key or touch skips the intro. When the system animations are off (animator duration scale 0) the finished picture is shown for about a second.

The tagline under the lettering is the text `splash_tagline` of the language files. The timing values (durations, when each layer appears) are the numbers in `SplashTimeline.kt`, a unit test checks that layers appear in order and never flicker.

## 5. Checklist

- [x] Logo delivered and traced as SVG
- [x] Launcher icons, banner, store icon, splash layers, repository graphics generated
- [ ] Splash screen looked at on a real TV box and a phone (the layout was checked in a browser replica, the animation itself was not run on a device)
- [ ] Feature graphic 1024 x 500 for the Play listing made from the logo
