package security;

import be.objectify.deadbolt.core.models.Subject;
import be.objectify.deadbolt.java.AbstractDeadboltHandler;
import controllers.UserController;
import play.libs.F;
import play.mvc.Http;
import play.mvc.SimpleResult;

/**
 * Created by avainik on 3/3/14.
 */
public class SitnetDeadboltHandler  extends AbstractDeadboltHandler {

    @Override
    public F.Promise<SimpleResult> beforeAuthCheck(Http.Context context) {

        // returning null means that everything is OK.  Return a real result if you want a redirect to a login page or
        // somewhere else
        return F.Promise.pure(null);    }

    @Override
    public Subject getSubject(Http.Context context)
    {
        // in a real application, the user name would probably be in the session following a login process
        return UserController.getLoggedUser();

    }

    @Override
    public F.Promise<SimpleResult> onAuthFailure(Http.Context context, String content)
    {
        // you can return any result from here - forbidden, etc
        return F.Promise.promise(new F.Function0<SimpleResult>()
        {
            @Override
            public SimpleResult apply() throws Throwable {
                return forbidden("Authentication failure");
            }
        });
    }

}
