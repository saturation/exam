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
import io.ebean.Ebean;
import io.ebean.Query;
import io.ebean.text.PathProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import controllers.base.ActionMethod;
import controllers.base.BaseController;
import models.*;
import models.questions.ClozeTestAnswer;
import models.questions.EssayAnswer;
import models.questions.Question;
import org.joda.time.DateTime;
import play.Environment;
import play.data.DynamicForm;
import play.db.ebean.Transactional;
import play.mvc.Result;
import scala.concurrent.duration.Duration;
import system.interceptors.ExamActionRouter;
import system.interceptors.SensitiveDataPolicy;
import util.AppUtil;
import impl.AutoEvaluationHandler;
import impl.EmailComposer;

import javax.inject.Inject;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@SensitiveDataPolicy(sensitiveFieldNames = {"score", "defaultScore", "correctOption"})
@Restrict({@Group("STUDENT")})
public class StudentExamController extends BaseController {

    protected final EmailComposer emailComposer;
    protected final ActorSystem actor;
    private final AutoEvaluationHandler autoEvaluationHandler;
    protected final Environment environment;

    @Inject
    public StudentExamController(EmailComposer emailComposer, ActorSystem actor,
                                 AutoEvaluationHandler autoEvaluationHandler, Environment environment) {
        this.emailComposer = emailComposer;
        this.actor = actor;
        this.autoEvaluationHandler = autoEvaluationHandler;
        this.environment = environment;
    }


    @ActionMethod
    @Transactional
    @ExamActionRouter
    public Result startExam(String hash) throws IOException {
        User user = getLoggedUser();
        Exam prototype = getPrototype(hash);
        Exam possibleClone = getPossibleClone(hash, user);
        // no exam found for hash
        if (prototype == null && possibleClone == null) {
            return notFound();
        }
        // Exam not started yet, create new exam for student
        if (possibleClone == null) {
            ExamEnrolment enrolment = getEnrolment(user, prototype);
            return getEnrolmentError(enrolment).orElseGet(() -> {
                Exam newExam = createNewExam(prototype, user, enrolment);
                newExam.setCloned(true);
                newExam.setDerivedMaxScores();
                processClozeTestQuestions(newExam);
                return ok(newExam, getPath(false));
            });
        } else {
            // Exam started already
            // sanity check
            if (possibleClone.getState() != Exam.State.STUDENT_STARTED) {
                return forbidden();
            }
            possibleClone.setCloned(false);
            possibleClone.setDerivedMaxScores();
            processClozeTestQuestions(possibleClone);
            return ok(possibleClone, getPath(false));
        }
    }

    @ActionMethod
    @Transactional
    public Result turnExam(String hash) {
        User user = getLoggedUser();

        Exam exam = Ebean.find(Exam.class)
                .fetch("examSections.sectionQuestions.question")
                .where()
                .eq("creator", user)
                .eq("hash", hash)
                .findUnique();
        if (exam == null) {
            return notFound("sitnet_error_exam_not_found");
        }
        ExamParticipation p = Ebean.find(ExamParticipation.class)
                .where()
                .eq("exam.id", exam.getId())
                .eq("user", user)
                .isNull("ended")
                .findUnique();

        if (p != null) {
            DateTime now = AppUtil.adjustDST(DateTime.now(), p.getReservation().getMachine().getRoom());
            p.setEnded(now);
            p.setDuration(new DateTime(p.getEnded().getMillis() - p.getStarted().getMillis()));

            GeneralSettings settings = SettingsController.getOrCreateSettings("review_deadline", null, "14");
            int deadlineDays = Integer.parseInt(settings.getValue());
            DateTime deadline = p.getEnded().plusDays(deadlineDays);
            p.setDeadline(deadline);
            p.save();
            exam.setState(Exam.State.REVIEW);
            exam.update();
            if (exam.isPrivate()) {
                notifyTeachers(exam);
            }
            autoEvaluationHandler.autoEvaluate(exam);
            return ok("Exam sent for review");
        } else {
            return ok("exam already returned");
        }
    }

    @ActionMethod
    @Transactional
    public Result abortExam(String hash) {
        User user = getLoggedUser();
        Exam exam = Ebean.find(Exam.class).where().eq("creator", user).eq("hash", hash).findUnique();
        if (exam == null) {
            return notFound("sitnet_error_exam_not_found");
        }
        ExamParticipation p = Ebean.find(ExamParticipation.class)
                .where()
                .eq("exam.id", exam.getId())
                .eq("user", user)
                .isNull("ended")
                .findUnique();

        if (p != null) {
            DateTime now = AppUtil.adjustDST(DateTime.now(), p.getReservation().getMachine().getRoom());
            p.setEnded(now);
            p.setDuration(new DateTime(p.getEnded().getMillis() - p.getStarted().getMillis()));
            p.save();
            exam.setState(Exam.State.ABORTED);
            exam.update();
            if (exam.isPrivate()) {
                notifyTeachers(exam);
            }
            return ok("Exam aborted");
        } else {
            return forbidden("Exam already returned");
        }
    }

    @ActionMethod
    public Result answerEssay(String hash, Long questionId) {
        return getEnrolmentError(hash).orElseGet(() -> {
            DynamicForm df = formFactory.form().bindFromRequest();
            String essayAnswer = df.get("answer");
            ExamSectionQuestion question = Ebean.find(ExamSectionQuestion.class, questionId);
            if (question == null) {
                return forbidden();
            }
            EssayAnswer answer = question.getEssayAnswer();
            if (answer == null) {
                answer = new EssayAnswer();
            } else if (df.get("objectVersion") != null) {
                long objectVersion = Long.parseLong(df.get("objectVersion"));
                answer.setObjectVersion(objectVersion);
            }
            answer.setAnswer(essayAnswer);
            answer.save();
            question.setEssayAnswer(answer);
            question.save();
            return ok(answer);
        });
    }

    @ActionMethod
    public Result answerMultiChoice(String hash, Long qid) {
        return getEnrolmentError(hash).orElseGet(() -> {
            ArrayNode node = (ArrayNode) request().body().asJson().get("oids");
            List<Long> optionIds = StreamSupport.stream(node.spliterator(), false)
                    .map(JsonNode::asLong)
                    .collect(Collectors.toList());
            ExamSectionQuestion question =
                    Ebean.find(ExamSectionQuestion.class, qid);
            if (question == null) {
                return forbidden();
            }
            question.getOptions().forEach(o -> {
                o.setAnswered(optionIds.contains(o.getId()));
                o.update();
            });
            return ok();
        });
    }

    @ActionMethod
    public Result answerClozeTest(String hash, Long questionId) {
        return getEnrolmentError(hash).orElseGet(() -> {
            ExamSectionQuestion esq = Ebean.find(ExamSectionQuestion.class, questionId);
            if (esq == null) {
                return forbidden();
            }
            JsonNode node = request().body().asJson();
            ClozeTestAnswer answer = esq.getClozeTestAnswer();
            if (answer == null) {
                answer = new ClozeTestAnswer();
            } else {
                long objectVersion = node.get("objectVersion").asLong();
                answer.setObjectVersion(objectVersion);
            }
            answer.setAnswer(node.get("answer").toString());
            answer.save();
            return ok(answer, PathProperties.parse("(id, objectVersion, answer)"));
        });
    }

    private static Exam getPrototype(String hash) {
        return createQuery()
                .where()
                .eq("hash", hash)
                .isNull("parent")
                .orderBy("examSections.id, examSections.sectionQuestions.sequenceNumber")
                .findUnique();
    }

    private static Exam getPossibleClone(String hash, User user) {
        return createQuery().where()
                .eq("hash", hash)
                .eq("creator", user)
                .isNotNull("parent")
                .orderBy("examSections.id, examSections.sectionQuestions.sequenceNumber")
                .findUnique();
    }

    private static Exam createNewExam(Exam prototype, User user, ExamEnrolment enrolment) {
        Exam studentExam = prototype.copyForStudent(user);
        studentExam.setState(Exam.State.STUDENT_STARTED);
        studentExam.setCreator(user);
        studentExam.setParent(prototype);
        studentExam.generateHash();
        studentExam.save();

        enrolment.setExam(studentExam);
        enrolment.save();

        ExamParticipation examParticipation = new ExamParticipation();
        examParticipation.setUser(user);
        examParticipation.setExam(studentExam);
        examParticipation.setReservation(enrolment.getReservation());
        DateTime now = AppUtil.adjustDST(DateTime.now(), enrolment.getReservation().getMachine().getRoom());
        examParticipation.setStarted(now);
        examParticipation.save();
        user.getParticipations().add(examParticipation);

        return studentExam;
    }

    private static ExamEnrolment getEnrolment(User user, Exam prototype) {
        DateTime now = AppUtil.adjustDST(DateTime.now());
        return Ebean.find(ExamEnrolment.class)
                .fetch("reservation")
                .fetch("reservation.machine")
                .fetch("reservation.machine.room")
                .where()
                .eq("user.id", user.getId())
                .eq("exam.id", prototype.getId())
                .le("reservation.startAt", now.toDate())
                .gt("reservation.endAt", now.toDate())
                .findUnique();
    }

    protected Optional<Result> getEnrolmentError(ExamEnrolment enrolment) {
        // If this is null, it means someone is either trying to access an exam by wrong hash
        // or the reservation is not in effect right now.
        if (enrolment == null) {
            return Optional.of(forbidden("sitnet_reservation_not_found"));
        }
        String clientIP = request().remoteAddress();
        // Exam and enrolment found. Is student on the right machine?
        if (enrolment.getReservation() == null) {
            return Optional.of(forbidden("sitnet_reservation_not_found"));
        } else if (enrolment.getReservation().getMachine() == null) {
            return Optional.of(forbidden("sitnet_reservation_machine_not_found"));
        } else if (!environment.isDev() && !enrolment.getReservation().getMachine().getIpAddress().equals(clientIP)) {
            ExamRoom examRoom = Ebean.find(ExamRoom.class)
                    .fetch("mailAddress")
                    .where()
                    .eq("id", enrolment.getReservation().getMachine().getRoom().getId())
                    .findUnique();
            if (examRoom == null) {
                return Optional.of(notFound());
            }
            String message = "sitnet_wrong_exam_machine " + examRoom.getName()
                    + ", " + examRoom.getMailAddress().toString()
                    + ", sitnet_exam_machine " + enrolment.getReservation().getMachine().getName();
            return Optional.of(forbidden(message));
        }
        return Optional.empty();
    }


    public static PathProperties getPath(boolean includeEnrolment) {
        String path = "(id, name, state, instruction, hash, duration, cloned, external, course(id, code, name), executionType(id, type), " + // (
                "examLanguages(code), attachment(fileName), examOwners(firstName, lastName)" +
                "examInspections(*, user(id, firstName, lastName))" +
                "examSections(id, name, sequenceNumber, description, lotteryOn, lotteryItemCount," + // ((
                "sectionQuestions(id, sequenceNumber, maxScore, answerInstructions, evaluationCriteria, expectedWordCount, evaluationType, derivedMaxScore, " + // (((
                "question(id, type, question, attachment(id, fileName))" +
                "options(id, answered, option(id, option))" +
                "essayAnswer(id, answer, objectVersion, attachment(fileName))" +
                "clozeTestAnswer(id, question, answer, objectVersion)" +
                ")))";
        return PathProperties.parse(includeEnrolment ? String.format("(exam%s)", path) : path);
    }

    private static Query<Exam> createQuery() {
        Query<Exam> query = Ebean.find(Exam.class);
        PathProperties props = getPath(false);
        props.apply(query);
        return query;
    }

    protected void processClozeTestQuestions(Exam exam) {
        Set<Question> questionsToHide = new HashSet<>();
        exam.getExamSections().stream()
                .flatMap(es -> es.getSectionQuestions().stream())
                .filter(esq -> esq.getQuestion().getType() == Question.Type.ClozeTestQuestion)
                .forEach(esq -> {
                    ClozeTestAnswer answer = esq.getClozeTestAnswer();
                    if (answer == null) {
                        answer = new ClozeTestAnswer();
                    }
                    answer.setQuestion(esq);
                    esq.setClozeTestAnswer(answer);
                    esq.update();
                    questionsToHide.add(esq.getQuestion());
                });
        questionsToHide.forEach(q -> q.setQuestion(null));
    }


    private Optional<Result> getEnrolmentError(String hash) {
        ExamEnrolment enrolment = Ebean.find(ExamEnrolment.class).where()
                .eq("exam.hash", hash)
                .eq("exam.creator", getLoggedUser())
                .eq("exam.state", Exam.State.STUDENT_STARTED)
                .findUnique();
        return getEnrolmentError(enrolment);
    }

    private void notifyTeachers(Exam exam) {
        Set<User> recipients = new HashSet<>();
        recipients.addAll(exam.getParent().getExamOwners());
        recipients.addAll(exam.getExamInspections().stream().map(
                ExamInspection::getUser).collect(Collectors.toSet()));
        actor.scheduler().scheduleOnce(Duration.create(1, TimeUnit.SECONDS),
                () -> AppUtil.notifyPrivateExamEnded(recipients, exam, emailComposer),
                actor.dispatcher());
    }

}
