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

import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import io.ebean.Ebean;
import io.ebean.ExpressionList;
import io.ebean.text.PathProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Exam;
import models.ExamSection;
import models.ExamSectionQuestion;
import models.ExamSectionQuestionOption;
import models.User;
import models.api.Sortable;
import models.questions.MultipleChoiceOption;
import models.questions.Question;
import org.joda.time.DateTime;
import play.data.DynamicForm;
import play.db.ebean.Transactional;
import play.libs.Json;
import play.mvc.Result;
import sanitizers.SanitizingHelper;
import util.AppUtil;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


public class ExamSectionController extends QuestionController {

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public Result insertSection(Long id) {
        Exam exam = Ebean.find(Exam.class, id);
        if (exam == null) {
            return notFound();
        }
        User user = getLoggedUser();
        if (exam.isOwnedOrCreatedBy(user) || user.hasRole("ADMIN", getSession())) {
            ExamSection section = new ExamSection();
            section.setLotteryItemCount(1);
            section.setExam(exam);
            section.setSectionQuestions(Collections.emptySet());
            section.setSequenceNumber(exam.getExamSections().size());
            section.setExpanded(true);
            AppUtil.setCreator(section, user);
            section.save();
            return ok(section, PathProperties.parse("(*, sectionQuestions(*))"));
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public Result removeSection(Long eid, Long sid) {
        ExamSection section = Ebean.find(ExamSection.class)
                .fetch("exam.examOwners")
                .where()
                .eq("exam.id", eid)
                .idEq(sid)
                .findUnique();
        if (section == null) {
            return notFound("sitnet_error_not_found");
        }
        Exam exam = section.getExam();

        User user = getLoggedUser();
        if (exam.isOwnedOrCreatedBy(user) || user.hasRole("ADMIN", getSession())) {
            exam.getExamSections().remove(section);
            exam.update();
            // clear parent id from children
            for (ExamSectionQuestion examSectionQuestion : section.getSectionQuestions()) {
                for (Question question : examSectionQuestion.getQuestion().getChildren()) {
                    question.setParent(null);
                    question.update();
                }
            }
            // Decrease sequences for the entries above the inserted one
            int seq = section.getSequenceNumber();
            for (ExamSection es : exam.getExamSections()) {
                int num = es.getSequenceNumber();
                if (num >= seq) {
                    es.setSequenceNumber(num - 1);
                    es.update();
                }
            }
            return ok();
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public Result updateSection(Long eid, Long sid) {
        ExamSection section = Ebean.find(ExamSection.class)
                .fetch("exam.examOwners")
                .where()
                .eq("exam.id", eid)
                .idEq(sid)
                .findUnique();
        if (section == null) {
            return notFound("sitnet_error_not_found");
        }
        User user = getLoggedUser();
        if (!section.getExam().isOwnedOrCreatedBy(user) && !user.hasRole("ADMIN", getSession())) {
            return forbidden("sitnet_error_access_forbidden");
        }

        ExamSection form = formFactory.form(ExamSection.class).bindFromRequest(
                "id",
                "name",
                "expanded",
                "lotteryOn",
                "lotteryItemCount",
                "description"
        ).get();

        section.setName(form.getName());
        section.setExpanded(form.getExpanded());
        section.setLotteryOn(form.getLotteryOn());
        section.setLotteryItemCount(Math.max(1, form.getLotteryItemCount()));
        section.setDescription(form.getDescription());

        section.update();

        return ok(section);
    }


    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public Result reorderSections(Long eid) {
        DynamicForm df = formFactory.form().bindFromRequest();
        Integer from = Integer.parseInt(df.get("from"));
        Integer to = Integer.parseInt(df.get("to"));
        return checkBounds(from, to).orElseGet(() -> {
            Exam exam = Ebean.find(Exam.class).fetch("examSections").where().idEq(eid).findUnique();
            if (exam == null) {
                return notFound("sitnet_error_exam_not_found");
            }
            User user = getLoggedUser();
            if (exam.isOwnedOrCreatedBy(user) || user.hasRole("ADMIN", getSession())) {
                // Reorder by sequenceNumber (TreeSet orders the collection based on it)
                List<ExamSection> sections = new ArrayList<>(new TreeSet<>(exam.getExamSections()));
                ExamSection prev = sections.get(from);
                boolean removed = sections.remove(prev);
                if (removed) {
                    sections.add(to, prev);
                    for (int i = 0; i < sections.size(); ++i) {
                        ExamSection section = sections.get(i);
                        section.setSequenceNumber(i);
                        section.update();
                    }
                }
                return ok();
            }
            return forbidden("sitnet_error_access_forbidden");
        });
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public Result reorderSectionQuestions(Long eid, Long sid) {
        DynamicForm df = formFactory.form().bindFromRequest();
        Integer from = Integer.parseInt(df.get("from"));
        Integer to = Integer.parseInt(df.get("to"));
        return checkBounds(from, to).orElseGet(() -> {
            Exam exam = Ebean.find(Exam.class, eid);
            if (exam == null) {
                return notFound("sitnet_error_exam_not_found");
            }
            User user = getLoggedUser();
            if (exam.isOwnedOrCreatedBy(user) || user.hasRole("ADMIN", getSession())) {
                ExamSection section = Ebean.find(ExamSection.class, sid);
                if (section == null) {
                    return notFound("section not found");
                }
                // Reorder by sequenceNumber (TreeSet orders the collection based on it)
                List<ExamSectionQuestion> questions = new ArrayList<>(new TreeSet<>(section.getSectionQuestions()));
                ExamSectionQuestion prev = questions.get(from);
                boolean removed = questions.remove(prev);
                if (removed) {
                    questions.add(to, prev);
                    for (int i = 0; i < questions.size(); ++i) {
                        ExamSectionQuestion question = questions.get(i);
                        question.setSequenceNumber(i);
                        question.update();
                    }
                }
                return ok();
            }
            return forbidden("sitnet_error_access_forbidden");
        });

    }

    private void updateSequences(Collection<? extends Sortable> sortables, int ordinal) {
        // Increase sequences for the entries above the inserted one
        for (Sortable s : sortables) {
            int sequenceNumber = s.getOrdinal();
            if (sequenceNumber >= ordinal) {
                s.setOrdinal(sequenceNumber + 1);
            }
        }
    }

    private void updateOptions(ExamSectionQuestion sectionQuestion, Question question) {
        sectionQuestion.getOptions().clear();
        for (MultipleChoiceOption option : question.getOptions()) {
            ExamSectionQuestionOption esqo = new ExamSectionQuestionOption();
            esqo.setOption(option);
            esqo.setScore(option.getDefaultScore());
            sectionQuestion.getOptions().add(esqo);
        }
    }

    private void updateExamQuestion(ExamSectionQuestion sectionQuestion, Question question) {
        sectionQuestion.setQuestion(question);
        sectionQuestion.setMaxScore(question.getDefaultMaxScore());
        sectionQuestion.setAnswerInstructions(question.getDefaultAnswerInstructions());
        sectionQuestion.setEvaluationCriteria(question.getDefaultEvaluationCriteria());
        sectionQuestion.setEvaluationType(question.getDefaultEvaluationType());
        sectionQuestion.setExpectedWordCount(question.getDefaultExpectedWordCount());
        updateOptions(sectionQuestion, question);
    }

    private void updateExamQuestion(ExamSectionQuestion sectionQuestion, JsonNode body) {
        sectionQuestion.setMaxScore(round(SanitizingHelper.parse("maxScore", body, Double.class).orElse(null)));
        sectionQuestion.setAnswerInstructions(
                SanitizingHelper.parse("answerInstructions", body, String.class).orElse(null));
        sectionQuestion.setEvaluationCriteria(
                SanitizingHelper.parse("evaluationCriteria", body, String.class).orElse(null));
        sectionQuestion.setEvaluationType(
                SanitizingHelper.parseEnum("evaluationType", body, Question.EvaluationType.class).orElse(null));
        sectionQuestion.setExpectedWordCount(
                SanitizingHelper.parse("expectedWordCount", body, Integer.class).orElse(null));
    }

    private Optional<Result> insertQuestion(Exam exam, ExamSection section, Question question, User user, Integer seq) {
        ExamSectionQuestion sectionQuestion = new ExamSectionQuestion();
        sectionQuestion.setExamSection(section);
        sectionQuestion.setQuestion(question);
        // Assert that the sequence number provided is within limits
        Integer sequence = Math.min(Math.max(0, seq), section.getSectionQuestions().size());
        updateSequences(section.getSectionQuestions(), sequence);
        sectionQuestion.setSequenceNumber(sequence);
        if (section.getSectionQuestions().contains(sectionQuestion) || section.hasQuestion(question)) {
            return Optional.of(badRequest("sitnet_question_already_in_section"));
        }
        if (question.getType().equals(Question.Type.EssayQuestion)) {
            // disable auto evaluation for this exam
            if (exam.getAutoEvaluationConfig() != null) {
                exam.getAutoEvaluationConfig().delete();
            }
        }

        Ebean.updateAll(section.getSectionQuestions());

        // Insert new section question
        sectionQuestion.setCreator(user);
        sectionQuestion.setCreated(DateTime.now());
        sectionQuestion.setExamSection(section);

        updateExamQuestion(sectionQuestion, question);

        section.getSectionQuestions().add(sectionQuestion);

        AppUtil.setModifier(section, user);
        section.save();
        section.setSectionQuestions(new TreeSet<>(section.getSectionQuestions()));
        return Optional.empty();
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public Result insertQuestion(Long eid, Long sid, Integer seq, Long qid) {
        Exam exam = Ebean.find(Exam.class, eid);
        ExamSection section = Ebean.find(ExamSection.class, sid);
        Question question = Ebean.find(Question.class, qid);
        if (exam == null || section == null || question == null) {
            return notFound();
        }
        if (exam.getAutoEvaluationConfig() != null && question.getType() == Question.Type.EssayQuestion) {
            return forbidden("sitnet_error_autoevaluation_essay_question");
        }
        User user = getLoggedUser();
        if (!exam.isOwnedOrCreatedBy(user) && !user.hasRole("ADMIN", getSession())) {
            return forbidden("sitnet_error_access_forbidden");
        }
        // TODO: response payload should be trimmed down (use path properties)
        return insertQuestion(exam, section, question, user, seq)
                .orElse(ok(Json.toJson(section)));
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    @Transactional
    public Result insertMultipleQuestions(Long eid, Long sid, Integer seq, String questions) {

        Exam exam = Ebean.find(Exam.class, eid);
        ExamSection section = Ebean.find(ExamSection.class, sid);
        if (exam == null || section == null) {
            return notFound();
        }
        User user = getLoggedUser();
        if (!exam.isOwnedOrCreatedBy(user) && !user.hasRole("ADMIN", getSession())) {
            return forbidden("sitnet_error_access_forbidden");
        }
        int sequence = seq;
        for (String s : questions.split(",")) {
            Question question = Ebean.find(Question.class, Long.parseLong(s));
            if (question == null) {
                continue;
            }
            if (exam.getAutoEvaluationConfig() != null && question.getType() == Question.Type.EssayQuestion) {
                return forbidden("sitnet_error_autoevaluation_essay_question");
            }
            Optional<Result> result = insertQuestion(exam, section, question, user, sequence);
            if (result.isPresent()) {
                return result.get();
            }
            ++sequence;
        }
        return ok(section);
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public Result removeQuestion(Long eid, Long sid, Long qid) {
        User user = getLoggedUser();
        ExamSectionQuestion sectionQuestion = Ebean.find(ExamSectionQuestion.class)
                .fetch("examSection.exam.examOwners")
                .fetch("question")
                .where()
                .eq("examSection.exam.id", eid)
                .eq("examSection.id", sid)
                .eq("question.id", qid)
                .findUnique();
        if (sectionQuestion == null) {
            return notFound("sitnet_error_not_found");
        }
        ExamSection section = sectionQuestion.getExamSection();
        Exam exam = section.getExam();
        if (!exam.isOwnedOrCreatedBy(user) && !user.hasRole("ADMIN", getSession())) {
            return forbidden("sitnet_error_access_forbidden");
        }
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
        section.update();
        return ok(section);
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public Result clearQuestions(Long eid, Long sid) {
        ExamSection section = Ebean.find(ExamSection.class)
                .fetch("exam.creator")
                .fetch("exam.examOwners")
                .fetch("exam.parent.examOwners")
                .where()
                .idEq(sid)
                .eq("exam.id", eid)
                .findUnique();
        if (section == null) {
            return notFound("sitnet_error_not_found");
        }
        User user = getLoggedUser();
        if (section.getExam().isOwnedOrCreatedBy(user) || user.hasRole("ADMIN", getSession())) {
            section.getSectionQuestions().forEach(sq -> {
                sq.getQuestion().getChildren().forEach(c -> {
                    c.setParent(null);
                    c.update();
                });
                sq.delete();
            });
            section.getSectionQuestions().clear();
            section.setLotteryOn(false);
            section.update();
            return ok(section);
        } else {
            return forbidden("sitnet_error_access_forbidden");
        }
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public Result getExamQuestion(Long id) {
        User user = getLoggedUser();
        ExpressionList<ExamSectionQuestion> query = Ebean.find(ExamSectionQuestion.class)
                .fetch("question")
                .fetch("options")
                .fetch("examSection")
                .where().idEq(id);
        if (user.hasRole("TEACHER", getSession())) {
            query = query.eq("examSection.exam.examOwners", user);
        }
        ExamSectionQuestion examQuestion = query.findUnique();
        if (examQuestion == null) {
            return forbidden("sitnet_error_access_forbidden");
        }
        Collections.sort(examQuestion.getQuestion().getOptions());
        return ok(examQuestion);
    }

    private void processExamQuestionOptions(Question question, ExamSectionQuestion esq, ArrayNode node) { // esq.options
        Set<Long> persistedIds = question.getOptions().stream()
                .map(MultipleChoiceOption::getId)
                .collect(Collectors.toSet());
        Set<Long> providedIds = StreamSupport.stream(node.spliterator(), false)
                .map(n -> n.get("option"))
                .filter(n -> SanitizingHelper.parse("id", n, Long.class).isPresent())
                .map(n -> SanitizingHelper.parse("id", n, Long.class).get())
                .collect(Collectors.toSet());
        // Updates
        StreamSupport.stream(node.spliterator(), false)
                .map(n -> n.get("option"))
                .filter(o -> {
                    Optional<Long> id = SanitizingHelper.parse("id", o, Long.class);
                    return id.isPresent() && persistedIds.contains(id.get());
                }).forEach(o -> updateOption(o, true));
        // Removals
        question.getOptions().stream()
                .filter(o -> !providedIds.contains(o.getId()))
                .forEach(this::deleteOption);
        // Additions
        StreamSupport.stream(node.spliterator(), false)
                .filter(o -> SanitizingHelper.parse("id", o, Long.class) == null)
                .forEach(o -> createOptionBasedOnExamQuestion(question, esq, o));
        // Finally update own option scores:
        for (JsonNode option : node) {
            SanitizingHelper.parse("id", option, Long.class).ifPresent(id -> {
                ExamSectionQuestionOption esqo = Ebean.find(ExamSectionQuestionOption.class, id);
                if (esqo != null) {
                    esqo.setScore(round(
                            SanitizingHelper.parse("score", option, Double.class).orElse(null)));
                    esqo.update();
                }
            });
        }
    }

    private boolean hasPositiveOptionScore(ArrayNode an) {
        return StreamSupport.stream(an.spliterator(), false).anyMatch(n -> n.get("score").asDouble() > 0);
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public Result updateDistributedExamQuestion(Long esqId) {
        User user = getLoggedUser();
        ExpressionList<ExamSectionQuestion> query = Ebean.find(ExamSectionQuestion.class).where().idEq(esqId);
        if (user.hasRole("TEACHER", getSession())) {
            query = query.eq("examSection.exam.examOwners", user);
        }
        PathProperties pp = PathProperties.parse("(*, question(*, options(*)), options(*, option(*)))");
        query.apply(pp);
        ExamSectionQuestion examSectionQuestion = query.findUnique();
        if (examSectionQuestion == null) {
            return forbidden("sitnet_error_access_forbidden");
        }
        Question question = Ebean.find(Question.class)
                .fetch("examSectionQuestions")
                .fetch("examSectionQuestions.options")
                .where()
                .idEq(examSectionQuestion.getQuestion().getId())
                .findUnique();
        if (question == null) {
            return notFound();
        }
        JsonNode body = request().body().asJson();
        if (question.getType() == Question.Type.WeightedMultipleChoiceQuestion &&
                !hasPositiveOptionScore((ArrayNode) body.get("options"))) {
            return badRequest("sitnet_correct_option_required");
        }
        // Update question: text
        JsonNode questionNode = body.get("question");
        question.setQuestion(SanitizingHelper.parse("question", questionNode, String.class).orElse(null));
        question.update();
        updateExamQuestion(examSectionQuestion, body);
        examSectionQuestion.update();
        if (question.getType() != Question.Type.EssayQuestion && question.getType() != Question.Type.ClozeTestQuestion) {
            // Process the options, this has an impact on the base question options as well as all the section questions
            // utilizing those.
            processExamQuestionOptions(question, examSectionQuestion, (ArrayNode) body.get("options"));
        }
        // Bit dumb, refetch from database to get the updated options right in response. Could be made more elegantly
        return ok(query.findUnique(), pp);
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public Result updateUndistributedExamQuestion(Long esqId) {
        User user = getLoggedUser();
        ExpressionList<ExamSectionQuestion> query = Ebean.find(ExamSectionQuestion.class).where().idEq(esqId);
        if (user.hasRole("TEACHER", getSession())) {
            query = query.eq("examSection.exam.examOwners", user);
        }
        ExamSectionQuestion examSectionQuestion = query.findUnique();
        if (examSectionQuestion == null) {
            return forbidden("sitnet_error_access_forbidden");
        }
        Question question = Ebean.find(Question.class, examSectionQuestion.getQuestion().getId());
        if (question == null) {
            return notFound();
        }
        updateExamQuestion(examSectionQuestion, question);
        examSectionQuestion.update();
        return ok(examSectionQuestion);
    }

    private Optional<Result> checkBounds(Integer from, Integer to) {
        if (from < 0 || to < 0) {
            return Optional.of(badRequest());
        }
        if (from.equals(to)) {
            return Optional.of(ok());
        }
        return Optional.empty();
    }

    @Restrict({@Group("TEACHER"), @Group("ADMIN")})
    public Result getQuestionDistribution(Long id) {
        ExamSectionQuestion esq = Ebean.find(ExamSectionQuestion.class, id);
        if (esq == null) {
            return notFound();
        }
        Question question = esq.getQuestion();
        // ATM it is enough that question is bound to multiple exams
        Set<Exam> boundExams = question.getExamSectionQuestions().stream()
                .map(eq -> eq.getExamSection().getExam())
                .collect(Collectors.toSet());
        boolean isDistributed = boundExams.size() > 1;
        ObjectNode node = Json.newObject();
        node.put("distributed", isDistributed);
        return ok(Json.toJson(node));
    }

}
