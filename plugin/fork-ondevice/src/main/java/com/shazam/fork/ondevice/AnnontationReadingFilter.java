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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class AnnontationReadingFilter extends Filter {
    private static final int MSG_LENGTH_LIMIT_WITHOUT_PREFIX = 4000 - 16;
    private final AtomicInteger index = new AtomicInteger();
    private final String objectId = String.format("%08x", System.identityHashCode(this));

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

            // Method annotations override class ones, so they should be added last
            appendAnnotationInfos(Arrays.asList(description.getTestClass().getAnnotations()),
                    annotationsInfo);
            appendAnnotationInfos(description.getAnnotations(), annotationsInfo);

            info.put("annotations", annotationsInfo);

            String message = info.toString() + ",";
            int messageLength = message.length();
            for (int i = 0; i < messageLength; i += MSG_LENGTH_LIMIT_WITHOUT_PREFIX) {
                String linePrefix = objectId + "-" + String.format("%08x", index.getAndIncrement()) + ":";
                int endIndex = Math.min(i + MSG_LENGTH_LIMIT_WITHOUT_PREFIX, messageLength);
                Log.i("Fork.TestInfo", linePrefix + message.substring(i, endIndex));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    private void appendAnnotationInfos(Iterable<Annotation> annotations, JSONArray annotationsInfo) throws JSONException {
        for (Annotation annotation: annotations) {
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
