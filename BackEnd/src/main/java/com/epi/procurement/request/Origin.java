package com.epi.procurement.request;

/**
 * Internal (spending) or external (customer, sales) request (spec §3.5). Only INTERNAL is created until the
 * external-request decision (RC-15) is taken; the column exists so no migration is needed then.
 */
enum Origin {

	INTERNAL, EXTERNAL

}
