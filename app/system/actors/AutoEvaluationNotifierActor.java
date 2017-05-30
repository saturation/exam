package system.actors;

import akka.actor.UntypedActor;
import com.avaje.ebean.Ebean;
import models.AutoEvaluationConfig;
import models.Exam;
import models.User;
import org.joda.time.DateTime;
import play.Logger;
import util.AppUtil;
import util.java.EmailComposer;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Optional;

public class AutoEvaluationNotifierActor extends UntypedActor {

    private EmailComposer composer;

    @Inject
    public AutoEvaluationNotifierActor(EmailComposer composer) {
        this.composer = composer;
    }

    @Override
    public void onReceive(Object message) throws Exception {
        Logger.debug("{}: Running auto evaluation notification check ...", getClass().getCanonicalName());
        Ebean.find(Exam.class)
                .fetch("autoEvaluationConfig")
                .where()
                .eq("state", Exam.State.GRADED)
                .isNotNull("gradedTime")
                .isNotNull("autoEvaluationConfig")
                .isNotNull("grade")
                .isNotNull("creditType")
                .isNotNull("answerLanguage")
                .isNull("autoEvaluationNotified")
                .findList()
                .stream()
                .filter(this::isPastReleaseDate)
                .forEach(this::notifyStudent);

        Logger.debug("{}: ... Done", getClass().getCanonicalName());
    }

    private DateTime adjustReleaseDate(DateTime date) {
        return AppUtil.adjustDST(date.withHourOfDay(5).withMinuteOfHour(0).withSecondOfMinute(0));
    }

    private boolean isPastReleaseDate(Exam exam) {
        Optional<DateTime> releaseDate;
        AutoEvaluationConfig config = exam.getAutoEvaluationConfig();
        switch (config.getReleaseType()) {
            // Put some delay in these dates to avoid sending stuff in the middle of the night
            case GIVEN_DATE:
                releaseDate = Optional.of(adjustReleaseDate(new DateTime(config.getReleaseDate())));
                break;
            case GIVEN_AMOUNT_DAYS:
                releaseDate = Optional.of(adjustReleaseDate(new DateTime(exam.getGradedTime()).plusDays(config.getAmountDays())));
                break;
            case AFTER_EXAM_PERIOD:
                releaseDate = Optional.of(adjustReleaseDate(new DateTime(exam.getExamActiveEndDate()).plusDays(1)));
                break;
            // Not handled at least by this actor
            case IMMEDIATE:
            case NEVER:
            default:
                releaseDate = Optional.empty();
                break;
        }
        return releaseDate.isPresent() && releaseDate.get().isBeforeNow();
    }

    private void notifyStudent(Exam exam) {
        User student = exam.getCreator();
        try {
            composer.composeInspectionReady(student, null, exam, Collections.emptySet());
            Logger.debug("{}: ... Mail sent to {}", getClass().getCanonicalName(), student.getEmail());
            exam.setAutoEvaluationNotified(DateTime.now());
            exam.update();
        } catch (RuntimeException e) {
            Logger.error("{}: ... Sending email to {} failed", getClass().getCanonicalName(), student.getEmail());
        }
    }

}
