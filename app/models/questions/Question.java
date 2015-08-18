package models.questions;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import models.Attachment;
import models.ExamSectionQuestion;
import models.OwnedModel;
import models.Tag;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.springframework.beans.BeanUtils;

import javax.persistence.*;
import java.util.List;

@Entity
public class Question extends OwnedModel {

    public enum Type { MultipleChoiceQuestion, EssayQuestion }

    @Column
    protected String type;

    @Column(columnDefinition = "TEXT")
    protected String question;

    protected boolean shared;

    @Column(columnDefinition = "TEXT")
    protected String instruction;

    protected String state;

    @Column(columnDefinition="numeric default 0")
    protected Double maxScore = 0.0;

    @Column(columnDefinition="numeric default 0")
    protected Double evaluatedScore;

    @ManyToOne(cascade = CascadeType.PERSIST) // do not delete parent question
    protected Question parent;

    @OneToMany(mappedBy = "parent")
    @JsonBackReference
    protected List<Question> children;

    @OneToOne (cascade = CascadeType.ALL)
    protected Answer answer;

    @Column(columnDefinition = "TEXT")
    protected String evaluationCriterias;

    @OneToOne(mappedBy = "question")
    @JsonBackReference
    protected ExamSectionQuestion examSectionQuestion;

    @OneToOne(cascade = CascadeType.ALL)
    protected Attachment attachment;

    // In UI, section has been expanded
    @Column(columnDefinition="boolean default false")
    protected boolean expanded;

    // not really max length, Just a recommendation
    private Long maxCharacters;

    // Points, Select
    private String evaluationType;

    @OneToMany(cascade = {CascadeType.PERSIST, CascadeType.REMOVE}, mappedBy = "question")
    @JsonManagedReference
    private List<MultipleChoiseOption> options;


    @ManyToMany(cascade = CascadeType.ALL)
    protected List<Tag> tags;


    public String getState() { return state; }

    public void setState(String state) {
        this.state = state;
    }

    public String getType() { return type; }

    public void setType(String type) {
        this.type = type;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public Question getParent() {
        return parent;
    }

    public void setParent(Question parent) {
        this.parent = parent;
    }

    public Answer getAnswer() {
        return answer;
    }

    public void setAnswer(Answer answer) {
        this.answer = answer;
    }

    public String getEvaluationCriterias() {
        return evaluationCriterias;
    }

    public void setEvaluationCriterias(String evaluationCriterias) {
        this.evaluationCriterias = evaluationCriterias;
    }

    public Attachment getAttachment() {
        return attachment;
    }

    public void setAttachment(Attachment attachment) {
        this.attachment = attachment;
    }

    public boolean getExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public Double getMaxScore() {
        return maxScore;
    }

    public void setMaxScore(Double maxScore) {
        this.maxScore = maxScore;
    }

    public List<MultipleChoiseOption> getOptions() {
        return options;
    }

    public void setOptions(List<MultipleChoiseOption> options) {
        this.options = options;
    }

    public Double getEvaluatedScore() {
        return evaluatedScore;
    }

    public void setEvaluatedScore(Double evaluatedScore) {
        this.evaluatedScore = evaluatedScore;
    }

    public ExamSectionQuestion getExamSectionQuestion() {
        return examSectionQuestion;
    }

    public void setExamSectionQuestion(ExamSectionQuestion examSectionQuestion) {
        this.examSectionQuestion = examSectionQuestion;
    }

    public Long getMaxCharacters() {
        return maxCharacters;
    }

    public void setMaxCharacters(Long maxCharacters) {
        this.maxCharacters = maxCharacters;
    }

    public String getEvaluationType() {
        return evaluationType;
    }

    public void setEvaluationType(String evaluationType) {
        this.evaluationType = evaluationType;
    }

    public List<Question> getChildren() {
        return children;
    }

    public void setChildren(List<Question> children) {
        this.children = children;
    }

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Question)) {
            return false;
        }
        Question other = (Question)object;
        return new EqualsBuilder().append(id, other.getId()).build();
    }

	public Question copy() {
        Question question = new Question();
        BeanUtils.copyProperties(this, question, "id", "answer", "options", "tags", "children");
        question.setParent(this);
        for (MultipleChoiseOption o : options) {
            question.getOptions().add(o.copy());
        }
        if (attachment != null) {
            Attachment copy = new Attachment();
            BeanUtils.copyProperties(attachment, copy, "id");
            question.setAttachment(copy);
        }
        return question;
    }

   	@Override
    public String toString() {
        return "Question [type=" + type
                + ", id=" + id + "]";
    }

}
