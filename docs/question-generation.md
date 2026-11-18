# Nghiệp vụ tạo câu hỏi — game chính (GUESS)

Trạng thái: **ĐÃ CHỐT hướng** (2026-11-18). Chưa triển khai code. Các mục "Chưa chốt" ở cuối phải được quyết trước khi code.

## 1. Quy tắc nghiệp vụ đã chốt

1. **Câu hỏi hiểu biết luôn kèm hình.** Hình của câu hỏi là hình của **đáp án đúng**:
   `answer_items.metadata.image` (ảnh thật) → nếu chưa có thì `answer_items.metadata.emoji` (tạm thời).
   Emoji được chấp nhận tạm cho tới khi có đủ ảnh; khi có ảnh thì nạp vào `metadata.image`, không đổi code.
2. **Mỗi đáp án thuộc 1 chủ đề, 1 type và 1 mẫu câu hỏi (question template).**
   Ví dụ: `LAND_ANIMALS_BEAR` → chủ đề LAND_ANIMALS → type IMAGE_TO_WORD → mẫu `WHAT_ANIMAL` ("Đây là con gì?").
3. **Mỗi câu có 4 lựa chọn: 1 đáp án đúng + 3 đáp án sai** lấy theo quy tắc của mẫu
   (`distractor_rules`: cùng `category`, không trùng tên, không có khả năng cũng đúng), rồi xáo vị trí.
4. **Chọn đáp án đúng trước, rồi truy ngược ra câu hỏi** (cho mẫu nhận biết hình).
   - Lý do: bé học **từ**; mỗi từ có cơ hội ngang nhau; thêm cách hỏi mới không làm lệch tỉ lệ xuất hiện;
     không trùng từ trong phiên là tự nhiên.
   - Nếu một đáp án có nhiều câu hỏi (nhiều cách hỏi): **bốc ngẫu nhiên 1 câu** trong số đó.
5. **Ngoại lệ — chọn câu hỏi trước** (giữ như hiện tại):
   - Phép tính (`CALCULATE`, `MATH_QUICK*`): 1 kết quả ứng với nhiều phép tính, nên bài toán là gốc.
   - Câu động (`HOW_MANY` / COUNT_OBJECTS): đáp án do code sinh, không có đáp án cố định.
6. **Một từ chỉ hỏi 1 lần trong một phiên** (5 câu).
7. **Đóng băng**: 5 câu + 4 lựa chọn + vị trí được lưu vào `session_questions` (`question_snapshot`,
   `options_snapshot`) lúc tạo phiên. Sửa dữ liệu sau đó chỉ có hiệu lực từ phiên mới.
8. **Lời hỏi và giọng đọc** lấy từ `question_templates.i18n` theo chế độ ngôn ngữ (vi/en);
   tên đáp án lấy từ `answer_items.i18n`.

## 2. Luồng khi bé bấm chủ đề

```
1. Cây chủ đề  = chủ đề được bấm + chủ đề con đang hoạt động (lọc topics.supported_modes theo chế độ ngôn ngữ)
2. Nguồn A (mẫu target = item, ví dụ WHAT_ANIMAL / WHAT_COLOR / WHAT_SHAPE / WHAT_NUMBER):
     danh sách ĐÁP ÁN ĐÚNG trong cây chủ đề có ít nhất 1 câu hỏi hợp lệ
     (câu + game mode + đáp án đang hoạt động, đúng nhóm tuổi, đúng is_basic_english, có distractor_rules)
   Nguồn B (phép tính / câu động): danh sách CÂU HỎI như hiện tại
3. Lặp tới đủ 5 câu:
     - Nguồn A: bốc ngẫu nhiên 1 đáp án chưa hỏi → bốc ngẫu nhiên 1 câu hỏi của đáp án đó
     - Nguồn B: bốc câu hỏi như hiện tại
     - Sinh 3 đáp án sai (OptionGeneratorImpl) → lỗi thiếu đáp án sai thì bỏ, bốc cái khác
4. Nhóm tuổi 4-5: giữ tỉ lệ 60 % AGE_4_5 / 40 % AGE_1_3 (mẫu HEHEH), áp dụng theo đáp án
5. Đóng băng vào session_questions
```

## 3. Bảng và khóa

| Bảng | Vai trò trong luồng |
|---|---|
| `topics` | Cây chủ đề (`parent_id`), `supported_modes` |
| `answer_items` | Từ vựng = đáp án đúng/sai; `metadata` (category, image, emoji), `i18n`, `is_basic_english` |
| `game_questions` | Cặp (đáp án, mẫu, game mode): `correct_answer_item_id`, `template_id`, `game_mode_id`, `age_group`, `metadata.distractor_rules` — dùng để **truy ngược** từ đáp án ra câu hỏi |
| `question_templates` | Mẫu câu: `question_type`, `i18n` (lời hỏi + audio), `generation` (target, topics, distractors) |
| `game_modes` | Kiểu chơi (GUESS, ANIMAL_SOUND) |
| `session_questions` | Câu đã đóng băng; `question_id` NOT NULL → luôn trỏ tới 1 dòng `game_questions` thật |

## 4. Rủi ro và cách xử lý

| # | Rủi ro | Cách xử lý |
|---|---|---|
| R1 | `answer_items` chưa có `topic_id`; chủ đề đang suy ra qua `metadata.category` / câu hỏi | Giai đoạn 2: thêm `answer_items.topic_id`, điền từ `category` sau khi kiểm tra 1 category ↔ 1 chủ đề |
| R2 | 1 đáp án có nhiều câu hỏi (vd "Ai biết bay?" + "Đây là con gì?") | Đã chốt: bốc ngẫu nhiên 1 câu của đáp án |
| R3 | Phép tính: 1 kết quả ↔ nhiều phép tính | Đã chốt: giữ chọn theo câu hỏi |
| R4 | Câu động không có đáp án cố định | Đã chốt: giữ DynamicQuestionServiceImpl |
| R6 | Chủ đề ít đáp án (FLY_VEHICLES 5, SHAPES 6) | Chưa chốt (xem mục 6) |
| R7 | Tỉ lệ xuất hiện đổi nhẹ (vd NUMBERS 28 đáp án / 31 câu) | Chấp nhận: bốc đều theo từ là mục tiêu |
| R8 | Đáp án mới chưa có dòng `game_questions` → truy ngược rỗng | Truy vấn chỉ lấy đáp án có câu hợp lệ (JOIN); giai đoạn 2 sinh câu tự động bằng migration `R__` |
| R9 | Trộn tuổi 4-5 đang theo câu | Lấy nhóm tuổi qua câu hỏi của đáp án, giữ mẫu HEHEH |

## 5. Lộ trình triển khai

1. **Giai đoạn 1 — đổi cách bốc, không đổi bảng**: truy vấn danh sách đáp án (nguồn A) + giữ nguồn B; cờ cấu hình
   bật/tắt để quay lại cách cũ; test: chủ đề ít đáp án, đáp án nhiều câu, trộn với câu động, trộn tuổi.
2. **Giai đoạn 2 — chuẩn hóa dữ liệu**: `answer_items.topic_id`; mỗi (chủ đề, type) có 1 mẫu chính;
   migration `R__` tự tạo `game_questions` cho mỗi cặp (đáp án, mẫu của chủ đề).
3. **Giai đoạn 3 (tùy chọn)**: `game_questions` thành bảng nối sinh tự động, không nhập tay.

Lưu ý: migration mới đánh số tiếp sau số lớn nhất đang có (hiện thấy V31 trong thư mục api).

## 6. Chưa chốt (phải quyết trước khi code)

- **Q1 — "Mode đang chơi"** là game mode (GUESS / ANIMAL_SOUND), chế độ ngôn ngữ (VI / EN / VI_EN), hay cả hai.
  Đề xuất: lọc cả hai.
- **Q4 — Chủ đề có < 5 đáp án đủ dùng**: ẩn chủ đề, hay cho lấy đáp án sai từ chủ đề cha.
- **Ảnh bắt buộc**: hiện chấp nhận emoji tạm; khi nào chuyển sang bắt buộc ảnh thật (theo từng chủ đề hay toàn bộ).
