package com.github.tarcv.test;

import android.content.pm.PackageManager;
import android.support.test.rule.ActivityTestRule;
import android.support.v4.content.ContextCompat;
import com.shazam.fork.GrantPermission;
import org.junit.Rule;
import org.junit.Test;

import static android.Manifest.permission;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.*;
import static com.github.tarcv.test.Config.TEST_DURATION;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.assertEquals;

@GrantPermission({permission.WRITE_CALENDAR})
public class GrantPermissionsForClassTest {
    @Rule
    public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(
            MainActivity.class, true, true);

    @Test
    public void testPermissionGranted1() throws InterruptedException {
        assertEquals(PackageManager.PERMISSION_GRANTED,
                ContextCompat.checkSelfPermission(rule.getActivity(), permission.WRITE_CALENDAR));

        onView(withId(R.id.hello_text))
                .check(matches(allOf(
                        isDisplayed(),
                        withText("Hello World!")
                )));

        Thread.sleep(TEST_DURATION);
    }

    @Test
    public void testPermissionGranted2() throws InterruptedException {
        assertEquals(PackageManager.PERMISSION_GRANTED,
                ContextCompat.checkSelfPermission(rule.getActivity(), permission.WRITE_CALENDAR));

        onView(withId(R.id.hello_text))
                .check(matches(allOf(
                        isDisplayed(),
                        withText("Hello World!")
                )));

        Thread.sleep(TEST_DURATION);
    }
}
