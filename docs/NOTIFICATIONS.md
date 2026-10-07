# How notifications work, and whether a message broker (RabbitMQ) is needed

## How it works today

1. Something happens in a service (an application is received, an employer moves it to INTERVIEW, a password changes).
2. The service calls `NotificationService.notify(...)`. Before storing anything it asks every `NotificationGate`;
   the user's own settings (`PreferenceService`, `GET/PUT /users/me/preferences`) are one gate and drop the
   message if the user switched that type off. `ACCOUNT` (security) messages cannot be switched off.
3. The message is a row in `notifications`, written **in the same database transaction as the change that caused it**.
   If the change rolls back, so does the message; if storing the message fails, the change fails.
4. The browser polls `GET /notifications/unread-count` (30 to 60 s) and loads the list when the panel opens.

Nothing is sent to a second system, so nothing can get out of step with the database.

## Is RabbitMQ necessary? **No. Would it be better? Not yet.**

For what exists now (in-app messages, one instance, a handful per user per day, delivered by polling) a broker is
**not necessary and would make the system worse**:

| | Database row (now) | Add RabbitMQ |
|---|---|---|
| Consistency with the change that caused it | Same transaction: exactly consistent | Two systems: a crash between "save" and "publish" loses or duplicates messages, so you must build an outbox table anyway |
| Parts to run, secure, back up, monitor | None added | A broker, its credentials, its disk, its monitoring |
| Failure modes | The database, which the app needs anyway | Broker down / queue full / poison message / consumer lag |
| Speed | A row insert in the request | Faster to return, but nothing here is slow |
| Delivery to the user | Poll, or later server-sent events | A broker does not reach browsers by itself; you would still need a push gateway |

## When it becomes better, and the order to do it in

Do **not** add a broker for its own sake. Add the cheapest thing that solves the problem actually in front of you:

1. **Email or mobile push** (slow, can fail, needs retries): first add an **outbox**. The notification row (or a
   `delivery` row next to it) is written in the same transaction; a small scheduled worker in the app reads pending
   rows (`select ... for update skip locked`, PostgreSQL), sends, retries with back-off, marks done. Per-channel
   settings go next to the type toggles in `user_preferences`. This is "a queue in the database" and already gives
   durable, at-least-once delivery with no new infrastructure. In-process decoupling, if wanted, is a
   `@TransactionalEventListener(phase = AFTER_COMMIT)`.
2. **Live updates in the browser** (no 45-second delay): server-sent events from the app. With one instance this
   needs nothing else.
3. **Several app instances, or thousands of messages a minute, or other consumers** (analytics, search indexing,
   other services): now a broker pays for itself. Keep the outbox as the producer side (the app writes to the
   database, a relay publishes to RabbitMQ), so you never lose the transactional guarantee. Alternatives to weigh
   at that point: PostgreSQL `LISTEN/NOTIFY` for wake-ups, Redis streams, or a managed queue (SQS, Pub/Sub).

Rule of thumb: the broker becomes useful when **someone other than this one process needs to consume the events**.

## Settings that exist

`GET /users/me/preferences` returns `{ notifications: {INTERVIEW, APPLICATION, SYSTEM, HIRING, ACCOUNT: boolean}, accentColor }`;
`PUT` changes only what is sent. Only deviations are stored (`user_preferences`, `user_disabled_notifications`), so a
new notification type is on for everyone without a migration. A switched-off type is **not stored at all**, so it is
not counted in the badge and cannot be seen later by switching the toggle back on.
