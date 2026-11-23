# Vanta

A black-and-white, motion-first Android launcher. Oreo (8.0) and up.

Package: `com.xcluice.thelauncherwearemaking`

## Phase 1 — Core
- Home app (HOME intent), wallpaper shows through
- Swipeable home pages, dock, page indicator
- App drawer with live search, hidden apps
- Live package add/remove updates, first-run defaults

## Phase 2 — Motion
- Spring physics on every press, drag and settle
- Icon scale-up launch animation from the tapped icon
- Finger-tracked drawer (swipe up), with staggered icon entrance
- Page parallax and fading while swiping between pages
- Swipe down on home for notifications, long-press empty space for settings
- Long-press menu with origin-anchored pop animation
- No ripples or tap highlights anywhere

## Next
- Phase 3: drag to rearrange, folders, icon packs, gestures
- Phase 4: widgets, shortcuts, backup/restore

Releases are built by GitHub Actions on every push to `main`.
