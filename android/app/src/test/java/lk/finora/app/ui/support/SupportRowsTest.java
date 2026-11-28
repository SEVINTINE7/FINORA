package lk.finora.app.ui.support;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import lk.finora.app.R;
import lk.finora.app.data.repo.SupportRepository.Topic;

public final class SupportRowsTest {
    @Test public void statesKeepTheirWordsAndUnknownFallsBack() {
        assertEquals(R.string.sms_diagnostic_open, SupportRows.stateLabel("OPEN"));
        assertEquals(R.string.support_in_progress, SupportRows.stateLabel("IN_PROGRESS"));
        assertEquals(R.string.support_closed, SupportRows.stateLabel("CLOSED"));
        assertEquals(R.string.support_retired, SupportRows.stateLabel("RETIRED"));
        assertEquals(R.string.sms_diagnostic_unknown, SupportRows.stateLabel("SOMETHING_NEW"));
    }

    @Test public void everyTopicHasAnIconAndTheStandardSentenceShownBeforeSending() {
        for (Topic topic : Topic.values()) {
            assertNotEquals(0, SupportRows.icon(topic));
            assertFalse(topic.body().isBlank());
        }
        assertEquals("I need help signing in.", Topic.SIGN_IN.body());
        assertEquals(R.drawable.finora_ic_mail, SupportRows.icon(null));
    }
}
