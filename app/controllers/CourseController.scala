package controllers

import com.avaje.ebean.Ebean
import models.Course
import play.api.mvc.{Controller, Action}
import util.scala.ScalaHacks

object CourseController extends Controller with ScalaHacks {

  sealed trait FilterType
  case object FilterByName extends FilterType
  case object FilterByCode extends FilterType

  val CriteriaLengthLimiter = 2

  def getCourses(filterType: Option[FilterType], criteria: Option[String]) =
    Action {
      (filterType, criteria) match {
        case (Some(FilterByCode), Some(x)) =>
          Ebean.find(classOf[Course]).where()
            .like("code", s"$x%")
            .orderBy("name desc")
            .findList()
        case (Some(FilterByName), Some(x)) if x.size >= CriteriaLengthLimiter =>
          Ebean.find(classOf[Course]).where()
            .ilike("name", s"$x%")
            .orderBy("name desc")
            .findList()
        case (Some(FilterByName), Some(x)) =>
          throw new IllegalArgumentException("Too short criteria")
        case _ =>
          Ebean.find(classOf[Course]).findList
        }
    }

  def getCourse(id: Long) = Action {
    Ebean.find(classOf[Course], id)
  }

}