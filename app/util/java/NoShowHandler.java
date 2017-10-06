package util.java;

import com.google.inject.ImplementedBy;
import models.Reservation;

import java.util.List;

@ImplementedBy(NoShowHandlerUtil.class)
public interface NoShowHandler {

    void handleNoShows(List<Reservation> noShows);
    void handleNoShowAndNotify(Reservation reservation);

}