package com.casms.trip.service;

import com.casms.common.exception.BusinessException;
import com.casms.identity.entity.User;
import com.casms.identity.enums.Role;
import com.casms.identity.enums.UserStatus;
import com.casms.identity.repository.UserRepository;
import com.casms.trip.dto.request.TripCreateRequest;
import com.casms.trip.dto.request.TripDraftPatchRequest;
import com.casms.trip.dto.response.TripDetailResponse;
import com.casms.trip.dto.request.TripArchiveRequest;
import com.casms.trip.dto.request.TripDayManageRequest;
import com.casms.trip.dto.response.TripAuditResponse;
import com.casms.trip.dto.response.TripReviewQueueItemResponse;
import com.casms.trip.entity.Trip;
import com.casms.trip.entity.TripScheduleVersion;
import com.casms.trip.enums.TripDayManageAction;
import com.casms.trip.enums.TripDurationGroup;
import com.casms.trip.enums.TripScheduleVersionStatus;
import com.casms.trip.enums.TripStatus;
import com.casms.trip.repository.TripRepository;
import com.casms.trip.repository.TripScheduleVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TripPlanningPostgresIntegrationTest {

    @Autowired
    private TripService tripService;

    @Autowired
    private TripScheduleVersionService versionService;

    @Autowired
    private TripRepository tripRepository;

    @Autowired
    private TripScheduleVersionRepository versionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    private User testPlanner;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        testPlanner = new User();
        testPlanner.setUsername("planner-" + UUID.randomUUID().toString().substring(0, 8));
        testPlanner.setPasswordHash("$2a$10$abcdefghijklmnopqrstuv");
        testPlanner.setStatus(UserStatus.ACTIVE);
        testPlanner.setRoles(Set.of(Role.OPERATIONS_MANAGER));
        testPlanner = userRepository.save(testPlanner);
        actorId = testPlanner.getUserId();
    }

    // =========================================================================
    // BE-01: Revision Management on Real PostgreSQL
    // =========================================================================

    @Test
    @DisplayName("BE-01: Hibernate quản lý @Version, response trả đúng revision persistence và GET khớp")
    void testHibernateManagesRevisionPersistence() {
        TripCreateRequest req = new TripCreateRequest();
        req.setName("Postgres Revision Test Trip " + UUID.randomUUID().toString().substring(0, 6));
        req.setDurationGroup(TripDurationGroup.SHORT);
        req.setDurationDays(2);
        req.setPlannedScope("DOMESTIC");
        req.setDomesticRegions(List.of("BAC_BO"));

        TripDetailResponse created = tripService.createTrip(req, actorId);
        assertNotNull(created);
        assertEquals(1L, created.getRevision(), "Trip mới tạo phải có revision persistence là 1L");

        // Verify in DB
        Trip inDb = tripRepository.findById(created.getTripId()).orElseThrow();
        assertEquals(1L, inDb.getRevision());

        // Update with expectedRevision = 1L
        TripDraftPatchRequest patch1 = new TripDraftPatchRequest();
        patch1.setExpectedRevision(1L);
        patch1.setName("Postgres Revision Renamed Once");

        TripDetailResponse patched1 = tripService.patchTripDraft(created.getTripId(), patch1, actorId);
        assertNotNull(patched1);
        assertEquals(2L, patched1.getRevision(), "Sau cập nhật lần 1, revision persistence phải là 2L");

        // DB and GET must equal 2L
        Trip inDbAfterPatch1 = tripRepository.findById(created.getTripId()).orElseThrow();
        assertEquals(2L, inDbAfterPatch1.getRevision(), "DB persistence revision phải bằng 2L");

        TripDetailResponse getResponse = tripService.getTripDetail(created.getTripId());
        assertEquals(2L, getResponse.getRevision(), "GET detail response phải bằng 2L");

        // Update with expectedRevision = 2L (succeeds)
        TripDraftPatchRequest patch2 = new TripDraftPatchRequest();
        patch2.setExpectedRevision(2L);
        patch2.setDescription("Updated description at revision 2");

        TripDetailResponse patched2 = tripService.patchTripDraft(created.getTripId(), patch2, actorId);
        assertEquals(3L, patched2.getRevision(), "Sau cập nhật lần 2, revision persistence phải là 3L");

        Trip inDbAfterPatch2 = tripRepository.findById(created.getTripId()).orElseThrow();
        assertEquals(3L, inDbAfterPatch2.getRevision(), "DB persistence revision phải bằng 3L");
    }

    @Test
    @DisplayName("BE-01: Gửi request với revision cũ (stale) bị từ chối STALE_REVISION và không làm thay đổi dữ liệu")
    void testStaleRevisionRejectedWithoutDataChange() {
        TripCreateRequest req = new TripCreateRequest();
        req.setName("Postgres Stale Test Trip " + UUID.randomUUID().toString().substring(0, 6));
        req.setDurationGroup(TripDurationGroup.SHORT);
        req.setDurationDays(1);

        TripDetailResponse created = tripService.createTrip(req, actorId);

        // Advance revision from 1 to 2
        TripDraftPatchRequest patch1 = new TripDraftPatchRequest();
        patch1.setExpectedRevision(1L);
        patch1.setName("Trip Name at Rev 2");
        tripService.patchTripDraft(created.getTripId(), patch1, actorId);

        // Attempt patch with stale revision 1L
        TripDraftPatchRequest stalePatch = new TripDraftPatchRequest();
        stalePatch.setExpectedRevision(1L);
        stalePatch.setName("Illegal Overwrite Name");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tripService.patchTripDraft(created.getTripId(), stalePatch, actorId));
        assertEquals("STALE_REVISION", ex.getCode());

        // Check DB: data remains "Trip Name at Rev 2" and revision remains 2L
        Trip currentInDb = tripRepository.findById(created.getTripId()).orElseThrow();
        assertEquals(2L, currentInDb.getRevision());
        assertEquals("Trip Name at Rev 2", currentInDb.getName());
    }

    @Test
    @DisplayName("BE-01: Hai request cập nhật đồng thời cùng revision chỉ một request được commit thành công")
    void testConcurrentOptimisticLockOnlyOneSucceeds() throws Exception {
        TripCreateRequest req = new TripCreateRequest();
        req.setName("Postgres Concurrency Test Trip " + UUID.randomUUID().toString().substring(0, 6));
        req.setDurationGroup(TripDurationGroup.SHORT);
        req.setDurationDays(2);

        TripDetailResponse created = tripService.createTrip(req, actorId);
        UUID tripId = created.getTripId();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = List.of(
                () -> {
                    barrier.await();
                    try {
                        TripDraftPatchRequest p = new TripDraftPatchRequest();
                        p.setExpectedRevision(1L);
                        p.setName("Concurrent Update from Thread 1");
                        tripService.patchTripDraft(tripId, p, actorId);
                        successCount.incrementAndGet();
                    } catch (BusinessException ex) {
                        if ("STALE_REVISION".equals(ex.getCode()) || "OPTIMISTIC_LOCK_CONFLICT".equals(ex.getCode())) {
                            conflictCount.incrementAndGet();
                        }
                    } catch (Exception ex) {
                        if (ex instanceof org.springframework.dao.OptimisticLockingFailureException
                                || (ex.getCause() != null && ex.getCause() instanceof jakarta.persistence.OptimisticLockException)
                                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("optimistic"))) {
                            conflictCount.incrementAndGet();
                        }
                    }
                    return null;
                },
                () -> {
                    barrier.await();
                    try {
                        TripDraftPatchRequest p = new TripDraftPatchRequest();
                        p.setExpectedRevision(1L);
                        p.setName("Concurrent Update from Thread 2");
                        tripService.patchTripDraft(tripId, p, actorId);
                        successCount.incrementAndGet();
                    } catch (BusinessException ex) {
                        if ("STALE_REVISION".equals(ex.getCode()) || "OPTIMISTIC_LOCK_CONFLICT".equals(ex.getCode())) {
                            conflictCount.incrementAndGet();
                        }
                    } catch (Exception ex) {
                        if (ex instanceof org.springframework.dao.OptimisticLockingFailureException
                                || (ex.getCause() != null && ex.getCause() instanceof jakarta.persistence.OptimisticLockException)
                                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("optimistic"))) {
                            conflictCount.incrementAndGet();
                        }
                    }
                    return null;
                }
        );

        List<Future<Void>> futures = executor.invokeAll(tasks);
        for (Future<Void> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertEquals(1, successCount.get(), "Chính xác 1 request thành công");
        assertEquals(1, conflictCount.get(), "Request thứ 2 phải nhận conflict STALE_REVISION");

        Trip finalDbTrip = tripRepository.findById(tripId).orElseThrow();
        assertEquals(2L, finalDbTrip.getRevision(), "Revision cuối cùng phải được tăng đúng 1 lần lên 2L");
    }

    // =========================================================================
    // BE-02: Real PostgreSQL Idempotency Tests
    // =========================================================================

    @Test
    @DisplayName("BE-02: Hai request đồng thời cùng Idempotency-Key và payload chỉ commit 1 Trip và trả cùng kết quả")
    void testConcurrentIdempotentCreateTripOnlyCommitsOneTrip() throws Exception {
        String key = "idemp-key-concurrent-" + UUID.randomUUID();
        String tripName = "Postgres Concurrent Idempotent Trip " + UUID.randomUUID().toString().substring(0, 6);

        TripCreateRequest req = new TripCreateRequest();
        req.setName(tripName);
        req.setDurationGroup(TripDurationGroup.SHORT);
        req.setDurationDays(1);

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        List<Callable<TripDetailResponse>> tasks = List.of(
                () -> {
                    barrier.await();
                    return idempotencyService.execute(key, actorId, "CREATE_TRIP", req,
                            () -> tripService.createTrip(req, actorId));
                },
                () -> {
                    barrier.await();
                    return idempotencyService.execute(key, actorId, "CREATE_TRIP", req,
                            () -> tripService.createTrip(req, actorId));
                }
        );

        List<Future<TripDetailResponse>> futures = executor.invokeAll(tasks);
        TripDetailResponse res1 = futures.get(0).get();
        TripDetailResponse res2 = futures.get(1).get();
        executor.shutdown();

        assertNotNull(res1);
        assertNotNull(res2);
        assertEquals(res1.getTripId(), res2.getTripId(), "Cả 2 request đồng thời phải nhận cùng tripId");
        assertEquals(res1.getTripCode(), res2.getTripCode(), "Cả 2 request đồng thời phải nhận cùng tripCode");

        // Verify that only 1 Trip exists in database with this name
        List<Trip> tripsInDb = tripRepository.findAll().stream()
                .filter(t -> tripName.equals(t.getName()))
                .toList();
        assertEquals(1, tripsInDb.size(), "Database chỉ commit duy nhất 1 Trip, không tạo duplicate");
    }

    @Test
    @DisplayName("BE-02: Idempotency-Key trùng nhưng payload khác trả về IDEMPOTENCY_CONFLICT (409)")
    void testIdempotencyDifferentPayloadConflict() {
        String key = "idemp-key-diff-" + UUID.randomUUID();

        TripCreateRequest req1 = new TripCreateRequest();
        req1.setName("Trip Payload Original");
        req1.setDurationGroup(TripDurationGroup.SHORT);
        req1.setDurationDays(1);

        TripDetailResponse res1 = idempotencyService.execute(key, actorId, "CREATE_TRIP", req1,
                () -> tripService.createTrip(req1, actorId));
        assertNotNull(res1);

        TripCreateRequest req2 = new TripCreateRequest();
        req2.setName("Trip Payload Changed");
        req2.setDurationGroup(TripDurationGroup.LONG);
        req2.setDurationDays(3);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> idempotencyService.execute(key, actorId, "CREATE_TRIP", req2,
                        () -> tripService.createTrip(req2, actorId)));
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getCode());
    }

    @Test
    @DisplayName("BE-02: Hai request đồng thời cùng Idempotency-Key khi Clone Trip chỉ commit 1 bản sao")
    void testConcurrentIdempotentCloneTripOnlyCommitsOneClone() throws Exception {
        // First create source trip
        TripCreateRequest req = new TripCreateRequest();
        req.setName("Source Trip for Clone " + UUID.randomUUID().toString().substring(0, 6));
        req.setDurationGroup(TripDurationGroup.SHORT);
        req.setDurationDays(2);
        TripDetailResponse sourceTrip = tripService.createTrip(req, actorId);
        UUID sourceTripId = sourceTrip.getTripId();

        String cloneKey = "idemp-clone-" + UUID.randomUUID();
        String payloadKey = sourceTripId.toString();

        int threads = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);

        List<Callable<TripDetailResponse>> tasks = List.of(
                () -> {
                    barrier.await();
                    return idempotencyService.execute(cloneKey, actorId, "CLONE_TRIP", payloadKey,
                            () -> tripService.cloneTrip(sourceTripId, null, actorId));
                },
                () -> {
                    barrier.await();
                    return idempotencyService.execute(cloneKey, actorId, "CLONE_TRIP", payloadKey,
                            () -> tripService.cloneTrip(sourceTripId, null, actorId));
                }
        );

        List<Future<TripDetailResponse>> futures = executor.invokeAll(tasks);
        TripDetailResponse clone1 = futures.get(0).get();
        TripDetailResponse clone2 = futures.get(1).get();
        executor.shutdown();

        assertNotNull(clone1);
        assertNotNull(clone2);
        assertEquals(clone1.getTripId(), clone2.getTripId(), "Cả 2 request clone đồng thời phải nhận cùng tripId");

        // Verify only 1 clone exists with the cloned name
        String expectedCloneName = "Bản sao - " + req.getName();
        List<Trip> clonesInDb = tripRepository.findAll().stream()
                .filter(t -> expectedCloneName.equals(t.getName()))
                .toList();
        assertEquals(1, clonesInDb.size(), "Chỉ có duy nhất 1 bản sao được commit vào DB");
    }

    @Test
    @DisplayName("BE-09: PATCH/PUT cố thay đổi durationDays bị từ chối INVALID_DAY_COUNT, gửi lại cùng số ngày thành công")
    void testBE09_PatchTripDraftRejectsDurationDaysChange() {
        TripCreateRequest req = new TripCreateRequest();
        req.setName("Postgres BE-09 Day Bypass Test " + UUID.randomUUID().toString().substring(0, 6));
        req.setDurationGroup(TripDurationGroup.SHORT);
        req.setDurationDays(2);

        TripDetailResponse created = tripService.createTrip(req, actorId);
        assertEquals(2, created.getDurationDays());

        // Attempt to change durationDays via PATCH -> rejected
        TripDraftPatchRequest invalidPatch = new TripDraftPatchRequest();
        invalidPatch.setExpectedRevision(1L);
        invalidPatch.setDurationDays(3);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tripService.patchTripDraft(created.getTripId(), invalidPatch, actorId));
        assertEquals("INVALID_DAY_COUNT", ex.getCode());

        // Resubmitting same durationDays (2) succeeds
        TripDraftPatchRequest validPatch = new TripDraftPatchRequest();
        validPatch.setExpectedRevision(1L);
        validPatch.setDurationDays(2);
        validPatch.setName("Valid Renamed Trip BE-09");

        TripDetailResponse patched = tripService.patchTripDraft(created.getTripId(), validPatch, actorId);
        assertEquals("Valid Renamed Trip BE-09", patched.getName());
        assertEquals(2, patched.getDurationDays());
    }

    @Test
    @DisplayName("BE-03: Chính sách durationGroup - kiểm tra tính tương thích và chặn đổi khi không khớp số ngày")
    void testBE03_DurationGroupPolicy() {
        TripCreateRequest req = new TripCreateRequest();
        req.setName("Postgres BE-03 Duration Group Policy " + UUID.randomUUID().toString().substring(0, 6));
        req.setDurationGroup(TripDurationGroup.SHORT);
        req.setDurationDays(2);

        TripDetailResponse created = tripService.createTrip(req, actorId);

        // Trip is 2 days. Attempting to change durationGroup to LONG (requires 3-5 days) must fail
        TripDraftPatchRequest patchLong = new TripDraftPatchRequest();
        patchLong.setExpectedRevision(1L);
        patchLong.setDurationGroup(TripDurationGroup.LONG);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tripService.patchTripDraft(created.getTripId(), patchLong, actorId));
        assertEquals("INVALID_DURATION", ex.getCode());

        // Data in DB remains SHORT and 2 days
        Trip inDb = tripRepository.findById(created.getTripId()).orElseThrow();
        assertEquals(TripDurationGroup.SHORT, inDb.getDurationGroup());
        assertEquals(2, inDb.getDurationDays());
    }

    @Test
    @DisplayName("BE-17: Lưu trữ Trip kiểm tra expectedRevision và chặn mutation sau khi lưu trữ")
    void testBE17_TripArchiveWithRevisionAndMutationBlocking() {
        TripCreateRequest req = new TripCreateRequest();
        req.setName("Postgres BE-17 Archive Test " + UUID.randomUUID().toString().substring(0, 6));
        req.setDurationGroup(TripDurationGroup.SHORT);
        req.setDurationDays(1);

        TripDetailResponse created = tripService.createTrip(req, actorId);
        assertEquals(1L, created.getRevision());

        // Archive with stale revision -> STALE_REVISION
        TripArchiveRequest staleArchive = new TripArchiveRequest();
        staleArchive.setExpectedRevision(999L);
        staleArchive.setReason("Test stale");

        BusinessException exStale = assertThrows(BusinessException.class,
                () -> tripService.archiveTrip(created.getTripId(), staleArchive, actorId));
        assertEquals("STALE_REVISION", exStale.getCode());

        // Archive with valid revision -> SUCCESS
        TripArchiveRequest validArchive = new TripArchiveRequest();
        validArchive.setExpectedRevision(1L);
        validArchive.setReason("Lưu trữ theo kế hoạch");

        TripDetailResponse archived = tripService.archiveTrip(created.getTripId(), validArchive, actorId);
        assertEquals(TripStatus.ARCHIVED, archived.getStatus());
        assertEquals(2L, archived.getRevision());

        // Mutation after archive -> TRIP_ARCHIVED
        TripDraftPatchRequest patch = new TripDraftPatchRequest();
        patch.setExpectedRevision(2L);
        patch.setName("Illegal Name on Archived Trip");

        BusinessException exArchived = assertThrows(BusinessException.class,
                () -> tripService.patchTripDraft(created.getTripId(), patch, actorId));
        assertEquals("TRIP_ARCHIVED", exArchived.getCode());
    }

    @Test
    @DisplayName("BE-11 & BE-12/16: Hàng đợi duyệt review-queue trả đúng phiên bản IN_REVIEW kèm actorName và loại trừ Trip ARCHIVED")
    void testBE11_ReviewQueueAndArchivedExclusion() {
        TripCreateRequest req = new TripCreateRequest();
        req.setName("Postgres Review Queue Trip " + UUID.randomUUID().toString().substring(0, 6));
        req.setDurationGroup(TripDurationGroup.SHORT);
        req.setDurationDays(1);

        TripDetailResponse created = tripService.createTrip(req, actorId);
        UUID versionId = created.getVersions().get(0).getVersionId();

        // Simulate version submitted to IN_REVIEW
        TripScheduleVersion version = versionRepository.findById(versionId).orElseThrow();
        version.setStatus(TripScheduleVersionStatus.IN_REVIEW);
        version.setSubmitter(testPlanner);
        version.setSubmittedRevision(1L);
        version.setSubmittedAt(Instant.now());
        versionRepository.save(version);
        versionRepository.flush();

        // Check review queue contains this version
        Page<TripReviewQueueItemResponse> queue = versionService.getReviewQueue(PageRequest.of(0, 20));
        assertFalse(queue.isEmpty(), "Hàng đợi duyệt không được rỗng");
        TripReviewQueueItemResponse foundItem = queue.getContent().stream()
                .filter(item -> item.getVersionId().equals(versionId))
                .findFirst()
                .orElse(null);
        assertNotNull(foundItem, "Phiên bản IN_REVIEW phải xuất hiện trong review-queue");
        assertEquals(testPlanner.getUsername(), foundItem.getSubmitterName(), "Tên người gửi duyệt phải là username của submitter");
        assertNotNull(foundItem.getSubmittedAt(), "Thời điểm submittedAt phải có giá trị");
        assertEquals(created.getTripCode(), foundItem.getTripCode(), "Mã Trip phải khớp");

        // BE-17: Archive Trip while version is IN_REVIEW is blocked
        TripArchiveRequest archiveReq = new TripArchiveRequest();
        archiveReq.setExpectedRevision(1L);
        BusinessException exArchiveBlocked = assertThrows(BusinessException.class,
                () -> tripService.archiveTrip(created.getTripId(), archiveReq, actorId));
        assertEquals("CONCURRENT_REVIEW_IN_PROGRESS", exArchiveBlocked.getCode());

        // Return version to DRAFT so trip can be archived
        version = versionRepository.findById(versionId).orElseThrow();
        version.setStatus(TripScheduleVersionStatus.DRAFT);
        version = versionRepository.save(version);
        versionRepository.flush();

        // Archive the trip
        tripService.archiveTrip(created.getTripId(), archiveReq, actorId);

        // Put version back to IN_REVIEW directly in DB to verify ARCHIVED Trip exclusion
        TripScheduleVersion freshVersion = versionRepository.findById(versionId).orElseThrow();
        freshVersion.setStatus(TripScheduleVersionStatus.IN_REVIEW);
        versionRepository.save(freshVersion);
        versionRepository.flush();

        // Review queue must now filter out the version because its Trip is ARCHIVED
        Page<TripReviewQueueItemResponse> queueAfterArchive = versionService.getReviewQueue(PageRequest.of(0, 20));
        boolean stillInQueue = queueAfterArchive.getContent().stream()
                .anyMatch(item -> item.getVersionId().equals(versionId));
        assertFalse(stillInQueue, "Phiên bản của Trip ARCHIVED không được xuất hiện trong review-queue");
    }
}
