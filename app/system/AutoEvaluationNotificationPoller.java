package system;

import com.avaje.ebean.Ebean;
import models.AutoEvaluationConfig;
import models.ExamParticipation;
import models.User;
import org.joda.time.DateTime;
import play.Logger;
import util.AppUtil;
import util.java.EmailComposer;

import javax.inject.Singleton;
import java.util.Date;
import java.util.List;

@Singleton
public class AutoEvaluationNotificationPoller implements Runnable {

    EmailComposer emailComposer;

    public AutoEvaluationNotificationPoller(EmailComposer composer) {
        emailComposer = composer;
    }

    @Override
    public void run() {
        Logger.debug("{}: Running auto evaluation notification check ...", getClass().getCanonicalName());
        List<ExamParticipation> participations = Ebean.find(ExamParticipation.class)
                .fetch("exam.autoEvaluationConfig")
                .where()
                .isNotNull("exam.autoEvaluationConfig")
                .isNotNull("ended")
                .isNotNull("exam.grade")
                .isNotNull("exam.creditType")
                .isNotNull("exam.gradedTime")
                .isNotNull("exam.answerLanguage")
                .isNull("autoEvaluationNotified")
                .findList();

        participations.stream()
                .filter(this::isPastReleaseDate)
                .forEach(this::notifyStudent);

        Logger.debug("{}: ... Done", getClass().getCanonicalName());
    }

    private DateTime adjustReleaseDate(DateTime date) {
        return AppUtil.adjustDST(date.withHourOfDay(5).withMinuteOfHour(0).withSecondOfMinute(0));
    }

    private boolean isPastReleaseDate(ExamParticipation participation) {
        DateTime releaseDate;
        AutoEvaluationConfig config = participation.getExam().getAutoEvaluationConfig();
        switch (config.getReleaseType()) {
            case IMMEDIATE:
                releaseDate = DateTime.now();
                break;
            // Put some delay in these dates to avoid sending stuff in the middle of the night
            case GIVEN_DATE:
                releaseDate = adjustReleaseDate(new DateTime(config.getReleaseDate()));
                break;
            case GIVEN_AMOUNT_DAYS:
                releaseDate = adjustReleaseDate(new DateTime(participation.getEnded()).plusDays(config.getAmountDays()));
                break;
            case AFTER_EXAM_PERIOD:
                releaseDate = adjustReleaseDate(new DateTime(participation.getExam().getExamActiveEndDate()));
                break;
            case NEVER:
            default:
                releaseDate = null;
                break;
        }
        return releaseDate != null && releaseDate.isBeforeNow();
    }

    private void notifyStudent(ExamParticipation participation) {
        User student = participation.getUser();
        try {
            emailComposer.composeInspectionReady(student, null, participation.getExam());
            Logger.debug("{}: ... Mail sent to {}", getClass().getCanonicalName(), student.getEmail());
            participation.setAutoEvaluationNotified(new Date());
            participation.update();
        } catch (RuntimeException e) {
            Logger.error("{}: ... Sending email to {} failed", getClass().getCanonicalName(), student.getEmail());
        }
    }

}
