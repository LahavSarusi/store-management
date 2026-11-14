package com.store.client.ui;

import com.store.client.dto.ErrorCode;
import com.store.client.net.ServerException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

/** What the login screen tells the user for each way a login can fail. */
class LoginMessagesTest {

    @Test
    void doubleLoginIsExplained() {
        String m = LoginFrame.messageFor(new ServerException(ErrorCode.ALREADY_LOGGED_IN, "raw"));
        assertTrue(m.contains("already logged in"), m);
    }

    @Test
    void wrongPasswordAndBusyServerHaveTheirOwnMessages() {
        assertEquals("Wrong username or password.",
                LoginFrame.messageFor(new ServerException(ErrorCode.INVALID_CREDENTIALS, "raw")));
        assertTrue(LoginFrame.messageFor(new ServerException(ErrorCode.SERVER_BUSY, "raw")).contains("Try again"));
    }

    @Test
    void otherErrorsShowTheServerMessage() {
        assertEquals("Account is locked",
                LoginFrame.messageFor(new ServerException(ErrorCode.FORBIDDEN, "Account is locked")));
        assertEquals("Cannot reach the server",
                LoginFrame.messageFor(new ServerException(null, "Cannot reach the server")));
    }

    @Test
    void asyncWrappersAreUnwrapped() {
        Throwable wrapped = new CompletionException(new ServerException(ErrorCode.CONFLICT, "Taken"));
        assertEquals("Taken", Ui.messageOf(wrapped));
    }
}
