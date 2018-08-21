package com.github.tarcv.test;

import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;

import junit.framework.Assert;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static android.content.Context.MODE_PRIVATE;
import static com.github.tarcv.test.Config.TEST_DURATION;

@RunWith(Parameterized.class)
public class ResetPrefsTest {
    @Rule
    public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(
            MainActivity.class, true, true);

    public ResetPrefsTest(int param) {

    }

    @Test
    public void testPrefsAreClearedBetweenTests() throws InterruptedException {
        boolean prefNotPresent = InstrumentationRegistry.getTargetContext()
                .getSharedPreferences(this.getClass().getName(), MODE_PRIVATE)
                .getAll()
                .isEmpty();
        Assert.assertTrue("Prefs should be empty", prefNotPresent);

        Thread.sleep(TEST_DURATION);

        InstrumentationRegistry.getTargetContext()
                .getSharedPreferences(this.getClass().getName(), MODE_PRIVATE)
                .edit()
                .putBoolean("TEST_KEY", true)
                .commit();
    }

    @Parameters
    public static Object[] data() {
        return new Object[] { 1, 2, 3, 4 };
    }
}
