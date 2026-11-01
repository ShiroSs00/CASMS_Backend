# Port Domain — Coding handoff, 01/11/2026

Chỉ giai đoạn CODING + INTEGRATION. Không phải kết quả nghiệm thu.

BE: `codex/be-dur-01` · HEAD `fa8eeaf1b5b578e954ccecb2c64d8e3e8e3e49ba`.
FE: `main` · HEAD `b72ae568032db98fe81169c8a9b2792a45068304`.
Cả hai có thay đổi local từ trước; danh sách dưới so với bản sao working tree **ngay trước task**, không gán toàn bộ Git diff cũ cho task này.

| Task | Status | FE files chính | BE files chính | DB/API changes | Blockers |
|---|---|---|---|---|---|
| PORT-MASTER-01 | IMPLEMENTED — NOT TESTED | MasterDataPages, itinerary-api, LocationPicker, DirectoryHistory, itinerary model | Port/Destination controllers, DestinationService/Repository, PortService/Repository, DTOs, AuditService | Server filters, region DTO, history reads; giữ master cardinality | Schema/migration/runtime chưa chạy |
| PORT-WIN-01 | IMPLEMENTED — NOT TESTED | PortWindows + CSS, port-windows API/types | PortCallWindow/Exception, repos, PortWindowService/Controller, PortWindowContract, AuditService | V78; read/create/update/verify/lifecycle/closure/history | Khung giờ băng qua nửa đêm chưa mở khi chưa có endDayOffset contract; PortCall qua đêm vẫn có evaluator |
| ITN-VISIT-01 | IMPLEMENTED — NOT TESTED | DestinationVisits + CSS, current-contract/mappers, PlanDrawers/Details | DestinationVisit, nested DTOs, TripPlanStop, TripScheduleVersionService | V79; visits[] nested dưới stops[] qua aggregate hiện có | Không tự chuyển legacy destinationId; không ép tour vào Visit |
| ITN-PORT-01 | IMPLEMENTED — NOT TESTED | PlanDrawers/Details, PortWindows, catalogs, business error copy | TripCatalogController, PlanningSourcePortResponse; giữ validation/finalize hiện có | Local catalog enrich locality; Window read bằng quyền Port/Trip read hiện có | Date-dependent check vẫn NEEDS_SAILING_DATE; không Window pinning |
| DEP-PORT-01 | PARTIALLY IMPLEMENTED | DepartureData/Preview, PortWindowChecks, departure-contract, mainflow-api, CruisePlanReview | PortWindowEvaluator, materialization, CruiseMainflowService, CruiseStopService, CruiseService, PortFacts, CruiseDestinationVisit, snapshot fields | V79; preview checks; Create/Approve/Ready revalidation; independent facts/visits/evidence | Legacy facts chưa được chụp: không thể phục dựng lịch sử tự động; gate BLOCK và cần phương án đối chiếu có căn cứ |

## Những phần giữ nguyên

Port UUID/code/provenance; Destination name uniqueness, cascade/cardinality; Shore Excursion → PortCall; SHORT/LONG; chronology/overlap/allAboard; CRM finalize và OPS review/Ready/Open Sale riêng; legacy SERVING_WINDOW protection; ITN-04 transaction/idempotency. Không thêm role/permission; dùng PORT_CALL_WINDOW_* có sẵn và read-only PORT_READ/TRIP_READ cho Window reference. Không đổi mã quyết định có ID collision.

## Contract đang được nối

- `GET /ports` và `/destinations`: search/countryCode/status + pagination ở BE; Port code/country khóa khi edit; trạng thái qua action riêng. Destination domesticRegion nhận/trả theo enum miền đã có, không suy từ Port.
- `GET /ports/{portId}/history`, `/destinations/{destinationId}/history`: OPS đọc AuditLog thực, phân trang. Destination audit thêm before/after; không backfill audit cũ.
- `GET/POST /ports/{portId}/call-windows`; `PUT /{windowId}`; `POST /{windowId}/verify`; `PATCH /{windowId}/lifecycle`; tương tự `exceptions` cho closure; history ở từng resource.
- Window source chỉ PORT_AUTHORITY/PORT_AGENT; không lấy giờ từ Geocoding. Create/update về UNVERIFIED + INACTIVE, verify lấy actor/time/Port-zone từ BE, activate cần verified zone khớp. Updates/verify/lifecycle dùng expectedRevision. Audit cùng giao dịch; không OVERRIDE.
- Khoảng giờ cùng ngày `[start,end)`; validTo là ngày cuối được áp dụng. Closure đóng trọn các ngày địa phương trong khoảng, đã VERIFIED + ACTIVE; không có fallback 24/7.
- Aggregate `PUT /trip-schedule-versions/{versionId}/content`: stops[].visits[] chứa visitId (existing), clientKey, destinationId, sequence. Thiếu/null visits giữ nguyên; [] xóa tường minh trong DRAFT. Identity phải đúng parent, không unique theo Destination. Clone giữ lượt với identity mới. Không thêm giờ/giá/capacity/tour registration.
- Port facts chụp lúc finalize nguồn mới, rồi copy độc lập sang CruiseStop/Itinerary; DestinationVisit materialize thành CruiseDestinationVisit. Không tự cập nhật khi master đổi. Legacy không có facts trả unknown thay vì khẳng định master hôm nay là lịch sử.
- Preview trả `portWindowChecks` ở tổng và từng call; FE hiển thị movement/local datetime/reason/evidence/visits. Create tự chiếu và kiểm lại trong transaction. Approve và Ready dùng cùng evaluator; reject/request-revision vẫn có đường xử lý khi điều kiện chưa đạt.
- Directory writes và gate ghi khóa cùng Port; reload facts sau khóa để không dùng timezone cũ trong persistence context. Khóa nhiều Port theo UUID ổn định. Preview chỉ tham khảo, không giữ khóa.
- Evaluator kiểm độc lập ARRIVAL/DEPARTURE trên ngày địa phương; nguồn effective/verified/active, union giờ, closure, stay permission/maxStay khi có nguồn. Lưu qua đêm không suy từ giờ mở cửa; nguồn mâu thuẫn không cho phép sẽ BLOCK. Evidence gồm policy ID/revision/source và dữ liệu xác minh của stay. Không buffer, không tính tide/daylight/pilotage, không coi PASS là đặt cầu cảng.
- Evidence lúc materialize lưu riêng; OPS revalidation append audit, không sửa snapshot/fingerprint bằng timestamp đánh giá mới. Nullable fields/new Visit collection vắng không tự đổi hình dạng fingerprint legacy.
- Controller/DTO OpenAPI annotations và TS client được cập nhật cùng source; JSON OpenAPI runtime chưa regenerate vì không được chạy app/build.

## Migration và execution

- Mới: V78__port_call_windows_and_closures.sql; V79__destination_visits_and_port_snapshot_facts.sql. Additive, không seed giờ, không backfill legacy, không sửa/xóa ID cũ. **CHƯA CHẠY**.
- V75/V76/V77: so byte với baseline trước task, **UNCHANGED**. Port.java, Destination.java và permission catalog/initializer cũng giữ nguyên so baseline task.
- Test/unit/integration/build/lint/typecheck/E2E/browser/screenshot: **NOT RUN — theo chỉ thị PO**. Không có kết quả PASS hay runtime evidence.
- Không start FE/BE, gọi API live, kết nối/ghi DB, chạy migration, commit/push/merge. Chỉ đọc source/Git và so diff văn bản trong coding pass.
- Impeccable context launcher không chạy được do engine chưa cài/cache ngoài writable roots; dùng project source/AGENTS/glossary và skill Operate/craft-floor trực tiếp. Không cài engine hoặc thay design system.

## Clarifications / integration còn mở

1. **CR-PORT-LEGACY-FACTS**: cần nguồn lịch sử nào để xác nhận Port facts cho mẫu/Departure cũ? Không backfill master hiện tại, không sửa PUBLISHED, không tái tạo Departure. Hiện tên/timezone lịch sử thiếu facts được báo unknown; Approve/Ready không cho PASS khi thiếu cơ sở giờ. Cần PO duyệt quy trình correction riêng trước release cho legacy.
2. **CR-PORT-CROSS-MIDNIGHT-WINDOW**: nếu cần một Window qua nửa đêm, chốt endDayOffset/weekday/effectivity semantics; hiện từ chối rõ trường hợp đó. Không ảnh hưởng việc PortCall qua nhiều ngày với Window từng ngày và bằng chứng cho lưu qua đêm.
3. **CR-PORT-VESSEL-DATA**: model Ship hiện thấy lengthMeters, chưa có contract xác định đó là LOA, draft/vessel class đủ căn cứ. Không tự coi lengthMeters là LOA hoặc thêm dữ liệu tàu giả; chưa triển khai so sánh các restriction này. Overnight/maxStay có nguồn đã được triển khai. Không tuyên bố đã đủ vessel-restriction coverage.
4. Pending schema V75–V79, runtime OpenAPI, database column validation, constructor/consumer regressions, permission positives/negatives, concurrency/DST, UI render và E2E phải nằm trong đợt TESTING & ACCEPTANCE riêng. Chưa biết kết quả vì không được chạy.

Có nền code để tiếp tục ITN-05 sau schema readiness, xử lý legacy decision và đợt nghiệm thu. Không coi đây là full UX/UI completion hoặc release-ready.

## Changed files trong task

### CASMS_Backend

- `src/main/java/com/casms/audit/AuditService.java`
- `src/main/java/com/casms/ship/controller/DestinationController.java`
- `src/main/java/com/casms/ship/controller/PortController.java`
- `src/main/java/com/casms/ship/controller/PortWindowController.java`
- `src/main/java/com/casms/ship/dto/PortFacts.java`
- `src/main/java/com/casms/ship/dto/PortWindowContract.java`
- `src/main/java/com/casms/ship/dto/request/DestinationCreateRequest.java`
- `src/main/java/com/casms/ship/dto/request/DestinationUpdateRequest.java`
- `src/main/java/com/casms/ship/dto/response/CruiseSchedulePreviewResponse.java`
- `src/main/java/com/casms/ship/dto/response/DestinationResponse.java`
- `src/main/java/com/casms/ship/entity/CruiseDestinationVisit.java`
- `src/main/java/com/casms/ship/entity/CruiseStop.java`
- `src/main/java/com/casms/ship/entity/Itinerary.java`
- `src/main/java/com/casms/ship/entity/PortCallWindow.java`
- `src/main/java/com/casms/ship/entity/PortCallWindowException.java`
- `src/main/java/com/casms/ship/repository/DestinationRepository.java`
- `src/main/java/com/casms/ship/repository/PortCallWindowExceptionRepository.java`
- `src/main/java/com/casms/ship/repository/PortCallWindowRepository.java`
- `src/main/java/com/casms/ship/repository/PortRepository.java`
- `src/main/java/com/casms/ship/service/CruiseMainflowService.java`
- `src/main/java/com/casms/ship/service/CruiseService.java`
- `src/main/java/com/casms/ship/service/CruiseStopService.java`
- `src/main/java/com/casms/ship/service/DestinationService.java`
- `src/main/java/com/casms/ship/service/PortService.java`
- `src/main/java/com/casms/ship/service/PortWindowEvaluator.java`
- `src/main/java/com/casms/ship/service/PortWindowService.java`
- `src/main/java/com/casms/ship/service/ScheduleMaterializationService.java`
- `src/main/java/com/casms/trip/controller/TripCatalogController.java`
- `src/main/java/com/casms/trip/dto/request/DestinationVisitDraftDto.java`
- `src/main/java/com/casms/trip/dto/request/TripPlanStopDraftDto.java`
- `src/main/java/com/casms/trip/dto/response/DestinationVisitResponse.java`
- `src/main/java/com/casms/trip/dto/response/PlanningSourcePortResponse.java`
- `src/main/java/com/casms/trip/dto/response/TripPlanStopResponse.java`
- `src/main/java/com/casms/trip/entity/DestinationVisit.java`
- `src/main/java/com/casms/trip/entity/TripPlanDay.java`
- `src/main/java/com/casms/trip/entity/TripPlanStop.java`
- `src/main/java/com/casms/trip/service/TripScheduleVersionService.java`
- `src/main/resources/db/migration/V78__port_call_windows_and_closures.sql`
- `src/main/resources/db/migration/V79__destination_visits_and_port_snapshot_facts.sql`

### CASMS_Frontend

- `CASMS_WebApp/docs/product/glossary.md`
- `CASMS_WebApp/src/features/cruises/api/mainflow-api.ts`
- `CASMS_WebApp/src/features/cruises/components/CruisePlanReview.tsx`
- `CASMS_WebApp/src/features/cruises/components/DepartureData.tsx`
- `CASMS_WebApp/src/features/cruises/components/DepartureSchedulePreview.tsx`
- `CASMS_WebApp/src/features/cruises/components/PortWindowChecks.tsx`
- `CASMS_WebApp/src/features/cruises/model/departure-contract.ts`
- `CASMS_WebApp/src/features/cruises/model/operations-workflow.ts`
- `CASMS_WebApp/src/features/itineraries/api/itinerary-api.ts`
- `CASMS_WebApp/src/features/itineraries/api/port-windows.ts`
- `CASMS_WebApp/src/features/itineraries/components/DirectoryHistory.tsx`
- `CASMS_WebApp/src/features/itineraries/components/LocationPicker.tsx`
- `CASMS_WebApp/src/features/itineraries/components/PortWindows.module.css`
- `CASMS_WebApp/src/features/itineraries/components/PortWindows.tsx`
- `CASMS_WebApp/src/features/itineraries/model/itinerary.ts`
- `CASMS_WebApp/src/features/itineraries/pages/MasterDataPages.tsx`
- `CASMS_WebApp/src/features/trip-planning/api/catalogs.ts`
- `CASMS_WebApp/src/features/trip-planning/api/current-contract.ts`
- `CASMS_WebApp/src/features/trip-planning/api/mappers.ts`
- `CASMS_WebApp/src/features/trip-planning/components/DestinationVisits.module.css`
- `CASMS_WebApp/src/features/trip-planning/components/DestinationVisits.tsx`
- `CASMS_WebApp/src/features/trip-planning/components/PlanDetails.tsx`
- `CASMS_WebApp/src/features/trip-planning/components/PlanDrawers.tsx`
- `CASMS_WebApp/src/shared/api/business-errors.ts`


Incremental patch để review (không tự apply): C:/Users/USER/.codex/visualizations/2026/11/01/01a194c5-3d43-7233-95bd-2d58f7f8457c/port-domain-implementation/port-domain-local-changes.patch
