# 00 — High-Level Domain Class Diagram

```mermaid
classDiagram
    direction TB
    title CASMS — High-Level Domain Architecture Overview

    class User {
        +UUID userId
        +String username
        +String email
        +String phoneNumber
        +UserStatus status
    }

    class Passenger {
        +UUID passengerId
        +String fullName
        +LocalDate dateOfBirth
        +String nationality
        +String documentNumber
    }

    class Ship {
        +UUID shipId
        +String shipCode
        +String shipName
        +Integer passengerCapacity
        +ShipStatus status
    }

    class Cruise {
        +UUID cruiseId
        +String cruiseCode
        +String cruiseName
        +Instant departureDatetime
        +Instant returnDatetime
        +String plannedScope
        +CruiseStatus status
    }

    class Itinerary {
        +UUID itineraryId
        +String itineraryCode
        +String itineraryName
        +Integer durationDays
    }

    class Port {
        +UUID portId
        +String portCode
        +String portName
        +String countryCode
    }

    class Cabin {
        +UUID cabinId
        +String cabinNumber
        +String cabinType
        +Integer maxOccupancy
    }

    class Booking {
        +UUID bookingId
        +String bookingReference
        +BigDecimal agreedFareAmount
        +BookingStatus bookingStatus
        +BookingContractStatus contractStatus
        +PaymentPlan paymentPlan
    }

    class BookingPassenger {
        +UUID bookingPassengerId
        +BookingPassengerParticipationStatus participationStatus
    }

    class Payment {
        +UUID paymentId
        +BigDecimal amount
        +String currency
        +PaymentStatus status
        +PaymentType paymentType
    }

    class OnboardAccount {
        +UUID onboardAccountId
        +BigDecimal creditLimit
        +OnboardAccountStatus status
    }

    class OnboardActivity {
        +UUID onboardActivityId
        +String activityCode
        +String activityName
        +Integer capacity
        +BigDecimal price
        +OnboardActivityStatus status
    }

    class TourPackage {
        +UUID tourPackageId
        +String packageCode
        +String packageName
        +BigDecimal basePrice
    }

    %% Relationships
    User "1" --> "0..1" Passenger : profileOf
    Ship "1" *-- "1..*" Cabin : contains
    Ship "1" o-- "0..*" Cruise : deployedOn
    Itinerary "1" o-- "1..*" Cruise : basisFor
    Itinerary "1" o-- "2..*" Port : callsAt
    Cruise "1" o-- "0..*" Booking : bookedUnder
    Booking "1" *-- "1..*" BookingPassenger : consistsOf
    Passenger "1" <-- "1" BookingPassenger : identifies
    Cabin "1" <-- "0..1" BookingPassenger : assignedTo
    Booking "1" *-- "0..*" Payment : settledBy
    Booking "1" --> "0..1" OnboardAccount : settlementFolio
    Cruise "1" o-- "0..*" OnboardActivity : offers
    BookingPassenger "0..*" o-- "0..1" TourPackage : subscribedPackage
    OnboardActivity "0..*" o-- "0..*" TourPackage : includedIn
```
