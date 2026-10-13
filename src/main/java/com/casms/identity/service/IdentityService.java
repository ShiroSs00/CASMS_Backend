package com.casms.identity.service;

import com.casms.identity.dto.UserLookupResponse;
import com.casms.identity.entity.User;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<UserLookupResponse> findUsers(UserStatus status, String assignableFor) {
        UserStatus targetStatus = status != null ? status : UserStatus.ACTIVE;
        List<User> users = userRepository.findByStatus(targetStatus);

        String capabilityLabel = "OPERATIONAL_ISSUE".equalsIgnoreCase(assignableFor)
                ? "Operational Issue Handler"
                : null;

        return users.stream()
                .map(u -> UserLookupResponse.builder()
                        .id(u.getUserId())
                        .displayName(u.getUsername())
                        .username(u.getUsername())
                        .status(u.getStatus())
                        .capabilityLabel(capabilityLabel)
                        .build())
                .toList();
    }
}
