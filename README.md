[![Release](https://jitpack.io/v/umjammer/vavi-idea-plugin-maven.svg)](https://jitpack.io/#umjammer/vavi-idea-plugin-maven)
[![Java CI](https://github.com/umjammer/vavi-idea-plugin-maven/actions/workflows/maven.yml/badge.svg)](https://github.com/umjammer/vavi-idea-plugin-maven/actions/workflows/maven.yml)
[![CodeQL](https://github.com/umjammer/vavi-idea-plugin-maven/actions/workflows/codeql.yml/badge.svg)](https://github.com/umjammer/vavi-idea-plugin-maven/actions/workflows/codeql.yml)
![Java](https://img.shields.io/badge/Java-25-b07219)

# vavi-idea-plugin-maven

## Install

 * [maven](https://jitpack.io/#umjammer/vavi-idea-plugin-maven)

## Usage

 * put the caret on a `<version>` tag of a dependency / plugin / parent in `pom.xml` and press `cmd+/` (`ctrl+/`):
   the latest version list is fetched from the repository (Maven Central `maven-metadata.xml`, no database nor scraping) and shown as a popup.
   Elsewhere `cmd+/` works as usual (comment).
 * versions which have newer releases are highlighted (weak warning, quick fix: update). Inspection: `Maven > Newer dependency version available`.
   Versions of all poms are prefetched in the background when a project is opened. Cache TTL is 30 minutes.
 * a stable version is compared with stable releases only. Property references (`${...}`) and ranges are skipped.

## References

## TODO
