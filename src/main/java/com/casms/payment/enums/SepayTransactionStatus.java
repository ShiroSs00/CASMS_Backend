package com.casms.payment.enums;

public enum SepayTransactionStatus {
    PROCESSED,         // Đã xử lý và xác nhận thanh toán thành công
    UNMATCHED,         // Không tìm thấy mã đơn/thanh toán phù hợp trong nội dung chuyển khoản
    AMOUNT_MISMATCH,   // Tìm thấy đơn nhưng số tiền chuyển khoản không đủ
    IGNORED            // Giao dịch bị bỏ qua (ví dụ: giao dịch chuyển tiền đi / transferType != 'in')
}

