# Phân Hệ Lập Kế Hoạch Hải Trình & Cảng Biển (Itinerary & Port Planning)

Quản lý lộ trình đường biển (`Itinerary`), danh mục các cảng đón trả khách (`Port`), điểm đến tham quan (`Destination`), chuyến đi được lên lịch (`Cruise`), các điểm cập cảng (`CruiseStop`) và kế hoạch phân bổ theo ngày (`TripPlanDay`).

## Mã Mermaid UML

```mermaid
classDiagram
    direction TB

    class Port {
        +UUID portId
        +String portCode
        +String portName
        +String countryCode
        +Double latitude
        +Double longitude
        +Boolean isHomePort
    }

    class Destination {
        +UUID destinationId
        +String destinationCode
        +String destinationName
        +String countryCode
        +String description
    }

    class Itinerary {
        +UUID itineraryId
        +String itineraryCode
        +String itineraryName
        +Integer durationDays
        +Integer durationNights
        +String plannedScope
        +ItineraryStatus status
    }

    class Cruise {
        +UUID cruiseId
        +String cruiseCode
        +String cruiseName
        +Instant departureDatetime
        +Instant returnDatetime
        +String plannedScope
        +CruiseStatus status
        +Instant publishedAt
    }

    class CruiseStop {
        +UUID cruiseStopId
        +Integer sequenceNumber
        +Instant plannedArrivalTime
        +Instant plannedDepartureTime
        +Instant actualArrivalTime
        +Instant actualDepartureTime
        +String berthNumber
    }

    class TripPlanDay {
        +UUID planDayId
        +Integer dayIndex
        +LocalDate planDate
        +String dayTheme
    }

    class TripPlanEntry {
        +UUID planEntryId
        +Instant startTime
        +Instant endTime
        +String activityTitle
        +String category
        +String locationNote
    }

    class DestinationVisit {
        +UUID visitId
        +String excursionSummary
        +BigDecimal estimatedHours
    }

    %% Relationships
    Itinerary "1" o-- "0..*" Cruise : instantiates
    Itinerary "1" *-- "2..*" CruiseStop : definesStops
    CruiseStop "0..*" --> "1" Port : atPort
    Destination "1" o-- "0..*" DestinationVisit : highlights
    CruiseStop "1" o-- "0..*" DestinationVisit : enablesVisits
    Cruise "1" *-- "1..*" TripPlanDay : scheduledDays
    TripPlanDay "1" *-- "0..*" TripPlanEntry : dailySchedule
```

## Giải Thích Quan Hệ & Ràng Buộc
- **Itinerary - CruiseStop**: Hải trình mẫu bao gồm danh sách các điểm dừng (`CruiseStop`) theo thứ tự (`sequenceNumber`), mỗi điểm dừng gắn liền với một Cảng biển (`Port`). Một hải trình tiêu chuẩn cần ít nhất 2 điểm dừng (cảng đi và cảng đến).
- **Itinerary - Cruise**: Một hải trình (`Itinerary`) có thể được dùng để khởi tạo nhiều chuyến tàu cụ thể (`Cruise`) với các mốc thời gian khởi hành (`departureDatetime`) khác nhau.
- **Cruise - TripPlanDay - TripPlanEntry**: Mỗi chuyến đi được chia thành từng ngày (`TripPlanDay`), mỗi ngày có danh sách các mốc hoạt động chi tiết (`TripPlanEntry`).
- **CruiseStop - DestinationVisit - Destination**: Khi tàu cập một cảng, hành khách có thể tham gia các chuyến tham quan điểm đến địa phương liên kết (`Destination`).

