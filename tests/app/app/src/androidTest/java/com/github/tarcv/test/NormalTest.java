package com.github.tarcv.test;

import android.support.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.github.tarcv.test.Config.TEST_DURATION;
import static org.hamcrest.core.AllOf.allOf;

public class NormalTest {
    @Rule
    public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(
            MainActivity.class, true, true);

    @Test
    public void test() throws InterruptedException {
        onView(withId(R.id.hello_text))
                .check(matches(allOf(
                        isDisplayed(),
                        withText("Hello World!")
                )));

        Thread.sleep(TEST_DURATION);
    }
}
