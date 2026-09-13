# AniZen Issue #2 Progress Tracker & Action Plan

> This file tracks progress on GitHub Issue #79 (Performance, GC & Rendering optimizations).
> Items already completed have their code snippets removed to keep this tracker compact and actionable.

---

## Progress Overview

- [x] **3.2 Ephemeral String keys in large episode lists** (`AnimeScreen.kt:1651-1657`) - **COMPLETED**
- [x] **3.3 Ephemeral String keys in GlobalSearch card rows** (`GlobalSearchCardRow.kt:56`) - **COMPLETED**
- [x] **3.6 Leaking unmanaged 5-thread pool** (`SearchScreenModel.kt:47`) - **COMPLETED**
- [x] **3.7 Redundant LTR/RTL branch in UpIcon** (`AppBar.kt:426-431`) - **COMPLETED**
- [x] **3.8 .map {} used for Composable side-effects & rotation bug** (`LibrarySettingsDialog.kt:259, 310, 320, 330`) - **COMPLETED**
- [x] **1.5 Dynamic regex compilation in ExtensionDetailsScreen** (`ExtensionDetailsScreen.kt:80-96`) - **COMPLETED**
- [x] **3.9 Misuse of derivedStateOf on primitive in AppBar** (`AppBar.kt:98-100`) - **COMPLETED**
- [x] **P0.8 runBlocking in Compose & bogus filter loop** (`SettingsDiscordScreen.kt:240-255`) - **COMPLETED**
- [x] **1.4 Redundant padStart String Allocations in ExtensionManager** (`ExtensionManager.kt:81-86`) - **COMPLETED**
- [x] **4.6 SharedPreferences Repeatedly Read in VideoComparator** (`VideoComparator.kt:12-14`) - **COMPLETED**
- [x] **4.2 Uncached Java Reflection in EpisodeLoader** (`EpisodeLoader.kt:75-93`) - **COMPLETED**
- [x] **4.4 Root pos StateFlow in GestureHandler** (`GestureHandler.kt:99, 404, 444`) - **COMPLETED**
- [x] **LibraryCompactGrid keys & asAnimeCover** (`LibraryCompactGrid.kt:67, 93`) - **COMPLETED**

---

## Remaining Tasks (With Solutions Ready to Copy)

### 1. Compose UI & Scroll Fluidity (120Hz)

#### [ ] 3.1 SubcomposeAsyncImage Overhead in CastRow
- **File:** `app/src/main/java/eu/kanade/presentation/anime/components/CastRow.kt` (lines 156–171)
- **Problem:** `SubcomposeAsyncImage` in `LazyRow` disables slot reuse and layout prefetching, dropping frames during horizontal flings.
- **Fix:** Replace with standard `AsyncImage` with static painter placeholders.

#### [ ] 3.4 Unstable Collections in AnimeScreenModel Disabling Recomposition Skipping
- **File:** `app/src/main/java/eu/kanade/tachiyomi/ui/anime/AnimeScreenModel.kt` (lines 2195–2196) & `AnimeScreen.kt:1640`
- **Problem:** `fillerEpisodes: Set<Float>` and `episodeToSeason: Map<Long, String>` use mutable interfaces, flagged as Unstable by Compose compiler.
- **Fix:** Wrap in `ImmutableSet<Float>` and `ImmutableMap<Long, String>` from `kotlinx.collections.immutable`.

#### [ ] 3.5 Modifier.composed & Draw-Time Allocation
- **File:** `presentation-core/src/main/java/tachiyomi/presentation/core/util/Modifier.kt` (lines 34–43)
- **Problem:** `Modifier.composed` creates extra nodes, and allocates `CornerRadius` in the draw lambda on every frame tick (120 Hz).
- **Fix:** Migrate to `drawWithCache` or precomputed density corner radius.

---

### 4. Player Subsystem

#### [ ] 4.1 12 In-Method Regex Compilations in DefaultStreamSelector
- **File:** `app/src/main/java/eu/kanade/tachiyomi/ui/player/utils/DefaultStreamSelector.kt` (lines 332–525)
- **Problem:** 12 regexes compiled inside function bodies on every candidate evaluation.
- **Fix:** Hoist all 12 patterns to top-level `private val` constants.

#### [ ] 4.4 Root pos StateFlow Collection in PlayerControls & GestureHandler
- **File:** `app/src/main/java/eu/kanade/tachiyomi/ui/player/controls/PlayerControls.kt:143` & `GestureHandler.kt:99`
- **Problem:** Root collection of `pos` forces 60Hz full-screen recompositions.
- **Fix:** Scope collection strictly down to Seekbar; read `.value` on gesture down.

---

### 5. Storage, Downloads & Network

#### [ ] 2.1 SAF findFile Binder IPC Storm in DownloadProvider
- **File:** `app/src/main/java/eu/kanade/tachiyomi/data/download/DownloadProvider.kt` (lines 170–186)
- **Problem:** Redundant `findFile` ContentResolver queries while `allFiles` is already in RAM.
- **Fix:** Pre-index `allFiles` by name in a memory `Map`.

#### [ ] 2.2 Un-pooled Cipher.getInstance in HLS Downloading
- **File:** `app/src/main/java/eu/kanade/tachiyomi/data/download/Downloader.kt` (lines 1118–1122)
- **Problem:** JCA security provider lookup and buffer allocation per TS segment (600–1500x per episode).
- **Fix:** Pool cipher via `ThreadLocal<Cipher>`.

#### [ ] 1.1 O(N^2) APK Manifest Re-Parsing in ExtensionLoader
- **File:** `app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt` (lines 152–179)
- **Problem:** Unmemoized lazy Sequence re-extracts APK manifests $N \times N$ times on startup.
- **Fix:** Materialize private extensions once with `associateBy`.
