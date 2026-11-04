package com.casms.payment.enums;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description =
                "3 hình thức thanh toán cho đơn đặt tour: FULL (Toàn bộ 100%), DEPOSIT (Đặt cọc),"
                    + " STAGE (Theo giai đoạn/từng đợt)")
public enum PaymentType {
    FULL, // Thanh toán 100% toàn bộ tiền vé
    DEPOSIT, // Đặt cọc 30% (hoặc số tiền cọc quy định) để kích hoạt giữ chỗ
    STAGE // Thanh toán theo giai đoạn (theo từng đợt của lộ trình)
}
