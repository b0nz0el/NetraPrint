# Physical Device & Printer Validation

Run on each supported Android version and on both 58 mm and 80 mm thermal printers. Record the device, Android version, printer model, firmware, paper width, and date for every run.

## Bluetooth

- [ ] Pair the printer in Android Bluetooth settings; verify it appears in NetraPrint.
- [ ] Print the connection test receipt and a shipping label; confirm the selected paper width, readable text, and correct cut behavior.
- [ ] Turn Bluetooth off and on, reconnect, and retry a queued job.
- [ ] Disconnect or power off the printer during a multi-page print; verify retry is available and record whether completed pages are retransmitted.

## Wi-Fi

- [ ] Connect the phone and printer to the same network; verify the configured IP and port, then print a test receipt.
- [ ] Repeat with an unreachable IP and confirm the job is retained for retry without exposing document data in notifications or logs.
- [ ] Disconnect Wi-Fi or power off the printer during a multi-page print; restore connectivity, retry the queued job, and record whether completed pages are retransmitted.

## Paper, raster, and QR output

- [ ] Print representative text, a shipping label, and a PDF on 58 mm and 80 mm paper; check margins, scaling, wrapping, and page order.
- [ ] Print a dark photo and a light/gradient photo with Floyd-Steinberg, Atkinson, and threshold dithering; compare detail, contrast, and banding.
- [ ] Print generated and scanned QR codes at normal and reduced sizes; scan each from a second device under typical lighting.
- [ ] Confirm the test receipt and status messages follow both Indonesian and English app-language selections.

## Recovery and sign-off

- [ ] Cancel a queued job and confirm its temporary payload is removed; complete a job and confirm its payload is removed.
- [ ] Force-stop and relaunch after queueing a job; verify pending jobs recover and expired orphan files are purged.
- [ ] Trigger a simulated mid-print disconnect on both transports; verify retry behavior and that no receipt or document contents appear in Logcat.
- [ ] Record failures, printer/device details, evidence, and retest results.

**Result:** Pass / Fail  
**Tester:** ____________________  
**Date:** ____________________  
**Outstanding issues:** ____________________
