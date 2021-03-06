/*
 * Copyright (c) 2017 The members of the EXAM Consortium (https://confluence.csc.fi/display/EXAM/Konsortio-organisaatio)
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl/licence-eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed
 * on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

package controllers;

import akka.actor.ActorSystem;
import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import controllers.base.BaseController;
import controllers.iop.api.ExternalReservationHandler;
import exceptions.NotFoundException;
import impl.EmailComposer;
import io.ebean.Ebean;
import models.*;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.format.ISODateTimeFormat;
import play.Logger;
import play.libs.Json;
import play.mvc.Result;
import play.mvc.With;
import sanitizers.Attrs;
import sanitizers.CalendarReservationSanitizer;
import scala.concurrent.duration.Duration;
import util.AppUtil;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;


public class CalendarController extends BaseController {

    @Inject
    protected EmailComposer emailComposer;

    @Inject
    protected ActorSystem system;

    @Inject
    protected ExternalReservationHandler externalReservationHandler;

    private static final int LAST_HOUR = 23;

    @Restrict({@Group("ADMIN"), @Group("STUDENT")})
    public Result removeReservation(long id) throws NotFoundException {
        User user = getLoggedUser();
        final ExamEnrolment enrolment = Ebean.find(ExamEnrolment.class)
                .fetch("reservation")
                .fetch("reservation.machine")
                .fetch("reservation.machine.room")
                .where()
                .eq("user.id", user.getId())
                .eq("reservation.id", id)
                .findUnique();
        if (enrolment == null) {
            throw new NotFoundException(String.format("No reservation with id %d for current user.", id));
        }
        // Removal not permitted if reservation is in the past or ongoing
        final Reservation reservation = enrolment.getReservation();
        DateTime now = AppUtil.adjustDST(DateTime.now(), reservation);
        if (reservation.toInterval().isBefore(now) || reservation.toInterval().contains(now)) {
            return forbidden("sitnet_reservation_in_effect");
        }
        enrolment.setReservation(null);
        enrolment.setReservationCanceled(true);
        Ebean.save(enrolment);
        Ebean.delete(Reservation.class, id);

        // send email asynchronously
        final boolean isStudentUser = user.equals(enrolment.getUser());
        system.scheduler().scheduleOnce(Duration.create(1, TimeUnit.SECONDS), () -> {
            emailComposer.composeReservationCancellationNotification(enrolment.getUser(), reservation, "", isStudentUser, enrolment);
            Logger.info("Reservation cancellation confirmation email sent");
        }, system.dispatcher());
        return ok("removed");
    }

    protected Optional<Result> checkEnrolment(ExamEnrolment enrolment, User user) {
        if (enrolment == null) {
            return Optional.of(forbidden("sitnet_error_enrolment_not_found"));
        }
        // Removal not permitted if old reservation is in the past or if exam is already started
        Reservation oldReservation = enrolment.getReservation();
        if (enrolment.getExam().getState() == Exam.State.STUDENT_STARTED ||
                (oldReservation != null && oldReservation.toInterval().isBefore(DateTime.now()))) {
            return Optional.of(forbidden("sitnet_reservation_in_effect"));
        }
        // No previous reservation or it's in the future
        // If no previous reservation, check if allowed to participate. This check is skipped if user already
        // has a reservation to this exam so that change of reservation is always possible.
        if (oldReservation == null && !isAllowedToParticipate(enrolment.getExam(), user, emailComposer)) {
            return Optional.of(forbidden("sitnet_no_trials_left"));
        }
        return Optional.empty();
    }

    @With(CalendarReservationSanitizer.class)
    @Restrict({@Group("ADMIN"), @Group("STUDENT")})
    public CompletionStage<Result> createReservation() {
        Long roomId = request().attrs().get(Attrs.ROOM_ID);
        Long examId = request().attrs().get(Attrs.EXAM_ID);
        DateTime start = request().attrs().get(Attrs.START_DATE);
        DateTime end = request().attrs().get(Attrs.END_DATE);
        Collection<Integer> aids = request().attrs().get(Attrs.ACCESSABILITES);

        ExamRoom room = Ebean.find(ExamRoom.class, roomId);
        DateTime now = AppUtil.adjustDST(DateTime.now(), room);
        final User user = getLoggedUser();
        final ExamEnrolment enrolment = Ebean.find(ExamEnrolment.class)
                .fetch("reservation")
                .where()
                .eq("user.id", user.getId())
                .eq("exam.id", examId)
                .eq("exam.state", Exam.State.PUBLISHED)
                .disjunction()
                .isNull("reservation")
                .gt("reservation.startAt", now.toDate())
                .endJunction()
                .findUnique();
        Optional<Result> badEnrolment = checkEnrolment(enrolment, user);
        if (badEnrolment.isPresent()) {
            return wrapAsPromise(badEnrolment.get());
        }
        Optional<ExamMachine> machine = getRandomMachine(room, enrolment.getExam(), start, end, aids);
        if (!machine.isPresent()) {
            return wrapAsPromise(forbidden("sitnet_no_machines_available"));
        }

        // We are good to go :)
        Reservation oldReservation = enrolment.getReservation();
        Reservation reservation = new Reservation();
        reservation.setEndAt(end);
        reservation.setStartAt(start);
        reservation.setMachine(machine.get());
        reservation.setUser(user);

        // If this is due in less than a day, make sure we won't send a reminder
        if (start.minusDays(1).isBeforeNow()) {
            reservation.setReminderSent(true);
        }

        // Nuke the old reservation if any
        if (oldReservation != null) {
            String externalReference = oldReservation.getExternalRef();
            if (externalReference != null) {
                return externalReservationHandler.removeReservation(oldReservation, user)
                        .thenCompose(result -> {
                            // Refetch enrolment, otherwise
                            ExamEnrolment updatedEnrolment = Ebean.find(ExamEnrolment.class, enrolment.getId());
                            return makeNewReservation(updatedEnrolment, reservation, user);
                        });
            } else {
                enrolment.setReservation(null);
                enrolment.update();
                oldReservation.delete();
            }
        }
        return makeNewReservation(enrolment, reservation, user);
    }

    private CompletionStage<Result> makeNewReservation(ExamEnrolment enrolment, Reservation reservation, User user) {
        Ebean.save(reservation);
        enrolment.setReservation(reservation);
        enrolment.setReservationCanceled(false);
        Ebean.save(enrolment);
        Exam exam = enrolment.getExam();
        // Send some emails asynchronously
        system.scheduler().scheduleOnce(Duration.create(1, TimeUnit.SECONDS), () -> {
            emailComposer.composeReservationNotification(user, reservation, exam, false);
            Logger.info("Reservation confirmation email sent to {}", user.getEmail());
        }, system.dispatcher());

        return wrapAsPromise(ok("ok"));
    }

    protected Optional<ExamMachine> getRandomMachine(ExamRoom room, Exam exam, DateTime start, DateTime end, Collection<Integer> aids) {
        List<ExamMachine> machines = getEligibleMachines(room, aids, exam);
        Collections.shuffle(machines);
        Interval wantedTime = new Interval(start, end);
        for (ExamMachine machine : machines) {
            if (!machine.isReservedDuring(wantedTime)) {
                return Optional.of(machine);
            }
        }
        return Optional.empty();
    }

    @Restrict({@Group("ADMIN"), @Group("STUDENT")})
    public Result getSlots(Long examId, Long roomId, String day, Collection<Integer> aids) {
        User user = getLoggedUser();
        Exam exam = getEnrolledExam(examId, user);
        if (exam == null) {
            return forbidden("sitnet_error_enrolment_not_found");
        }
        ExamRoom room = Ebean.find(ExamRoom.class, roomId);
        if (room == null) {
            return forbidden(String.format("No room with id: (%d)", roomId));
        }
        Collection<TimeSlot> slots = new ArrayList<>();
        if (!room.getOutOfService() && !room.getState().equals(ExamRoom.State.INACTIVE.toString()) &&
                isRoomAccessibilitySatisfied(room, aids) && exam.getDuration() != null) {
            LocalDate searchDate;
            try {
                searchDate = parseSearchDate(day, exam, room);
            } catch (NotFoundException e) {
                return notFound();
            }
            // users reservations starting from now
            List<Reservation> reservations = Ebean.find(Reservation.class)
                    .fetch("enrolment.exam")
                    .where()
                    .eq("user", user)
                    .gt("startAt", searchDate.toDate())
                    .findList();
            // Resolve eligible machines based on software and accessibility requirements
            List<ExamMachine> machines = getEligibleMachines(room, aids, exam);
            LocalDate endOfSearch = getEndSearchDate(exam, searchDate);
            while (!searchDate.isAfter(endOfSearch)) {
                Set<TimeSlot> timeSlots = getExamSlots(user, room, exam, searchDate, reservations, machines);
                if (!timeSlots.isEmpty()) {
                    slots.addAll(timeSlots);
                }
                searchDate = searchDate.plusDays(1);
            }
        }
        return ok(Json.toJson(slots));
    }

    protected Collection<Interval> gatherSuitableSlots(ExamRoom room, LocalDate date, Integer examDuration) {
        Collection<Interval> examSlots = new ArrayList<>();
        // Resolve the opening hours for room and day
        List<ExamRoom.OpeningHours> openingHours = room.getWorkingHoursForDate(date);
        if (!openingHours.isEmpty()) {
            // Get suitable slots based on exam duration
            for (Interval slot : allSlots(openingHours, room, date)) {
                DateTime beginning = slot.getStart();
                DateTime openUntil = getEndOfOpeningHours(beginning, openingHours);
                if (!beginning.plusMinutes(examDuration).isAfter(openUntil)) {
                    DateTime end = beginning.plusMinutes(examDuration);
                    examSlots.add(new Interval(beginning, end));
                }
            }
        }
        return examSlots;
    }

    /**
     * Queries for slots for given room and day
     */
    private Set<TimeSlot> getExamSlots(User user, ExamRoom room, Exam exam, LocalDate date,
                                       Collection<Reservation> reservations, Collection<ExamMachine> machines) {
        Integer examDuration = exam.getDuration();
        Collection<Interval> examSlots = gatherSuitableSlots(room, date, examDuration);
        Map<Interval, Optional<Integer>> map = examSlots.stream().collect(
                Collectors.toMap(
                        Function.identity(),
                        es -> Optional.empty(),
                        (u, v) -> {
                            throw new IllegalStateException(String.format("Duplicate key %s", u));
                        },
                        LinkedHashMap::new));
        // Check reservation status and machine availability for each slot
        return handleReservations(map, reservations, exam, machines, user);
    }

    // HELPERS -->

    /**
     * Search date is the current date if searching for current week or earlier,
     * If searching for upcoming weeks, day of week is one.
     */
    protected static LocalDate parseSearchDate(String day, Exam exam, ExamRoom room) throws NotFoundException {
        String reservationWindow = SettingsController.getOrCreateSettings(
                "reservation_window_size", null, null).getValue();
        int windowSize = 0;
        if (reservationWindow != null) {
            windowSize = Integer.parseInt(reservationWindow);
        }
        int offset = room != null ?
                DateTimeZone.forID(room.getLocalTimezone()).getOffset(DateTime.now()) :
                AppUtil.getDefaultTimeZone().getOffset(DateTime.now());
        LocalDate now = DateTime.now().plusMillis(offset).toLocalDate();
        LocalDate reservationWindowDate = now.plusDays(windowSize);
        LocalDate examEndDate = new DateTime(exam.getExamActiveEndDate()).plusMillis(offset).toLocalDate();
        LocalDate searchEndDate = reservationWindowDate.isBefore(examEndDate) ? reservationWindowDate : examEndDate;
        LocalDate examStartDate = new DateTime(exam.getExamActiveStartDate()).plusMillis(offset).toLocalDate();
        LocalDate searchDate = day.equals("") ? now : LocalDate.parse(day, ISODateTimeFormat.dateParser());
        searchDate = searchDate.withDayOfWeek(1);
        if (searchDate.isBefore(now)) {
            searchDate = now;
        }
        // if searching for month(s) after exam's end month -> no can do
        if (searchDate.isAfter(searchEndDate)) {
            throw new NotFoundException();
        }
        // Do not execute search before exam starts
        if (searchDate.isBefore(examStartDate)) {
            searchDate = examStartDate;
        }
        return searchDate;
    }

    /**
     * @return which one is sooner, exam period's end or week's end
     */
    private static LocalDate getEndSearchDate(Exam exam, LocalDate searchDate) {
        LocalDate endOfWeek = searchDate.dayOfWeek().withMaximumValue();
        LocalDate examEnd = new LocalDate(exam.getExamActiveEndDate());
        String reservationWindow = SettingsController.getOrCreateSettings(
                "reservation_window_size", null, null).getValue();
        int windowSize = 0;
        if (reservationWindow != null) {
            windowSize = Integer.parseInt(reservationWindow);
        }
        LocalDate reservationWindowDate = LocalDate.now().plusDays(windowSize);
        LocalDate endOfSearchDate = examEnd.isBefore(reservationWindowDate) ? examEnd : reservationWindowDate;

        return endOfWeek.isBefore(endOfSearchDate) ? endOfWeek : endOfSearchDate;
    }

    protected Exam getEnrolledExam(Long examId, User user) {
        DateTime now = AppUtil.adjustDST(DateTime.now());
        ExamEnrolment enrolment = Ebean.find(ExamEnrolment.class)
                .fetch("exam")
                .where()
                .eq("user", user)
                .eq("exam.id", examId)
                .eq("exam.state", Exam.State.PUBLISHED)
                .disjunction()
                .isNull("reservation")
                .gt("reservation.startAt", now.toDate())
                .endJunction()
                .findUnique();
        return enrolment == null ? null : enrolment.getExam();
    }

    /**
     * @return all intervals that fall within provided working hours
     */
    private static Iterable<Interval> allSlots(Iterable<ExamRoom.OpeningHours> openingHours, ExamRoom room, LocalDate date) {
        Collection<Interval> intervals = new ArrayList<>();
        List<ExamStartingHour> startingHours = room.getExamStartingHours();
        if (startingHours.isEmpty()) {
            // Default to 1 hour slots that start at the hour
            startingHours = createDefaultStartingHours(room.getLocalTimezone());
        }
        Collections.sort(startingHours);
        DateTime now = DateTime.now().plusMillis(DateTimeZone.forID(room.getLocalTimezone()).getOffset(DateTime.now()));
        for (ExamRoom.OpeningHours oh : openingHours) {
            int tzOffset = oh.getTimezoneOffset();
            DateTime instant = now.getDayOfYear() == date.getDayOfYear() ? now : oh.getHours().getStart();
            DateTime slotEnd = oh.getHours().getEnd();
            DateTime beginning = nextStartingTime(instant, startingHours, tzOffset);
            while (beginning != null) {
                DateTime nextBeginning = nextStartingTime(beginning.plusMillis(1), startingHours, tzOffset);
                if (beginning.isBefore(oh.getHours().getStart())) {
                    beginning = nextBeginning;
                    continue;
                }
                if (nextBeginning != null && !nextBeginning.isAfter(slotEnd)) {
                    intervals.add(new Interval(beginning.minusMillis(tzOffset), nextBeginning.minusMillis(tzOffset)));
                    beginning = nextBeginning;
                } else if (beginning.isBefore(slotEnd)) {
                    // We have some spare time in the end, take it as well
                    intervals.add(new Interval(beginning.minusMillis(tzOffset), slotEnd.minusMillis(tzOffset)));
                    break;
                } else {
                    break;
                }
            }
        }
        return intervals;
    }

    // Go through the slots and check if conflicting reservations exist. Decorate such slots with conflict information.
    // Available machine count can be either pre-calculated (in which case the amount comes in form of map value) or not
    // (in which case the calculation is done based on machines provided).
    protected Set<TimeSlot> handleReservations(Map<Interval, Optional<Integer>> examSlots, Collection<Reservation> reservations,
                                               Exam exam, Collection<ExamMachine> machines, User user) {
        Set<TimeSlot> results = new LinkedHashSet<>();
        for (Map.Entry<Interval, Optional<Integer>> entry : examSlots.entrySet()) {
            Interval slot = entry.getKey();
            List<Reservation> conflicting = getReservationsDuring(reservations, slot);
            if (!conflicting.isEmpty()) {
                Optional<Reservation> concernsAnotherExam = conflicting.stream()
                        .filter(c -> !c.getEnrolment().getExam().equals(exam))
                        .findFirst();
                if (concernsAnotherExam.isPresent()) {
                    // User has a reservation to another exam, do not allow making overlapping reservations
                    Reservation reservation = concernsAnotherExam.get();
                    String conflictingExam = reservation.getEnrolment().getExam().getName();
                    results.add(new TimeSlot(reservation.toInterval(), -1, conflictingExam));
                    continue;
                } else {
                    // User has an existing reservation to this exam
                    Reservation reservation = conflicting.get(0);
                    if (!reservation.toInterval().equals(slot)) {
                        // No matching slot found in this room, add the reservation as-is.
                        results.add(new TimeSlot(reservation.toInterval(), -1, null));
                    } else {
                        // This is exactly the same slot, avoid duplicates and continue.
                        results.add(new TimeSlot(slot, -1, null));
                        continue;
                    }
                }
            }
            // Resolve available machine count. Assume precalculated values within the map if no machines provided
            int availableMachineCount;
            if (entry.getValue().isPresent()) {
                availableMachineCount = entry.getValue().get();
            } else {
                availableMachineCount = machines.stream()
                        .filter(m -> !isReservedByOthersDuring(m, slot, user))
                        .collect(Collectors.toList())
                        .size();
            }
            results.add(new TimeSlot(slot, availableMachineCount, null));
        }
        return results;
    }

    private boolean isReservedByUser(Reservation reservation, User user) {
        boolean externallyReserved = reservation.getExternalUserRef() != null
                && reservation.getExternalRef().equals(user.getEppn());
        return externallyReserved ||
                (reservation.getUser() != null && reservation.getUser().equals(user));
    }

    private boolean isReservedByOthersDuring(ExamMachine machine, Interval interval, User user) {
        return machine.getReservations()
                .stream()
                .filter(r -> !isReservedByUser(r, user))
                .anyMatch(r -> interval.overlaps(r.toInterval()));
    }

    private static List<Reservation> getReservationsDuring(Collection<Reservation> reservations, Interval interval) {
        return reservations.stream().filter(r -> interval.overlaps(r.toInterval())).collect(Collectors.toList());
    }

    private static List<ExamMachine> getEligibleMachines(ExamRoom room, Collection<Integer> access, Exam exam) {
        List<ExamMachine> candidates = Ebean.find(ExamMachine.class)
                .where()
                .eq("room.id", room.getId())
                .ne("outOfService", true)
                .ne("archived", true)
                .findList();
        Iterator<ExamMachine> it = candidates.listIterator();
        while (it.hasNext()) {
            ExamMachine machine = it.next();
            if (!isMachineAccessibilitySatisfied(machine, access)) {
                it.remove();
            }
            if (exam != null && !machine.hasRequiredSoftware(exam)) {
                it.remove();
            }
        }
        return candidates;
    }

    private static boolean isRoomAccessibilitySatisfied(ExamRoom room, Collection<Integer> wanted) {
        List<Accessibility> roomAccessibility = room.getAccessibility();
        return roomAccessibility.stream()
                .map(accessibility -> accessibility.getId().intValue())
                .collect(Collectors.toList())
                .containsAll(wanted);
    }

    // TODO: this room vs machine accessibility needs some UI work and rethinking.
    private static boolean isMachineAccessibilitySatisfied(ExamMachine machine, Collection<Integer> wanted) {
        if (machine.isAccessible()) { // this has it all :)
            return true;
        }
        // The following is always empty because no UI-support for adding
        List<Accessibility> machineAccessibility = machine.getAccessibility();
        return machineAccessibility.stream()
                .map(accessibility -> accessibility.getId().intValue())
                .collect(Collectors.toList())
                .containsAll(wanted);
    }

    private static DateTime nextStartingTime(DateTime instant, List<ExamStartingHour> startingHours, int offset) {
        for (ExamStartingHour esh : startingHours) {
            int timeMs = new LocalTime(esh.getStartingHour()).plusMillis(offset).getMillisOfDay();
            DateTime datetime = instant.withMillisOfDay(timeMs);
            if (!datetime.isBefore(instant)) {
                return datetime;
            }
        }
        return null;
    }

    private static List<ExamStartingHour> createDefaultStartingHours(String roomTz) {
        // Get offset from Jan 1st in order to no have DST in effect
        DateTimeZone zone = DateTimeZone.forID(roomTz);
        DateTime beginning = DateTime.now().withDayOfYear(1).withTimeAtStartOfDay();
        DateTime ending = beginning.plusHours(LAST_HOUR);
        List<ExamStartingHour> hours = new ArrayList<>();
        while (!beginning.isAfter(ending)) {
            ExamStartingHour esh = new ExamStartingHour();
            esh.setStartingHour(beginning.toDate());
            esh.setTimezoneOffset(zone.getOffset(beginning));
            hours.add(esh);
            beginning = beginning.plusHours(1);
        }
        return hours;
    }

    private static DateTime getEndOfOpeningHours(DateTime instant, List<ExamRoom.OpeningHours> openingHours) {
        for (ExamRoom.OpeningHours oh : openingHours) {
            if (oh.getHours().contains(instant.plusMillis(oh.getTimezoneOffset()))) {
                return oh.getHours().getEnd().minusMillis(oh.getTimezoneOffset());
            }
        }
        // should not occur, indicates programming error
        throw new RuntimeException("slot not contained within opening hours, recheck logic!");
    }

    // DTO aimed for clients
    protected static class TimeSlot {
        private final String start;
        private final String end;
        private final int availableMachines;
        private final boolean ownReservation;
        private final String conflictingExam;

        public TimeSlot(Interval interval, int machineCount, String exam) {
            start = ISODateTimeFormat.dateTime().print(interval.getStart());
            end = ISODateTimeFormat.dateTime().print(interval.getEnd());
            availableMachines = machineCount;
            ownReservation = machineCount < 0;
            conflictingExam = exam;
        }

        public String getStart() {
            return start;
        }

        public String getEnd() {
            return end;
        }

        public int getAvailableMachines() {
            return availableMachines;
        }

        public boolean isOwnReservation() {
            return ownReservation;
        }

        public String getConflictingExam() {
            return conflictingExam;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TimeSlot)) return false;
            TimeSlot timeSlot = (TimeSlot) o;
            return new EqualsBuilder().append(start, timeSlot.start).append(end, timeSlot.end).build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(start).append(end).build();
        }

    }

}
