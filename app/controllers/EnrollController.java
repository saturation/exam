package controllers;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

import models.Exam;
import models.ExamEnrolment;
import models.User;
import play.mvc.Controller;
import play.mvc.Result;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.text.json.JsonContext;
import com.avaje.ebean.text.json.JsonWriteOptions;

public class EnrollController extends Controller {

//  @Restrict(@Group({"STUDENT"}))
    public static Result enrollExamList(String code)  {

        Timestamp now = new Timestamp(new Date().getTime());

        List<Exam> activeExams = Ebean.find(Exam.class)
                .fetch("course")
                .where()
                .eq("course.code", code)
                .eq("state", "PUBLISHED")
                .betweenProperties("examActiveStartDate", "examActiveEndDate", now)
                .findList();

        JsonContext jsonContext = Ebean.createJsonContext();
        JsonWriteOptions options = new JsonWriteOptions();
        options.setRootPathProperties("id, name, course, examActiveStartDate, examActiveEndDate, creator");
        options.setPathProperties("course", "code");
        options.setPathProperties("creator", "firstName, lastName");

        return ok(jsonContext.toJsonString(activeExams, true, options)).as("application/json");
    }
    
//  @Restrict(@Group({"STUDENT"}))
    public static Result enrollExamInfo(String code, Long id)  {
  	
    	Exam exam = Ebean.find(Exam.class)
    			.fetch("course")
    			.where()
    			.eq("course.code", code)
    			.eq("id", id)
    			.findUnique();
    	
    	JsonContext jsonContext = Ebean.createJsonContext();
    	JsonWriteOptions options = new JsonWriteOptions();
    	options.setRootPathProperties("id, name, examActiveStartDate, examActiveEndDate, duration, grading, room, course, creator");
    	options.setPathProperties("course", "code, name, level, type, credits");
    	options.setPathProperties("creator", "firstName, lastName, email");
    	
    	return ok(jsonContext.toJsonString(exam, true, options)).as("application/json");
    }
    
    
    public static Result createEnrolment(String code, Long id)  {

    	Timestamp now = new Timestamp(new Date().getTime());
    	User user = UserController.getLoggedUser();
  	
    	Exam exam = Ebean.find(Exam.class)
    			.where()
    			.eq("course.code", code)
    			.eq("id", id)
    			.findUnique();

    	// check if enrolment already exist?
    	ExamEnrolment enrolment = Ebean.find(ExamEnrolment.class)
    			.where()
    			.eq("user.id", user.getId())
    			.eq("exam.id", exam.getId())
    			.findUnique();
    	
    	if(enrolment != null) {
    		return forbidden("Enrolment already exist");
    	}

    	if(exam != null)
    	{
    		enrolment = new ExamEnrolment();
    		enrolment.setEnrolledOn(now);
    		enrolment.setUser(user);
    		enrolment.setExam(exam);
    		enrolment.save();
    		
            return redirect("/#/home/");
    	}
    	else {
    		return notFound("Exam not found");
    	}
    }

    public static Result deleteEnrolment(String code, Long id)  {
    	
    	Timestamp now = new Timestamp(new Date().getTime());
    	User user = UserController.getLoggedUser();
    	
    	Exam exam = Ebean.find(Exam.class)
    			.where()
    			.eq("course.code", code)
    			.eq("id", id)
    			.findUnique();
    	if(exam != null)
    	{
        	ExamEnrolment enrolment = Ebean.find(ExamEnrolment.class)
        			.where()
        			.eq("user.id", user.getId())
        			.eq("exam.id", exam.getId())
        			.findUnique();
        	
        	enrolment.delete();
        	
    		return ok();
    	}
    	else {
    		return notFound("Exam not found");
    	}
    }

}