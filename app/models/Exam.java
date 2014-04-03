package models;

import annotations.NonCloneable;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;

import models.questions.AbstractQuestion;
import models.questions.MultipleChoiceQuestion;
import models.questions.MultipleChoiseOption;
import util.SitnetUtil;

import javax.persistence.*;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/*
 * HUOM tämä luokka ei ole Tentin toteutus, vaan tentin tietomalli
 * 
 * Kuvaa Sitnettiin tallennettavan tentin rakenteen
 * 
 */
@Entity
public class Exam extends SitnetModel {

//  @OneToOne
//  @JsonManagedReference
//  private ExamEvent examEvent;
	

//    @ManyToMany(cascade = CascadeType.ALL)
//    @NonCloneable
//    @JoinTable(name="exam_event_inspectors",
//            joinColumns={@JoinColumn(name="user_id")},
//            inverseJoinColumns={@JoinColumn(name="exam_event_id")})
//    private List<User> inspectors;
//
//    @ManyToMany(cascade = CascadeType.ALL)
//    @NonCloneable
//    @JoinTable(name="exam_event_enrolled_student",
//            joinColumns={@JoinColumn(name="user_id")},
//            inverseJoinColumns={@JoinColumn(name="exam_event_id")})
//    private List<User> enrolledStudents;
	
    // student User who has participated in this Exam
    @ManyToOne
    @NonCloneable
    @JsonBackReference
    private User student;
    


    private String name;
    
    @ManyToOne
    @NonCloneable
    private Course course;

    private ExamType examType;
    
    // Opettajan antama ohje Opiskelijalle tentin suorittamista varten
    private String instruction;

    private boolean shared;

    // An ExamSection may be used only in one Exam
    @OneToMany(cascade = CascadeType.PERSIST, mappedBy = "exam")
    @JsonManagedReference
    private List<ExamSection> examSections;

    /*
     *  Kun opiskelijalle tehdään kopio tentistä, tämä tulee viittaamaan alkuperäiseen tenttiin
     *  
     *  Lisäksi tentaattori pitää löytää joukko tenttejä, jotka ovat suoritettuja, jotka pitää tarkistaa
     *  tätä viitettä voidaan käyttää niiden tenttien löytämiseen  
     */
    @OneToOne
    @NonCloneable
    private Exam parent;


    @Column(length = 32, unique = true)
    private String hash;

    // tentin voimassaoloaika, tentti on avoin opiskelijoille tästä lähtien
    private Timestamp examActiveStartDate;
    
    // tentin voimassaoloaika, tentti sulkeutuu
    private Timestamp examActiveEndDate;

    // Akvaario
    private String room;

    // tentin kesto
    private Double duration;

    // Exam grading, e.g. 1-5
    private String grading;

    // Exam language
    private String examLanguage;

    // Exam answer language
    private String answerLanguage;
    
    private String state;

    private String grade;
    
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

//    public ExamEvent getExamEvent() {
//        return examEvent;
//    }
//
//    public void setExamEvent(ExamEvent examEvent) {
//        this.examEvent = examEvent;
//    }

    public String getHash() {
        return hash;
    }

    public User getStudent() {
		return student;
	}

	public void setStudent(User student) {
		this.student = student;
	}

	public Timestamp getExamActiveStartDate() {
		return examActiveStartDate;
	}

	public void setExamActiveStartDate(Timestamp examActiveStartDate) {
		this.examActiveStartDate = examActiveStartDate;
	}

	public Timestamp getExamActiveEndDate() {
		return examActiveEndDate;
	}

	public void setExamActiveEndDate(Timestamp examActiveEndDate) {
		this.examActiveEndDate = examActiveEndDate;
	}

	public String getRoom() {
		return room;
	}

	public void setRoom(String room) {
		this.room = room;
	}

	public Double getDuration() {
		return duration;
	}

	public void setDuration(Double duration) {
		this.duration = duration;
	}

	public String getGrading() {
		return grading;
	}

	public void setGrading(String grading) {
		this.grading = grading;
	}

	public String getExamLanguage() {
		return examLanguage;
	}

	public void setExamLanguage(String examLanguage) {
		this.examLanguage = examLanguage;
	}

	public String getAnswerLanguage() {
		return answerLanguage;
	}

	public void setAnswerLanguage(String answerLanguage) {
		this.answerLanguage = answerLanguage;
	}

	public String generateHash() {

        // TODO: what attributes make examEvent unique?
        // create unique hash for exam
        String attributes = name + state;

//                examEvent.getStartTime().toString() +
//                examEvent.getEndTime().toString();

        this.hash = SitnetUtil.encodeMD5(attributes);
        play.Logger.debug("Exam hash: " + this.hash);
        return hash;
    }

    public Exam getParent() {
		return parent;
	}

	public void setParent(Exam parent) {
		this.parent = parent;
	}

	public String getGrade() {
		return grade;
	}

	public void setGrade(String grade) {
		this.grade = grade;
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



        Exam clone = (Exam)SitnetUtil.getClone(this);

        clone.setState("STUDENT_STARTED");
        clone.generateHash();

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

        //clone.setExamEvent(this.getExamEvent());
        clone.generateHash();
        clone.setState("STUDENT_STARTED");

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
//                ", examEvent=" + examEvent +
                ", hash='" + hash + '\'' +
                ", state='" + state + '\'' +
                '}';
    }
}
