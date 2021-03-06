/*
 * Copyright (c) 2017 The members of the EXAM Consortium (https://confluence.csc.fi/display/EXAM/Konsortio-organisaatio)
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl/licence-eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the Licence is distributed
 * on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and limitations under the Licence.
 */

package controllers;

import be.objectify.deadbolt.java.actions.Group;
import be.objectify.deadbolt.java.actions.Restrict;
import be.objectify.deadbolt.java.actions.SubjectPresent;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.ConfigFactory;
import controllers.base.ActionMethod;
import controllers.base.BaseController;
import controllers.iop.api.ExternalExamAPI;
import exceptions.NotFoundException;
import io.ebean.Ebean;
import models.ExamEnrolment;
import models.Language;
import models.Organisation;
import models.Reservation;
import models.Role;
import models.Session;
import models.User;
import models.dto.Credentials;
import org.joda.time.DateTime;
import org.joda.time.Minutes;
import play.Environment;
import play.Logger;
import play.libs.Json;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.Http;
import play.mvc.Result;
import util.AppUtil;

import javax.inject.Inject;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

public class SessionController extends BaseController {

    private final Environment environment;

    private final ExternalExamAPI externalExamAPI;

    private final HttpExecutionContext ec;

    private static final String CSRF_COOKIE = ConfigFactory.load().getString("play.filters.csrf.cookie.name");

    @Inject
    public SessionController(Environment environment, ExternalExamAPI externalExamAPI, HttpExecutionContext ec) {
        this.environment = environment;
        this.externalExamAPI = externalExamAPI;
        this.ec = ec;
    }


    @ActionMethod
    public CompletionStage<Result> login() {
        CompletionStage<Result> result;
        switch (LOGIN_TYPE) {
            case "HAKA":
                result = hakaLogin();
                break;
            case "DEBUG":
                result = devLogin();
                break;
            default:
                result = wrapAsPromise(badRequest("login type not supported"));
        }
        return result;
    }

    private CompletionStage<Result> hakaLogin() {
        Optional<String> id = parse(request().header("eppn").orElse("")); //TODO: check this shit out
        if (!id.isPresent()) {
            return wrapAsPromise(badRequest("No credentials!"));
        }
        String eppn = id.get();
        Reservation externalReservation = getUpcomingExternalReservation(eppn);
        boolean isTemporaryVisitor = externalReservation != null;
        User user = Ebean.find(User.class)
                .where()
                .eq("eppn", eppn)
                .findUnique();
        boolean newUser = user == null;
        try {
            if (newUser) {
                user = createNewUser(eppn, isTemporaryVisitor);
            } else {
                updateUser(user);
            }
        } catch (NotFoundException | AddressException e) {
            return wrapAsPromise(badRequest(e.getMessage()));
        }
        user.setLastLogin(new Date());
        user.save();
        if (newUser) {
            associateWithPreEnrolments(user);
        }
        return handleExternalReservationAndCreateSession(user, externalReservation);
    }

    private CompletionStage<Result> devLogin() {
        if (!environment.isDev()) {
            return wrapAsPromise(unauthorized());
        }
        Credentials credentials = bindForm(Credentials.class);
        Logger.debug("User login with username: {}", credentials.getUsername() + "@funet.fi");
        if (credentials.getPassword() == null || credentials.getUsername() == null) {
            return wrapAsPromise(unauthorized("sitnet_error_unauthenticated"));
        }
        String pwd = AppUtil.encodeMD5(credentials.getPassword());
        User user = Ebean.find(User.class)
                .where().eq("eppn", credentials.getUsername() + "@funet.fi")
                .eq("password", pwd).findUnique();

        if (user == null) {
            return wrapAsPromise(unauthorized("sitnet_error_unauthenticated"));
        }
        user.setLastLogin(new Date());
        user.update();
        // In dev environment we will not fiddle with the role definitions here regarding visitor status
        Reservation externalReservation = getUpcomingExternalReservation(user.getEppn());
        return handleExternalReservationAndCreateSession(user, externalReservation);
    }

    private CompletionStage<Result> handleExternalReservationAndCreateSession(User user, Reservation reservation) {
        if (reservation != null) {
            try {
                return handleExternalReservation(user, reservation).
                        thenApplyAsync(r -> createSession(user, true), ec.current());
            } catch (MalformedURLException e) {
                return wrapAsPromise(internalServerError());
            }
        } else {
            return wrapAsPromise(createSession(user, false));
        }
    }

    private void associateWithPreEnrolments(User user) {
        // Associate pre-enrolment with a real user now that he/she is logged in
        Ebean.find(ExamEnrolment.class)
                .where()
                .eq("preEnrolledUserEmail", user.getEmail())
                .findEach(e -> {
                    e.setPreEnrolledUserEmail(null);
                    e.setUser(user);
                    e.update();
                });
    }

    private Reservation getUpcomingExternalReservation(String eppn) {
        DateTime now = AppUtil.adjustDST(new DateTime());
        int lookAheadMinutes = Minutes.minutesBetween(now, now.plusDays(1).withMillisOfDay(0)).getMinutes();
        DateTime future = now.plusMinutes(lookAheadMinutes);
        List<Reservation> reservations = Ebean.find(Reservation.class).where()
                .eq("externalUserRef", eppn)
                .isNotNull("externalRef")
                .le("startAt", future)
                .gt("endAt", now)
                .orderBy("startAt")
                .findList();
        return reservations.isEmpty() ? null : reservations.get(0);
    }

    private CompletionStage<Result> handleExternalReservation(User user, Reservation reservation) throws MalformedURLException {
        ExamEnrolment enrolment = Ebean.find(ExamEnrolment.class).where().eq("reservation", reservation).findUnique();
        if (enrolment != null) {
            // already imported
            return wrapAsPromise(ok());
        }
        reservation.setUser(user);
        reservation.update();
        return externalExamAPI.requestEnrolment(user, reservation)
                .thenApplyAsync(e -> e == null ? internalServerError() : ok());

    }

    private static Language getLanguage(String code) {
        Language language = null;
        if (code != null) {
            // for example: en-US -> en
            String lcCode = code.split("-")[0].toLowerCase();
            language = Ebean.find(Language.class, lcCode);
        }
        if (language == null) {
            // Default to English
            language = Ebean.find(Language.class, "en");
        }
        return language;
    }

    private Optional<String> validateEmail(String email) {
        try {
            new InternetAddress(email).validate();
        } catch (AddressException e) {
            return Optional.empty();
        }
        return Optional.of(email);
    }

    private String parseUserIdentifier(String src) {
        return src.substring(src.lastIndexOf(":") + 1);
    }

    private Optional<String> parseDisplayName(Http.Request request) {
        return parse(request.header("displayName").orElse("")).map(n ->
                n.indexOf(" ") > 0 ? n.substring(0, n.lastIndexOf(" ")) : n);
    }

    private String parseGivenName(Http.Request request) {
        return parse(request.header("givenName").orElse(""))
                .orElse(parseDisplayName(request)
                        .orElseThrow(IllegalArgumentException::new));
    }

    private Organisation findOrganisation(String attribute) {
        return Ebean.find(Organisation.class).where().eq("code", attribute).findUnique();
    }

    private void updateUser(User user) throws AddressException {
        user.setOrganisation(parse(request().header("homeOrganisation").orElse(""))
                .map(this::findOrganisation).orElse(null));
        user.setUserIdentifier(parse(request().header("schacPersonalUniqueCode").orElse(""))
                .map(this::parseUserIdentifier).orElse(null));
        user.setEmail(parse(request().header("mail").orElse(""))
                .flatMap(this::validateEmail).orElseThrow(AddressException::new));

        user.setLastName(parse(request().header("sn").orElse(""))
                .orElseThrow(IllegalArgumentException::new));
        user.setFirstName(parseGivenName(request()));
        user.setEmployeeNumber(parse(request().header("employeeNumber").orElse("")).orElse(null));
        user.setLogoutUrl(parse(request().header("logouturl").orElse("")).orElse(null));
    }

    private User createNewUser(String eppn, boolean ignoreRoleNotFound) throws NotFoundException, AddressException {
        User user = new User();
        user.getRoles().addAll(parseRoles(parse(request().header("unscoped-affiliation").orElse(""))
                .orElseThrow(NotFoundException::new), ignoreRoleNotFound));
        user.setLanguage(getLanguage(parse(request().header("preferredLanguage").orElse(""))
                .orElse(null)));
        user.setEppn(eppn);
        updateUser(user);
        return user;
    }

    private Result createSession(User user, boolean isTemporaryVisitor) {
        Session session = new Session();
        session.setSince(DateTime.now());
        session.setUserId(user.getId());
        session.setValid(true);
        // If user has just one role, set it as the one used for login
        if (user.getRoles().size() == 1) {
            session.setLoginRole(user.getRoles().get(0).getName());
        }
        session.setTemporalStudent(isTemporaryVisitor);
        String token = createSession(session);
        List<Role> roles = isTemporaryVisitor ?
                Ebean.find(Role.class).where().eq("name", Role.Name.STUDENT.toString()).findList() : user.getRoles();

        ObjectNode result = Json.newObject();
        result.put("id", user.getId());
        result.put("token", token);
        result.put("firstName", user.getFirstName());
        result.put("lastName", user.getLastName());
        result.put("lang", user.getLanguage().getCode());
        result.set("roles", Json.toJson(roles));
        result.set("permissions", Json.toJson(user.getPermissions()));
        result.put("userAgreementAccepted", user.isUserAgreementAccepted());
        result.put("userIdentifier", user.getUserIdentifier());
        return ok(result);
    }

    // prints HAKA attributes, used for debugging
    public Result getAttributes() {
        Http.Headers attributes = request().getHeaders();
        ObjectNode node = Json.newObject();

        for (Map.Entry<String, List<String>> entry : attributes.toMap().entrySet()) {
            node.put(entry.getKey(), String.join(", ", entry.getValue()));
        }

        return ok(node);
    }

    private static Set<Role> parseRoles(String attribute, boolean ignoreRoleNotFound) throws NotFoundException {
        Map<Role, List<String>> roleMapping = getConfiguredRoleMapping();
        Set<Role> userRoles = new HashSet<>();
        for (String affiliation : attribute.split(";")) {
            for (Map.Entry<Role, List<String>> entry : roleMapping.entrySet()) {
                if (entry.getValue().contains(affiliation)) {
                    userRoles.add(entry.getKey());
                    break;
                }
            }
        }
        if (userRoles.isEmpty() && !ignoreRoleNotFound) {
            throw new NotFoundException("sitnet_error_role_not_found " + attribute);
        }
        return userRoles;
    }

    private static Map<Role, List<String>> getConfiguredRoleMapping() {
        Role student = Ebean.find(Role.class).where().eq("name", Role.Name.STUDENT.toString()).findUnique();
        Role teacher = Ebean.find(Role.class).where().eq("name", Role.Name.TEACHER.toString()).findUnique();
        Role admin = Ebean.find(Role.class).where().eq("name", Role.Name.ADMIN.toString()).findUnique();
        Map<Role, List<String>> roles = new HashMap<>();
        roles.put(student, ConfigFactory.load().getStringList("sitnet.roles.student"));
        roles.put(teacher, ConfigFactory.load().getStringList("sitnet.roles.teacher"));
        roles.put(admin, ConfigFactory.load().getStringList("sitnet.roles.admin"));
        return roles;
    }

    @ActionMethod
    public Result logout() {
        Session session = getSession();
        Result result = ok();
        if (session != null) {
            User user = Ebean.find(User.class, session.getUserId());
            session.setValid(false);
            updateSession(session);
            Logger.info("Set session for user #{} as invalid", session.getUserId());
            if (user != null && user.getLogoutUrl() != null) {
                ObjectNode node = Json.newObject();
                node.put("logoutUrl", user.getLogoutUrl());
                result = ok(Json.toJson(node));
            }
        }

        response().discardCookie(CSRF_COOKIE);
        session().clear();
        return result;
    }

    @SubjectPresent
    public Result setLoginRole(Long uid, String roleName) {
        Session session = getSession();
        if (session == null) {
            return unauthorized();
        }
        User user = Ebean.find(User.class, uid);
        if (user == null) {
            return notFound();
        }
        Role role = Ebean.find(Role.class).where().eq("name", roleName).findUnique();
        if (role == null) {
            return notFound();
        }
        if (!user.getRoles().contains(role)) {
            return forbidden();
        }
        session.setLoginRole(roleName);
        updateSession(session);
        return ok();
    }


    @Restrict({@Group("TEACHER"), @Group("ADMIN"), @Group("STUDENT")})
    public Result extendSession() {
        Session session = getSession();
        if (session == null) {
            return unauthorized();
        }
        session.setSince(DateTime.now());
        updateSession(session);
        return ok();
    }

    @ActionMethod
    public Result checkSession() {
        Session session = getSession();
        if (session == null || session.getSince() == null) {
            Logger.info("Session not found");
            return ok("no_session");
        }
        DateTime expirationTime = session.getSince().plusMinutes(SITNET_TIMEOUT_MINUTES);
        DateTime alarmTime = expirationTime.minusMinutes(2);
        Logger.debug("Session expiration due at {}", expirationTime);
        if (expirationTime.isBeforeNow()) {
            Logger.info("Session has expired");
            return ok("no_session");
        } else if (alarmTime.isBeforeNow()) {
            return ok("alarm");
        }
        return ok();
    }

    private static Optional<String> parse(String src) {
        if (src == null || src.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(URLDecoder.decode(src, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

}
