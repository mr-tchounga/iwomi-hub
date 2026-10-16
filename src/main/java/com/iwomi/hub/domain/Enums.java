package com.iwomi.hub.domain;

public final class Enums {
    private Enums() {}
    public enum ProjectStatus { ACTIVE, MAINTENANCE, ARCHIVED }
    public enum ComponentType { API, FRONTOFFICE, BACKOFFICE, MOBILE }
    public enum SaleStatus { ACTIVE, ENDED }
    public enum KycStatus { DRAFT, SUBMITTED, APPROVED, REJECTED }
    public enum TicketPriority { LOW, MEDIUM, HIGH, URGENT }
    public enum TicketStatus { OPEN, ASSIGNED, IN_PROGRESS, RESOLVED, CLOSED }
    public enum TaskStatus { PLANNED, IN_PROGRESS, BLOCKED, DONE, CANCELLED }
    public enum Role { ADMIN, CTO, HR, ACCOUNTANT, EMPLOYEE, EXTERNAL }
}
