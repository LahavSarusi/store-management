package com.store.client.ui;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * Runs the continuation of a server call on the Swing thread. The UI thread never blocks waiting for the server.
 */
public final class Async {

    private Async() { }

    /** On failure, shows the error in a dialog over {@code parent}. */
    public static <T> void then(Component parent, CompletableFuture<T> future, Consumer<T> onSuccess) {
        then(future, onSuccess, error -> Ui.showError(parent, error));
    }

    public static <T> void then(CompletableFuture<T> future, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        future.whenComplete((value, error) -> SwingUtilities.invokeLater(() -> {
            if (error == null) {
                onSuccess.accept(value);
            } else {
                onError.accept(unwrap(error));
            }
        }));
    }

    static Throwable unwrap(Throwable t) {
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        return t;
    }
}
