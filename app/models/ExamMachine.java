package models;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import play.db.ebean.Model;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

/*
 * Tenttikone jolla opiskelija tekee tentin
 * Kone sijaitsee Tenttiakvaariossa
 * 
 */
@Entity
public class ExamMachine extends Model {

    @Version
    @Temporal(TemporalType.TIMESTAMP)
    protected Date ebeanTimestamp;

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Long id;

    /*
     * jonkinlainen ID jolla kone tunnistetaan
     *
     * Esim akvaario-koneenID  IT103-7
     */
    private String name;

    /*
     * Other identifier for the exam machine, specified in task SIT-84
     */
    private String otherIdentifier;
    // Esteettömyystieto
    @Deprecated
    private String accessibilityInfo;

    // Checkbox indicating is there any accessibility issues concerning the room
    @Column(columnDefinition="boolean default false")
    @Deprecated
    private boolean accessible;

    // Ohjelmistot
    @ManyToMany(cascade = CascadeType.ALL)
    private List<Software> softwareInfo;

    // Esteettömyys
    @ManyToMany(cascade = CascadeType.ALL)
    @JsonManagedReference
    private List<Accessibility> accessibility;

    private String ipAddress;

    private String surveillanceCamera;

    private String videoRecordings;

    @ManyToOne
    @JsonBackReference
	private ExamRoom room;

    @OneToMany(cascade = CascadeType.PERSIST, mappedBy = "machine")
    @JsonManagedReference
    private List<Reservation> reservation;

    // In UI, section has been expanded
    @Column(columnDefinition="boolean default false")
    private boolean expanded;

    // Machine may be out of service,
    private String statusComment;

    private boolean archived;

    private boolean outOfService;

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public boolean getExpanded() {
        return expanded;
    }

    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ExamRoom getRoom() {
        return room;
    }

    public void setRoom(ExamRoom room) {
        this.room = room;
    }

    public List<Reservation> getReservation() {
        return reservation;
    }

    public String getAccessibilityInfo() {
        return accessibilityInfo;
    }

    public void setAccessibilityInfo(String accessibilityInfo) {
        this.accessibilityInfo = accessibilityInfo;
    }

    public boolean isAccessible() {
        return accessible;
    }

    public void setAccessible(boolean accessible) {
        this.accessible = accessible;
    }

    public List<Software> getSoftwareInfo() {
        return softwareInfo;
    }

    public void setSoftwareInfo(List<Software> softwareInfo) {
        this.softwareInfo = softwareInfo;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getStatusComment() {
        return statusComment;
    }

    public void setStatusComment(String statusComment) {
        this.statusComment = statusComment;
    }

    public boolean getOutOfService() {
        return outOfService;
    }

    public void setOutOfService(boolean outOfService) {
        this.outOfService = outOfService;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setReservation(List<Reservation> reservation) {
        this.reservation = reservation;
    }

    public String getOtherIdentifier() {
        return otherIdentifier;
    }

    public void setOtherIdentifier(String otherIdentifier) {
        this.otherIdentifier = otherIdentifier;
    }

    public String getSurveillanceCamera() {
        return surveillanceCamera;
    }

    public void setSurveillanceCamera(String surveillanceCamera) {
        this.surveillanceCamera = surveillanceCamera;
    }

    public String getVideoRecordings() {
        return videoRecordings;
    }

    public void setVideoRecordings(String videoRecordings) {
        this.videoRecordings = videoRecordings;
    }

    public List<Accessibility> getAccessibility() {
        return accessibility;
    }

    public void setAccessibility(List<Accessibility> accessibility) {
        this.accessibility = accessibility;
    }

    @Override
    public String toString() {
        return "ExamMachine{" +
                "accessibilityInfo='" + accessibilityInfo + '\'' +
                ", id=" + id +
                ", name='" + name + '\'' +
                ", archived='" + archived + '\'' +
                ", otherIdentifier='" + otherIdentifier + '\'' +
                ", accessible=" + accessible +
                ", softwareInfo=" + softwareInfo +
                ", ipAddress='" + ipAddress + '\'' +
                ", surveillanceCamera='" + surveillanceCamera + '\'' +
                ", videoRecordings='" + videoRecordings + '\'' +
                ", room=" + room +
                ", reservation=" + reservation +
                ", expanded=" + expanded +
                ", statusComment='" + statusComment + '\'' +
                ", outOfService=" + outOfService +
                '}';
    }
}
