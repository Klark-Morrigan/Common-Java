#!/usr/bin/env bash
# The JVM tier's own text type, added to the whitespace trim Common-Automation
# owns the engine for.
#
# Why this file exists rather than two words in the generic engine. Which files
# are Gradle scripts is JVM knowledge, and the generic tier is written to hold
# none: it trims Markdown, the type every repo has whatever it is written in,
# and a tier with more text types of its own widens the set. Put the other way
# round, Common-Automation would have to name a build tool it knows nothing
# about, and a second language tier would have to add its own beside it.
#
# Why here rather than in Spotless, which is this tier's formatter and does own
# .java. Spotless reads source sets, so no .gradle file is in its reach; and it
# is opt-in, so the repos that need this most are the ones without it - this
# repo has no Gradle project at its root at all. A hook covers every JVM repo
# on the one event that matters, which is a commit.
#
# Why a file of its own rather than a line in each hook. Every JVM repo's hook
# sources this, so the type is declared once for the tier: four repos each
# appending '*.gradle' for themselves is four places to edit the day a fifth
# type joins it.
#
# Sourced only, after the engine - it appends to an array the engine declares.
# Executing it on its own does nothing and is not an error.

WHITESPACE_TRIMMED_TYPES+=('*.gradle')
