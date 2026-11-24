# MangaDex API integration

This is a standalone Kotlin/JVM API client that provides a testable foundation for a future Aniyomi source adapter. It is not itself an installable extension.

MangaDex API documentation: https://api.mangadex.org/docs/

Commands:

```bash
gradle run --args='search "solo leveling"'
gradle run --args='chapters <manga-uuid>'
gradle run --args='pages <chapter-uuid>'
```

The API is public, but still apply sensible request pacing. Chapter/image access is subject to MangaDex availability and rules.
