# Git Conventions in the Relations Project

## Purpose

This document describes how to create commit names, PR titles and descriptions, and branch names in the project.

## Commit Names

Commit names must use the full Conventional Commits format required by Semantic Release:

```text
<type>(<scope>): <subject>

<body description>
```

Rules:

* use lowercase types, for example `feat`, `fix`, `docs`, `chore`, `refactor`, `test`, and `ci`,
* the first line is a short subject in Conventional Commits format,
* leave one blank line after the subject line,
* keep the subject short and write it in the imperative mood,
* the body briefly describes the most important change or the most recent significant change,
* write commit messages in English,
* return the entire commit message as a single text field, without additional comments.

## Pull Requests

The PR title must use the same format as the main commit:

```text
<type>(<scope>): <description>
```

Rules:

* write PR titles in English,
* the title should identify the most important change, usually the main feature or fix,
* write PR descriptions only in English,
* when updating an existing PR, retain information about earlier changes and add the new information.

## Branches

Write branch names in English.

A branch name should be short, readable, and related to the scope of the change.
