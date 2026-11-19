# NimoKids — Nghiệp vụ (một file duy nhất)

> **Quy ước:** đây là nơi DUY NHẤT ghi nghiệp vụ. Mỗi khi nghiệp vụ thay đổi (code, dữ liệu hoặc quyết định của chủ dự án)
> phải sửa file này trong cùng lần làm và thêm một dòng vào mục 12 "Nhật ký thay đổi".
> Trạng thái: ✅ đang chạy · 🟡 đã chốt, chưa code · 🔧 đang làm dở · ❓ chưa chốt.

## 1. Tổng quan
- Game học cho bé 1–5 tuổi: bé chọn chủ đề → trả lời 5 câu trắc nghiệm (4 lựa chọn) → xem kết quả.
- Chơi không cần đăng nhập (người chơi ẩn danh). Đăng nhập Google là tùy chọn, dành cho **ba mẹ** để lưu điểm và cột mốc.
- **Backend quyết định đúng/sai, điểm, chuỗi đúng và hết giờ; giao diện chỉ hiển thị.**
- Hai repo: `web` (React/Vite) và `api` (Spring Boot, Flyway, PostgreSQL). Database dev: `nimokids_dev`.

## 2. Nhóm tuổi và chế độ ngôn ngữ
- Nhóm tuổi: `AGE_1_3`, `AGE_4_5`. ✅ Với bé 4–5 tuổi, câu hỏi trộn theo mẫu `HEHEH` (H = câu 4–5 tuổi, E = câu 1–3 tuổi):
  60 % khó, 40 % ôn dễ; hết một nhóm thì nhóm kia lấp chỗ.
- Chế độ ngôn ngữ: VI / EN / VI_EN. Chủ đề lọc theo `topics.supported_modes`.
- Câu hỏi ngắn theo mẫu ("Đây là con gì?") là chuẩn cho bé nhỏ. ✅ Chủ đề có từ 8 câu mẫu trở lên (mỗi nhóm tuổi) thì không
  hiện câu dài viết tay nữa (`TEMPLATE_ONLY_FROM = 8`); câu dài vẫn nằm trong DB.

## 3. Chọn chủ đề
- Cây chủ đề: chủ đề được bấm + các chủ đề con đang hoạt động. ✅
- Chủ đề chỉ hiện để chơi khi có **ít nhất 5 từ khác nhau** hợp lệ (đúng nhóm tuổi `AGE_1_3`). ✅ (P-10)
  🔧 Ngoại lệ Q4 (mục 4.4): chủ đề con có ≥1 từ riêng mà chủ đề cha có ≥5 từ trong cả cây vẫn chơi được, phần thiếu lấy từ chủ đề cha.
  Chủ đề cha như MATH, VEHICLES chơi được qua các chủ đề con.

## 4. Tạo câu hỏi — game chính (GUESS)
Trạng thái: **🔧 giai đoạn 1 (chọn đáp án trước) đã code, chờ test và commit**; Q4 (đáp án sai từ chủ đề cha) chưa code.
Cờ bật/tắt: `app.question-selection.answer-first` (biến môi trường `QUESTION_SELECTION_ANSWER_FIRST`, mặc định true); false = cách cũ (chọn câu hỏi trước).

### 4.1 Quy tắc đã chốt
1. **Câu hỏi hiểu biết luôn kèm hình.** Hình là hình của **đáp án đúng**: `answer_items.metadata.image` (ảnh thật) → chưa có thì
   `metadata.emoji` (tạm thời). Có ảnh thì nạp vào `metadata.image`, không đổi code.
2. **Mỗi đáp án thuộc 1 chủ đề, 1 type và 1 mẫu câu hỏi.** Ví dụ `LAND_ANIMALS_BEAR` → LAND_ANIMALS → IMAGE_TO_WORD → `WHAT_ANIMAL`.
3. **Mỗi câu có 4 lựa chọn: 1 đúng + 3 sai** theo `distractor_rules` của câu (cùng `category`, không trùng tên, không thể cũng đúng;
   đồ vật giống nhau `lookalike` không bao giờ là đáp án sai của nhau), rồi xáo vị trí. ✅
4. 🔧 **Chọn đáp án đúng trước, rồi truy ngược ra câu hỏi** (mẫu nhận biết hình). Một đáp án nhiều câu hỏi → bốc ngẫu nhiên 1.
   Lý do: bé học **từ**, mỗi từ có cơ hội ngang nhau, thêm cách hỏi không làm lệch tỉ lệ.
5. **Ngoại lệ — chọn câu hỏi trước:** phép tính (`CALCULATE`, `MATH_QUICK*`) và câu động (`HOW_MANY` / COUNT_OBJECTS, đáp án do code sinh) — trong code là mọi câu có `metadata.generator`; mỗi câu như vậy tính là một "từ" riêng.
6. **Một từ chỉ hỏi 1 lần trong một phiên.**
7. **Đóng băng:** 5 câu + 4 lựa chọn + vị trí lưu vào `session_questions` lúc tạo phiên; sửa dữ liệu sau chỉ có hiệu lực từ phiên mới. ✅
8. Lời hỏi và giọng đọc lấy từ `question_templates.i18n`; tên đáp án từ `answer_items.i18n`.

### 4.2 Luồng khi bé bấm chủ đề (theo hướng đã chốt)
1. Cây chủ đề (mục 3).
2. Nguồn A (mẫu có đáp án cố định: WHAT_ANIMAL / WHAT_COLOR / WHAT_SHAPE / WHAT_NUMBER): danh sách ĐÁP ÁN ĐÚNG có ≥1 câu hợp lệ.
   Nguồn B (phép tính / câu động): danh sách CÂU HỎI như hiện tại.
3. Lặp tới đủ 5 câu: nguồn A bốc 1 đáp án chưa hỏi rồi 1 câu của nó; nguồn B bốc câu; sinh 3 đáp án sai
   (`OptionGeneratorImpl`); thiếu đáp án sai thì bỏ và bốc cái khác.
4. Nhóm tuổi 4–5 giữ mẫu `HEHEH`, áp dụng theo đáp án.
5. Đóng băng vào `session_questions`.

### 4.3 Bảng liên quan
| Bảng | Vai trò |
|---|---|
| `topics` | Cây chủ đề (`parent_id`), `supported_modes` |
| `answer_items` | Từ vựng (đáp án đúng/sai); `metadata` (category, image, emoji), `i18n`, `is_basic_english` |
| `game_questions` | Cặp (đáp án, mẫu, game mode): `correct_answer_item_id`, `template_id`, `game_mode_id`, `age_group`, `metadata.distractor_rules` |
| `question_templates` | Mẫu câu: `question_type`, `i18n` (lời hỏi + audio), `generation` |
| `game_modes` | Kiểu chơi (GUESS, ANIMAL_SOUND) |
| `session_questions` | Câu đã đóng băng; `question_id` NOT NULL, luôn trỏ tới 1 dòng `game_questions` thật |
| `media_assets` | Ảnh/giọng thật cho đáp án, câu hỏi, sticker (hiện ảnh/giọng nằm trong `metadata` và `public/audio`); **giữ**, code còn dùng |

### 4.4 Lộ trình và việc chưa chốt
- Giai đoạn 1: đổi cách bốc, không đổi bảng; có cờ bật/tắt để quay về cách cũ. Giai đoạn 2: `answer_items.topic_id`, mỗi (chủ đề, type) có 1 mẫu chính,
  migration `R__` tự tạo `game_questions`. Giai đoạn 3 (tùy chọn): `game_questions` thành bảng nối sinh tự động.
- ✔ **Q1 (đã chốt 2026-11-19):** "Mode đang chơi" là **chế độ ngôn ngữ** (VI / EN / VI_EN): chủ đề, câu hỏi và đáp án lọc theo chế độ này.
- ✔ **Q4 (đã chốt 2026-11-19):** chủ đề có ít hơn 5 đáp án dùng được **không bị ẩn**; đáp án sai được lấy thêm từ **chủ đề cha**.
  (Ví dụ FLY_VEHICLES chỉ có 5, SHAPES 6.) 🔧 đã code, chờ commit:
  - **Đáp án sai:** khi category của chủ đề không đủ 3 từ khác thì lấy phần thiếu từ các từ của **chủ đề cha** (cả cây của cha). Chỉ áp dụng cho câu nhận biết hình (IMAGE_TO_WORD, SHAPE_RECOGNITION, COLOR_RECOGNITION); phép tính, đếm số, câu thuộc tính ("Ai biết bay?") không mượn. Từ mượn vẫn phải qua kiểm tra `correct_match` (không thể cũng đúng).
  - **Câu hỏi:** chủ đề con có ít hơn 5 từ riêng thì các câu của chủ đề cha nối thêm phía sau; từ riêng của chủ đề luôn được hỏi trước.
  - **Chủ đề chơi được:** có ≥5 từ riêng, hoặc có ≥1 từ riêng và chủ đề cha có ≥5 từ trong cả cây.
  - Với dữ liệu hiện tại mọi chủ đề đang hoạt động đều có ≥5 từ (ít nhất: FLY_VEHICLES 5, SHAPES 6), nên chưa có chủ đề nào đổi trạng thái; quy tắc có tác dụng khi thêm chủ đề nhỏ.
- ✔ **Ảnh (đã chốt 2026-11-19):** chưa có ảnh thật thì **dùng emoji tạm**, không chặn chủ đề; không có mốc bắt buộc ảnh. Hiện 387 đáp án chưa có ảnh.

### 4.5 Lời hỏi theo chủ đề (`question_templates`) ✅
Nguồn thật là `R__zz_templates.sql` (chạy lại khi API khởi động); giọng đọc là file Hải Đăng trong `web/public/audio/vi`.
| Chủ đề | Mẫu | Tiếng Việt | English |
|---|---|---|---|
| ANIMALS (FLY/LAND/SEA_ANIMALS) | WHAT_ANIMAL | Đây là con gì? | What animal is this? |
| FRUITS | WHAT_FRUIT | Đây là quả gì? | What fruit is this? |
| VEGETABLES | WHAT_VEGETABLE | Đây là rau củ gì? | What vegetable is this? |
| NUMBERS | WHAT_NUMBER | Đây là số mấy? | What number is this? |
| OBJECTS | WHAT_IS_THIS | Đây là cái gì? | What is this? |
| COLORS | WHAT_COLOR | Đây là màu gì? | What color is this? |
| SHAPES | WHAT_SHAPE | Đây là hình gì? | What shape is this? |
| JOB (nghề nghiệp) | WHAT_JOB | **Đây là ai nhỉ?** | **Who is this?** |
| PEOPLE_CHARACTERS | WHO_IS_THIS | **Đây là ai nhỉ?** | Who is this? |
| LAND/SEA/FLY_VEHICLES | WHAT_VEHICLE | Đây là phương tiện gì? | What vehicle is this? |
Hai chủ đề JOB và PEOPLE_CHARACTERS dùng chung một câu và một file giọng `who_is_this.mp3`.

## 5. Một phiên chơi
- 5 câu mỗi phiên; 4 lựa chọn mỗi câu. ✅
- Thời gian trả lời mặc định 8 giây; đồng hồ bắt đầu khi giọng đọc câu hỏi xong. Server cộng 1 giây dung sai mạng; trả lời sau hạn = **TIMEOUT**. ✅
- Nút "Nghe lại" tối đa 2 lần mỗi câu, mỗi lần đếm lại giờ. ✅
- **Điểm = số câu đúng.** Đúng: +1 điểm và +1 chuỗi. Sai hoặc hết giờ: giữ điểm, **chuỗi về 0**. ✅
- Mất mạng khi gửi đáp án: cho bé bấm lại; phiên hết hạn/mất: tạo phiên mới. ✅
- Phiên không hoạt động quá 30 phút → `ABANDONED` (job nền). ✅

## 6. Phản hồi sau mỗi câu (web)
- Bé luôn nghe **đáp án ĐÚNG** (không đọc đáp án sai bé vừa bấm), nghỉ 0,6 giây, rồi đọc câu hiển thị trên màn hình (bỏ emoji). ✅
- Đúng: "Đúng rồi, bé giỏi quá! 🎉" · Sai: "Gần đúng rồi, Thử lại nào bé ơi! 💛" · Hết giờ: "Hết giờ rồi, cố lên nào! ⏰".
- Câu chuyển tiếp sau phản hồi đợi ~0,8 giây.

## 7. Giọng đọc
- Tiếng Việt dùng giọng thu sẵn **Hải Đăng** (dịch vụ TTS riêng của chủ dự án; mp3 48 kHz, mono, 128 kbps).
  `public/audio/vi/manifest.json` ánh xạ **chữ trên màn hình (bỏ emoji)** → `w/<sha1(NFC)[:10]>.mp3`. ✅
- Không có file/manifest hoặc file lỗi → dùng giọng trình duyệt (`speechSynthesis`), để bé luôn nghe được câu. ✅
- Câu mới hiển thị trên màn hình phải được thu giọng và thêm vào manifest.

## 8. Màn hình kết quả (web)
- Thứ tự: cúp 🏆 → "Giỏi quá! Chúc mừng con 🎉" → hàng sao → độ chính xác, chuỗi đúng dài nhất, chuỗi hiện tại → nút **"Chơi tiếp! 🚀"**, "Về trang chủ", "Khoe thành tích". ✅
- Không còn dòng "Hoan hô bé đã hoàn thành!" và câu "Bé đã học thêm được rất nhiều điều bổ ích hôm nay!" (bỏ 2026-11-19).
- Thanh **"Dành cho ba mẹ"** nằm dưới thẻ kết quả, rộng bằng thẻ: "Đăng nhập để lưu điểm và các cột mốc của bé. Không bắt buộc."
  Có nút Google và "Để sau" (chỉ một cách đóng). Là phần phụ, không tranh với nút chơi tiếp. ✅

## 8b. Trợ giúp cho ba mẹ (web, đường dẫn /help)

- Có ở thanh điều hướng (máy tính) và chân trang (mọi màn hình). Gồm: **gửi góp ý**, giới thiệu **tự tạo câu hỏi** (🔜 đợt sau, cần đăng nhập, câu gửi lên để đội ngũ duyệt trước khi vào kho chung), và 3 câu hỏi thường gặp. ✅ (góp ý)
- Góp ý: loại (Lỗi / Ý tưởng / Câu hỏi muốn thêm / Khác), nội dung 5 đến 2000 ký tự, liên hệ tùy chọn (≤ 200). **Không cần đăng nhập**; chống spam: tối đa **5 tin mỗi giờ** cho mỗi thiết bị (`X-Anonymous-Id`), vượt thì báo `RATE_LIMITED`. ✅
- Lưu bảng `support_feedback` (V37), trạng thái NEW → SEEN → DONE / REJECTED. API gửi: `POST /api/v1/support-feedback`. Admin (ADMIN, SUPER_ADMIN): `GET /api/v1/admin/support-feedback?status=&page=&size=` và `PATCH /api/v1/admin/support-feedback/{id}` (đổi trạng thái, ghi chú). ✅
- Chưa có màn admin trên web; hiện xem qua API.

## 9. Tài khoản và phiên đăng nhập
- Đăng nhập Google tùy chọn (người dùng) và admin có mật khẩu. ✅
- **`user_sessions`: một dòng cho mỗi lần đăng nhập** (không phải mỗi người dùng), kèm họ refresh token riêng; thu hồi theo từng thiết bị. ✅
- Chính sách mặc định: access token 15 phút · refresh token 7 ngày, xoay vòng · nghỉ 24 giờ hết hạn · tối đa 7 ngày · phát hiện thiết bị mới.
- **Giới hạn phiên cùng lúc:** admin 5; **user 3** (🔧 đổi từ "không giới hạn", chưa commit). Vượt giới hạn thì phiên cũ nhất bị `EVICTED`.
  Chỉnh qua `auth_settings` (`maxSessionsAdmin` / `maxSessionsUser`).
- `oauth_login_attempts`: dòng tạm sống 10 phút cho mỗi lần bấm đăng nhập Google, `AuthCleanupJob` dọn; bảng thường rỗng là bình thường.

## 10. Lưu giữ dữ liệu (job nền, chạy mỗi giờ, xóa theo lô)
| Bảng | Giữ | Cấu hình | Trạng thái |
|---|---|---|---|
| `api_logs` | 30 ngày | `API_LOG_RETENTION_DAYS` | ✅ |
| `activity_logs` | 180 ngày | `ACTIVITY_LOG_RETENTION_DAYS` | ✅ |
| `user_sessions` (đã kết thúc hoặc quá hạn tuyệt đối) | 90 ngày | `USER_SESSION_RETENTION_DAYS` | 🔧 đang làm, chưa commit |

- 0 = giữ mãi. Xóa phiên đăng nhập kéo theo refresh token của phiên (CASCADE); `login_history`, `security_events` giữ nguyên.

## 11. Quy tắc làm việc với dữ liệu và code
- Không đổi nghiệp vụ, schema, API contract khi chỉ tối ưu; không `DROP` / `TRUNCATE` / xóa dữ liệu khi chưa được chủ dự án đồng ý từng việc.
- Không chạy migration trực tiếp trên Production; mọi thay đổi database có phương án rollback.
- Không dùng `CREATE INDEX CONCURRENTLY` trong Flyway (treo vì khóa advisory).
- Làm việc và commit thẳng trên `main` (api và web); không để lại nhánh/worktree phụ. Chỉ commit/push khi được yêu cầu.
- Bảng `answer_items`, `game_questions`, `media_assets`, `oauth_login_attempts` đang được dùng: không xóa.

## 12. Nhật ký thay đổi
| Ngày | Thay đổi |
|---|---|
| 2026-11-18 | Chốt hướng tạo câu hỏi: đáp án trước, truy ngược câu hỏi; emoji tạm khi chưa có ảnh |
| 2026-11-19 | Tối ưu DB: dọn `api_logs` (30 ngày) và `activity_logs` (180 ngày); chủ đề chơi được tính theo từ khác nhau, chỉ `AGE_1_3` |
| 2026-11-19 | Phản hồi đúng: "Đúng rồi, bé giỏi quá!" bằng giọng Hải Đăng (thu lại 2 lần) |
| 2026-11-19 | Màn hình kết quả: nút "Chơi tiếp! 🚀"; thanh "Dành cho ba mẹ"; bỏ dòng "Hoan hô…" và câu "Bé đã học thêm…" |
| 2026-11-19 | Phiên đăng nhập: giới hạn user 3 phiên; dọn phiên đã kết thúc sau 90 ngày (🔧 chưa commit) |
| 2026-11-19 | Chốt Q1 (lọc theo chế độ ngôn ngữ), Q4 (đáp án sai lấy từ chủ đề cha khi ít từ), ảnh (dùng emoji tạm, không bắt buộc ảnh); chưa code |
| 2026-11-19 | Giai đoạn 1: phiên chơi chọn đáp án (từ) trước rồi bốc 1 câu của từ; có cờ bật/tắt (🔧 chưa commit) |
| 2026-11-19 | Q4 đã code: đáp án sai và câu hỏi mượn từ chủ đề cha khi chủ đề ít từ; truy vấn chủ đề chơi được cập nhật (🔧 chưa commit) |
| 2026-11-19 | Lời hỏi: JOB và PEOPLE_CHARACTERS đổi thành "Đây là ai nhỉ?" / "Who is this?"; thu lại giọng Hải Đăng (🔧 chưa commit) |
| 2026-11-19 | Bỏ chủ đề OBJECTS (Các đồ vật) và TOYS (Đồ chơi) cùng câu hỏi và từ của chúng: migration V34 (🔧 chưa commit) |
| 2026-11-19 | Ẩn chủ đề BODY_PARTS (Bộ phận cơ thể): is_active = false, giữ dữ liệu; migration V35 (🔧 chưa commit) |
| 2026-11-19 | Lấy lại chủ đề OBJECTS (Các đồ vật) cùng 98 từ: migration V36 (TOYS vẫn bỏ) (🔧 chưa commit) |
| 2026-11-19 | Màn kết quả: lời chúc đổi từ "Bé giỏi tuyệt vời!" thành "Giỏi quá! Chúc mừng con" (🔧 chưa commit) |
| 2026-11-19 | Trợ giúp cho ba mẹ: màn Trợ giúp gửi góp ý (không cần đăng nhập, tối đa 5 tin/giờ/thiết bị) lưu bảng support_feedback, admin xem và đổi trạng thái; tạo câu hỏi do phụ huynh để đợt sau (🔧 chưa commit) |
| 2026-11-19 | Gộp toàn bộ nghiệp vụ vào file này (thay `question-generation.md`) |
