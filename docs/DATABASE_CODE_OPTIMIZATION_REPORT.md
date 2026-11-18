# DATABASE & SOURCE CODE OPTIMIZATION REPORT — NimoKids API

- Ngày: 2026-11-18
- Phạm vi: repo `api` (Spring Boot 3 + JPA/Hibernate + Flyway + PostgreSQL), nhánh `main` @ `fc7c3c0` (V31).
- Trạng thái (cập nhật sau review): phân tích xong, đã đo trên DB thật (mục 0.1), đã review lại từng phát hiện
  (`DATABASE_CODE_OPTIMIZATION_PLAN.md`, mục 1). **Chỉ 1 thay đổi được triển khai** — P-04, code thuần, không đổi
  schema/SQL/API/nghiệp vụ — trên nhánh `feature/db-optimization`, **chưa commit**. Mọi thay đổi SQL/index ở dạng đề xuất.

## 0. Phương pháp và giới hạn

| Nguồn | Đã dùng | Ghi chú |
|---|---|---|
| Flyway `db/migration` V1–V31 + `R__*` | Có | Dựng lại schema cuối cùng từ toàn bộ DDL (V1–V6, V8–V15, V20 là các file có DDL; các file khác chỉ là dữ liệu). |
| Entity JPA (`entity/*.java`) | Có | `spring.jpa.hibernate.ddl-auto: validate` → mọi cột entity khai báo phải tồn tại trong DB. |
| Repository (`@Query` native + JPQL), Service, Mapper, Job | Có | Grep toàn bộ tham chiếu getter / tên cột. |
| Trigger / Function / View | Có (từ migration) | 1 function + 2 trigger (V12); 1 function retention (V12). Không có VIEW, không có stored procedure nào khác trong migration. |
| **DB thật `nimokids_dev`** (PostgreSQL 16.15) | Có — chỉ đọc | Chạy toàn bộ Phụ lục A trong giao dịch read-only, rollback. Kết quả ở mục 0.1. `pg_stat_statements` chưa bật nên không có thống kê thời gian từng câu SQL. |
| Tích hợp bên ngoài | Một phần | Web (`nimoKids_web`) chỉ gọi REST API, không đọc DB trực tiếp. Không thấy batch/ETL ngoài repo, nhưng **chưa thể khẳng định** không có công cụ admin/BI đọc thẳng DB. |

Mức độ: **Critical** (sai dữ liệu / sập) · **High** (chậm, phình to, thiết kế chưa hợp lý) · **Medium** (dư thừa, có thể cải thiện) · **Low** (đặt tên, bảo trì).

**Kết luận nhanh (sau review):** không có vấn đề Critical. Còn **2 nhóm High**, đều là rủi ro *tăng trưởng*: log không có retention (C1) và ảnh base64 trong JSONB bị nạp theo mọi truy vấn chọn đáp án (Q1/C2/D7). I1 hạ xuống Medium vì `refresh_tokens` hiện chỉ 20 dòng. Hai đề xuất bị bác khi review: C5 (repository vẫn được test dùng) và nhận định "tải lần 3" trong Q2.

### 0.1 Số liệu đo trên `nimokids_dev` (2026-11-18, chỉ đọc)

- **Không có đối tượng ngoài Flyway**: đúng 23 bảng + `flyway_schema_history`; function chỉ có của `pgcrypto` + `auth_history_immutable`, `auth_apply_retention`; trigger chỉ có 2 trigger append-only (V12). Không có VIEW.
- **Kích thước**: `api_logs` 31 MB / 6.475 dòng (lớn nhất; đã tích lại ~2–3 nghìn dòng/ngày từ sau lần xóa tay 112.124 dòng — dung lượng 31 MB phần lớn là bloat sau DELETE, chưa VACUUM FULL); `session_questions` 0 dòng (đã xóa tay); `game_questions` 556; `activity_logs` 4.111; `game_sessions` 1.086; `answer_items` 439. Các bảng khác < 200 dòng.
- **Index không được dùng** (`idx_scan = 0`, thống kê chưa từng reset, nhưng đây là DB dev nên chỉ mang tính tham khảo): `idx_api_logs_created_at` (2,5 MB), `idx_api_logs_endpoint`, `idx_api_logs_player_id`, `idx_activity_logs_event_type_time`, `idx_game_questions_question_key`, `idx_game_questions_question_type`, `idx_game_sessions_player_status` và nhiều index nhỏ ở nhóm auth (bảng gần rỗng). `idx_api_logs_status_code` chỉ 4 lần.
- **Cột luôn NULL**: `anonymous_players.platform` (0/143), `api_logs.metadata` (0), `game_questions.explanation` (0). `question_stickers`: **0 dòng** (tính năng có code nhưng chưa có dữ liệu).
- **`users.email` trùng**: không có (2 user).
- **A6 lệch mẫu**: 43 câu có `age_group = AGE_4_5` trong khi mẫu là `AGE_1_3` (`question_type` thì khớp hết). Nhiều khả năng **có chủ đích** (V28: một cách hỏi dùng chung cho cả hai nhóm tuổi) → `age_group` của câu hỏi là nguồn đúng, của mẫu chỉ là mặc định. Không phải lỗi dữ liệu.
- **A7**: `answer_items.name` khớp `i18n.en.text` ở 100 % dòng.
- **A8**: 11 đáp án có ảnh, tổng 8,4 KB (ảnh màu phẳng). Rủi ro Q1 chỉ xuất hiện khi nạp ảnh thật.

---

## 1. Tổng quan kiến trúc Database

- PostgreSQL, extension `pgcrypto` (`gen_random_uuid()`), enum type `age_group ('AGE_1_3','AGE_4_5')`.
- Quy ước: PK `UUID`, thời gian `TIMESTAMPTZ`, cờ mềm `is_active`, dữ liệu mở rộng `JSONB` (`metadata`, `i18n`, `generation`, snapshot).
- Quản lý schema: 100 % qua Flyway; Hibernate chỉ `validate`. Dev nạp thêm `db/seed`.

Nhóm bảng (23 bảng):

| Nhóm | Bảng |
|---|---|
| Nội dung game | `topics`, `game_modes`, `question_templates`, `game_questions`, `answer_items`, `media_assets`, `stickers`, `question_stickers` |
| Chơi game (giao dịch) | `anonymous_players`, `game_sessions`, `session_questions`, `player_stickers` |
| Log / phân tích | `api_logs`, `activity_logs` |
| Xác thực / bảo mật | `admin_users`, `users`, `oauth_accounts`, `user_sessions`, `refresh_tokens`, `oauth_login_attempts`, `login_history`, `security_events`, `auth_settings` |

Quan hệ chính:

```
topics (parent_id → topics) ─< game_questions >─ game_modes
                                  │  ├─ template_id ───────────► question_templates
                                  │  ├─ correct_answer_item_id ► answer_items (image_id, voice_id → media_assets)
                                  │  └─ question_voice_id / object_sound_id → media_assets
anonymous_players ─< game_sessions ─< session_questions (question_id → game_questions; snapshot JSONB)
                         └─< player_stickers >─ stickers ─< question_stickers >─ game_questions
api_logs / activity_logs → anonymous_players, game_sessions (ON DELETE SET NULL)
admin_users / users ─< user_sessions ─< refresh_tokens (replaced_by_id → refresh_tokens)
users ─< oauth_accounts ; anonymous_players.user_id → users
login_history, security_events: không FK, append-only (trigger V12)
```

---

## 2. Danh sách Table

| Bảng | Tạo ở | Entity | Vai trò | Ghi chú |
|---|---|---|---|---|
| topics | V1 (+V5, V8, V20) | Topic | Cây chủ đề | |
| game_modes | V1 | GameMode | Kiểu chơi (GUESS…) | |
| media_assets | V1 | MediaAsset | Metadata file ảnh/âm thanh | `MediaAssetRepository` không được inject ở đâu |
| game_questions | V1 (+V5, V6, V10, V15) | GameQuestion | Câu hỏi = (mẫu, đáp án, mode) | |
| answer_items | V5 (+V10, V20) | AnswerItem | Từ vựng = đáp án | Ảnh base64 trong `metadata.image` (V24/V25) |
| question_templates | V10 | QuestionTemplate | Mẫu câu + i18n | |
| anonymous_players | V1 (+V11) | AnonymousPlayer | Bé chơi ẩn danh | |
| game_sessions | V1 (+V6, V8) | GameSession | Phiên chơi | |
| session_questions | V1 (+V5, V9, V13, V14) | SessionQuestion | 5 câu đóng băng | |
| stickers | V1 | Sticker | Danh mục sticker | |
| question_stickers | V1 | QuestionSticker | Sticker theo câu | Có dùng (`StickerServiceImpl`); **0 dòng** trên dev (mục 0.1) |
| player_stickers | V1 | PlayerSticker | Sticker bé đã nhận | |
| activity_logs | V1 | ActivityLog | Sự kiện phân tích | Không có retention |
| api_logs | V1 | ApiLog | Log mọi request `/api/**` | Không có retention |
| admin_users | V2→V3→V4 (+V11) | AdminUser | Tài khoản admin | |
| users | V11 | User | Tài khoản Google | |
| oauth_accounts | V11 | OAuthAccount | Liên kết Google | |
| user_sessions | V11 | AuthSession | Phiên đăng nhập | Tên entity ≠ tên bảng |
| refresh_tokens | V11 | RefreshToken | Refresh token (hash) | |
| oauth_login_attempts | V11 | OAuthLoginAttempt | State/PKCE tạm | |
| login_history | V11 (+V12 trigger) | LoginHistory | Lịch sử đăng nhập, bất biến | |
| security_events | V11 (+V12 trigger) | SecurityEvent | Sự kiện bảo mật/audit, bất biến | |
| auth_settings | V11 | AuthSettings | Chính sách (1 dòng) | |

Bảng đã xóa trong lịch sử: `question_options` (V5), `app_users`→`users`→`admin_users` (đổi tên V3/V4).

Không có bảng nào **trùng chức năng** thực sự. Cặp dễ nhầm: `admin_users` vs `users` (tách có chủ đích — admin đăng nhập mật khẩu, user đăng nhập Google); `game_sessions` vs `user_sessions` (phiên chơi vs phiên đăng nhập).

---

## 3. Danh sách Column của từng Table

Ký hiệu: **NN** = NOT NULL, **D** = default, *(v)* = được đọc trong code, *(w)* = chỉ được ghi, *(−)* = không đọc/không ghi ngoài entity. Mọi bảng (trừ ghi chú) có `id UUID PK`, `created_at`, `updated_at TIMESTAMPTZ NN D now()`.

**topics**: code VARCHAR(50) NN UQ *(v)* · name VARCHAR(100) NN *(v)* · slug VARCHAR(100) NN UQ *(v)* · description TEXT *(v)* · icon_url VARCHAR(500) *(v)* · cover_image_url VARCHAR(500) *(v)* · min_age SMALLINT NN *(v, trả về API)* · max_age SMALLINT NN *(v)* · is_active BOOL NN D true *(v)* · display_order INT NN D 0 *(v)* · parent_id UUID FK topics *(v)* · name_vi VARCHAR(100) *(v)* · description_vi TEXT *(v)* · supported_modes VARCHAR(20)[] NN D {VI,EN,VI_EN} *(v)*

**game_modes**: code VARCHAR(50) NN UQ · name VARCHAR(100) NN · description TEXT · is_active BOOL NN — tất cả *(v)*

**media_assets**: asset_type VARCHAR(30) NN CHECK · name VARCHAR(255) NN · storage_url VARCHAR(1000) NN *(v)* · mime_type VARCHAR(100) · duration_ms INT *(v)* · file_size_bytes BIGINT · metadata JSONB · is_active BOOL NN

**game_questions**: topic_id NN FK *(v)* · game_mode_id NN FK *(v)* · question_text VARCHAR(500) NN *(v, chỉ dự phòng)* · question_voice_id FK media *(v)* · object_sound_id FK media *(v)* · explanation TEXT *(−)* · difficulty SMALLINT NN 1–5 *(v, snapshot)* · min_age SMALLINT NN *(−)* · max_age SMALLINT NN *(−)* · time_limit_seconds SMALLINT NN D 8 *(v)* · is_active BOOL NN *(v)* · display_order INT NN D 0 *(−)* · metadata JSONB NN D {} *(v)* · correct_answer_item_id FK answer_items *(v)* · age_group age_group NN D AGE_1_3 *(v)* · question_key VARCHAR(50) *(v, snapshot)* · question_type VARCHAR(30) *(v, snapshot → web)* · template_id FK question_templates *(v)*

**answer_items**: code VARCHAR(100) NN UQ *(v)* · name VARCHAR(100) NN *(v)* · image_id FK media *(v)* · voice_id FK media *(v)* · metadata JSONB NN D {} *(v)* · is_active BOOL NN *(v)* · i18n JSONB NN D {} *(v)* · is_basic_english BOOL NN D true *(v)*

**question_templates**: code VARCHAR(100) NN UQ · kind VARCHAR(10) NN (TEMPLATE/CURATED) *(v, SQL)* · question_type VARCHAR(30) · age_group age_group NN · difficulty SMALLINT NN 1–3 · i18n JSONB NN *(v)* · generation JSONB NN · is_active BOOL NN

**anonymous_players**: anonymous_id UUID NN UQ *(v)* · device_type VARCHAR(30) · platform VARCHAR(30) *(−, luôn NULL)* · browser VARCHAR(100) · os VARCHAR(100) · first_seen_at / last_seen_at NN · total_games / total_questions / total_correct INT NN D 0 *(v)* · user_id FK users

**game_sessions**: session_id UUID NN UQ (id công khai) · player_id NN FK · topic_id FK (NULL = MIX) · game_mode_id NN FK · total_questions SMALLINT NN CHECK =5 · current_question_number · correct_answers · wrong_answers · timeout_answers · score (CHECK = correct_answers) · current_streak · max_streak · status VARCHAR(20) CHECK · started_at NN · finished_at · language_mode VARCHAR(10) NN D 'EN' CHECK

**session_questions**: session_id NN FK CASCADE · question_id NN FK RESTRICT · question_number SMALLINT NN 1–5 · selected_option_id UUID (không FK, id trong snapshot) · result VARCHAR(20) CHECK · response_time_ms · answered_at · options_snapshot JSONB NN (array) · presented_at · timer_started_at · question_snapshot JSONB · timer_restarted_at · timer_restarts SMALLINT NN D 0. UQ (session_id, question_number), UQ (session_id, question_id)

**stickers**: code VARCHAR(100) NN UQ · name · description · image_id FK media · rarity VARCHAR(20) CHECK · is_active

**question_stickers**: question_id NN FK CASCADE · sticker_id NN FK · result_condition VARCHAR(20) NN. UQ (question_id, sticker_id)

**player_stickers**: player_id NN FK · sticker_id NN FK · session_id FK SET NULL · earned_at NN. UQ (player_id, sticker_id)

**activity_logs**: player_id / session_id / topic_id / question_id FK SET NULL · event_type VARCHAR(50) NN CHECK · option_id UUID (FK đã bỏ ở V5) · event_time NN · duration_ms · metadata JSONB

**api_logs**: request_id UUID NN UQ · player_id / session_id FK SET NULL · http_method VARCHAR(10) NN · endpoint VARCHAR(500) NN · status_code SMALLINT NN · response_time_ms · ip_address INET · user_agent TEXT · request_size_bytes · response_size_bytes · error_code VARCHAR(100) · error_message TEXT · metadata JSONB *(w? — không thấy code ghi)*

**admin_users**: email VARCHAR(254) NN (UQ lower(email)) · password_hash VARCHAR(100) NN · role VARCHAR(20) NN CHECK · is_active · last_login_at · failed_login_count SMALLINT NN D 0 · locked_until

**users**: email VARCHAR(254) NN (**index thường**, không UNIQUE) · email_verified · display_name · avatar_url · locale · status CHECK · disabled_at · disabled_reason · deleted_at · last_login_at

**oauth_accounts**: user_id NN FK CASCADE · provider CHECK GOOGLE · provider_subject NN · email_at_link · email_verified_at_link · last_login_at. UQ (provider, provider_subject), UQ (user_id, provider)

**user_sessions** (không có `id` chung BaseEntity — entity tự khai báo): user_id / admin_user_id FK CASCADE (đúng 1 trong 2) · auth_method · status · last_seen_at · last_refreshed_at · idle_expires_at NN · absolute_expires_at NN · ended_at · ended_reason · revoked_by_admin_id FK SET NULL · login_ip / last_ip INET · ip_change_count · user_agent_login / user_agent_last VARCHAR(300) · browser · os · device_type · device_id_hash BYTEA · login_history_id UUID (không FK) · policy_snapshot JSONB NN

**refresh_tokens**: session_id NN FK CASCADE · token_hash BYTEA NN UQ · generation INT NN · status CHECK · issued_at · expires_at NN · used_at · replaced_by_id FK refresh_tokens SET NULL · issued_ip INET

**oauth_login_attempts**: state_hash BYTEA NN UQ · nonce_hash · code_verifier_enc · return_to · ip · user_agent · created_at · expires_at NN · consumed_at

**login_history** / **security_events**: xem V11 — append-only, không FK có chủ đích, retention bằng `auth_apply_retention()`.

**auth_settings**: id SMALLINT PK CHECK =1 · version · config JSONB NN · updated_at · updated_by_admin_id FK SET NULL

---

## 4. Bảng / cột trùng lặp hoặc dư thừa

| ID | Mức | Vị trí | Hiện trạng | Ảnh hưởng | Đề xuất |
|---|---|---|---|---|---|
| D1 | Medium | `game_questions.question_type` vs `question_templates.question_type` | Hai nguồn cho cùng một thông tin. Snapshot (và web — vẽ ô màu tròn cho `COLOR_RECOGNITION`) đọc cột của **game_questions**; mẫu cũng có cột riêng. | Sửa mẫu mà quên câu hỏi → hai nơi lệch nhau, giao diện hiển thị sai kiểu. | Xác định **mẫu là nguồn chuẩn**; thêm kiểm tra dữ liệu (Phụ lục A, truy vấn A6) để phát hiện lệch. Về lâu dài đọc từ mẫu, giữ cột cũ một thời gian (tương thích ngược). |
| D2 | Low | `game_questions.age_group`, `difficulty` vs `question_templates.age_group`, `difficulty` | Trùng tương tự D1; thang `difficulty` khác nhau (1–5 vs 1–3). Đo thật: 43 câu `AGE_4_5` dùng mẫu `AGE_1_3` — **có chủ đích** (một cách hỏi dùng cho cả hai nhóm tuổi). | Dễ hiểu nhầm cột nào là nguồn đúng. | Ghi rõ: `age_group` của **câu hỏi** là nguồn đúng, của mẫu chỉ là mặc định khi sinh câu. Không đổi dữ liệu. |
| D3 | Medium | `game_questions.min_age`, `max_age` | NN + CHECK nhưng **không code nào đọc**; đã được thay bằng `age_group` (V6). | Mỗi lần thêm câu phải điền 2 cột vô nghĩa; dễ hiểu nhầm là đang lọc theo tuổi. | Giai đoạn 1: thêm DEFAULT (vd 1 / 5) để insert không cần điền. Giai đoạn 2 (sau xác nhận): bỏ cột. |
| D4 | Low | `game_questions.explanation`, `display_order` | Không đọc/không dùng. | Không đáng kể. | Giữ; đánh dấu "reserved" trong tài liệu. Không xóa. |
| D5 | Medium | `game_questions.question_text` vs `question_templates.i18n.en.text` | Văn bản câu hỏi lưu 2 nơi; resolver chỉ dùng `question_text` khi không có mẫu. | Sửa lời hỏi ở mẫu nhưng `question_text` vẫn cũ (dữ liệu "mồ côi"). | Giữ làm dự phòng; không đổi. Ghi rõ nguồn chuẩn là `i18n`. |
| D6 | Medium | `answer_items.name` vs `answer_items.i18n.en.text` | Tên tiếng Anh lưu 2 nơi. `name` dùng để chống trùng tên đáp án (`OptionGeneratorImpl.normalized`). | Lệch nhau → chống trùng theo tên khác với tên hiển thị. | Giữ `name` như "mã hiển thị nội bộ"; thêm kiểm tra lệch (A7). |
| D7 | High (tương lai) | `answer_items.image_id`/`voice_id` vs `metadata.image` / `i18n.*.audio` | **Hai cơ chế ảnh**: `media_assets` (URL) và base64 trong JSONB (từ V24). Snapshot ưu tiên `metadata.image`; `DynamicQuestionServiceImpl` vẫn đọc `image_id`. | Khó biết ảnh thật của 1 đáp án nằm đâu; xem thêm Q1 về hiệu năng. | Chốt 1 cơ chế trước khi nạp 387 ảnh (xem Q1). |
| D8 | Low | `topics.name_vi` / `description_vi` | Cột theo ngôn ngữ, trong khi V10 đã chọn mô hình `i18n` JSONB cho câu hỏi/đáp án. | Không nhất quán; thêm ngôn ngữ phải thêm cột. | Giữ; nếu thêm ngôn ngữ thứ 3 thì chuyển sang `topics.i18n`. |
| D9 | Low | `game_sessions.score` (CHECK = correct_answers), `total_questions` (CHECK = 5) | Cột luôn suy ra được. | Không đáng kể; API trả `score`. | Giữ (API contract). |
| D10 | Low | `anonymous_players.total_*` | Bộ đếm phi chuẩn hóa (suy ra được từ `game_sessions`). | Có thể lệch nếu cập nhật lỗi. | Giữ (tránh aggregate mỗi lần đọc). |

---

## 5. Bảng / cột không tìm thấy tham chiếu

| Đối tượng | Kết quả tìm | Kết luận |
|---|---|---|
| `anonymous_players.platform` | Không có code ghi hay đọc | Đo dev: 0/143 dòng có giá trị. **Giữ** (chưa đo production). |
| `game_questions.explanation`, `display_order`, `min_age`, `max_age` | Không đọc | Xem D3/D4. |
| `api_logs.metadata` | Builder không set | Đo dev: 0 dòng có giá trị. Giữ. |
| `media_assets.mime_type`, `file_size_bytes`, `metadata` | Không đọc | Giữ (metadata file). |
| `MediaAssetRepository` | Không được inject | Repository thừa (Low). Bảng vẫn dùng qua quan hệ. |
| `ApiLogRepository.findByRequestId` | Không gọi trong `src/main` | Method thừa (Low); có thể test dùng — kiểm tra trước khi xóa. |
| `question_stickers` | Được đọc bởi `StickerServiceImpl` | Có dùng; đo dev: **0 dòng** (tính năng chưa có dữ liệu). Giữ. |

Nhắc lại theo yêu cầu: không đề xuất xóa bảng/cột nào chỉ dựa trên việc không thấy tham chiếu. Cần A3 (dữ liệu thật), xác nhận không có công cụ admin/BI ngoài repo, rồi mới xem xét.

---

## 6. Index và Constraint cần tối ưu

| ID | Mức | Vị trí | Hiện trạng / nguyên nhân | Ảnh hưởng | Đề xuất (chưa thực hiện) | Rollback |
|---|---|---|---|---|---|---|
| I1 | Medium — đề xuất P-03 (đã chạy thử trên DB tạm, đã rút khỏi nhánh) | `refresh_tokens.replaced_by_id` (FK tự tham chiếu, `ON DELETE SET NULL`) | **Không có index**. Job xóa token hết hạn (`deleteExpiredBefore`, mỗi giờ) và xóa phiên cũ (`deleteEndedBefore` → CASCADE) → với **mỗi dòng bị xóa**, Postgres phải quét `refresh_tokens` tìm dòng có `replaced_by_id` trỏ tới nó. | O(n·m) khi số token tăng; job dọn dẹp chậm dần, khóa bảng lâu. | `CREATE INDEX IF NOT EXISTS idx_refresh_tokens_replaced_by ON refresh_tokens (replaced_by_id) WHERE replaced_by_id IS NOT NULL;` | `DROP INDEX IF EXISTS idx_refresh_tokens_replaced_by;` |
| I2 | Low — đề xuất P-03 | `refresh_tokens (expires_at) WHERE status='ACTIVE'` | `deleteExpiredBefore` xóa mọi trạng thái theo `expires_at` → index một phần không dùng được → quét toàn bảng. | Chậm dần theo số token. | `CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);` (không đổi điều kiện xóa = không đổi nghiệp vụ). | `DROP INDEX IF EXISTS idx_refresh_tokens_expires_at;` |
| I3 | Low — đề xuất P-03 | `activity_logs.question_id`, `activity_logs.topic_id` (FK `SET NULL`) | Không có index. Xóa câu hỏi/chủ đề (V27, V31 đã làm) → quét `activity_logs` cho mỗi dòng bị xóa. | Migration xóa dữ liệu chậm khi log lớn. | Index một phần `WHERE question_id IS NOT NULL` / `topic_id IS NOT NULL`. | DROP INDEX |
| I4 | Medium | `api_logs`: `idx_api_logs_endpoint`, `idx_api_logs_created_at`, `idx_api_logs_player_id`, `idx_api_logs_status_code` | Bảng ghi nhiều nhất hệ thống (mỗi request). Không có truy vấn nào trong code dùng các index này. Đo trên dev: 3 index đầu `idx_scan = 0`, `status_code` = 4. | Mỗi INSERT cập nhật 7 index (pkey + UQ request_id + 5). | **Giữ `created_at`** — sẽ cần cho job retention (C1). Đề xuất bỏ `endpoint`, `player_id`, `status_code` **sau khi** đo trên production (dev không đại diện) và xác nhận không có công cụ ngoài dùng. | `CREATE INDEX` lại theo định nghĩa trong V1. |
| I5 | Low | `game_questions`: `idx_game_questions_age_group` (2 giá trị), `idx_game_questions_question_type` (cột không nằm trong WHERE nào), `idx_game_questions_game_mode_id` | Độ chọn lọc thấp / không có truy vấn dùng. Bảng nhỏ (~600 dòng). | Không đáng kể ở quy mô hiện tại. | Đo A2; chỉ dọn khi xác nhận `idx_scan = 0`. | Tạo lại. |
| I6 | Medium | `users.email` | Chỉ index thường (`idx_users_email_lower`), **không UNIQUE**. Danh tính duy nhất là `(provider, provider_subject)`. | Có thể có 2 user cùng email (vd đổi tài khoản Google). Nếu nghiệp vụ coi email là duy nhất thì đây là lỗ hổng toàn vẹn. | **Cần quyết định nghiệp vụ.** Kiểm tra trùng (A5) trước. Nếu cần duy nhất: `CREATE UNIQUE INDEX CONCURRENTLY … (lower(email)) WHERE deleted_at IS NULL`. | DROP INDEX |
| I7 | Low | `answer_items` GIN `jsonb_path_ops` | Phục vụ `@>` (đúng). `findActiveByCategoryAndValues` dùng `metadata->>'category' = …` → không dùng được GIN này. | Bảng ~460 dòng → không đáng kể. | Không đổi bây giờ. Nếu bảng lớn: viết lại bằng `@>`. | — |
| I8 | Low | FK không index: `answer_items.image_id/voice_id`, `game_questions.question_voice_id/object_sound_id`, `stickers.image_id`, `user_sessions.revoked_by_admin_id`, `auth_settings.updated_by_admin_id` | Chỉ ảnh hưởng khi xóa dòng cha (hiếm, bảng nhỏ). | Không đáng kể. | Không làm. | — |

Constraint đáng chú ý (không cần đổi, ghi lại để tránh hiểu nhầm):
- `session_questions` UQ `(session_id, question_id)` và `question_id NOT NULL` → mọi câu trong phiên phải là một dòng `game_questions` thật (ảnh hưởng thiết kế "chọn đáp án trước" — đã ghi trong `docs/question-generation.md`).
- `game_questions.ck_game_questions_answer_source`: phải có đáp án cố định **hoặc** `metadata.generator` — tốt.
- `login_history` / `security_events` không FK — có chủ đích (append-only).

---

## 7. SQL Query cần cải thiện

| ID | Mức | Vị trí | Hiện trạng | Đề xuất | Rủi ro |
|---|---|---|---|---|---|
| Q1 | **High** | `AnswerItemRepository.findRandomMatching` / `findRandomActive` / `findActiveByCategoryAndValues` (`SELECT *`) | Trả về cả `metadata`, trong đó có **ảnh base64** (`metadata.image`). Mỗi quy tắc đáp án sai lấy `count × 8` ứng viên (thường 24 dòng). Hiện chỉ 11 ảnh màu (V24+V25 ≈ 30 KB) nên chưa thấy. **Khi nạp 387 ảnh** (ước tính 15–60 KB/ảnh webp 512px — *ước tính, chưa đo*) → mỗi câu kéo ~0,4–1,4 MB, mỗi phiên 5 câu ~2–7 MB dữ liệu TOAST chỉ để chọn đáp án sai. | **Trước khi nạp ảnh hàng loạt**: tách ảnh ra khỏi `metadata` sang bảng riêng, vd `answer_item_images(item_id PK/FK, content_type, bytes BYTEA, etag)` — `ItemImageServiceImpl` đọc bảng này; snapshot vẫn chỉ lưu đường dẫn có id (không đổi API). Phương án nhẹ hơn: giữ trong JSONB nhưng các truy vấn chọn đáp án loại `image` (`metadata - 'image'`) — phức tạp hơn với JPA entity. | Thay đổi cấu trúc → cần migration + rollback (giữ `metadata.image` song song tới khi chuyển xong). |
| Q2 | Medium — **ĐÃ TRIỂN KHAI (P-04)** | `OptionGeneratorImpl.generate`: sau mỗi `findRandomMatching` (24 ứng viên) lại gọi `findAllWithMedia` cho **toàn bộ** ứng viên, kể cả những đáp án không được chọn và dù gần như không đáp án nào có `image_id`/`voice_id` (bảng `media_assets` đang rỗng). *(Sửa lại nhận định ban đầu: `LanguageResolverServiceImpl.loadItems` dùng `findById` từ persistence context nên **không** tạo truy vấn thứ 3.)* | Chỉ nạp media **một lần cho 4 đáp án đã chọn**, và chỉ những đáp án thật sự có `image_id`/`voice_id` (`preloadMedia`). Liên kết LAZY có khóa ngoại NULL là `null`, không phải proxy → đọc không tốn truy vấn. | Thấp: hành vi giữ nguyên (cùng URL ảnh/giọng trong snapshot). Bớt 1 truy vấn mỗi quy tắc đáp án sai (≈ 5 truy vấn mỗi lần tạo phiên với dữ liệu hiện tại). |
| Q3 | Medium — đề xuất P-06 (đã chạy thử, 3 test log đạt, đã rút khỏi nhánh vì là thay đổi SQL) | `ApiLogServiceImpl.record` | Mỗi request `/api/**` (kể cả tải ảnh `/api/v1/media/...`) = 2 SELECT (player, session) + 1 INSERT trên thread async. | **Đề xuất:** một câu `INSERT … VALUES ((SELECT …), (SELECT …)) ON CONFLICT (request_id) DO NOTHING` (`ApiLogRepository.insertLog`) → 1 round-trip; dòng trùng request id vẫn chỉ giữ 1 dòng. **Chưa làm:** không log `GET /api/v1/media/**` (thay đổi hành vi log → cần xác nhận). | Thấp. Khác biệt duy nhất: `created_at` lấy giờ DB (`now()`) thay vì giờ JVM. |
| Q4 | Low | `GameQuestionRepository.findCandidateIds` | Sub-query đếm số câu TEMPLATE chạy **cho từng dòng ứng viên** (correlated). Có index `(topic_id, game_mode_id, is_active)` phục vụ. ~600 câu → không đáng kể. | Nếu dữ liệu tăng nhiều: tính trước bằng CTE `GROUP BY topic_id, game_mode_id, age_group` rồi JOIN. | Thấp. |
| Q5 | Low | `findPlayableTopicIds` vs `findCandidateIds` | Hai truy vấn lọc **khác nhau**: "chủ đề chơi được" không xét nhóm tuổi, quy tắc TEMPLATE_ONLY_FROM, game mode cụ thể, và không trừ đáp án trùng. | Một chủ đề có thể hiện trong danh sách nhưng khi bấm lại báo không đủ câu (vd chủ đề ít câu như FLY_VEHICLES = 5). | Đây là **nghiệp vụ** → không tự sửa; ghi nhận để xử lý cùng thiết kế "chọn đáp án trước". |
| Q6 | Low | Đọc phiên (`submit-answer`, `timer-start`, `getSession`) | ~5–7 truy vấn mỗi request (player, session FOR UPDATE, danh sách session_questions, câu hỏi hiện tại để lấy `time_limit_seconds`, voice). Không phải N+1 thật (cố định 5 câu). | Có thể đưa `time_limit_seconds` vào `question_snapshot` để bỏ 1 truy vấn. | Thấp. |

---

## 8. Source Code cần refactor

| ID | Mức | Vị trí | Vấn đề | Đề xuất |
|---|---|---|---|---|
| C1 | **High** | Retention `api_logs`, `activity_logs` | Không có job nào dọn. `api_logs` đã lên **112.124 dòng** trên dev (đã xóa tay hôm 2026-11-16), rồi tích lại ~2–3 nghìn dòng/ngày. Prod sẽ tăng vô hạn; kéo theo 7 index (mục I4). | Thêm job retention giống `AuthCleanupJob` (vd giữ 30 ngày `api_logs`, 180 ngày `activity_logs`), xóa theo lô (`DELETE … WHERE id IN (SELECT id … LIMIT 5000)`) để không khóa lâu. Thời hạn lưu là **quyết định nghiệp vụ**. |
| C2 | High | Hai cơ chế ảnh (D7 + Q1) | `metadata.image` (V24) và `image_id → media_assets` cùng tồn tại; `DynamicQuestionServiceImpl` dùng cơ chế cũ. | Chốt một cơ chế trước khi nạp 387 ảnh. |
| C3 | Medium — **ĐÃ TRIỂN KHAI (P-04)** | `OptionGeneratorImpl` | Đọc lại media của mọi ứng viên (Q2). | Như Q2. |
| C4 | Medium — đề xuất P-06 | `ApiLogServiceImpl` | Q3. | Như Q3. |
| C5 | **Bác bỏ khi review** | `MediaAssetRepository` | Có dùng: `@Autowired` trong `PostgresIntegrationTest`. `ApiLogRepository.findByRequestId` cũng dùng trong `SeedAndApiLogIntegrationTest`. | Giữ nguyên. |
| C6 | Low | `GameQuestionRepository` | 5 overload `findCandidateIds` (một số chỉ cho "legacy callers"). | Gom còn 1–2 overload sau khi kiểm tra nơi gọi. |
| C7 | Low | Tên entity `AuthSession` ↔ bảng `user_sessions` | Dễ nhầm khi đọc SQL. | Chỉ ghi chú, không đổi tên (nguyên tắc 3). |

Không phát hiện: SQL nối chuỗi từ input người dùng (mọi native query dùng tham số), N+1 thật trong vòng lặp không giới hạn, hay truy vấn quét toàn bảng trên đường chơi game ở quy mô hiện tại.

---

## 9. Danh sách thay đổi đã thực hiện

**Đã triển khai (nhánh `feature/db-optimization`, worktree `E:\MY PRO\applications\NimoKids\api-wt-db-opt`, CHƯA commit):**

| File | Thay đổi | Hạng mục |
|---|---|---|
| `src/main/java/com/nimokids/service/impl/OptionGeneratorImpl.java` | Bỏ 2 lời gọi `findAllWithMedia` trong vòng chọn đáp án; thêm `preloadMedia(all)`: một truy vấn duy nhất, chỉ cho các đáp án **đã chọn** có `image_id`/`voice_id`. +17 / −4 dòng. | P-04 |
| `src/test/java/com/nimokids/service/OptionGeneratorTest.java` | +2 unit test: không truy vấn media khi không đáp án nào có media; truy vấn đúng 1 lần, chỉ cho đáp án đã chọn có media, URL ảnh/giọng giữ nguyên. | P-04 |
| `src/test/java/com/nimokids/repository/EngineIntegrationTest.java` | Thêm bean `DynamicQuestionServiceImpl`; viết lại 5 test lỗi thời theo dữ liệu hiện tại (chỉ test). +83/−55. | P-11 |

Không đổi: schema (không migration), câu SQL nào, API contract, nghiệp vụ, dữ liệu.

**Đã thử rồi rút lại (giữ làm đề xuất có bằng chứng):**
- `V32__index_foreign_keys_used_by_cleanup.sql` (P-03): chạy được trên DB tạm (bản không `CONCURRENTLY`); bản `CONCURRENTLY` làm Flyway **treo vĩnh viễn**. Rút vì là thay đổi schema.
- `ApiLogRepository.insertLog` + `ApiLogServiceImpl` (P-06): 3 test của `SeedAndApiLogIntegrationTest` đạt. Rút vì là câu SQL mới.
- `EngineIntegrationTest` thêm `DynamicQuestionServiceImpl` vào `@Import` (P-05): làm lộ 5 assertion lỗi thời → rút, chuyển thành P-11.
- Bản sao các file đã rút nằm ở thư mục tạm của phiên (`scratchpad/proposals/`), không nằm trong repo.

**Thao tác trên database:**
- `nimokids_dev`: **chỉ đọc** (Phụ lục A, giao dịch read-only + rollback). Không ghi gì.
- Tạo 2 database tạm `nimokids_optcheck_tmp_base`, `nimokids_optcheck_tmp_new` trên cùng server để chạy integration test (Flyway + seed dev). `nimokids_optcheck_tmp_new` đã được drop/tạo lại 3 lần giữa các lượt chạy (trước khi có yêu cầu giữ nguyên). **Đã xóa cả hai sau khi được xác nhận, không backup** (mục 10.3).

**Tài liệu mới (chưa commit, ở thư mục `api/`):** báo cáo này, `DATABASE_CODE_OPTIMIZATION_PLAN.md`.

## 10. Kết quả kiểm thử

Mốc unit test trước tối ưu: mục 10.1 (cuối file). Kết quả có integration test và so sánh trước/sau: mục 10.2.

## 11. Rủi ro và phương án rollback

| Thay đổi đề xuất | Rủi ro | Rollback |
|---|---|---|
| Thêm index (I1, I2, I3, I6) | Khóa ghi khi tạo index trên bảng lớn | **Đã kiểm chứng: KHÔNG dùng `CREATE INDEX CONCURRENTLY` trong Flyway của dự án này.** Flyway giữ khóa PostgreSQL trong một transaction mở; build CONCURRENTLY chờ mọi transaction kết thúc → migration (và app khi khởi động) **treo vĩnh viễn** — đã tái hiện trên DB tạm (`wait_event = virtualxid`). Bản đề xuất (P-03) dùng `CREATE INDEX IF NOT EXISTS` thường (bảng nhỏ, vài ms) — đã chạy thử đạt trên DB tạm, chưa đưa vào nhánh. Với bảng lớn sau này: tạo index CONCURRENTLY bằng tay trước, migration chỉ còn là no-op nhờ `IF NOT EXISTS`. Rollback = `DROP INDEX`. Chạy dev → staging → prod. |
| Bỏ index không dùng (I4, I5) | Công cụ ngoài đang dùng mà ta không thấy | Chỉ làm khi A2 cho `idx_scan = 0` trong đủ dài thời gian; lưu định nghĩa để tạo lại. |
| Retention log (C1) | Mất dữ liệu phân tích cũ | Cấu hình được số ngày; mặc định tắt trên prod tới khi chốt; có thể export trước khi xóa. |
| Tách ảnh ra bảng riêng (Q1/C2) | Ảnh không hiện nếu chuyển thiếu | Giữ `metadata.image` song song; `ItemImageServiceImpl` đọc bảng mới, thiếu thì rơi về JSONB; xóa JSONB chỉ ở bước cuối sau xác nhận. |
| DEFAULT cho `min_age/max_age` (D3) | Không có | `ALTER … DROP DEFAULT`. |

Mọi migration: không DROP/TRUNCATE/DELETE dữ liệu; không chạy trực tiếp trên production; theo luồng Flyway hiện có.

## 12. Các đề xuất chưa triển khai

Chi tiết từng hạng mục (giải pháp, test case, rollback): `DATABASE_CODE_OPTIMIZATION_PLAN.md`.

| ID | Nội dung | Ưu tiên | Rủi ro | Chờ |
|---|---|---|---|---|
| P-01 | Retention `api_logs` / `activity_logs` | P1 | Cao (xóa dữ liệu) | Chốt số ngày lưu |
| P-02 | Tách ảnh đáp án khỏi `metadata` trước khi nạp 387 ảnh | P1 | Trung bình | Duyệt thiết kế |
| P-03 | 4 index FK cho job dọn dẹp (không `CONCURRENTLY`) | P2 | Thấp–TB | Duyệt thay đổi schema |
| P-06 | Ghi `api_logs` 1 câu lệnh; cân nhắc không log `/media` | P2 | Trung bình | Duyệt thay đổi SQL |
| P-10 | Đồng bộ "chủ đề chơi được" với điều kiện tạo phiên | P2 | Trung bình (nghiệp vụ) | Chốt nghiệp vụ |
| P-08 | `users.email` có duy nhất không | P3 | Trung bình | Quyết định nghiệp vụ |
| P-09 | Cột trùng/không dùng ở `game_questions`, `answer_items` | P3 | Trung bình | — |
| P-07 | Dọn index không dùng | P4 | Trung bình | Số liệu production |

**Rủi ro còn tồn tại (chưa xử lý):**
1. `api_logs`, `activity_logs` tiếp tục tăng không giới hạn cho tới khi có P-01.
2. Nạp ảnh thật hàng loạt trước P-02 sẽ làm mọi truy vấn chọn đáp án kéo theo dữ liệu ảnh.
3. `EngineIntegrationTest` hỏng trên `main`; **đã sửa ở nhánh (P-11, 9/9 đạt)** nhưng chưa commit/merge. Lớp test này chạy ~23 phút với DB ở xa.
4. Integration test chỉ chạy khi có biến `DB_HOST`; trên máy Windows còn cần `-Duser.timezone=UTC` (server PostgreSQL không nhận `Asia/Saigon`). Nếu chạy với `.env` mặc định, test sẽ chạy **trên `nimokids_dev`** — nên luôn trỏ `DB_NAME` tới database tạm.
5. Không thể dùng `CREATE INDEX CONCURRENTLY` trong migration Flyway hiện tại (đã tái hiện treo).

---

## Phụ lục A — Script đo trên DB thật (chỉ đọc)

```sql
-- A1. Kích thước và số dòng ước tính
SELECT relname, n_live_tup, pg_size_pretty(pg_total_relation_size(relid)) AS total
FROM pg_stat_user_tables ORDER BY pg_total_relation_size(relid) DESC;

-- A2. Index ít / không được dùng (đo sau một thời gian chạy thật)
SELECT relname, indexrelname, idx_scan, pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes ORDER BY idx_scan, pg_relation_size(indexrelid) DESC;

-- A3. Cột luôn NULL / bảng rỗng cần xác nhận
SELECT count(*) FILTER (WHERE platform IS NOT NULL) AS platform_set FROM anonymous_players;
SELECT count(*) FILTER (WHERE metadata IS NOT NULL) AS metadata_set FROM api_logs;
SELECT count(*) AS question_stickers FROM question_stickers;
SELECT count(*) FILTER (WHERE explanation IS NOT NULL) AS explanation_set FROM game_questions;

-- A4. Đối tượng tạo ngoài Flyway (view, function, trigger, bảng lạ)
SELECT table_name, table_type FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1;
SELECT routine_name FROM information_schema.routines WHERE routine_schema = 'public';
SELECT tgname, tgrelid::regclass FROM pg_trigger WHERE NOT tgisinternal;

-- A5. Email trùng ở users
SELECT lower(email), count(*) FROM users GROUP BY 1 HAVING count(*) > 1;

-- A6. question_type / age_group lệch giữa câu hỏi và mẫu
SELECT q.id, q.question_type, t.question_type AS tpl_type, q.age_group, t.age_group AS tpl_age
FROM game_questions q JOIN question_templates t ON t.id = q.template_id
WHERE q.question_type IS DISTINCT FROM t.question_type OR q.age_group <> t.age_group;

-- A7. answer_items.name lệch i18n.en.text
SELECT code, name, i18n->'en'->>'text' AS en FROM answer_items
WHERE i18n->'en'->>'text' IS DISTINCT FROM name;

-- A8. Dung lượng ảnh trong JSONB
SELECT count(*) FILTER (WHERE metadata ? 'image') AS items_with_image,
       pg_size_pretty(sum(octet_length(metadata->>'image'))::bigint) AS image_bytes
FROM answer_items;

-- A9. Số dòng log theo tháng (để chọn thời hạn retention)
SELECT date_trunc('month', created_at) m, count(*) FROM api_logs GROUP BY 1 ORDER BY 1;
SELECT date_trunc('month', event_time) m, count(*) FROM activity_logs GROUP BY 1 ORDER BY 1;
```

### 10.1 Kết quả mốc (2026-11-18, `main` @ `fc7c3c0`, chưa có thay đổi)

- `mvn test`: **BUILD SUCCESS** — 291 test, 0 failure, 0 error, **66 skipped**.
- 66 test bị bỏ qua đều là **integration test cần PostgreSQL thật**, lý do `Environment variable [DB_HOST] does not exist`:
  `EngineIntegrationTest`, `PostgresIntegrationTest`, `SeedAndApiLogIntegrationTest`, `AuthSessionServiceIntegrationTest`,
  `GoogleLoginServiceIntegrationTest`. Nghĩa là **phần SQL native (repository) chưa được kiểm thử trong lần chạy này**;
  phải chạy lại khi có DB (đặt `DB_HOST`, …) trước và sau mỗi thay đổi ở mục 6–7.
- Các dòng `ERROR` trong log (AdminBootstrapRunner, GlobalExceptionHandler) là log mong đợi của các test kiểm tra lỗi, không phải lỗi build.

### 10.2 Kiểm thử có integration test — trước / sau tối ưu

Môi trường: PostgreSQL 16.15 , mỗi bản chạy trên **một database tạm riêng** (Flyway V1–V31 + seed dev từ
đầu), `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC`, lệnh `mvn -q test` (bản sau: `mvn -q clean test`). Không chạy trên `nimokids_dev`.

| | Trước — `main` @ `fc7c3c0` | Sau — `feature/db-optimization` (chỉ P-04) |
|---|---|---|
| Database tạm | `nimokids_optcheck_tmp_base` | `nimokids_optcheck_tmp_new` (tạo mới trước lượt chạy) |
| Build | thành công (compile) | thành công (`clean` + compile) |
| Test chạy | 291 | **293** (= 291 + 2 test mới của P-04) |
| Failure / Error | 0 / **9** | 0 / **9** |
| Skipped | 0 | 0 |
| Test lỗi | 9/9 `EngineIntegrationTest` — `NoSuchBeanDefinitionException: DynamicQuestionService` (có sẵn trên `main`) | **Đúng 9 test đó, cùng nguyên nhân** — tập lỗi so khớp từng tên với bản trước: giống hệt |
| `OptionGeneratorTest` | xanh | xanh, gồm `mediaIsNotQueriedWhenNoChosenItemHasAnImageOrAVoice` và `mediaIsLoadedOnceAndOnlyForTheChosenItemsThatHaveIt` |
| Integration test khác trên PostgreSQL (`PostgresIntegrationTest`, `SeedAndApiLogIntegrationTest`, `AuthSessionServiceIntegrationTest`, `GoogleLoginServiceIntegrationTest`) | đạt | đạt |
| Log | `scratchpad/it_base.log` | `scratchpad/it_final.log` |

Tiêu chí đạt của P-04: cùng bộ test, **không có lỗi mới** ngoài 9 lỗi có sẵn; `OptionGeneratorTest` (kể cả 2 test mới) xanh.
**Kết quả: ĐẠT.** Lệnh `mvn` trả mã thoát 1 ở **cả hai** bản vì 9 lỗi có sẵn — đây không phải build xanh; cần P-11 để bộ test xanh hoàn toàn.

Kiểm tra SQL: P-04 **không thêm hay sửa câu SQL nào**; chỉ bớt số lần gọi `findAllWithMedia` (JPQL có sẵn, không đổi).
Git diff cuối (worktree): `OptionGeneratorImpl.java` +17/−4, `OptionGeneratorTest.java` +43 — không file nào khác.

Đối chứng bổ sung đã có kết quả (lượt chạy riêng, với import P-05 thêm tạm rồi rút lại):

| `EngineIntegrationTest` (9 test) | Không có P-04 | Có P-04 |
|---|---|---|
| Đạt | 4 | 4 |
| Lỗi | 5 — cùng tập | 5 — cùng tập |
| `aWholeGameOnTheSeedPlaysThroughTheRealServices` (chơi trọn 1 ván trên PostgreSQL thật) | đạt | **đạt** |

5 lỗi chung (do assertion lỗi thời, không do P-04): `aRootTopicDrawsQuestionsFromAllItsDescendants`,
`everySeededQuestionAlwaysProducesFourDistinctOptionsFromItsOwnGroup`, `lookAlikeAnswersAreNeverOfferedAsWrongAnswers`,
`shortTemplateQuestionsAreGeneratedAndNeverOfferALookAlikeAsAWrongAnswer`, `theSeedHasFourteenPlayableRootTopicsWithTenQuestionsEach`.

Bằng chứng cho các đề xuất đã thử rồi rút (lượt chạy có V32 không-CONCURRENTLY + P-06 + P-04): 291 test, 9 lỗi — đúng
9 lỗi có sẵn; migration V32 áp thành công; 3 test của `SeedAndApiLogIntegrationTest` đạt.

### 10.3 Hai database tạm — trạng thái và phương án trước khi dọn

| Database | Kích thước | Nội dung | Kết nối (lúc kiểm tra) |
|---|---|---|---|
| `nimokids_optcheck_tmp_base` | 11 MB | Flyway V1–V31 (31 migration, tất cả success) + seed dev; thêm 20 dòng `login_history` do test auth commit | 0 |
| `nimokids_optcheck_tmp_new` | 11 MB | Flyway V1–V31 + seed dev | 20 khi test đang chạy → **0** sau khi test cuối xong |

Kiểm tra sau lượt test cuối: **cả hai database tạm 0 kết nối**, không còn được dùng.

**Đã dọn (2026-11-18 16:17 UTC), theo xác nhận của chủ dự án:**
- Backup: phương án (1) — **không backup** (dựng lại được từ Flyway V1–V31 + seed).
- Trước khi xóa: kiểm tra lại tên và kết nối — đúng `nimokids_optcheck_tmp_base`, `nimokids_optcheck_tmp_new`, cả hai 0 kết nối.
- Lệnh: `DROP DATABASE IF EXISTS <db> WITH (FORCE)` cho **đúng hai** database trên (script từ chối mọi tên khác).
- Sau khi xóa: `SELECT datname FROM pg_database WHERE datname LIKE 'nimokids_optcheck_tmp_%'` → **0 dòng**.
- `nimokids` (12 MB) và `nimokids_dev` (49 MB) **vẫn còn, không bị đụng tới** (kích thước, số kết nối trước/sau như nhau).

Không phải dữ liệu thật: cả hai **dựng lại được hoàn toàn** từ Flyway + `db/seed` tại `fc7c3c0`. Database khác trên
server (`nimokids`, `nimokids_dev`) **không thuộc phạm vi** và không bị đụng tới.

Phương án backup (chọn 1 trước khi dọn; **chưa thực hiện**):
1. **Không backup** — dựng lại bằng cách tạo database rỗng và chạy lại test (Flyway tự áp V1–V31 + seed).
2. **Backup dữ liệu bằng JDBC (chỉ đọc)** — script `scratchpad/jq/Backup.java`: `COPY … TO STDOUT` từng bảng ra CSV,
   từ chối mọi database không có tiền tố `nimokids_optcheck_tmp_`. Không cần `pg_dump` (máy này không có `pg_dump`/`psql`/Docker).
3. **`pg_dump` trên server** (nếu bạn có SSH vào máy DB): `pg_dump -Fc -d <db> -f <db>.dump` — schema + dữ liệu đầy đủ.

Lệnh dọn (chỉ chạy khi được xác nhận): `DROP DATABASE IF EXISTS <db> WITH (FORCE)` qua `scratchpad/jq/DB.java drop …`
(script chỉ chấp nhận tên có tiền tố `nimokids_optcheck_tmp_`). Điều kiện trước khi dọn: 0 kết nối vào database đó.

### 10.4 Việc cần làm sau khi lượt test cuối hoàn tất (đã làm đủ bước 1–8, 2026-11-18)

1. Đọc `it_final.log`: build, số test chạy / lỗi / skipped, danh sách test lỗi.
2. So với mốc 10.2: lỗi có nằm **đúng** trong 9 lỗi có sẵn không; `OptionGeneratorTest` có 2 test mới đạt không.
3. Điền kết quả thật vào bảng 10.2 (thay `_CHỜ KẾT QUẢ_`); nếu có lỗi mới → ghi lại, **không sửa thêm code** khi chưa trao đổi.
4. `git diff --stat` worktree lần cuối: chỉ 2 file của P-04.
5. Kiểm tra cả hai database tạm còn **0 kết nối**.
6. Báo kết quả + hỏi xác nhận: (a) phương án backup, (b) dọn hai database tạm.
7. Sau xác nhận: backup (nếu chọn) → dọn → kiểm tra không còn database `nimokids_optcheck_tmp_%` → cập nhật 10.3.
8. Không commit / push.

### 10.5 Sau P-11 (2026-11-18)

- `EngineIntegrationTest` trên DB tạm `nimokids_optcheck_tmp_p11` (Flyway V1–V31 + seed): **9 test, 0 failure, 0 error**, ~1.368 s.
- Toàn bộ unit test (không DB): 293 test, 0 failure, 0 error, 66 skipped (integration, không có `DB_HOST`).
- Git diff worktree: 3 file — `OptionGeneratorImpl.java` (P-04), `OptionGeneratorTest.java` (P-04), `EngineIntegrationTest.java` (P-11).
- Database tạm `nimokids_optcheck_tmp_p11` **vẫn còn** — chờ xác nhận để xóa.

### 10.6 Đợt 2 (2026-11-19): P-01, P-03, P-10 — kết quả kiểm thử đến thời điểm này

- Unit test (không DB): `LogRetentionJobTest` 4/4 đạt; toàn bộ unit test còn lại đạt (chạy trước khi sửa stub Mockito của test mới).
- Integration test trên `nimokids_optcheck_tmp_p11`: **bị dừng do máy thiếu bộ nhớ** sau khi V32, V33 áp thành công; chưa có test lỗi trong log. **Chưa có kết luận** cho `PostgresIntegrationTest`, `LogRetentionIntegrationTest`, `EngineIntegrationTest`.
- Theo quyết định chủ dự án: **không chạy lại test**. Integration test của P-01/P-10 vì vậy **chưa được xác nhận trên PostgreSQL** — cần chạy trước khi merge/deploy.
- JVM test mồ côi đã tự kết thúc; `nimokids_optcheck_tmp_p11` (0 kết nối) **đã xóa** 2026-11-19 theo xác nhận; `nimokids`, `nimokids_dev` không bị đụng tới; không còn database `nimokids_optcheck_tmp_%`.
- P-08: giữ nguyên theo quyết định chủ dự án. P-02: không tách ảnh.
