# AGENTS.md

## SeminarArc Project Skills

Use the project-local skills in `.agents/skills/` for Android and Jetpack Compose work in this repository.

### Android Lead Skill

For Android app architecture, data layer, Room, WorkManager, foreground service, Media3, testing, build logic, modularity decisions, and product-quality UI review, follow:

`.agents/skills/android-lead/SKILL.md`

Load supporting references from:

`.agents/skills/android-lead/references/`

### Compose Expert Skill

For Jetpack Compose UI implementation, state management, modifier ordering, performance, navigation patterns, animation, Material 3, and source-backed Compose guidance, follow:

`.agents/skills/compose-expert/SKILL.md`

Load supporting references from:

`.agents/skills/compose-expert/references/`

### Usage Rules

- Use both skills together for Compose-heavy Android features.
- Prefer `android-lead` for product architecture and app-level decisions.
- Prefer `compose-expert` for composable APIs, state/effect choices, navigation patterns, and performance-sensitive UI behavior.
- Before making non-trivial Compose decisions, consult the relevant reference files instead of relying on memory.
