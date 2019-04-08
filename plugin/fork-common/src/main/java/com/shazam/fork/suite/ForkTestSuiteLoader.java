/*
 * Copyright 2016 Shazam Entertainment Limited
 * Derivative work is Copyright 2018 TarCV
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
 */

package com.shazam.fork.suite;

import com.google.gson.JsonObject;
import com.shazam.fork.io.DexFileExtractor;
import com.shazam.fork.model.TestCaseEvent;
import org.jf.dexlib.*;
import org.jf.dexlib.EncodedValue.AnnotationEncodedSubValue;
import org.jf.dexlib.EncodedValue.ArrayEncodedValue;
import org.jf.dexlib.EncodedValue.EncodedValue;
import org.jf.dexlib.EncodedValue.StringEncodedValue;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.*;

import static com.shazam.fork.model.TestCaseEvent.newTestCase;
import static java.lang.Math.min;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class ForkTestSuiteLoader implements TestSuiteLoader {
    private static final String TEST_ANNOTATION = "Lorg/junit/Test;";
    private static final String IGNORE_ANNOTATION = "Lorg/junit/Ignore;";
    private static final String REVOKE_PERMISSION_ANNOTATION = "Lcom/shazam/fork/RevokePermission;";
    private static final String TEST_PROPERTIES_ANNOTATION = "Lcom/shazam/fork/TestProperties;";

    private final File instrumentationApkFile;
    private final DexFileExtractor dexFileExtractor;
    private final TestClassMatcher testClassMatcher;

    public ForkTestSuiteLoader(File instrumentationApkFile, DexFileExtractor dexFileExtractor, TestClassMatcher testClassMatcher) {
        this.instrumentationApkFile = instrumentationApkFile;
        this.dexFileExtractor = dexFileExtractor;
        this.testClassMatcher = testClassMatcher;
    }

    @Override
    public Collection<TestCaseEvent> loadTestSuite() throws NoTestCasesFoundException {
        List<TestCaseEvent> testCaseEvents = dexFileExtractor.getDexFiles(instrumentationApkFile).stream()
                .map(dexFile -> dexFile.ClassDefsSection.getItems())
                .flatMap(Collection::stream)
                .filter(c -> testClassMatcher.matchesPatterns(c.getClassType().getTypeDescriptor()))
                .map(this::convertClassToTestCaseEvents)
                .flatMap(Collection::stream)
                .collect(toList());

        if (testCaseEvents.isEmpty()) {
            throw new NoTestCasesFoundException("No tests cases were found in the test APK: " + instrumentationApkFile.getAbsolutePath());
        }
        return testCaseEvents;
    }

    @Nonnull
    private List<TestCaseEvent> convertClassToTestCaseEvents(ClassDefItem classDefItem) {
        AnnotationDirectoryItem annotationDirectoryItem = classDefItem.getAnnotations();
        if (annotationDirectoryItem == null) {
            return emptyList();
        }

        List<TestCaseEvent> testCaseEvents = new ArrayList<>();
        for (AnnotationDirectoryItem.MethodAnnotation method : annotationDirectoryItem.getMethodAnnotations()) {
            stream(method.annotationSet.getAnnotations())
                    .filter(annotation -> TEST_ANNOTATION.equals(stringType(annotation)))
                    .map(annotation -> convertToTestCaseEvent(classDefItem, annotationDirectoryItem, method))
                    .forEach(testCaseEvents::add);
        }
        return testCaseEvents;
    }

    @Nonnull
    private TestCaseEvent convertToTestCaseEvent(ClassDefItem classDefItem,
                                                 AnnotationDirectoryItem annotationDirectoryItem,
                                                 AnnotationDirectoryItem.MethodAnnotation method) {
        String testMethod = method.method.getMethodName().getStringValue();
        AnnotationItem[] annotations = method.annotationSet.getAnnotations();
        String testClass = getClassName(classDefItem);
        boolean ignored = isClassIgnored(annotationDirectoryItem) || isMethodIgnored(annotations);
        List<String> permissionsToRevoke = getPermissionsToRevoke(annotations);
        Map<String, String> properties = getTestProperties(annotations);
        return newTestCase(testMethod, testClass, ignored, permissionsToRevoke, properties, new JsonObject());
    }

    private String getClassName(ClassDefItem classDefItem) {
        String typeDescriptor = classDefItem.getClassType().getTypeDescriptor();
        return typeDescriptor.substring(1, typeDescriptor.length() - 1).replace('/', '.');
    }

    private boolean isMethodIgnored(AnnotationItem... annotationItems) {
        return containsAnnotation(IGNORE_ANNOTATION, annotationItems);
    }

    private List<String> getPermissionsToRevoke(AnnotationItem[] annotations) {
        return stream(annotations)
                .filter(annotationItem -> REVOKE_PERMISSION_ANNOTATION.equals(stringType(annotationItem)))
                .map(annotationItem -> annotationItem.getEncodedAnnotation().values)
                .flatMap(encodedValues -> stream(encodedValues)
                        .flatMap(encodedValue -> stream(((ArrayEncodedValue) encodedValue).values)
                                .map(stringEncoded -> ((StringEncodedValue) stringEncoded).value.getStringValue())))
                .collect(toList());
    }

    private Map<String, String> getTestProperties(AnnotationItem[] annotations) {
        Map<String, String> properties = new HashMap<>();
        stream(annotations)
                .filter(annotationItem -> TEST_PROPERTIES_ANNOTATION.equals(stringType(annotationItem)))
                .map(AnnotationItem::getEncodedAnnotation)
                .forEach(an -> {
                    keyValueArraysToProperties(properties, getAnnotationProperty(an, "keys"), getAnnotationProperty(an, "values"));
                });
        return properties;
    }

    public static void keyValueArraysToProperties(Map<String, String> properties, List<String> keys, List<String> values) {
        for (int i = 0; i < min(values.size(), keys.size()); i++) {
            properties.put(keys.get(i), values.get(i));
        }
    }

    private List<String> getAnnotationProperty(AnnotationEncodedSubValue an, String propertyName) {
        int propValueIndex = indexOfName(an, propertyName);
        if (propValueIndex >= 0) {
            EncodedValue[] values = ((ArrayEncodedValue) an.values[propValueIndex]).values;
            return stream(values)
                    .map(stringEncodedValue -> ((StringEncodedValue) stringEncodedValue).value.getStringValue())
                    .collect(toList());
        } else {
            return emptyList();
        }
    }

    private int indexOfName(AnnotationEncodedSubValue p, String key) {
        int index = -1;
        StringIdItem[] names = p.names;
        for (int i = 0; i < names.length; i++) {
            if (names[i].getStringValue().equals(key)) return i;
        }
        return index;
    }

    private boolean isClassIgnored(AnnotationDirectoryItem annotationDirectoryItem) {
        AnnotationSetItem classAnnotations = annotationDirectoryItem.getClassAnnotations();
        if (classAnnotations == null) {
            return false;
        }
        return containsAnnotation(IGNORE_ANNOTATION, classAnnotations.getAnnotations());
    }

    private boolean containsAnnotation(String comparisonAnnotation, AnnotationItem... annotations) {
        return stream(annotations).anyMatch(annotation -> comparisonAnnotation.equals(stringType(annotation)));
    }

    private String stringType(AnnotationItem annotation) {
        return annotation.getEncodedAnnotation().annotationType.getTypeDescriptor();
    }
}
