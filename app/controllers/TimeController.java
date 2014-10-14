package controllers;

import com.avaje.ebean.Ebean;
import models.Exam;
import models.ExamEnrolment;
import models.User;
import org.joda.time.DateTime;
import org.joda.time.Minutes;
import org.joda.time.Seconds;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import play.mvc.Controller;
import play.mvc.Result;


public class TimeController extends Controller {

    private static DateTimeFormatter format = DateTimeFormat.forPattern("dd.MM.yyyy HH:mm");


    public static Result getTime() {
        return ok(DateTime.now().toString(format));
    }


    public static Result getExamRemainingTime(Long examId) {

        User user = null;
        try {
            user = UserController.getLoggedUser();
        } catch (Exception ex) {
        }

        if (user == null) {
            return forbidden("invalid session");
        }

        ExamEnrolment enrolment = Ebean.find(ExamEnrolment.class)
                .fetch("reservation")
                .fetch("exam")
                .where()
                .eq("exam.id", examId)
                .eq("user.id", user.getId())
                .findUnique();


        if (enrolment == null) {
            return notFound();
        }

        Exam exam = enrolment.getExam();

        final Seconds examDuration = Minutes.minutes(exam.getDuration()).toStandardSeconds();
        final DateTime now = DateTime.now();
        final DateTime started = new DateTime(enrolment.getReservation().getStartAt());
        final Seconds tau = Seconds.secondsBetween(started, now);

        return ok(String.valueOf(examDuration.minus(tau).getSeconds()));
    }

}
