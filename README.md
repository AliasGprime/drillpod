**This project is far from perfect, I am just having fun with it.**

# DrillPod

**Unofficial Android launcher dedicated to *Motherload* (XGen Studios, 2004) — play your own copy with a gamepad, touch controls, and on two screens.**

DrillPod is built specifically for **Motherload**, the classic Flash mining game by XGen Studios. It runs the game with the open-source [Ruffle](https://ruffle.rs) emulator and adapts it to today's devices: Android handhelds (designed for the dual-screen **AYN Thor**), phones and tablets. Every feature — button-to-button menu navigation, the second-screen dashboard, local saves — is tailored to Motherload's screens, shops and save system; DrillPod is not a general-purpose Flash player.

> **Unofficial fan project, not affiliated with or endorsed by XGen Studios.** The app contains no part of the game (no graphics, sounds or code): every player provides their own copy of Motherload (`8khsgfbehb.swf`), verified by its MD5 fingerprint. See [Legal](#legal).

*Résumé en français à la [fin de cette page](#français).*

---

## Contents
- [Features](#features)
- [Installation](#installation)
- [Default controls](#default-controls)
- [Settings](#settings)
- [How it works](#how-it-works)
- [Building the APK](#building-the-apk)
- [Project structure](#project-structure)
- [Legal](#legal)
- [Thanks](#thanks)
- [Français](#français)

---

## Features

### Launcher
- Start menu fully usable **with a gamepad** or by touch.
- **Two ways to get Motherload**: direct download from XGen Studios' official website, or import of a file already on the device.
- **MD5 check** of the file (`42469867aa46129edbab37894c344cb4`): only this exact version of Motherload is accepted; any other file is rejected.
- **English / French** interface (automatic from the Android language, or chosen manually).
- Adaptive layout, tested from 640×360 to 1920×1080 points, landscape and portrait.

### Gamepad
- **Every action can be remapped** (16 actions in three contexts: driving, game menus, anywhere). A button already used in the same context is swapped automatically.
- **Button-to-button navigation in the game's menus**: on every screen (shops, inventory, options, save bot, game over…), the cursor lands on the most useful button by itself (*Fill Tank*, *OK*, *Sell All*, *Total Repair*…) and then jumps from button to button with the d-pad or the sticks.
- **B = back**: closes the screen or dialog on display (X, OK, NO, CANCEL).
- Left stick read **by angle** (not per axis) to avoid accidental direction changes; adjustable sensitivity.
- Cursor shaped like a small drill.

### Touch controls
- **Virtual gamepad modelled on the AYN Thor**: stick and d-pad on the left, A/B/X/Y diamond and right stick on the right, L1/L2/R1/R2 in the corners, Select/Start at the bottom.
- Touch buttons go through **exactly the same path as a real gamepad**: the cursor, the menus and custom bindings work the same way by touch.
- **Layout editor**: move, resize or hide every element with your finger.
- Settings: overall size, opacity, labels (buttons, action icons, or button + action), vibration, show auto/always/never.

### Two screens
- On a dual-screen device (AYN Thor), the **bottom screen shows a live dashboard**: cash, depth, score, fuel / hull / cargo-bay gauges (% used), detailed cargo with estimated value, items with their gamepad button, current equipment, alerts (low fuel, hull damaged, cargo bay full).
- The dashboard adapts to any resolution and screen density.

### Display
- Three modes: **stretched 16:9**, **original 4:3**, **wide** (more scenery on the sides).
- Rendering at the screen's full resolution.
- Optional **high-quality mode**: 1.5× supersampling, best Flash quality, and smoothing of textures that looked blocky when scaled up.

### Saves
- The game used to save to an online server that no longer exists. DrillPod **answers those requests locally**: games are kept on the device.
- **Automatic login** on the save / load screen: one press of A at the save bot, and one on the title screen to load.

---

## Installation

1. Download **`DrillPod.apk`** from the [Releases](../../releases) page.
2. Open it on the Android device (allow installs from unknown sources if asked). Play Protect may show a warning because the APK is not distributed through the Play Store.
3. Start **DrillPod**, then:
   - **Download the game** — fetches the file from the publisher's official website (Internet connection required), **or**
   - **Import a file** — select your copy of `8khsgfbehb.swf`.
4. The file is verified and prepared on the device. Choose **Play**.

Requires Android 7.0 or later.

---

## Default controls

| Button | Driving | Game menus |
|---|---|---|
| D-pad / left stick | Drive and drill | Jump to the next button (hold = repeat) |
| Right stick | Show the cursor (top buttons) | Jump to the next button |
| A | Show the cursor (INV / OPTIONS / HELP) | Click |
| B | Pause | Back / close |
| X | Dynamite | — |
| Y | Reserve fuel tank | — |
| L1 | Plastic explosives (C4) | Scroll up |
| R1 | Hull repair nanobots | Scroll down |
| L2 | Quantum teleporter | — |
| R2 | Matter transmitter | — |
| Select | Inventory | Inventory |
| Start | In-game help | In-game help |
| L3 / R3 | Display mode / show-hide touch controls | same |

Everything can be changed in **Launcher → Controls**. Pressing Android's Back button twice in game returns to the launcher.

---

## Settings

| Setting | Effect |
|---|---|
| Language | Automatic, English or French |
| Display mode | Stretched, original 4:3 or wide (can also be changed in game) |
| Second screen | Dashboard on the 2nd screen |
| High-quality mode | Supersampling, best quality, smoothed textures |
| Graphics quality | High / medium / low (outside high-quality mode) |
| Volume | 100 % to mute |
| Stick sensitivity | Normal, low or high |
| Automatic save login | Fills in and confirms the save login screen |
| Touch controls | Visibility, size, opacity, labels, vibration, visible elements, layout |

---

## How it works

- **Host**: a minimal Android app (no Gradle) with a WebView serving the local pages and Ruffle. Nothing is loaded from the Internet while playing.
- **Real input**: the cursor clicks by injecting genuine Android events (mouse hover and touch) at the target point, like a real mouse or finger would.
- **Hooks added to the game, on the device**: on import, `SwfPatcher.java` adds about thirty small notifications (`fscommand`) to **your copy** — when each screen or dialog opens, and on every HUD update — without changing the game's logic. The app therefore knows which menu is displayed (to place the cursor) and reads live values (for the second screen).
- **Button positions**: measured by scanning every game screen for the areas where the game shows its "hand" mouse cursor.
- **High quality**: a copy whose "non-smoothed" bitmap fills are marked "smoothed" is generated on the device; the picture is rendered in a 1.5× larger area, then scaled down.
- **Saves**: the game's requests to its former save server are intercepted and stored locally.

---

## Building the APK

Tools (Debian / Ubuntu):
```bash
sudo apt install aapt apksigner zipalign dalvik-exchange android-sdk-platform-23 openjdk-17-jdk npm
```

Fetch Ruffle (once):
```bash
./scripts/fetch-ruffle.sh
```

Create **your own signing key** (never publish it):
```bash
keytool -genkeypair -keystore ~/drillpod.jks -alias drillpod -keyalg RSA -keysize 3072 -validity 10000
```

Build and sign:
```bash
KEYSTORE=~/drillpod.jks KEYSTORE_PASS='...' KEY_ALIAS=drillpod ./build.sh DrillPod.apk
```

Optional variables: `ANDROID_JAR` (path to the platform-23 `android.jar`), `DX` (dex tool), `KEY_PASS`, `RUFFLE_VERSION`.

> An APK signed with a different key cannot be installed over a version signed with the official key (Android requires it): uninstall first.

### Automated releases

`.github/workflows/release.yml` builds, signs and publishes a GitHub release whenever a push to `main` changes `android:versionName` in `app/AndroidManifest.xml` (or the release notes), or when it is started manually from the **Actions** tab. To publish version `X.Y`:

1. set `android:versionName="X.Y"` (and increase `android:versionCode`) in `app/AndroidManifest.xml`;
2. write the notes in `release-notes/vX.Y.md` (first line = release title);
3. push to `main`. The workflow adds the APK and its SHA-256 / signing-certificate fingerprints to the release.

It needs two repository secrets: `KEYSTORE_BASE64` (the `.jks` keystore, base64-encoded) and `KEYSTORE_PASS` (optional: `KEY_ALIAS`, default `drillpod`). Without them the workflow skips the release.

---

## Project structure

```
app/
  AndroidManifest.xml
  src/com/drillpod/launcher/
    MainActivity.java          WebView, local file serving, import + MD5, input injection,
                               gamepad, second screen, high-quality copy
    SwfPatcher.java            adds the hooks and smooths textures (on the device)
    SecondScreenActivity.java  fallback for the second screen
  res/                         app icon and name
  assets/www/
    launcher.html              launcher (settings, controls, touch, licences)
    index.html                 game page (Ruffle, cursor, menus, touch, local saves)
    second.html                second-screen dashboard
    common.js                  languages, gamepad bindings, touch layout
tools/                         development tools (Python)
  avm1dis.py                   ActionScript 1/2 disassembler
  swfpatch.py                  same hooks as SwfPatcher.java (cross-check)
  swfsmooth.py / swfgfx.py     texture smoothing and bitmap analysis
scripts/fetch-ruffle.sh        downloads Ruffle from npm
build.sh                       build without Gradle
release-notes/                 release notes, one file per version
.github/workflows/release.yml  automated build + GitHub release
```

---

## Legal

- This repository and the APK **contain no part of the game**. The file is provided by each player — downloaded from the publisher's official website or imported — and never leaves the device.
- The changes (hooks, smoothing) are applied **locally, to the player's own copy**.
- The icon, the name "DrillPod" and all the code in this repository are original.
- The game and its trademarks belong to their respective owners. **This project is not affiliated with or endorsed by them.**
- To start, the game requires being loaded from its original web address: the app presents that address to it locally. If you redistribute this project, check that this complies with the law in your country.

---

## Thanks

A big thank-you to **XGen Studios**, the creators of **Motherload** (2004). Their mining game, with its Martian atmosphere, upgrades to unlock and surprising story, left its mark on a whole generation of browser gamers. This project only exists so people can keep enjoying their work on today's devices, now that Flash is gone. Discover their other creations at [xgenstudios.com](https://www.xgenstudios.com).

Thanks as well to the **[Ruffle](https://ruffle.rs)** team and contributors (MIT / Apache 2.0), without whom none of this would be possible. See [THIRD_PARTY.md](THIRD_PARTY.md).

## License

The code of this project is under the [MIT license](LICENSE). It covers neither Ruffle (own licenses) nor any game file.

---

## Français

**DrillPod** est un lanceur Android non officiel conçu spécifiquement pour **Motherload** (XGen Studios, 2004). Il fait tourner **votre propre copie** du jeu (`8khsgfbehb.swf`, MD5 `42469867aa46129edbab37894c344cb4`) avec [Ruffle](https://ruffle.rs) ; toutes ses fonctions (menus à la manette, tableau de bord du 2e écran, sauvegardes locales) sont adaptées à Motherload, ce n'est pas un lecteur Flash généraliste. **Il ne contient aucun élément du jeu** : le fichier est téléchargé depuis le site officiel d'XGen Studios ou importé depuis l'appareil, vérifié, puis préparé sur l'appareil. Projet de fan, ni affilié ni approuvé par XGen Studios.

Points forts :
- **Manette** entièrement réattribuable ; dans chaque menu du jeu, le curseur se place tout seul sur le bouton le plus utile puis saute **de bouton en bouton** ; B = retour / fermer.
- **Manette tactile virtuelle** inspirée de l'AYN Thor (joysticks, croix, A/B/X/Y, gâchettes, Start/Select), avec éditeur de disposition ; elle passe par le même chemin que la vraie manette.
- **Tableau de bord sur le 2e écran** (écran du bas de l'AYN Thor) : argent, profondeur, carburant, coque, soute, cargaison et valeur estimée, objets, équipement, alertes.
- **Modes d'affichage** (étiré, 4:3, élargi), rendu pleine résolution, **mode haute qualité** optionnel.
- **Sauvegardes locales** remplaçant l'ancien serveur en ligne, avec connexion automatique.
- Interface anglais / français, mise en page adaptative.

Installation : téléchargez `DrillPod.apk` depuis [Releases](../../releases), ouvrez-le sur Android 7.0+, puis **Télécharger le jeu** ou **Importer un fichier**, et **Jouer**.

Merci à **XGen Studios**, créateurs de Motherload (2004), et à l'équipe **Ruffle**. Ce projet n'est ni affilié ni approuvé par eux. Code sous licence MIT.
