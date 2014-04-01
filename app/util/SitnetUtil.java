package util;

import Exceptions.SitnetException;
import annotations.NonCloneable;
import com.fasterxml.jackson.annotation.JsonBackReference;
import controllers.UserController;
import models.SitnetModel;
import models.User;
import org.apache.commons.codec.digest.DigestUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Timestamp;

/**
 * Created by avainik on 3/19/14.
 */
public class SitnetUtil {


    public static Object getClone(Object object) {
        Object clone = null;

        try {
            clone = object.getClass().newInstance();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        // Walk up the superclass hierarchy
        for (Class obj = object.getClass(); !obj.equals(Object.class); obj = obj.getSuperclass()) {
            // Todo: check annotation
            Field[] fields = obj.getDeclaredFields();
            for (int i = 0; i < fields.length; i++) {
                fields[i].setAccessible(true);

                try {
                    if(fields[i].get(object) != null) {


                        if (fields[i].getAnnotation(JsonBackReference.class) == null) {

                            if (fields[i].getAnnotation(NonCloneable.class) == null) {
                                fields[i].setAccessible(true);
                                try {

                                    Class clazz = fields[i].get(object).getClass();
                                    Class superclass = clazz.getSuperclass();

                                    if (SitnetModel.class.isAssignableFrom(superclass)) {

                                        Method method = null;
                                        try {
                                            method = clazz.getDeclaredMethod("clone", null);
                                            if (method == null) {
                                                break;
                                            } else {
                                                if(fields[i].get(object) != null) {

                        // ERROR here, returned object has all fields null

                                                    Object obo = method.invoke(fields[i].get(object), null);
                                                    fields[i].set(clone, obo);

                                                }
                                            }
                                        } catch (NoSuchMethodException e) {
                                            e.printStackTrace();
                                        } catch (InvocationTargetException e) {
                                            e.printStackTrace();
                                        } catch (IllegalAccessException e) {
                                            e.printStackTrace();
                                        }

                                    } else  // its not SitnetModel, just clone it
                                    {
                                        if(fields[i].get(object) != null) {
                                            String name = fields[i].getName();

                                            // if this is SitnetModel and must be cloned; set ID null
                                            if(name.equals("id"))
                                                fields[i].set(clone, null);
                                            else
                                                fields[i].set(clone, fields[i].get(object));
                                        }
                                    }

                                } catch (IllegalAccessException e) {
                                    e.printStackTrace();
                                }
                            }
                            else
                                try {
                                    fields[i].setAccessible(true);
                                    if(fields[i].get(object) != null)
                                        fields[i].set(clone, fields[i].get(object));
                                } catch (IllegalAccessException e) {
                                    e.printStackTrace();
                                }
                        }

                    }
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return clone;
    }

    static public SitnetModel setCreator(SitnetModel object) throws SitnetException {

        User user = UserController.getLoggedUser();
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());

        if(object.getCreator() != null) {
        	throw new SitnetException("Object already has creator");
        } else {
            object.setCreator(user);
            object.setCreated(currentTime);
        }

        return object;
    }

    static public SitnetModel setModifier(SitnetModel object) throws SitnetException {
    	
    	User user = UserController.getLoggedUser();
    	Timestamp currentTime = new Timestamp(System.currentTimeMillis());
    	
    	// check if user is the owner of this object
    	if(object.getCreator() != user) {
        	throw new SitnetException("User id:"+ user.getId() +" is not owner of this object");
    	} else {
    		object.setModifier(user);
    		object.setModified(currentTime);
    	}
    	
    	return object;
    }
    
    static public String encodeMD5(String str) {
        return DigestUtils.md5Hex(str);
    }
}
