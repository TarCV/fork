package com.github.tarcv.test;

import android.support.test.rule.ActivityTestRule;
import com.shazam.fork.TestProperties;
import org.junit.Rule;
import org.junit.Test;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.*;
import static com.github.tarcv.test.Config.TEST_DURATION;
import static org.hamcrest.core.AllOf.allOf;

public class PropertiesTest {
    @Rule
    public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(
            MainActivity.class, true, true);

    @Test
    @TestProperties(keys = {"a", "b"}, values = {"1"})
    public void missingValuesTest() throws InterruptedException {
        onView(withId(R.id.hello_text))
                .check(matches(allOf(
                        isDisplayed(),
                        withText("Hello World!")
                )));

        Thread.sleep(TEST_DURATION);
    }

    @Test
    @TestProperties(keys = {"c"}, values = {"3", "4"})
    public void missingKeysTest() throws InterruptedException {
        onView(withId(R.id.hello_text))
                .check(matches(allOf(
                        isDisplayed(),
                        withText("Hello World!")
                )));

        Thread.sleep(TEST_DURATION);
    }
}
