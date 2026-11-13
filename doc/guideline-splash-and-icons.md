# ProIPTV: Logo, splash screen and icons

- **Version:** 0.1 (draft)
- **Date:** November 13, 2026
- **Audience:** Maintainers who deliver the logo and regenerate the graphics
- **Reference implementation:** `app/src/main/java/com/kutlusoy/proiptv/ui/SplashActivity.kt`, `app/src/main/res/drawable/`, `app/src/main/res/mipmap-anydpi-v26/`
- **See also:** TVSig `design/` scripts and `SplashAnimationView`, which are the model for the final splash

Requirement levels follow common practice: **MUST** = mandatory, **SHOULD** = strongly recommended, **MAY** = optional.

---

## 1. State in version 0.0.1

The app ships a neutral placeholder: a blue play triangle on a dark background. It is used for the splash, the launcher icon and the TV banner. It MUST be replaced before the first public beta (`0.1.0-rc1`).

| Graphic | File | Size |
|---|---|---|
| Launcher icon (foreground) | `app/src/main/res/drawable/ic_launcher_foreground.xml` | 108 x 108 dp, content inside the center 66 x 66 dp |
| Launcher icon (background color) | `app/src/main/res/values/colors.xml`, `icon_background` | one color |
| Icon for Android 7.x | `app/src/main/res/mipmap-anydpi/ic_launcher.xml` | layers of the two above |
| Android TV banner | `app/src/main/res/drawable/tv_banner.xml` | 320 x 180 dp |
| Splash | `ui/SplashActivity.kt` | drawn by the app |

## 2. What you deliver

Put the files into the folder `design/` of the repository (create it) and tell the maintainer working on the repository, or run the generator once it exists:

| File | Content |
|---|---|
| `design/ProIPTV_icon.svg` | the logo as a square SVG, 1024 x 1024, transparent background, nothing important closer than 15% to the edge |
| `design/ProIPTV_banner.svg` | optional, wide version for the TV banner, 320 x 180 ratio |
| brand colors | the main color and the dark background color as hex values, written in the issue or the commit message |

The SVG MUST NOT contain text that depends on installed fonts (convert text to paths).

## 3. How the final splash is built

Like in TVSig: the intro is drawn by the app, with a timeline (logo turning in, glow, light sweep, title, subtitle, slow push in) and a particle layer. The glow is blurred once when the graphics are generated, so the app only moves and fades finished layers, which keeps it smooth on weak TV boxes. Any key or touch skips it, and it is shown as a still image when the system animations are off.

The generator script `design/generate-assets.js` (adapted from TVSig) will write the launcher icons, the TV banner, the splash layers, the notification icon, the store icon and the repository graphics from the SVG. It is added when the logo is delivered. Running it needs Node.js and Playwright:

```bash
npm install playwright
node design/generate-assets.js
```

## 4. Checklist

- [ ] `design/ProIPTV_icon.svg` delivered
- [ ] brand colors named
- [ ] generator added and run, icons and banner replaced
- [ ] splash timeline and particles built and checked on a TV box
- [ ] store icon 512 x 512 and feature graphic 1024 x 500 generated
