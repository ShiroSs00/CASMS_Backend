package com.casms.pos;

import com.casms.common.exception.*;
import com.casms.identity.entity.User;
import com.casms.identity.enums.*;
import com.casms.identity.repository.UserRepository;
import com.casms.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

/** Machine identity and employee identity are verified separately. */
@Service @RequiredArgsConstructor @Transactional
public class PosDeviceAccountService {
    private final PosRepository terminals;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final JwtService jwt;

    public PosDtos.DeviceAccount account(UUID terminal) {
        terminal(terminal); return new PosDtos.DeviceAccount(terminal, linkedUser(terminal));
    }
    public PosDtos.DeviceAccount bind(UUID terminal, UUID userId) {
        terminal(terminal);
        if(jdbc.queryForList("SELECT user_id FROM \"User\" WHERE user_id=? FOR UPDATE",UUID.class,userId).isEmpty())
            throw new ResourceNotFoundException("POS account",userId);
        if(!eligible(users.findById(userId).orElseThrow()))
            throw new BusinessException("INVALID_POS_DEVICE_ACCOUNT","An active account with only role POS is required");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"PosTerminal\" WHERE device_user_id=? AND terminal_id<>?)",Boolean.class,userId,terminal)))
            throw new BusinessException(HttpStatus.CONFLICT,"POS_ACCOUNT_ALREADY_BOUND","Account is already linked to another terminal");
        jdbc.update("UPDATE \"PosTerminal\" SET device_user_id=? WHERE terminal_id=?",userId,terminal);
        jdbc.update("UPDATE \"PosSession\" SET revoked_at=now() WHERE terminal_id=? AND revoked_at IS NULL",terminal);
        return new PosDtos.DeviceAccount(terminal,userId);
    }
    Instant authenticate(UUID terminal,PosDtos.LoginInput input) {
        UUID id=linkedUser(terminal);
        if(id==null) {
            if(input.deviceAccessToken()!=null && !input.deviceAccessToken().isBlank())throw denied("Account is not linked to this POS");
            return null; // Preserve legacy device secrets until an account is explicitly bound.
        }
        String token=input.deviceAccessToken();
        if(token==null || !jwt.isValid(token))throw denied("The linked POS account token is required");
        User account=active(id);
        if(!id.equals(jwt.extractUserId(token)) || !account.getUsername().equals(jwt.extractUsername(token)))throw denied("Token belongs to another POS account");
        return jwt.expiresAt(token);
    }
    void requireActive(UUID terminal) {UUID id=linkedUser(terminal);if(id!=null)active(id);}
    private User active(UUID id) {
        User user=users.findById(id).orElseThrow(()->denied("POS account no longer exists"));
        if(!eligible(user))throw denied("POS account is inactive or no longer a device account");
        return user;
    }
    private static boolean eligible(User u) {return u.getStatus()==UserStatus.ACTIVE && Set.of(Role.POS).equals(u.getRoles());}
    private UUID linkedUser(UUID terminal) {return jdbc.queryForObject("SELECT device_user_id FROM \"PosTerminal\" WHERE terminal_id=?",UUID.class,terminal);}
    private void terminal(UUID id) {terminals.device(id,true).orElseThrow(()->new ResourceNotFoundException("POS terminal",id));}
    private static AccessDeniedException denied(String message) {return new AccessDeniedException(message);}
}
