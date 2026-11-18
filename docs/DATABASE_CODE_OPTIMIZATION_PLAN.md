# DATABASE & CODE OPTIMIZATION — IMPLEMENTATION PLAN (NimoKids API)

- Ngày: 2026-11-18 · Nguồn: `DATABASE_CODE_OPTIMIZATION_REPORT.md` (cùng thư mục), đã review lại từng mục.
- Nhánh làm việc: `feature/db-optimization` (worktree `E:\MY PRO\applications\NimoKids\api-wt-db-opt`), tách từ `origin/main` @ `fc7c3c0`.
- **Không commit, không push.**

## 0. Ràng buộc của đợt này

Chỉ được **triển khai** thay đổi thỏa đủ 5 điều kiện:
1. không đổi Database Schema (không migration, không index, không bảng/cột);
2. không đổi API Contract;
3. không đổi Business Logic;
4. không làm mất dữ liệu;
5. kiểm chứng được bằng Unit Test / Integration Test.

Mọi thay đổi SQL, Index, Table, Column, hoặc có thể ảnh hưởng nghiệp vụ → **ĐỀ XUẤT, chưa triển khai**.

Hệ quả: hai thay đổi đã thử ở bước trước phải **rút lại** khỏi nhánh:
- `V32__index_foreign_keys_used_by_cleanup.sql` (thêm index → đổi schema);
- `ApiLogRepository.insertLog` + `ApiLogServiceImpl` (viết câu SQL mới → thay đổi SQL).
Cả hai giữ lại ở dạng đề xuất (P-03, P-06) kèm bằng chứng đã thu được khi chạy thử.

## 1. Kết quả review từng phát hiện

Cách xác minh: đọc lại code tại `fc7c3c0`, schema thật trên `nimokids_dev` (PostgreSQL 16.15, chỉ đọc), và test.

| Mục báo cáo | Kết quả review | Bằng chứng | Quyết định |
|---|---|---|---|
| C1 retention log | **Xác nhận** | Không có câu `DELETE`/job nào cho `api_logs`, `activity_logs` trong `src/main`. Dev: `api_logs` 1.728 → 2.876 → 1.873 dòng/ngày (16–18/11). | P-01 (đề xuất) |
| Q1 / C2 / D7 ảnh trong JSONB + 2 cơ chế ảnh | **Xác nhận (rủi ro tương lai)** | `findRandomMatching`/`findRandomActive`/`findActiveByCategoryAndValues` là `SELECT *` → nạp `metadata` (có `image`). `DynamicQuestionServiceImpl:123` đọc `image_id`, snapshot đọc `metadata.image`. Hiện 11 ảnh = 8,4 KB → **chưa** có tác động đo được. Con số "2–7 MB/phiên" là **ước tính**, đã ghi rõ. | P-02 (đề xuất) |
| I1 `refresh_tokens.replaced_by_id` không index | **Xác nhận**, hạ mức High → Medium | FK `ON DELETE SET NULL` có trong V11, không index nào trên cột. `deleteExpiredBefore`, `deleteEndedBefore` có thật. Nhưng bảng hiện 20 dòng → chỉ là rủi ro khi tăng trưởng. | P-03 (đề xuất) |
| I2 index `expires_at` một phần | **Xác nhận**, Low | `deleteExpiredBefore` lọc mọi status; index `WHERE status='ACTIVE'`. | P-03 (gộp) |
| I3 FK `activity_logs.question_id/topic_id` | **Xác nhận**, Low | Chỉ tác động khi xóa câu/chủ đề (chỉ có trong migration V27, V31). | P-03 (gộp) |
| Bài học `CREATE INDEX CONCURRENTLY` trong Flyway | **Xác nhận bằng thực nghiệm** | Trên DB tạm: Flyway giữ transaction `idle in transaction`, lệnh CONCURRENTLY chờ `virtualxid` vô hạn. | Ghi vào P-03 |
| Q2 / C3 nạp media của mọi ứng viên | **Xác nhận** | `OptionGeneratorImpl:76, 92` gọi `findAllWithMedia` cho toàn bộ ứng viên (24/quy tắc). Bảng `media_assets` = **0 dòng** → truy vấn luôn vô ích. | **P-04 — TRIỂN KHAI** (code thuần) |
| Báo cáo cũ: "`loadItems` tải lần 3" | **Bác bỏ** | `LanguageResolverServiceImpl.loadItems` dùng `findById` từ persistence context, không query. | Đã sửa trong báo cáo |
| Q3 / C4 ghi `api_logs` 3 round-trip | **Xác nhận** | `ApiLogServiceImpl.record`: 2 SELECT + 1 INSERT mỗi request. | P-06 (đề xuất — đổi SQL) |
| EngineIntegrationTest lỗi trên `main` | **Phát hiện mới, xác nhận** | Chạy trên DB tạm: 9/9 test lỗi `No qualifying bean of type DynamicQuestionService` — `@Import` thiếu `DynamicQuestionServiceImpl` (thêm vào `GameSessionServiceImpl` sau này). | P-05 (đã thử, rút lại) → P-11 (đề xuất) |
| I4 index `api_logs` không dùng | **Bằng chứng yếu** | `idx_scan = 0` chỉ trên DB **dev**; không đo được production. | P-07 (đề xuất, cần số liệu prod) |
| I5 index `game_questions` | **Bằng chứng yếu**, lợi ích ~0 | Bảng 556 dòng. | P-07 (gộp) |
| I6 `users.email` không UNIQUE | **Xác nhận về schema**, là câu hỏi nghiệp vụ | Không trùng trên dev (2 user). | P-08 (quyết định nghiệp vụ) |
| D1 `question_type` 2 nơi | **Xác nhận**, hiện không lệch | A6: 0 dòng lệch `question_type`. | P-09 (đề xuất tài liệu + kiểm tra) |
| D2 `age_group` 2 nơi | **Xác nhận, có chủ đích** | 43 câu `AGE_4_5` dùng mẫu `AGE_1_3`. | Không hành động (đã ghi nguồn đúng) |
| D3 `game_questions.min_age/max_age` không đọc | **Xác nhận** | Chỉ test và seed điền; `src/main` không đọc. | P-09 (gộp, đổi schema → đề xuất) |
| D5, D6 văn bản/tên lưu 2 nơi | **Xác nhận**, hiện không lệch | A7: 0 dòng lệch. | P-09 (gộp) |
| Q5 `findPlayableTopicIds` ≠ `findCandidateIds` | **Xác nhận về code**; tác động thật **chưa chứng minh** | Hai câu SQL lọc khác nhau (tuổi, TEMPLATE_ONLY_FROM, game mode). Chưa tái hiện được một chủ đề cụ thể bị lỗi. | P-10 (đề xuất, nghiệp vụ) |
| Q4 sub-query tương quan | Xác nhận, lợi ích ~0 ở 556 dòng | | Không hành động |
| Q6 lazy load `time_limit_seconds` | Xác nhận, 1 query/request | Đưa vào snapshot = đổi dữ liệu lưu → không thuộc đợt này. | Không hành động |
| C5 xóa `MediaAssetRepository` | **Bác bỏ** | Được `@Autowired` trong `PostgresIntegrationTest:57`. | Loại khỏi kế hoạch |
| C6 gom overload `findCandidateIds` | Lợi ích nhỏ, rủi ro sửa test nhiều | | Không hành động |
| D4, D8, D9, D10, C7, I7, I8 | Không phải vấn đề / có chủ đích / lợi ích ~0 | | Không hành động, giữ ghi chú trong báo cáo |

## 2. Hạng mục

Ưu tiên: **P1** làm ngay · **P2** đợt tới · **P3** khi có nhu cầu · **P4** chỉ theo dõi.
Rủi ro: **Thấp** (không đổi hành vi) · **Trung bình** (đổi SQL/schema có rollback) · **Cao** (đổi dữ liệu/nghiệp vụ).

### P-01 — Retention cho `api_logs` và `activity_logs` · ĐỀ XUẤT
- **Vấn đề:** hai bảng log tăng không giới hạn; `api_logs` ~2–3 nghìn dòng/ngày trên dev, 7 index cập nhật mỗi insert.
- **Liên quan:** bảng `api_logs`, `activity_logs`; `service/impl/AuthCleanupJob.java` (mẫu job); `application*.yml`.
- **Giải pháp:** job `@Scheduled` xóa theo lô (`DELETE … WHERE id IN (SELECT id … WHERE created_at < :cutoff LIMIT 5000)`), số ngày cấu hình được, **mặc định tắt** trên prod.
- **Ưu tiên:** P1 · **Rủi ro:** Cao (xóa dữ liệu) · **Phạm vi:** chỉ dữ liệu log; không đụng game.
- **Chờ:** chốt số ngày lưu (đề xuất 30 ngày `api_logs`, 180 ngày `activity_logs`).
- **Test case:** IT: chèn log cũ/mới → chạy job → chỉ dòng cũ bị xóa; lô > 5000 xóa nhiều vòng; `retention-days = 0` = tắt.
- **Rollback:** tắt cờ cấu hình; dữ liệu đã xóa chỉ khôi phục từ backup → export trước khi bật lần đầu.

### P-02 — Chốt một cơ chế lưu ảnh đáp án trước khi nạp ảnh hàng loạt · ĐỀ XUẤT
- **Vấn đề:** ảnh base64 nằm trong `answer_items.metadata` bị nạp cùng mọi truy vấn chọn đáp án; song song còn `image_id → media_assets`.
- **Liên quan:** `answer_items.metadata.image`, `answer_items.image_id`; `AnswerItemRepository` (3 truy vấn `SELECT *`); `ItemImageServiceImpl`; `DynamicQuestionServiceImpl:123`; `QuestionSnapshot.of`.
- **Giải pháp:** bảng `answer_item_images(item_id PK/FK, content_type, bytes BYTEA, etag)`; `ItemImageServiceImpl` đọc bảng mới, thiếu thì rơi về JSONB; URL ảnh giữ nguyên `/api/v1/media/answer-items/{id}/image` → **không đổi API**.
- **Ưu tiên:** P1 (phải làm **trước** khi nạp 387 ảnh) · **Rủi ro:** Trung bình · **Phạm vi:** ảnh câu hỏi; schema mới.
- **Test case:** IT: ảnh ở bảng mới được phục vụ, ETag/304 đúng; ảnh chỉ ở JSONB vẫn phục vụ; truy vấn chọn đáp án không còn cột ảnh; snapshot không đổi.
- **Rollback:** giữ `metadata.image` song song đến khi xác nhận; rollback = bỏ đọc bảng mới + `DROP TABLE answer_item_images`.

### P-03 — Index cho FK dùng khi dọn dẹp (I1, I2, I3) · ĐỀ XUẤT
- **Vấn đề:** xóa token/câu hỏi/chủ đề phải quét toàn bảng con cho mỗi dòng bị xóa.
- **Liên quan:** `refresh_tokens.replaced_by_id`, `refresh_tokens.expires_at`, `activity_logs.question_id`, `activity_logs.topic_id`; `RefreshTokenRepository.deleteExpiredBefore`, `AuthSessionRepository.deleteEndedBefore`.
- **Giải pháp (đã viết và chạy thử trên DB tạm, đã rút khỏi nhánh):**
  ```sql
  CREATE INDEX IF NOT EXISTS idx_refresh_tokens_replaced_by ON refresh_tokens (replaced_by_id) WHERE replaced_by_id IS NOT NULL;
  CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at  ON refresh_tokens (expires_at);
  CREATE INDEX IF NOT EXISTS idx_activity_logs_question_id ON activity_logs (question_id) WHERE question_id IS NOT NULL;
  CREATE INDEX IF NOT EXISTS idx_activity_logs_topic_id    ON activity_logs (topic_id)    WHERE topic_id IS NOT NULL;
  ```
  **Không dùng `CONCURRENTLY` trong migration Flyway** — đã tái hiện: migration treo vĩnh viễn (Flyway giữ transaction khóa mở). Bảng lớn thì tạo tay CONCURRENTLY trước, migration thành no-op nhờ `IF NOT EXISTS`.
- **Ưu tiên:** P2 · **Rủi ro:** Thấp–Trung bình (chỉ thêm) · **Phạm vi:** job dọn auth, migration xóa nội dung.
- **Test case:** IT auth (`AuthSessionServiceIntegrationTest`) xanh; `EXPLAIN` câu `DELETE FROM refresh_tokens WHERE expires_at < …` dùng index; app khởi động với migration mới không treo.
- **Rollback:** `DROP INDEX IF EXISTS …` (4 lệnh) + xóa dòng `flyway_schema_history` version tương ứng.

### P-04 — Chỉ nạp media cho 4 đáp án đã chọn · **TRIỂN KHAI**
- **Vấn đề:** `OptionGeneratorImpl.generate` gọi `findAllWithMedia` cho mọi ứng viên của mọi quy tắc (≈ 24 id/quy tắc) và cho đáp án sai do generator sinh, kể cả khi không đáp án nào có `image_id`/`voice_id` (`media_assets` hiện 0 dòng).
- **Liên quan:** `src/main/java/com/nimokids/service/impl/OptionGeneratorImpl.java`; `AnswerItemRepository.findAllWithMedia` (không đổi).
- **Giải pháp:** bỏ 2 lời gọi trong vòng lặp; thêm `preloadMedia(all)` sau khi đã chọn đủ 4 đáp án — gọi `findAllWithMedia` **một lần**, chỉ với các đáp án có `getImage() != null || getVoice() != null` (liên kết LAZY có FK NULL là `null`, đọc không tốn query).
- **Không đổi:** SQL, schema, API, thứ tự/nội dung đáp án, URL ảnh/giọng trong snapshot.
- **Ưu tiên:** P1 · **Rủi ro:** Thấp · **Phạm vi:** tạo phiên chơi (giảm 1 query mỗi quy tắc đáp án sai, ≈ 5 query/phiên với dữ liệu hiện tại).
- **Test case (unit, `OptionGeneratorTest`):**
  1. Không đáp án nào có media → `findAllWithMedia` **không** được gọi.
  2. Có đáp án mang media → gọi **đúng 1 lần**, chỉ với id của các đáp án đã chọn có media (không có ứng viên bị loại).
  3. URL ảnh/giọng của option vẫn lấy từ media (hành vi cũ).
  4. Toàn bộ test hiện có của `OptionGeneratorTest`, `GameSessionServiceTest` xanh.
  5. IT trên DB tạm: số test đạt/lỗi của nhánh **không kém** `main` gốc (mốc: 291 chạy, 9 lỗi có sẵn ở `EngineIntegrationTest`).
  6. Đối chứng end-to-end (chạy tạm với import của P-05): `EngineIntegrationTest` cho **cùng tập 5 lỗi** khi có và khi không có P-04, và `aWholeGameOnTheSeedPlaysThroughTheRealServices` đạt khi có P-04. *(Đã chạy: đạt.)*
- **Rollback:** `git revert` commit này (code thuần, không dữ liệu).

### P-05 — Sửa cấu hình `EngineIntegrationTest` (chỉ test) · **ĐÃ THỬ — RÚT LẠI theo điều kiện**

> **Kết quả thực hiện:** thêm `DynamicQuestionServiceImpl` vào `@Import` → context khởi tạo được, 4/9 test đạt (gồm
> `aWholeGameOnTheSeedPlaysThroughTheRealServices`), nhưng 5/9 lỗi vì **assertion lỗi thời so với seed hiện tại**
> (tìm câu "What farm animal lays eggs?" đã bị xóa; chờ 14 chủ đề × 10 câu; câu động không có `correctAnswerItem`).
> Đúng 5 lỗi đó xuất hiện **cả khi gỡ P-04** → không do P-04. Theo điều kiện đã đặt, import được rút lại; cần một
> việc riêng để cập nhật các assertion theo seed V16–V31 (P-11).

- **Vấn đề:** trên `main`, 9/9 test của `EngineIntegrationTest` lỗi khởi tạo context (`DynamicQuestionService` không có bean) → phần chơi game end-to-end trên PostgreSQL thật **không được kiểm thử**, kể cả cho P-04.
- **Liên quan:** `src/test/java/com/nimokids/repository/EngineIntegrationTest.java` (`@Import`).
- **Giải pháp:** thêm `DynamicQuestionServiceImpl.class` vào `@Import`. Không sửa assertion nào.
- **Điều kiện:** nếu sau khi thêm, test lộ lỗi assertion khác (dữ liệu seed đã đổi từ V16+), **không sửa assertion** trong đợt này — rút lại, ghi nhận là nợ kiểm thử.
- **Ưu tiên:** P1 · **Rủi ro:** Thấp (chỉ test) · **Phạm vi:** không đụng code chạy thật.
- **Test case:** chạy với `DB_HOST` trỏ tới **database tạm** (không phải `nimokids_dev`).
- **Rollback:** revert 1 dòng import.

### P-06 — Ghi `api_logs` bằng 1 câu lệnh · ĐỀ XUẤT
- **Vấn đề:** mỗi request `/api/**` tốn 2 SELECT + 1 INSERT.
- **Liên quan:** `ApiLogServiceImpl.record`, `ApiLogRepository`.
- **Giải pháp (đã viết, đã rút khỏi nhánh):** `INSERT … VALUES ((SELECT id FROM anonymous_players WHERE anonymous_id = …), (SELECT id FROM game_sessions WHERE session_id = …), …) ON CONFLICT (request_id) DO NOTHING`.
- **Ưu tiên:** P2 · **Rủi ro:** Trung bình (SQL mới; `created_at` lấy giờ DB thay vì giờ JVM) · **Phạm vi:** log kỹ thuật.
- **Test case:** 3 test có sẵn của `SeedAndApiLogIntegrationTest` (lưu đúng player/IP/lỗi; IP không hợp lệ & player lạ → NULL; request id trùng → 1 dòng).
- **Rollback:** revert code; không có thay đổi dữ liệu.
- **Kèm theo (cần xác nhận):** không log `GET /api/v1/media/**` thành công.

### P-07 — Dọn index không dùng (I4, I5) · ĐỀ XUẤT, P4
- Chỉ sau khi đo `pg_stat_user_indexes` trên **production** đủ lâu. Giữ `idx_api_logs_created_at` cho P-01.
- **Rủi ro:** Trung bình (công cụ ngoài có thể dùng) · **Rollback:** tạo lại theo định nghĩa V1/V6.

### P-08 — `users.email` có phải duy nhất? · QUYẾT ĐỊNH NGHIỆP VỤ, P3
- Nếu có: kiểm tra trùng (A5) → `CREATE UNIQUE INDEX … (lower(email)) WHERE deleted_at IS NULL` (tạo tay CONCURRENTLY nếu bảng lớn).
- **Rủi ro:** Trung bình (đăng nhập Google có thể bị chặn nếu trùng) · **Rollback:** `DROP INDEX`.

### P-09 — Cột trùng / không dùng ở `game_questions`, `answer_items` (D1, D3, D5, D6) · ĐỀ XUẤT, P3
- Ghi rõ nguồn chuẩn (mẫu cho `question_type`; câu hỏi cho `age_group`; `i18n` cho văn bản); thêm truy vấn A6/A7 vào kiểm tra định kỳ; DEFAULT cho `min_age/max_age` rồi mới tính chuyện bỏ cột.
- **Rủi ro:** Trung bình (schema) · **Rollback:** `ALTER … DROP DEFAULT`.

### P-10 — Đồng bộ điều kiện "chủ đề chơi được" với điều kiện tạo phiên (Q5) · ĐỀ XUẤT, P2
- Làm cùng thiết kế "chọn đáp án trước" (`docs/question-generation.md`). Là thay đổi nghiệp vụ → cần chốt.
- **Test case:** với mỗi chủ đề `GET /topics` trả về, `POST /game-sessions` cho cả 2 nhóm tuổi không được trả `INSUFFICIENT_QUESTIONS`.

### P-11 — Cập nhật `EngineIntegrationTest` theo seed hiện tại · **ĐÃ TRIỂN KHAI (2026-11-18), chưa commit**

> **Kết quả:** thêm `DynamicQuestionServiceImpl` vào `@Import`; viết lại 5 test lỗi thời để lấy kỳ vọng từ dữ liệu thay vì số cứng (giữ nguyên ý định: chủ đề cha chơi được qua chủ đề con; chủ đề gốc lấy câu từ mọi con; mọi câu có đáp án cố định sinh 4 lựa chọn khác nhau, đáp án sai thuộc đúng nhóm; không bao giờ đưa đáp án "trông giống" làm đáp án sai — 87 đáp án có tag `lookalike`). Câu động (13 câu có `metadata.generator`) được loại khỏi các vòng lặp dùng đáp án cố định. Số lần lặp giảm (20→5, 10→3, 5→3) vì DB ở xa: cả lớp test chạy ~23 phút.
> Kết quả trên DB tạm `nimokids_optcheck_tmp_p11`: **9/9 đạt**. Chỉ sửa file test, không đổi code chạy thật.
- **Vấn đề:** trên `main`, cả 9 test lỗi (thiếu bean); sửa bean thì 5 test lỗi vì dữ liệu mong đợi đã cũ.
- **Liên quan:** `src/test/java/com/nimokids/repository/EngineIntegrationTest.java`; seed `db/seed`, V16–V31.
- **Giải pháp:** thêm `DynamicQuestionServiceImpl` vào `@Import`; viết lại 5 assertion theo dữ liệu hiện tại (số chủ đề/câu, câu mẫu dùng làm ví dụ, bỏ qua câu động khi đọc `correctAnswerItem`).
- **Ưu tiên:** P2 · **Rủi ro:** Thấp (chỉ test), nhưng phải hiểu đúng ý định từng test → cần review.
- **Test case:** 9/9 xanh trên DB tạm. · **Rollback:** revert file test.

## 3. Kết quả đợt này (2026-11-18)

| Bước | Trạng thái |
|---|---|
| Rút V32 và thay đổi `api_logs` khỏi nhánh | Xong — worktree chỉ còn 2 file của P-04 |
| P-04 + 2 unit test | **Đã triển khai, chưa commit** |
| P-05 | Đã thử, rút lại theo điều kiện → P-11 |
| P-11 | **Đã triển khai, chưa commit** — `EngineIntegrationTest` 9/9 đạt |
| Validation | `main`: 291 test / 9 lỗi có sẵn · nhánh: 293 test / **cùng 9 lỗi**, không lỗi mới; 2 test mới đạt (báo cáo mục 10.2) |
| Database tạm | Đã xóa cả hai sau xác nhận, không backup; `nimokids`, `nimokids_dev` không bị đụng tới (báo cáo mục 10.3) |
| Commit / push | **Chưa** — chờ chỉ đạo |

Việc tiếp theo cần quyết định: P-01 (số ngày lưu log), P-02 (cơ chế ảnh, trước khi nạp 387 ảnh), P-03/P-06 (duyệt đổi schema/SQL), P-08, P-10 (nghiệp vụ).

## 4. Thứ tự thực hiện đợt này (kế hoạch ban đầu)

1. Rút V32 và thay đổi `api_logs` khỏi nhánh (về đúng `origin/main`).
2. P-04: giữ thay đổi `OptionGeneratorImpl`, thêm unit test.
3. P-05: thêm import, chạy IT trên DB tạm; giữ hay rút theo điều kiện.
4. Validation: `mvn test` (unit) + IT trên DB tạm cho **cả** `main` gốc và nhánh; so sánh; `git diff --stat` chỉ được chứa file của P-04/P-05.
5. Cập nhật báo cáo (mục 9, 10, 11, 12).

## 5. Đợt 2 (2026-11-19) — theo duyệt của chủ dự án

| Hạng mục | Quyết định | Trạng thái |
|---|---|---|
| P-01 retention log | Duyệt (30 ngày `api_logs`, 180 ngày `activity_logs`) | **Đã code, chưa commit**: `LogRetentionJob` (mỗi giờ, xóa theo lô 5.000, tối đa 100 lô/lần, 0 = tắt; cấu hình `app.retention.*`, env `API_LOG_RETENTION_DAYS`, `ACTIVITY_LOG_RETENTION_DAYS`) + `V33__index_log_retention.sql` (`idx_activity_logs_created_at`). Unit test 4/4 đạt; `LogRetentionIntegrationTest` (2 test) **chưa chạy xong**. |
| P-02 tách ảnh | **Không tách** | Đóng |
| P-03 index FK | Duyệt | **Đã code, chưa commit**: `V32__index_foreign_keys_used_by_cleanup.sql` (không CONCURRENTLY). Đã áp thành công lên DB tạm. |
| P-06 ghi api_logs 1 câu | Chưa duyệt | Giữ đề xuất |
| P-08 email duy nhất | **Giữ nguyên** (đăng nhập Google cố ý không liên kết theo email) | Đóng, không đổi |
| P-10 chủ đề chơi được | Duyệt | **Đã code, chưa commit**: `findPlayableTopicIds` chỉ đếm câu AGE_1_3 và đếm **từ khác nhau** (`count(DISTINCT coalesce(correct_answer_item_id, q.id))`). Dữ liệu hiện tại: không chủ đề nào thay đổi trạng thái. Test: `PostgresIntegrationTest` (+2 test, sửa 1), `EngineIntegrationTest` (helper theo quy tắc mới) — **chưa chạy xong**. |

Lượt integration test đầy đủ trên `nimokids_optcheck_tmp_p11` bị **dừng do máy thiếu bộ nhớ** (không phải lỗi test); trước khi dừng: V32, V33 áp thành công, chưa có test nào lỗi.

Kết thúc đợt 2: chủ dự án quyết định **không chạy lại test**; database tạm `nimokids_optcheck_tmp_p11` đã xóa. Còn mở: chạy integration test đầy đủ trước khi merge/deploy; commit/push chờ chỉ đạo; P-06 chờ duyệt.
