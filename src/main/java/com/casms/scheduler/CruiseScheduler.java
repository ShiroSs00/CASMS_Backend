package com.casms.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BE-21: Quản trị tác vụ định kỳ của chuyến đi.
 * QUY TẮC BẤT BIẾN (BR Invariant):
 * TUYỆT ĐỐI KHÔNG để Scheduler tự động chuyển trạng thái Cruise sang IN_PROGRESS chỉ dựa vào timestamp khởi hành.
 * Mọi quyết định khởi hành bắt buộc phải đi qua cổng kiểm tra điều kiện thực tế (Departure Gate / validateDepartureReadiness)
 * và chỉ được kích hoạt bởi nhân sự có thẩm quyền (Operations Manager / Captain qua endpoint /cruise/{id}/start).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CruiseScheduler {

    public void execute() {
        log.debug("CruiseScheduler heartbeat: System maintains active readiness monitoring without unauthorized automatic transitions.");
    }
}

