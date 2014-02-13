package models;


/*
 * Opintojakso
 * http://tietomalli.csc.fi/Opintojakso-kaavio.html
 * 
 * 
 */
public class Course extends SitnetModel {

	
	
	// Opintojakson koodi, 811380A 	Tietokantojen perusteet 
	private String code;
	
	private String name;
	
	private CourseType type;
	
	// Laajuus, opintopisteet
	private Double credits;

	public Course(User creator, String code, String name, CourseType type, Double credits) {
		super(creator);
		this.code = code;
		this.name = name;
		this.type = type;
		this.credits = credits;
	}

	public String getCode() {
		return code;
	}

	public void setCode(String code) {
		this.code = code;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public CourseType getType() {
		return type;
	}

	public void setType(CourseType type) {
		this.type = type;
	}

	public Double getCredits() {
		return credits;
	}

	public void setCredits(Double credits) {
		this.credits = credits;
	}
	
}
