package lk.finora.app.acceptance;

import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;

import android.view.View;

import org.hamcrest.Matcher;

import lk.finora.app.R;

/** T68: More and Settings entries are rows whose label is a title inside the clickable row. */
final class SettingsRowMatchers {
    private SettingsRowMatchers() {}

    /** A clickable control with this text, or a row (item_import_link) whose title has this text. */
    static Matcher<View> control(int label) {
        return anyOf(allOf(withText(label), isClickable()),
                allOf(isClickable(), hasDescendant(allOf(withId(R.id.link_title), withText(label)))));
    }
}
