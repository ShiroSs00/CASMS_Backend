# Port Domain — Test & Push Gate, 01/11/2026

**Status: BLOCKED FOR PUSH. Không stage/commit/push khi bộ kiểm tra mở rộng còn lỗi.**

PO đã cho phép test và push toàn bộ code FE/BE đã kiểm tra, bỏ cấu hình máy, file môi trường, logs/ZIP. Chỉ test/compile/lint/build và sửa lỗi liên quan được thực hiện; không chạy app, DB migration, DB integration hoặc E2E live.

## Baseline cuối phiên

- BE branch `codex/be-dur-01`, HEAD `fa8eeaf1b5b578e954ccecb2c64d8e3e8e3e49ba`.
- FE branch `main`, HEAD `b72ae568032db98fe81169c8a9b2792a45068304`.
- `git fetch origin` đã chạy hai repo, không merge/rebase/reset. BE origin/main có thêm hai commit CASMS-91; FE HEAD khớp origin/main tại fetch. Không tích hợp remote vào tree đang dirty.
- Working trees được giữ lại; index không có staged diff. Không thay SHA local.

## Kết quả thực thi

| Command / scope | Exit | Kết quả |
|---|---|---|
| FE `npm run typecheck` | 0 | TypeScript đạt |
| FE `npm run lint` (sau sửa) | 0 | 0 lỗi/warning; lệnh cuối ghép trước build cũng hoàn thành |
| FE `npm run build` (sau sửa) | 0 | 568 modules; có warning chunk lớn/model dynamic import, không fail build |
| FE `npm test` (sau sửa) | 0 | 642 tests: 641 pass, 0 fail, 1 skipped |
| BE `mvn -DskipTests compile` | 0 | Biên dịch production code đạt; test compile cũng đạt trong lượt sau |
| BE targeted unit/MockMvc/contract + checksum lock | 0 | 77 tests, 0 failures/errors/skips |
| BE expanded non-database tests, 75 classes | 1 | 472 tests, 26 failures, 15 errors, 0 skipped trong lượt mở rộng cuối. Checksum lock sau đó đã sửa và rerun PASS; các nhóm còn lại chưa chạy xanh |
| PostgreSQL integration / migration / E2E live / browser | NOT RUN | Không sử dụng DB của PO để chạy migration/test |
| `npx ai-devkit@latest lint` | 1 | DNS ENOTFOUND registry.npmjs.org; không cài dependency |

Lệnh BE targeted cuối:

```powershell
mvn '-Dtest=PortWindowEvaluatorTest,PortDestinationTimeZoneServiceTest,PortLocalTimeTransitionTest,ScheduleMaterializationServiceTest,TripPlanningContractTest,TripDurationContractTest,MigrationChecksumLockTest' test
```

Bộ mở rộng chọn mọi `*Test.java` không chứa `@SpringBootTest`, `DriverManager`, `@DataJpaTest`, `@Testcontainers`, `@WebMvcTest`; số lớp được ghi trong log. Không dùng báo cáo surefire cũ của integration test làm kết quả lượt này.

## Những lỗi đã sửa trong phiên test

- FE Window dùng chung chuyển sang `src/domain/port-windows` với public index; loại import chéo feature, giữ cùng API/UI behavior.
- FE request loading/retry tuân quy tắc hooks; bỏ dead imports/variables trong test harness, không bỏ assertion nghiệp vụ.
- Geocoding vẫn cho thiếu timezone metadata, nhưng từ chối IANA timezone sai khi nguồn có gửi.
- Cập nhật fixture/harness FE cho revision nguồn, provenance, read queries, local-country field, server Window PASS và legacy SERVING_WINDOW chỉ đọc/omission. Không đổi validation production để chiều fixture cũ.
- BE thêm `PortWindowEvaluatorTest` kiểm start/end boundary, movement độc lập, union, unverified/inactive, closure, thiếu window, overnight, maxStay, timezone đổi và legacy thiếu facts.
- Materializer dùng constructor injection cho evaluator; cập nhật mocks/source revision/snapshot facts/map result trong test.
- Fixture ship dùng READY thay enum ACTIVE đã bỏ; constructor của Preparation authorization test đồng bộ evaluator.
- Test timezone cũ trông chờ tự default Nhật Bản được đổi sang xác minh contract bắt buộc chọn timezone nghiệp vụ.
- Chỉ append checksum V74–V79 vào test manifest. Không sửa hoặc thực thi các migration đó.

## Nhóm BE còn chặn

- BookingServiceTest: thiếu mock Mainflow dependency trong fixture, một số assertion create chưa tới được rule cần kiểm.
- CruiseServiceTest/CruiseControllerTest: fixture chưa theo preparation/audit/mainflow và cruise-create idempotency hiện hành; lifecycle error code đã thay đổi.
- ItineraryServiceTest/OperationalProgramUnknownInputTest: dữ liệu ngày/Instant hoặc quan hệ cần thiết trong fixture đang thiếu; chưa xác nhận mọi lỗi chỉ do fixture.
- ItnDurationValidationTest: finalize fixture/dependency lỗi; chưa đạt.
- TripPlanningEnhancementTest/TripScheduleVersionServiceTest: nhiều assertion vẫn yêu cầu template submit/review/withdraw/publish cũ đã retired; thêm một số fixture day/stop thiếu version parent và assertion boundary-day chưa đạt.
- PermissionConstraintInitializerTest: kiểm enum với allowlist migration V70, trong khi permission bổ sung nằm ở các migration sau; cần đối chiếu đầy đủ contract và sửa kiểm tra tích lũy.

Không thể coi toàn bộ code hiện tại ổn chỉ từ targeted Port tests. Không tự bỏ/disable các nhóm lỗi để push. Cần sửa/đối chiếu các nhóm này và rerun non-database suite trước commit; integration DB/migrations cần môi trường thử riêng trước release.

## Evidence logs

- [fe-test.log](C:/Users/USER/.codex/visualizations/2026/11/01/01a194c5-3d43-7233-95bd-2d58f7f8457c/port-domain-implementation/fe-test.log)
- [fe-lint.log](C:/Users/USER/.codex/visualizations/2026/11/01/01a194c5-3d43-7233-95bd-2d58f7f8457c/port-domain-implementation/fe-lint.log)
- [fe-targeted-test.log](C:/Users/USER/.codex/visualizations/2026/11/01/01a194c5-3d43-7233-95bd-2d58f7f8457c/port-domain-implementation/fe-targeted-test.log)
- [be-unit-test.log](C:/Users/USER/.codex/visualizations/2026/11/01/01a194c5-3d43-7233-95bd-2d58f7f8457c/port-domain-implementation/be-unit-test.log)
- [be-final-targeted.log](C:/Users/USER/.codex/visualizations/2026/11/01/01a194c5-3d43-7233-95bd-2d58f7f8457c/port-domain-implementation/be-final-targeted.log)
