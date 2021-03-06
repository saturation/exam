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

package system;


import com.google.inject.Inject;
import controllers.base.BaseController;
import io.ebean.Ebean;
import models.Exam;
import models.ExamEnrolment;
import models.ExamMachine;
import models.ExamRoom;
import models.Session;
import models.User;
import org.joda.time.DateTime;
import org.joda.time.Minutes;
import org.joda.time.format.ISODateTimeFormat;
import play.Environment;
import play.Logger;
import play.cache.SyncCacheApi;
import play.http.ActionCreator;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Result;
import util.AppUtil;

import javax.xml.bind.DatatypeConverter;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class SystemRequestHandler implements ActionCreator {

    private static final String SITNET_FAILURE_HEADER_KEY = "x-exam-token-failure";

    protected SyncCacheApi cache;
    protected Environment environment;

    @Inject
    public SystemRequestHandler(SyncCacheApi cache, Environment environment) {
        this.cache = cache;
        this.environment = environment;
    }

    @Override
    public Action createAction(Http.Request request, Method actionMethod) {
        String token = BaseController.getToken(request).orElse("");
        Session session = cache.get(BaseController.SITNET_CACHE_KEY + token);
        boolean temporalStudent = session != null && session.isTemporalStudent();
        User user = session == null ? null : Ebean.find(User.class, session.getUserId());
        AuditLogger.log(request, user);

        // logout, no further processing
        if (request.path().equals("/app/logout")) {
            return propagateAction();
        }

        return validateSession(session, token).orElseGet(() -> {
            updateSession(request, session, token);
            if ((user == null || !user.hasRole("STUDENT", session)) && !temporalStudent) {
                // propagate further right away
                return propagateAction();
            } else {
                // requests are candidates for extra processing
                return propagateAction(getReservationHeaders(request, user));
            }
        });
    }

    private Optional<Action> validateSession(Session session, String token) {
        if (session == null) {
            if (token == null) {
                Logger.debug("User not logged in");
            } else {
                Logger.info("Session with token {} not found", token);
            }
            return Optional.of(propagateAction());
        } else if (!session.getValid()) {
            Logger.warn("Session #{} is marked as invalid", token);
            return Optional.of(new Action.Simple() {
                @Override
                public CompletionStage<Result> call(final Http.Context ctx) {
                    return CompletableFuture.supplyAsync(() -> {
                                ctx.response().getHeaders().put(SITNET_FAILURE_HEADER_KEY, "Invalid token");
                                return Action.badRequest("Token has expired / You have logged out, please close all browser windows and login again.");
                            }
                    );
                }
            });
        } else {
            return Optional.empty();
        }
    }

    private Map<String, String> getReservationHeaders(Http.RequestHeader request, User user) {
        Map<String, String> headers = new HashMap<>();
        Optional<ExamEnrolment> ongoingEnrolment = getNextEnrolment(user.getId(), 0);
        if (ongoingEnrolment.isPresent()) {
            handleOngoingEnrolment(ongoingEnrolment.get(), request, headers);
        } else {
            DateTime now = new DateTime();
            int lookAheadMinutes = Minutes.minutesBetween(now, now.plusDays(1).withMillisOfDay(0)).getMinutes();
            Optional<ExamEnrolment> upcomingEnrolment = getNextEnrolment(user.getId(), lookAheadMinutes);
            if (upcomingEnrolment.isPresent()) {
                handleUpcomingEnrolment(upcomingEnrolment.get(), request, headers);
            } else if (isOnExamMachine(request)) {
                // User is logged on an exam machine but has no exams for today
                headers.put("x-exam-upcoming-exam", "none");
            }
        }
        return headers;
    }

    private Action propagateAction() {
        return propagateAction(Collections.emptyMap());
    }

    private Action propagateAction(Map<String, String> headers) {
        return new Action.Simple() {
            @Override
            public CompletionStage<Result> call(Http.Context ctx) {
                CompletionStage<Result> result = delegate.call(ctx);
                Http.Response response = ctx.response();
                response.setHeader("Cache-Control", "no-cache;no-store");
                response.setHeader("Pragma", "no-cache");
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    response.setHeader(entry.getKey(), entry.getValue());
                }
                return result;
            }
        };
    }

    private void updateSession(Http.RequestHeader request, Session session, String token) {
        if (!request.path().contains("checkSession")) {
            session.setSince(DateTime.now());
            cache.set(BaseController.SITNET_CACHE_KEY + token, session);
        }
    }

    private boolean isOnExamMachine(Http.RequestHeader request) {
        return Ebean.find(ExamMachine.class).where()
                .eq("ipAddress", request.remoteAddress())
                .findOneOrEmpty()
                .isPresent();
    }

    private boolean isMachineOk(ExamEnrolment enrolment, Http.RequestHeader request, Map<String,
            String> headers) {
        // Loose the checks for dev usage to facilitate for easier testing
        if (environment.isDev()) {
            return true;
        }
        ExamMachine examMachine = enrolment.getReservation().getMachine();
        ExamRoom room = examMachine.getRoom();

        String machineIp = examMachine.getIpAddress();
        String remoteIp = request.remoteAddress();

        Logger.debug("User is on IP: {} <-> Should be on IP: {}", remoteIp, machineIp);

        if (!remoteIp.equals(machineIp)) {
            String message;
            String header;

            // Is this a known machine?
            ExamMachine lookedUp = Ebean.find(ExamMachine.class).where().eq("ipAddress", remoteIp).findUnique();
            if (lookedUp == null) {
                // IP not known
                header = "x-exam-unknown-machine";
                message = room.getCampus() + ":::" +
                        room.getBuildingName() + ":::" +
                        room.getRoomCode() + ":::" +
                        examMachine.getName() + ":::" +
                        ISODateTimeFormat.dateTime().print(new DateTime(enrolment.getReservation().getStartAt()));
            } else if (lookedUp.getRoom().getId().equals(room.getId())) {
                // Right room, wrong machine
                header = "x-exam-wrong-machine";
                message = enrolment.getId() + ":::" + lookedUp.getId();
            } else {
                // Wrong room
                header = "x-exam-wrong-room";
                message = enrolment.getId() + ":::" + lookedUp.getId();
            }
            headers.put(header, DatatypeConverter.printBase64Binary(message.getBytes()));
            Logger.debug("room and machine not ok. " + message);
            return false;
        }
        Logger.debug("room and machine ok");
        return true;
    }

    private String getExamHash(ExamEnrolment enrolment) {
        return enrolment.getExternalExam() != null ? enrolment.getExternalExam().getHash() : enrolment.getExam().getHash();
    }

    private void handleOngoingEnrolment(ExamEnrolment enrolment, Http.RequestHeader request, Map<String, String> headers) {
        if (isMachineOk(enrolment, request, headers)) {
            String hash = getExamHash(enrolment);
            headers.put("x-exam-start-exam", hash);
        }
    }

    private void handleUpcomingEnrolment(ExamEnrolment enrolment, Http.RequestHeader request, Map<String, String> headers) {
        if (isMachineOk(enrolment, request, headers)) {
            String hash = getExamHash(enrolment);
            headers.put("x-exam-start-exam", hash);
            headers.put("x-exam-upcoming-exam", enrolment.getId().toString());
        }
    }

    private Optional<ExamEnrolment> getNextEnrolment(Long userId, int minutesToFuture) {
        DateTime now = AppUtil.adjustDST(new DateTime());
        DateTime future = now.plusMinutes(minutesToFuture);
        List<ExamEnrolment> results = Ebean.find(ExamEnrolment.class)
                .fetch("reservation")
                .fetch("reservation.machine")
                .fetch("reservation.machine.room")
                .fetch("exam")
                .fetch("externalExam")
                .where()
                .eq("user.id", userId)
                .disjunction()
                .eq("exam.state", Exam.State.PUBLISHED)
                .eq("exam.state", Exam.State.STUDENT_STARTED)
                .jsonEqualTo("externalExam.content", "state", Exam.State.PUBLISHED.toString())
                .jsonEqualTo("externalExam.content", "state", Exam.State.STUDENT_STARTED.toString())
                .endJunction()
                .le("reservation.startAt", future)
                .gt("reservation.endAt", now)
                .isNotNull("reservation.machine")
                .orderBy("reservation.startAt")
                .findList();
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }

}
