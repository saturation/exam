package controllers;

import com.avaje.ebean.Ebean;
import com.google.inject.Inject;
import exceptions.MalformedDataException;
import models.Session;
import models.User;
import play.cache.CacheApi;
import play.data.Form;
import play.libs.F;
import play.mvc.Controller;
import play.mvc.Result;

public class BaseController extends Controller {

    public static final String SITNET_TOKEN_HEADER_KEY = "x-exam-authentication";
    public static final String SITNET_CACHE_KEY = "user.session.";
    public static final int SITNET_TIMEOUT_MINUTES = 30;

    @Inject
    protected CacheApi cache;

    public <T> T bindForm(final Class<T> clazz) {
        final Form<T> form = Form.form(clazz);
        if (form.hasErrors()) {
            throw new MalformedDataException(form.errorsAsJson().asText());
        }
        return form.bindFromRequest().get();
    }

    protected Result ok(Object object) {
        String body = Ebean.json().toJson(object);
        return ok(body).as("application/json");
    }

    protected User getLoggedUser() {
        String token = request().getHeader(SITNET_TOKEN_HEADER_KEY);
        Session session = cache.get(SITNET_CACHE_KEY + token);
        return Ebean.find(User.class, session.getUserId());
    }

    protected F.Promise<Result> wrapAsPromise(final Result result) {
        return F.Promise.promise(() -> result);
    }
}