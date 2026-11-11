# FocusFlow security-hardening — how to test in the app

You need: the FocusFlow build from `FocusFlow-main-hardened.zip` installed on your Android phone.
Nothing below needs a PC except the optional "proof" step (C).

## 0. Put the test files on the phone
1. Unzip this package on the phone (Files by Google can open zips), or copy the folders over USB.
2. Copy `should-work` and `should-be-rejected` into `Pictures/ff-tests/` so the gallery/photo picker can see them.
   Corrupted files are sometimes NOT shown by the gallery. If one is missing, use the picker's own
   "Browse…" / Files option and open it from there. The wallpaper picker already shows every file.
3. Put the two `.focusflow` files in `Download/`.

What the files are
- `should-work/` valid images, including a PNG named .jpg and a JPEG with junk appended (the app re-encodes it, so the
  junk is dropped). `6_wallpaper_only_big_4096x4096.jpg` is for the wallpaper test only.
- `should-be-rejected/` broken on purpose: cut in half, random bytes, a text file renamed .png, an empty file, a GIF,
  a cut-off WebP, a tiny file that *claims* 30000x30000 pixels, and a 25 MB file.

## A. Launcher wallpaper (the newly connected feature)
Needs the Glassy launcher style (the default) and FocusFlow set as your home app (Defense → Home launcher setup).
1. In Home launcher setup, row "Wallpaper" → Pick → choose `should-work/1_normal_photo_1600x1200.jpg`.
   Expect: the row says "Custom wallpaper selected".
2. Press the Home button. Expect: your picture, blurred, behind the launcher. (Before this fix it never appeared.)
3. Force-stop FocusFlow, reopen: still selected and still shown. Reboot the phone: still shown.
4. Pick `6_wallpaper_only_big_4096x4096.jpg`. Expect: works, no freeze, no crash.
5. Pick every file in `should-be-rejected/`, one at a time.
   Expect each time: a "That image could not be used as a wallpaper." message, and the previous wallpaper stays.
6. Tap "Clear wallpaper". Expect: "Default dynamic gradient" and the gradient back on the launcher.

## B. Task pictures
Import `security-test-picture-links.focusflow` (see D), open the task, open a subtask → "Choose photo".
- Every file in `should-work/` should attach (the 4096 one may be refused here; that is fine, it is for wallpaper).
- Every file in `should-be-rejected/` should fail with an error and attach nothing.
- After any failure, attach a good picture again: it must still work (the decoder restarts by itself).

## C. Proof that decoding is isolated (optional, needs a PC with USB debugging)
1. Run `adb shell ps -A | grep -i focusflow` and note the lines.
2. Attach a good picture, and immediately run it again.
   Expect: one extra FocusFlow entry (its user column looks like `u0_i` + digits) that disappears about 20 seconds later.

## D. Download-pictures dialog (hosts are now all listed)
1. Import `security-test-picture-links.focusflow` (Settings → Import backup, or open the file from the Files app).
2. Open the task "Security test: picture links". Tap the banner "Download N pictures from their links".
3. Expect the dialog "Download pictures?" to list each website once, in lower case:
   `www.gstatic.com`, `upload.wikimedia.org`, `images.example.com`.
   It must NOT list `93.184.216.34`, `192.168.1.1`, `localhost`, and `www.gstatic.com` must not appear twice.
4. Tap Cancel: nothing is downloaded.
5. Optional: confirm. The gstatic/wikimedia links should download if those files still exist; example.com fails.
   The five "Refused" subtasks never had a link and should just show "Photo needed".
   (The gstatic/wikimedia links are public sample files I could not check from here; any direct https link to a
   JPEG/PNG/WebP works instead.)

## E. A backup file can no longer change your wallpaper
1. Set a wallpaper as in A.1.
2. Import `security-test-backup-with-wallpaper.focusflow`.
3. Expect: the import works (one task "Security test: backup wallpaper") and your wallpaper is unchanged.

## F. Quick regression pass (nothing should have changed)
App starts; a focus session starts and ends and the end-of-task alarm can be dismissed; blocked apps still show the
block screen (with your overlay wallpaper if you set one); changing the daily allowance takes effect; the home-screen
widget still updates; installing or uninstalling an app still updates the app lists.
