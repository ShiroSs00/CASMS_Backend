package com.casms.ship.service;

import com.casms.activity.entity.Registration;
import com.casms.activity.repository.RegistrationRepository;
import com.casms.booking.entity.BookingPassenger;
import com.casms.booking.enums.BookingStatus;
import com.casms.booking.repository.BookingPassengerRepository;
import com.casms.ship.dto.response.PassengerItineraryResponse;
import com.casms.ship.dto.response.PassengerItineraryResponse.CruiseDay;
import com.casms.ship.dto.response.PassengerItineraryResponse.CruiseStopSchedule;
import com.casms.ship.dto.response.PassengerItineraryResponse.RegisteredActivity;
import com.casms.ship.entity.Cruise;
import com.casms.ship.entity.CruiseStop;
import com.casms.ship.entity.Itinerary;
import com.casms.ship.entity.ItineraryDay;
import com.casms.ship.enums.ItineraryStatus;
import com.casms.ship.repository.CruiseStopRepository;
import com.casms.ship.repository.ItineraryDayRepository;
import com.casms.ship.repository.ItineraryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PassengerItineraryService {

    private static final List<BookingStatus> VISIBLE_BOOKING_STATUSES =
            List.of(BookingStatus.Confirmed, BookingStatus.Cancelled);
    private static final List<ItineraryStatus> PASSENGER_VISIBLE_ITINERARY_STATUSES = List.of(
            ItineraryStatus.OPEN_FOR_SALE,
            ItineraryStatus.ACTIVE,
            ItineraryStatus.COMPLETED,
            ItineraryStatus.CANCELLED
    );

    private final BookingPassengerRepository bookingPassengerRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final CruiseStopRepository cruiseStopRepository;
    private final RegistrationRepository registrationRepository;

    @Transactional(readOnly = true)
    public List<PassengerItineraryResponse> mine(UUID authenticatedUserId) {
        if (authenticatedUserId == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authenticated passenger is required");
        }

        List<BookingPassenger> passengers = bookingPassengerRepository.findPassengerTrips(
                authenticatedUserId, VISIBLE_BOOKING_STATUSES);
        if (passengers.isEmpty()) return List.of();

        List<UUID> cruiseIds = passengers.stream().map(bp -> bp.getBooking().getCruise().getCruiseId())
                .distinct().toList();
        List<Itinerary> itineraries = itineraryRepository.findByCruiseCruiseIdInAndStatusIn(
                cruiseIds, PASSENGER_VISIBLE_ITINERARY_STATUSES);
        Map<UUID, Itinerary> itineraryByCruiseId = itineraries.stream()
                .collect(Collectors.toMap(itinerary -> itinerary.getCruise().getCruiseId(), itinerary -> itinerary));

        List<UUID> itineraryIds = itineraries.stream().map(Itinerary::getItineraryId).toList();
        Map<UUID, List<CruiseStop>> stopsByArrivalDay = new HashMap<>();
        Map<UUID, List<CruiseStop>> stopsByDepartureDay = new HashMap<>();
        if (!itineraryIds.isEmpty()) {
            List<CruiseStop> stops = cruiseStopRepository
                    .findByItineraryItineraryIdInOrderByItineraryItineraryIdAscStopOrderAsc(itineraryIds);
            for (CruiseStop stop : stops) {
                stopsByArrivalDay.computeIfAbsent(stop.getArrivalItineraryDay().getItineraryDayId(), ignored -> new ArrayList<>()).add(stop);
                stopsByDepartureDay.computeIfAbsent(stop.getDepartureItineraryDay().getItineraryDayId(), ignored -> new ArrayList<>()).add(stop);
            }
        }
        Map<UUID, List<ItineraryDay>> daysByItinerary = itineraryIds.isEmpty() ? Map.of()
                : itineraryDayRepository.findByItineraryItineraryIdInOrderByItineraryItineraryIdAscDayNumberAsc(itineraryIds)
                .stream().collect(Collectors.groupingBy(day -> day.getItinerary().getItineraryId()));

        List<UUID> bookingPassengerIds = passengers.stream().map(BookingPassenger::getBookingPassengerId).toList();
        Map<UUID, List<Registration>> registrationsByBookingPassenger = registrationRepository
                .findTripRegistrations(bookingPassengerIds).stream()
                .collect(Collectors.groupingBy(r -> r.getBookingPassenger().getBookingPassengerId()));

        return passengers.stream().map(bp -> {
            Cruise cruise = bp.getBooking().getCruise();
            Itinerary itinerary = itineraryByCruiseId.get(cruise.getCruiseId());
            List<CruiseDay> cruiseDays = itinerary == null ? List.of()
                    : daysByItinerary.getOrDefault(itinerary.getItineraryId(), List.of()).stream()
                    .sorted(Comparator.comparing(ItineraryDay::getDayNumber))
                    .map(day -> toCruiseDay(day, stopsByArrivalDay, stopsByDepartureDay))
                    .toList();

            return new PassengerItineraryResponse(
                    bp.getBookingPassengerId(), bp.getBooking().getBookingId(), bp.getBooking().getBookingReference(),
                    bp.getBooking().getBookingStatus(), bp.getParticipationStatus(),
                    cruise.getCruiseId(), cruise.getCruiseCode(), cruise.getCruiseName(), cruise.getStatus(),
                    itinerary == null ? null : itinerary.getItineraryId(),
                    itinerary == null ? null : itinerary.getStatus(),
                    itinerary == null ? null : itinerary.getStartDate(),
                    itinerary == null ? null : itinerary.getEndDate(),
                    cruiseDays,
                    registrationsByBookingPassenger.getOrDefault(bp.getBookingPassengerId(), List.of()).stream()
                            .filter(registration -> belongsToCruise(registration, cruise.getCruiseId()))
                            .map(PassengerItineraryService::toRegisteredActivity).toList()
            );
        }).toList();
    }

    private CruiseDay toCruiseDay(ItineraryDay day,
                                 Map<UUID, List<CruiseStop>> arrivalStops,
                                 Map<UUID, List<CruiseStop>> departureStops) {
        Map<UUID, CruiseStop> stopsForDay = new HashMap<>();
        arrivalStops.getOrDefault(day.getItineraryDayId(), List.of()).forEach(stop -> stopsForDay.put(stop.getCruiseStopId(), stop));
        departureStops.getOrDefault(day.getItineraryDayId(), List.of()).forEach(stop -> stopsForDay.put(stop.getCruiseStopId(), stop));
        List<CruiseStopSchedule> stops = stopsForDay.values().stream()
                .sorted(Comparator.comparing(CruiseStop::getStopOrder))
                .map(stop -> new CruiseStopSchedule(stop.getCruiseStopId(), stop.getStopOrder(),
                        stop.getPort().getPortName(), stop.getDestination() == null ? null : stop.getDestination().getDestinationName(),
                        stop.getPlannedArrival(), stop.getPlannedDeparture(), stop.getActualArrival(),
                        stop.getActualDeparture(), stop.getStatus()))
                .toList();
        return new CruiseDay(day.getDayNumber(), day.getDate(), day.getDayType(), day.getDescription(), stops);
    }

    private static RegisteredActivity toRegisteredActivity(Registration registration) {
        if (registration.getOnboardActivity() != null) {
            var activity = registration.getOnboardActivity();
            return new RegisteredActivity(registration.getRegistrationId(), "ONBOARD_ACTIVITY",
                    activity.getOnboardActivityId(), activity.getActivityName(), activity.getStartTime(),
                    activity.getEndTime(), activity.getLocation(), registration.getStatus(),
                    activity.getStatus().name(), registration.getQuantity());
        }
        var excursion = registration.getShoreExcursion();
        if (excursion != null) {
            return new RegisteredActivity(registration.getRegistrationId(), "SHORE_EXCURSION",
                    excursion.getShoreExcursionId(), excursion.getExcursionName(), excursion.getStartTime(),
                    excursion.getPlannedReturnTime(), excursion.getMeetingPoint(), registration.getStatus(),
                    excursion.getStatus().name(), registration.getQuantity());
        }
        throw new IllegalStateException("Registration must reference an onboard activity or shore excursion");
    }

    private static boolean belongsToCruise(Registration registration, UUID cruiseId) {
        if (registration.getOnboardActivity() != null) {
            return registration.getOnboardActivity().getCruise().getCruiseId().equals(cruiseId);
        }
        return registration.getShoreExcursion() != null
                && registration.getShoreExcursion().getCruiseStop().getItinerary().getCruise().getCruiseId().equals(cruiseId);
    }
}
