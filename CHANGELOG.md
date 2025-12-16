# Change Log

All notable changes to this project will be documented in this file.
This change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased][unreleased]

### Fixed

- Reworked the database export Kaitai Struct definition to incorporate some important discoveries by the [Mixxx](https://mixxx.org) and [rekordcrate](https://github.com/Holzhaus/rekordcrate) developers (thanks once again [@Swiftb0y](https://github.com/Swiftb0y)): all tables that use string offsets have subtypes which control whether those offsets are eight or sixteen bits.
  We had previously only noted that for the Artists table, but the Album, Tag, and Tag Track tables behave this way as well, and in fact even the Track table follows this pattern, but its offsets always use the sixteen bit variant because the rows are so big.
- The understanding of row counts in DeviceSQL data pages has been broken since the very beginning (though we had some clumsy workarounds that were good enough for reading).
  Thanks to [Robin McCorkell](https://github.com/RobinMcCorkell) we now can properly interpret these non-byte-aligned numbers. 

### Changed

- Updated the analysis file Kaitai Struct definition to cope with the fact that rekordbox sometimes puts truly bizarre values (we have seen `f3` and `f9`) in the track bank byte of song structure tags.
  Previously this cause construction of the entire tag object to fail because no matching enumeration value could be found.
  Now we have a separate `raw_bank` field that holds the numeric value, and `bank` is a value instance that can be `null` when `raw_bank` is not recognizable.


## [0.2.1] - 2025-07-21

### Added

- Thanks to [Dominik Stolz (@voidc)](https://github.com/voidc), we can now parse `exportExt.pdb` files for the tag information they contain.
- Metadata archives can be created for USB drives containing device libraries, to enable Beat Link to offer metadata when that drive is used in an Opus Quad.

### Fixed

- An error in interpreting a value in the database export file format could lead to some rows that were actually present in tables not being found. Thanks to [@IvanOnishchenko](https://github.com/IvanOnishchenko) for [pointing this out](https://github.com/Deep-Symmetry/crate-digger/issues/32), and [Jan Holthuis (@Holzhaus)](https://github.com/Holzhaus) for identifying a flaw in the first attempt at correcting it.


## [0.2.0] - 2024-05-04

May the Fourth be with you.

### Changed

- Upgraded Kaitai Struct to version 0.10, which includes a number of
  fixes and adds linting of mapped values.
  > :wrench:  This is a backwards-incompatible change.
- Since we are already backwards incompatible with previous releases,
  changed some mapped value names to correspond to
  the KSY style guide and fix linter errors reported by KSC 0.10:
  1. In `rekordbox_pdb.ksy` renamed `num_groups` to `num_row_groups`.
     This means that `Page.numGroups` is now `Page.numRowGroups` in
     the generated Java class.
  2. In `rekordbox_pdb.ksy` renamed `autoload_hotcues` to `autoload_hot_cues`.
     This means that `TrackRow.autoloadHotcues` is now
     `TrackRow.autoloadHotCues` in the generated Java class.
  3. In `rekordbox_anlz.ksy` renamed `len_preview` to `len_data`.
     This means that `WavePreviewTag.lenPreview` is now
     `WavePreviewTag.lenData` in the generated Java class.
  4. In `rekordbox_anlz.ksy` renamed `len_beats` to `num_beats`.
     This means that `BeatGridTag.lenBeats` is now
     `BeatGridTag.numBeats` in the generated Java class.
  5. In `rekordbox_anlz.ksy` renamed `len_cues` to `num_cues`.
     This means that `CueTag.lenCues` and `ExtendedCueTag.lenCues` are now
     `CueTag.numCues` and `ExtendedCueTag.numCues` in the generated Java
     classes.
- Also upgraded to Java 11 as a compilation target, in order to be able to work with the new IO API, among other things.

### Added

- New API to create an archive of all the metadata from a media export volume needed to support Beat Link (to support working with the Opus Quad, which cannot provide metadata itself).

### Fixed

- With the help of new linting in the latest unreleased version of KSC,
  changed all character encoding names to use their soon-to-be-canonical
  uppercase versions.


## [0.1.6] - 2022-03-07

### Added

- Ability to parse `PSSI` (song structure/phrase analysis) tags now
  exported by rekordbox 6.
- Ability to parse the history playlist names and entries found in
  rekordbox database exports.

### Changed

- Upgraded Kaitai Struct to version 0.9, which fixes a bug that
  prevented us from using enums for the four-character-codes in
  track analysis files. This makes the parsed values more readable
  in the Web IDE, and in the generated classes as well.
  > :wrench:  This is a backwards-incompatible change.
- @Swiftb0y improved our understanding of the format of DeviceSQL
  strings in export files, which simplified the Kaitai Struct
  definitions for them, and greatly improved the documentation.

### Fixed

- The offset numbers in table page diagrams were incorrect (the
  documentation did not accurately reflect the Kaitai Struct parser).

## [0.1.5] - 2020-12-28

### Fixed

- DeviceSQL Strings with kind `90` are actually stored as UTF-16LE with
  an unknown `00` byte between the length and text.
- The structure analysis documentation and diagram for database table
  pages neglected a pair of unknown bytes at the end, so the locations
  of the row presence flags and row offsets were shown incorrectly.

## [0.1.4] - 2020-05-10

### Fixed

- The colors assigned to ordinary memory points and loops use
  a [completely separate mechanism](https://github.com/Deep-Symmetry/crate-digger/pull/13),
  which was discovered by [@ehendrikd](https://github.com/ehendrikd).
- Apparently some `DAT` files can be created by mixers somehow? And
  apparently these have incorrectly-formatted vestigial `PWAV` and
  `PWV2` tags that claim to have the normal number of preview bytes,
  but whose tag size is equal to their header size, so they have no
  actual data at all. This was causing the Kaitai Struct parser to
  crash. This change allows the parse to succeed, and instead these
  tags will return `nil` when you ask for their `data()`.
- Rekordbox 6 seems to have at least one new phrase style in it, and
  this was also crashing the parser. For now, until KSC can handle
  switching on possibly-null enum values in Java, give up on using an
  enum for this.


## [0.1.3] - 2020-02-09

### Fixed

- The parsing of `DAT` files would crash on some cue lists because we
  originally assumed the cue count was a four byte value, but it
  appears to actually only be two bytes (which is more than enough)
  and sometimes non-zero values would appear in the high bytes,
  causing us to try to read vastly more cues than actually existed.
  Thanks to [@drummerclint](https://github.com/drummerclint) for
  reporting this and sharing the problem file that enabled me to
  figure it out.


## [0.1.2] - 2019-10-25

### Fixed

- Some extended cue entries found in the field were missing the color
  bytes which are expected to follow the comment text, and this was
  causing the parsing of `EXT` files to fail. These values are now
  treated as optional.
- Now builds properly under current JDKs, including Amazon Corretto 11
  (which is a long-term support release). The minimum JDK for building
  is now Java 9, but the resulting build is still compatible back to
  Java 1.6. Building under Java 11 results in much nicer JavaDoc, with
  search support.


## [0.1.1] - 2019-08-25

### Added

- Ability to parse `PCO2` tags, which hold cues and loops along with
  DJ-assigned comment labels and colors, displayable on nxs2 players.
- Ability to parse `PSSI` tags, which hold phrase structure analysis
  performed by rekord box with a Performance license. (As this is not
  currently included when tracks are exported to removable media,
  nor available through a `dbserver` query, it cannot be used by
  Beat Link.) Many thanks to [@mganss](https://github.com/mganss) for
  this contribution!
- More explanation of the interpretation of waveform color preview
  tags.


## 0.1.0 - 2019-02-23

### Added

- Initial release.


[unreleased]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.2.1...HEAD
[0.2.1]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.2.0...v0.2.1
[0.2.0]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.1.6...v0.2.0
[0.1.6]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.1.5...v0.1.6
[0.1.5]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.1.4...v0.1.5
[0.1.4]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.1.3...v0.1.4
[0.1.3]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.1.0...v0.1.1
