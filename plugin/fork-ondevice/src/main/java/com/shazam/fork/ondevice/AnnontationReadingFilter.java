package com.shazam.fork.ondevice;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.runner.Description;
import org.junit.runner.manipulation.Filter;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class AnnontationReadingFilter extends Filter {
    @Override
    public boolean shouldRun(Description description) {
        if (!description.isTest()) {
            return true;
        }

        JSONObject info = new JSONObject();
        JSONArray annotationsInfo = new JSONArray();

        try {
            info.put("testClass", description.getClassName());
            info.put("testMethod", description.getMethodName());

            for (Annotation annotation: description.getAnnotations()) {
                JSONObject currentAnnotationInfo = new JSONObject();
                Class<? extends Annotation> annotationType = annotation.annotationType();

                currentAnnotationInfo.put("annotationType", annotationType.getCanonicalName());

                for (Method method : annotationType.getMethods()) {
                    if (!isAnnotationParameter(method)) {
                        continue;
                    }

                    String name = method.getName();
                    Object value = getAnnotationParameterValue(annotation, method);
                    currentAnnotationInfo.put(name, JSONObject.wrap(value));
                }

                annotationsInfo.put(currentAnnotationInfo);
            }

            info.put("annotations", annotationsInfo);

            // Indentation MUST be enabled, so that it is sensibly split line by line
            String message = info.toString(1);
            boolean first = true;
            for( String line : message.split("\n") ) {
                if (first) {
                    Log.i("Fork.TestInfo", line);
                    first = false;
                } else {
                    Log.i("Fork.TestInfo", " " + line);
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    private Object getAnnotationParameterValue(Annotation annotation, Method method) {
        try {
            return method.invoke(annotation);
        } catch (IllegalAccessException e) {
            throw createAnnotationParameterValueException(method, e);
        } catch (InvocationTargetException e) {
            throw createAnnotationParameterValueException(method, e);
        }
    }

    private static RuntimeException createAnnotationParameterValueException(Method method, Exception e) {
        return new RuntimeException(String.format("Failed to get value of '%s' annotation parameter", method.getName()), e);
    }

    private boolean isAnnotationParameter(Method method) {
        if (method.getParameterTypes().length > 0) {
            return false;
        }
        switch (method.getName()) {
            case "annotationType":
            case "hashCode":
            case "toString":
                return false;
        }

        return true;
    }

    @Override
    public String describe() {
        return "reading annotations";
    }
}
