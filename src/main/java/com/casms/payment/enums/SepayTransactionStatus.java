package com.casms.payment.enums;

public enum SepayTransactionStatus {
    RECEIVED, // Đã tiếp nhận sự kiện webhook, chưa ghi sổ
    PROCESSED, // Đã xử lý và xác nhận thanh toán thành công
    RETRY_REQUIRED, // Lỗi tạm thời khi ghi sổ, cần xử lý lại an toàn
    UNMATCHED, // Không tìm thấy mã đơn/thanh toán phù hợp trong nội dung chuyển khoản
    AMOUNT_MISMATCH, // Tìm thấy lệnh nhưng số tiền thực nhận không khớp (thiếu hoặc dư)
    RECONCILIATION_REQUIRED, // Ngoại lệ nghiệp vụ cần Finance đối soát (sai tài khoản, đơn hủy/hết hạn, trùng khoản thu...)
    IGNORED // Giao dịch bị bỏ qua (ví dụ: giao dịch chuyển tiền đi / transferType != 'in')
}
