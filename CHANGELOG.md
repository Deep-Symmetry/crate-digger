# Change Log

All notable changes to this project will be documented in this file.
This change log follows the conventions of
[keepachangelog.com](http://keepachangelog.com/).

## [Unreleased][unreleased]

Nothing so far.


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


[unreleased]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.1.4...HEAD
[0.1.4]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.1.3...v0.1.4
[0.1.3]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.1.2...v0.1.3
[0.1.2]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.1.1...v0.1.2
[0.1.1]: https://github.com/Deep-Symmetry/crate-digger/compare/v0.1.0...v0.1.1
