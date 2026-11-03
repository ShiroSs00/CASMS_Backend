package com.casms.cruisepackage.service;

import com.casms.common.exception.BusinessException;
import com.casms.cruisepackage.dto.CruisePackageContracts.Blocker;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import com.casms.cruisepackage.entity.CruisePackageBenefit;
import com.casms.cruisepackage.entity.BookingCruisePackageBenefitSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Structured configuration only. No text interpretation, default quota/unit, age/health rules or generic rule engine. */
@Component @RequiredArgsConstructor
public class CruisePackageBenefitUsagePolicy {
    private final JdbcTemplate jdbc;
    public void validateDraft(String inclusion,Integer quantity,String unit,Boolean reservation,Instant start,Instant end) {
        if(reservation==null)throw new BusinessException("BENEFIT_RESERVATION_CONFIGURATION_REQUIRED","Phải khai báo có cần đăng ký trước khi dùng quyền lợi hay không.");
        if("LIMITED_INCLUDED".equals(inclusion)&&(quantity==null||quantity<1||unit==null||unit.isBlank()))
            throw new BusinessException("BENEFIT_USAGE_CONFIGURATION_REQUIRED","Quyền lợi có giới hạn phải có số lượng dương và đơn vị sử dụng được khai báo.");
        if((start==null)!=(end==null)||start!=null&&!start.isBefore(end))
            throw new BusinessException("BENEFIT_USAGE_WINDOW_INVALID","Khoảng sử dụng phải có đủ hai mốc và mốc bắt đầu trước kết thúc.");
    }
    public List<Blocker> blockers(CruisePackageBenefit benefit) {
        return blockers(benefit.getId(),benefit.getInclusion(),benefit.getQuantityLimit(),benefit.getUsageUnit(),benefit.getRequiresReservation(),
                benefit.getUsagePolicy(),benefit.getServiceUnitCode(),benefit.getServiceId());
    }
    public List<Blocker> blockers(BookingCruisePackageBenefitSnapshot benefit) {
        return blockers(benefit.getId(),benefit.getInclusion(),benefit.getQuantityLimit(),benefit.getUsageUnit(),benefit.getRequiresReservation(),
                benefit.getUsagePolicy(),benefit.getServiceUnitCode(),benefit.getServiceId());
    }
    private List<Blocker> blockers(UUID id,String inclusion,Integer quantity,String unit,Boolean reservation,String policy,String serviceUnitCode,UUID serviceId) {
        if(!List.of("INCLUDED","LIMITED_INCLUDED").contains(inclusion))return List.of();
        var blockers=new ArrayList<Blocker>();
        if(reservation==null)blockers.add(new Blocker("BENEFIT_RESERVATION_CONFIGURATION_REQUIRED","Chưa khai báo yêu cầu đăng ký trước khi dùng quyền lợi.",id));
        else if(!reservation)blockers.add(new Blocker("BENEFIT_RESERVATION_MODE_UNSUPPORTED","Nguồn hoạt động/dịch vụ hiện có yêu cầu đăng ký hoặc yêu cầu phục vụ; chưa có luồng sử dụng trực tiếp được xác nhận.",id));
        if("LIMITED_INCLUDED".equals(inclusion)&&(quantity==null||quantity<1||unit==null||unit.isBlank()))
            blockers.add(new Blocker("BENEFIT_USAGE_CONFIGURATION_REQUIRED","Quyền lợi có giới hạn thiếu số lượng hoặc đơn vị sử dụng hợp lệ.",id));
        if("INCLUDED".equals(inclusion)&&(!"UNLIMITED".equals(policy)||quantity!=null))
            blockers.add(new Blocker("BENEFIT_USAGE_POLICY_REQUIRED","Quyền lợi đã bao gồm phải khai báo rõ UNLIMITED và không có quota; quyền lợi một lần dùng LIMITED_INCLUDED.",id));
        if("LIMITED_INCLUDED".equals(inclusion)&&!"LIMITED".equals(policy))
            blockers.add(new Blocker("BENEFIT_USAGE_POLICY_REQUIRED","Quyền lợi có giới hạn phải khai báo chính sách LIMITED.",id));
        if(serviceId==null) {
            if(!"PARTICIPATION".equals(unit)||serviceUnitCode!=null)
                blockers.add(new Blocker("BENEFIT_USAGE_UNIT_UNSUPPORTED","Hoạt động và tour bờ dùng PARTICIPATION cho mỗi hành khách hoàn thành session; không quy đổi số lượng đăng ký thành lượt dùng.",id));
        } else {
            var codes=jdbc.queryForList("SELECT unit_code FROM \"Service\" WHERE service_id=?",String.class,serviceId);
            String current=codes.isEmpty()?null:codes.getFirst();
            if(!"SERVICE_UNIT".equals(unit)||current==null||current.isBlank()||!current.equals(serviceUnitCode))
                blockers.add(new Blocker("BENEFIT_USAGE_UNIT_UNSUPPORTED","SERVICE_UNIT phải giữ đúng unitCode cấu trúc của dịch vụ; không tự quy đổi hoặc đặt đơn vị.",id));
        }
        return List.copyOf(blockers);
    }
    public List<String> supportedUnits() {return List.of("PARTICIPATION","SERVICE_UNIT");}
}
