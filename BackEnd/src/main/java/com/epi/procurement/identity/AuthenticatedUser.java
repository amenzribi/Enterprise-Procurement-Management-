package com.epi.procurement.identity;

import java.io.Serializable;

/**
 * Principal stored in the HTTP session. Only the user id is trusted; everything else is reloaded per request.
 */
record AuthenticatedUser(long userId, String email) implements Serializable {
}
