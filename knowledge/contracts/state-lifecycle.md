# State Lifecycle

## Source Processing States

- `new_source`: source has been registered and has no prior processed snapshot
- `unchanged`: latest source content fingerprint matches the last successful snapshot
- `partial_update`: source changed and only part of the downstream state requires refresh
- `major_update`: source changed and requires broad downstream refresh
- `failed`: latest processing attempt failed and no new successful state was promoted

Only the Java state core may assign or change these states within a specific `space_id`.

## Knowledge Lifecycle

- `candidate`: worker produced a knowledge artifact that is not yet promoted
- `active`: artifact is accepted as a stable knowledge asset
- `needs_review`: artifact has gaps, conflicts, or insufficient coverage
- `stale`: artifact was valid before but is now outdated
- `superseded`: artifact was replaced by a newer accepted artifact
- `rejected`: artifact was explicitly rejected

Worker results may suggest issues, coverage, and scope. Java decides promotion within the target `space_id`.
