package com.casms.cruisepackage.service;

import com.casms.common.exception.BusinessException;
import com.casms.cruisepackage.dto.CruisePackageContracts.*;
import com.casms.cruisepackage.entity.*;
import com.casms.cruisepackage.repository.CruisePackageRepository;
import com.casms.identity.enums.Role;
import com.casms.security.principal.CasmsPrincipal;
import com.casms.ship.service.CruiseMainflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service("cruisePackageCommercialService") @RequiredArgsConstructor @Transactional(readOnly=true)
public class CruisePackageService {
    private final CruisePackageRepository repository;
    private final JdbcTemplate jdbc;
    private final CruiseMainflowService plans;
    private final CruisePackageBenefitUsagePolicy benefitUsage;
    private final CruisePackageCabinMappingService cabinMappings;
    private final org.springframework.beans.factory.ObjectProvider<com.casms.ship.service.CruiseService> cruiseServices;

    public List<CruisePackageTemplate> templates() { requireRead(); return repository.templates(); }
    public List<VersionView> versions(UUID templateId) {
        requireRead(); require(CruisePackageTemplate.class,templateId,false);
        return repository.versions(templateId).stream().map(this::versionView).toList();
    }
    public List<OfferView> offers(UUID cruiseId) {
        requireRead(); cruise(cruiseId);
        return repository.offers(cruiseId).stream().map(this::offerView).toList();
    }
    public CruisePackageContext context(UUID cruiseId) {
        requireRead(); UUID shipId=(UUID)cruise(cruiseId).get("ship_id");
        var rows=jdbc.queryForList("SELECT status FROM \"CruisePlanReview\" WHERE cruise_id=?",cruiseId);
        String status=rows.isEmpty()?"DRAFT":(String)rows.getFirst().get("status");
        boolean planning=Boolean.TRUE.equals(jdbc.queryForObject("SELECT status IN ('DRAFT','PUBLISHED') FROM \"Cruise\" WHERE cruise_id=?",Boolean.class,cruiseId));
        boolean maker=hasActor(Role.ACTIVITY_SERVICE_MANAGER,"TOUR_PACKAGE_MANAGE");
        return new CruisePackageContext(cruiseId,shipId,status,shipId!=null&&planning&&maker,
                planning&&maker,planning&&hasActor(Role.OPERATIONS_MANAGER,"CRUISE_PRICING_APPROVE"));
    }
    public List<ResourceOption> resources(ResourceType type,UUID cruiseId) {
        requireRead();
        return switch(type) {
            case ACTIVITY -> cruiseId==null
                    ? jdbc.query("SELECT onboard_activity_id,activity_name FROM \"OnboardActivity\" WHERE cruise_id IS NULL AND deck_id IS NULL AND cabin_id IS NULL ORDER BY activity_name,onboard_activity_id",(r,n)->new ResourceOption(r.getObject(1,UUID.class),r.getString(2)))
                    : jdbc.query("SELECT onboard_activity_id,activity_name FROM \"OnboardActivity\" WHERE cruise_id=? ORDER BY activity_name,onboard_activity_id",(r,n)->new ResourceOption(r.getObject(1,UUID.class),r.getString(2)),cruiseId);
            case EXCURSION -> cruiseId==null
                    ? jdbc.query("SELECT shore_excursion_id,excursion_name FROM \"ShoreExcursion\" WHERE cruise_stop_id IS NULL ORDER BY excursion_name,shore_excursion_id",(r,n)->new ResourceOption(r.getObject(1,UUID.class),r.getString(2)))
                    : jdbc.query("SELECT e.shore_excursion_id,e.excursion_name FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" s USING(cruise_stop_id) JOIN \"Itinerary\" i USING(itinerary_id) WHERE i.cruise_id=? ORDER BY e.excursion_name,e.shore_excursion_id",(r,n)->new ResourceOption(r.getObject(1,UUID.class),r.getString(2)),cruiseId);
            case SERVICE -> jdbc.query("SELECT service_id,service_name,unit_code FROM \"Service\" ORDER BY service_name,service_id",(r,n)->new ResourceOption(r.getObject(1,UUID.class),r.getString(2),r.getString(3)));
        };
    }
    public List<String> cabinOptions(UUID cruiseId) {
        requireRead(); currentShip(cruiseId);
        return jdbc.queryForList("SELECT DISTINCT c.cabin_type FROM \"Cabin\" c JOIN \"Cruise\" f USING(ship_id) WHERE f.cruise_id=? AND c.cabin_type IS NOT NULL ORDER BY c.cabin_type",String.class,cruiseId);
    }

    @Transactional
    public CruisePackageTemplate createTemplate(CreateTemplate input) {
        UUID actor=maker();
        var template=new CruisePackageTemplate();
        template.setId(UUID.randomUUID()); template.setCode(input.code().trim());
        template.setCreatedAt(Instant.now()); template.setCreatedBy(actor);
        repository.insert(template); repository.flush(); return template;
    }

    @Transactional
    public VersionView createVersion(UUID templateId, CreateVersion input) {
        maker(); var template=require(CruisePackageTemplate.class,templateId,true);
        revision(template.getRevision(),input.expectedTemplateRevision());
        var categories=input.logicalCategories().stream().map(String::trim).toList();
        if(new HashSet<>(categories).size()!=categories.size()) fail("FARE_CATEGORY_DUPLICATE","Hạng phòng logic bị trùng.");
        Set<String> references=new HashSet<>();
        for(var benefit:input.benefits()) {
            benefitUsage.validateDraft(benefit.inclusion().name(),benefit.quantityLimit(),benefit.usageUnit(),benefit.requiresReservation(),benefit.usageStartsAt(),benefit.usageEndsAt());
            reference(benefit.resourceType(),benefit.resourceId());
            requireSharedReference(benefit.resourceType(),benefit.resourceId());
            if(!references.add(benefit.resourceType()+":"+benefit.resourceId())) fail("FARE_BENEFIT_DUPLICATE","Quyền lợi bị trùng trong phiên bản.");
            if(benefit.inclusion()==Inclusion.LIMITED_INCLUDED&&benefit.quantityLimit()==null)
                fail("FARE_BENEFIT_LIMIT_REQUIRED","Quyền lợi có giới hạn phải khai báo số lượt.");
        }
        var version=new CruisePackageTemplateVersion();
        version.setId(UUID.randomUUID()); version.setTemplateId(templateId);
        version.setVersionNumber(repository.versions(templateId).stream().mapToInt(CruisePackageTemplateVersion::getVersionNumber).max().orElse(0)+1);
        version.setName(input.name().trim()); version.setDescription(input.description());
        version.setLogicalCategories(categories); version.setStatus("DRAFT"); version.setCreatedAt(Instant.now());
        repository.insert(version);
        repository.flush();
        cabinMappings.createCategories(version.getId(),templateId,categories,input.reusedLogicalCategoryIds());
        for(var inputBenefit:input.benefits()) {
            var benefit=new CruisePackageBenefit(); benefit.setId(UUID.randomUUID()); benefit.setTemplateVersionId(version.getId());
            benefit.setInclusion(inputBenefit.inclusion().name()); benefit.setQuantityLimit(inputBenefit.quantityLimit());
            benefit.setEligibility(inputBenefit.eligibility().trim());
            benefit.setUsageUnit(inputBenefit.usageUnit()==null?null:inputBenefit.usageUnit().trim());
            benefit.setUsagePolicy(inputBenefit.usagePolicy());benefit.setServiceUnitCode(inputBenefit.serviceUnitCode());
            benefit.setRequiresReservation(inputBenefit.requiresReservation());benefit.setUsageStartsAt(inputBenefit.usageStartsAt());benefit.setUsageEndsAt(inputBenefit.usageEndsAt());
            switch(inputBenefit.resourceType()) {
                case ACTIVITY -> benefit.setOnboardActivityId(inputBenefit.resourceId());
                case EXCURSION -> benefit.setShoreExcursionId(inputBenefit.resourceId());
                case SERVICE -> benefit.setServiceId(inputBenefit.resourceId());
            }
            repository.insert(benefit);
        }
        repository.increment(template); repository.flush(); return versionView(version);
    }

    @Transactional
    public VersionView publish(UUID versionId, Revision input) {
        UUID actor=maker();
        var found=require(CruisePackageTemplateVersion.class,versionId,false);
        var template=require(CruisePackageTemplate.class,found.getTemplateId(),true);
        var version=require(CruisePackageTemplateVersion.class,versionId,true);
        revision(version.getRevision(),input.expectedRevision());
        if(!"DRAFT".equals(version.getStatus())) fail("FARE_VERSION_IMMUTABLE","Phiên bản đã phát hành không được sửa hoặc phát hành lại.");
        for(var existing:repository.versions(template.getId())) {
            if("PUBLISHED".equals(existing.getStatus())) {
                if(existing.getVersionNumber()>=version.getVersionNumber()) fail("FARE_VERSION_ORDER_INVALID","Phải phát hành phiên bản mới hơn phiên bản hiện hành.");
                existing.setStatus("SUPERSEDED");
            }
        }
        for(var benefit:repository.benefits(versionId)) {
            reference(resourceType(benefit),resourceId(benefit));
            var blockers=benefitUsage.blockers(benefit);
            if(!blockers.isEmpty())throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"BENEFIT_USAGE_CONFIGURATION_REQUIRED",
                    "Chưa đủ cấu hình sử dụng quyền lợi để phát hành mẫu.",Map.of("blockers",blockers));
        }
        repository.flush(); // Retire the previous publication before the partial unique index sees the new one.
        version.setStatus("PUBLISHED"); version.setPublishedBy(actor); version.setPublishedAt(Instant.now());
        repository.increment(template); repository.flush(); return versionView(version);
    }

    @Transactional
    public OfferView createOffer(UUID cruiseId, OfferInput input) {
        UUID actor=maker(); lockEditableCruise(cruiseId);
        var version=offerVersion(input); UUID shipId=currentShip(cruiseId);
        var offer=new DepartureCruisePackage(); offer.setId(UUID.randomUUID()); offer.setCruiseId(cruiseId);
        offer.setShipId(shipId); offer.setCreatedAt(Instant.now()); offer.setCreatedBy(actor);
        offer.setStatus("DRAFT");
        applyOffer(offer,input,version); repository.insert(offer); repository.flush(); return offerView(offer);
    }

    @Transactional
    public OfferView upgradeOffer(UUID cruiseId, UUID offerId, UpgradeOffer input) {
        maker(); lockEditableCruise(cruiseId);
        var offer=offer(cruiseId,offerId,true); revision(offer.getRevision(),input.expectedRevision());
        // A different product version is an explicit action. Old price approvals remain history only.
        if(offer.getTemplateVersionId().equals(input.offer().templateVersionId()))
            fail("FARE_NEW_VERSION_REQUIRED","Thao tác nâng phiên bản phải chọn phiên bản mẫu khác.");
        var version=offerVersion(input.offer());
        var old=require(CruisePackageTemplateVersion.class,offer.getTemplateVersionId(),false);
        if(!old.getTemplateId().equals(version.getTemplateId())) fail("FARE_TEMPLATE_MISMATCH","Chỉ nâng phiên bản trong cùng mẫu gói vé.");
        offer.setShipId(currentShip(cruiseId)); applyOffer(offer,input.offer(),version);
        offer.setStatus("DRAFT"); offer.setPublishedAt(null); offer.setPublishedBy(null);
        offer.setSaleEnabled(false);
        offer.setCommercialRevision(offer.getCommercialRevision()+1);
        repository.flush(); return offerView(offer);
    }

    @Transactional
    public OfferView createPrice(UUID cruiseId, UUID offerId, CreatePrice input) {
        UUID actor=maker(); lockPricingCruise(cruiseId); var offer=offer(cruiseId,offerId,true);
        revision(offer.getRevision(),input.expectedOfferRevision());
        var price=new DepartureCruisePackagePrice(); price.setId(UUID.randomUUID()); price.setOfferId(offerId);
        price.setTemplateVersionId(offer.getTemplateVersionId());
        price.setOfferCommercialRevision(offer.getCommercialRevision());
        price.setRevisionNumber(repository.prices(offerId).stream().mapToInt(DepartureCruisePackagePrice::getRevisionNumber).max().orElse(0)+1);
        price.setAmount(input.amount()); price.setCurrencyCode("USD"); price.setPriceUnit("PERSON"); price.setStatus("DRAFT");
        price.setCreatedAt(Instant.now()); price.setCreatedBy(actor); repository.insert(price);
        repository.increment(offer); repository.flush(); return offerView(offer);
    }

    @Transactional
    public OfferView bindBenefit(UUID cruiseId, UUID offerId, BindBenefit input) {
        maker(); lockEditableCruise(cruiseId); var offer=offer(cruiseId,offerId,true);
        revision(offer.getRevision(),input.expectedOfferRevision());
        if(!"DRAFT".equals(offer.getStatus())) fail("FARE_OFFER_IMMUTABLE","Không sửa quyền lợi của gói vé đã phát hành; tạo cấu hình phiên bản mới.");
        var benefit=require(CruisePackageBenefit.class,input.benefitId(),false);
        if(!benefit.getTemplateVersionId().equals(offer.getTemplateVersionId())) fail("FARE_BENEFIT_VERSION_MISMATCH","Quyền lợi không thuộc phiên bản mẫu của gói vé.");
        ResourceType type=resourceType(benefit); reference(type,input.occurrenceId());
        requireOccurrence(type,input.occurrenceId(),cruiseId);
        if(type==ResourceType.SERVICE&&!benefit.getServiceId().equals(input.occurrenceId()))
            fail("FARE_SERVICE_MISMATCH","Phải giữ đúng dịch vụ trong định nghĩa quyền lợi.");
        var activeBenefitIds=repository.benefits(offer.getTemplateVersionId()).stream().map(CruisePackageBenefit::getId).collect(java.util.stream.Collectors.toSet());
        if(repository.bindings(offerId).stream().anyMatch(b->activeBenefitIds.contains(b.getBenefitId())&&!b.getBenefitId().equals(benefit.getId())
                &&(input.occurrenceId().equals(b.getOnboardActivityId())||input.occurrenceId().equals(b.getShoreExcursionId())||input.occurrenceId().equals(b.getServiceId()))))
            fail("FARE_BINDING_RESOURCE_DUPLICATE","Lần phục vụ đã thuộc một quyền lợi khác của phiên bản này; không cấp quyền lợi trùng.");
        boolean duplicate=repository.bindings(offerId).stream().anyMatch(b -> b.getBenefitId().equals(benefit.getId())
                &&(input.occurrenceId().equals(b.getOnboardActivityId())||input.occurrenceId().equals(b.getShoreExcursionId())||input.occurrenceId().equals(b.getServiceId())));
        if(duplicate) fail("FARE_BINDING_DUPLICATE","Lần phục vụ đã được liên kết với quyền lợi này.");
        var binding=new DepartureCruisePackageBenefit(); binding.setId(UUID.randomUUID()); binding.setOfferId(offerId); binding.setBenefitId(benefit.getId());
        binding.setOnboardActivityId(type==ResourceType.ACTIVITY?input.occurrenceId():null);
        binding.setShoreExcursionId(type==ResourceType.EXCURSION?input.occurrenceId():null);
        binding.setServiceId(type==ResourceType.SERVICE?input.occurrenceId():null);
        repository.insert(binding);
        offer.setCommercialRevision(offer.getCommercialRevision()+1);
        repository.increment(offer); repository.flush(); return offerView(offer);
    }

    @Transactional
    public OfferView reviewPrice(UUID cruiseId, UUID offerId, UUID priceId, String action, ReviewPrice input) {
        UUID actor="submit".equals(action)?maker():checker();
        lockPricingCruise(cruiseId); var offer=offer(cruiseId,offerId,true);
        var price=require(DepartureCruisePackagePrice.class,priceId,true);
        if(!price.getOfferId().equals(offerId)) fail("FARE_PRICE_OFFER_MISMATCH","Revision giá không thuộc gói vé của chuyến.");
        revision(price.getRevision(),input.expectedRevision());
        if(!price.getTemplateVersionId().equals(offer.getTemplateVersionId())||price.getOfferCommercialRevision()!=offer.getCommercialRevision())
            fail("FARE_PRICE_VERSION_STALE","Nội dung thương mại đã đổi; cần tạo revision giá mới.");
        if("submit".equals(action)) {
            if(!"DRAFT".equals(price.getStatus())) fail("FARE_PRICE_STATE_INVALID","Chỉ gửi duyệt revision giá nháp.");
            if(repository.prices(offerId).stream().anyMatch(p -> "SUBMITTED".equals(p.getStatus())
                    &&p.getTemplateVersionId().equals(offer.getTemplateVersionId())&&p.getOfferCommercialRevision()==offer.getCommercialRevision()))
                fail("FARE_PRICE_REVIEW_PENDING","Gói vé đã có một revision giá chờ duyệt.");
            price.setStatus("SUBMITTED"); price.setSubmittedBy(actor); price.setSubmittedAt(Instant.now());
        } else {
            if(!List.of("approve","return").contains(action)) fail("FARE_REVIEW_ACTION_INVALID","Thao tác duyệt không hợp lệ.");
            if(!"SUBMITTED".equals(price.getStatus())) fail("FARE_PRICE_STATE_INVALID","Chỉ xét duyệt revision giá đã gửi.");
            if(actor.equals(price.getCreatedBy())||actor.equals(price.getSubmittedBy()))
                throw new BusinessException(HttpStatus.FORBIDDEN,"MAKER_CHECKER_VIOLATION","Người lập/gửi giá không được tự xét duyệt.");
            if("return".equals(action)&&(input.reason()==null||input.reason().isBlank()))
                fail("FARE_REVIEW_REASON_REQUIRED","Cần nêu lý do trả giá.");
            price.setStatus("approve".equals(action)?"APPROVED":"RETURNED");
            price.setReviewedBy(actor); price.setReviewedAt(Instant.now()); price.setReviewReason(input.reason());
        }
        repository.increment(offer); repository.flush(); return offerView(offer);
    }

    public Eligibility eligibility(DepartureCruisePackage offer) {
        return eligibility(offer,false);
    }

    @Transactional
    public OfferView publishOffer(UUID cruiseId,UUID offerId,Revision input) {
        UUID actor=maker(); lockEditableCruise(cruiseId); var offer=offer(cruiseId,offerId,true);
        revision(offer.getRevision(),input.expectedRevision());
        if(!"DRAFT".equals(offer.getStatus())) fail("FARE_OFFER_STATE_INVALID","Gói vé đã được phát hành.");
        var assessment=eligibility(offer,true);
        if(!assessment.eligible()) throw new BusinessException(HttpStatus.CONFLICT,"FARE_OFFER_INELIGIBLE","Gói vé chưa đủ điều kiện phát hành.",Map.of("farePackageEligibility",assessment));
        offer.setStatus("PUBLISHED"); offer.setPublishedBy(actor); offer.setPublishedAt(Instant.now());
        repository.flush(); return offerView(offer);
    }

    private Eligibility eligibility(DepartureCruisePackage offer,boolean publishing) {
        return eligibility(offer, publishing, true);
    }

    private Eligibility eligibility(DepartureCruisePackage offer,boolean publishing,boolean checkFreeCabinInventory) {
        var blockers=new ArrayList<Blocker>(); var version=require(CruisePackageTemplateVersion.class,offer.getTemplateVersionId(),false);
        if(!publishing&&!"PUBLISHED".equals(offer.getStatus())) blockers.add(new Blocker("OFFER_NOT_PUBLISHED","Gói vé của chuyến chưa được phát hành.",offer.getId()));
        if(!List.of("PUBLISHED","SUPERSEDED").contains(version.getStatus()))
            blockers.add(new Blocker("TEMPLATE_NOT_PUBLISHED","Phiên bản mẫu chưa được phát hành.",version.getId()));
        if(!Objects.equals(currentShip(offer.getCruiseId()),offer.getShipId()))
            blockers.add(new Blocker("SHIP_CHANGED","Tàu của chuyến không khớp cấu hình gói vé.",offer.getShipId()));
        var mapping=cabinMappings.assess(offer.getShipId(),offer.getLogicalCategoryId(),version.getId());
        blockers.addAll(mapping.blockers());
        if(checkFreeCabinInventory&&mapping.blockers().isEmpty()&&!cabinMappings.hasInventory(offer.getCruiseId(),mapping.cabinIds()))
            blockers.add(new Blocker("CABIN_MAPPING_INVENTORY_UNAVAILABLE","Mapping hợp lệ nhưng chưa chứng minh cabin vật lý còn khả dụng cho hạng thương mại này.",offer.getLogicalCategoryId()));
        if(currentApprovedPrice(offer)==null) blockers.add(new Blocker("PRICE_NOT_APPROVED","Chưa có revision giá được duyệt cho phiên bản mẫu này.",offer.getId()));
        Instant now=Instant.now();
        if((!publishing&&now.isBefore(offer.getSalesStart()))||!now.isBefore(offer.getSalesEnd()))
            blockers.add(new Blocker("OUTSIDE_SALES_WINDOW","Gói vé nằm ngoài thời gian bán đã cấu hình.",offer.getId()));
        var bindings=repository.bindings(offer.getId());
        for(var benefit:repository.benefits(version.getId())) {
            blockers.addAll(benefitUsage.blockers(benefit));
            if("NOT_INCLUDED".equals(benefit.getInclusion())) continue;
            var slots=bindings.stream().filter(b -> b.getBenefitId().equals(benefit.getId())).toList();
            if(slots.isEmpty()) { blockers.add(new Blocker("BENEFIT_OCCURRENCE_UNBOUND","Quyền lợi chưa được liên kết tường minh với lần phục vụ trên chuyến.",benefit.getId())); continue; }
            for(var binding:slots) {
                if(binding.getServiceId()!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"DeckServiceAssignment\" a JOIN \"Deck\" d USING(deck_id) JOIN \"Service\" s USING(service_id) WHERE d.ship_id=? AND a.service_id=? AND s.status='ACTIVE')",Boolean.class,offer.getShipId(),binding.getServiceId())))
                    blockers.add(new Blocker("SERVICE_NOT_AVAILABLE_ON_SHIP","Dịch vụ trong quyền lợi chưa được cấu hình trên tàu hoặc không hoạt động.",binding.getServiceId()));
                UUID occurrence=binding.getOnboardActivityId()!=null?binding.getOnboardActivityId():binding.getShoreExcursionId()!=null?binding.getShoreExcursionId():binding.getServiceId();
                if(!sameCruise(resourceType(benefit),occurrence,offer.getCruiseId()))
                    blockers.add(new Blocker("BENEFIT_OCCURRENCE_WRONG_CRUISE","Lần phục vụ không thuộc đúng chuyến khởi hành.",benefit.getId()));
            }
            if(List.of("INCLUDED","LIMITED_INCLUDED").contains(benefit.getInclusion())
                    &&slots.stream().noneMatch(b -> available(b,offer.getCruiseId())))
                blockers.add(new Blocker("INCLUDED_BENEFIT_UNAVAILABLE","Không còn lần phục vụ khả dụng cho quyền lợi đã bao gồm. Mua gói vé không tự giữ chỗ phục vụ.",benefit.getId()));
        }
        return new Eligibility(blockers.isEmpty(),List.copyOf(blockers));
    }

    public List<UUID> eligibleOfferIds(UUID cruiseId) {
        return repository.offers(cruiseId).stream().filter(o -> eligibility(o).eligible()).map(DepartureCruisePackage::getId).sorted().toList();
    }
    public Set<UUID> sellableCabinIds(UUID cruiseId) {
        return repository.offers(cruiseId).stream().filter(o->o.isSaleEnabled()&&eligibility(o).eligible())
                .flatMap(o->cabinMappings.assess(o.getShipId(),o.getLogicalCategoryId(),o.getTemplateVersionId()).cabinIds().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    /** Sale configuration gate without free-inventory filtering, so zero stock remains SOLD_OUT. */
    public Set<UUID> saleScopeCabinIds(UUID cruiseId) {
        return repository.offers(cruiseId).stream().filter(o->o.isSaleEnabled()&&eligibility(o,false,false).eligible())
                .flatMap(o->cabinMappings.assess(o.getShipId(),o.getLogicalCategoryId(),o.getTemplateVersionId()).cabinIds().stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
    public boolean requiresFareSelection(UUID cruiseId) {
        return repository.offers(cruiseId).stream().anyMatch(DepartureCruisePackage::isSaleRequested);
    }
    public Set<com.casms.ship.enums.CabinType> sellableCategories(UUID cruiseId) {
        Set<com.casms.ship.enums.CabinType> result=new HashSet<>();
        for(UUID id:saleScopeCabinIds(cruiseId)){
            String type=jdbc.queryForObject("SELECT cabin_type::text FROM \"Cabin\" WHERE cabin_id=?",String.class,id);
            result.add(com.casms.ship.enums.CabinType.valueOf(type));
        }
        return Set.copyOf(result);
    }
    public CruisePackageCatalog available(UUID cruiseId) {
        cruise(cruiseId); cruiseServices.getObject().requirePublicSaleVisible(cruiseId);
        var offers=repository.offers(cruiseId).stream().filter(o->o.isSaleRequested()&&o.isSaleEnabled()&&eligibility(o).eligible())
                .map(o->{var price=currentApprovedPrice(o);return new AvailableCruisePackage(cruiseId,preparePurchase(cruiseId,o.getId(),price.getId()),"USD","PERSON");}).toList();
        return new CruisePackageCatalog(cruiseId,requiresFareSelection(cruiseId),offers);
    }
    /** Booking/quote callers already serialize on Cruise. Never interpret TourPackage as a Fare Offer. */
    public com.casms.cruisepackage.dto.CruisePackagePurchaseSelection preparePurchase(UUID cruiseId,UUID offerId,UUID priceId) {
        var offer=offer(cruiseId,offerId,false);
        if(!offer.isSaleEnabled()||!"PUBLISHED".equals(offer.getStatus()))
            throw new BusinessException(HttpStatus.NOT_FOUND,"FARE_OFFER_NOT_AVAILABLE","Gói vé không khả dụng để mua trên chuyến khởi hành này.");
        var assessment=eligibility(offer);
        if(!assessment.eligible())throw new BusinessException(HttpStatus.CONFLICT,"FARE_OFFER_INELIGIBLE",
                "Gói du thuyền chưa đủ điều kiện mua.",purchaseFailureDetails(cruiseId,offerId));
        var price=currentApprovedPrice(offer);
        if(price==null||priceId==null||!price.getId().equals(priceId))
            throw new BusinessException(HttpStatus.CONFLICT,"FARE_QUOTED_PRICE_STALE","Phiên bản giá đã thay đổi; cần lấy báo giá mới.");
        var view=offerView(offer); var version=view.template().version();
        var benefits=view.template().benefits().stream().map(benefit -> {
            var occurrences=view.bindings().stream().filter(b->b.getBenefitId().equals(benefit.getId()))
                    .map(b->new com.casms.cruisepackage.dto.CruisePackagePurchaseSelection.Occurrence(resourceType(benefit).name(),
                            b.getOnboardActivityId()!=null?b.getOnboardActivityId():b.getShoreExcursionId()!=null?b.getShoreExcursionId():b.getServiceId(),
                            view.bindingNames().get(b.getId()))).toList();
            return new com.casms.cruisepackage.dto.CruisePackagePurchaseSelection.Benefit(benefit.getId(),benefit.getInclusion(),
                    benefit.getOnboardActivityId(),benefit.getShoreExcursionId(),benefit.getServiceId(),benefit.getQuantityLimit(),
                    benefit.getEligibility(),view.template().resourceNames().get(benefit.getId()),occurrences,
                    benefit.getUsageUnit(),benefit.getRequiresReservation(),benefit.getUsageStartsAt(),benefit.getUsageEndsAt(),benefit.getUsagePolicy(),benefit.getServiceUnitCode());
        }).toList();
        return new com.casms.cruisepackage.dto.CruisePackagePurchaseSelection(offerId,version.getId(),priceId,offer.getShipId(),
                offer.getLogicalCategory(),com.casms.ship.enums.CabinType.valueOf(offer.getProposedCabinType()),price.getAmount(),
                version.getName(),version.getDescription(),version.getVersionNumber(),price.getRevisionNumber(),offer.getCommercialRevision(),benefits,
                offer.getLogicalCategoryId(),view.cabinMapping().mappingId(),view.cabinMapping().cabinIds());
    }
    /** Caller holds the Cruise lock and validates this booking's existing allocation separately. */
    public void validateHeldPurchase(UUID cruiseId,com.casms.cruisepackage.dto.CruisePackagePurchaseSelection selection) {
        var offer=offer(cruiseId,selection.offerId(),false);
        if(!offer.isSaleEnabled()||!"PUBLISHED".equals(offer.getStatus())
                ||!eligibility(offer,false,false).eligible())
            throw new BusinessException(HttpStatus.CONFLICT,"FARE_OFFER_INELIGIBLE",
                    "Gói du thuyền không còn đủ điều kiện xác nhận.",purchaseFailureDetails(cruiseId,offer.getId()));
        var price=currentApprovedPrice(offer);
        if(price==null||!price.getId().equals(selection.priceRevisionId())
                ||!offer.getTemplateVersionId().equals(selection.templateVersionId())
                ||offer.getCommercialRevision()!=selection.commercialRevision())
            throw new BusinessException(HttpStatus.CONFLICT,"FARE_QUOTED_PRICE_STALE",
                    "Cấu hình hoặc revision giá đã thay đổi; cần kiểm tra lại báo giá.",purchaseFailureDetails(cruiseId,offer.getId()));
        cabinMappings.requirePurchasedCabins(selection);
    }

    private Map<String,Object> purchaseFailureDetails(UUID cruiseId,UUID offerId) {
        // Public purchase errors must not expose internal readiness assessments or dependency IDs.
        return Map.of("scope","CRUISE_PACKAGE_OFFER","targetId",offerId,"cruiseId",cruiseId,
                "allowedActions",List.of("REFRESH_QUOTE"),"evaluatedAt",Instant.now());
    }

    public Set<UUID> requestedSaleOfferIds(UUID cruiseId) {
        var ids=new TreeSet<UUID>();
        repository.offers(cruiseId).stream().filter(DepartureCruisePackage::isSaleRequested).map(DepartureCruisePackage::getId).forEach(ids::add);
        return Set.copyOf(ids);
    }
    public String saleStateFingerprint(UUID cruiseId) {
        var state=new StringBuilder();
        for(var offer:repository.offers(cruiseId)) {
            state.append(offer.getId()).append(':').append(offer.getRevision()).append(':').append(offer.getTemplateVersionId());
            state.append(':').append(cabinMappings.evidence(offer.getShipId()));
            var price=currentApprovedPrice(offer);
            state.append(':').append(price==null?"NONE":price.getId());
            for(var blocker:eligibility(offer).blockers()) state.append(':').append(blocker.code()).append(':').append(blocker.dependencyId());
        }
        state.append(jdbc.queryForList("SELECT addon_offer_id,revision,published_at,sales_windows,amount FROM \"CruisePackageAddonOffer\" WHERE cruise_id=? ORDER BY addon_offer_id",cruiseId));
        return CruiseMainflowService.hash(state.toString());
    }
    /** Called only by the existing Open Sale transaction, after the existing Cruise lock. */
    @Transactional
    public void activateSaleScope(UUID cruiseId,List<UUID> requestedIds) {
        actor(Role.OPERATIONS_MANAGER,"CRUISE_OPEN_SALE");
        plans.lockOperational(cruiseId);
        if(requestedIds==null||requestedIds.isEmpty()||requestedIds.stream().anyMatch(Objects::isNull))
            fail("FARE_SALE_SCOPE_REQUIRED","Chọn ít nhất một gói vé Fare Package để mở bán.");
        var requested=new TreeSet<>(requestedIds); List<DepartureCruisePackage> eligible=new ArrayList<>();
        Map<UUID,Eligibility> assessments=new LinkedHashMap<>();
        for(UUID id:requested) {
            var offer=offer(cruiseId,id,true); var assessment=eligibility(offer); assessments.put(id,assessment);
            if(assessment.eligible()) eligible.add(offer);
        }
        if(eligible.size()!=requested.size()) throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY,"FARE_SALE_SCOPE_INELIGIBLE",
                "Mọi gói du thuyền được chọn phải đủ điều kiện mở bán.",Map.of("farePackageEligibility",assessments));
        var approvedIds=eligible.stream().map(DepartureCruisePackage::getId).collect(java.util.stream.Collectors.toSet());
        for(var offer:repository.offers(cruiseId)) {
            offer.setSaleRequested(requested.contains(offer.getId()));
            offer.setSaleEnabled(approvedIds.contains(offer.getId()));
        }
        repository.flush();
    }

    private OfferView offerView(DepartureCruisePackage offer) {
        var current=currentApprovedPrice(offer);
        var bindings=repository.bindings(offer.getId()); Map<UUID,String> names=new LinkedHashMap<>();
        for(var binding:bindings) {
            if(binding.getOnboardActivityId()!=null) names.put(binding.getId(),jdbc.queryForObject("SELECT activity_name FROM \"OnboardActivity\" WHERE onboard_activity_id=?",String.class,binding.getOnboardActivityId()));
            else if(binding.getShoreExcursionId()!=null) names.put(binding.getId(),jdbc.queryForObject("SELECT excursion_name FROM \"ShoreExcursion\" WHERE shore_excursion_id=?",String.class,binding.getShoreExcursionId()));
            else names.put(binding.getId(),jdbc.queryForObject("SELECT service_name FROM \"Service\" WHERE service_id=?",String.class,binding.getServiceId()));
        }
        return new OfferView(offer,versionView(require(CruisePackageTemplateVersion.class,offer.getTemplateVersionId(),false)),bindings,Map.copyOf(names),repository.prices(offer.getId()),current==null?null:current.getId(),eligibility(offer),cabinMappings.assess(offer.getShipId(),offer.getLogicalCategoryId(),offer.getTemplateVersionId()));
    }
    private DepartureCruisePackagePrice currentApprovedPrice(DepartureCruisePackage offer) {
        return repository.prices(offer.getId()).stream().filter(p -> "APPROVED".equals(p.getStatus())
                &&p.getTemplateVersionId().equals(offer.getTemplateVersionId())&&p.getOfferCommercialRevision()==offer.getCommercialRevision()).findFirst().orElse(null);
    }
    private VersionView versionView(CruisePackageTemplateVersion version) {
        var benefits=repository.benefits(version.getId()); Map<UUID,String> names=new LinkedHashMap<>();
        for(var benefit:benefits) {
            String sql=switch(resourceType(benefit)) {
                case ACTIVITY -> "SELECT activity_name FROM \"OnboardActivity\" WHERE onboard_activity_id=?";
                case EXCURSION -> "SELECT excursion_name FROM \"ShoreExcursion\" WHERE shore_excursion_id=?";
                case SERVICE -> "SELECT service_name FROM \"Service\" WHERE service_id=?";
            };
            names.put(benefit.getId(),jdbc.queryForObject(sql,String.class,resourceId(benefit)));
        }
        return new VersionView(version,benefits,Map.copyOf(names),cabinMappings.categories(version.getId()));
    }
    private CruisePackageTemplateVersion offerVersion(OfferInput input) {
        var version=require(CruisePackageTemplateVersion.class,input.templateVersionId(),false);
        if(!"PUBLISHED".equals(version.getStatus())) fail("FARE_VERSION_NOT_CURRENT_PUBLISHED","Cấu hình mới phải chọn phiên bản đang phát hành.");
        if(!version.getLogicalCategories().contains(input.logicalCategory())) fail("FARE_CATEGORY_UNSUPPORTED","Phiên bản mẫu không hỗ trợ hạng phòng logic đã chọn.");
        if(!cabinMappings.requireCategory(input.logicalCategoryId(),version.getId()).name().equals(input.logicalCategory()))
            fail("CABIN_CATEGORY_REFERENCE_INVALID","Tên hiển thị không khớp ID hạng thương mại của phiên bản mẫu.");
        if(!input.salesStart().isBefore(input.salesEnd())) fail("FARE_SALES_WINDOW_INVALID","Thời điểm bắt đầu bán phải trước thời điểm kết thúc.");
        return version;
    }
    private void applyOffer(DepartureCruisePackage offer,OfferInput input,CruisePackageTemplateVersion version) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"Cabin\" WHERE ship_id=? AND cabin_type::text=?)",Boolean.class,offer.getShipId(),input.proposedCabinType().name())))
            fail("FARE_CABIN_TYPE_NOT_ON_SHIP","Hạng phòng dự kiến không có trong dữ liệu cabin của tàu đã phân công.");
        offer.setTemplateVersionId(version.getId()); offer.setLogicalCategory(input.logicalCategory());
        offer.setLogicalCategoryId(input.logicalCategoryId());
        offer.setProposedCabinType(input.proposedCabinType().name()); offer.setSalesStart(input.salesStart()); offer.setSalesEnd(input.salesEnd());
    }
    private DepartureCruisePackage offer(UUID cruiseId,UUID id,boolean lock) {
        var offer=require(DepartureCruisePackage.class,id,lock);
        if(!offer.getCruiseId().equals(cruiseId)) fail("FARE_OFFER_CRUISE_MISMATCH","Gói vé không thuộc chuyến khởi hành.");
        return offer;
    }
    private void lockEditableCruise(UUID cruiseId) { lockPricingCruise(cruiseId); }
    private void lockPricingCruise(UUID cruiseId) {
        plans.lockOperational(cruiseId);
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT status IN ('DRAFT','PUBLISHED') FROM \"Cruise\" WHERE cruise_id=?",Boolean.class,cruiseId)))
            fail("FARE_PRICING_CRUISE_STATE_INVALID","Trạng thái chuyến khởi hành không cho phép lập hoặc xét duyệt giá mới.");
    }
    private Map<String,Object> cruise(UUID id) {
        var rows=jdbc.queryForList("SELECT ship_id FROM \"Cruise\" WHERE cruise_id=?",id);
        if(rows.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND,"CRUISE_NOT_FOUND","Không tìm thấy chuyến khởi hành.");
        return rows.getFirst();
    }
    private UUID currentShip(UUID cruiseId) {
        UUID ship=(UUID)cruise(cruiseId).get("ship_id");
        if(ship==null) fail("FARE_SHIP_REQUIRED","Chuyến khởi hành chưa được phân công tàu.");
        return ship;
    }
    private <T> T require(Class<T> type,UUID id,boolean lock) {
        T value=repository.find(type,id,lock);
        if(value==null) throw new BusinessException(HttpStatus.NOT_FOUND,"FARE_RESOURCE_NOT_FOUND","Không tìm thấy dữ liệu gói vé.");
        return value;
    }
    private void revision(long actual,Long expected) {
        if(expected==null||actual!=expected) throw new BusinessException(HttpStatus.CONFLICT,"STALE_REVISION","Dữ liệu đã thay đổi. Đối chiếu phiên bản mới trước khi thao tác.",Map.of("currentRevision",actual));
    }
    private void reference(ResourceType type,UUID id) {
        String sql=switch(type) {
            case ACTIVITY -> "SELECT EXISTS(SELECT 1 FROM \"OnboardActivity\" WHERE onboard_activity_id=?)";
            case EXCURSION -> "SELECT EXISTS(SELECT 1 FROM \"ShoreExcursion\" WHERE shore_excursion_id=?)";
            case SERVICE -> "SELECT EXISTS(SELECT 1 FROM \"Service\" WHERE service_id=?)";
        };
        if(!Boolean.TRUE.equals(jdbc.queryForObject(sql,Boolean.class,id))) fail("FARE_BENEFIT_RESOURCE_NOT_FOUND","Quyền lợi phải tham chiếu danh mục hiện hữu.");
    }
    private void requireSharedReference(ResourceType type,UUID id) {
        boolean operational=switch(type) {
            case ACTIVITY -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"OnboardActivity\" WHERE onboard_activity_id=? AND (cruise_id IS NOT NULL OR deck_id IS NOT NULL OR cabin_id IS NOT NULL))",Boolean.class,id));
            case EXCURSION -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"ShoreExcursion\" WHERE shore_excursion_id=? AND cruise_stop_id IS NOT NULL)",Boolean.class,id));
            case SERVICE -> false;
        };
        if(operational) fail("FARE_SHARED_RESOURCE_REQUIRED","Mẫu dùng chung không được trỏ trực tiếp vào hoạt động/địa điểm của một chuyến hoặc tàu cụ thể.");
    }
    private void requireOccurrence(ResourceType type,UUID id,UUID cruiseId) {
        if(!sameCruise(type,id,cruiseId)) fail("FARE_BENEFIT_CRUISE_MISMATCH","Lần phục vụ phải thuộc đúng chuyến khởi hành; không liên kết theo tên.");
    }
    private boolean sameCruise(ResourceType type,UUID id,UUID cruiseId) {
        return switch(type) {
            case ACTIVITY -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"OnboardActivity\" WHERE onboard_activity_id=? AND cruise_id=?)",Boolean.class,id,cruiseId));
            case EXCURSION -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"ShoreExcursion\" e JOIN \"CruiseStop\" s USING(cruise_stop_id) JOIN \"Itinerary\" i USING(itinerary_id) WHERE e.shore_excursion_id=? AND i.cruise_id=?)",Boolean.class,id,cruiseId));
            case SERVICE -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM \"Service\" WHERE service_id=?)",Boolean.class,id));
        };
    }
    private boolean available(DepartureCruisePackageBenefit binding,UUID cruiseId) {
        if(binding.getOnboardActivityId()!=null) return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM \"OnboardActivity\" a WHERE a.onboard_activity_id=? AND a.cruise_id=? AND a.status='OPEN' AND a.end_time>now() AND a.capacity>COALESCE((SELECT sum(r.quantity) FROM \"Registration\" r WHERE r.onboard_activity_id=a.onboard_activity_id AND r.status IN ('PENDING','CONFIRMED')),0))",Boolean.class,binding.getOnboardActivityId(),cruiseId));
        if(binding.getShoreExcursionId()!=null) return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM \"ShoreExcursion\" e WHERE e.shore_excursion_id=? AND e.status='OPEN' AND e.planned_return_time>now() AND e.maximum_capacity>COALESCE((SELECT sum(r.quantity) FROM \"Registration\" r WHERE r.shore_excursion_id=e.shore_excursion_id AND r.status IN ('PENDING','CONFIRMED')),0))",Boolean.class,binding.getShoreExcursionId()));
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM "Service" s WHERE s.service_id=? AND s.status='ACTIVE'
                  AND (s.requires_schedule OR s.capacity IS NULL OR s.capacity>COALESCE((SELECT sum(r.quantity) FROM "ServiceRequest" r
                    WHERE r.service_id=s.service_id AND r.status IN ('REQUESTED','ACCEPTED','IN_PROGRESS')),0))
                  AND (NOT s.requires_schedule OR EXISTS(SELECT 1 FROM "ServiceSchedule" w
                    LEFT JOIN "CruiseStop" p USING(cruise_stop_id) LEFT JOIN "Itinerary" i USING(itinerary_id)
                    WHERE w.service_id=s.service_id AND (w.cruise_id=? OR i.cruise_id=?) AND w.status='OPEN' AND w.ends_at>now()
                      AND w.capacity>COALESCE((SELECT sum(r.quantity) FROM "ServiceRequest" r WHERE r.service_schedule_id=w.service_schedule_id
                        AND r.status IN ('REQUESTED','ACCEPTED','IN_PROGRESS')),0))))
                """,Boolean.class,binding.getServiceId(),cruiseId,cruiseId));
    }
    private ResourceType resourceType(CruisePackageBenefit b) { return b.getOnboardActivityId()!=null?ResourceType.ACTIVITY:b.getShoreExcursionId()!=null?ResourceType.EXCURSION:ResourceType.SERVICE; }
    private UUID resourceId(CruisePackageBenefit b) { return b.getOnboardActivityId()!=null?b.getOnboardActivityId():b.getShoreExcursionId()!=null?b.getShoreExcursionId():b.getServiceId(); }
    private UUID maker() { return actor(Role.ACTIVITY_SERVICE_MANAGER,"TOUR_PACKAGE_MANAGE"); }
    private UUID checker() { return actor(Role.OPERATIONS_MANAGER,"CRUISE_PRICING_APPROVE"); }
    private UUID actor(Role role,String permission) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(!hasActor(role,permission))
            throw new BusinessException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Không có quyền thao tác gói vé.");
        return ((CasmsPrincipal)auth.getPrincipal()).getUserId();
    }
    private boolean hasActor(Role role,String permission) {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        return auth!=null&&auth.isAuthenticated()&&auth.getPrincipal() instanceof CasmsPrincipal principal
                &&principal.hasRole(role)&&auth.getAuthorities().stream().anyMatch(a -> permission.equals(a.getAuthority()));
    }
    private void requireRead() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth==null||!auth.isAuthenticated()||!(auth.getPrincipal() instanceof CasmsPrincipal principal)
                ||List.of(Role.ACTIVITY_SERVICE_MANAGER,Role.OPERATIONS_MANAGER,Role.CRUISE_MANAGER,Role.BOOKING_AGENT).stream().noneMatch(principal::hasRole)
                ||auth.getAuthorities().stream().noneMatch(a -> Set.of("TOUR_PACKAGE_MANAGE","CRUISE_READ","CRUISE_PRICING_APPROVE","BOOKING_READ").contains(a.getAuthority())))
            throw new BusinessException(HttpStatus.FORBIDDEN,"ACCESS_DENIED","Không có quyền xem cấu hình gói vé.");
    }
    private void fail(String code,String message) { throw new BusinessException(code,message); }
}
