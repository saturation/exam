package controllers;

import Exceptions.MalformedDataException;
import com.avaje.ebean.Ebean;
import models.Session;
import models.User;
import org.joda.time.Period;
import play.cache.Cache;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;

public class SitnetController extends Controller {

    public static final String SITNET_TOKEN_HEADER_KEY = "x-sitnet-authentication";
    public static final String SITNET_CACHE_KEY = "user.session.";
    public static final int SITNET_TIMEOUT_MINUTES = 30;

    static <T> T bindForm(Class<T> clazz) throws MalformedDataException {
        Form<T> form = Form.form(clazz);
        if (form.hasErrors()) {
            throw new MalformedDataException(form.errorsAsJson().asText());
        }
        return form.bindFromRequest().get();
    }
}
