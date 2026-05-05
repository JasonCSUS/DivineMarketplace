# Stale File Review

Current status for the latest inspected zip:

- Previously marked stale extractor/repository files are no longer present in `src`.
- `src/main/java/divinejason/divinemarketplace/design/ProjectTodoMap.java` is absent from the current source tree. Keep it absent unless there is a specific reason to restore source-side planning.
- Runtime claim persistence is SQLite. Any old owner-hash shard/file claim notes should be read as obsolete storage planning, not current implementation.
- Per-player GUI/session/prompt state is intentionally sharded in memory by player UUID so simultaneous users do not share menu state.
- Custom item auto-discovery/admin definition should write runtime definitions to SQLite-backed `market_index` and write readable flagged/unknown metadata logs for server-owner review.


Comment cleanup status:
- Source comments were reviewed for old planning wording and updated to describe current runtime behavior.
- Root docs remain the source of truth for remaining work; source-side planning files should not be reintroduced.
