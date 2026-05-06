# Campus Notifications Microservice — System Design

---

## Stage 1: REST API Design, Contract & Real-Time Notification Mechanism

### 1.1 Core Actions

The campus notification platform must support the following core actions:

1. **Create Notification** — Send a new notification to a student (for Placement, Event, or Result)
2. **Fetch Notifications** — Retrieve all notifications for a given student
3. **Fetch Unread Notifications** — Retrieve only unread notifications for a student
4. **Mark as Read** — Mark a single notification as read
5. **Mark All as Read** — Mark all notifications for a student as read
6. **Delete Notification** — Remove a specific notification
7. **Bulk Notify** — Send a notification to multiple students at once
8. **Real-Time Streaming** — Push new notifications to connected students in real-time

### 1.2 REST API Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| `GET` | `/api/notifications?studentId={id}` | Fetch all notifications for a student | Yes |
| `GET` | `/api/notifications/unread?studentId={id}` | Fetch unread notifications for a student | Yes |
| `GET` | `/api/notifications/{notificationId}` | Fetch a single notification by ID | Yes |
| `POST` | `/api/notifications` | Create a new notification | Yes |
| `PUT` | `/api/notifications/{notificationId}/read` | Mark a notification as read | Yes |
| `PUT` | `/api/notifications/read-all?studentId={id}` | Mark all notifications as read for a student | Yes |
| `DELETE` | `/api/notifications/{notificationId}` | Delete a notification | Yes |
| `POST` | `/api/notifications/bulk` | Send notification to multiple students | Yes |
| `GET` | `/api/notifications/stream?studentId={id}` | SSE stream for real-time notifications | Yes |

### 1.3 JSON Request/Response Structures

#### Create Notification — `POST /api/notifications`

**Request Headers:**
```
Content-Type: application/json
Authorization: Bearer <token>
```

**Request Body:**
```json
{
  "studentId": 1042,
  "type": "Placement",
  "message": "Google hiring drive scheduled for May 15"
}
```

**Response (201 Created):**
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

#### Fetch Notifications — `GET /api/notifications?studentId=1042`

**Request Headers:**
```
Authorization: Bearer <token>
```

**Response (200 OK):**
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

#### Bulk Notify — `POST /api/notifications/bulk`

**Request Body:**
```json
{
  "studentIds": [1001, 1002, 1003, 1042],
  "type": "Placement",
  "message": "TCS campus drive on May 20"
}
```

**Response (202 Accepted):**
```json
{
  "batchId": "batch-uuid-123",
  "status": "QUEUED",
  "totalRecipients": 4,
  "message": "Notifications queued for delivery"
}
```

#### Mark as Read — `PUT /api/notifications/{id}/read`

**Response (200 OK):**
```json
{
  "id": "d146095a-0d86-4a34-9e69-3900a14576bc",
  "isRead": true,
  "readAt": "2026-05-06T11:00:00"
}
```

### 1.4 JSON Schema — Notification Object

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["id", "studentId", "type", "message", "isRead", "createdAt"],
  "properties": {
    "id": {
      "type": "string",
      "format": "uuid",
      "description": "Unique notification identifier"
    },
    "studentId": {
      "type": "integer",
      "description": "ID of the student receiving the notification"
    },
    "type": {
      "type": "string",
      "enum": ["Placement", "Event", "Result"],
      "description": "Category of the notification"
    },
    "message": {
      "type": "string",
      "maxLength": 500,
      "description": "Notification content"
    },
    "isRead": {
      "type": "boolean",
      "default": false,
      "description": "Whether the student has read this notification"
    },
    "createdAt": {
      "type": "string",
      "format": "date-time",
      "description": "Timestamp when the notification was created"
    }
  }
}
```

### 1.5 Real-Time Notification Mechanism — Server-Sent Events (SSE)

**Why SSE over WebSocket?**
- SSE is **unidirectional** (server → client) — perfect for notifications where the client only listens
- Simpler to implement than WebSocket (no handshake protocol)
- Built-in **auto-reconnection** in the browser
- Works over standard HTTP — no firewall issues
- Sufficient for our use case (students receive notifications, they don't send them via this channel)

**How it works:**
1. Student opens the app → client connects to `GET /api/notifications/stream?studentId=1042`
2. Server keeps the connection open using `text/event-stream` content type
3. When a new notification is created for student 1042, the server pushes it via SSE
4. Client receives the event and updates the UI in real-time

**SSE Event Format:**
```
event: notification
data: {"id":"uuid","type":"Placement","message":"Google hiring","createdAt":"2026-05-06T10:30:00"}

event: notification
data: {"id":"uuid2","type":"Result","message":"Mid-sem results","createdAt":"2026-05-06T10:31:00"}
```

---

## Stage 2: Database Design & Storage

### 2.1 Database Choice — PostgreSQL (Relational)

**Why PostgreSQL?**
- **ACID compliance** — notifications must be reliably stored and read status must be consistent
- **Rich indexing** — supports composite indexes, partial indexes, and BRIN indexes for time-series data
- **JSON support** — can store flexible metadata via `JSONB` if needed
- **Scalability features** — table partitioning, read replicas, logical replication
- **Mature ecosystem** — well-supported by Spring Boot, Hibernate, and cloud platforms

**Why not NoSQL (MongoDB)?**
- While MongoDB offers flexible schemas, our notification data has a consistent structure
- We need strong consistency for read/unread status
- Relational queries (JOINs for student info, aggregations) are simpler in SQL

### 2.2 Database Schema

```sql
-- Students table (reference)
CREATE TABLE students (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    department      VARCHAR(100),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Notifications table
CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      BIGINT NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    type            VARCHAR(20) NOT NULL CHECK (type IN ('Placement', 'Event', 'Result')),
    message         VARCHAR(500) NOT NULL,
    is_read         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMP NULL
);

-- Essential indexes
CREATE INDEX idx_notifications_student_unread
    ON notifications (student_id, is_read, created_at DESC)
    WHERE is_read = FALSE;

CREATE INDEX idx_notifications_student_created
    ON notifications (student_id, created_at DESC);

CREATE INDEX idx_notifications_type_created
    ON notifications (type, created_at DESC);
```

### 2.3 Problems at Scale & Solutions

| Problem | Solution |
|---------|----------|
| **Table bloat** (millions of rows) | **Table partitioning** by `created_at` (monthly/quarterly range partitions) |
| **Slow reads** with large data | **Partial indexes** (only index unread notifications) + **pagination** |
| **High read traffic** | **Read replicas** — route read queries to replicas |
| **Old notifications** accumulating | **Archival strategy** — move notifications older than 6 months to an archive table |
| **Connection exhaustion** | **Connection pooling** with HikariCP (Spring Boot default) |

### 2.4 SQL Queries for Stage 1 APIs

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

-- POST /api/notifications (INSERT)
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

-- POST /api/notifications/bulk (batch insert)
INSERT INTO notifications (student_id, type, message)
SELECT unnest(ARRAY[1001, 1002, 1003]), 'Placement', 'TCS campus drive';
```

---

## Stage 3: Query Optimization & Analysis

### 3.1 Analyzing the Slow Query

```sql
SELECT * FROM notifications
WHERE studentID = 1042 AND isRead = false
ORDER BY createdAt DESC;
```

**Is this query accurate?**

Yes, the query is **logically correct** — it fetches all unread notifications for student 1042, ordered by most recent first. However, it is **not optimized for performance** at scale (50,000 students, 5,000,000 notifications).

**Why is this query slow?**

1. **Full Table Scan** — With 5,000,000 rows and no appropriate index, the database must scan every row to find matches. This is O(n) where n = 5M.

2. **`SELECT *`** — Fetching all columns when only a few are needed. This increases I/O by transferring unnecessary data from disk to memory.

3. **Sorting without index** — `ORDER BY createdAt DESC` requires an in-memory or on-disk sort (O(n log n)) on the filtered result set because there's no index that provides pre-sorted data.

4. **No LIMIT** — Returns ALL matching rows. A student might have thousands of unread notifications, all returned at once.

**What would I change?**
- Replace `SELECT *` with specific columns: `SELECT id, type, message, created_at`
- Add a `LIMIT` clause (e.g., `LIMIT 20`) for pagination
- Create a composite index (see Section 3.2)

**Likely Computation Cost:**
- **Without index**: O(5,000,000) full scan + O(k log k) sort = several seconds
- **With composite index**: O(log n) index lookup + O(k) sequential read = sub-millisecond
- **I/O**: Full table scan reads hundreds of MB from disk vs index scan reads a few KB

### 3.2 Is Adding Indexes on Every Column Effective?

**No, adding indexes on every column is NOT effective.** Here's why:

1. **Write Amplification** — Every INSERT, UPDATE, or DELETE must also update ALL indexes. With 50,000 students generating notifications, this severely degrades write performance.

2. **Storage Overhead** — Each index consumes disk space. With 5M rows and indexes on every column, the total index size could exceed the table size itself.

3. **Index Maintenance** — The database must maintain B-tree structures for every index during VACUUM, REINDEX, and other maintenance operations.

4. **Diminishing Returns** — The query only filters on `studentID`, `isRead`, and sorts by `createdAt`. Indexes on `message`, `id`, etc. provide zero benefit for this query.

5. **Query Planner Confusion** — Too many indexes can cause the query planner to choose suboptimal execution plans.

**Correct Approach:** Create a **composite index** tailored to the query:
```sql
CREATE INDEX idx_student_unread_recent
    ON notifications (student_id, is_read, created_at DESC);
```

This single index covers the WHERE clause AND the ORDER BY, enabling an **index-only scan** — the fastest possible execution.

**Optimized Query:**
```sql
SELECT id, type, message, created_at
FROM notifications
WHERE student_id = 1042 AND is_read = FALSE
ORDER BY created_at DESC
LIMIT 20;
```

### 3.3 Query: Students with Placement Notifications in Last 7 Days

The table contains a `notificationType` column which accepts `notification_type` enum values. The `notification_type` enum contains `Event`, `Result`, and `Placement`.

```sql
SELECT DISTINCT student_id
FROM notifications
WHERE notificationType = 'Placement'
  AND created_at >= NOW() - INTERVAL '7 days';
```

**Supporting index:**
```sql
CREATE INDEX idx_notifications_type_recent
    ON notifications (notificationType, created_at DESC);
```

This query:
- Uses the `notificationType` enum column to filter for `'Placement'` type
- Filters by `created_at` for last 7 days
- Returns distinct student IDs (avoids duplicates if a student has multiple placement notifications)
- The composite index on `(notificationType, created_at DESC)` ensures an efficient range scan

---

## Stage 4: Caching & Performance Strategy

### Problem
Notifications are fetched on each page load for every student. With 50,000 students, this creates massive read pressure on the database, causing slow responses and bad UX.

### Solution 1: Redis Caching (Recommended — Primary Strategy)

**How it works:**
- Cache each student's unread notifications in Redis with key: `notifications:unread:{studentId}`
- On page load: check Redis first → if cache hit, return cached data → if cache miss, query DB and populate cache
- When a new notification arrives: invalidate/update the cache for that student
- TTL: 5 minutes (balances freshness vs DB load)

**Tradeoffs:**
| Pro | Con |
|-----|-----|
| 90%+ reduction in DB reads | Additional infrastructure (Redis server) |
| Sub-millisecond response times | Cache invalidation complexity |
| Handles 50K concurrent users easily | Slight data staleness (up to TTL duration) |
| | Memory cost for caching all active students |

### Solution 2: Cursor-Based Pagination

**How it works:**
- Instead of fetching ALL notifications, fetch in pages of 20
- Use cursor-based pagination: `GET /api/notifications?studentId=1042&after=<lastNotificationId>&limit=20`
- Client loads more on scroll (infinite scroll pattern)

**Tradeoffs:**
| Pro | Con |
|-----|-----|
| Reduces data transferred per request | Still hits DB on every request |
| Simple to implement | Doesn't reduce total DB query count |
| Better UX with lazy loading | Requires client-side pagination logic |

### Solution 3: Push Model with SSE (Replace Polling)

**How it works:**
- Instead of fetching notifications on every page load (polling), maintain a persistent SSE connection
- Server pushes new notifications in real-time
- Client only fetches full list once on initial load, then receives incremental updates

**Tradeoffs:**
| Pro | Con |
|-----|-----|
| Eliminates repeated DB queries | Requires persistent connections (memory per connection) |
| Real-time UX | Server must manage 50K concurrent connections |
| Reduces network traffic | More complex server infrastructure |
| | Need fallback for SSE connection drops |

### Solution 4: Database Read Replicas

**How it works:**
- Set up 1-2 read replicas of the primary database
- Route all notification read queries to replicas
- Writes (new notifications, mark as read) go to primary

**Tradeoffs:**
| Pro | Con |
|-----|-----|
| Distributes read load | Replication lag (eventual consistency) |
| No application code changes needed | Additional infrastructure cost |
| Scales horizontally for reads | Doesn't solve the fundamental query volume problem |

### Recommended Combined Strategy:
**Redis Cache + Cursor Pagination + SSE** — This combination gives:
- Fast reads via Redis (cache hit path)
- Reasonable data transfer via pagination
- Real-time updates via SSE (no polling)
- DB is only hit on cache misses and writes

---

## Stage 5: Bulk Notification & Reliability

### 5.1 Shortcomings of the Given Pseudocode

```
function notify_all(student_ids: array, message: string):
    for student_id in student_ids:
        send_email(student_id, message)   # calls Email API
        save_to_db(student_id, message)   # DB insert
        push_to_app(student_id, message)  # pushes via SSE (Server-Sent Events
                                          # as chosen in Stage 1)
```

**Critical Problems:**

1. **Sequential Processing** — Processes 50,000 students one-by-one. If each takes 100ms, total time = 5,000 seconds (~83 minutes). Completely unacceptable.

2. **No Error Handling** — If `send_email` fails at student #200, the loop crashes. Students 201-50,000 get nothing. No retry, no recovery.

3. **Tight Coupling** — Email, DB save, and push notification are synchronous and tightly coupled. If the Email API is slow, DB and push also wait.

4. **No Idempotency** — If the process restarts after failure, students 1-200 might get duplicate notifications.

5. **No Transactional Safety** — If `save_to_db` succeeds but `send_email` fails, the notification exists in DB but the student never got the email.

6. **Single Point of Failure** — One server processes everything. If it crashes, everything stops.

### 5.2 Logs Indicate send_email Failed for 200 Students Midway — What Now?

Since the loop is sequential and has no error handling, the failure at student #200 means:
- **Students 1-199**: Received email, DB insert done, push sent ✅
- **Student 200**: Email failed, but DB insert and push may or may not have executed (undefined state) ⚠️
- **Students 201-50,000**: Got **nothing** — no email, no DB record, no push ❌

**Immediate recovery steps:**

1. **Identify affected students** — From logs, extract the list of student IDs that failed (starting from #200 onwards)
2. **Check DB state** — Query the database to find which students already have the notification saved:
   ```sql
   SELECT student_id FROM notifications
   WHERE message = '<the message>' AND created_at >= '<batch start time>'
   ```
3. **Compute the delta** — Students NOT in the DB result are the ones who were never processed
4. **Retry only the missing students** — Run the notification flow again ONLY for the students who didn't receive it
5. **For student #200 specifically** — Check if the DB insert happened. If yes, only retry the email. If no, retry everything.

**This is exactly why the redesign (Section 5.4) uses a message queue with retry logic — so failures are handled automatically.**

### 5.3 Should DB Save and Email Happen Together?

**No, they should NOT happen in the same transaction.** Reasons:

1. **Different failure modes** — DB operations are fast and reliable; email API calls are slow and unreliable (network timeouts, rate limits, third-party downtime)
2. **Holding DB transactions open** while waiting for email API responses causes connection pool exhaustion
3. **Email failures should not roll back DB saves** — the notification record should exist regardless of email delivery status
4. **Separation allows independent retry** — failed emails can be retried without re-inserting DB records

**Better approach:** Save to DB first (fast, reliable), then queue the email as an async task with its own retry logic.

### 5.4 Redesigned Pseudocode

```
function notify_all(student_ids: array, message: string):
    batch_id = generate_uuid()
    
    # Step 1: Batch insert all notifications to DB (fast, single transaction)
    chunks = split_into_chunks(student_ids, chunk_size=1000)
    for chunk in chunks:
        batch_insert_to_db(chunk, message, batch_id)   # Bulk INSERT, ~50 inserts vs 50,000
    
    # Step 2: Queue async tasks for email + push delivery
    for chunk in chunks:
        enqueue_to_message_queue({
            batch_id: batch_id,
            student_ids: chunk,
            message: message,
            task_type: "DELIVER_NOTIFICATIONS"
        })

# ---- Runs on separate worker processes (consumers) ----

function process_notification_batch(task):
    for student_id in task.student_ids:
        try:
            # Email and push are independent — use parallel execution
            futures = parallel_execute([
                lambda: send_email_with_retry(student_id, task.message, max_retries=3),
                lambda: push_to_app(student_id, task.message)
            ])
            
            # Mark as delivered
            update_delivery_status(task.batch_id, student_id, "DELIVERED")
            
        except EmailFailureException as e:
            # Move to Dead Letter Queue for manual review/retry
            send_to_dead_letter_queue({
                student_id: student_id,
                message: task.message,
                error: e.message,
                retry_count: 3
            })
            update_delivery_status(task.batch_id, student_id, "EMAIL_FAILED")
            
        except PushFailureException as e:
            # Push failures are less critical — log and continue
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

**Key Improvements:**
1. **Batch DB inserts** — 50 bulk inserts instead of 50,000 individual ones
2. **Message Queue** (RabbitMQ/Kafka) — decouples production from delivery
3. **Parallel workers** — multiple consumers process chunks simultaneously
4. **Retry with exponential backoff** — handles transient email failures
5. **Dead Letter Queue** — failed emails are captured for retry/investigation
6. **Idempotency via batch_id** — prevents duplicate processing on restart
7. **Independent email + push** — one failing doesn't block the other
8. **Delivery status tracking** — know exactly which students received what

---

## Stage 6: Priority Inbox Design

### 6.1 Problem Statement

Display the top N most important **unread** notifications to a student. Priority is determined by:
1. **Type weight**: Placement > Result > Event
2. **Recency**: More recent notifications are more important

### 6.2 Priority Score Calculation

```
priority_score = (type_weight × 10,000,000) + recency_score

Type weights:
  Placement = 3   (highest — directly impacts career)
  Result    = 2   (medium — academic updates)
  Event     = 1   (lowest — informational)

Recency score:
  epoch_seconds of the notification timestamp
  (higher epoch = more recent = higher score)
```

By multiplying type_weight by 10,000,000 (a large constant), we ensure type always dominates over recency. Within the same type, recency breaks ties.

### 6.3 Efficient Top-N Maintenance

**Data Structure: Min-Heap (PriorityQueue) of size N**

- Maintain a min-heap of size N
- For each notification: if its priority > heap's minimum, replace the min and re-heapify
- Final result: extract all N elements from the heap (sorted by priority descending)

**Time Complexity:** O(n log N) where n = total notifications, N = top count
**Space Complexity:** O(N)

This is optimal because:
- We don't need to sort ALL notifications (O(n log n))
- We only maintain the top N at any time
- New notifications can be added incrementally without reprocessing everything

### 6.4 Implementation

See the working code in the `notification_app_be/` folder.

The implementation:
1. Fetches notifications from `http://20.207.122.201/evaluation-service/notifications`
2. Computes priority score for each notification
3. Uses a PriorityQueue (Min-Heap) to efficiently find the top N
4. Returns them sorted by priority (highest first)

### 6.5 Handling Continuous New Notifications

As new notifications keep arriving:
- The min-heap naturally handles this — simply offer each new notification to the heap
- If the heap is full (size = N) and new item has higher priority than the heap's min → poll min, offer new item
- This maintains the top N at all times with O(log N) per new notification
- No need to re-scan or re-sort the entire collection
