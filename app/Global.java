import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import Exceptions.AuthenticateException;
import Exceptions.MalformedDataException;
import Exceptions.UnauthorizedAccessException;
import models.Exam;
import models.ExamEvent;
import models.Question;
import models.User;
import play.Application;
import play.GlobalSettings;
import play.Logger;
import play.libs.F;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Action;
import play.mvc.SimpleResult;
import play.mvc.Http;
import play.mvc.Results;

import play.libs.Yaml;
import play.mvc.Http.Request;

import com.avaje.ebean.Ebean;

public class Global extends GlobalSettings {

    @Override
    public void onStart(Application app) {
        InitialData.insert(app);
    }

    @Override
    public Promise<SimpleResult> onError(Http.RequestHeader request, final Throwable t) {
        F.Promise<SimpleResult> promise = F.Promise.promise(new F.Function0<SimpleResult>() {
            public SimpleResult apply() {
                Throwable cause = t.getCause();
                String errorMessage = cause.getMessage();
                if (cause instanceof UnauthorizedAccessException) {
                    return Results.forbidden(Json.toJson(errorMessage));
                }
                if (cause instanceof AuthenticateException) {
                    return Results.unauthorized(Json.toJson(errorMessage));
                }
                if (cause instanceof MalformedDataException) {
                    return Results.badRequest(Json.toJson(errorMessage));
                }
                return Results.internalServerError(Json.toJson(errorMessage));
            }
        });
        return promise;
    }

    @Override
    public Action onRequest(Request request, Method actionMethod) {
        Logger.debug(request.path());
        return super.onRequest(request, actionMethod);
    }

    private static class InitialData {
        public static void insert(Application app) {
            if (Ebean.find(User.class).findRowCount() == 0) {
                @SuppressWarnings("unchecked")
                Map<String, List<Object>> all = (Map<String, List<Object>>) Yaml.load("initial-data.yml");
                
                // HUOM, järjestyksellä on väliä 
                Ebean.save(all.get("user_languages"));
                Ebean.save(all.get("users"));
                Ebean.save(all.get("courses"));
                Ebean.save(all.get("questions"));         
                Ebean.save(all.get("examsections"));
                Ebean.save(all.get("examevents"));
                Ebean.save(all.get("exams"));

                // generate hashes for questions
                List<Question> questions = (List)all.get("questions");
                for (Question q : questions){
                    q.generateHash();
                }
                Ebean.save(questions);

                ExamEvent examEvent = Ebean.find(ExamEvent.class, 1);
                if(examEvent != null)
                {
                    // startTime 1393549200   28.02.2014 00:00
                    // endTime   1419728400   28.12.2014 00:00

                    examEvent.setExamActiveStartDate(new Timestamp(1393549200));
                    examEvent.setExamActiveEndDate(new Timestamp(1419728400));
                    examEvent.setDuration(new Double(1.0));
                    examEvent.save();
                    Logger.debug("Exam event initialized");
                }
                else
                    Logger.debug("Exam event: "+ examEvent);

                // generate hashes for questions
                List<Exam> exams = (List)all.get("exams");
                for (Exam e : exams){
                    e.generateHash();
                }
                Ebean.save(exams);

            }
        }
    }
}