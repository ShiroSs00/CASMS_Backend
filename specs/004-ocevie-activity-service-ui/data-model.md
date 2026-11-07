# UI state và dữ liệu hiện có

Không thêm entity hoặc trường lưu trữ server. Dùng ActivityCatalogEntry, ActivityDraft, CatalogItem, CatalogDraft và Benefits của domain hiện tại.

| UI state | Dữ liệu | Quy tắc |
|---|---|---|
| List/filter | query, bộ lọc, dataset đang đọc | Giữ semantics tìm và lọc hiện tại; lỗi và rỗng phân biệt |
| Editor | initial, draft, fieldErrors, submitError, busy | FieldErrors từ validator hiện có. Giữ kiểu dữ liệu/payload và các giá trị lịch sử |
| Dirty state | baseline và draft hiện có | Reuse unsaved guard; hủy phải có quyết định nếu bản nhập đổi |
| Mutation | pending, known failure, unknown outcome, success | Giữ recovery hiện có, chặn gửi lặp; success sau server |
| Confirmation | action, record, reason/fields hiện có | Cùng quyền và điều kiện lifecycle hiện tại; footer phản ánh intent |
| Benefits/schedules | thành phần, quyền lợi, lịch được trả về | Chuẩn hóa cách hiển thị và editing hiện có; giữ mối quan hệ và giới hạn |

Khi đổi route hoặc user, giữ cơ chế loading/reset/query key đang dùng. Kiểm tra quyền tại điểm gửi cũng như hiển thị. Dữ liệu cũ thiếu trường được hiển thị theo fallback hiện có.
