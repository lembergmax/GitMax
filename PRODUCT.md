# Product

[Deutsch](PRODUCT.de.md)

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Native Android, Java 21, XML views with Material 3, no Kotlin and no Compose. Decided in the planning round
(see `CLAUDE.md`); the pattern of the author's sister projects is the template.

## Users

To begin with exactly one person: the author of the app, who works on his own projects from the phone. He uses
GitHub and GitLab, knows Git and wants no beginner hand-holding. The app is published as source; anyone who knows
Git and personal access tokens can build it and sideload it. It runs on Android 14 or newer, on the go and at the
desk, in changing light, which is why light and dark follow the system.

## Product Purpose

Make Git on the phone as easy as on the computer: link GitHub and GitLab accounts, clone and update projects one
by one or in bundles into a single folder on the phone, review changes, commit and push. Success means: a repo is
up to date with its remote in a few taps, and a change is pushed without detours. Everything beyond that (history,
branches, stash, conflicts …) is there but never pushes itself in front of the four standard actions.

## Positioning

A Git client that puts repos in a **public folder** of the phone so that file managers, editors and Termux can see
them, and that clones or updates whole groups of repos in one go. Clone, update, commit and push are up front, the
rest is one level deeper.

## Operating Context

- Repos live as plain folders in phone storage (permission “All files access”).
- Authentication through personal access tokens per account, optionally SSH keys.
- Long operations (cloning) continue in a foreground service when the app is in the background.
- Editing happens partly in other apps, partly in the built-in simple editor.
- Tested on an emulator (API 35); real devices and real accounts follow.

## Capabilities and Constraints

- Standard: clone (one or several into one folder), update (one or all), commit, push.
- Advanced: history, diff, branches, stash, tags, remotes, merge and conflict resolution, reset/revert/cherry-pick,
  submodules, SSH, new repo, file tree with viewer and editor.
- Phone storage has no symlinks, no hard links and no file names with `: ? * " < > | \`.
- No Git hooks, no GPG signing, no partial clone. Git LFS is deliberately off: LFS files stay pointer files and the
  interface says so (findings of the spike in `CLAUDE.md`).
- Interface in English (default) and German, selectable in Settings. No analytics, no crash reporting, network access
  only to the user's own Git hosts and their APIs.
- Tokens and keys live only in the Android Keystore, never in logs, URLs or backups.

## Brand Commitments

Name **GitMax**, package `de.lembergmax.gitmax`. Tone set by the author: **soft and friendly** (approachable, round
shapes, warm colours, plenty of air) without giving up the density and clarity of a tool. No confetti, no mascots.

## Evidence on Hand

No real user, customer or performance data and no brand assets except the name. Demo data exists only in tests and
in the README screenshots and is marked as synthetic there; the release shows no invented numbers or sample repos.

## Product Principles

1. The four standard actions (clone, update, commit, push) are always at most two taps away; advanced features never
   displace them.
2. No silent data loss: conflicts, overwriting updates and force push require an explicit, understandable decision.
3. Batch operations are robust: one failed repo does not stop the others and stays retryable.
4. State is readable without reading: a repo's status and an operation's progress appear in the same place and in the
   same form.
5. Secrets stay secret; error messages name the problem and the next step.

## Accessibility & Inclusion

Material standard: contrast ≥ 4.5 : 1 for text, 48 dp targets, type up to 200 %, TalkBack labels for status and
selection, colour never the only carrier of information, the system setting “Remove animations” is respected.
