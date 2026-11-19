package com.epi.procurement.identity;

/** An account's e-mail address changed after proof of control: the OLD address is told, as a security signal. */
public record EmailAddressChanged(long userId, String oldEmail, String newEmail, String displayName) {
}
