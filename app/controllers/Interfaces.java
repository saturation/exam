package controllers;


import com.avaje.ebean.Ebean;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import exceptions.NotFoundException;
import models.*;
import models.dto.ExamScore;
import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import play.Logger;
import play.libs.F;
import play.libs.Json;
import play.libs.ws.WS;
import play.libs.ws.WSRequestHolder;
import play.libs.ws.WSResponse;
import play.mvc.Result;
import play.mvc.Results;

import javax.servlet.http.HttpServletResponse;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class Interfaces extends SitnetController {

    private static final String USER_ID_PLACEHOLDER = "${employee_number}";
    private static final ObjectMapper SORTED_MAPPER = new ObjectMapper();

    static {
        SORTED_MAPPER.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    private static URL parseUrl(User user) throws MalformedURLException {
        String url = ConfigFactory.load().getString("sitnet.integration.enrolmentPermissionCheck.url");
        if (url == null || !url.contains(USER_ID_PLACEHOLDER)) {
            throw new RuntimeException("sitnet.integration.enrolmentPermissionCheck.url is malformed");
        }
        url = url.replace(USER_ID_PLACEHOLDER, user.getEmployeeNumber());
        return new URL(url);
    }

    public static F.Promise<Collection<String>> getPermittedCourses(User user) throws MalformedURLException {
        URL url = parseUrl(user);
        WSRequestHolder request = WS.url(url.toString().split("\\?")[0]);
        if (url.getQuery() != null) {
            request = request.setQueryString(url.getQuery());
        }
        F.Function<WSResponse, Collection<String>> onSuccess = new F.Function<WSResponse, Collection<String>>() {
            @Override
            public Collection<String> apply(WSResponse response) throws Throwable {
                Set<String> results = new HashSet<>();
                for (JsonNode json : response.asJson()) {
                    if (json.has("courseUnitCode")) {
                        results.add(json.get("courseUnitCode").asText());
                    } else {
                        Logger.warn("Unexpected content {}", json.asText());
                    }
                }
                return results;
            }
        };
        F.Function<Throwable, Collection<String>> onFailure = new F.Function<Throwable, Collection<String>>() {
            @Override
            public Collection<String> apply(Throwable throwable) throws Throwable {
                if (throwable instanceof TimeoutException || throwable instanceof ConnectException) {
                    throw new TimeoutException("sitnet_request_timed_out");
                }
                throw throwable;
            }
        };
        return request.get().map(onSuccess).recover(onFailure);
    }

    public static F.Promise<List<Course>> getCourseInfo(String code) throws MalformedURLException {
        URL url = new URL(ConfigFactory.load().getString("sitnet.integration.courseUnitInfo.url"));
        final List<Course> courses = Ebean.find(Course.class).where().like("code", code + "%").orderBy("name desc").findList();
        if (!courses.isEmpty() || !isCourseSearchActive()) {
            // we already have it or we don't want to fetch it
            return F.Promise.promise(new F.Function0<List<Course>>() {
                @Override
                public List<Course> apply() throws Throwable {
                    return courses;
                }
            });
        }
        return WS.url(url.toString()).setQueryParameter("courseUnitCode", code).get().map(new F.Function<WSResponse, List<Course>>() {
            @Override
            public List<Course> apply(WSResponse wsResponse) throws Throwable {
                int status = wsResponse.getStatus();
                if (status == HttpServletResponse.SC_OK) {
                    return parseResponse(wsResponse.asJson());
                }
                Logger.info("Non-OK response received {}", status);
                throw new NotFoundException(String.format("sitnet_remote_failure %d %s", status, wsResponse.getStatusText()));
            }
        }).recover(new F.Function<Throwable, List<Course>>() {
            @Override
            public List<Course> apply(Throwable throwable) throws Throwable {
                if (throwable instanceof TimeoutException || throwable instanceof ConnectException) {
                    throw new TimeoutException("sitnet_request_timed_out");
                }
                throw throwable;
            }
        });
    }

    private static List<GradeScale> getGradeScales(JsonNode node) {
        List<GradeScale> scales = new ArrayList<>();
        if (node.has("gradeScale")) {
            node = node.get("gradeScale");
            for (JsonNode scale : node) {
                String type = scale.get("type").asText();
                GradeScale.Type scaleType = GradeScale.Type.get(type);
                if (scaleType == null) {
                    // not understood
                    Logger.warn("Skipping over unknown grade scale type {}", type);
                    continue;
                }
                if (scaleType.equals(GradeScale.Type.OTHER)) {
                    // This needs custom handling
                    if (!scale.has("code") || !scale.has("name")) {
                        Logger.warn("Skipping over grade scale of type OTHER, requirednodes are missing: {}",
                                scale.asText());
                        continue;
                    }
                    Long externalRef = scale.get("code").asLong();
                    GradeScale gs = Ebean.find(GradeScale.class).where().eq("externalRef", externalRef).findUnique();
                    if (gs != null) {
                        scales.add(gs);
                        continue;
                    }
                    gs = new GradeScale();
                    gs.setDescription(GradeScale.Type.OTHER.toString());
                    gs.setExternalRef(externalRef);
                    gs.setDisplayName(scale.get("name").asText());
                    gs.save();
                    for (JsonNode grade : scale.get("grades")) {
                        if (!grade.has("description")) {
                            Logger.warn("Skipping over grade, required nodes are missing: {}", grade.asText());
                            continue;
                        }
                        Grade g = new Grade();
                        g.setName(grade.get("description").asText());
                        g.setGradeScale(gs);
                        g.save();
                    }
                    scales.add(gs);
                } else {
                    scales.add(Ebean.find(GradeScale.class, scaleType.getValue()));
                }
            }
        }
        return scales;
    }

    public static Result getNewRecords(String startDate) {
        return ok(Json.toJson(getScores(startDate)));
    }

    // for testing purposes
    public static Result getNewRecordsAlphabeticKeyOrder(String startDate) {
        try {
            return ok(convertNode(Json.toJson(getScores(startDate))));
        } catch (JsonProcessingException e) {
            return Results.internalServerError(e.getMessage());
        }
    }

    private static String getFirstChildNameValue(JsonNode json, String columnName) {
        if (json.has(columnName)) {
            JsonNode array = json.get(columnName);
            if (array.has(0)) {
                JsonNode child = array.get(0);
                if (child.has("name")) {
                    return child.get("name").asText();
                }
            }
        }
        return null;
    }

    private static List<ExamScore> getScores(String startDate) {
        DateTime start = ISODateTimeFormat.dateTimeParser().parseDateTime(startDate);
        List<ExamRecord> examRecords = Ebean.find(ExamRecord.class)
                .select("exam_score")
                .where()
                .gt("time_stamp", start.toDate())
                .findList();

        List<ExamScore> examScores = new ArrayList<>();
        for (ExamRecord record : examRecords) {
            examScores.add(record.getExamScore());
        }
        return examScores;
    }

    private static List<Course> parseResponse(JsonNode response) {
        List<Course> results = new ArrayList<>();
        for (JsonNode node : response) {
            // check that this is a course node, response can also contain error messages and so on
            if (node.has("identifier")) {
                Course course = new Course();
                course.setId(0L); // FIXME: smells like a hack

                if (node.has("courseUnitTitle")) {
                    course.setName(node.get("courseUnitTitle").asText());
                }
                if (node.has("courseUnitCode")) {
                    course.setCode(node.get("courseUnitCode").asText());
                }
                if (node.has("courseUnitType")) {
                    course.setCourseUnitType(node.get("courseUnitType").asText());
                }
                if (node.has("startDate")) {
                    course.setStartDate(node.get("startDate").asText());
                }
                if (node.has("credits")) {
                    course.setCredits(node.get("credits").asDouble());
                }
                if (node.has("identifier")) {
                    course.setIdentifier(node.get("identifier").asText());
                }
                if (node.has("institutionName")) {
                    String name = node.get("institutionName").asText();
                    Organisation organisation = Ebean.find(Organisation.class).where().ieq("name", name).findUnique();
                    // TODO: organisations should preexist or not? As a safeguard, lets create these for now if not found.
                    if (organisation == null) {
                        organisation = new Organisation();
                        organisation.setName(name);
                        organisation.save();
                    }
                    course.setOrganisation(organisation);
                }
                List<GradeScale> scales = getGradeScales(node);
                if (!scales.isEmpty()) {
                    // For now support just a single scale per course
                    course.setGradeScale(scales.get(0));
                }

                // in array form
                course.setCampus(getFirstChildNameValue(node, "campus"));
                course.setDegreeProgramme(getFirstChildNameValue(node, "degreeProgramme"));
                course.setDepartment(getFirstChildNameValue(node, "department"));
                course.setLecturer(getFirstChildNameValue(node, "lecturer"));
                course.setCreditsLanguage(getFirstChildNameValue(node, "creditsLanguage"));
                results.add(course);
            }
        }
        return results;
    }

    private static String convertNode(JsonNode node) throws JsonProcessingException {
        Object obj = SORTED_MAPPER.treeToValue(node, Object.class);
        return SORTED_MAPPER.writeValueAsString(obj);
    }

    private static boolean isCourseSearchActive() {
        try {
            return ConfigFactory.load().getBoolean("sitnet.integration.courseUnitInfo.active");
        } catch (ConfigException e) {
            Logger.error("Failed to load config", e);
            return false;
        }
    }

}
