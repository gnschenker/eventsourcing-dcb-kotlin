# Event Sourcing with Dynamic Consistency Boundaries (Kotlin)

A small Kotlin library for **event sourcing without aggregates**. Consistency is decided per command, from the facts that command actually needs, using [Dynamic Consistency Boundaries](https://dcb.events/).

The store is PostgreSQL. There is no Spring, no framework ceremony, and the domain code is meant to be readable by a semi-technical subject-matter expert.

This is a working proof of concept, not a finished product.

## Why DCB

In classical event sourcing each entity lives on its own stream (the aggregate). A rule that spans two entities — *a course cannot accept more than N students, and a student cannot take more than M courses* — usually becomes a saga, two events for one fact, and a window where the system is wrong.

DCB keeps **one global sequence of facts**. Each fact is tagged with the subjects it is about (`student:s1`, `course:c1`). A decision:

1. Loads only the facts it needs (by type and subject).
2. Folds them into a small local state.
3. Accepts or rejects the command.
4. Appends new facts **only if no new matching facts appeared in the meantime**.

The query used to build the decision is the consistency boundary. It is not a class you designed last year.

The official minimum store API is two calls: `read(query)` and `append(facts, condition?)`. This library adds `subscribe` for projectors, and a thin decision vocabulary on top so domain authors never write queries by hand.

## Modules

```
dcb-core        facts, questions, decision DSL, in-memory store, Given/When/Then, ad-hoc and async projections
dcb-postgres    JDBC event store, JSON codec, tag table, checkpoints, projection snapshots
enrolment       course subscriptions — the DCB hello-world, in business language
```

`enrolment` depends on `dcb-core` only. Persistence is an adapter. Tests can run the same scenarios against memory or Postgres.

A second sample — a term-life sales journey from offer screen to Buy — is specified in [docs/term-life-sales-journey.html](docs/term-life-sales-journey.html) and implemented in the `harbor` module.

Dependencies are deliberately few: Kotlin, JUnit 5, the PostgreSQL JDBC driver, and kotlinx.serialization. No Spring, Exposed, Arrow, or Testcontainers.

## Requirements

- JDK 21
- Docker, for the Postgres tests
- The Gradle wrapper in this repo (`./gradlew`)

## Quick start

```bash
docker compose up -d
./gradlew test
```

Postgres is published on **localhost:5433** so it does not collide with a local server on 5432.

| Variable | Default |
|---|---|
| `DCB_PG_URL` | `jdbc:postgresql://localhost:5433/dcb` |
| `DCB_PG_USER` | `dcb` |
| `DCB_PG_PASSWORD` | `dcb` |

In-memory tests always run. Postgres tests run when the database is reachable and are skipped otherwise (`@EnabledIf`).

## Writing a domain

Three files, one language: **facts**, **questions**, **decisions**. Tests are the fourth artifact and should read like the conversation with the domain expert.

### Facts

A fact is something that happened. It declares what it is **about**. Those subjects become DCB tags; domain code never writes `"student:s1"` by hand.

```kotlin
@JvmInline
value class StudentId(val value: String) : About {
    override fun asSubject() = Subject("student:$value")
    override fun toString() = value
}

@Serializable
data class StudentSubscribedToCourse(
    val student: StudentId,
    val course: CourseId,
) : Fact {
    @Transient override val about = subjects(student, course)
}
```

The recorded type is the class simple name (`StudentSubscribedToCourse`). Payload is JSON. Subjects are stored separately as tags.

### Questions

A question is the event set for the decider **and** the fold into local state. Handlers define which types to load; `about` defines which subjects.

```kotlin
fun seatsTaken(course: CourseId) = question(initial = 0, about = course) {
    on<StudentSubscribedToCourse> { this + 1 }
    on<StudentUnsubscribedFromCourse> { this - 1 }
}

fun alreadySubscribed(student: StudentId, course: CourseId) =
    question(initial = false, about = subjects(student, course)) {
        on<StudentSubscribedToCourse> { true }
        on<StudentUnsubscribedFromCourse> { false }
    }
```

Keep questions small and reusable. A fat `CourseState` object is how aggregates sneak back in: you load and lock more history than the command needs.

### Decisions

Compose questions. The read query is the union of those questions. The append condition defaults to the questions you **require**.

```kotlin
fun subscribeStudentToCourse(student: StudentId, course: CourseId) = decision {
    val registered  by requiring { studentExists(student) }
    val defined     by requiring { courseExists(course) }
    val already     by requiring { alreadySubscribed(student, course) }
    val takenByThem by requiring { coursesTaken(student) }
    val seats       by requiring { seatsTaken(course) }
    val registration by requiring { studentRegistration(student) }
    val capacity    by considering { courseCapacity(course) }

    decide {
        unless(registered) { "Student $student is not registered" }
        unless(defined)    { "Course $course is not defined" }
        unless(!already)   { "Student $student is already subscribed to $course" }
        val maxCourses = registration?.maxCourses ?: 0
        unless(takenByThem < maxCourses) {
            "Student $student cannot take more than $maxCourses courses"
        }
        unless(seats < capacity) { "Course $course is full" }
        then(StudentSubscribedToCourse(student, course))
    }
}
```

| Word | Loads facts | Fails the append if those facts grow |
|---|---|---|
| `requiring` | yes | yes |
| `considering` | yes | no |

Use `considering` when you need a number (capacity, displayed price) but a concurrent change of that number should not bounce the write. `subscribeStudentToCourse` considers capacity and requires seats: two students racing for the last seat still conflict; a capacity change in flight does not.

`unless(condition) { reason }` rejects unless the condition is true. `then(...)` records new facts. Facts carry their own subjects.

Words that do **not** belong in domain code: aggregate, stream, repository, serializer, append condition, expected version.

### Tests

Given / when / then, no mocks, no Docker required:

```kotlin
given(
    CourseDefined(history, "History", capacity = 1),
    StudentRegistered(ada, "Ada"),
    StudentRegistered(grace, "Grace"),
    StudentSubscribedToCourse(ada, history),
).whenever {
    subscribeStudentToCourse(grace, history)
}.expectRejection("Course c1 is full")
```

The same history can be replayed against Postgres:

```kotlin
given(...).against(postgresStore).whenever { subscribeStudentToCourse(grace, history) }
```

Rejection text is a sentence a UI could show. If an expert cannot read the test name and the `given` list, fix the language before adding persistence.

## How a command runs

```
decision { requiring / considering / decide }     // describe the use case
        │
        ▼
store.read(union of questions)                    // head = last position in the store
        │
        ▼
fold each question over the matching facts
        │
        ▼
decide { unless / then }                          // pure rules
        │
        ▼
store.append(facts, failIfEventsMatch = required questions, after = head)
```

`after` is the **store head at read time**, not the last matching event. That is what the [DCB specification](https://dcb.events/specification/) asks for. An empty store and a uniqueness check (`this student must not already exist`) use the same mechanism.

A concurrent writer that appends a fact matching the lock query wins; the other call gets `ConcurrencyConflict` and the command should be retried.

## Event store

```kotlin
interface EventStore {
    fun read(query: Query, after: Position? = null): ReadResult
    fun append(facts: List<Fact>, condition: AppendCondition? = null): Position
    fun subscribe(query: Query, after: Position): Sequence<RecordedFact>
}
```

A DCB query is a list of items combined with **OR**. An item matches when:

- the event type is in the item’s types (or the type list is empty), **and**
- the event carries **every** listed subject (AND).

`Query.all()` matches every event.

### In memory

`InMemoryEventStore` holds `Fact` objects. It is the default for unit tests and is fast enough to be the inner loop.

### PostgreSQL

```sql
CREATE TABLE events (
  position bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  type     text NOT NULL,
  tags     text[] NOT NULL,
  data     jsonb NOT NULL
);

CREATE TABLE event_tags (
  tag      text NOT NULL,
  position bigint NOT NULL REFERENCES events(position),
  PRIMARY KEY (tag, position)
);
```

Reads and append-condition checks go through `event_tags` first (high-cardinality subjects), then filter by type (low cardinality). A GIN index on `tags` is the obvious first cut and is **not** the scale path; the tag table is.

Appends run in a `SERIALIZABLE` transaction: check the condition, insert the facts and their tags, or fail with `ConcurrencyConflict`. That is required on Postgres — a naive `INSERT … SELECT … WHERE NOT EXISTS` is only safe under SQLite’s file lock.

`ensureSchema()` creates the tables if they are missing. `docker/init.sql` does the same for a fresh Compose volume.

Each `read` / `append` opens a JDBC connection. There is no pool in this PoC. That is fine for correctness tests; it dominates micro-benchmarks.

## Projections

A projection is the same thing as a decision question: which facts to load, and how they fold into state. The difference is when it runs.

| Kind | When it is built | This repo |
|---|---|---|
| **Ad-hoc** | At query time, from the event store | done |
| **Asynchronous** | After append, checkpointed, can run in the background | done |
| **Synchronous** | In the same transaction as the append | later |

### Ad-hoc (just in time)

Nothing is stored. Each query reads the matching facts and folds them now.

```kotlin
val seats = store.ask(seatsTaken(history))
```

`project` also returns the store head at read time, so a later UI can wait for a slower projection:

```kotlin
val snapshot = store.project(seatsTaken(history))
snapshot.state   // 1
snapshot.asOf    // Position(5)
```

Several questions become one view with a single store read. Enrolment uses this for course availability:

```kotlin
fun EventStore.availabilityOf(course: CourseId): CourseAvailability = project {
    val defined  by lookingAt { courseExists(course) }
    val title    by lookingAt { courseTitle(course) }
    val seats    by lookingAt { seatsTaken(course) }
    val capacity by lookingAt { courseCapacity(course) }
    answer {
        CourseAvailability(defined, title, seats, capacity)
    }
}.state
```

`lookingAt` does not lock. Ad-hoc projections do not append.

`projection { }` is an alias for `question { }` when you want the read-side word.

### Async (after the fact)

An async projection has a name, a question, and a [ProjectionStore](dcb-core/src/main/kotlin/dcb/ProjectionStore.kt). It reads facts after the last saved store head, folds them, and writes **state and head together**. A new process with the same name continues from that snapshot.

```kotlin
val directory = store.projectAsync("course-directory", courseDirectory())
directory.catchUp()
directory.state[history]?.seatsTaken
```

`catchUpTo(position)` waits until the projection has seen that position — the usual “do not show a stale page after a command” hook:

```kotlin
val outcome = store.handle(subscribeStudentToCourse(ada, history))
val position = (outcome as Outcome.Accepted).position!!
directory.catchUpTo(position)
```

`start()` runs catch-up in the background. In-memory waits on the store’s monitor; Postgres uses `LISTEN` / `NOTIFY dcb_append`, with a poll timeout so a missed notify still recovers.

```kotlin
directory.start(pollMillis = 50).use {
    store.append(CourseDefined(history, "History", 8))
    directory.catchUpTo(Position(1))
}
```

Enrolment’s `courseDirectory()` is a global question (`about` is empty, so every course fact matches). That is the right shape for a persistent read model. Per-course ad-hoc views stay on `ask` / `availabilityOf`.

`rebuild()` discards the snapshot and folds the whole history again.

`InMemoryProjectionStore` is the default. `PostgresProjectionStore` keeps snapshots in a `projections` table; you pass encode/decode for the state type.

`Projector` / `FoldingProjector` remain as a lower-level “call this handler for each new fact” catch-up. Prefer `projectAsync` when you want a resumable read model.

## Bench

Off by default so `./gradlew test` stays short.

```bash
DCB_BENCH=1 DCB_BENCH_EVENTS=300 \
  ./gradlew :dcb-postgres:test --tests dcb.postgres.PostgresBenchTest
```

This measures unpooled appends, a tagged read, and one conditional append. Do not treat the millisecond numbers as a claim about the tag index; they include a new `DriverManager` connection on every call.

## Design notes

- Follow the [DCB spec](https://dcb.events/specification/) at the store. Domain code uses business words.
- Questions, not a custom English parser. Semi-technical people can read Kotlin if the names are theirs.
- `after` is store head. Empty query as a lock means “conflict on any new event” — the library never sends that by accident. A decision that only `consider`s appends unconditionally.
- One fact, several subjects. `StudentSubscribedToCourse` is about the student **and** the course.
- Hot subjects (a popular course) are still a contended boundary. DCB does not remove that; it just stops you locking the rest of the world with it.
- Multi-tenancy and synchronous projections are still ahead.

## Status

Working:

- DCB read and conditional append, in memory and in Postgres
- Decision queries derived from named questions
- Optional narrower lock (`considering`)
- Given/When/Then against both stores
- Tag-table access path
- Catch-up projector and checkpoint table
- Ad-hoc projections (`ask` / `project` / composed `lookingAt`)
- Async projections (`projectAsync`, catch-up, live tail, persisted snapshots)

Obvious next steps: synchronous projections, a connection pool, and a concurrent overlapping-append test.

## References

- [Dynamic Consistency Boundaries](https://dcb.events/)
- [Specification](https://dcb.events/specification/)
- [Sara Pellegrini — Killing the Aggregate](https://sara.event-thinking.io/2023/04/kill-aggregate-chapter-1-I-am-here-to-kill-the-aggregate.html)
- [Jérémie Chassaing — The Decider](https://thinkbeforecoding.com/post/2021/12/17/functional-event-sourcing-decider)
