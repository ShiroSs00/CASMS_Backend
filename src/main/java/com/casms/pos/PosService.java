package com.casms.pos;

import com.casms.booking.enums.BookingStatus;
import com.casms.booking.enums.BookingPassengerParticipationStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.common.exception.BusinessException;
import com.casms.common.exception.ResourceNotFoundException;
import com.casms.common.util.NewWriteCurrency;
import com.casms.identity.enums.Permission;
import com.casms.payment.entity.*;
import com.casms.payment.enums.*;
import com.casms.payment.repository.*;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.servicepackage.controller.ServicepackageController.ServiceRequestView;
import com.casms.servicepackage.entity.ServiceRequest;
import com.casms.servicepackage.repository.*;
import com.casms.servicepackage.service.ServiceRequestService;
import com.casms.ship.enums.CruiseStatus;
import com.casms.ship.repository.DeckServiceAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class PosService {
    private final PosRepository repository;
    private final ServiceRepository services;
    private final DeckServiceAssignmentRepository deckServices;
    private final ServiceRequestService serviceRequests;
    private final ServiceRequestRepository requests;
    private final ServiceScheduleRepository schedules;
    private final BookingPassengerRepository passengers;
    private final OnboardAccountRepository accounts;
    private final ChargeRepository charges;
    private final PosLedgerService ledger;
    private final OnboardTransactionRepository transactions;
    private final PasswordEncoder encoder;
    @Value("${casms.pos.session-ttl:PT30M}") private Duration sessionTtl;
    private static final SecureRandom RANDOM = new SecureRandom();
    record Access(PosDtos.Terminal terminal, PosRepository.Area area, CasmsPrincipal employee) {}

    public List<PosDtos.AreaService> assignments(UUID ship,UUID area) {
        area(ship,area,false);return repository.assignments(area);
    }
    public PosDtos.AreaService assign(UUID ship,UUID id,UUID service,PosDtos.AssignmentInput input) {
        var area=area(ship,id,true);
        services.findById(service).orElseThrow(()->new ResourceNotFoundException("Service",service));
        if(deckServices.findByDeckDeckIdAndServiceServiceId(area.deckId(),service).isEmpty())
            throw invalid("SERVICE_NOT_ON_AREA_DECK","Assign the service to this area's deck first");
        repository.assign(id,service,input);
        return new PosDtos.AreaService(id,service,input.permissions(),input.enabled());
    }
    public void unassign(UUID ship,UUID area,UUID service) {area(ship,area,true);repository.unassign(area,service);}
    public List<PosDtos.Terminal> terminals(UUID ship) {return repository.devices(ship);}
    public PosDtos.Credential create(UUID ship,PosDtos.TerminalInput input) {
        area(ship,input.areaId(),true);
        var t=new PosDtos.Terminal(UUID.randomUUID(),ship,input.areaId(),input.code().trim(),input.name().trim(),input.permissions(),input.enabled());
        String secret=secret();repository.createDevice(t,encoder.encode(secret));return new PosDtos.Credential(t,secret);
    }
    public PosDtos.Terminal update(UUID id,PosDtos.TerminalUpdate input) {
        device(id);repository.updateDevice(id,input);return device(id).terminal();
    }
    public PosDtos.Credential rotate(UUID id) {
        var d=device(id);String secret=secret();repository.rotate(id,encoder.encode(secret));return new PosDtos.Credential(d.terminal(),secret);
    }
    public PosDtos.Session login(UUID id,PosDtos.LoginInput input,CasmsPrincipal user) {
        employee(user);
        var d=device(id);
        if(!d.terminal().enabled() || !encoder.matches(input.deviceSecret(),d.secretHash()))throw denied("Invalid or inactive POS device");
        area(d.terminal().shipId(),d.terminal().areaId(),true);
        if(sessionTtl.isNegative() || sessionTtl.isZero() || sessionTtl.compareTo(Duration.ofHours(8))>0)
            throw new IllegalStateException("POS session TTL must be positive and at most eight hours");
        String token=secret();Instant expiry=Instant.now().plus(sessionTtl);
        repository.session(hash(token),id,user.getUserId(),expiry);
        return new PosDtos.Session(token,expiry,d.terminal());
    }
    public PosDtos.Context context(String token,CasmsPrincipal user) {
        var a=access(token,user);return new PosDtos.Context(a.terminal(),available(a));
    }
    public void logout(String token,CasmsPrincipal user) {access(token,user);repository.logout(hash(token));}
    public List<PosDtos.AvailableService> available(String token,CasmsPrincipal user) {
        return available(access(token,user)).stream().filter(s->s.permissions().contains(PosPermission.SERVICE_READ)).toList();
    }
    public List<ServiceRequestView> requests(String token,CasmsPrincipal user) {
        var a=access(token,user);
        requireTerminal(a,PosPermission.SERVICE_REQUEST_READ);
        var allowed=available(a).stream().filter(s->s.permissions().contains(PosPermission.SERVICE_REQUEST_READ))
                .map(PosDtos.AvailableService::serviceId).collect(java.util.stream.Collectors.toSet());
        return repository.requestIds(a.area().id(),a.area().shipId()).stream().map(id->serviceRequests.get(id,user.getUserId(),true))
                .filter(r->allowed.contains(r.getService().getServiceId())).map(PosService::requestView).toList();
    }
    public ServiceRequestView action(String token,CasmsPrincipal user,UUID request,String action) {
        var a=access(token,user);
        PosPermission permission=switch(action) {
            case "assign"->PosPermission.SERVICE_REQUEST_ASSIGN;
            case "accept","reject","start","complete","cancel"->PosPermission.SERVICE_REQUEST_EXECUTE;
            default->throw invalid("INVALID_POS_ACTION","Unsupported service request action");
        };
        var r=requests.lockById(request).orElseThrow(()->new ResourceNotFoundException("Service request",request));
        require(a,r.getService().getServiceId(),permission);
        if(r.getBooking()==null || r.getBooking().getCruise().getShip()==null
                || !a.area().shipId().equals(r.getBooking().getCruise().getShip().getShipId()))throw denied("Request is outside this POS ship");
        return requestView(serviceRequests.transition(request,user.getUserId(),action));
    }
    public PosDtos.Sale sell(String token,CasmsPrincipal user,PosDtos.SaleInput input) {
        var a=access(token,user);
        require(a,input.serviceId(),PosPermission.SERVICE_SALE);
        String fingerprint=hash(input.serviceId()+"|"+input.bookingPassengerId()+"|"+input.onboardAccountId()+"|"+input.scheduleId()+"|"+input.quantity());
        var existing=repository.sale(a.terminal().terminalId(),input.idempotencyKey().trim());
        if(existing.isPresent()) {
            if(!fingerprint.equals(existing.get().fingerprint()))throw conflict("POS_IDEMPOTENCY_CONFLICT","Key was used for a different sale");
            return existing.get().sale();
        }
        // All internal allowance writes serialize on the account; no money is collected at POS.
        ledger.lockAccount(input.onboardAccountId());
        var account=accounts.findById(input.onboardAccountId()).orElseThrow(()->new ResourceNotFoundException("Onboard account",input.onboardAccountId()));
        var bp=passengers.findById(input.bookingPassengerId()).orElseThrow(()->new ResourceNotFoundException("Booking passenger",input.bookingPassengerId()));
        var cruise=bp.getBooking().getCruise();
        if(cruise.getShip()==null || !a.area().shipId().equals(cruise.getShip().getShipId()) || !cruise.getCruiseId().equals(account.getCruise().getCruiseId()))
            throw denied("Passenger/account is outside this POS ship or cruise");
        if(account.getStatus()!=OnboardAccountStatus.ACTIVE || cruise.getStatus()!=CruiseStatus.IN_PROGRESS
                || bp.getBooking().getBookingStatus()!=BookingStatus.Confirmed || bp.getParticipationStatus()!=BookingPassengerParticipationStatus.Active)
            throw invalid("POS_PASSENGER_NOT_ELIGIBLE","An active account and passenger on an in-progress confirmed cruise are required");
        var catalog=services.findByIdForUpdate(input.serviceId()).orElseThrow(()->new ResourceNotFoundException("Service",input.serviceId()));
        // Deck unlink/status updates also lock the service. Re-check scope after that lock,
        // including when the request waited for a concurrent configuration change.
        require(a,input.serviceId(),PosPermission.SERVICE_SALE);
        if(!catalog.isCanSellIndependently() || !catalog.isCanBuyOnboard())throw invalid("SERVICE_NOT_RETAIL_ONBOARD","Service cannot be sold independently onboard");
        NewWriteCurrency.requireUsd(catalog.getCurrencyCode());
        if(catalog.getPrice()==null || catalog.getPrice().signum()<=0)throw invalid("POS_PRICE_REQUIRED","A positive catalogue price is required");
        if(input.scheduleId()!=null) {
            var schedule=schedules.findById(input.scheduleId()).orElseThrow(()->new ResourceNotFoundException("Service schedule",input.scheduleId()));
            if(schedule.getCruise()==null || !schedule.getCruise().getCruiseId().equals(cruise.getCruiseId()))throw denied("Schedule belongs to another cruise");
        }
        var passenger=bp.getPassenger();
        BigDecimal total=catalog.getPrice().multiply(BigDecimal.valueOf(input.quantity()));
        if(total.precision()-total.scale()>15)throw invalid("POS_AMOUNT_TOO_LARGE","Sale exceeds supported monetary precision");
        ledger.assertCanSpend(account,passenger.getPassengerId(),total);
        var request=serviceRequests.createForPos(input.bookingPassengerId(),input.serviceId(),input.scheduleId(),input.quantity());
        UUID saleId=UUID.randomUUID();Instant now=Instant.now();
        var transaction=transaction(account,passenger,user,total,TransactionType.CHARGE,"POS_SALE",saleId,now);
        var charge=new Charge();charge.setTransaction(transaction);charge.setPassenger(passenger);charge.setSourceType("POS_SALE");
        charge.setSourceReferenceId(saleId);charge.setQuantity(BigDecimal.valueOf(input.quantity()));charge.setUnitPrice(catalog.getPrice());
        charge.setGrossAmount(total);charge.setDiscountAmount(BigDecimal.ZERO);charge.setNetAmount(total);charge.setTaxAmount(BigDecimal.ZERO);
        charge.setChargedAt(now);charge.setCreatedByUser(user.getUser());charges.saveAndFlush(charge);
        var sale=new PosDtos.Sale(saleId,a.terminal().terminalId(),a.area().id(),input.serviceId(),request.getServiceRequestId(),
                account.getOnboardAccountId(),bp.getBookingPassengerId(),input.quantity(),catalog.getPrice(),total,"USD",user.getUserId(),charge.getChargeId(),now,null);
        repository.sale(sale,input.idempotencyKey().trim(),fingerprint);return sale;
    }
    public PosDtos.Sale sale(String token,CasmsPrincipal user,UUID id) {
        var a=access(token,user);var sale=scopedSale(a,id);
        if(!has(a,sale.serviceId(),PosPermission.SERVICE_SALE))throw denied("Sale access denied");
        return sale;
    }
    public PosDtos.Statement statement(String token,CasmsPrincipal user,UUID id,UUID bookingPassengerId) {
        var a=access(token,user);
        if(available(a).stream().noneMatch(s->s.permissions().contains(PosPermission.SERVICE_SALE)))throw denied("Account access denied");
        ledger.lockAccount(id);
        var account=accounts.findById(id).orElseThrow(()->new ResourceNotFoundException("Onboard account",id));
        var bp=passengers.findById(bookingPassengerId).orElseThrow(()->new ResourceNotFoundException("Booking passenger",bookingPassengerId));
        if(account.getCruise().getShip()==null || !a.area().shipId().equals(account.getCruise().getShip().getShipId())
                || !bp.getBooking().getCruise().getCruiseId().equals(account.getCruise().getCruiseId()))throw denied("Account or passenger is outside this POS ship/cruise");
        return new PosDtos.Statement(ledger.budget(account,bp.getPassenger().getPassengerId()),ledger.history(id));
    }
    private PosDtos.Sale scopedSale(Access a,UUID id) {
        var sale=repository.sale(id).orElseThrow(()->new ResourceNotFoundException("POS sale",id)).sale();
        if(!sale.terminalId().equals(a.terminal().terminalId()) || !sale.areaId().equals(a.area().id()))throw denied("Sale belongs to another POS");
        return sale;
    }
    private OnboardTransaction transaction(OnboardAccount account,com.casms.identity.entity.Passenger passenger,CasmsPrincipal user,
            BigDecimal amount,TransactionType type,String source,UUID reference,Instant now) {
        var t=new OnboardTransaction();t.setOnboardAccount(account);t.setPassenger(passenger);t.setTransactionType(type);t.setAmount(amount);
        t.setCurrencyCode("USD");t.setTransactionTime(now);t.setPostedAt(now);t.setStatus(TransactionStatus.POSTED);
        t.setSourceType(source);t.setSourceReferenceId(reference);t.setCreatedByUser(user.getUser());t.setSyncStatus(TransactionSyncStatus.SYNCED);t.setSyncedAt(now);
        return transactions.save(t);
    }
    private Access access(String token,CasmsPrincipal user) {
        employee(user);
        if(token==null || token.length()>128)throw denied("POS session is required");
        var session=repository.session(hash(token)).orElseThrow(()->denied("Invalid POS session"));
        if(!session.userId().equals(user.getUserId()))throw denied("POS session belongs to another employee");
        var d=device(session.terminalId()).terminal();
        // Re-read after locking the terminal so concurrent disable/rotation cannot revive a session.
        session=repository.session(hash(token)).orElseThrow();
        if(session.revokedAt()!=null || !session.expiresAt().isAfter(Instant.now()) || !d.enabled())throw denied("POS session expired or revoked");
        return new Access(d,area(d.shipId(),d.areaId(),true),user);
    }
    private static void employee(CasmsPrincipal user) {
        if(user==null || !user.isEnabled() || !user.isAccountNonLocked() || !user.isStaff() || !user.hasPermission(Permission.POS_ACCESS))
            throw denied("An active employee with POS_ACCESS is required");
    }
    private PosRepository.Device device(UUID id) {return repository.device(id,true).orElseThrow(()->new ResourceNotFoundException("POS terminal",id));}
    private PosRepository.Area area(UUID ship,UUID id,boolean lock) {
        var a=repository.area(ship,id,lock).orElseThrow(()->new ResourceNotFoundException("Physical area in ship",id));
        if(!"READY".equals(a.modelStatus()))throw invalid("POS_AREA_NOT_PUBLISHED","POS requires a physical area in a READY model");
        return a;
    }
    private List<PosDtos.AvailableService> available(Access a) {return repository.available(a.area().id(),a.terminal().permissions());}
    private boolean has(Access a,UUID service,PosPermission p) {return available(a).stream().anyMatch(s->s.serviceId().equals(service)&&s.permissions().contains(p));}
    private void require(Access a,UUID service,PosPermission p) {if(!has(a,service,p))throw denied("Service or permission is unavailable at this POS");}
    private static void requireTerminal(Access a,PosPermission p) {if(!a.terminal().permissions().contains(p))throw denied("Operation unavailable at this POS");}
    static ServiceRequestView requestView(ServiceRequest r) {
        return new ServiceRequestView(r.getServiceRequestId(),r.getPassenger().getPassengerId(),r.getService().getServiceId(),
                r.getServiceSchedule()==null?null:r.getServiceSchedule().getServiceScheduleId(),r.getBooking()==null?null:r.getBooking().getBookingId(),
                r.getBookingPassenger()==null?null:r.getBookingPassenger().getBookingPassengerId(),r.getQuantity(),r.getUnitPriceSnapshot(),r.getCurrencySnapshot(),r.getNote(),r.getStatus().name(),
                r.getAssignedUser()==null?null:r.getAssignedUser().getUserId(),r.getRequestTime(),r.getCompletedAt());
    }
    private static String secret() {byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
    static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private static AccessDeniedException denied(String message) {return new AccessDeniedException(message);}
    private static BusinessException invalid(String code,String message) {return new BusinessException(code,message);}
    private static BusinessException conflict(String code,String message) {return new BusinessException(HttpStatus.CONFLICT,code,message);}
}
