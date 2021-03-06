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

package system.actors;

import akka.actor.AbstractActor;
import controllers.SettingsController;
import io.ebean.Ebean;
import models.Exam;
import models.ExamEnrolment;
import models.ExamInspection;
import models.ExamParticipation;
import models.GeneralSettings;
import models.Reservation;
import models.User;
import models.json.ExternalExam;
import org.joda.time.DateTime;
import play.Logger;
import util.AppUtil;
import impl.EmailComposer;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ExamAutoSaverActor extends AbstractActor {

    private EmailComposer composer;

    @Inject
    public ExamAutoSaverActor(EmailComposer composer) {
        this.composer = composer;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(String.class, s -> {
            Logger.debug("{}: Checking for ongoing exams ...", getClass().getCanonicalName());
            checkLocalExams();
            checkExternalExams();
        }).build();
    }

    private void checkLocalExams() {
        List<ExamParticipation> participations = Ebean.find(ExamParticipation.class)
                .fetch("exam")
                .fetch("reservation")
                .fetch("reservation.machine.room")
                .where()
                .isNull("ended")
                .isNotNull("reservation")
                .findList();

        if (participations.isEmpty()) {
            Logger.debug("{}: ... none found.", getClass().getCanonicalName());
            return;
        }
        markEnded(participations);
    }

    private void markEnded(List<ExamParticipation> participations) {
        for (ExamParticipation participation : participations) {
            Exam exam = participation.getExam();
            Reservation reservation = participation.getReservation();
            DateTime reservationStart = new DateTime(reservation.getStartAt());
            DateTime participationTimeLimit = reservationStart.plusMinutes(exam.getDuration());
            DateTime now = AppUtil.adjustDST(DateTime.now(), reservation.getMachine().getRoom());
            if (participationTimeLimit.isBefore(now)) {
                participation.setEnded(now);
                participation.setDuration(
                        new DateTime(participation.getEnded().getMillis() - participation.getStarted().getMillis()));

                GeneralSettings settings = SettingsController.getOrCreateSettings("review_deadline", null, "14");
                int deadlineDays = Integer.parseInt(settings.getValue());
                DateTime deadline = new DateTime(participation.getEnded()).plusDays(deadlineDays);
                participation.setDeadline(deadline);

                participation.save();
                Logger.info("{}: ... setting exam {} state to REVIEW", getClass().getCanonicalName(), exam.getId());
                exam.setState(Exam.State.REVIEW);
                exam.save();
                if (exam.isPrivate()) {
                    // Notify teachers
                    Set<User> recipients = new HashSet<>();
                    recipients.addAll(exam.getParent().getExamOwners());
                    recipients.addAll(exam.getExamInspections().stream().map(
                            ExamInspection::getUser).collect(Collectors.toSet()));
                    AppUtil.notifyPrivateExamEnded(recipients, exam, composer);
                }
            } else {
                Logger.info("{}: ... exam {} is ongoing until {}", getClass().getCanonicalName(), exam.getId(),
                        participationTimeLimit);
            }
        }
    }

    private void checkExternalExams() {
        List<ExamEnrolment> enrolments = Ebean.find(ExamEnrolment.class)
                .fetch("externalExam")
                .fetch("reservation")
                .fetch("reservation.machine.room")
                .where()
                .isNotNull("externalExam")
                .isNotNull("externalExam.started")
                .isNull("externalExam.finished")
                .isNotNull("reservation.externalRef")
                .findList();
        for (ExamEnrolment enrolment : enrolments) {
            ExternalExam exam = enrolment.getExternalExam();
            Exam content;
            try {
                content = exam.deserialize();
            } catch (IOException e) {
                Logger.error("failed to parse content out of an external exam");
                continue;
            }
            Reservation reservation = enrolment.getReservation();
            DateTime reservationStart = new DateTime(reservation.getStartAt());
            DateTime participationTimeLimit = reservationStart.plusMinutes(content.getDuration());
            DateTime now = AppUtil.adjustDST(DateTime.now(), reservation.getMachine().getRoom());
            if (participationTimeLimit.isBefore(now)) {
                exam.setFinished(now);
                content.setState(Exam.State.REVIEW);
                try {
                    exam.serialize(content);
                    Logger.info("{}: ... setting external exam {} state to REVIEW", getClass().getCanonicalName(), exam.getHash());
                } catch (IOException e) {
                    Logger.error("failed to parse content out of an external exam");
                }
            }
        }
    }

}
