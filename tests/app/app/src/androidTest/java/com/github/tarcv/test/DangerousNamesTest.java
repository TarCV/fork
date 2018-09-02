package com.github.tarcv.test;

import android.support.test.rule.ActivityTestRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static com.github.tarcv.test.Config.TEST_DURATION;
import static org.hamcrest.core.AllOf.allOf;

@RunWith(Parameterized.class)
public class DangerousNamesTest {
    @Rule
    public ActivityTestRule<MainActivity> rule = new ActivityTestRule<>(
            MainActivity.class, true, true);

    public DangerousNamesTest(String param) {

    }

    @Test
    public void test() throws InterruptedException {
        onView(withId(R.id.hello_text))
                .check(matches(allOf(
                        isDisplayed(),
                        withText("Hello World!")
                )));

        Thread.sleep(TEST_DURATION);
    }

    @Parameters(name = "param = {0}")
    public static Object[] data() {
        return new Object[] {
                "$THIS_IS_NOT_A_VAR",
                "       1       ",
                "#######",
                "!!!!!!!",
                "'''''''",
                "\"\"\"\"\"\"\"\"",
                "()$(echo)`echo`()$(echo)`echo`()$(echo)`echo`()$(echo)`echo`()$(echo)`echo`" +
                        "()$(echo)`echo`()$(echo)`echo`",
                "* *.* * *.* * *.* * *.* * *.* * *.* * *.* * *.* *",
                ". .. . .. . .. . .. . .. . .. . .. . .. . .. . ..",
                "|&;<>()$`?[]#~=%|&;<>()$`?[]#~=%|&;<>()$`?[]#~=%|&;<>()$`?[]#~=%|&;<>()$`?[]#~=%" +
                        "|&;<>()$`?[]#~=%|&;<>()$`?[]#~=%",
                "; function {}; while {}; for {}; do {}; done {}; exit"
        };
    }
}
