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

package impl;

import com.google.inject.ImplementedBy;
import models.Exam;
import models.ExamEnrolment;
import models.ExamMachine;
import models.LanguageInspection;
import models.Reservation;
import models.User;

import java.util.Set;

@ImplementedBy(value = EmailComposerImpl.class)
public interface EmailComposer {

    /**
     * Message sent to student when review is ready.
     */
    void composeInspectionReady(User student, User reviewer, Exam exam, Set<User> cc);

    /**
     * Message sent to student when review is ready.
     */
    void composeInspectionMessage(User inspector, User sender, Exam exam, String msg);

    /**
     * Weekly summary report
     */
    void composeWeeklySummary(User teacher);

    /**
     * Message sent to student when reservation has been made.
     */
    void composeReservationNotification(User student, Reservation reservation, Exam exam, Boolean isReminder);

    /**
     * Message sent to newly added inspectors.
     */
    void composeExamReviewRequest(User toUser, User fromUser, Exam exam, String message);

    /**
     * Message sent to student when reservation has been cancelled.
     */
    void composeReservationCancellationNotification(User student, Reservation reservation, String message,
                                                    Boolean isStudentUser, ExamEnrolment enrolment);

    /**
     * Message sent to student when reservation has been changed.
     */
    void composeReservationChangeNotification(User student, ExamMachine previous, ExamMachine current,
                                              ExamEnrolment enrolment);

    /**
     * Message sent to student when he/she has been enrolled to a private exam.
     */
    void composePrivateExamParticipantNotification(User student, User fromUser, Exam exam);

    /**
     * Message sent to teacher when student has finished a private exam.
     */
    void composePrivateExamEnded(User toUser, Exam exam);

    /**
     * Message sent to teacher when student did not show up for a private exam.
     */
    void composeNoShowMessage(User toUser, User student, Exam exam);

    /**
     * Message sent to student when he did not show up for exam.
     */
    void composeNoShowMessage(User student, Exam exam);

    /**
     * Message sent to teacher when language inspection is finished.
     */
    void composeLanguageInspectionFinishedMessage(User toUser, User inspector, LanguageInspection inspection);

}
