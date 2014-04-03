package models;

import annotations.NonCloneable;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import controllers.UserController;
import models.questions.AbstractQuestion;
import models.questions.MultipleChoiceQuestion;
import models.questions.MultipleChoiseOption;
import util.SitnetUtil;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/*
 * HUOM tämä luokka ei ole Tentin toteutus, vaan tentin tietomalli
 * 
 * Kuvaa Sitnettiin tallennettavan tentin rakenteen
 * 
 */
@Entity

public class Exam extends SitnetModel {

    // student User who has participated in this Exam
    @ManyToOne
    @NonCloneable
    @JsonBackReference
    private User student;

    // onko tentillä joku toinen nimi, Opintojakson nimen lisäksi
    private String name;
    
    // Tentti liittyy Opintojaksoon
    @ManyToOne
    @NonCloneable
    private Course course;

    @OneToOne
    @JsonManagedReference
    private ExamEvent examEvent;
    
    private ExamType examType;

    // Opettajan antama ohje Opiskelijalle tentin suorittamista varten
    private String instruction;

    private boolean shared;

    // An ExamSection may be used only in one Exam
    @OneToMany(cascade = CascadeType.PERSIST, mappedBy = "exam")
    @JsonManagedReference
    private List<ExamSection> examSections;

    @Column(length = 32, unique = true)
    private String hash;

    private Timestamp answeringStarted;

    private Timestamp answeringEnded;

    private String state;

    public Timestamp getAnsweringStarted() {
        return answeringStarted;
    }

    public void setAnsweringStarted(Timestamp answeringStarted) {
        this.answeringStarted = answeringStarted;
    }

    public Timestamp getAnsweringEnded() {
        return answeringEnded;
    }

    public void setAnsweringEnded(Timestamp answeringEnded) {
        this.answeringEnded = answeringEnded;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ExamType getExamType() {
        return examType;
    }

    public void setExamType(ExamType examType) {
        this.examType = examType;
    }

    public List<ExamSection> getExamSections() {
        return examSections;
    }

    public void setExamSections(List<ExamSection> examSections) {
        this.examSections = examSections;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public ExamEvent getExamEvent() {
        return examEvent;
    }

    public void setExamEvent(ExamEvent examEvent) {
        this.examEvent = examEvent;
    }

    public String getHash() {
        return hash;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public String generateHash() {

        Random rand = new Random();

        // TODO: what attributes make examEvent unique?
        // create unique hash for exam
        String attributes = name + state + new String(rand.nextDouble()+"");

//                examEvent.getStartTime().toString() +
//                examEvent.getEndTime().toString();

        this.hash = SitnetUtil.encodeMD5(attributes);
        play.Logger.debug("Exam hash: " + this.hash);
        return hash;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public Object clone() {

//        return SitnetUtil.getClone(this);

        Exam clone = new Exam();

//        Exam clone = (Exam)SitnetUtil.getClone(this);

//        clone.setState("STUDENT_STARTED");
//        clone.generateHash();

        // Causes infinite recursion
//        clone.setStudent(UserController.getLoggedUser());
        clone.setCreated(this.getCreated());
        clone.setCreator(this.getCreator());
        clone.setModified(this.getModified());
        clone.setModifier(this.getModifier());
        clone.setCourse(this.getCourse());
        clone.setName(this.getName());
        clone.setExamType(this.getExamType());
        clone.setInstruction(this.getInstruction());
        clone.setShared(this.isShared());

//            if(this.examEvent != null) {
//                clone.setExamEvent((ExamEvent) this.examEvent.clone());
//            }

//        try {
//            if(this.examEvent != null) {
//                clone.setExamEvent((ExamEvent) this.examEvent.clone());
//            }
//        } catch (CloneNotSupportedException e) {
//            e.printStackTrace();
//        }


        List<ExamSection> examSectionsCopies = createNewExamSectionList();

        for (ExamSection es : this.getExamSections()) {

            // New arrays are needed for every examsection
            List<AbstractQuestion> examQuestionCopies = createNewExamQuestionList();

            ExamSection examsec_copy = (ExamSection)es._ebean_createCopy();
            examsec_copy.setId(null);

            for (AbstractQuestion q : es.getQuestions()) {

                AbstractQuestion question_copy = (AbstractQuestion)q._ebean_createCopy();
                question_copy.setId(null);
                question_copy.setParent(q);

                    switch (q.getType()) {
                        case "MultipleChoiceQuestion": {
                            List<MultipleChoiseOption> multipleChoiceOptionCopies = createNewMultipleChoiceOptionList();


                            List<MultipleChoiseOption> options = ((MultipleChoiceQuestion) q).getOptions();
                            for (MultipleChoiseOption o : options) {
                                MultipleChoiseOption m_option_copy = (MultipleChoiseOption)o._ebean_createCopy();
                                m_option_copy.setId(null);
                                multipleChoiceOptionCopies.add(m_option_copy);
                            }
                            ((MultipleChoiceQuestion)question_copy).setOptions(multipleChoiceOptionCopies);
                        }
                        break;
                    }
                examQuestionCopies.add(question_copy);
            }
            examsec_copy.setQuestions(examQuestionCopies);
            examSectionsCopies.add(examsec_copy);
        }

        clone.setExamSections(examSectionsCopies);

        clone.setExamEvent(this.getExamEvent());

        clone.setState("STUDENT_STARTED");
        clone.generateHash();

        return clone;
    }

    public List<ExamSection> createNewExamSectionList() {
        List<ExamSection> examSectionsCopies = new ArrayList<ExamSection>();
        return examSectionsCopies;
    }

    public List<AbstractQuestion> createNewExamQuestionList() {
        List<AbstractQuestion> examQuestionCopies = new ArrayList<AbstractQuestion>();
        return examQuestionCopies;
    }

    public List<MultipleChoiseOption> createNewMultipleChoiceOptionList() {
        List<MultipleChoiseOption> multipleChoiceOptionCopies = new ArrayList<MultipleChoiseOption>();
        return multipleChoiceOptionCopies;
    }

    @Override
    public String toString() {
        return "Exam{" +
                "course=" + course +
                ", name='" + name + '\'' +
                ", examType=" + examType +
                ", instruction='" + instruction + '\'' +
                ", shared=" + shared +
//                ", examSections=" + examSections +
                ", examEvent=" + examEvent +
                ", hash='" + hash + '\'' +
                ", state='" + state + '\'' +
                '}';
    }
}
