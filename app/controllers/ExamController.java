package controllers;

import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import com.avaje.ebean.Ebean;
import com.avaje.ebean.Expr;
import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.FetchConfig;
import com.avaje.ebean.text.json.JsonContext;
import com.avaje.ebean.text.json.JsonWriteOptions;
import com.fasterxml.jackson.databind.node.ObjectNode;
import exceptions.MalformedDataException;
import exceptions.SitnetException;
import models.*;
import models.questions.AbstractQuestion;
import models.questions.EssayQuestion;
import models.questions.MultipleChoiceQuestion;
import org.springframework.util.CollectionUtils;
import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.Json;
import play.mvc.Result;
import util.SitnetUtil;
import util.java.EmailComposer;
import util.java.ValidationUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class ExamController extends SitnetController {

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getExams() {

        User user = UserController.getLoggedUser();
        ExpressionList<Exam> expr = Ebean.find(Exam.class)
                .fetch("course")
                .where()
                .disjunction()
                .eq("state", Exam.State.PUBLISHED.toString())
                .eq("state", Exam.State.SAVED.toString())
                .eq("state", Exam.State.DRAFT.toString())
                .endJunction();
        if (user.hasRole("TEACHER")) {
            expr = expr
                    .disjunction()
                    .eq("shared", true)
                    .eq("creator.id", user.getId())
                    .endJunction();
        }
        List<Exam> exams = expr.findList();

        JsonContext jsonContext = Ebean.createJsonContext();
        JsonWriteOptions options = new JsonWriteOptions();
        options.setRootPathProperties("id, name, creator, course, examActiveStartDate, examActiveEndDate, parent, state");
        options.setPathProperties("creator", "firstName, lastName");
        options.setPathProperties("course", "code");
        options.setPathProperties("parent", "id");

        return ok(jsonContext.toJsonString(exams, true, options)).as("application/json");
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getExamsByState(String state) {
        List<Exam> exams = Ebean.find(Exam.class)
                .where()
                .eq("state", state)
                .findList();
        return ok(Json.toJson(exams));
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getActiveExams() {

        User user = UserController.getLoggedUser();

        // Todo: Oletetaan että tentin luoja on automaattisesti tentin tarkastaja
        List<Exam> activeExams = Ebean.find(Exam.class)
                .fetch("course")
                .where()
                .eq("creator.id", user.getId())
                .ne("state", Exam.State.SAVED.toString())
                .ne("state", Exam.State.DRAFT.toString())
                .betweenProperties("examActiveStartDate", "examActiveEndDate", new Date())
                .findList();

        // Todo: Hae tentit joissa tämä käyttäjä on tarkastaja, palauta JSON propertiesilla
        //        List<ExamEnrolment> enrolments =

        JsonContext jsonContext = Ebean.createJsonContext();
        JsonWriteOptions options = new JsonWriteOptions();
        options.setRootPathProperties("id, name, course, examActiveStartDate, examActiveEndDate");
        options.setPathProperties("course", "code");
        return ok(jsonContext.toJsonString(activeExams, true, options)).as("application/json");
    }

    @Restrict(@Group("TEACHER"))
    public static Result getTeachersExams() {

        User user = UserController.getLoggedUser();

        //Get list of exams that user is assigned to inspect
        // .setDistinct(true) not working !!!!
        List<ExamInspection> examInspections = Ebean.find(ExamInspection.class)
                .fetch("user")
                .fetch("assignedBy")
                .fetch("exam")
                .fetch("exam.parent")
                .where()
                .eq("exam.state", Exam.State.PUBLISHED.toString())
                .disjunction()
                .eq("user.id", user.getId())
                .eq("assignedBy.id", user.getId())
                .eq("exam.creator.id", user.getId())
                .endJunction()
                .isNull("exam.parent")
                .findList();

        List<ExamInspection> distinctList = new ArrayList<>();

        // distinct fix ->
        if (!CollectionUtils.isEmpty(examInspections)) {

            if (!CollectionUtils.isEmpty(examInspections)) {

                List<Exam> exams = new ArrayList<>();
                // java 1.7 ->
                for (ExamInspection e : examInspections) {
                    if (!exams.contains(e.getExam())) {
                        exams.add(e.getExam());
                        distinctList.add(e);
                    }
                }
            }
        }
        // -- fix end

        JsonContext jsonContext = Ebean.createJsonContext();
        JsonWriteOptions options = new JsonWriteOptions();
        options.setRootPathProperties("id, exam, user, assignedBy");
        options.setPathProperties("exam", "id, name, course, examActiveStartDate, examActiveEndDate");
        options.setPathProperties("user", "id");
        options.setPathProperties("assignedBy", "id");
        options.setPathProperties("exam.course", "code");

        return ok(jsonContext.toJsonString(distinctList, true, options)).as("application/json");
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getFinishedExams() {
        List<Exam> finishedExams = Ebean.find(Exam.class)
                .where()
                .lt("examActiveEndDate", new Date())
                .findList();

        return ok(Json.toJson(finishedExams));
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result deleteExam(Long id) {
        Exam exam = Ebean.find(Exam.class, id);
        if (UserController.getLoggedUser().hasRole("ADMIN") || SitnetUtil.isOwner(exam)) {

            // check if exam has children
            // if true, we cannot delete it because children exams reference this exam
            // so we just set it ARCHIVED

            int count = Ebean.find(Exam.class)
                    .where()
                    .eq("parent.id", id)
                    .findRowCount();

            if (count > 0) {
                exam.setState(Exam.State.ARCHIVED.name());
                exam = (Exam) SitnetUtil.setModifier(exam);
                exam.save();
                return ok("Exam archived");
            } else {

                // If we're here it means, this exam does not have any children.
                // e.g. this exam has never been cloned
                // we can safely delete it completely from DB

                // 1. remove enrolments. Though there shouldn't be any
                List<ExamEnrolment> examEnrolments = Ebean.find(ExamEnrolment.class)
                        .where()
                        .eq("exam.id", id)
                        .findList();

                for (ExamEnrolment e : examEnrolments) {
                    e.delete();
                }

                List<ExamInspection> examInspections = Ebean.find(ExamInspection.class)
                        .where()
                        .eq("exam.id", id)
                        .findList();

                // 2. remove inspections
                for (ExamInspection e : examInspections) {
                    e.getUser().getInspections().remove(e);
                    e.delete();
                }

                for (ExamSection es : exam.getExamSections()) {
                    for (ExamSectionQuestion esq : es.getSectionQuestions()) {
                        esq.delete();
                    }
                    es.getSectionQuestions().clear();
                    es.save();
                }

                exam.getExamSections().clear();

                // yes yes, its weird, but Ebean won't delete relations with ManyToMany on enchaced classes
                // so we just tell everyone its "deleted"
                exam.setState(Exam.State.DELETED.name());
                exam.save();

//                exam.delete();
            }


            return ok("Exam deleted");
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }

    }

    private static JsonWriteOptions getJsonOptions() {
        JsonWriteOptions options = new JsonWriteOptions();
        options.setRootPathProperties("id, name, course, parent, examType, instruction, enrollInstruction, " +
                "shared, examSections, examActiveStartDate, examActiveEndDate, room, " +
                "duration, grading, grade, customCredit, totalScore, answerLanguage, " +
                "state, examFeedback, creditType, expanded, attachment, creator, softwares, examLanguages");
        options.setPathProperties("creator", "id, firstName, lastName");
        options.setPathProperties("parent", "id, creator");
        options.setPathProperties("parent.creator", "id, firstName, lastName");
        options.setPathProperties("course", "id, code, name, level, courseUnitType, credits, institutionName, department, organisation, gradeScale");
        options.setPathProperties("course.organisation", "id, name");
        options.setPathProperties("room", "id, name");
        options.setPathProperties("softwares", "id, name");
        options.setPathProperties("examLanguages", "code");
        options.setPathProperties("attachment", "id, fileName");
        options.setPathProperties("examType", "id, type");
        options.setPathProperties("examSections", "id, name, sectionQuestions, totalScore, expanded, " +
                "lotteryOn, lotteryItemCount");
        options.setPathProperties("examSections.sectionQuestions", "sequenceNumber, question");
        options.setPathProperties("examSections.sectionQuestions.question", "attachment, id, type, question, " +
                "shared, instruction, maxCharacters, maxScore, evaluationType, evaluatedScore, evaluationCriterias, options, answer");
        options.setPathProperties("examSections.sectionQuestions.question.answer", "attachment, type, option, answer");
        options.setPathProperties("examSections.sectionQuestions.question.answer.option", "id, option, correctOption, score");
        options.setPathProperties("examSections.sectionQuestions.question.attachment", "id, fileName");
        options.setPathProperties("examSections.sectionQuestions.question.answer.attachment", "id, fileName");
        options.setPathProperties("examSections.sectionQuestions.question.options", "id, option, correctOption");
        options.setPathProperties("examSections.sectionQuestions.question.comments", "id, comment");
        options.setPathProperties("examFeedback", "id, comment");
        options.setPathProperties("examFeedback.comment", "id, comment, reply");
        return options;
    }

    private static Exam doGetExam(Long id) {
        return Ebean.find(Exam.class)
                .fetch("examSections.sectionQuestions")
                .fetch("course")
                .fetch("course.organisation")
                .where()
                .eq("id", id)
                .disjunction()
                .eq("state", Exam.State.DRAFT.toString())
                .eq("state", Exam.State.SAVED.toString())
                .eq("state", Exam.State.PUBLISHED.toString())
                .endJunction()
                .orderBy("examSections.id, examSections.sectionQuestions.sequenceNumber")
                .findUnique();
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getExamReview(Long eid) {
        Exam exam = Ebean.find(Exam.class)
                .fetch("examSections.sectionQuestions")
                .where()
                .eq("id", eid)
                .disjunction()
                .eq("state", Exam.State.REVIEW.toString())
                .eq("state", Exam.State.REVIEW_STARTED.toString())
                .eq("state", Exam.State.GRADED.toString())
                .eq("state", Exam.State.GRADED_LOGGED.toString())
                .orderBy("examSections.id, examSections.sectionQuestions.sequenceNumber")
                .findUnique();

        if (exam == null) {
            return notFound("sitnet_error_exam_not_found");
        } else if (SitnetUtil.isInspector(exam)) {
            JsonContext jsonContext = Ebean.createJsonContext();
            return ok(jsonContext.toJsonString(exam, true, getJsonOptions())).as("application/json");
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getExam(Long id) {

        Exam exam = doGetExam(id);
        if (exam == null) {
            return notFound("sitnet_error_exam_not_found");
        } else if (exam.isShared() || SitnetUtil.isOwner(exam) || UserController.getLoggedUser().hasRole("ADMIN") || SitnetUtil.isInspector(exam)) {
            JsonContext jsonContext = Ebean.createJsonContext();
            return ok(jsonContext.toJsonString(exam, true, getJsonOptions())).as("application/json");
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }


    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getExamPreview(Long id) {

        Exam exam = doGetExam(id);
        if (exam == null) {
            return notFound("sitnet_error_exam_not_found");
        }
        if (exam.isShared() || SitnetUtil.isOwner(exam) || UserController.getLoggedUser().hasRole("ADMIN") ||
                SitnetUtil.isInspector(exam)) {
            for (ExamSection es : exam.getExamSections()) {
                if (es.getLotteryOn()) {
                    Collections.shuffle(es.getSectionQuestions());
                    es.setSectionQuestions(es.getSectionQuestions().subList(0, es.getLotteryItemCount()));
                }
            }
            JsonContext jsonContext = Ebean.createJsonContext();
            return ok(jsonContext.toJsonString(exam, true, getJsonOptions())).as("application/json");
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }


    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result reviewExam(Long id) {
        DynamicForm df = Form.form().bindFromRequest();
        Exam exam = Ebean.find(Exam.class, id);
        exam.setGrade(df.get("grade"));
        exam.setCreditType(df.get("creditType"));
        exam.setAnswerLanguage(df.get("answerLanguage"));
        exam.setState(df.get("state"));
        exam.setTotalScore(Double.parseDouble(df.get("totalScore")));
        if (df.get("customCredit") != null) {
            exam.setCustomCredit(Double.parseDouble(df.get("customCredit")));
        } else {
            exam.setCustomCredit(null);
        }
        // set user only if exam is really graded, not just modified
        if (exam.getState().equals(Exam.State.GRADED.name()) || exam.getState().equals(Exam.State.GRADED_LOGGED.name())) {
            exam.setGradedTime(new Date());
            exam.setGradedByUser(UserController.getLoggedUser());
        }
        exam.generateHash();
        exam.update();

        return ok();
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getExamReviews(Long eid, List<String> statuses) {
        // Assure that ongoing exams will not be returned
        statuses.remove(Exam.State.STUDENT_STARTED.toString());
        // Todo: Assume that exam creator is also exam inspector
        List<ExamParticipation> participations = Ebean.find(ExamParticipation.class)
                .fetch("user")
                .fetch("exam")
                .where()
                .eq("exam.parent.id", eid)
                .in("exam.state", statuses)
                .findList();

        if (participations == null) {
            return notFound();
        } else {
            JsonContext jsonContext = Ebean.createJsonContext();
            JsonWriteOptions options = new JsonWriteOptions();
            options.setRootPathProperties("user, exam, started, ended, duration, deadline");
            options.setPathProperties("user", "id, firstName, lastName, email");
            options.setPathProperties("exam", "id, name, course, state, grade, gradedTime, customCredit");
            options.setPathProperties("exam.course", "code, credits");

            return ok(jsonContext.toJsonString(participations, true, options)).as("application/json");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getExamStudentInfo(Long eid) {

        ExamParticipation participation = Ebean.find(ExamParticipation.class)
                .fetch("user")
                .where()
                .eq("exam.id", eid)
                .findUnique();

        if (participation == null) {
            return notFound();
        } else {
            JsonContext jsonContext = Ebean.createJsonContext();
            JsonWriteOptions options = new JsonWriteOptions();
            options.setRootPathProperties("user, started, ended, duration");

            // Todo: tähän mahd paljon infoa opiskelijasta, HAKAsta jne
            options.setPathProperties("user", "id, firstName, lastName, email");

            return ok(jsonContext.toJsonString(participation, true, options)).as("application/json");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result insertComment(Long eid, Long cid) throws MalformedDataException {

        Comment comment = bindForm(Comment.class);
        comment.save();

        Exam exam = Ebean.find(Exam.class, eid);
        exam.setExamFeedback(comment);
        exam.save();

        JsonContext jsonContext = Ebean.createJsonContext();
        JsonWriteOptions options = new JsonWriteOptions();
        options.setRootPathProperties("id, comment, creator");
        options.setPathProperties("creator", "id, firstName, lastName");

        return ok(jsonContext.toJsonString(comment, true, options)).as("application/json");
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result updateComment(Long eid, Long cid) throws MalformedDataException {

        Comment bindComment = bindForm(Comment.class);

        Comment comment = Ebean.find(Comment.class, cid);
        if (comment == null) {
            return notFound();
        }

        SitnetUtil.setCreator(comment);
        if(bindComment.getComment() != null) {
            comment.setComment(bindComment.getComment());
        } else {
            comment.setComment("");
        }
        comment.save();

        Exam exam = Ebean.find(Exam.class, eid);

        exam.setExamFeedback(comment);
        exam.save();

        JsonContext jsonContext = Ebean.createJsonContext();
        JsonWriteOptions options = new JsonWriteOptions();
        options.setRootPathProperties("id, comment, creator");
        options.setPathProperties("creator", "id, firstName, lastName");

        return ok(jsonContext.toJsonString(comment, true, options)).as("application/json");
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result updateExam(Long id) {

        DynamicForm df = Form.form().bindFromRequest();

        Exam exam = Ebean.find(Exam.class)
                .fetch("course")
                .fetch("course.organisation")
                .fetch("examSections")
                .fetch("examSections.sectionQuestions")
                .fetch("examSections.sectionQuestions.question")
                .fetch("softwares")
                .fetch("examLanguages")
                .fetch("attachment")
                .where()
                .eq("id", id)
                .orderBy("examSections.id, examSections.sectionQuestions.sequenceNumber")
                .findUnique();

        if (exam == null) {
            return notFound();
        } else if (SitnetUtil.isOwner(exam) || UserController.getLoggedUser().hasRole("ADMIN")) {

            String str = ValidationUtil.validateExamForm(df);
            if (!str.equalsIgnoreCase("OK")) {
                return badRequest(str);
            }

            String examName = df.get("name");
            boolean shared = Boolean.parseBoolean(df.get("shared"));
            String duration = df.get("duration");
            String grading = df.get("grading");
            String answerLanguage = df.get("answerLanguage");
            String instruction = df.get("instruction");
            String enrollInstruction = df.get("enrollInstruction");
            String state = df.get("state");
            boolean expanded = Boolean.parseBoolean(df.get("expanded"));

            if (examName != null) {
                exam.setName(examName);
            }

            if (state != null) {
                exam.setState(state);
            }

            if (shared) {
                exam.setShared(shared);
            }

            Long start = new Long(df.get("examActiveStartDate"));
            Long end = new Long(df.get("examActiveEndDate"));

            if (start != 0) {
                exam.setExamActiveStartDate(new Date(start));
            }
            if (end != 0) {
                exam.setExamActiveEndDate(new Date(end));
            }

            if (duration != null) {
                exam.setDuration(Integer.valueOf(duration));
            }

            if (grading != null) {
                exam.setGrading(grading);
            }

            if (answerLanguage != null) {
                exam.setAnswerLanguage(answerLanguage);
            }

            if (instruction != null) {
                exam.setInstruction(instruction);
            }

            if (enrollInstruction != null) {
                exam.setEnrollInstruction(enrollInstruction);
            }

            if (df.get("course.credits") != null) {
                Double credits = new Double(df.get("course.credits"));

                // TODO: this is not right, we cant set credits to Course,
                // TODO: move this to Exam
                exam.getCourse().setCredits(credits);
                exam.getCourse().save();
            }

            if (df.get("examType.type") != null) {
                String examType = df.get("examType.type");

                ExamType eType = Ebean.find(ExamType.class)
                        .where()
                        .eq("type", examType)
                        .findUnique();

                if (eType == null) {
                    ExamType newType = new ExamType(examType);
                    newType.save();
                    exam.setExamType(newType);
                } else {
                    exam.setExamType(eType);
                }
            }
            exam.generateHash();

            if (df.get("expanded") != null) {
                exam.setExpanded(expanded);
            }

            exam.save();

            JsonContext jsonContext = Ebean.createJsonContext();
            return ok(jsonContext.toJsonString(exam, true, getJsonOptions())).as("application/json");
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result resetExamSoftwareInfo(Long eid) {
        Exam exam = Ebean.find(Exam.class, eid);

        exam.getSoftwareInfo().clear();
        exam.update();

        return ok(Json.toJson(exam));
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result resetExamLanguages(Long eid) {
        Exam exam = Ebean.find(Exam.class, eid);

        exam.getExamLanguages().clear();
        exam.update();

        return ok(Json.toJson(exam));
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result updateExamSoftwareInfo(Long eid, Long sid) {
        Exam exam = Ebean.find(Exam.class, eid);
        Software software = Ebean.find(Software.class, sid);

        exam.getSoftwareInfo().add(software);
        exam.update();

        return ok(Json.toJson(exam));
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result addExamLanguage(Long eid, String code) {
        Exam exam = Ebean.find(Exam.class, eid);
        Language language = Ebean.find(Language.class, code);

        exam.getExamLanguages().add(language);
        exam.update();

        return ok(Json.toJson(exam));
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result createExamDraft() {

        Exam exam = new Exam();
        exam.setName("Kirjoita tentin nimi tähän");
        exam.setState(Exam.State.DRAFT.toString());
        SitnetUtil.setCreator(exam);
        exam.save();

        ExamSection examSection = new ExamSection();
        examSection.setName("Aihealue");
        SitnetUtil.setCreator(examSection);

        examSection.setExam(exam);
        examSection.setExpanded(true);
        examSection.save();

        exam.getExamSections().add(examSection);
        exam.getExamLanguages().add(Ebean.find(Language.class, "fi")); // Finnish
        exam.setExamType(Ebean.find(ExamType.class, 2)); // Final
        exam.save();

        // add creator to inspector list (SITNET-178)
        ExamInspection inspection = new ExamInspection();
        inspection.setExam(exam);
        inspection.setUser(UserController.getLoggedUser());
        inspection.save();

        exam.setExpanded(true);
        exam.save();

        // return only id, its all we need at this point
        ObjectNode part = Json.newObject();
        part.put("id", exam.getId());

        return ok(Json.toJson(part));
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result insertSection(Long id) throws MalformedDataException {

        Exam exam = Ebean.find(Exam.class, id);
        if (SitnetUtil.isOwner(exam) || UserController.getLoggedUser().hasRole("ADMIN")) {

            ExamSection section = bindForm(ExamSection.class);
            section.setExam(Ebean.find(Exam.class, id));
            SitnetUtil.setCreator(section);
            section.save();
            return ok(Json.toJson(section));
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result updateCourse(Long eid, Long cid) {

        Exam exam = Ebean.find(Exam.class, eid);
        if (SitnetUtil.isOwner(exam) || UserController.getLoggedUser().hasRole("ADMIN")) {
            Course course = Ebean.find(Course.class, cid);
            exam.setCourse(course);
            exam.save();
            return ok(Json.toJson(exam));
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result removeExamCourse(Long eid) {

        Exam exam = Ebean.find(Exam.class, eid);
        if (SitnetUtil.isOwner(exam) || UserController.getLoggedUser().hasRole("ADMIN")) {
            exam.setCourse(null);
            exam.save();
            return ok(Json.toJson(exam));
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }

    private static AbstractQuestion clone(String type, Long id) {
        switch (type) {
            case "MultipleChoiceQuestion":
                MultipleChoiceQuestion choice = Ebean.find(MultipleChoiceQuestion.class, id).copy();
                choice.setCreator(UserController.getLoggedUser());
                choice.setCreated(new Date());
                SitnetUtil.setModifier(choice);
                choice.save();
                Ebean.save(choice.getOptions());
                return choice;
            case "EssayQuestion":
                EssayQuestion essay = Ebean.find(EssayQuestion.class, id).copy();
                SitnetUtil.setModifier(essay);
                essay.save();
                return essay;
            default:
                return null;
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result reorderQuestion(Long eid, Long sid, Integer from, Integer to) {
        Exam exam = Ebean.find(Exam.class, eid);
        if (SitnetUtil.isOwner(exam) || UserController.getLoggedUser().hasRole("ADMIN")) {
            ExamSection section = Ebean.find(ExamSection.class, sid);
            if (from == to) {
                return ok();
            }
            for (ExamSectionQuestion esq : section.getSectionQuestions()) {
                int seq = esq.getSequenceNumber();
                if (seq == from) {
                    esq.setSequenceNumber(to);
                    esq.update();
                } else {
                    if (from > to) {
                        if (seq <= from && seq >= to) {
                            esq.setSequenceNumber(seq + 1);
                            esq.update();
                        }
                    } else {
                        if (seq >= from && seq <= to) {
                            esq.setSequenceNumber(seq - 1);
                            esq.update();
                        }
                    }
                }
            }
            return ok();
        }
        return forbidden("sitnet_error_access_forbidden");
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result insertQuestion(Long eid, Long sid, Integer seq, Long qid) {
        Exam exam = Ebean.find(Exam.class, eid);
        if (SitnetUtil.isOwner(exam) || UserController.getLoggedUser().hasRole("ADMIN")) {
            AbstractQuestion question = Ebean.find(AbstractQuestion.class, qid);

            ExamSection section = Ebean.find(ExamSection.class, sid);
            AbstractQuestion clone = clone(question.getType(), question.getId());
            if (clone == null) {
                return notFound("Question type not specified");
            }

            // Assert that the sequence number provided is within limits
            if (seq < 0) {
                seq = 0;
            }
            if (seq > section.getSectionQuestions().size()) {
                seq = section.getSectionQuestions().size();
            }

            // Increase sequences for the entries above the inserted one
            for (ExamSectionQuestion esq : section.getSectionQuestions()) {
                int sequenceNumber = esq.getSequenceNumber();
                if (sequenceNumber >= seq) {
                    esq.setSequenceNumber(sequenceNumber + 1);
                    esq.update();
                }
            }
            // Insert new section question
            ExamSectionQuestion sectionQuestion = new ExamSectionQuestion();
            sectionQuestion.setExamSection(section);
            sectionQuestion.setQuestion(clone);
            sectionQuestion.setSequenceNumber(seq);
            section.getSectionQuestions().add(sectionQuestion);
            SitnetUtil.setModifier(section);
            section.save();
            return ok(Json.toJson(section));
        }
        return forbidden("sitnet_error_access_forbidden");
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result removeQuestion(Long eid, Long sid, Long qid) {
        Exam exam = Ebean.find(Exam.class, eid);
        if (SitnetUtil.isOwner(exam) || UserController.getLoggedUser().hasRole("ADMIN")) {
            AbstractQuestion question = Ebean.find(AbstractQuestion.class, qid);
            ExamSection section = Ebean.find(ExamSection.class)
                    .fetch("sectionQuestions")
                    .where()
                    .eq("id", sid)
                    .findUnique();
            ExamSectionQuestion sectionQuestion = Ebean.find(ExamSectionQuestion.class).where().eq("question",
                    question).eq("examSection", section).findUnique();
            if (sectionQuestion == null) {
                return notFound("sitnet_error_not_found");
            }
            // Detach possible student exam questions from this one
            List<AbstractQuestion> children = Ebean.find(AbstractQuestion.class)
                    .where()
                    .eq("parent.id", sectionQuestion.getQuestion().getId())
                    .findList();
            for (AbstractQuestion child : children) {
                child.setParent(null);
                child.save();
            }
            sectionQuestion.delete();
            section.getSectionQuestions().remove(sectionQuestion);

            // Decrease sequences for the entries above the inserted one
            int seq = sectionQuestion.getSequenceNumber();
            for (ExamSectionQuestion esq : section.getSectionQuestions()) {
                int num = esq.getSequenceNumber();
                if (num >= seq) {
                    esq.setSequenceNumber(num - 1);
                    esq.update();
                }
            }
            section.save();
            return ok(Json.toJson(section));
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result clearQuestions(Long sid) {
        ExamSection section = Ebean.find(ExamSection.class, sid);

        if (SitnetUtil.isOwner(section) || UserController.getLoggedUser().hasRole("ADMIN")) {
            for (ExamSectionQuestion esq : section.getSectionQuestions()) {
                esq.delete();
            }
            section.getSectionQuestions().clear();
            section.save();
            return ok(Json.toJson(section));
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result removeSection(Long eid, Long sid) {
        Exam exam = Ebean.find(Exam.class, eid);
        if (SitnetUtil.isOwner(exam) || UserController.getLoggedUser().hasRole("ADMIN")) {
            ExamSection section = Ebean.find(ExamSection.class, sid);
            exam.getExamSections().remove(section);
            exam.save();
            section.delete();
            return ok();
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result updateSection(Long eid, Long sid) {

        // TODO: should check is user is owner ?
        ExamSection section = Form.form(ExamSection.class).bindFromRequest(
                "id",
                "name",
                "expanded",
                "lotteryOn",
                "lotteryItemCount"
        ).get();

        ExamSection sectionToUpdate = Ebean.find(ExamSection.class, sid);
        sectionToUpdate.setName(section.getName());
        sectionToUpdate.setExpanded(section.getExpanded());
        sectionToUpdate.setLotteryOn(section.getLotteryOn());
        sectionToUpdate.setLotteryItemCount(section.getLotteryItemCount());
        sectionToUpdate.update();

        return ok(Json.toJson(sectionToUpdate));
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getExamSections(Long examid) {
        Exam exam = Ebean.find(Exam.class, examid);
        if (SitnetUtil.isOwner(exam) || UserController.getLoggedUser().hasRole("ADMIN")) {
            List<ExamSection> sections = Ebean.find(ExamSection.class).where()
                    .eq("id", examid)
                    .findList();

            return ok(Json.toJson(sections));
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result deleteSection(Long sectionId) {

        Exam exam = Ebean.find(Exam.class)
                .where()
                .eq("examSections.id", sectionId)
                .findUnique();

        if (SitnetUtil.isOwner(exam) || UserController.getLoggedUser().hasRole("ADMIN")) {

            ExamSection section = Ebean.find(ExamSection.class, sectionId);
            exam.getExamSections().remove(section);
            exam.save();
            section.delete();

            return ok("removed");
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getEnrolments() {
        List<ExamEnrolment> enrolments = Ebean.find(ExamEnrolment.class).findList();

        if (enrolments == null) {
            return notFound();
        } else {
            JsonContext jsonContext = Ebean.createJsonContext();
            JsonWriteOptions options = new JsonWriteOptions();
            options.setRootPathProperties("id, enrolledOn, user, exam");
            options.setPathProperties("user", "id");
            options.setPathProperties("exam", "id");

            return ok(jsonContext.toJsonString(enrolments, true, options)).as("application/json");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getEnrolmentsForUser(Long uid) {
        List<ExamEnrolment> enrolments = Ebean.find(ExamEnrolment.class)
                .fetch("exam")
                .fetch("reservation")
                .where()
                .eq("user.id", uid)
                .findList();

        if (enrolments == null) {
            return notFound();
        } else {
            JsonContext jsonContext = Ebean.createJsonContext();
            JsonWriteOptions options = new JsonWriteOptions();
            options.setRootPathProperties("id, enrolledOn, user, exam, reservation");
            options.setPathProperties("user", "id");
            options.setPathProperties("exam", "id, name, course");
            options.setPathProperties("exam.course", "code");
            options.setPathProperties("reservation", "startAt, machine");
            options.setPathProperties("reservation.machine", "name");

            return ok(jsonContext.toJsonString(enrolments, true, options)).as("application/json");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN"), @Group("STUDENT")})
    public static Result getRoomInfoFromEnrollment(Long eid) {
        ExamEnrolment enrollment = Ebean.find(ExamEnrolment.class)
                .fetch("user")
                .fetch("user.userLanguage")
                .fetch("reservation")
                .fetch("reservation.machine")
                .fetch("reservation.machine.room")
                .where()
                .eq("exam.id", eid)
                .findUnique();

        if (enrollment == null) {
            return notFound();
        } else {
            JsonContext jsonContext = Ebean.createJsonContext();
            JsonWriteOptions options = new JsonWriteOptions();
            options.setRootPathProperties("id, user, reservation");
            options.setPathProperties("user", "id, userLanguage");
            options.setPathProperties("user.userLanguage", "nativeLanguageCode, UILanguageCode");
            options.setPathProperties("reservation", "machine");
            options.setPathProperties("reservation.machine", "room");
            options.setPathProperties("reservation.machine.room", "roomInstruction, roomInstructionEN, roomInstructionSV");

            return ok(jsonContext.toJsonString(enrollment, true, options)).as("application/json");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getParticipationsForExamAndUser(Long eid, Long uid) {
        List<ExamParticipation> participations = Ebean.find(ExamParticipation.class)
                .fetch("exam")
                .fetch("exam.parent")
                .where()
                .eq("user.id", uid)
                .eq("exam.parent.id", eid)
                .disjunction()
                .eq("exam.state", Exam.State.ABORTED.toString())
                .eq("exam.state", Exam.State.GRADED.toString())
                .eq("exam.state", Exam.State.GRADED_LOGGED.toString())
                .eq("exam.state", Exam.State.ARCHIVED.toString())
                .endJunction()
                .findList();
        JsonContext context = Ebean.createJsonContext();
        JsonWriteOptions options = new JsonWriteOptions();
        options.setRootPathProperties("started, exam");
        options.setPathProperties("exam", "id, grade, state");
        return ok(context.toJsonString(participations, true, options)).as("application/json");
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN"), @Group("STUDENT")})
    public static Result getEnrolmentsForExam(Long eid) {
        List<ExamEnrolment> enrolments = Ebean.find(ExamEnrolment.class)
                .fetch("exam")
                .fetch("exam.parent")
                .fetch("reservation")
                .where()
                .eq("exam.id", eid)             // Just enrolled, not started
//                .eq("exam.parent.id", eid)    // Exams that have been started by student
                .findList();

        if (enrolments == null) {
            return notFound();
        } else {
            JsonContext jsonContext = Ebean.createJsonContext();
            JsonWriteOptions options = new JsonWriteOptions();
            options.setRootPathProperties("id, enrolledOn, user, exam, reservation");
            options.setPathProperties("user", "id");
            options.setPathProperties("exam", "id, name, course");
            options.setPathProperties("exam.course", "code");
            options.setPathProperties("reservation", "startAt, machine");
            options.setPathProperties("reservation.machine", "name");

            return ok(jsonContext.toJsonString(enrolments, true, options)).as("application/json");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getReservationInformationForExam(Long eid) {
        ExamEnrolment enrolment = Ebean.find(ExamEnrolment.class)
                .where()
                .eq("exam.id", eid)
                .findUnique();
        if (enrolment == null || enrolment.getReservation() == null) {
            return notFound();
        }
        ExamMachine machine = enrolment.getReservation().getMachine();
        JsonContext jsonContext = Ebean.createJsonContext();
        JsonWriteOptions options = new JsonWriteOptions();
        options.setRootPathProperties("name, otherIdentifier, room");
        options.setPathProperties("room", "name, roomCode, buildingName, campus");
        return ok(jsonContext.toJsonString(machine, true, options)).as("application/json");
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN"), @Group("STUDENT")})
    public static Result getEnrolmentsWithReservationsForExam(Long eid) {
        List<ExamEnrolment> enrolments = Ebean.find(ExamEnrolment.class)
                .fetch("exam")
                .fetch("exam.parent")
                .fetch("reservation")
                .where()
                .ne("reservation", null)             // Just enrolled, not started
                .eq("exam.id", eid)             // Just enrolled, not started
//                .eq("exam.parent.id", eid)    // Exams that have been started by student
                .findList();

        if (enrolments == null) {
            return notFound();
        } else {
            JsonContext jsonContext = Ebean.createJsonContext();
            JsonWriteOptions options = new JsonWriteOptions();
            options.setRootPathProperties("id, enrolledOn, user, exam, reservation");
            options.setPathProperties("user", "id");
            options.setPathProperties("exam", "id, name, course");
            options.setPathProperties("exam.course", "code");
            options.setPathProperties("reservation", "startAt, machine");
            options.setPathProperties("reservation.machine", "name");

            return ok(jsonContext.toJsonString(enrolments, true, options)).as("application/json");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getParticipationsForExam(Long eid) {

        List<ExamParticipation> participations = Ebean.find(ExamParticipation.class)
                .fetch("exam")
                .fetch("exam.parent")
                .where()
                .eq("exam.parent.id", eid)
                .disjunction()
                .eq("exam.state", "ABORTED")
                .eq("exam.state", "REVIEW")
                .eq("exam.state", "REVIEW_STARTED")
                .eq("exam.state", "GRADED")
                .eq("exam.state", "GRADED_LOGGED")
                .endJunction()
                .findList();

        if (participations == null) {
            return notFound();
        } else {
            JsonContext jsonContext = Ebean.createJsonContext();
            JsonWriteOptions options = new JsonWriteOptions();
            options.setRootPathProperties("id, user, exam, started, ended");
            options.setPathProperties("user", "id");
            options.setPathProperties("exam", "id, name, course");
            options.setPathProperties("exam.course", "code");

            return ok(jsonContext.toJsonString(participations, true, options)).as("application/json");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getParticipationsAndReviewedForExam(Long eid) {
        List<ExamParticipation> participations = Ebean.find(ExamParticipation.class)
                .fetch("exam")
                .fetch("exam.parent")
                .where()
                .eq("exam.parent.id", eid)
                .or(
                        Expr.eq("exam.state", "GRADED"),
                        Expr.eq("exam.state", "GRADED_LOGGED")
                )
                .findList();

        if (participations == null) {
            return notFound();
        } else {
            JsonContext jsonContext = Ebean.createJsonContext();
            JsonWriteOptions options = new JsonWriteOptions();
            options.setRootPathProperties("id, user, exam, started, ended");
            options.setPathProperties("user", "id, firstName, lastName");
            options.setPathProperties("exam", "id, name, course");
            options.setPathProperties("exam.course", "code");

            return ok(jsonContext.toJsonString(participations, true, options)).as("application/json");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getExamInspections(Long id) {

        List<ExamInspection> inspections = Ebean.find(ExamInspection.class)
                .fetch("user")
                .fetch("exam")
                .where()
                .eq("exam.id", id)
                .findList();

        if (inspections == null) {
            return notFound();
        } else {
            JsonContext jsonContext = Ebean.createJsonContext();
            JsonWriteOptions options = new JsonWriteOptions();
            options.setRootPathProperties("id, user, exam, ready");
            options.setPathProperties("user", "id, email, firstName, lastName, roles, userLanguage");
            options.setPathProperties("exam", "id");

            return ok(jsonContext.toJsonString(inspections, true, options)).as("application/json");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result getInspections() {
        List<ExamInspection> inspections = Ebean.find(ExamInspection.class)
                .fetch("exam", "name", new FetchConfig().query())
                .findList();

        if (inspections == null) {
            return notFound();
        } else {
            JsonContext jsonContext = Ebean.createJsonContext();
            JsonWriteOptions options = new JsonWriteOptions();
            options.setRootPathProperties("id, user, exam");
            options.setPathProperties("user", "id");
            options.setPathProperties("exam", "id");

            return ok(jsonContext.toJsonString(inspections, true, options)).as("application/json");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result insertInspection(Long eid, Long uid) throws SitnetException {

        final ExamInspection inspection = bindForm(ExamInspection.class);
        final User recipient = Ebean.find(User.class, uid);
        final Exam exam = Ebean.find(Exam.class, eid);

        if (exam.getParent() == null) {
            inspection.setExam(exam);
        } else {
            inspection.setExam(exam.getParent());
        }
        inspection.setUser(recipient);
        inspection.setAssignedBy(UserController.getLoggedUser());

        inspection.setComment((Comment) SitnetUtil.setCreator(inspection.getComment()));
        inspection.getComment().save();
        inspection.save();

        Exam source = exam.getParent() == null ? exam : exam.getParent();

        // SITNET-295
        try {
            EmailComposer.composeExamReviewedRequest(recipient, UserController.getLoggedUser(), source,
                    inspection.getComment().getComment());
        } catch (IOException e) {
            Logger.error("Failure to access message template on disk", e);
            e.printStackTrace();
        }

        return ok(Json.toJson(inspection));
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result deleteInspection(Long id) {

        Ebean.delete(ExamInspection.class, id);

        return ok();
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result updateInspection(Long id, boolean ready) {

        ExamInspection inspection = Ebean.find(ExamInspection.class, id);

        if(inspection == null) {
            return notFound();
        }
        inspection.setReady(ready);
        inspection.update();

        return ok();
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result sendInspectionMessage(Long eid, String msg) {

        final Exam exam = Ebean.find(Exam.class, eid);
        List<ExamInspection> inspections = Ebean.find(ExamInspection.class)
                .fetch("user")
                .fetch("exam")
                .where()
                .eq("exam.id", exam.getParent().getId())
                .findList();

        if (CollectionUtils.isEmpty(inspections)) {
            return notFound();
        }

        try {
            for (ExamInspection inspection : inspections) {
                EmailComposer.composeInspectionMessage(inspection.getUser(), UserController.getLoggedUser(), exam, msg);
            }
        } catch (IOException e) {
            Logger.error("Failure to access message template on disk", e);
            return internalServerError("sitnet_internal_error");
        }
        if (exam == null) {
            return notFound();
        }

        return ok();
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public static Result insertLocalInspectionWithoutCommentAndEmail(Long eid, Long uid) {

        ExamInspection inspection = new ExamInspection();
        final User recipient = Ebean.find(User.class, uid);
        final Exam exam = Ebean.find(Exam.class, eid);

        if(recipient == null || exam == null) {
            return notFound();
        }
        inspection.setExam(exam);
        inspection.setUser(recipient);
        inspection.setAssignedBy(UserController.getLoggedUser());

        inspection.save();

        return ok(Json.toJson(inspection));
    }

}
