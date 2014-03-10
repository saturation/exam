package models;

import org.apache.commons.codec.digest.DigestUtils;

import javax.persistence.*;
import java.util.List;

/*
 * HUOM tämä luokka ei ole Tentin toteutus, vaan tentin tietomalli
 * 
 * Kuvaa Sitnettiin tallennettavan tentin rakenteen
 * 
 */
@Entity
public class Exam extends SitnetModel {

    // Tentti liittyy Opintojaksoon
    @ManyToOne
    private Course course;

    // onko tentillä joku toinen nimi, Opintojakson nimen lisäksi
    private String name;

    private ExamType examType;

    // Opettajan antama ohje Opiskelijalle tentin suorittamista varten
    private String instruction;

    private boolean shared;


    // XXX: This should be actually @OneToMany relationship, but there's problems with Ebean
    @ManyToMany(cascade = CascadeType.PERSIST)
    private List<ExamSection> examSections;


    @OneToOne
    private ExamEvent examEvent;

    @Column(length = 32)
    private String hash;

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

    public String generateHash() {

        // TODO: what attributes make examEvent unique?
        // create unique hash for exam
        String attributes = name +
                course.getCode();

//                examEvent.getStartTime().toString() +
//                examEvent.getEndTime().toString();

        this.hash = DigestUtils.md5Hex(attributes);
        play.Logger.debug("Exam hash: " + this.hash);
        return hash;
    }

    @Override
    public String toString() {
        return "Exam{" +
                "hash='" + hash + '\'' +
                ", course=" + course +
                ", name='" + name + '\'' +
                ", examType=" + examType +
                ", instruction='" + instruction + '\'' +
                ", shared=" + shared +
                ", examEvent=" + examEvent +
                '}';
    }
}
