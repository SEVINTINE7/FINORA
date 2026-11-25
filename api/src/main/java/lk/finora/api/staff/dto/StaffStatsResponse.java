package lk.finora.api.staff.dto;

/** Unresolved support-queue counts. No ticket contents or owner identifiers. */
public record StaffStatsResponse(long myOpenTickets, long unassignedTickets) {}
