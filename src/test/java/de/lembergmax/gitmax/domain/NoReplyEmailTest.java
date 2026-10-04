package de.lembergmax.gitmax.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class NoReplyEmailTest {

    @Test
    public void githubFormatIsIdPlusLogin() {
        assertEquals("42+max@users.noreply.github.com", NoReplyEmail.github(42, "max"));
    }

    @Test
    public void gitlabFormatUsesHostnameWithoutPort() {
        assertEquals("9-max@users.noreply.gitlab.com", NoReplyEmail.gitlab(9, "max", "gitlab.com"));
        assertEquals("9-max@users.noreply.gitlab.intern", NoReplyEmail.gitlab(9, "max", "gitlab.intern:8443"));
    }
}
