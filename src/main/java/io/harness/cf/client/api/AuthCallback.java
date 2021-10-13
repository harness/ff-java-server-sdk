package io.harness.cf.client.api;

/**
 * Authentication callback.
 */
public interface AuthCallback {

    /**
     * Authentication success.
     *
     * @param success True == Authentication ok.
     * @param error   Error if occurred.
     */
    void onSuccess(boolean success, Exception error);
}
