# Notification System Design

## Stage 1

### Core Actions

I identified the following core actions that the notification platform needs to support:

1. Create Notification - Send a notification to a student (Placement, Event, or Result)
2. Fetch Notifications - Get all notifications for a student
3. Fetch Unread Notifications - Get only unread ones
4. Mark as Read - Mark one notification as read
5. Mark All as Read - Mark all notifications as read for a student
6. Delete Notification - Remove a notification
7. Bulk Notify - Send notification to multiple students at once
8. Real-Time Streaming - Push notifications to students in real time

### REST API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/notifications?studentId={id}` | Fetch all notifications for a student |
| GET | `/api/notifications/unread?studentId={id}` | Fetch unread notifications |
| GET | `/api/notifications/{notificationId}` | Fetch a single notification |
| POST | `/api/notifications` | Create a new notification |
| PUT | `/api/notifications/{notificationId}/read` | Mark notification as read |
| PUT | `/api/notifications/read-all?studentId={id}` | Mark all as read |
| DELETE | `/api/notifications/{notificationId}` | Delete a notification |
| POST | `/api/notifications/bulk` | Bulk notify multiple students |
| GET | `/api/notifications/stream?studentId={id}` | SSE stream for real-time |

All endpoints require `Authorization: Bearer <token>` header.

### JSON Request/Response Structures

**Create Notification - POST /api/notifications**

Request:
```json
{
  "studentId": 1042,
  "type": "Placement",
  "message": "Google hiring drive scheduled for May 15"
}
```

Response (201 Created):
```json
{
  "id": "d146095a-0d86-4a34-9e69-3900a14576bc",
  "studentId": 1042,
  "type": "Placement",
  "message": "Google hiring drive scheduled for May 15",
  "isRead": false,
  "createdAt": "2026-05-06T10:30:00"
}
```

**Fetch Notifications - GET /api/notifications?studentId=1042**

Response (200 OK):
```json
{
  "notifications": [
    {
      "id": "d146095a-0d86-4a34-9e69-3900a14576bc",
      "studentId": 1042,
      "type": "Placement",
      "message": "Google hiring drive scheduled for May 15",
      "isRead": false,
      "createdAt": "2026-05-06T10:30:00"
    },
    {
      "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "studentId": 1042,
      "type": "Result",
      "message": "Mid-sem results published",
      "isRead": true,
      "createdAt": "2026-05-05T09:00:00"
    }
  ],
  "totalCount": 2,
  "unreadCount": 1
}
```

**Bulk Notify - POST /api/notifications/bulk**

Request:
```json
{
  "studentIds": [1001, 1002, 1003, 1042],
  "type": "Placement",
  "message": "TCS campus drive on May 20"
}
```

Response (202 Accepted):
```json
{
  "batchId": "batch-uuid-123",
  "status": "QUEUED",
  "totalRecipients": 4,
  "message": "Notifications queued for delivery"
}
```

**Mark as Read - PUT /api/notifications/{id}/read**

Response (200 OK):
```json
{
  "id": "d146095a-0d86-4a34-9e69-3900a14576bc",
  "isRead": true,
  "readAt": "2026-05-06T11:00:00"
}
```

### JSON Schema for Notification Object

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["id", "studentId", "type", "message", "isRead", "createdAt"],
  "properties": {
    "id": { "type": "string", "format": "uuid" },
    "studentId": { "type": "integer" },
    "type": { "type": "string", "enum": ["Placement", "Event", "Result"] },
    "message": { "type": "string", "maxLength": 500 },
    "isRead": { "type": "boolean", "default": false },
    "createdAt": { "type": "string", "format": "date-time" }
  }
}
```

### Real-Time Notification Mechanism

I chose Server-Sent Events (SSE) for real-time notifications.

Why SSE instead of WebSocket:
- SSE is unidirectional (server to client) which is exactly what notifications need - students just receive, they don't send through this channel
- It's simpler than WebSocket since there's no handshake protocol
- Browsers have built-in auto-reconnection for SSE
- Works over standard HTTP so no firewall issues

How it works:
1. Student opens the app and the client connects to `GET /api/notifications/stream?studentId=1042`
2. Server keeps the connection open using `text/event-stream` content type
3. When a new notification is created for that student, the server pushes it through SSE
4. Client receives it and updates the UI immediately

SSE event format:
```
event: notification
data: {"id":"uuid","type":"Placement","message":"Google hiring","createdAt":"2026-05-06T10:30:00"}
```

---

## Stage 2

### Database Choice - PostgreSQL

I'm going with PostgreSQL because:
- It has ACID compliance which we need since notification read/unread status must be consistent
- It supports composite indexes, partial indexes, and BRIN indexes which will help with performance
- It has JSONB support if we need flexible metadata later
- It supports table partitioning and read replicas for scaling
- It works well with Spring Boot and Hibernate

I considered MongoDB but went against it because our notification data has a fixed structure, we need strong consistency for read/unread state, and relational queries like JOINs for student info are easier in SQL.

### Database Schema

```sql
CREATE TABLE students (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    department      VARCHAR(100),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    type            VARCHAR(20) NOT NULL CHECK (type IN ('Placement', 'Event', 'Result')),
    message         VARCHAR(500) NOT NULL,
    is_read         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMP NULL
);

-- Indexes
CREATE INDEX idx_notifications_student_unread
    ON notifications (student_id, is_read, created_at DESC)
    WHERE is_read = FALSE;

CREATE INDEX idx_notifications_student_created
    ON notifications (student_id, created_at DESC);

CREATE INDEX idx_notifications_type_created
    ON notifications (type, created_at DESC);
```

### Problems at Scale and Solutions

As data grows, these problems can come up:

1. **Table bloat** (millions of rows) - I'd use table partitioning by `created_at` (monthly or quarterly range partitions)
2. **Slow reads** - Use partial indexes (only index unread notifications) and add pagination
3. **High read traffic** - Set up read replicas and route read queries to them
4. **Old notifications piling up** - Move notifications older than 6 months to an archive table
5. **Connection exhaustion** - Use connection pooling with HikariCP (Spring Boot default)

### SQL Queries for Stage 1 APIs

```sql
-- GET /api/notifications?studentId=1042
SELECT * FROM notifications
WHERE student_id = 1042
ORDER BY created_at DESC
LIMIT 50 OFFSET 0;

-- GET /api/notifications/unread?studentId=1042
SELECT * FROM notifications
WHERE student_id = 1042 AND is_read = FALSE
ORDER BY created_at DESC;

-- GET /api/notifications/{id}
SELECT * FROM notifications WHERE id = 'd146095a-...';

-- POST /api/notifications
INSERT INTO notifications (student_id, type, message)
VALUES (1042, 'Placement', 'Google hiring drive')
RETURNING *;

-- PUT /api/notifications/{id}/read
UPDATE notifications
SET is_read = TRUE, read_at = CURRENT_TIMESTAMP
WHERE id = 'd146095a-...';

-- PUT /api/notifications/read-all?studentId=1042
UPDATE notifications
SET is_read = TRUE, read_at = CURRENT_TIMESTAMP
WHERE student_id = 1042 AND is_read = FALSE;

-- DELETE /api/notifications/{id}
DELETE FROM notifications WHERE id = 'd146095a-...';

-- POST /api/notifications/bulk
INSERT INTO notifications (student_id, type, message)
SELECT unnest(ARRAY[1001, 1002, 1003]), 'Placement', 'TCS campus drive';
```

---

## Stage 3

### Analyzing the Slow Query

The given query:
```sql
SELECT * FROM notifications
WHERE studentID = 1042 AND isRead = false
ORDER BY createdAt DESC;
```

**Is this query accurate?**

Yes, the query is logically correct - it fetches all unread notifications for student 1042 ordered by most recent first. But it's not optimized for performance at the current scale (50,000 students, 5,000,000 notifications).

**Why is it slow?**

1. **Full Table Scan** - Without a proper index, the DB has to scan all 5 million rows to find matches. That's O(n) where n = 5M.
2. **SELECT \*** - Fetching all columns when we probably only need a few. Extra I/O for unnecessary data.
3. **No index on ORDER BY** - `ORDER BY createdAt DESC` needs an in-memory sort (O(k log k)) since there's no index providing sorted data.
4. **No LIMIT** - Returns every matching row. If a student has thousands of unread notifications, all get returned at once.

**What I would change:**
- Replace `SELECT *` with specific columns: `SELECT id, type, message, created_at`
- Add `LIMIT 20` for pagination
- Create a composite index (see below)

**Computation cost:**
- Without index: O(5,000,000) full scan + O(k log k) sort = several seconds
- With composite index: O(log n) index lookup + O(k) sequential read = sub-millisecond
- The I/O difference is huge - full table scan reads hundreds of MB vs index scan reads a few KB

### Is Adding Indexes on Every Column Effective?

No, adding indexes on every column is not a good idea. Here's why:

1. **Write Amplification** - Every INSERT, UPDATE, DELETE has to update ALL the indexes too. With 50,000 students generating notifications constantly, this will kill write performance.
2. **Storage Overhead** - Each index takes disk space. With 5M rows, total index size could become bigger than the table itself.
3. **Index Maintenance** - DB has to maintain B-tree structures for every index during VACUUM, REINDEX and other maintenance.
4. **Diminishing Returns** - This query only filters on `studentID`, `isRead`, and sorts by `createdAt`. Indexes on `message`, `id`, etc. give zero benefit here.
5. **Query Planner Confusion** - Too many indexes can confuse the query planner into picking suboptimal plans.

The right approach is a composite index tailored to the query:
```sql
CREATE INDEX idx_student_unread_recent
    ON notifications (student_id, is_read, created_at DESC);
```

This one index covers both the WHERE clause and ORDER BY, enabling an index-only scan.

Optimized query:
```sql
SELECT id, type, message, created_at
FROM notifications
WHERE student_id = 1042 AND is_read = FALSE
ORDER BY created_at DESC
LIMIT 20;
```

### Query: Students with Placement Notifications in Last 7 Days

The table contains a `notificationType` column which accepts `notification_type` enum values. The `notification_type` enum contains `Event`, `Result`, and `Placement`.

```sql
SELECT DISTINCT student_id
FROM notifications
WHERE notificationType = 'Placement'
  AND created_at >= NOW() - INTERVAL '7 days';
```

Supporting index:
```sql
CREATE INDEX idx_notifications_type_recent
    ON notifications (notificationType, created_at DESC);
```

This uses the `notificationType` enum column to filter for Placement type, filters by `created_at` for the last 7 days, and returns distinct student IDs to avoid duplicates.

---

## Stage 4

### Problem

Notifications are fetched on each page load for every student. With 50,000 students, the DB is getting overwhelmed which causes bad UX.

### Solution 1: Redis Caching

How it works:
- Cache each student's unread notifications in Redis with key like `notifications:unread:{studentId}`
- On page load, check Redis first. If cache hit, return cached data. If miss, query DB and populate cache.
- When a new notification comes in, invalidate the cache for that student
- Set TTL of 5 minutes to balance freshness vs DB load

Tradeoffs:
| Pro | Con |
|-----|-----|
| 90%+ reduction in DB reads | Need to run a Redis server (extra infra) |
| Sub-millisecond response times | Cache invalidation can get complex |
| Handles 50K concurrent users easily | Data might be slightly stale (up to TTL) |
| | Memory cost for caching all active students |

### Solution 2: Cursor-Based Pagination

How it works:
- Instead of fetching ALL notifications, fetch in pages of 20
- Use cursor-based pagination: `GET /api/notifications?studentId=1042&after=<lastId>&limit=20`
- Client loads more on scroll (infinite scroll)

Tradeoffs:
| Pro | Con |
|-----|-----|
| Reduces data per request | Still hits DB on every request |
| Simple to implement | Doesn't reduce total query count |
| Better UX with lazy loading | Client needs pagination logic |

### Solution 3: Push Model with SSE (Replace Polling)

How it works:
- Instead of fetching on every page load, keep a persistent SSE connection
- Server pushes new notifications in real time
- Client only fetches full list once on initial load, then gets incremental updates

Tradeoffs:
| Pro | Con |
|-----|-----|
| Eliminates repeated DB queries | Needs persistent connections (memory per connection) |
| Real-time UX | Server must handle 50K concurrent connections |
| Less network traffic | More complex infrastructure |
| | Need fallback for connection drops |

### Solution 4: Database Read Replicas

How it works:
- Set up 1-2 read replicas of the primary DB
- Route read queries to replicas
- Writes go to primary only

Tradeoffs:
| Pro | Con |
|-----|-----|
| Distributes read load | Replication lag (eventual consistency) |
| No app code changes needed | Extra infrastructure cost |
| Scales reads horizontally | Doesn't fix the fundamental query volume issue |

### My Recommendation

I'd go with Redis Cache + Cursor Pagination + SSE combined. This gives fast reads through Redis, reasonable data transfer through pagination, real-time updates through SSE, and the DB only gets hit on cache misses and writes.

---

## Stage 5

### Shortcomings of the Given Pseudocode

```
function notify_all(student_ids: array, message: string):
    for student_id in student_ids:
        send_email(student_id, message)   # calls Email API
        save_to_db(student_id, message)   # DB insert
        push_to_app(student_id, message)  # pushes via SSE (as chosen in Stage 1)
```

Problems I see:

1. **Sequential Processing** - Goes through 50,000 students one by one. At 100ms each, that's 5,000 seconds (~83 minutes). Way too slow.
2. **No Error Handling** - If `send_email` fails at student #200, the loop crashes. Students 201 to 50,000 get nothing.
3. **Tight Coupling** - Email, DB, and push are all synchronous. If the email API is slow, everything waits.
4. **No Idempotency** - If the process restarts after a failure, students 1-200 might get duplicate notifications.
5. **No Transactional Safety** - If `save_to_db` works but `send_email` fails, the notification is in the DB but the student never got the email.
6. **Single Point of Failure** - One server does everything. If it crashes, everything stops.

### send_email Failed for 200 Students Midway - What Now?

Since the loop is sequential with no error handling, the failure at student #200 means:
- Students 1-199: Got email, DB insert done, push sent
- Student 200: Email failed, DB insert and push may or may not have run (undefined state)
- Students 201-50,000: Got nothing at all

What I'd do to recover:

1. Check the logs to identify which student IDs failed
2. Query the DB to find which students already have the notification:
   ```sql
   SELECT student_id FROM notifications
   WHERE message = '<the message>' AND created_at >= '<batch start time>'
   ```
3. Find the students NOT in the DB result - those are the ones who missed it
4. Run the notification flow again only for those missing students
5. For student #200 specifically, check if the DB insert happened. If yes, just retry the email. If not, retry everything.

This is exactly why we need the redesigned approach below.

### Should DB Save and Email Happen Together?

No, they should not happen in the same transaction. Reasons:

1. DB operations are fast and reliable. Email API calls are slow and unreliable (timeouts, rate limits, downtime).
2. Keeping a DB transaction open while waiting for an email API response will exhaust the connection pool.
3. If an email fails, we shouldn't roll back the DB save - the notification record should exist regardless.
4. Separating them lets us retry failed emails independently without re-inserting DB records.

Better approach: Save to DB first (fast, reliable), then queue the email as an async task with its own retry logic.

### Redesigned Pseudocode

```
function notify_all(student_ids: array, message: string):
    batch_id = generate_uuid()
    
    # Step 1: Batch insert all notifications to DB (fast, single transaction)
    chunks = split_into_chunks(student_ids, chunk_size=1000)
    for chunk in chunks:
        batch_insert_to_db(chunk, message, batch_id)
    
    # Step 2: Queue async tasks for email + push delivery
    for chunk in chunks:
        enqueue_to_message_queue({
            batch_id: batch_id,
            student_ids: chunk,
            message: message,
            task_type: "DELIVER_NOTIFICATIONS"
        })

# Runs on separate worker processes
function process_notification_batch(task):
    for student_id in task.student_ids:
        try:
            # Email and push run in parallel
            futures = parallel_execute([
                lambda: send_email_with_retry(student_id, task.message, max_retries=3),
                lambda: push_to_app(student_id, task.message)
            ])
            update_delivery_status(task.batch_id, student_id, "DELIVERED")
            
        except EmailFailureException as e:
            send_to_dead_letter_queue({
                student_id: student_id,
                message: task.message,
                error: e.message,
                retry_count: 3
            })
            update_delivery_status(task.batch_id, student_id, "EMAIL_FAILED")
            
        except PushFailureException as e:
            update_delivery_status(task.batch_id, student_id, "PUSH_FAILED")

function send_email_with_retry(student_id, message, max_retries):
    for attempt in range(max_retries):
        try:
            send_email(student_id, message)
            return SUCCESS
        except TransientError:
            wait(exponential_backoff(attempt))   # 1s, 2s, 4s
    raise EmailFailureException("Failed after max retries")
```

Key improvements over the original:
1. Batch DB inserts - 50 bulk inserts instead of 50,000 individual ones
2. Message Queue (like RabbitMQ or Kafka) decouples production from delivery
3. Multiple worker processes handle chunks in parallel
4. Retry with exponential backoff for transient email failures
5. Dead Letter Queue captures permanently failed emails for review
6. batch_id ensures idempotency and prevents duplicates on restart
7. Email and push are independent - one failing doesn't block the other
8. Delivery status tracking so we know exactly which students received what

---

## Stage 6

### Problem

We need a Priority Inbox that shows the top N most important unread notifications. Priority depends on:
1. Type weight: Placement > Result > Event
2. Recency: More recent notifications should rank higher

### Priority Score Calculation

```
priority_score = (type_weight * 10,000,000) + recency_score
```

Type weights:
- Placement = 3 (highest, directly impacts career)
- Result = 2 (medium, academic updates)
- Event = 1 (lowest, informational)

Recency score = epoch seconds of the notification timestamp

By multiplying type_weight by 10,000,000 (a large constant), the type always dominates over recency. Within the same type, the more recent one ranks higher.

### How I'm Finding Top N Efficiently

I'm using a Min-Heap (PriorityQueue) of size N.

How it works:
- Go through each notification and compute its priority score
- If the heap has fewer than N items, just add it
- If the heap is full and the new item has a higher priority than the heap's minimum, remove the min and add the new item
- At the end, the heap contains exactly the top N notifications

Time Complexity: O(n log N) where n = total notifications, N = top count
Space Complexity: O(N)

This is better than sorting all notifications (which would be O(n log n)) because we only care about the top N.

### Implementation

The working code is in the `notification_app_be/` folder. It's a Spring Boot app that:
1. Fetches notifications from `http://20.207.122.201/evaluation-service/notifications`
2. Computes priority score for each notification
3. Uses a PriorityQueue (Min-Heap) to find the top N
4. Returns them sorted by priority (highest first)

Endpoint: `GET /api/notifications/priority?n=10`

### Handling New Notifications

As new notifications keep arriving, the min-heap handles this naturally:
- For each new notification, compute its priority
- If its priority > heap's current minimum, swap them
- This maintains the top N at all times with O(log N) per new notification
- No need to re-scan or re-sort the entire collection
