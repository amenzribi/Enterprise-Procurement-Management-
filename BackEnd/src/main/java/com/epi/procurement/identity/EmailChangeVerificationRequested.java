package com.epi.procurement.identity;

/**
 * An e-mail change is ready for proof of control: the notification module sends a single-use link to the NEW
 * address (the token is created when the e-mail is sent, see {@link EmailChangeTokens}).
 */
public record EmailChangeVerificationRequested(long changeId, long userId, String newEmail, String displayName) {
}
